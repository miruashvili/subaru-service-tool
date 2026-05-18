package com.subaru.servicetool.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subaru.servicetool.data.alert.AlertManager
import com.subaru.servicetool.data.alert.TempAlertLevel
import com.subaru.servicetool.data.bluetooth.BluetoothConnectionState
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.model.VehicleSpec
import com.subaru.servicetool.data.obd.ObdPid
import com.subaru.servicetool.data.obd.ObdPids
import com.subaru.servicetool.data.obd.ObdQueryEngine
import com.subaru.servicetool.data.preferences.DisplayUnits
import com.subaru.servicetool.data.preferences.UserPreferences
import com.subaru.servicetool.data.util.UnitConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ObdConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

enum class MetricAlertLevel { NONE, WARNING, CRITICAL }

data class LiveMetric(
    val id: String,
    val cmd: String = "",
    val label: String,
    val value: String,
    val unit: String,
    val iconRes: MetricIcon,
    val highlight: Boolean = false,
    val fraction: Float? = null,
    val alertLevel: MetricAlertLevel = MetricAlertLevel.NONE,
)

enum class MetricIcon { RPM, SPEED, TEMP, THROTTLE, VOLTAGE, INTAKE, FUEL, OIL, AMBIENT, ENGINE_LOAD, MAP, MAF, CVT, AWD }

data class TpmsData(
    val fl: Float? = null,
    val fr: Float? = null,
    val rl: Float? = null,
    val rr: Float? = null,
)

data class FuelConsumptionState(
    val instantL100: Float? = null,
    val averageL100: Float? = null,
    val sampleCount: Int = 0,
    val averageMpg: Float? = null,
    val instantMpg: Float? = null,
    val instantKml: Float? = null,
    val averageKml: Float? = null,
)

data class DashboardUiState(
    val vehicle: VehicleSpec? = null,
    val connectionState: ObdConnectionState = ObdConnectionState.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val metrics: List<LiveMetric> = emptyList(),
    val wideMetrics: List<LiveMetric> = emptyList(),
    val dtcCount: Int = 0,
    val errorMessage: String? = null,
    val ssmFallback: Boolean = false,
    val ambientTemp: Float? = null,
    val fuelConsumption: FuelConsumptionState = FuelConsumptionState(),
    val awdDuty: Float? = null,
    val tpmsData: TpmsData = TpmsData(),
    val displayUnits: DisplayUnits = DisplayUnits(),
)

