package com.subaru.servicetool.data.obd

import android.util.Log
import com.subaru.servicetool.data.bluetooth.BluetoothConnectionState
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
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
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ObdQueryEngine"

/**
 * Orchestrates the independent polling engine.
 *
 * On each BT connection four specialised pollers are launched as sibling coroutines
 * inside a [supervisorScope]:
 *
 *   EnginePoller — OBD + ECU sensors (HIGH/MEDIUM/LOW priority queues)
 *   CvtPoller    — TCU CVT sensors (HIGH/MEDIUM queues, header 7E1)
 *   AwdPoller    — AWD Transfer Duty (dedicated HIGH poller, header 7E1)
 *   TpmsPoller   — BCM TPMS sensors (LOW queue, header 7D4)
 *
 * Failure of any poller is logged but does not cancel the others.
 *
 * [moduleHeaderMutex] is shared by CvtPoller, AwdPoller, and TpmsPoller to ensure
 * ATSH→read→ATSH7E0 sequences are never interleaved across pollers.
 *
 * [singleReadMode] is an [AtomicBoolean] shared across all pollers; set to true the
 * first time a multi-address A8 batch returns a malformed response.
 */
@Singleton
class ObdQueryEngine @Inject constructor(
    private val btManager: OBDBluetoothManager,
    private val userPreferences: UserPreferences,
    private val capabilityProber: ObdCapabilityProber,
    private val sensorRegistry: SensorRegistry,
    private val moduleDiscovery: ModuleDiscoveryService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sensorValues = MutableStateFlow<Map<String, Float>>(emptyMap())
    val sensorValues: StateFlow<Map<String, Float>> = _sensorValues.asStateFlow()

    private val _dtcCount = MutableStateFlow(0)
    val dtcCount: StateFlow<Int> = _dtcCount.asStateFlow()

    // Advisory count of SSM addresses that responded during the capability probe.
    private val _detectedSensorCount = MutableStateFlow(0)
    val detectedSensorCount: StateFlow<Int> = _detectedSensorCount.asStateFlow()

    private val _carActivePids = MutableStateFlow<Set<ObdPid>>(emptySet())
    fun setCarActivePids(pids: Set<ObdPid>) { _carActivePids.value = pids }

    /** Live runtime module map from [ModuleDiscoveryService]. Populated after first connect. */
    val discoveredModules get() = moduleDiscovery.modules

    private var pollJob: Job? = null
    private var cachedSnapshot: CapabilitySnapshot? = null

    // Shared across all pollers — set true on first A8 batch parse failure.
    private val singleReadMode = AtomicBoolean(false)

    // Shared mutex for any poller that needs to switch the CAN header away from 7E0.
    private val moduleHeaderMutex = Mutex()

    // Called by pollers when batch-read mode fails; persists the flag to DataStore.
    private val onBatchFailed: () -> Unit = {
        singleReadMode.set(true)
        scope.launch { userPreferences.setAdapterSingleRead(true) }
        Log.i(TAG, "A8 batch failed — all pollers switched to single-read mode")
    }

    // Poller instances — created once, reused across connections.
    private val enginePoller = EnginePoller(
        btManager, capabilityProber, sensorRegistry, _sensorValues, singleReadMode, onBatchFailed,
    )
    private val cvtPoller = CvtPoller(
        btManager, capabilityProber, sensorRegistry, _sensorValues, moduleHeaderMutex, singleReadMode, onBatchFailed,
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
                    is BluetoothConnectionState.Connected -> startPolling()
                    else                                  -> stopPolling()
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
            sensorRegistry.reset()

            delay(500)
            queryDtcs()

            singleReadMode.set(userPreferences.adapterSingleRead.first())
            Log.i(TAG, "Polling started — singleReadMode=${singleReadMode.get()}")

            val snapshot = loadOrRunSnapshot()
            _detectedSensorCount.value =
                snapshot.ecuStates.values.count { it == CapabilityState.SUPPORTED } +
                snapshot.tcuStates.values.count { it == CapabilityState.SUPPORTED }
            Log.i(TAG, "Advisory detected sensors: ${_detectedSensorCount.value}")

            // ── Module discovery ── runs before pollers so headers are uncontested ──
            Log.i(TAG, "Running module discovery (sequential, before pollers start)")
            try {
                moduleDiscovery.discover()
            } catch (e: Exception) {
                Log.e(TAG, "Module discovery failed — pollers will start regardless: ${e.message}", e)
            }

            val vehicle = userPreferences.selectedVehicle.first()
            val isTurbo = vehicle?.isTurbo ?: true
            val carPids = _carActivePids.value

            Log.i(TAG, "Launching pollers — isTurbo=$isTurbo carActivePids=${carPids.size}")
            Log.i(TAG, "EnginePoller — OBD + ECU module sensors (HIGH/MEDIUM/LOW queues)")
            Log.i(TAG, "CvtPoller    — TCU CVT sensors (HIGH/MEDIUM queues, ATSH7E1)")
            Log.i(TAG, "AwdPoller    — AWD Transfer Duty (dedicated HIGH poller, ATSH7E1)")
            Log.i(TAG, "TpmsPoller   — BCM TPMS sensors (LOW queue, ATSH7D4)")

            // supervisorScope: child failures are isolated — one crash doesn't cancel others.
            // When pollJob is cancelled (on disconnect), all children are cancelled.
            supervisorScope {
                launch {
                    try {
                        enginePoller.run(snapshot, isTurbo, carPids)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "EnginePoller terminated unexpectedly: ${e.message}", e)
                    }
                }
                launch {
                    try {
                        cvtPoller.run(snapshot, isTurbo)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "CvtPoller terminated unexpectedly: ${e.message}", e)
                    }
                }
                launch {
                    try {
                        awdPoller.run(isTurbo)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "AwdPoller terminated unexpectedly: ${e.message}", e)
                    }
                }
                launch {
                    try {
                        tpmsPoller.run()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "TpmsPoller terminated unexpectedly: ${e.message}", e)
                    }
                }
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        _sensorValues.value = emptyMap()
        _dtcCount.value = 0
        cachedSnapshot = null
        sensorRegistry.reset()
        moduleDiscovery.reset()
        Log.i(TAG, "Polling stopped — all pollers cancelled, registries reset")
    }

    // ── Capability snapshot ───────────────────────────────────────────────────

    private suspend fun loadOrRunSnapshot(): CapabilitySnapshot {
        cachedSnapshot?.let {
            Log.d(TAG, "In-memory snapshot cache hit")
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
            Log.i(TAG, "DataStore snapshot cache hit: oil=$src ecuSupported=$ecuSupported tcuSupported=$tcuSupported")
            Log.d(TAG, "ECU advisory: ${ecuStates.entries.joinToString { "0x%06X→%s".format(it.key, it.value) }}")
            Log.d(TAG, "TCU advisory: ${tcuStates.entries.joinToString { "0x%06X→%s".format(it.key, it.value) }}")

            return CapabilitySnapshot(src, ecuStates, tcuStates).also { cachedSnapshot = it }
        }

        Log.i(TAG, "No snapshot cache — running probe (advisory mode)")
        return runSnapshot().also {
            cachedSnapshot = it
            userPreferences.saveCapabilities(serializeCaps(it.ecuStates), serializeCaps(it.tcuStates))
            userPreferences.saveSensorProbe(
                it.oilTempSource.name,
                it.tcuStates.values.any { s -> s == CapabilityState.SUPPORTED },
            )
            Log.i(TAG, "Snapshot saved: oil=${it.oilTempSource} " +
                "ecuSupported=${it.ecuStates.values.count { s -> s == CapabilityState.SUPPORTED }} " +
                "tcuSupported=${it.tcuStates.values.count { s -> s == CapabilityState.SUPPORTED }}")
        }
    }

    private suspend fun runSnapshot(): CapabilitySnapshot {
        Log.i(TAG, "=== Capability probe START ===")
        val ecuStates     = capabilityProber.probeEcuCapabilities()
        val oilTempSource = probeOilTempSource(ecuStates)
        val tcuStates     = capabilityProber.probeTcuCapabilities()
        Log.i(TAG, "=== Capability probe END: oil=$oilTempSource ===")
        return CapabilitySnapshot(oilTempSource, ecuStates, tcuStates)
    }

    private suspend fun probeOilTempSource(ecuStates: Map<Int, CapabilityState>): OilTempSource {
        Log.d(TAG, "Probing oil temp source...")

        val stdResp = btManager.sendCommand("015C", 2_000L)
        if (stdResp != null && ObdParser.parseStandard(stdResp, "015C")?.isNotEmpty() == true) {
            Log.i(TAG, "Oil temp → OBD_STANDARD (015C)")
            return OilTempSource.OBD_STANDARD
        }

        val afState = ecuStates[0x0000AF] ?: CapabilityState.UNKNOWN
        Log.d(TAG, "0x0000AF advisory=$afState")
        if (afState == CapabilityState.SUPPORTED || afState == CapabilityState.UNKNOWN) {
            Log.i(TAG, "Oil temp → SSM_ECU (0x0000AF) [advisory=$afState]")
            return OilTempSource.SSM_ECU
        }

        val altResp = btManager.sendCommand(capabilityProber.buildSsmA8Single(0x009D5C), 2_000L)
        if (altResp != null && ObdParser.parseSsmResponse(altResp) != null) {
            Log.i(TAG, "Oil temp → SSM_ECU_ALT (0x009D5C)")
            return OilTempSource.SSM_ECU_ALT
        }

        Log.i(TAG, "Oil temp → NONE (inconclusive) — EnginePoller will default to SSM_ECU")
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
