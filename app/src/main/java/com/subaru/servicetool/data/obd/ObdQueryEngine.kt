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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sensorValues = MutableStateFlow<Map<String, Float>>(emptyMap())
    val sensorValues: StateFlow<Map<String, Float>> = _sensorValues.asStateFlow()

    private val _dtcCount = MutableStateFlow(0)
    val dtcCount: StateFlow<Int> = _dtcCount.asStateFlow()

    // Number of SSM sensors detected by the capability probe (ECU + TCU).
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
            val probeResult = loadOrRunProbe()
            _detectedSensorCount.value = probeResult.ecuCaps.size + probeResult.tcuCaps.size
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

                val due = t1 + t2 + t3 + t4

                // ── Classify the due PIDs ────────────────────────────────────
                // Standard OBD-II + ECM oil-temp (handled individually).
                val individualDue = due.filter {
                    (it.header == null || it.header == "7E0") &&
                    (it.ssmAddress == null || it == ObdPids.OIL_TEMP)
                }
                // ECU SSM addresses — read together in one A8 batch (gated by capability probe).
                val ecuSsmDue = due.filter {
                    it.header == "7E0" && it.ssmAddress != null && it != ObdPids.OIL_TEMP &&
                    isEcuAllowed(it.ssmAddress!!, probeResult)
                }
                // TCU SSM addresses — A8 batch against header 7E1.
                val tcuSsmDue = due.filter {
                    it.header == "7E1" && it.ssmAddress != null &&
                    isTcuAllowed(it.ssmAddress!!, probeResult)
                }
                // Remaining module PIDs (UDS Mode 22, header 7E1/7D4/…) — per-PID batch.
                val moduleGroups = due
                    .filter { pid ->
                        pid.header != null && pid.header != "7E0" && pid.ssmAddress == null &&
                        (moduleSkipCycles[pid.header] ?: 0) == 0
                    }
                    .groupBy { it.header!! }

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
                            val fallback = btManager.sendCommand("0168", profile.commandTimeoutMs)
                            if (fallback != null) {
                                resolvedValue = ObdParser.parseStandard(fallback, "0168")
                                    ?.let { bytes -> if (bytes.size >= 2) (bytes[1] - 40).toFloat() else null }
                            }
                        }
                        trackResult(pid, resolvedValue, current, consecutiveErrors, skipCycles)
                    }
                    if (profile.delayBetweenPidsMs > 0L) delay(profile.delayBetweenPidsMs)
                }

                // ── ECU SSM batch (header 7E0, already selected) ─────────────
                if (ecuSsmDue.isNotEmpty() && isConnected()) {
                    val ok = batchSsm(null, ecuSsmDue, current, consecutiveErrors, skipCycles, profile)
                    consecutiveTimeouts = if (ok) 0 else consecutiveTimeouts + 1
                }

                // ── TCU SSM batch (header 7E1, restore 7E0) ──────────────────
                if (tcuSsmDue.isNotEmpty() && isConnected() &&
                    (moduleSkipCycles["7E1"] ?: 0) == 0) {
                    val ok = batchSsm("7E1", tcuSsmDue, current, consecutiveErrors, skipCycles, profile)
                    if (!ok) registerModuleFailure("7E1", moduleFailures, moduleSkipCycles)
                    else moduleFailures.remove("7E1")
                }

                // ── Remaining module batches (UDS Mode 22) ───────────────────
                for ((header, pids) in moduleGroups) {
                    if (pids.isEmpty() || !isConnected()) continue
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

    private fun registerModuleFailure(
        header: String,
        moduleFailures: MutableMap<String, Int>,
        moduleSkipCycles: MutableMap<String, Int>,
    ) {
        val fails = (moduleFailures[header] ?: 0) + 1
        moduleFailures[header] = fails
        if (fails >= MODULE_FAIL_THRESHOLD) {
            Log.w(TAG, "Module $header failed $fails times — skipping for $MODULE_SKIP_CYCLES cycles")
            moduleSkipCycles[header] = MODULE_SKIP_CYCLES
        }
    }

    // ── Capability + sensor probe ─────────────────────────────────────────────

    private suspend fun loadOrRunProbe(): ProbeResult {
        cachedProbe?.let { return it }

        // null = never probed on this install; non-null (incl. "") = cached result.
        val storedCaps = userPreferences.ecuCaps.first()
        if (storedCaps != null) {
            val ecu = parseCaps(storedCaps)
            val tcu = parseCaps(userPreferences.tcuCaps.first() ?: "")
            val src = runCatching {
                OilTempSource.valueOf(userPreferences.probeOilTempSource.first())
            }.getOrDefault(OilTempSource.NONE)
            Log.d(TAG, "Probe cache hit: oil=$src ecuCaps=${ecu.size} tcuCaps=${tcu.size}")
            return ProbeResult(src, ecu, tcu).also { cachedProbe = it }
        }

        return runProbe().also {
            cachedProbe = it
            userPreferences.saveCapabilities(serializeCaps(it.ecuCaps), serializeCaps(it.tcuCaps))
            userPreferences.saveSensorProbe(it.oilTempSource.name, it.tcuCaps.isNotEmpty())
            Log.i(TAG, "Probe complete: oil=${it.oilTempSource} ecuCaps=${it.ecuCaps.size} tcuCaps=${it.tcuCaps.size}")
        }
    }

    private suspend fun runProbe(): ProbeResult {
        // 1. ECU capability bitmap (leaves header on 7E0)
        val ecuCaps = capabilityProber.probeEcuCapabilities()
        // 2. Oil-temp physical source (depends on ECU caps for the SSM_ECU case)
        val oilTempSource = probeOilTempSource(ecuCaps)
        // 3. TCU capability bitmap (restores header to 7E0)
        val tcuCaps = capabilityProber.probeTcuCapabilities()
        return ProbeResult(oilTempSource, ecuCaps, tcuCaps)
    }

    /** Resolves where engine oil temperature actually comes from on this vehicle. */
    private suspend fun probeOilTempSource(ecuCaps: Set<Int>): OilTempSource {
        // Standard OBD-II Mode 01 PID 5C takes priority when present.
        val stdResp = btManager.sendCommand("015C", SSM_TIMEOUT_MS)
        if (stdResp != null && ObdParser.parseStandard(stdResp, "015C")?.isNotEmpty() == true) {
            Log.d(TAG, "Oil temp source → OBD_STANDARD (015C)")
            return OilTempSource.OBD_STANDARD
        }
        // SSM address 0x0000AF — already probed in the capability sweep.
        if (0x0000AF in ecuCaps) {
            Log.d(TAG, "Oil temp source → SSM_ECU (0x0000AF)")
            return OilTempSource.SSM_ECU
        }
        // SSM alternate address 0x009D5C (not in the candidate sweep).
        val altResp = btManager.sendCommand(capabilityProber.buildSsmA8Single(0x009D5C), SSM_TIMEOUT_MS)
        if (altResp != null && ObdParser.parseSsmResponse(altResp) != null) {
            Log.d(TAG, "Oil temp source → SSM_ECU_ALT (0x009D5C)")
            return OilTempSource.SSM_ECU_ALT
        }
        return OilTempSource.NONE
    }

    private fun isEcuAllowed(address: Int, probe: ProbeResult): Boolean =
        probe.ecuCaps.isEmpty() || address in probe.ecuCaps

    private fun isTcuAllowed(address: Int, probe: ProbeResult): Boolean =
        probe.tcuCaps.isNotEmpty() && address in probe.tcuCaps

    private fun parseCaps(raw: String): Set<Int> =
        raw.split(",").mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() }?.toIntOrNull(16) }.toSet()

    private fun serializeCaps(caps: Set<Int>): String =
        caps.joinToString(",") { "%X".format(it) }

    // ── Active PID set ────────────────────────────────────────────────────────

    private suspend fun buildActivePidSet(probe: ProbeResult): Set<ObdPid> {
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
        val tcuAvailable = probe.tcuCaps.isNotEmpty()

        val active = mutableSetOf<ObdPid>()

        active += ObdPids.TIER1
        active += ObdPids.AMBIENT_TEMP
        active += ObdPids.MAF
        active += ObdPids.INTAKE_TEMP

        if (probe.oilTempSource != OilTempSource.NONE) active += ObdPids.OIL_TEMP
        if (tcuAvailable) {
            active += ObdPids.CVT_TEMP
            active += ObdPids.AWD_DUTY
        }

        if ("TPMS_ALL" in allCmds) active += ObdPids.TIER4

        val candidates = (ObdPids.TIER2 + ObdPids.TIER3 + ObdPids.TIER4)
            .filter { !it.isTurboOnly || isTurbo }
            .filter { tcuAvailable || it.header != "7E1" }
        for (pid in candidates) {
            if (pid.cmd in allCmds) active += pid
        }

        active += _carActivePids.value

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
                    val resp = btManager.sendCommand("015C", timeout)
                    val v = resp?.let {
                        ObdParser.parseStandard(it, "015C")
                            ?.let { b -> if (b.isNotEmpty()) (b[0] - 40).toFloat() else null }
                    }
                    resp to v
                }
                OilTempSource.SSM_ECU -> {
                    val resp = btManager.sendCommand(capabilityProber.buildSsmA8Single(0x0000AF), timeout)
                    resp to resp?.let { parsePid(pid, it) }
                }
                OilTempSource.SSM_ECU_ALT -> {
                    val resp = btManager.sendCommand(capabilityProber.buildSsmA8Single(0x009D5C), timeout)
                    resp to resp?.let { parsePid(pid, it) }
                }
                OilTempSource.NONE -> null to null
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
        val result = capabilityProber.readSsmBatch(addresses, allowBatch = !singleReadMode)

        if (result.batchFailed && !singleReadMode) {
            singleReadMode = true
            userPreferences.setAdapterSingleRead(true)
            Log.i(TAG, "Adapter can't batch A8 reads — switching to single-read mode")
        }

        for (pid in pids) {
            val raw = result.values[pid.ssmAddress]
            val value = raw?.let { pid.parse(listOf(it)) }
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
                delay(500L)
                response = btManager.sendCommand(pid.cmd, SSM_TIMEOUT_MS)
            }
            if (response == null) continue
            val value = ObdParser.parseUdsResponse(response, pid.cmd)?.let { pid.parse(it) }
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

/** Cached result of the first-connect capability + sensor probe. */
private data class ProbeResult(
    val oilTempSource: OilTempSource,
    val ecuCaps: Set<Int>,
    val tcuCaps: Set<Int>,
)