private data class SlotsUnitsWide(
    val slots: List<String>,
    val units: DisplayUnits,
    val wideSlots: List<String>,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val bluetoothManager: OBDBluetoothManager,
    private val obdEngine: ObdQueryEngine,
    private val alertManager: AlertManager,
) : ViewModel() {

    private val selectedVehicle: StateFlow<VehicleSpec?> = userPreferences.selectedVehicle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val gaugeSlots: StateFlow<List<String>> = userPreferences.gaugeSlots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf("0105", "221017", "010C", "010D"))

    private val allSlotsFlow: Flow<SlotsUnitsWide> = combine(
        gaugeSlots,
        userPreferences.displayUnits,
        userPreferences.wideGaugeSlots,
    ) { slots, units, wideSlots -> SlotsUnitsWide(slots, units, wideSlots) }

    // ── Grid gauge editing ────────────────────────────────────────────────────

    private val _editingSlot = MutableStateFlow<Int?>(null)
    val editingSlot: StateFlow<Int?> = _editingSlot.asStateFlow()

    // ── Wide gauge editing (portrait) ─────────────────────────────────────────

    private val _editingWideSlot = MutableStateFlow<Int?>(null)
    val editingWideSlot: StateFlow<Int?> = _editingWideSlot.asStateFlow()

    // ── Landscape bottom slot editing ─────────────────────────────────────────

    private val _editingLsSlot = MutableStateFlow<Int?>(null)
    val editingLsSlot: StateFlow<Int?> = _editingLsSlot.asStateFlow()

    // ── Fuel consumption accumulator ──────────────────────────────────────────

    private val _fuelSamples = MutableStateFlow<List<Float>>(emptyList())
    private val _fuelResetTs = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            userPreferences.fuelAvgResetTs.collect { _fuelResetTs.value = it }
        }
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { state ->
                if (state !is BluetoothConnectionState.Connected) {
                    _fuelSamples.value = emptyList()
                }
            }
        }
        viewModelScope.launch {
            obdEngine.sensorValues.collect { values ->
                val maf   = values[ObdPids.MAF.cmd]   ?: return@collect
                val speed = values[ObdPids.SPEED.cmd] ?: return@collect
                if (speed < 2f) return@collect
                val instant = (maf * 3600f) / (14.7f * 0.745f * speed * 10f)
                if (instant in 0.1f..99f) {
                    _fuelSamples.update { it + instant }
                }
            }
        }
    }

    private val defaultSlots     = listOf("0105", "221017", "010C", "010D")
    private val defaultWideSlots = listOf("221018", "01C1")

    val uiState: StateFlow<DashboardUiState> = combine(
        selectedVehicle,
        bluetoothManager.connectionState,
        obdEngine.sensorValues,
        obdEngine.dtcCount,
        allSlotsFlow,
    ) { vehicle, btState, sensorValues, dtcCount, allSlots ->
        val obdState  = btState.toObdState()
        val connected = obdState == ObdConnectionState.CONNECTED
        val ssmFallback = vehicle?.ssmSupported == true
        DashboardUiState(
            vehicle             = vehicle,
            connectionState     = obdState,
            connectedDeviceName = (btState as? BluetoothConnectionState.Connected)?.deviceName,
            metrics             = if (connected) sensorValues.toSlotMetrics(allSlots.slots, allSlots.units) else emptySlotMetrics(allSlots.slots),
            wideMetrics         = if (connected) sensorValues.toWideSlotMetrics(allSlots.wideSlots, allSlots.units) else emptyWideSlotMetrics(allSlots.wideSlots),
            dtcCount            = if (connected) dtcCount else 0,
            errorMessage        = (btState as? BluetoothConnectionState.Error)?.message,
            ssmFallback         = connected && ssmFallback,
            ambientTemp         = if (connected) sensorValues[ObdPids.AMBIENT_TEMP.cmd] else null,
            fuelConsumption     = computeFuelConsumption(sensorValues, allSlots.units),
            awdDuty             = if (connected) sensorValues[ObdPids.AWD_DUTY.cmd] else null,
            tpmsData            = if (connected) TpmsData(
                fl = sensorValues[ObdPids.TPMS_FL.cmd],
                fr = sensorValues[ObdPids.TPMS_FR.cmd],
                rl = sensorValues[ObdPids.TPMS_RL.cmd],
                rr = sensorValues[ObdPids.TPMS_RR.cmd],
            ) else TpmsData(),
            displayUnits        = allSlots.units,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardUiState(
            metrics     = emptySlotMetrics(defaultSlots),
            wideMetrics = emptyWideSlotMetrics(defaultWideSlots),
        ),
    )

    // ── Landscape bottom metrics ──────────────────────────────────────────────

    val landscapeBottomLayout: StateFlow<String> = userPreferences.landscapeBottomLayout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "wide")

    val landscapeBottomMetrics: StateFlow<List<LiveMetric>> = combine(
        userPreferences.landscapeBottomLayout,
        userPreferences.landscapeBottomSlots,
        obdEngine.sensorValues,
        userPreferences.displayUnits,
    ) { layout, allLsSlots, sensorValues, units ->
        val slots = if (layout == "wide") allLsSlots.take(2) else allLsSlots.drop(2)
        slots.mapIndexed { i, cmd ->
            val pid    = ObdPids.CONFIGURABLE.find { it.cmd == cmd } ?: ObdPids.RPM
            val rawVal = sensorValues[cmd]
            pidToMetric(i, pid, rawVal, units).copy(id = "ls_$i", cmd = pid.cmd)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun computeFuelConsumption(values: Map<String, Float>, units: DisplayUnits): FuelConsumptionState {
        val maf   = values[ObdPids.MAF.cmd]
        val speed = values[ObdPids.SPEED.cmd]
        val instant = if (maf != null && speed != null && speed >= 2f)
            (maf * 3600f) / (14.7f * 0.745f * speed * 10f)
        else null
        val samples   = _fuelSamples.value
        val avg       = if (samples.size >= 3) samples.average().toFloat() else null
        val fuelUnit  = units.fuelUnit
        return FuelConsumptionState(
            instantL100 = if (fuelUnit == "L100") instant?.takeIf { it in 0.1f..99f } else null,
            averageL100 = if (fuelUnit == "L100") avg?.takeIf { it in 0.1f..99f } else null,
            sampleCount = samples.size,
            instantMpg  = if (fuelUnit == "MPG") instant?.let { if (it > 0f) 235.214f / it else null } else null,
            averageMpg  = if (fuelUnit == "MPG") avg?.let { if (it > 0f) 235.214f / it else null } else null,
            instantKml  = if (fuelUnit == "KML") instant?.let { if (it > 0f) 100f / it else null } else null,
            averageKml  = if (fuelUnit == "KML") avg?.let { if (it > 0f) 100f / it else null } else null,
        )
    }

    // ── Alert banner ──────────────────────────────────────────────────────────

    private val _dismissedLevel = MutableStateFlow<TempAlertLevel?>(null)

    val showAlertBanner: StateFlow<TempAlertLevel> = combine(
        alertManager.alertLevel,
        _dismissedLevel,
    ) { level, dismissed ->
        if (level != TempAlertLevel.NONE && level != dismissed) level else TempAlertLevel.NONE
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TempAlertLevel.NONE)

    fun dismissAlert() { _dismissedLevel.value = alertManager.alertLevel.value }

    init {
        viewModelScope.launch {
            alertManager.alertLevel.collect { level ->
                if (level == TempAlertLevel.NONE) _dismissedLevel.value = null
            }
        }
    }

    // ── Connection-lost snackbar ──────────────────────────────────────────────

    private val _connectionLostVisible = MutableStateFlow(false)
    val connectionLostVisible: StateFlow<Boolean> = _connectionLostVisible.asStateFlow()

    init {
        viewModelScope.launch {
            var prevConnected = false
            bluetoothManager.connectionState.collect { state ->
                val connected = state is BluetoothConnectionState.Connected
                if (prevConnected && !connected) {
                    _connectionLostVisible.value = true
                    delay(4_000L)
                    _connectionLostVisible.value = false
                }
                prevConnected = connected
            }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private val _navigateToBluetooth = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToBluetooth: SharedFlow<Unit> = _navigateToBluetooth.asSharedFlow()

    fun connect() {
        if (bluetoothManager.lastDeviceMac != null) bluetoothManager.reconnectToLastDevice()
        else _navigateToBluetooth.tryEmit(Unit)
    }

    fun disconnect() = bluetoothManager.disconnect()

    // ── Grid gauge editing ────────────────────────────────────────────────────

    fun openGaugeEditor(slot: Int) { _editingSlot.value = slot }
    fun closeGaugeEditor() { _editingSlot.value = null }

    fun setGaugeSlot(slot: Int, pidCmd: String) {
        _editingSlot.value = null
        viewModelScope.launch { userPreferences.setGaugeSlot(slot, pidCmd) }
    }

    // ── Wide gauge editing ────────────────────────────────────────────────────

    fun openWideGaugeEditor(slot: Int) { _editingWideSlot.value = slot }
    fun closeWideGaugeEditor() { _editingWideSlot.value = null }

    fun setWideGaugeSlot(slot: Int, pidCmd: String) {
        _editingWideSlot.value = null
        viewModelScope.launch { userPreferences.setWideGaugeSlot(slot, pidCmd) }
    }

    // ── Landscape bottom slot editing ─────────────────────────────────────────

    fun openLsGaugeEditor(slot: Int) { _editingLsSlot.value = slot }
    fun closeLsGaugeEditor() { _editingLsSlot.value = null }

    fun setLandscapeSlot(slot: Int, pidCmd: String) {
        _editingLsSlot.value = null
        viewModelScope.launch { userPreferences.setLandscapeSlot(slot, pidCmd) }
    }

    // ── Fuel avg reset ────────────────────────────────────────────────────────

    fun resetFuelAvg() {
        _fuelSamples.value = emptyList()
        viewModelScope.launch { userPreferences.resetFuelAvg() }
    }

    // ── Expose configurable PIDs ──────────────────────────────────────────────

    val vehicle: StateFlow<VehicleSpec?> = selectedVehicle

    val configurablePids: List<ObdPid> = ObdPids.CONFIGURABLE

    val configurableWidePids: List<ObdPid> = ObdPids.CONFIGURABLE +
        ObdPids.AWD_DUTY +
        ObdPids.TPMS_FL.copy(name = "TPMS Pressure (All)")

    val currentGaugeSlots: StateFlow<List<String>> = gaugeSlots

    val currentWideGaugeSlots: StateFlow<List<String>> = userPreferences.wideGaugeSlots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf("221018", "01C1"))
}

// ── Extension helpers ─────────────────────────────────────────────────────────

private fun BluetoothConnectionState.toObdState() = when (this) {
    is BluetoothConnectionState.Disconnected -> ObdConnectionState.DISCONNECTED
    is BluetoothConnectionState.Connecting   -> ObdConnectionState.CONNECTING
    is BluetoothConnectionState.Connected    -> ObdConnectionState.CONNECTED
    is BluetoothConnectionState.Reconnecting -> ObdConnectionState.CONNECTING
    is BluetoothConnectionState.Error        -> ObdConnectionState.ERROR
}

private fun Map<String, Float>.toSlotMetrics(slots: List<String>, units: DisplayUnits): List<LiveMetric> =
    slots.mapIndexed { i, cmd ->
        val pid = ObdPids.CONFIGURABLE.find { it.cmd == cmd } ?: ObdPids.RPM
        val rawVal = this[cmd]
        pidToMetric(i, pid, rawVal, units)
    }

private fun Map<String, Float>.toWideSlotMetrics(slots: List<String>, units: DisplayUnits): List<LiveMetric> =
    slots.mapIndexed { i, cmd ->
        val pid = ObdPids.CONFIGURABLE.find { it.cmd == cmd }
            ?: ObdPids.TPMS.find { it.cmd == cmd }
            ?: if (cmd == ObdPids.AWD_DUTY.cmd) ObdPids.AWD_DUTY else null
            ?: ObdPids.RPM
        val rawVal = this[cmd]
        pidToMetric(i, pid, rawVal, units).copy(id = "wide_$i", cmd = pid.cmd)
    }

internal fun emptySlotMetrics(slots: List<String>): List<LiveMetric> =
    slots.mapIndexed { i, cmd ->
        val pid = ObdPids.CONFIGURABLE.find { it.cmd == cmd } ?: ObdPids.RPM
        LiveMetric(
            id      = "slot_$i",
            cmd     = pid.cmd,
            label   = pid.name,
            value   = "--",
            unit    = pid.unit,
            iconRes = pid.toMetricIcon(),
        )
    }

internal fun emptyWideSlotMetrics(slots: List<String>): List<LiveMetric> =
    slots.mapIndexed { i, cmd ->
        val pid = ObdPids.CONFIGURABLE.find { it.cmd == cmd }
            ?: ObdPids.TPMS.find { it.cmd == cmd }
            ?: if (cmd == ObdPids.AWD_DUTY.cmd) ObdPids.AWD_DUTY else null
            ?: ObdPids.RPM
        LiveMetric(
            id      = "wide_$i",
            cmd     = pid.cmd,
            label   = pid.name,
            value   = "--",
            unit    = pid.unit,
            iconRes = pid.toMetricIcon(),
        )
    }

private fun pidToMetric(idx: Int, pid: ObdPid, rawVal: Float?, units: DisplayUnits): LiveMetric {
    val isTemp = pid.unit == "°C"
    val (displayVal, displayUnit) = if (isTemp && rawVal != null) {
        if (units.temperatureUnit == "fahrenheit")
            "%.0f".format(UnitConverter.celsiusToFahrenheit(rawVal.toDouble())) to "°F"
        else
            "%.0f".format(rawVal) to "°C"
    } else {
        (rawVal?.let { formatValue(it, pid) } ?: "--") to pid.unit
    }

    val highlight = when (pid.cmd) {
        ObdPids.RPM.cmd          -> rawVal != null && rawVal > 4000f
        ObdPids.COOLANT_TEMP.cmd -> rawVal != null && rawVal >= 110f
        ObdPids.OIL_TEMP.cmd     -> rawVal != null && rawVal >= 127f
        ObdPids.VOLTAGE.cmd      -> rawVal != null && rawVal < 11.8f
        else -> false
    }
    val alertLevel = when (pid.cmd) {
        ObdPids.COOLANT_TEMP.cmd -> when {
            rawVal != null && rawVal >= 120f -> MetricAlertLevel.CRITICAL
            rawVal != null && rawVal >= 100f -> MetricAlertLevel.WARNING
            else                             -> MetricAlertLevel.NONE
        }
        ObdPids.OIL_TEMP.cmd -> when {
            rawVal != null && rawVal >= 140f -> MetricAlertLevel.CRITICAL
            rawVal != null && rawVal >= 127f -> MetricAlertLevel.WARNING
            else                             -> MetricAlertLevel.NONE
        }
        ObdPids.CVT_TEMP.cmd -> when {
            rawVal != null && rawVal >= 130f -> MetricAlertLevel.CRITICAL
            rawVal != null && rawVal >= 110f -> MetricAlertLevel.WARNING
            else                             -> MetricAlertLevel.NONE
        }
        else -> MetricAlertLevel.NONE
    }
    val fraction = rawVal?.let { ((it - pid.minVal) / (pid.maxVal - pid.minVal)).coerceIn(0f, 1f) }

    return LiveMetric(
        id         = "slot_$idx",
        cmd        = pid.cmd,
        label      = pid.name,
        value      = displayVal,
        unit       = displayUnit,
        iconRes    = pid.toMetricIcon(),
        highlight  = highlight,
        fraction   = fraction,
        alertLevel = alertLevel,
    )
}

private fun formatValue(v: Float, pid: ObdPid): String = when {
    pid.unit == "rpm" -> "%,.0f".format(v)
    pid.unit == "s"   -> "%d".format(v.toLong())
    else              -> "%.1f".format(v).trimEnd('0').trimEnd('.')
        .let { if (it.isEmpty() || it == "-") "0" else it }
}

private fun ObdPid.toMetricIcon(): MetricIcon = when (cmd) {
    ObdPids.RPM.cmd          -> MetricIcon.RPM
    ObdPids.SPEED.cmd        -> MetricIcon.SPEED
    ObdPids.COOLANT_TEMP.cmd -> MetricIcon.TEMP
    ObdPids.THROTTLE.cmd     -> MetricIcon.THROTTLE
    ObdPids.VOLTAGE.cmd      -> MetricIcon.VOLTAGE
    ObdPids.INTAKE_TEMP.cmd  -> MetricIcon.INTAKE
    ObdPids.AMBIENT_TEMP.cmd -> MetricIcon.AMBIENT
    ObdPids.FUEL_LEVEL.cmd   -> MetricIcon.FUEL
    ObdPids.OIL_TEMP.cmd     -> MetricIcon.OIL
    ObdPids.CVT_TEMP.cmd     -> MetricIcon.CVT
    ObdPids.AWD_DUTY.cmd     -> MetricIcon.AWD
    ObdPids.ENGINE_LOAD.cmd  -> MetricIcon.ENGINE_LOAD
    ObdPids.MAP.cmd          -> MetricIcon.MAP
    ObdPids.MAF.cmd          -> MetricIcon.MAF
    else                     -> MetricIcon.RPM
}

internal fun emptyMetrics() = listOf(
    LiveMetric("rpm",      "rpm",      "Engine RPM",    "--", "rpm", MetricIcon.RPM),
    LiveMetric("speed",    "010D",     "Vehicle Speed", "--", "km/h", MetricIcon.SPEED),
    LiveMetric("coolant",  "0105",     "Coolant Temp",  "--", "°C",  MetricIcon.TEMP),
    LiveMetric("throttle", "0111",     "Throttle Pos",  "--", "%",   MetricIcon.THROTTLE),
)
