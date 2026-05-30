package com.subaru.servicetool.data.obd

import android.util.Log
import com.subaru.servicetool.data.bluetooth.BluetoothConnectionState
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.bluetooth.adapter.AdapterProfileManager
import com.subaru.servicetool.data.obd.discovery.ModuleDiscoveryService
import com.subaru.servicetool.data.obd.polling.AwdPoller
import com.subaru.servicetool.data.obd.polling.CvtPoller
import com.subaru.servicetool.data.obd.polling.EnginePoller
import com.subaru.servicetool.data.obd.polling.TpmsPoller
import com.subaru.servicetool.data.preferences.UserPreferences
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ObdQueryEngine"

/**
 * Orchestrates the independent polling engine.
 *
 * Connection lifecycle:
 *   Connected     → startPolling()   (full setup on first connect; fast restart on reconnect)
 *   Reconnecting  → pausePolling()   (stops pollers, PRESERVES all caches)
 *   Disconnected  → stopPolling()    (stops pollers, clears all caches)
 *   Error         → stopPolling()    (stops pollers, clears all caches)
 *
 * This means a reconnect never re-runs adapter detection, capability probe, or module
 * discovery — pollers restart within ~0.5 s of the link coming back.
 *
 * Pollers run as sibling coroutines inside a [supervisorScope]:
 *   EnginePoller — OBD + ECU sensors (HIGH/MEDIUM/LOW priority queues)
 *   CvtPoller    — TCU CVT sensors (HIGH/MEDIUM queues, header 7E1)
 *   AwdPoller    — AWD Transfer Duty (dedicated HIGH poller, header 7E1)
 *   TpmsPoller   — BCM TPMS sensors (LOW queue, header 7D4)
 *
 * Failure of any poller is logged but does not cancel the others.
 */
