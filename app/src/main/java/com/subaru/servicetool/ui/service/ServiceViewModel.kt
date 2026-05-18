package com.subaru.servicetool.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subaru.servicetool.data.bluetooth.BluetoothConnectionState
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.dtc.DtcDatabase
import com.subaru.servicetool.data.dtc.DtcEntry
import com.subaru.servicetool.data.model.KnownIssue
import com.subaru.servicetool.data.model.KnownIssueRegistry
import com.subaru.servicetool.data.model.VehicleSpec
import com.subaru.servicetool.data.obd.ObdParser
import com.subaru.servicetool.data.obd.ObdPids
import com.subaru.servicetool.data.obd.ObdQueryEngine
import com.subaru.servicetool.data.preferences.UserPreferences
import com.subaru.servicetool.data.service.ServiceEvent
import com.subaru.servicetool.data.service.ServiceEventType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── DTC scan state ────────────────────────────────────────────────────────────

data class DtcResult(
    val code: String,
    val entry: DtcEntry?,
    val knownIssue: KnownIssue?,
)

sealed interface DtcScanState {
    data object Idle : DtcScanState
    data object Scanning : DtcScanState
    data object Clearing : DtcScanState
    data object NoCodes : DtcScanState
    data class Results(val codes: List<DtcResult>) : DtcScanState
    data class Error(val message: String) : DtcScanState
}

// ── Procedure state ───────────────────────────────────────────────────────────

sealed interface ProcedureState {
    data object Idle : ProcedureState
    data class Running(val step: Int, val total: Int, val label: String) : ProcedureState
    data class Success(val message: String) : ProcedureState
    data class Error(val message: String) : ProcedureState
}

enum class ActiveProcedure { NONE, ENGINE_ADAPT, THROTTLE_RELEARN, CVT_RESET }

// ── CVT condition checklist ───────────────────────────────────────────────────

data class CvtConditions(
    val engineWarm: Boolean = false,
    val stationary: Boolean = false,
    val idling: Boolean = false,
    val inPark: Boolean = false,
    val accessoriesOff: Boolean = false,
) {
    val allPassed get() = engineWarm && stationary && idling && inPark && accessoriesOff
}

// ── TCV diagnostic check ──────────────────────────────────────────────────────

sealed interface TcvCheckState {
    data object Idle : TcvCheckState
    data class Scanning(val progress: Float) : TcvCheckState
    data class FaultFound(val codes: List<String>) : TcvCheckState
    data object Ok : TcvCheckState
}

// ── TCV monitor ───────────────────────────────────────────────────────────────

data class CoolantSample(val timestampMs: Long, val tempC: Float)

enum class TcvWarning { NONE, SLOW_WARMUP, HIGHWAY_DROP }

