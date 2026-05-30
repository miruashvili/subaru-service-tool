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

// SSM A8 read: adapter needs extra time after ATSH switch
private const val SSM_SETTLE_MS        = 300L
// Per-PID timeout for SSM A8 reads (ECU is slower than standard OBD-II)
private const val SSM_TIMEOUT_MS       = 2_000L
// Number of consecutive module-batch failures before the batch is suspended
private const val MODULE_FAIL_THRESHOLD = 3
// How many cycles to skip a failed module batch
private const val MODULE_SKIP_CYCLES    = 20

@Singleton
class ObdQueryEngine @Inject constructor(
    private val btManager: OBDBluetoothManager,
    private val userPreferences: UserPreferences,
    private val capabilityProber: ObdCapabilityProber,
    private val sensorRegistry: SensorRegistry,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sensorValues = MutableStateFlow<Map<String, Float>>(emptyMap())
    val sensorValues: StateFlow<Map<String, Float>> = _sensorValues.asStateFlow()

    private val _dtcCount = MutableStateFlow(0)
    val dtcCount: StateFlow<Int> = _dtcCount.asStateFlow()

    // Number of SSM addresses confirmed SUPPORTED during the last capability probe (advisory).
    private val _detectedSensorCount = MutableStateFlow(0)
    val detectedSensorCount: StateFlow<Int> = _detectedSensorCount.asStateFlow()

    private val _carActivePids = MutableStateFlow<Set<ObdPid>>(emptySet())

    fun setCarActivePids(pids: Set<ObdPid>) { _carActivePids.value = pids }

    private var pollJob: Job? = null
    private var cachedProbe: ProbeResult? = null

    // Adapter can't segment multi-address A8 → read one address per command. Loaded from prefs,
    // flipped (and persisted) the first time a batch read comes back malformed.
    @Volatile private var singleReadMode = false

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

            singleReadMode = userPreferences.adapterSingleRead.first()
            Log.i(TAG, "Polling started — singleReadMode=$singleReadMode")

            val probeResult = loadOrRunProbe()

            // _detectedSensorCount is advisory — reflects how many SSM addresses actually
            // responded during the probe, not how many sensors will be polled.
            _detectedSensorCount.value =
                probeResult.ecuStates.values.count { it == CapabilityState.SUPPORTED } +
                probeResult.tcuStates.values.count { it == CapabilityState.SUPPORTED }
            Log.i(TAG, "Advisory sensor count: ${_detectedSensorCount.value} " +
                "(ECU: ${probeResult.ecuStates.values.count { it == CapabilityState.SUPPORTED }}, " +
                "TCU: ${probeResult.tcuStates.values.count { it == CapabilityState.SUPPORTED }})")
            Log.i(TAG, "Polling proceeds for ALL configured sensors regardless of advisory states")

            val activePids = buildActivePidSet(probeResult)

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

                val due = t1 + t2 + t3 + t4

                // ── Classify the due PIDs ────────────────────────────────────
                // Standard OBD-II + ECM oil-temp (handled individually).
                val individualDue = due.filter {
                    (it.header == null || it.header == "7E0") &&
                    (it.ssmAddress == null || it == ObdPids.OIL_TEMP)
                }
                // ECU SSM addresses — ALL due ECU SSM PIDs are read together in one A8 batch.
                // Capability probe state is advisory only — no address is excluded here.
                val ecuSsmDue = due.filter {
                    it.header == "7E0" && it.ssmAddress != null && it != ObdPids.OIL_TEMP
                }
                // TCU SSM addresses — ALL due TCU SSM PIDs are batched against header 7E1.
                // Capability probe state is advisory only — no address is excluded here.
                val tcuSsmDue = due.filter {
                    it.header == "7E1" && it.ssmAddress != null
                }
                // Remaining module PIDs (UDS Mode 22, header 7E1/7D4/…) — per-PID batch.
                val moduleGroups = due
                    .filter { pid ->
                        pid.header != null && pid.header != "7E0" && pid.ssmAddress == null &&
                        (moduleSkipCycles[pid.header] ?: 0) == 0
                    }
                    .groupBy { it.header!! }

                // Log advisory states for the ECU SSM batch (verbose — only when batch is non-empty)
                if (ecuSsmDue.isNotEmpty()) {
                    Log.d(TAG, "ECU SSM batch cycle=$cycle: " + ecuSsmDue.joinToString { pid ->
                        val state = probeResult.ecuStates[pid.ssmAddress!!] ?: CapabilityState.UNKNOWN
                        "${pid.name}[advisory=$state]"
                    })
                }
                // Log advisory states for the TCU SSM batch
                if (tcuSsmDue.isNotEmpty()) {
                    Log.d(TAG, "TCU SSM batch cycle=$cycle: " + tcuSsmDue.joinToString { pid ->
                        val state = probeResult.tcuStates[pid.ssmAddress!!] ?: CapabilityState.UNKNOWN
                        "${pid.name}[advisory=$state]"
                    })
                }

                // ── Standard OBD-II + oil temp ───────────────────────────────
                for (pid in individualDue) {
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
                            Log.d(TAG, "IAT 010F returned null — trying fallback 0168")
                            val fallback = btManager.sendCommand("0168", profile.commandTimeoutMs)
                            if (fallback != null) {
                                resolvedValue = ObdParser.parseStandard(fallback, "0168")
                                    ?.let { bytes -> if (bytes.size >= 2) (bytes[1] - 40).toFloat() else null }
                                Log.d(TAG, "IAT fallback 0168 → $resolvedValue")
                            }
                        }
                        trackResult(pid, resolvedValue, current, consecutiveErrors, skipCycles)
                    }
                    if (profile.delayBetweenPidsMs > 0L) delay(profile.delayBetweenPidsMs)
                }

                // ── ECU SSM batch (header 7E0, already selected) ─────────────
                if (ecuSsmDue.isNotEmpty() && isConnected()) {
                    Log.d(TAG, "ECU SSM batch: ${ecuSsmDue.size} PIDs")
                    val ok = batchSsm(null, ecuSsmDue, current, consecutiveErrors, skipCycles, profile)
                    consecutiveTimeouts = if (ok) 0 else consecutiveTimeouts + 1
                }

                // ── TCU SSM batch (header 7E1, restore 7E0) ──────────────────
                if (tcuSsmDue.isNotEmpty() && isConnected() &&
                    (moduleSkipCycles["7E1"] ?: 0) == 0) {
                    Log.d(TAG, "TCU SSM batch: ${tcuSsmDue.size} PIDs (advisory TCU states: " +
                        "${probeResult.tcuStates.values.count { it == CapabilityState.SUPPORTED }} SUPPORTED, " +
                        "${probeResult.tcuStates.values.count { it == CapabilityState.UNKNOWN }} UNKNOWN)")
                    val ok = batchSsm("7E1", tcuSsmDue, current, consecutiveErrors, skipCycles, profile)
                    if (!ok) registerModuleFailure("7E1", moduleFailures, moduleSkipCycles)
                    else moduleFailures.remove("7E1")
                } else if (tcuSsmDue.isNotEmpty() && (moduleSkipCycles["7E1"] ?: 0) > 0) {
                    Log.d(TAG, "TCU SSM batch skipped: 7E1 in error back-off for ${moduleSkipCycles["7E1"]} more cycles")
                }

                // ── Remaining module batches (UDS Mode 22) ───────────────────
                for ((header, pids) in moduleGroups) {
                    if (pids.isEmpty() || !isConnected()) continue
                    Log.d(TAG, "Module batch header=$header: ${pids.size} PIDs")
                    val ok = batchQueryModule(header, pids, profile, current, consecutiveErrors, skipCycles)
                    if (!ok) {
                        consecutiveTimeouts++
                        if (consecutiveTimeouts >= 5) {
                            Log.w(TAG, "5 consecutive timeouts — reinitializing ELM327")
                            btManager.reinitializeElm327()
                            consecutiveTimeouts = 0
                        }
                        registerModuleFailure(header, moduleFailures, moduleSkipCycles)
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
                            Log.w(TAG, "Cycle ${cycleTime}ms > 2× baseline ${baselineCycleTime}ms — downgrading profile")
                            btManager.downgradeProfile()
                            fastCycleCount = 0
                            baselineCycleTime = -1L
                            recentCycleTimes.clear()
                        }
                        cycleTime <= baselineCycleTime -> {
                            fastCycleCount++
                            if (fastCycleCount >= 5) {
                                Log.d(TAG, "5 consecutive fast cycles — upgrading profile")
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
        sensorRegistry.reset()
        Log.i(TAG, "Polling stopped — sensor map cleared, registry reset to UNKNOWN")
    }

    private fun registerModuleFailure(
        header: String,
        moduleFailures: MutableMap<String, Int>,
        moduleSkipCycles: MutableMap<String, Int>,
    ) {
        val fails = (moduleFailures[header] ?: 0) + 1
        moduleFailures[header] = fails
        if (fails >= MODULE_FAIL_THRESHOLD) {
            Log.w(TAG, "Module $header failed $fails times — suspending for $MODULE_SKIP_CYCLES cycles")
            Log.w(TAG, "Note: module suspension is runtime error recovery, not capability gating")
            moduleSkipCycles[header] = MODULE_SKIP_CYCLES
        } else {
            Log.d(TAG, "Module $header failure count: $fails/$MODULE_FAIL_THRESHOLD")
        }
    }

    // ── Capability + sensor probe ─────────────────────────────────────────────

    /**
     * Returns cached probe result if available, otherwise runs the full probe sequence.
     * The result is **advisory only** — it is used for logging, metrics, and oil-temp
     * source routing. It never gates which sensors are polled.
     */
    private suspend fun loadOrRunProbe(): ProbeResult {
        cachedProbe?.let {
            Log.d(TAG, "In-memory probe cache hit")
            return it
        }

        val storedCaps = userPreferences.ecuCaps.first()
        if (storedCaps != null) {
            val ecuStates = parseCapsToStates(storedCaps, ObdCapabilityProber.CANDIDATE_ECU_ADDRESSES)
            val tcuStates = parseCapsToStates(
                userPreferences.tcuCaps.first(),
                ObdCapabilityProber.CANDIDATE_TCU_ADDRESSES,
            )
            val src = runCatching {
                OilTempSource.valueOf(userPreferences.probeOilTempSource.first())
            }.getOrDefault(OilTempSource.NONE)

            val ecuSupported = ecuStates.values.count { it == CapabilityState.SUPPORTED }
            val tcuSupported = tcuStates.values.count { it == CapabilityState.SUPPORTED }
            Log.i(TAG, "DataStore probe cache hit: oil=$src ecuSupported=$ecuSupported tcuSupported=$tcuSupported")
            Log.d(TAG, "ECU advisory: ${ecuStates.entries.joinToString { "0x%06X→%s".format(it.key, it.value) }}")
            Log.d(TAG, "TCU advisory: ${tcuStates.entries.joinToString { "0x%06X→%s".format(it.key, it.value) }}")
            Log.i(TAG, "All sensors will be polled regardless of advisory states")

            return ProbeResult(src, ecuStates, tcuStates).also { cachedProbe = it }
        }

        Log.i(TAG, "No probe cache found — running full capability probe (advisory mode)")
        return runProbe().also {
            cachedProbe = it
            userPreferences.saveCapabilities(serializeCaps(it.ecuStates), serializeCaps(it.tcuStates))
            userPreferences.saveSensorProbe(
                it.oilTempSource.name,
                it.tcuStates.values.any { s -> s == CapabilityState.SUPPORTED },
            )
            val ecuSupported = it.ecuStates.values.count { s -> s == CapabilityState.SUPPORTED }
            val tcuSupported = it.tcuStates.values.count { s -> s == CapabilityState.SUPPORTED }
            Log.i(TAG, "Probe saved: oil=${it.oilTempSource} ecuSupported=$ecuSupported tcuSupported=$tcuSupported")
            Log.i(TAG, "Advisory probe complete — all sensors proceed to polling")
        }
    }

    private suspend fun runProbe(): ProbeResult {
        Log.i(TAG, "=== Capability probe START (advisory mode — no sensors will be disabled) ===")
        val ecuStates     = capabilityProber.probeEcuCapabilities()
        val oilTempSource = probeOilTempSource(ecuStates)
        val tcuStates     = capabilityProber.probeTcuCapabilities()
        Log.i(TAG, "=== Capability probe END: oil=$oilTempSource " +
            "ecuStates=${ecuStates.size} tcuStates=${tcuStates.size} ===")
        return ProbeResult(oilTempSource, ecuStates, tcuStates)
    }

    /**
     * Resolves which physical source to use for engine oil temperature.
     * The [ecuStates] map is advisory — an UNSUPPORTED or UNKNOWN state does not prevent
     * trying the source; it only informs routing priority.
     *
     * Priority: OBD_STANDARD (015C) → SSM_ECU (0x0000AF) → SSM_ECU_ALT (0x009D5C) → NONE.
     * When NONE is returned, the polling engine defaults to SSM_ECU (0x0000AF) and lets
     * the runtime error tracker determine actual support.
     */
    private suspend fun probeOilTempSource(ecuStates: Map<Int, CapabilityState>): OilTempSource {
        Log.d(TAG, "Probing oil temp source...")

        val stdResp = btManager.sendCommand("015C", SSM_TIMEOUT_MS)
        if (stdResp != null && ObdParser.parseStandard(stdResp, "015C")?.isNotEmpty() == true) {
            Log.i(TAG, "Oil temp source → OBD_STANDARD (015C)")
            return OilTempSource.OBD_STANDARD
        }
        Log.d(TAG, "015C: no data (resp=${stdResp?.take(30)?.trim()})")

        val afState = ecuStates[0x0000AF] ?: CapabilityState.UNKNOWN
        Log.d(TAG, "0x0000AF advisory state: $afState")
        if (afState == CapabilityState.SUPPORTED || afState == CapabilityState.UNKNOWN) {
            Log.i(TAG, "Oil temp source → SSM_ECU (0x0000AF) [advisory=$afState]")
            return OilTempSource.SSM_ECU
        }

        Log.d(TAG, "0x0000AF is UNSUPPORTED (advisory) — trying alternate address 0x009D5C")
        val altResp = btManager.sendCommand(capabilityProber.buildSsmA8Single(0x009D5C), SSM_TIMEOUT_MS)
        if (altResp != null && ObdParser.parseSsmResponse(altResp) != null) {
            Log.i(TAG, "Oil temp source → SSM_ECU_ALT (0x009D5C)")
            return OilTempSource.SSM_ECU_ALT
        }
        Log.d(TAG, "0x009D5C: no data (resp=${altResp?.take(30)?.trim()})")

        Log.i(TAG, "Oil temp source → NONE (probe inconclusive for all sources)")
        Log.i(TAG, "OIL_TEMP will still be polled — engine defaults to SSM_ECU (0x0000AF)")
        return OilTempSource.NONE
    }

    // ── Capability state deserialization ──────────────────────────────────────

    /**
     * Reconstructs an advisory [Map<Int, CapabilityState>] from the serialised DataStore string.
     *
     *  - [raw] == null → never probed → all [candidates] assigned UNKNOWN
     *  - [raw] != null → addresses present in [raw] → SUPPORTED; all others → UNSUPPORTED
     *
     * The DataStore format is unchanged (comma-separated hex addresses of SUPPORTED entries).
     */
    private fun parseCapsToStates(raw: String?, candidates: List<Int>): Map<Int, CapabilityState> {
        if (raw == null) {
            Log.d(TAG, "parseCapsToStates: raw=null → all ${candidates.size} candidates UNKNOWN")
            return candidates.associateWith { CapabilityState.UNKNOWN }
        }
        val supported = raw.split(",")
            .mapNotNull { it.trim().takeIf(String::isNotEmpty)?.toIntOrNull(16) }
            .toSet()
        return candidates.associateWith { addr ->
            if (addr in supported) CapabilityState.SUPPORTED else CapabilityState.UNSUPPORTED
        }
    }

    /** Serialises only SUPPORTED addresses to the existing comma-separated hex format. */
    private fun serializeCaps(states: Map<Int, CapabilityState>): String =
        states.entries
            .filter { it.value == CapabilityState.SUPPORTED }
            .joinToString(",") { "%X".format(it.key) }

    // ── Active PID set ────────────────────────────────────────────────────────

    /**
     * Builds the set of PIDs to poll this session from the [SensorRegistry].
     *
     * Every sensor registered in [SensorRegistry] is polled unconditionally.
     * Dashboard visibility and gauge slot configuration have no effect here — they
     * control only which values the UI displays, not which values are collected.
     *
     * The only filter applied is vehicle type: [ObdPid.isTurboOnly] PIDs are excluded
     * for NA engines. Capability probe results remain advisory only.
     */
    private suspend fun buildActivePidSet(probe: ProbeResult): Set<ObdPid> {
        val vehicle = userPreferences.selectedVehicle.first()
        val isTurbo = vehicle?.isTurbo ?: true

        val registeredSensors = sensorRegistry.allSensors()
        val active = registeredSensors
            .filter { !it.obdPid.isTurboOnly || isTurbo }
            .map { it.obdPid }
            .toMutableSet()

        active += _carActivePids.value

        val excluded = registeredSensors.count { it.obdPid.isTurboOnly && !isTurbo }
        Log.i(TAG, "buildActivePidSet from SensorRegistry: ${registeredSensors.size} registered, " +
            "${active.size} active (isTurbo=$isTurbo, $excluded turbo-only excluded)")
        Log.d(TAG, "Oil temp advisory source: ${probe.oilTempSource} " +
            "(0x0000AF=${probe.ecuStates[0x0000AF] ?: CapabilityState.UNKNOWN})")

        val byModule = registeredSensors.filter { !it.obdPid.isTurboOnly || isTurbo }
            .groupBy { it.module }
        Log.d(TAG, "Active by module: ${byModule.entries.joinToString { "${it.key}=${it.value.size}" }}")

        val byPriority = registeredSensors.filter { !it.obdPid.isTurboOnly || isTurbo }
            .groupBy { it.priority }
        Log.d(TAG, "Active by priority: ${byPriority.entries.joinToString { "${it.key}=${it.value.size}" }}")

        Log.d(TAG, "Active PIDs: ${active.joinToString { it.name }}")
        return active
    }

    // ── Regular PID query (standard OBD-II + ECM oil temp) ───────────────────

    private suspend fun queryRegularPid(
        pid: ObdPid,
        probe: ProbeResult,
        profile: AdapterSpeedProfile,
    ): Pair<String?, Float?> {
        val timeout = if (pid.ssmAddress != null) SSM_TIMEOUT_MS else profile.commandTimeoutMs

        if (pid == ObdPids.OIL_TEMP) {
            return when (probe.oilTempSource) {
                OilTempSource.OBD_STANDARD -> {
                    Log.d(TAG, "OIL_TEMP query: OBD_STANDARD (015C)")
                    val resp = btManager.sendCommand("015C", timeout)
                    val v = resp?.let {
                        ObdParser.parseStandard(it, "015C")
                            ?.let { b -> if (b.isNotEmpty()) (b[0] - 40).toFloat() else null }
                    }
                    resp to v
                }
                OilTempSource.SSM_ECU -> {
                    val state = probe.ecuStates[0x0000AF] ?: CapabilityState.UNKNOWN
                    Log.d(TAG, "OIL_TEMP query: SSM_ECU (0x0000AF) [advisory=$state]")
                    val resp = btManager.sendCommand(capabilityProber.buildSsmA8Single(0x0000AF), timeout)
                    resp to resp?.let { parsePid(pid, it) }
                }
                OilTempSource.SSM_ECU_ALT -> {
                    Log.d(TAG, "OIL_TEMP query: SSM_ECU_ALT (0x009D5C)")
                    val resp = btManager.sendCommand(capabilityProber.buildSsmA8Single(0x009D5C), timeout)
                    resp to resp?.let { parsePid(pid, it) }
                }
                OilTempSource.NONE -> {
                    // Probe was inconclusive — default to SSM_ECU (0x0000AF).
                    // If the ECU doesn't support it, the runtime skip tracker handles it
                    // after 3 consecutive NO DATA responses.
                    val state = probe.ecuStates[0x0000AF] ?: CapabilityState.UNKNOWN
                    Log.d(TAG, "OIL_TEMP query: source advisory NONE — " +
                        "defaulting to SSM_ECU (0x0000AF) [advisory=$state]; " +
                        "runtime error tracker will suppress after 3 failures")
                    val resp = btManager.sendCommand(capabilityProber.buildSsmA8Single(0x0000AF), timeout)
                    resp to resp?.let { parsePid(pid, it) }
                }
            }
        }

        val resp = btManager.sendCommand(pid.cmd, timeout)
        return resp to resp?.let { parsePid(pid, it) }
    }

    // ── SSM A8 batch (ECU 7E0 / TCU 7E1) ──────────────────────────────────────

    /**
     * Reads every [pids] entry's SSM address in a single A8 multi-address command (falling back to
     * single reads as needed). When [header] is non-null it is selected first and 7E0 is restored
     * afterwards; pass null when the ECM header (7E0) is already active.
     * Returns true if at least one value was read.
     */
    private suspend fun batchSsm(
        header: String?,
        pids: List<ObdPid>,
        current: MutableMap<String, Float>,
        consecutiveErrors: MutableMap<String, Int>,
        skipCycles: MutableMap<String, Int>,
        profile: AdapterSpeedProfile,
    ): Boolean {
        if (header != null) {
            if (btManager.sendCommand("ATSH$header", profile.commandTimeoutMs) == null) {
                Log.w(TAG, "ATSH$header timed out — skipping SSM batch")
                btManager.sendCommand("ATSH7E0", profile.commandTimeoutMs)
                return false
            }
            delay(SSM_SETTLE_MS)
        }

        val addresses = pids.map { it.ssmAddress!! }
        Log.d(TAG, "batchSsm header=${header ?: "7E0(current)"} addresses=${addresses.size} singleReadMode=$singleReadMode")
        val result = capabilityProber.readSsmBatch(addresses, allowBatch = !singleReadMode)

        if (result.batchFailed && !singleReadMode) {
            singleReadMode = true
            userPreferences.setAdapterSingleRead(true)
            Log.i(TAG, "Adapter can't batch A8 reads — switching to single-read mode (persisted)")
        }

        for (pid in pids) {
            val raw   = result.values[pid.ssmAddress]
            val value = raw?.let { pid.parse(listOf(it)) }
            if (raw != null) {
                Log.d(TAG, "batchSsm ${pid.name} (0x%06X) raw=0x%02X value=$value".format(pid.ssmAddress, raw))
            } else {
                Log.d(TAG, "batchSsm ${pid.name} (0x%06X) → no data".format(pid.ssmAddress!!))
            }
            trackResult(pid, value, current, consecutiveErrors, skipCycles)
        }

        if (header != null) btManager.sendCommand("ATSH7E0", profile.commandTimeoutMs)
        return result.values.isNotEmpty()
    }

    // ── Module batch query (UDS Mode 22, header 7E1/7D4/…) ────────────────────

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
        delay(SSM_SETTLE_MS)

        var anyRead = false
        for (pid in pids) {
            if (!isConnected()) break
            var response = btManager.sendCommand(pid.cmd, SSM_TIMEOUT_MS)
            if (response == null) {
                Log.d(TAG, "Module ${pid.name} timeout — retrying once")
                delay(500L)
                response = btManager.sendCommand(pid.cmd, SSM_TIMEOUT_MS)
            }
            if (response == null) {
                Log.d(TAG, "Module ${pid.name} no response after retry")
                continue
            }
            val value = ObdParser.parseUdsResponse(response, pid.cmd)?.let { pid.parse(it) }
            Log.d(TAG, "Module $header ${pid.name} → value=$value")
            trackResult(pid, value, current, consecutiveErrors, skipCycles)
            anyRead = true
            if (profile.delayBetweenPidsMs > 0L) delay(profile.delayBetweenPidsMs)
        }

        btManager.sendCommand("ATSH7E0", profile.commandTimeoutMs)
        return anyRead
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
        val entry = sensorRegistry.getByPidCmd(pid.cmd)

        if (value != null) {
            consecutiveErrors.remove(pid.cmd)
            current[pid.cmd] = value
            _sensorValues.value = current.toMap()
            entry?.let { sensorRegistry.updateStatus(it.sensorId, SensorStatus.ACTIVE) }
        } else {
            val errors = (consecutiveErrors[pid.cmd] ?: 0) + 1
            consecutiveErrors[pid.cmd] = errors
            entry?.let { sensorRegistry.updateStatus(it.sensorId, SensorStatus.ERROR) }

            if (errors >= 3) {
                val skipFor = when {
                    pid.ssmAddress != null -> {
                        Log.i(TAG, "Sensor ${pid.name} (SSM 0x%06X) not responding after 3 attempts — ".format(pid.ssmAddress) +
                            "suspending for session (runtime, not capability gate)")
                        entry?.let { sensorRegistry.updateStatus(it.sensorId, SensorStatus.UNSUPPORTED) }
                        9999
                    }
                    pid.header != null && pid.header != "7E0" -> {
                        Log.d(TAG, "Sensor ${pid.name} module PID not responding — suspending $MODULE_SKIP_CYCLES cycles")
                        MODULE_SKIP_CYCLES
                    }
                    else -> {
                        Log.d(TAG, "Sensor ${pid.name} not responding — suspending 10 cycles")
                        10
                    }
                }
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

/**
 * Cached result of the first-connect capability + sensor probe.
 *
 * Both [ecuStates] and [tcuStates] are advisory maps — the polling engine
 * logs them and uses them for oil-temp source routing only.
 * They never gate which sensors are included in a poll cycle.
 */
private data class ProbeResult(
    val oilTempSource: OilTempSource,
    val ecuStates: Map<Int, CapabilityState>,
    val tcuStates: Map<Int, CapabilityState>,
)
