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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ObdQueryEngine"
private const val TCU_READ_TIMEOUT_MS = 150L

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

                // Separate TCU (7E1) pids for single-switch batching
                val allDue      = t2 + t3 + t4
                val tcuPids     = allDue.filter { it.header == "7E1" }
                val regularPids = t1 + allDue.filter { it.header != "7E1" }

                // ── Regular PIDs ──────────────────────────────────────────────
                for (pid in regularPids) {
                    if (!isConnected()) break
                    val response = btManager.sendCommand(pid.cmd, profile.commandTimeoutMs)
                    if (response == null) {
                        consecutiveTimeouts++
                        if (consecutiveTimeouts >= 5) {
                            Log.w(TAG, "5 consecutive timeouts — reinitializing ELM327")
                            btManager.reinitializeElm327()
                            consecutiveTimeouts = 0
                        }
                    } else {
                        consecutiveTimeouts = 0
                        trackResult(pid, parsePid(pid, response), current, consecutiveErrors, skipCycles)
                    }
                    if (profile.delayBetweenPidsMs > 0L) delay(profile.delayBetweenPidsMs)
                }

                // ── TCU batch ─────────────────────────────────────────────────
                if (tcuPids.isNotEmpty() && isConnected()) {
                    val ok = batchQueryTcu(tcuPids, profile, current, consecutiveErrors, skipCycles)
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

        // TPMS sentinel: if any slot shows the combined TPMS widget, include all 4 sensors
        if ("TPMS_ALL" in allCmds) active += ObdPids.TIER4

        // All other tiered pids: include only if their cmd appears in a configured slot
        val candidates = ObdPids.TIER2 + ObdPids.TIER3 + ObdPids.TIER4
        for (pid in candidates) {
            if (pid.cmd in allCmds) active += pid
        }

        Log.d(TAG, "Active PIDs: ${active.joinToString { it.name }}")
        return active
    }

    // ── TCU batch query ───────────────────────────────────────────────────────

    private suspend fun batchQueryTcu(
        pids: List<ObdPid>,
        profile: AdapterSpeedProfile,
        current: MutableMap<String, Float>,
        consecutiveErrors: MutableMap<String, Int>,
        skipCycles: MutableMap<String, Int>,
    ): Boolean {
        // Switch to TCU header; bail immediately on timeout
        if (btManager.sendCommand("ATSH7E1", profile.commandTimeoutMs) == null) {
            btManager.sendCommand("ATSH7E0", profile.commandTimeoutMs)
            return false
        }

        var anyRead = false
        for (pid in pids) {
            if (!isConnected()) break
            val response = btManager.sendCommand(pid.cmd, TCU_READ_TIMEOUT_MS)
            if (response == null) break  // skip remaining TCU pids on timeout
            val value = ObdParser.parseUdsResponse(response, pid.cmd)?.let { pid.parse(it) }
            trackResult(pid, value, current, consecutiveErrors, skipCycles)
            anyRead = true
            if (profile.delayBetweenPidsMs > 0L) delay(profile.delayBetweenPidsMs)
        }

        // Always restore ECU header
        btManager.sendCommand("ATSH7E0", profile.commandTimeoutMs)
        return anyRead
    }

    // ── Query helpers ─────────────────────────────────────────────────────────

    private fun parsePid(pid: ObdPid, response: String): Float? = when {
        pid.cmd == "ATRV"        -> ObdParser.parseVoltage(response)
        pid.cmd.startsWith("22") -> ObdParser.parseUdsResponse(response, pid.cmd)?.let { pid.parse(it) }
        else                     -> ObdParser.parseStandard(response, pid.cmd)?.let { pid.parse(it) }
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
                Log.d(TAG, "Skipping ${pid.name} for 10 cycles after 3 consecutive parse errors")
                skipCycles[pid.cmd] = 10
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
