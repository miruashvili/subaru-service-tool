package com.subaru.servicetool.data.obd

import android.util.Log
import com.subaru.servicetool.data.bluetooth.BluetoothConnectionState
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ObdQueryEngine"

/**
 * Singleton that continuously polls OBD-II PIDs while the adapter is connected.
 *
 * Strategy:
 *  - Dashboard PIDs (RPM, speed, coolant, throttle, intake, voltage) are queried every round.
 *  - Extended PIDs (load, MAP, MAF, fuel, trims, etc.) are queried every 3rd round.
 *  - DTCs are queried once on first connect.
 *  - Natural BLE/SPP round-trip latency (~100-300 ms/command) sets the polling rate;
 *    no artificial inter-command delays are added.
 *  - Results are emitted incrementally so the UI updates as each PID arrives.
 */
@Singleton
class ObdQueryEngine @Inject constructor(
    private val btManager: OBDBluetoothManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sensorValues = MutableStateFlow<Map<String, Float>>(emptyMap())
    val sensorValues: StateFlow<Map<String, Float>> = _sensorValues.asStateFlow()

    private val _dtcCount = MutableStateFlow(0)
    val dtcCount: StateFlow<Int> = _dtcCount.asStateFlow()

    private var pollJob: Job? = null

    init {
        scope.launch {
            btManager.connectionState.collect { state ->
                when (state) {
                    is BluetoothConnectionState.Connected -> startPolling()
                    else -> stopPolling()
                }
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            _sensorValues.value = emptyMap()
            val current = mutableMapOf<String, Float>()
            var round = 0

            // Short settle delay: let the ELM327 finish any remaining init output
            delay(500)

            // Query DTCs once right after connect
            queryDtcs()

            while (btManager.connectionState.value is BluetoothConnectionState.Connected) {
                // Dashboard PIDs — polled every round
                for (pid in ObdPids.DASHBOARD) {
                    if (!isConnected()) break
                    val v = queryPid(pid) ?: continue
                    current[pid.cmd] = v
                    _sensorValues.value = current.toMap()
                }

                // Extended PIDs — polled every 3rd round
                if (round % 3 == 0) {
                    for (pid in ObdPids.EXTENDED) {
                        if (!isConnected()) break
                        val v = queryPid(pid) ?: continue
                        current[pid.cmd] = v
                        _sensorValues.value = current.toMap()
                    }
                }

                // TPMS PIDs — polled every 5th round (changes slowly)
                if (round % 5 == 0) {
                    for (pid in ObdPids.TPMS) {
                        if (!isConnected()) break
                        val v = queryPid(pid) ?: continue
                        current[pid.cmd] = v
                        _sensorValues.value = current.toMap()
                    }
                }

                round++
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        _sensorValues.value = emptyMap()
        _dtcCount.value = 0
    }

    // ── Query helpers ─────────────────────────────────────────────────────────

    private suspend fun queryPid(pid: ObdPid): Float? {
        val response = btManager.sendCommand(pid.cmd) ?: return null
        return if (pid.cmd == "ATRV") {
            ObdParser.parseVoltage(response)
        } else {
            ObdParser.parseStandard(response, pid.cmd)?.let { pid.parse(it) }
        }
    }

    fun requestDtcRefresh() {
        scope.launch { queryDtcs() }
    }

    private suspend fun queryDtcs() {
        val response = btManager.sendCommand("03") ?: return
        val count = ObdParser.parseDtcCount(response)
        _dtcCount.value = count
        Log.d(TAG, "DTC count: $count")
    }

    private fun isConnected() =
        btManager.connectionState.value is BluetoothConnectionState.Connected
}
