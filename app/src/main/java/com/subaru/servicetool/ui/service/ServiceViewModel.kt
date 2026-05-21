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
    data class Scanning(val message: String = "Reading fault codes…", val progress: Float = 0f) : DtcScanState
    data class Clearing(val message: String = "Clearing fault codes…", val progress: Float = 0f) : DtcScanState
    data object NoCodes : DtcScanState
    data class Results(val codes: List<DtcResult>) : DtcScanState
    data class Error(val message: String) : DtcScanState
}

private data class DiagModule(val name: String, val header: String)

private val DIAG_MODULES = listOf(
    DiagModule("Engine (ECM)",       "7E0"),
    DiagModule("Transmission (TCM)", "7E1"),
    DiagModule("ABS / VDC",          "7E2"),
    DiagModule("Airbag (SRS)",       "7E3"),
    DiagModule("Body Control (BCM)", "7D4"),
)

// ── Procedure state ───────────────────────────────────────────────────────────

sealed interface ProcedureState {
    data object Idle : ProcedureState
    data class Running(val step: Int, val total: Int, val label: String) : ProcedureState
    data class Success(val message: String) : ProcedureState
    data class Error(val message: String) : ProcedureState
}

enum class ActiveProcedure { NONE, GLOBAL_SWEEP }

// ── Per-issue active OBD check state ─────────────────────────────────────────