@Singleton
class ObdQueryEngine @Inject constructor(
    private val btManager: OBDBluetoothManager,
    private val userPreferences: UserPreferences,
    private val capabilityProber: ObdCapabilityProber,
    private val sensorRegistry: SensorRegistry,
    private val moduleDiscovery: ModuleDiscoveryService,
    private val adapterProfileManager: AdapterProfileManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sensorValues = MutableStateFlow<Map<String, Float>>(emptyMap())
    val sensorValues: StateFlow<Map<String, Float>> = _sensorValues.asStateFlow()

    private val _dtcCount = MutableStateFlow(0)
    val dtcCount: StateFlow<Int> = _dtcCount.asStateFlow()

    private val _detectedSensorCount = MutableStateFlow(0)
    val detectedSensorCount: StateFlow<Int> = _detectedSensorCount.asStateFlow()

    private val _carActivePids = MutableStateFlow<Set<ObdPid>>(emptySet())
    fun setCarActivePids(pids: Set<ObdPid>) { _carActivePids.value = pids }

    /** Live runtime module map — populated after first connect. */
    val discoveredModules get() = moduleDiscovery.modules

    /** Live adapter diagnostics snapshot (RTT, batch tiers, timeouts, retries). */
    val adapterDiagnostics get() = adapterProfileManager.diagnostics.snapshot

    /** Detected adapter type. */
    val adapterType get() = adapterProfileManager.adapterType

    // Guards all pollJob/dtcJob swaps. Lifecycle transitions can arrive concurrently from the
    // connectionState collector and the carActivePids collector (both on Dispatchers.IO), so the
    // cancel-then-reassign must be atomic to avoid leaking or double-running poller coroutines.
    private val lifecycleLock = Any()

    private var pollJob: Job? = null
    private var dtcJob:  Job? = null

    // Capability snapshot — preserved across Reconnecting cycles; cleared only on full disconnect.
    @Volatile private var cachedSnapshot: CapabilitySnapshot? = null

    // Shared mutex for CvtPoller, AwdPoller, TpmsPoller header transactions.
    private val moduleHeaderMutex = Mutex()

    // Poller instances — created once, reused across all sessions.
    private val enginePoller = EnginePoller(
        btManager, capabilityProber, sensorRegistry, _sensorValues,
        profileManager = adapterProfileManager,
    )
    private val cvtPoller = CvtPoller(
        btManager, capabilityProber, sensorRegistry, _sensorValues,
        moduleHeaderMutex, profileManager = adapterProfileManager,
    )
    private val awdPoller = AwdPoller(
        btManager, capabilityProber, sensorRegistry, _sensorValues, moduleHeaderMutex,
    )
    private val tpmsPoller = TpmsPoller(
        btManager, capabilityProber, sensorRegistry, _sensorValues, moduleHeaderMutex,
    )

    init {
        scope.launch {
            btManager.connectionState.collect { state ->
                when (state) {
                    is BluetoothConnectionState.Connected     -> startPolling()
                    // Connecting + Reconnecting are transient — pause pollers but PRESERVE the
                    // capability/module/adapter caches so a reconnect skips the expensive setup.
                    is BluetoothConnectionState.Connecting    -> pausePolling()
                    is BluetoothConnectionState.Reconnecting  -> pausePolling()
                    // Disconnected + Error are terminal — full teardown clears all caches.
                    is BluetoothConnectionState.Disconnected  -> stopPolling()
                    is BluetoothConnectionState.Error         -> stopPolling()
                }
            }
        }
        scope.launch {
            _carActivePids.drop(1).collect { if (pollJob?.isActive == true) startPolling() }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private fun startPolling() = synchronized(lifecycleLock) {
        pollJob?.cancel()
        pollJob = scope.launch {
            val t0 = System.currentTimeMillis()
            val isReconnect = cachedSnapshot != null

            _sensorValues.value = emptyMap()
            Log.i(TAG, "[CONNECT] ${if (isReconnect) "Reconnect" else "First connect"} — starting setup")

            delay(500)
            queryDtcs()

            if (!isReconnect) {
                // ── Full first-connect setup ────────────────────────────────
                sensorRegistry.reset()

                // 1. Adapter detection
                Log.i(TAG, "[DETECT] Identifying adapter hardware")
                val t1 = System.currentTimeMillis()
                try {
                    adapterProfileManager.detect(btManager.lastDeviceName)
                    Log.i(TAG, "[DETECT] ${adapterProfileManager.adapterType.value.displayName} " +
                        "in ${System.currentTimeMillis() - t1}ms — " +
                        "maxBatch=${adapterProfileManager.activeCapabilities.maxBatchAddresses} " +
                        "timeout=${adapterProfileManager.activeCapabilities.defaultTimeoutMs}ms")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "[DETECT] Failed (${e.message}) — using UNKNOWN profile")
                }

                // 2. Capability probe
                Log.i(TAG, "[PROBE] Running capability probe")
                val t2 = System.currentTimeMillis()
                val snapshot = loadOrRunSnapshot()
                cachedSnapshot = snapshot
                _detectedSensorCount.value =
                    snapshot.ecuStates.values.count { it == CapabilityState.SUPPORTED } +
                    snapshot.tcuStates.values.count { it == CapabilityState.SUPPORTED }
                Log.i(TAG, "[PROBE] Done in ${System.currentTimeMillis() - t2}ms — " +
                    "oil=${snapshot.oilTempSource} " +
                    "ecuSupported=${snapshot.ecuStates.values.count { it == CapabilityState.SUPPORTED }} " +
                    "tcuSupported=${snapshot.tcuStates.values.count { it == CapabilityState.SUPPORTED }}")

                // 3. Module discovery — sequential before pollers (header isolation)
                Log.i(TAG, "[DISCOVERY] Running module discovery")
                val t3 = System.currentTimeMillis()
                try {
                    moduleDiscovery.discover()
                    Log.i(TAG, "[DISCOVERY] Done in ${System.currentTimeMillis() - t3}ms")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "[DISCOVERY] Failed (${e.message}) — pollers start anyway")
                }
            } else {
                // ── Reconnect: skip expensive setup, reuse caches ────────────
                val snapshot = cachedSnapshot!!
                Log.i(TAG, "[RECONNECT] Reusing cached snapshot — " +
                    "oil=${snapshot.oilTempSource} " +
                    "ecuSupported=${snapshot.ecuStates.values.count { it == CapabilityState.SUPPORTED }} " +
                    "adapter=${adapterProfileManager.adapterType.value.displayName}")
            }

            val snapshot = cachedSnapshot ?: run {
                Log.e(TAG, "[CONNECT] BUG: cachedSnapshot null after setup — running emergency probe")
                loadOrRunSnapshot().also { cachedSnapshot = it }
            }

            val vehicle = userPreferences.selectedVehicle.first()
            val isTurbo = vehicle?.isTurbo ?: true
            val carPids = _carActivePids.value

            Log.i(TAG, "[POLLERS] Launching — isTurbo=$isTurbo carPids=${carPids.size} " +
                "setupMs=${System.currentTimeMillis() - t0}")

            // supervisorScope: child failures are isolated; cancelled when pollJob is cancelled.
            supervisorScope {
                launch {
                    try {
                        enginePoller.run(snapshot, isTurbo, carPids)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "[POLLERS] EnginePoller crashed: ${e.message}", e)
                    }
                }
                launch {
                    try {
                        cvtPoller.run(snapshot, isTurbo)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "[POLLERS] CvtPoller crashed: ${e.message}", e)
                    }
                }
                launch {
                    try {
                        awdPoller.run(isTurbo)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "[POLLERS] AwdPoller crashed: ${e.message}", e)
                    }
                }
                launch {
                    try {
                        tpmsPoller.run()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "[POLLERS] TpmsPoller crashed: ${e.message}", e)
                    }
                }
            }
        }
    }

    /**
     * Called on [BluetoothConnectionState.Reconnecting].
     * Stops all pollers and clears live data but PRESERVES the capability snapshot,
     * module discovery results, and adapter profile so they are not re-run on reconnect.
     */
    private fun pausePolling() = synchronized(lifecycleLock) {
        pollJob?.cancel(); pollJob = null
        _sensorValues.value = emptyMap()
        _dtcCount.value = 0
        Log.i(TAG, "[RECONNECT] Pollers paused — snapshot/profile/modules preserved")
    }

    /**
     * Called on [BluetoothConnectionState.Disconnected] or [BluetoothConnectionState.Error].
     * Full teardown: cancels pollers and clears ALL caches so the next connection
     * re-runs adapter detection, capability probe, and module discovery.
     */
    private fun stopPolling() = synchronized(lifecycleLock) {
        pollJob?.cancel(); pollJob = null
        dtcJob?.cancel(); dtcJob = null
        _sensorValues.value = emptyMap()
        _dtcCount.value = 0
        cachedSnapshot = null
        sensorRegistry.reset()
        moduleDiscovery.reset()
        adapterProfileManager.reset()
        Log.i(TAG, "[DISCONNECT] Full stop — all caches cleared")
    }

    // ── Capability snapshot ───────────────────────────────────────────────────

    private suspend fun loadOrRunSnapshot(): CapabilitySnapshot {
        cachedSnapshot?.let {
            Log.d(TAG, "[PROBE] In-memory cache hit")
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

            Log.i(TAG, "[PROBE] DataStore cache hit — oil=$src " +
                "ecuSupported=${ecuStates.values.count { it == CapabilityState.SUPPORTED }} " +
                "tcuSupported=${tcuStates.values.count { it == CapabilityState.SUPPORTED }}")
            Log.d(TAG, "[PROBE] ECU: ${ecuStates.entries.joinToString { "0x%06X→%s".format(it.key, it.value) }}")
            Log.d(TAG, "[PROBE] TCU: ${tcuStates.entries.joinToString { "0x%06X→%s".format(it.key, it.value) }}")

            return CapabilitySnapshot(src, ecuStates, tcuStates).also { cachedSnapshot = it }
        }

        Log.i(TAG, "[PROBE] No cache — running live probe (advisory mode, no sensors blocked)")
        return runSnapshot().also {
            cachedSnapshot = it
            userPreferences.saveCapabilities(serializeCaps(it.ecuStates), serializeCaps(it.tcuStates))
            userPreferences.saveSensorProbe(
                it.oilTempSource.name,
                it.tcuStates.values.any { s -> s == CapabilityState.SUPPORTED },
            )
        }
    }

    private suspend fun runSnapshot(): CapabilitySnapshot {
        val ecuStates     = capabilityProber.probeEcuCapabilities()
        val oilTempSource = probeOilTempSource(ecuStates)
        val tcuStates     = capabilityProber.probeTcuCapabilities()
        return CapabilitySnapshot(oilTempSource, ecuStates, tcuStates)
    }

    private suspend fun probeOilTempSource(ecuStates: Map<Int, CapabilityState>): OilTempSource {
        val stdResp = btManager.sendCommand("015C", 2_000L)
        if (stdResp != null && ObdParser.parseStandard(stdResp, "015C")?.isNotEmpty() == true) {
            Log.i(TAG, "[PROBE] Oil temp → OBD_STANDARD (015C)")
            return OilTempSource.OBD_STANDARD
        }

        val afState = ecuStates[0x0000AF] ?: CapabilityState.UNKNOWN
        if (afState == CapabilityState.SUPPORTED || afState == CapabilityState.UNKNOWN) {
            Log.i(TAG, "[PROBE] Oil temp → SSM_ECU (0x0000AF) [advisory=$afState]")
            return OilTempSource.SSM_ECU
        }

        val altResp = btManager.sendCommand(capabilityProber.buildSsmA8Single(0x009D5C), 2_000L)
        if (altResp != null && ObdParser.parseSsmResponse(altResp) != null) {
            Log.i(TAG, "[PROBE] Oil temp → SSM_ECU_ALT (0x009D5C)")
            return OilTempSource.SSM_ECU_ALT
        }

        Log.i(TAG, "[PROBE] Oil temp → NONE — EnginePoller will default to SSM_ECU (advisory)")
        return OilTempSource.NONE
    }

    // ── Capability serialization ──────────────────────────────────────────────

    private fun parseCapsToStates(raw: String?, candidates: List<Int>): Map<Int, CapabilityState> {
        if (raw == null) return candidates.associateWith { CapabilityState.UNKNOWN }
        val supported = raw.split(",")
            .mapNotNull { it.trim().takeIf(String::isNotEmpty)?.toIntOrNull(16) }
            .toSet()
        return candidates.associateWith { addr ->
            if (addr in supported) CapabilityState.SUPPORTED else CapabilityState.UNSUPPORTED
        }
    }

    private fun serializeCaps(states: Map<Int, CapabilityState>): String =
        states.entries
            .filter { it.value == CapabilityState.SUPPORTED }
            .joinToString(",") { "%X".format(it.key) }

    // ── DTC ───────────────────────────────────────────────────────────────────

    fun requestDtcRefresh() = synchronized(lifecycleLock) {
        dtcJob?.cancel()
        dtcJob = scope.launch { queryDtcs() }
    }

    private suspend fun queryDtcs() {
        val response = btManager.sendCommand("03") ?: return
        val count = ObdParser.parseDtcCount(response)
        _dtcCount.value = count
        Log.d(TAG, "[DTC] count=$count")
    }
}
