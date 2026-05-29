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

// SSM A8 single-address read: adapter needs extra time after ATSH switch
private const val SSM_SETTLE_MS        = 300L
// Per-PID timeout for SSM A8 reads (ECU is slower than standard OBD-II)
private const val SSM_TIMEOUT_MS       = 2_000L
// Retry pause when a module PID times out
private const val MODULE_RETRY_PAUSE_MS = 500L
// Number of consecutive module-batch failures before the batch is suspended
private const val MODULE_FAIL_THRESHOLD = 3
// How many cycles to skip a failed module batch
private const val MODULE_SKIP_CYCLES    = 20

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
    private var cachedProbe: SensorProbeResult? = null

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

            val probeResult = loadOrRunProbe()
            val activePids  = buildActivePidSet(probeResult)

            // Per-PID error/skip tracking
            val consecutiveErrors = mutableMapOf<String, Int>()
            val skipCycles        = mutableMapOf<String, Int>()
            // Per-module-header failure tracking (independent from per-PID)
            val moduleFailures    = mutableMapOf<String, Int>()
            val moduleSkipCycles  = mutableMapOf<String, Int>()

            val recentCycleTimes  = ArrayDeque<Long>()
            var baselineCycleTime = -1L
            var fastCycleCount    = 0
            var consecutiveTimeouts = 0
            var cycle = 0

            while (isConnected()) {
                val profile    = btManager.adapterSpeedProfile.value
                val cycleStart = System.currentTimeMillis()

                // Tick down per-PID skip counters
                skipCycles.keys.toList().forEach { key ->
                    val remaining = (skipCycles[key] ?: 0) - 1
                    if (remaining <= 0) { skipCycles.remove(key); consecutiveErrors.remove(key) }
                    else skipCycles[key] = remaining
                }
                // Tick down per-module skip counters
                moduleSkipCycles.keys.toList().forEach { header ->
                    val remaining = (moduleSkipCycles[header] ?: 0) - 1
                    if (remaining <= 0) { moduleSkipCycles.remove(header); moduleFailures.remove(header) }
                    else moduleSkipCycles[header] = remaining
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

                val allDue = t2 + t3 + t4

                // Group non-ECM module PIDs for header-switching batches (7E1, 7D4, …)
                val modulePidGroups = allDue
                    .filter { pid ->
                        pid.header != null && pid.header != "7E0" &&
                        (pid.header != "7E1" || probeResult.tcuAvailable) &&
                        (moduleSkipCycles[pid.header] ?: 0) == 0
                    }
                    .groupBy { it.header!! }
                val regularPids = t1 + allDue.filter { it.header == null || it.header == "7E0" }

                // ── Regular PIDs (standard OBD-II + ECM SSM A8) ──────────────
                for (pid in regularPids) {
                    if (!isConnected()) break

                    val (response, value) = queryRegularPid(pid, probeResult, profile)

                    if (response == null) {
                        consecutiveTimeouts++
                        if (consecutiveTimeouts >= 5) {
                            Log.w(TAG, "5 consecutive timeouts — reinitializing ELM327")
                            btManager.reinitializeElm327()
                            consecutiveTimeouts = 0
                        }
                    } else {
                        consecutiveTimeouts = 0
                        var resolvedValue = value

                        // IAT fallback: some adapters return NO DATA for 010F — try 0168
                        if (pid == ObdPids.INTAKE_TEMP && resolvedValue == null && isConnected()) {
                            val fallback = btManager.sendCommand("0168", profile.commandTimeoutMs)
                            if (fallback != null) {
                                // PID 68 byte A is a sensor-support bitmap; the first
                                // intake-air-temperature reading is byte B (value − 40).
                                resolvedValue = ObdParser.parseStandard(fallback, "0168")
                                    ?.let { bytes -> if (bytes.size >= 2) (bytes[1] - 40).toFloat() else null }
                            }
                        }
                        trackResult(pid, resolvedValue, current, consecutiveErrors, skipCycles)
                    }
                    if (profile.delayBetweenPidsMs > 0L) delay(profile.delayBetweenPidsMs)
                }

                // ── Per-module batches (TCU 7E1, BCM 7D4, …) ─────────────────
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
                        val fails = (moduleFailures[header] ?: 0) + 1
                        moduleFailures[header] = fails
                        if (fails >= MODULE_FAIL_THRESHOLD) {
                            Log.w(TAG, "Module $header failed $fails times — skipping for $MODULE_SKIP_CYCLES cycles")
                            moduleSkipCycles[header] = MODULE_SKIP_CYCLES
                        }
                    } else {
                        consecutiveTimeouts = 0
                        moduleFailures.remove(header)
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
        cachedProbe = null
    }

    // ── Sensor probe ──────────────────────────────────────────────────────────

    private suspend fun loadOrRunProbe(): SensorProbeResult {
        cachedProbe?.let { return it }

        val storedSource = userPreferences.probeOilTempSource.first()
        val storedTcu    = userPreferences.probeTcuAvailable.first()
        if (storedSource != "NONE" || storedTcu) {
            val src = runCatching { OilTempSource.valueOf(storedSource) }.getOrDefault(OilTempSource.NONE)
            Log.d(TAG, "Probe cache hit: oil=$src tcu=$storedTcu")
            return SensorProbeResult(src, storedTcu).also { cachedProbe = it }
        }

        return runSensorProbe().also {
            cachedProbe = it
            userPreferences.saveSensorProbe(it.oilTempSource.name, it.tcuAvailable)
            Log.d(TAG, "Probe complete: oil=${it.oilTempSource} tcu=${it.tcuAvailable}")
        }
    }

    private suspend fun runSensorProbe(): SensorProbeResult {
        val profile = btManager.adapterSpeedProfile.value

        // Probe 1: standard OBD-II Mode 01 PID 5C
        var oilTempSource = OilTempSource.NONE
        val stdResp = btManager.sendCommand("015C", SSM_TIMEOUT_MS)
        if (stdResp != null && ObdParser.parseStandard(stdResp, "015C")?.isNotEmpty() == true) {
            oilTempSource = OilTempSource.OBD_STANDARD
            Log.d(TAG, "Probe 1: OIL_TEMP → OBD_STANDARD (015C)")
        }

        if (oilTempSource == OilTempSource.NONE) {
            // Probe 2: SSM A8 read at ECU address 0x0000AF
            val ssmResp = btManager.sendCommand(buildSsmA8Command(0x0000AF), SSM_TIMEOUT_MS)
            if (ssmResp != null && ObdParser.parseSsmResponse(ssmResp) != null) {
                oilTempSource = OilTempSource.SSM_ECU
                Log.d(TAG, "Probe 2: OIL_TEMP → SSM_ECU (0x0000AF)")
            }
        }

        if (oilTempSource == OilTempSource.NONE) {
            // Probe 3: SSM A8 alternate ECU address 0x009D5C
            val ssmResp2 = btManager.sendCommand(buildSsmA8Command(0x009D5C), SSM_TIMEOUT_MS)
            if (ssmResp2 != null && ObdParser.parseSsmResponse(ssmResp2) != null) {
                oilTempSource = OilTempSource.SSM_ECU_ALT
                Log.d(TAG, "Probe 3: OIL_TEMP → SSM_ECU_ALT (0x009D5C)")
            }
        }

        // Probe 4: TCU via SSM A8 to header 7E1
        var tcuAvailable = false
        if (btManager.sendCommand("ATSH7E1", profile.commandTimeoutMs) != null) {
            delay(SSM_SETTLE_MS)
            val cvtResp = btManager.sendCommand(buildSsmA8Command(0x001017), SSM_TIMEOUT_MS)
            if (cvtResp != null && ObdParser.parseSsmResponse(cvtResp) != null) {
                tcuAvailable = true
                Log.d(TAG, "Probe 4: TCU available (SSM A8 0x001017 @ 7E1)")
            }
            // Always restore ECM header
            btManager.sendCommand("ATSH7E0", profile.commandTimeoutMs)
        }

        return SensorProbeResult(oilTempSource, tcuAvailable)
    }

    // ── Active PID set ────────────────────────────────────────────────────────

    private suspend fun buildActivePidSet(probe: SensorProbeResult): Set<ObdPid> {
        val allCmds = mutableSetOf<String>()
        allCmds += userPreferences.gaugeSlots.first()
        allCmds += userPreferences.wideGaugeSlots.first()
        allCmds += userPreferences.lsTopSlots.first()
        allCmds += userPreferences.lsMidSlots.first()
        allCmds += userPreferences.lsBotSlots.first()
        allCmds += userPreferences.lsBotWideSlots.first()
        allCmds += userPreferences.landscapeBottomSlots.first()

        val vehicle = userPreferences.selectedVehicle.first()
        val isTurbo = vehicle?.isTurbo ?: true

        val active = mutableSetOf<ObdPid>()

        active += ObdPids.TIER1
        active += ObdPids.AMBIENT_TEMP
        active += ObdPids.MAF
        active += ObdPids.INTAKE_TEMP

        if (probe.oilTempSource != OilTempSource.NONE) active += ObdPids.OIL_TEMP
        if (probe.tcuAvailable) {
            active += ObdPids.CVT_TEMP
            active += ObdPids.AWD_DUTY
        }

        if ("TPMS_ALL" in allCmds) active += ObdPids.TIER4

        val candidates = (ObdPids.TIER2 + ObdPids.TIER3 + ObdPids.TIER4)
            .filter { !it.isTurboOnly || isTurbo }
            .filter { probe.tcuAvailable || it.header != "7E1" }
        for (pid in candidates) {
            if (pid.cmd in allCmds) active += pid
        }

        active += _carActivePids.value

        Log.d(TAG, "Active PIDs: ${active.joinToString { it.name }}")
        return active
    }

    // ── Regular PID query (standard OBD-II + ECM SSM A8) ─────────────────────

    /**
     * Issues the correct command for [pid] and returns the raw response plus the parsed value.
     * OIL_TEMP is routed to the probed physical source; all other SSM PIDs use the A8 command
     * built from [ObdPid.ssmAddress].
     */
    private suspend fun queryRegularPid(
        pid: ObdPid,
        probe: SensorProbeResult,
        profile: AdapterSpeedProfile,
    ): Pair<String?, Float?> {
        val timeout = if (pid.ssmAddress != null) SSM_TIMEOUT_MS else profile.commandTimeoutMs

        if (pid == ObdPids.OIL_TEMP) {
            return when (probe.oilTempSource) {
                OilTempSource.OBD_STANDARD -> {
                    val resp = btManager.sendCommand("015C", timeout)
                    val v = resp?.let {
                        ObdParser.parseStandard(it, "015C")
                            ?.let { b -> if (b.isNotEmpty()) (b[0] - 40).toFloat() else null }
                    }
                    resp to v
                }
                OilTempSource.SSM_ECU -> {
                    // Uses pid.ssmAddress = 0x0000AF
                    val resp = btManager.sendCommand(buildSsmA8Command(0x0000AF), timeout)
                    resp to resp?.let { parsePid(pid, it) }
                }
                OilTempSource.SSM_ECU_ALT -> {
                    // Alternate memory address; parser is address-agnostic
                    val resp = btManager.sendCommand(buildSsmA8Command(0x009D5C), timeout)
                    resp to resp?.let { parsePid(pid, it) }
                }
                OilTempSource.NONE -> null to null
            }
        }

        val cmd  = if (pid.ssmAddress != null) buildSsmA8Command(pid.ssmAddress) else pid.cmd
        val resp = btManager.sendCommand(cmd, timeout)
        return resp to resp?.let { parsePid(pid, it) }
    }

    // ── Module batch query (TCU 7E1, BCM 7D4, …) ─────────────────────────────

    /**
     * Switches to [header], queries all [pids] via SSM A8 (or their native cmd), then
     * restores the ECM header (7E0). Returns true if at least one PID was read successfully.
     */
    private suspend fun batchQueryModule(
        header: String,
        pids: List<ObdPid>,
        profile: AdapterSpeedProfile,
        current: MutableMap<String, Float>,
        consecutiveErrors: MutableMap<String, Int>,
        skipCycles: MutableMap<String, Int>,
    ): Boolean {
        if (btManager.sendCommand("ATSH$header", profile.commandTimeoutMs) == null) {
            Log.w(TAG, "ATSH$header timed out — skipping module batch")
            btManager.sendCommand("ATSH7E0", profile.commandTimeoutMs)
            return false
        }
        // Give the adapter time to settle after a header switch
        delay(SSM_SETTLE_MS)

        var anyRead = false
        for (pid in pids) {
            if (!isConnected()) break
            val cmd = if (pid.ssmAddress != null) buildSsmA8Command(pid.ssmAddress) else pid.cmd
            var response = btManager.sendCommand(cmd, SSM_TIMEOUT_MS)
            if (response == null) {
                delay(MODULE_RETRY_PAUSE_MS)
                response = btManager.sendCommand(cmd, SSM_TIMEOUT_MS)
            }
            if (response == null) continue
            val value = if (pid.ssmAddress != null) {
                ObdParser.parseSsmResponse(response)?.let { v -> pid.parse(listOf(v)) }
            } else {
                ObdParser.parseUdsResponse(response, pid.cmd)?.let { pid.parse(it) }
            }
            trackResult(pid, value, current, consecutiveErrors, skipCycles)
            anyRead = true
            if (profile.delayBetweenPidsMs > 0L) delay(profile.delayBetweenPidsMs)
        }

        // Always restore ECM header regardless of read outcome
        btManager.sendCommand("ATSH7E0", profile.commandTimeoutMs)
        return anyRead
    }

    // ── SSM A8 command builder ────────────────────────────────────────────────

    /**
     * Builds an SSM-over-CAN read-single-address command:
     *   05 A8 00 [addr_hi] [addr_mid] [addr_lo]
     *
     * `05` = ISO 15765-4 single-frame PCI byte (5 data bytes following).
     * `A8` = Subaru SSM read-address service ID.
     * `00` = flags / sub-function (always 0 for single read).
     * The 3-byte address follows MSB-first.
     */
    private fun buildSsmA8Command(address: Int): String {
        val hi  = (address shr 16) and 0xFF
        val mid = (address shr 8) and 0xFF
        val lo  = address and 0xFF
        return "05A800%02X%02X%02X".format(hi, mid, lo)
    }

    // ── Parse helpers ─────────────────────────────────────────────────────────

    private fun parsePid(pid: ObdPid, response: String): Float? {
        if (pid.ssmAddress != null) {
            return ObdParser.parseSsmResponse(response)?.let { v -> pid.parse(listOf(v)) }
        }
        val mode = pid.cmd.take(2).toIntOrNull(16) ?: return null
        return if (mode > 9) {
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
                val skipFor = when {
                    pid.ssmAddress != null -> {
                        Log.i(TAG, "Sensor ${pid.name} not supported on this ECU — stopped polling")
                        9999
                    }
                    pid.header != null && pid.header != "7E0" -> MODULE_SKIP_CYCLES
                    else -> 10
                }
                Log.d(TAG, "Skipping ${pid.name} for $skipFor cycles after 3 consecutive NO DATA")
                skipCycles[pid.cmd] = skipFor
            }
        }
    }

    private fun isSkipped(pid: ObdPid, skipCycles: Map<String, Int>): Boolean =
        (skipCycles[pid.cmd] ?: 0) > 0

    // ── DTC ───────────────────────────────────────────────────────────────────

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
