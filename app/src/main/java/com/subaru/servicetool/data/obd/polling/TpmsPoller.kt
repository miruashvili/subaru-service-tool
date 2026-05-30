package com.subaru.servicetool.data.obd.polling

import android.util.Log
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.obd.ObdCapabilityProber
import com.subaru.servicetool.data.obd.ObdPids
import com.subaru.servicetool.data.obd.SensorRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Polls Body Control Module TPMS sensors at a low update rate.
 *
 * Tire pressure changes slowly — a 5-second interval is more than sufficient.
 * Keeping TPMS in its own poller ensures it never adds latency to the high-frequency
 * EnginePoller cycle.
 *
 * Priority queue:
 *   LOW — TPMS FL, FR, RL, RR (UDS Mode 22 via ATSH7D4, every [POLL_INTERVAL_MS])
 *
 * Header isolation: each poll cycle acquires [moduleHeaderMutex] so the BCM header
 * switch (7D4) is never interleaved with CvtPoller or AwdPoller's 7E1 transactions.
 *
 * Failure of TpmsPoller does not affect EnginePoller, CvtPoller, or AwdPoller.
 */
class TpmsPoller(
    btManager: OBDBluetoothManager,
    capabilityProber: ObdCapabilityProber,
    sensorRegistry: SensorRegistry,
    sensorValues: MutableStateFlow<Map<String, Float>>,
    private val moduleHeaderMutex: Mutex,
) : BasePoller(btManager, capabilityProber, sensorRegistry, sensorValues) {

    override val tag = "TpmsPoller"

    companion object {
        /** Tire pressure is slow-changing — 5 s between polls is more than sufficient. */
        private const val POLL_INTERVAL_MS = 5_000L
        private const val HEADER_TIMEOUT   = 2_000L
    }

    suspend fun run() {
        val tpmsPids = listOf(
            ObdPids.TPMS_FL,
            ObdPids.TPMS_FR,
            ObdPids.TPMS_RL,
            ObdPids.TPMS_RR,
        ).filter { sensorRegistry.getByPidCmd(it.cmd) != null }

        if (tpmsPids.isEmpty()) {
            Log.w(tag, "No TPMS PIDs found in registry — poller idle")
            return
        }

        Log.i(tag, "Starting — LOW queue: ${tpmsPids.joinToString { it.name }} interval=${POLL_INTERVAL_MS}ms via ATSH7D4")

        var iteration = 0

        while (isConnected()) {
            tickSkipCounters()

            val due = tpmsPids.filter { !isSkipped(it) }
            if (due.isNotEmpty() && isConnected()) {
                Log.d(tag, "--- iteration=$iteration TPMS poll (${due.size} sensors) ---")
                pollTpmsUnderHeaderLock(due)
            }

            delay(POLL_INTERVAL_MS)
            iteration++
        }
        Log.i(tag, "Exiting — not connected")
    }

    private suspend fun pollTpmsUnderHeaderLock(pids: List<com.subaru.servicetool.data.obd.ObdPid>) {
        moduleHeaderMutex.withLock {
            val headerOk = btManager.sendCommand("ATSH7D4", HEADER_TIMEOUT) != null
            if (!headerOk) {
                Log.w(tag, "ATSH7D4 timed out — skipping TPMS poll")
                return
            }
            delay(SSM_SETTLE_MS)
            try {
                for (pid in pids) {
                    if (!isConnected()) break
                    var resp = btManager.sendCommand(pid.cmd, SSM_TIMEOUT_MS)
                    if (resp == null) {
                        Log.d(tag, "${pid.name} timeout — retrying once")
                        delay(500L)
                        resp = btManager.sendCommand(pid.cmd, SSM_TIMEOUT_MS)
                    }
                    val value = resp?.let {
                        com.subaru.servicetool.data.obd.ObdParser.parseUdsResponse(it, pid.cmd)?.let { bytes -> pid.parse(bytes) }
                    }
                    emitValue(pid, value)
                }
            } finally {
                btManager.sendCommand("ATSH7E0", HEADER_TIMEOUT)
                Log.d(tag, "Header restored to 7E0")
            }
        }
    }
}
