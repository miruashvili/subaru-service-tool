package com.subaru.servicetool.data.obd

import android.util.Log
import com.subaru.servicetool.data.bluetooth.BluetoothConnectionState
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ObdQueryEngine"
private const val TCU_READ_TIMEOUT_MS = 2000L

@Singleton
class ObdQueryEngine @Inject constructor(
    private val btManager: OBDBluetoothManager,
    private val userPreferences: UserPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sensorValues = MutableStateFlow<Map<String, Float>>(emptyMap())
    val sensorValues: StateFlow<Map<String, Float>> = _sensorValues.asStateFlow()

    private val _dtcCount = MutableStateFlow(0)
    val dtcCount: StateFlow<Int> = _dtcCount.asStateFlow()

    private val _carActivePids = MutableStateFlow<Set<ObdPid>>(emptySet())

    fun setCarActivePids(pids: Set<ObdPid>) { _carActivePids.value = pids }

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
        scope.launch {
            _carActivePids.drop(1).collect { if (pollJob?.isActive == true) startPolling() }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            _sensorValues.value = emptyMap()
            val current = mutableMapOf<String, Float>()

            delay(500)
            queryDtcs()

            val activePids = buildActivePidSet()

            val consecutiveErrors   = mutableMapOf<String, Int>()
            val skipCycles          = mutableMapOf<String, Int>()
            val recentCycleTimes    = ArrayDeque<Long>()
            var baselineCycleTime   = -1L
            var fastCycleCount      = 0
            var consecutiveTimeouts = 0
            var cycle               = 0

            while (isConnected()) {
                val profile    = btManager.adapterSpeedProfile.value
                val cycleStart = System.currentTimeMillis()

                // Tick down skip counters; remove expired entries
                skipCycles.keys.toList().forEach { key ->
                    val remaining = (skipCycles[key] ?: 0) - 1
                    if (remaining <= 0) { skipCycles.remove(key); consecutiveErrors.remove(key) }
                    else skipCycles[key] = remaining
                }

                // Determine which tiers fire this cycle
                val t1 = ObdPids.TIER1.filter { it in activePids && !isSkipped(it, skipCycles) }
                val t2 = if (cycle % profile.tier2Every == 0)
                    ObdPids.TIER2.filter { it in activePids && !isSkipped(it, skipCycles) }
                    else emptyList()
                val t3 = if (cycle % profile.tier3Every == 0)
                    ObdPids.TIER3.filter { it in activePids && !isSkipped(it, skipCycles) }
                    else emptyList()
                val t4 = if (cycle % profile.tier4Every == 0)
                    ObdPids.TIER4.filter { it in activePids && !isSkipped(it, skipCycles) }
                    else emptyList()

                // Group non-ECM module PIDs for header-switching batches (7E1, 7D4, …)
                val allDue          = t2 + t3 + t4
                val modulePidGroups = allDue
                    .filter { it.header != null && it.header != "7E0" }
                    .groupBy { it.header!! }
                val regularPids     = t1 + allDue.filter { it.header == null || it.header == "7E0" }

                // ── Regular PIDs (standard OBD + ECM) ────────────────────────
                for (pid in regularPids) {
                    if (!isConnected()) break
                    val timeout = if (pid.cmd == ObdPids.OIL_TEMP.cmd) 2000L else profile.commandTimeoutMs
                    val response = btManager.sendCommand(pid.cmd, timeout)
                    if (response == null) {
                        consecutiveTimeouts++
                        if (consecutiveTimeouts >= 5) {
                            Log.w(TAG, "5 consecutive timeouts — reinitializing ELM327")
                            btManager.reinitializeElm327()
                            consecutiveTimeouts = 0
                        }
                    } else {
                        consecutiveTimeouts = 0
                        var value = parsePid(pid, response)
                        // IAT fallback: some adapters return NO DATA for 010F — try 0168
                        if (pid == ObdPids.INTAKE_TEMP && value == null && isConnected()) {
                            val fallback = btManager.sendCommand("0168", profile.commandTimeoutMs)
                            if (fallback != null) {
                                value = ObdParser.parseStandard(fallback, "0168")
                                    ?.let { bytes -> if (bytes.isNotEmpty()) (bytes[0] - 40).toFloat() else null }
                            }
                        }
                        trackResult(pid, value, current, consecutiveErrors, skipCycles)
                    }
                    if (profile.delayBetweenPidsMs > 0L) delay(profile.delayBetweenPidsMs)
                }

                // ── Per-module batches (TCM 7E1, BCM 7D4, …) ─────────────────
                for ((header, pids) in modulePidGroups) {
                    if (pids.isEmpty() || !isConnected()) continue
                    val ok = batchQueryModule(header, pids, profile, current, consecutiveErrors, skipCycles)
                    if (!ok) {
                        consecutiveTimeouts++
                        if (consecutiveTimeouts >= 5) {
                            Log.w(TAG, "5 consecutive timeouts — reinitializing ELM327")
                            btManager.reinitializeElm327()
                            consecutiveTimeouts = 0
                        }
                    } else {
                        consecutiveTimeouts = 0
                    }
                }

                // ── Adaptive throttle ─────────────────────────────────────────
                val cycleTime = System.currentTimeMillis() - cycleStart
                recentCycleTimes.addLast(cycleTime)
                if (recentCycleTimes.size > 10) recentCycleTimes.removeFirst()

                if (baselineCycleTime < 0 && recentCycleTimes.size >= 5) {
                    baselineCycleTime = recentCycleTimes.average().toLong()
                    Log.d(TAG, "Baseline: ${baselineCycleTime}ms, profile=${profile.label}")
                }

                if (baselineCycleTime >= 0) {
                    when {
                        cycleTime > baselineCycleTime * 2 -> {
                            btManager.downgradeProfile()
                            fastCycleCount = 0
                            baselineCycleTime = -1L
                            recentCycleTimes.clear()
                        }
                        cycleTime <= baselineCycleTime -> {
                            fastCycleCount++
                            if (fastCycleCount >= 5) {
                                btManager.upgradeProfile()
                                fastCycleCount = 0
                                baselineCycleTime = -1L
                                recentCycleTimes.clear()
                            }
                        }
                        else -> fastCycleCount = 0
                    }
                }

                if (profile.delayBetweenCyclesMs > 0L) delay(profile.delayBetweenCyclesMs)
                cycle++
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        _sensorValues.value = emptyMap()
        _dtcCount.value = 0
    }

    // ── Active PID set ────────────────────────────────────────────────────────

    private suspend fun buildActivePidSet(): Set<ObdPid> {
        val allCmds = mutableSetOf<String>()
        allCmds += userPreferences.gaugeSlots.first()
        allCmds += userPreferences.wideGaugeSlots.first()
        allCmds += userPreferences.lsTopSlots.first()
        allCmds += userPreferences.lsMidSlots.first()
        allCmds += userPreferences.lsBotSlots.first()
        allCmds += userPreferences.lsBotWideSlots.first()
        allCmds += userPreferences.landscapeBottomSlots.first()

        val active = mutableSetOf<ObdPid>()

        // TIER1 always polled — critical real-time readouts
        active += ObdPids.TIER1

        // Ambient temp feeds the landscape nav bar thermometer regardless of slots
        active += ObdPids.AMBIENT_TEMP

        // MAF used for fuel consumption computation
        active += ObdPids.MAF

        // Critical sensors always polled for dashboard widgets and temperature alerts
        active += ObdPids.INTAKE_TEMP
        active += ObdPids.OIL_TEMP
        active += ObdPids.CVT_TEMP
        active += ObdPids.AWD_DUTY

        // TPMS sentinel: if any slot shows the combined TPMS widget, include all 4 sensors
        if ("TPMS_ALL" in allCmds) active += ObdPids.TIER4

        // All other tiered pids: include only if their cmd appears in a configured slot
        val candidates = ObdPids.TIER2 + ObdPids.TIER3 + ObdPids.TIER4
        for (pid in candidates) {
            if (pid.cmd in allCmds) active += pid
        }

        // Android Auto car screen — only active while car screen is shown
        active += _carActivePids.value

        Log.d(TAG, "Active PIDs: ${active.joinToString { it.name }}")
        return active
    }

    // ── Module batch query (any non-ECM header: 7E1, 7D4, …) ─────────────────

    private suspend fun batchQueryModule(
        header: String,
        pids: List<ObdPid>,
        profile: AdapterSpeedProfile,
        current: MutableMap<String, Float>,
        consecutiveErrors: MutableMap<String, Int>,
        skipCycles: MutableMap<String, Int>,
    ): Boolean {
        // Switch to module header; bail immediately on timeout
        if (btManager.sendCommand("ATSH$header", profile.commandTimeoutMs) == null) {
            btManager.sendCommand("ATSH7E0", profile.commandTimeoutMs)
            return false
        }
        delay(200) // adapter needs extra settling time after header switch

        var anyRead = false
        for (pid in pids) {
            if (!isConnected()) break
            var response = btManager.sendCommand(pid.cmd, TCU_READ_TIMEOUT_MS)
            if (response == null) {
                delay(500) // retry once after brief pause
                response = btManager.sendCommand(pid.cmd, TCU_READ_TIMEOUT_MS)
            }
            if (response == null) continue
            val value = ObdParser.parseUdsResponse(response, pid.cmd)?.let { pid.parse(it) }
            trackResult(pid, value, current, consecutiveErrors, skipCycles)
            anyRead = true
            if (profile.delayBetweenPidsMs > 0L) delay(profile.delayBetweenPidsMs)
        }

        // Always restore ECM header
        btManager.sendCommand("ATSH7E0", profile.commandTimeoutMs)
        return anyRead
    }

    // ── Query helpers ─────────────────────────────────────────────────────────

    private fun parsePid(pid: ObdPid, response: String): Float? {
        val mode = pid.cmd.take(2).toIntOrNull(16) ?: return null
        return if (mode > 9) {
            // Mode 21 (Subaru SSM local-id) and Mode 22 (UDS) both use parseUdsResponse
            ObdParser.parseUdsResponse(response, pid.cmd)?.let { pid.parse(it) }
        } else {
            ObdParser.parseStandard(response, pid.cmd)?.let { pid.parse(it) }
        }
    }

    private fun trackResult(
        pid: ObdPid,
        value: Float?,
        current: MutableMap<String, Float>,
        consecutiveErrors: MutableMap<String, Int>,
        skipCycles: MutableMap<String, Int>,
    ) {
        if (value != null) {
            consecutiveErrors.remove(pid.cmd)
            current[pid.cmd] = value
            _sensorValues.value = current.toMap()
        } else {
            val errors = (consecutiveErrors[pid.cmd] ?: 0) + 1
            consecutiveErrors[pid.cmd] = errors
            if (errors >= 3) {
                val skipFor = if (pid.header != null && pid.header != "7E0") 20 else 10
                Log.d(TAG, "Skipping ${pid.name} for $skipFor cycles after 3 consecutive parse errors")
                skipCycles[pid.cmd] = skipFor
            }
        }
    }

    private fun isSkipped(pid: ObdPid, skipCycles: Map<String, Int>): Boolean =
        (skipCycles[pid.cmd] ?: 0) > 0

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
