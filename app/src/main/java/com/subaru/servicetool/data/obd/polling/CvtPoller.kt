package com.subaru.servicetool.data.obd.polling

import android.util.Log
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.bluetooth.adapter.AdapterProfileManager
import com.subaru.servicetool.data.obd.CapabilitySnapshot
import com.subaru.servicetool.data.obd.ObdCapabilityProber
import com.subaru.servicetool.data.obd.ObdPids
import com.subaru.servicetool.data.obd.SensorRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Polls Subaru TCU sensors related to CVT operation.
 *
 * Sensors: CVT fluid temperature, CVT gear ratios (actual + target), primary and
 * secondary pulley speeds, turbine revolution, and CVT lock-up duty.
 *
 * Priority queues:
 *   HIGH   — CVT fluid temperature (SSM A8, every iteration)
 *   MEDIUM — Ratios, pulley speeds, turbine RPM, lockup duty (every [MEDIUM_EVERY])
 *   LOW    — (empty for CVT)
 *
 * Header isolation: the entire TCU transaction (ATSH7E1 → reads → ATSH7E0) is
 * executed under [moduleHeaderMutex] so CvtPoller and AwdPoller never interleave
 * their header switches.
 *
 * Failure of CvtPoller does not affect EnginePoller, AwdPoller, or TpmsPoller.
 */
class CvtPoller(
    btManager: OBDBluetoothManager,
    capabilityProber: ObdCapabilityProber,
    sensorRegistry: SensorRegistry,
    sensorValues: MutableStateFlow<Map<String, Float>>,
    private val moduleHeaderMutex: Mutex,
    private val singleReadMode: AtomicBoolean,
    private val onBatchFailed: () -> Unit,
    profileManager: AdapterProfileManager? = null,
) : BasePoller(btManager, capabilityProber, sensorRegistry, sensorValues, profileManager) {

    override val tag = "CvtPoller"

    companion object {
        private const val MEDIUM_EVERY   = 3
        private const val CYCLE_DELAY_MS = 200L  // ~5 Hz for CVT temp
        private const val HEADER_TIMEOUT = 2_000L
    }

    suspend fun run(snapshot: CapabilitySnapshot, isTurbo: Boolean) {
        val tcuSensors = sensorRegistry.allSensors()
            .filter { it.module.name == "TCU" }
            .map { it.obdPid }

        // HIGH — CVT temp is SSM A8
        val highSsm = listOf(ObdPids.CVT_TEMP).filter { it in tcuSensors }

        // MEDIUM — lockup duty (SSM A8) + UDS sensors (ratios, speeds, turbine)
        val mediumSsm = listOf(ObdPids.LOCKUP_DUTY).filter { it in tcuSensors }
        val mediumUds = listOf(
            ObdPids.CVT_RATIO_ACTUAL,
            ObdPids.CVT_RATIO_TARGET,
            ObdPids.PRIMARY_PULLEY_SPEED,
            ObdPids.SECONDARY_PULLEY_SPEED,
            ObdPids.TURBINE_RPM,
        ).filter { it in tcuSensors }

        Log.i(tag, "Queues: HIGH-SSM=${highSsm.size} MEDIUM-SSM=${mediumSsm.size} MEDIUM-UDS=${mediumUds.size}")

        val tcuSupported = snapshot.tcuStates.values.count { it == com.subaru.servicetool.data.obd.CapabilityState.SUPPORTED }
        val tcuUnknown   = snapshot.tcuStates.values.count { it == com.subaru.servicetool.data.obd.CapabilityState.UNKNOWN }
        Log.i(tag, "TCU advisory: $tcuSupported SUPPORTED / $tcuUnknown UNKNOWN — polling all regardless")

        var iteration = 0

        while (isConnected()) {
            tickSkipCounters()

            // ── HIGH — CVT fluid temp (SSM A8, under header mutex) ────────────
            if (highSsm.isNotEmpty() && isConnected()) {
                val dueSsm = highSsm.filter { !isSkipped(it) }
                if (dueSsm.isNotEmpty()) {
                    Log.d(tag, "--- iteration=$iteration HIGH (CVT temp) ---")
                    tcuTransaction {
                        val results = querySsmBatch(dueSsm, !singleReadMode.get()) {
                            singleReadMode.set(true)
                            onBatchFailed()
                        }
                        for ((pid, value) in results) emitValue(pid, value)
                    }
                }
            }

            // ── MEDIUM — ratios, speeds, turbine, lockup ──────────────────────
            if (iteration % MEDIUM_EVERY == 0 && isConnected()) {
                val allMedium = (mediumSsm + mediumUds).filter { !isSkipped(it) }
                if (allMedium.isNotEmpty()) {
                    Log.d(tag, "--- MEDIUM (${allMedium.size} PIDs) ---")
                    tcuTransaction {
                        val ssmDue = mediumSsm.filter { !isSkipped(it) }
                        if (ssmDue.isNotEmpty()) {
                            val results = querySsmBatch(ssmDue, !singleReadMode.get()) {
                                singleReadMode.set(true)
                                onBatchFailed()
                            }
                            for ((pid, value) in results) emitValue(pid, value)
                        }
                        for (pid in mediumUds) {
                            if (!isConnected() || isSkipped(pid)) continue
                            emitValue(pid, queryUds(pid))
                        }
                    }
                }
            }

            delay(CYCLE_DELAY_MS)
            iteration++
        }
        Log.i(tag, "Exiting — not connected")
    }

    /**
     * Executes [block] under [moduleHeaderMutex] with ATSH7E1 set beforehand and
     * ATSH7E0 restored afterwards. The mutex prevents header collisions with AwdPoller.
     */
    private suspend fun tcuTransaction(block: suspend () -> Unit) {
        moduleHeaderMutex.withLock {
            val ok = btManager.sendCommand("ATSH7E1", HEADER_TIMEOUT) != null
            if (!ok) {
                Log.w(tag, "ATSH7E1 timed out — skipping TCU transaction")
                return
            }
            delay(SSM_SETTLE_MS)
            try {
                block()
            } finally {
                btManager.sendCommand("ATSH7E0", HEADER_TIMEOUT)
                Log.d(tag, "Header restored to 7E0")
            }
        }
    }
}