sealed interface IssueCheckState {
    data object Idle : IssueCheckState
    data class Scanning(val progress: Float) : IssueCheckState
    data class FaultFound(val codes: List<String>) : IssueCheckState
    data object Ok : IssueCheckState
}

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

    private val _issueCheckStates = MutableStateFlow<Map<String, IssueCheckState>>(emptyMap())
    val issueCheckStates: StateFlow<Map<String, IssueCheckState>> = _issueCheckStates.asStateFlow()

    private val _showClearConfirm = MutableStateFlow(false)
    val showClearConfirm: StateFlow<Boolean> = _showClearConfirm.asStateFlow()

    private val _showEcuRelearnDialog = MutableStateFlow(false)
    val showEcuRelearnDialog: StateFlow<Boolean> = _showEcuRelearnDialog.asStateFlow()

    val serviceLog: StateFlow<List<ServiceEvent>> = userPreferences.serviceLog
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _showAddService = MutableStateFlow(false)
    val showAddService: StateFlow<Boolean> = _showAddService.asStateFlow()

    private var procedureJob: Job? = null

    private val activeDtcCodes: MutableSet<String> = mutableSetOf()

    init {
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

    // ── DTC operations ────────────────────────────────────────────────────────

    fun scanDtcs() {
        if (!isConnected()) { _dtcScanState.value = DtcScanState.Error("OBD adapter not connected"); return }
        viewModelScope.launch {
            val allCodes = mutableListOf<String>()
            val total = DIAG_MODULES.size

            for ((idx, module) in DIAG_MODULES.withIndex()) {
                _dtcScanState.value = DtcScanState.Scanning(
                    message  = "Scanning ${module.name}…",
                    progress = idx.toFloat() / total,
                )

                // Switch CAN header to this module
                btManager.sendCommand("ATSH${module.header}", 2000L)
                delay(150)

                // Mode 03 — stored DTCs (supported by all ISO 15765 modules)
                val r03 = btManager.sendCommand("03", 5000L)
                val codes03 = if (r03 != null) ObdParser.parseDtcCodes(r03) else emptyList()

                if (codes03.isNotEmpty()) {
                    allCodes += codes03
                } else {
                    // UDS 19 02 0D fallback — confirmed faults on modules that don't respond to Mode 03
                    val rUds = btManager.sendCommand("19020D", 5000L)
                    if (rUds != null) allCodes += ObdParser.parseUdsDtcResponse(rUds)
                }

                // ECM only: also query pending (07) and permanent (0A) DTCs
                if (module.header == "7E0") {
                    val r07 = btManager.sendCommand("07", 5000L)
                    val r0A = btManager.sendCommand("0A", 5000L)
                    if (r07 != null) allCodes += ObdParser.parseDtcCodes(r07)
                    if (r0A != null) allCodes += ObdParser.parseDtcCodes(r0A)
                }
            }

            // Restore ECM header for normal polling
            btManager.sendCommand("ATSH7E0", 2000L)

            _dtcScanState.value = DtcScanState.Scanning("Scan complete", 1f)
            delay(300)

            val codes = allCodes.distinct()
            activeDtcCodes.clear()
            activeDtcCodes.addAll(codes)

            if (codes.isEmpty()) {
                _dtcScanState.value = DtcScanState.NoCodes
            } else {
                val results = codes.map { code ->
                    DtcResult(
                        code       = code,
                        entry      = DtcDatabase.lookup(code),
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
            var anyCleared = false
            val total = DIAG_MODULES.size

            for ((idx, module) in DIAG_MODULES.withIndex()) {
                _dtcScanState.value = DtcScanState.Clearing(
                    message  = "Clearing ${module.name}…",
                    progress = idx.toFloat() / total,
                )

                // Switch CAN header
                btManager.sendCommand("ATSH${module.header}", 2000L)
                delay(150)

                // OBD-II Mode 04 — universal clear on all ISO 15765 modules
                val r04 = btManager.sendCommand("04", 3000L)
                if (r04 != null && r04.uppercase().contains("44")) anyCleared = true

                // UDS Service 14 — ClearDTCInformation (group-of-DTC = FF FF FF = all)
                btManager.sendCommand("14FFFFFF", 3000L)

                delay(150) // EEPROM cycle time between modules
            }

            // Restore ECM header for normal polling
            btManager.sendCommand("ATSH7E0", 2000L)

            _dtcScanState.value = DtcScanState.Clearing("Clear complete", 1f)
            delay(400)

            activeDtcCodes.clear()
            obdEngine.requestDtcRefresh()

            if (anyCleared) {
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

    // ── Procedure: Global ECU Error Sweep & Reset ────────────────────────────

    fun startGlobalEcuSweep() {
        if (!isConnected()) {
            _procedureState.value = ProcedureState.Error("OBD adapter not connected.")
            _activeProcedure.value = ActiveProcedure.GLOBAL_SWEEP
            return
        }
        procedureJob?.cancel()
        _activeProcedure.value = ActiveProcedure.GLOBAL_SWEEP
        procedureJob = viewModelScope.launch {
            val total = DIAG_MODULES.size
            for ((idx, module) in DIAG_MODULES.withIndex()) {
                _procedureState.value = ProcedureState.Running(
                    step  = idx + 1,
                    total = total,
                    label = "Resetting ${module.name}…",
                )
                btManager.sendCommand("ATSH${module.header}", 2000L)
                delay(150)
                val r04 = btManager.sendCommand("04", 3000L)
                if (r04 == null || !r04.uppercase().contains("44")) {
                    btManager.sendCommand("14FFFFFF", 3000L)
                }
                delay(150)
            }
            btManager.sendCommand("ATSH7E0", 2000L)
            _procedureState.value = ProcedureState.Success(
                "All 5 modules reset. Drive gently — ECU will relearn adaptive parameters over the next drive cycle."
            )
            _activeProcedure.value = ActiveProcedure.NONE
            _showEcuRelearnDialog.value = true
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

    // ── Per-issue active OBD check ────────────────────────────────────────────

    fun startIssueCheck(issue: KnownIssue) {
        val header = issue.checkHeader ?: return
        if (!isConnected()) return
        viewModelScope.launch {
            _issueCheckStates.update { it + (issue.id to IssueCheckState.Scanning(0f)) }
            btManager.sendCommand("ATSH$header", 2000L)
            delay(150)
            val queryJob = async { btManager.sendCommand("03", 6000L) }
            val scanStart = System.currentTimeMillis()
            while (!queryJob.isCompleted) {
                val p = ((System.currentTimeMillis() - scanStart) / 6000f).coerceAtMost(0.95f)
                _issueCheckStates.update { it + (issue.id to IssueCheckState.Scanning(p)) }
                delay(80)
            }
            val response = queryJob.await()
            val allCodes = if (response != null) ObdParser.parseDtcCodes(response) else emptyList()
            val matching = allCodes.filter { it in issue.dtcCodes }
            btManager.sendCommand("ATSH7E0", 2000L)
            _issueCheckStates.update { it + (issue.id to
                if (matching.isNotEmpty()) IssueCheckState.FaultFound(matching) else IssueCheckState.Ok
            )}
        }
    }

    fun resetIssueCheck(issueId: String) {
        _issueCheckStates.update { it - issueId }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isConnected() =
        btManager.connectionState.value is BluetoothConnectionState.Connected
}
