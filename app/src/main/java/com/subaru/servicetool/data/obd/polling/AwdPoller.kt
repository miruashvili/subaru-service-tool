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
 * Dedicated high-frequency poller for AWD Transfer Duty.
 *
 * AWD duty is a critical real-time metric for the AWD engagement display.
 * Running it in its own poller ensures it updates at the highest possible rate
 * independent of the other (potentially slower) TCU sensors in CvtPoller.
 *
 * Priority queue:
 *   HIGH — AWD_TRANSFER_DUTY only (SSM A8, every iteration under [moduleHeaderMutex])
 *
 * Header isolation: each poll acquires [moduleHeaderMutex] so AwdPoller and
 * CvtPoller never interleave their ATSH switches. If the mutex is held by
 * CvtPoller, AwdPoller naturally queues behind it with no special handling.
 *
 * Failure of AwdPoller does not affect EnginePoller, CvtPoller, or TpmsPoller.
 */
class AwdPoller(
    btManager: OBDBluetoothManager,
    capabilityProber: ObdCapabilityProber,
    sensorRegistry: SensorRegistry,
    sensorValues: MutableStateFlow<Map<String, Float>>,
    private val moduleHeaderMutex: Mutex,
) : BasePoller(btManager, capabilityProber, sensorRegistry, sensorValues) {

    override val tag = "AwdPoller"

    companion object {
        /** Minimum delay between AWD polls — yields ~5–10 Hz depending on adapter RTT. */
        private const val POLL_INTERVAL_MS = 100L
        private const val HEADER_TIMEOUT   = 2_000L
    }

    suspend fun run(isTurbo: Boolean) {
        val pid = ObdPids.AWD_DUTY

        // Verify the sensor is actually in the registry for this vehicle
        val entry = sensorRegistry.getByPidCmd(pid.cmd)
        if (entry == null) {
            Log.w(tag, "AWD_DUTY not found in SensorRegistry — poller idle")
            return
        }

        Log.i(tag, "Starting — HIGH queue: AWD_TRANSFER_DUTY (SSM A8 0x%06X) interval=${POLL_INTERVAL_MS}ms".format(pid.ssmAddress))

        var iteration = 0

        while (isConnected()) {
            tickSkipCounters()

            if (!isSkipped(pid) && isConnected()) {
                Log.d(tag, "--- iteration=$iteration AWD poll ---")
                val value = pollAwdUnderHeaderLock()
                emitValue(pid, value)
            }

            delay(POLL_INTERVAL_MS)
            iteration++
        }
        Log.i(tag, "Exiting — not connected")
    }

    /**
     * Acquires [moduleHeaderMutex], switches to ATSH7E1, reads AWD duty via SSM A8,
     * then restores ATSH7E0. The lock prevents header collisions with CvtPoller.
     */
    private suspend fun pollAwdUnderHeaderLock(): Float? {
        return moduleHeaderMutex.withLock {
            val headerOk = btManager.sendCommand("ATSH7E1", HEADER_TIMEOUT) != null
            if (!headerOk) {
                Log.w(tag, "ATSH7E1 timed out — skipping AWD poll")
                return@withLock null
            }
            delay(SSM_SETTLE_MS)
            val value = try {
                querySsmSingle(ObdPids.AWD_DUTY)
            } finally {
                btManager.sendCommand("ATSH7E0", HEADER_TIMEOUT)
            }
            value
        }
    }
}