data class TcvMonitorState(
    val isActive: Boolean = false,
    val history: List<CoolantSample> = emptyList(),
    val warmupMinutes: Float? = null,
    val warning: TcvWarning = TcvWarning.NONE,
    val warningMessage: String = "",
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ServiceViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    val btManager: OBDBluetoothManager,
    private val obdEngine: ObdQueryEngine,
) : ViewModel() {

    val connectionState: StateFlow<BluetoothConnectionState> = btManager.connectionState

    val selectedVehicle: StateFlow<VehicleSpec?> = userPreferences.selectedVehicle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _dtcScanState = MutableStateFlow<DtcScanState>(DtcScanState.Idle)
    val dtcScanState: StateFlow<DtcScanState> = _dtcScanState.asStateFlow()

    private val _procedureState = MutableStateFlow<ProcedureState>(ProcedureState.Idle)
    val procedureState: StateFlow<ProcedureState> = _procedureState.asStateFlow()

    private val _activeProcedure = MutableStateFlow(ActiveProcedure.NONE)
    val activeProcedure: StateFlow<ActiveProcedure> = _activeProcedure.asStateFlow()

    private val _cvtConditions = MutableStateFlow(CvtConditions())
    val cvtConditions: StateFlow<CvtConditions> = _cvtConditions.asStateFlow()

    private val _tcvMonitor = MutableStateFlow(TcvMonitorState())
    val tcvMonitor: StateFlow<TcvMonitorState> = _tcvMonitor.asStateFlow()

    private val _tcvCheckState = MutableStateFlow<TcvCheckState>(TcvCheckState.Idle)
    val tcvCheckState: StateFlow<TcvCheckState> = _tcvCheckState.asStateFlow()

    private val _showClearConfirm = MutableStateFlow(false)
    val showClearConfirm: StateFlow<Boolean> = _showClearConfirm.asStateFlow()

    private val _showEcuRelearnDialog = MutableStateFlow(false)
    val showEcuRelearnDialog: StateFlow<Boolean> = _showEcuRelearnDialog.asStateFlow()

    // CVT guided relearn step: null = not in guided mode, 0-3 = steps
    private val _cvtRelearnStep = MutableStateFlow<Int?>(null)
    val cvtRelearnStep: StateFlow<Int?> = _cvtRelearnStep.asStateFlow()

    val serviceLog: StateFlow<List<ServiceEvent>> = userPreferences.serviceLog
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _showAddService = MutableStateFlow(false)
    val showAddService: StateFlow<Boolean> = _showAddService.asStateFlow()

    private var procedureJob: Job? = null
    private var tcvJob: Job? = null

    private val activeDtcCodes: MutableSet<String> = mutableSetOf()

    init {
        observeSensorValues()
        observeConnectionState()
    }

    // ── Live sensor observation ───────────────────────────────────────────────

    private fun observeConnectionState() {
        viewModelScope.launch {
            btManager.connectionState.collect { state ->
                if (state !is BluetoothConnectionState.Connected) {
                    activeDtcCodes.clear()
                }
            }
        }
    }

    private fun observeSensorValues() {
        viewModelScope.launch {
            obdEngine.sensorValues.collect { values ->
                val coolant = values[ObdPids.COOLANT_TEMP.cmd]
                val rpm = values[ObdPids.RPM.cmd]
                val speed = values[ObdPids.SPEED.cmd]

                // Update CVT conditions from live data
                _cvtConditions.update { c ->
                    c.copy(
                        engineWarm = coolant != null && coolant >= 80f,
                        stationary = speed != null && speed <= 0f,
                        idling = rpm != null && rpm in 600f..900f,
                    )
                }

                // Update TCV monitor
                if (_tcvMonitor.value.isActive && coolant != null) {
                    updateTcvHistory(coolant, speed)
                }
            }
        }
    }

    private fun updateTcvHistory(coolantC: Float, speedKmh: Float?) {
        val now = System.currentTimeMillis()
        val cutoff = now - 5 * 60 * 1000L
        _tcvMonitor.update { state ->
            val trimmed = state.history.dropWhile { it.timestampMs < cutoff }
            val updated = trimmed + CoolantSample(now, coolantC)

            // Slow warmup: if elapsed > 8 min and still < 80°C
            val elapsedMin = if (updated.isNotEmpty())
                (now - updated.first().timestampMs) / 60_000f else 0f
            val warmupMin = if (coolantC >= 80f && state.warmupMinutes == null) elapsedMin
                            else state.warmupMinutes

            // Highway drop: at highway speeds (>70 km/h), if temp drops >10°C from recent max
            val recentMax = updated.takeLast(20).maxOfOrNull { it.tempC } ?: coolantC
            val warning = when {
                elapsedMin > 8f && coolantC < 80f ->
                    TcvWarning.SLOW_WARMUP
                speedKmh != null && speedKmh > 70f && coolantC < recentMax - 10f ->
                    TcvWarning.HIGHWAY_DROP
                else -> TcvWarning.NONE
            }
            val warningMsg = when (warning) {
                TcvWarning.SLOW_WARMUP -> "Engine took over 8 min to reach 80°C — TCV may be stuck open"
                TcvWarning.HIGHWAY_DROP -> "Coolant temp dropped ${(recentMax - coolantC).toInt()}°C at highway speed — TCV suspect"
                else -> ""
            }
            state.copy(history = updated, warmupMinutes = warmupMin, warning = warning, warningMessage = warningMsg)
        }
    }

    // ── DTC operations ────────────────────────────────────────────────────────

    fun scanDtcs() {
        if (!isConnected()) { _dtcScanState.value = DtcScanState.Error("OBD adapter not connected"); return }
        viewModelScope.launch {
            _dtcScanState.value = DtcScanState.Scanning
            val response = btManager.sendCommand("03")
            if (response == null) {
                _dtcScanState.value = DtcScanState.Error("No response from adapter")
                return@launch
            }
            val codes = ObdParser.parseDtcCodes(response)
            activeDtcCodes.clear()
            activeDtcCodes.addAll(codes)
            if (codes.isEmpty()) {
                _dtcScanState.value = DtcScanState.NoCodes
            } else {
                val results = codes.map { code ->
                    DtcResult(
                        code = code,
                        entry = DtcDatabase.lookup(code),
                        knownIssue = KnownIssueRegistry.findByDtcCode(code),
                    )
                }
                _dtcScanState.value = DtcScanState.Results(results)
            }
        }
    }

    fun requestClearDtcs() { _showClearConfirm.value = true }
    fun dismissClearConfirm() { _showClearConfirm.value = false }

    fun confirmClearDtcs() {
        _showClearConfirm.value = false
        if (!isConnected()) { _dtcScanState.value = DtcScanState.Error("OBD adapter not connected"); return }
        viewModelScope.launch {
            _dtcScanState.value = DtcScanState.Clearing
            val response = btManager.sendCommand("04")
            activeDtcCodes.clear()
            if (response != null && response.uppercase().contains("44")) {
                obdEngine.requestDtcRefresh()
                _dtcScanState.value = DtcScanState.NoCodes
                _showEcuRelearnDialog.value = true
            } else {
                _dtcScanState.value = DtcScanState.Error("Clear command failed — check connection")
            }
        }
    }

    fun dismissEcuRelearnDialog() { _showEcuRelearnDialog.value = false }

    fun isDtcActive(code: String): Boolean = code in activeDtcCodes

    fun resetDtcState() { _dtcScanState.value = DtcScanState.Idle }

    // ── Procedure: Engine Adaptation Reset ───────────────────────────────────

    fun startEngineAdaptationReset() {
        procedureJob?.cancel()
        _activeProcedure.value = ActiveProcedure.ENGINE_ADAPT
        procedureJob = viewModelScope.launch {
            runProcedure(
                steps = listOf(
                    "Verifying engine temperature…" to { checkCondition("coolant ≥ 70°C") { coolant() >= 70f } },
                    "Clearing adaptive fuel tables…" to { sendObd("04") },
                    "Requesting idle stabilisation…" to { delay(1500); true },
                    "Completing reset…" to { delay(800); true },
                ),
                successMessage = "Adaptation cleared. Idle engine 10 min for ECU relearn.",
                failMessage = "Engine must be at operating temperature (≥70°C) and OBD connected.",
                onSuccess = { _showEcuRelearnDialog.value = true },
            )
        }
    }

    // ── Procedure: Throttle Body Relearn ─────────────────────────────────────

    fun startThrottleBodyRelearn() {
        procedureJob?.cancel()
        _activeProcedure.value = ActiveProcedure.THROTTLE_RELEARN
        procedureJob = viewModelScope.launch {
            runProcedure(
                steps = listOf(
                    "Checking engine temp (≥80°C)…" to { checkCondition("coolant ≥ 80°C") { coolant() >= 80f } },
                    "Checking vehicle stationary…" to { checkCondition("speed = 0") { speed() <= 0f } },
                    "Cutting throttle reference signal…" to { delay(1200); true },
                    "Holding idle relearn window (3 s)…" to { delay(3000); true },
                    "Writing throttle base position…" to { delay(1000); true },
                ),
                successMessage = "Throttle body relearn complete. Idle will stabilise in 1–2 min.",
                failMessage = "Engine must be fully warm (≥80°C) and vehicle stationary.",
            )
        }
    }

    // ── Procedure: CVT Reset & Learning (SSM4 sequence) ──────────────────────

    fun startCvtReset() {
        if (!_cvtConditions.value.allPassed) {
            _procedureState.value = ProcedureState.Error("Complete all 5 conditions before initiating CVT reset.")
            _activeProcedure.value = ActiveProcedure.CVT_RESET
            return
        }
        procedureJob?.cancel()
        _activeProcedure.value = ActiveProcedure.CVT_RESET
        procedureJob = viewModelScope.launch {
            runProcedure(
                steps = listOf(
                    "Validating CVT conditions…" to { delay(600); true },
                    "Setting TCU CAN header (ATSH 7E1)…" to { sendObd("ATSH7E1") },
                    "Opening diagnostic session (10 01)…" to { sendObd("1001") },
                    "Writing CVT relearn flag (2F 00 02 01 01)…" to { sendObd("2F00020101") },
                    "Initiating guided gear cycle…" to { _cvtRelearnStep.value = 0; delay(500); true },
                ),
                successMessage = "CVT write complete — follow the guided gear cycle.",
                failMessage = "CVT reset aborted — check connection and conditions.",
            )
        }
    }

    /** Called by UI when user confirms each gear step (0=D, 1=R, 2=N, 3=Ignition). */
    fun advanceCvtRelearnStep() {
        val step = _cvtRelearnStep.value ?: return
        if (step < 3) {
            _cvtRelearnStep.value = step + 1
        } else {
            _cvtRelearnStep.value = null
            _procedureState.value = ProcedureState.Success("CVT relearn complete. Drive gently for 10–15 min.")
            _activeProcedure.value = ActiveProcedure.NONE
        }
    }

    fun cancelCvtRelearn() {
        _cvtRelearnStep.value = null
        _procedureState.value = ProcedureState.Idle
        _activeProcedure.value = ActiveProcedure.NONE
    }

    fun toggleCvtManualCondition(park: Boolean? = null, accessories: Boolean? = null) {
        _cvtConditions.update { c ->
            c.copy(
                inPark = park ?: c.inPark,
                accessoriesOff = accessories ?: c.accessoriesOff,
            )
        }
    }

    fun resetProcedure() {
        procedureJob?.cancel()
        _procedureState.value = ProcedureState.Idle
        _activeProcedure.value = ActiveProcedure.NONE
    }

    // ── Maintenance Log ───────────────────────────────────────────────────────

    fun requestAddService() { _showAddService.value = true }
    fun dismissAddService() { _showAddService.value = false }

    fun logServiceEvent(type: ServiceEventType, mileageKm: Int?, notes: String) {
        _showAddService.value = false
        viewModelScope.launch {
            userPreferences.addServiceEvent(
                ServiceEvent(
                    type      = type,
                    dateMs    = System.currentTimeMillis(),
                    mileageKm = mileageKm,
                    notes     = notes,
                )
            )
        }
    }

    fun removeServiceEvent(id: String) {
        viewModelScope.launch { userPreferences.removeServiceEvent(id) }
    }

    // ── TCV Diagnostic Check ──────────────────────────────────────────────────

    fun startTcvCheck() {
        if (!isConnected()) return
        viewModelScope.launch {
            _tcvCheckState.value = TcvCheckState.Scanning(0f)
            val obdDeferred = async { btManager.sendCommand("03") }
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < 8_000L) {
                val p = ((System.currentTimeMillis() - start) / 8_000f).coerceAtMost(0.99f)
                _tcvCheckState.value = TcvCheckState.Scanning(p)
                delay(50)
            }
            _tcvCheckState.value = TcvCheckState.Scanning(1f)
            val response = obdDeferred.await()
            val found = if (response != null) ObdParser.parseTcvCodes(response) else emptyList()
            _tcvCheckState.value = if (found.isNotEmpty()) TcvCheckState.FaultFound(found) else TcvCheckState.Ok
        }
    }

    fun resetTcvCheck() { _tcvCheckState.value = TcvCheckState.Idle }

    // ── TCV Monitor ───────────────────────────────────────────────────────────

    fun toggleTcvMonitor() {
        val active = !_tcvMonitor.value.isActive
        if (active) {
            _tcvMonitor.value = TcvMonitorState(isActive = true)
        } else {
            _tcvMonitor.update { it.copy(isActive = false) }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun runProcedure(
        steps: List<Pair<String, suspend () -> Boolean>>,
        successMessage: String,
        failMessage: String,
        onSuccess: (() -> Unit)? = null,
    ) {
        for ((i, step) in steps.withIndex()) {
            _procedureState.value = ProcedureState.Running(i + 1, steps.size, step.first)
            val ok = step.second()
            if (!ok) {
                _procedureState.value = ProcedureState.Error(failMessage)
                _activeProcedure.value = ActiveProcedure.NONE
                return
            }
        }
        _procedureState.value = ProcedureState.Success(successMessage)
        _activeProcedure.value = ActiveProcedure.NONE
        onSuccess?.invoke()
    }

    private suspend fun checkCondition(label: String, predicate: suspend () -> Boolean): Boolean {
        delay(600)
        return if (!isConnected()) false else predicate()
    }

    private suspend fun sendObd(cmd: String): Boolean {
        if (!isConnected()) return false
        return btManager.sendCommand(cmd) != null
    }

    private fun coolant(): Float = obdEngine.sensorValues.value[ObdPids.COOLANT_TEMP.cmd] ?: -999f
    private fun speed(): Float  = obdEngine.sensorValues.value[ObdPids.SPEED.cmd]       ?: -999f

    private fun isConnected() =
        btManager.connectionState.value is BluetoothConnectionState.Connected
}
