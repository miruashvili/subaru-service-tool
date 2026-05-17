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

data class LiveMetric(
    val id: String,
    val label: String,
    val value: String,
    val unit: String,
    val iconRes: MetricIcon,
    val highlight: Boolean = false,
    val fraction: Float? = null,  // 0..1 for arc gauge, null when no data
)

enum class MetricIcon { RPM, SPEED, TEMP, THROTTLE, VOLTAGE, INTAKE, FUEL, OIL, AMBIENT, ENGINE_LOAD, MAP, MAF }

data class FuelConsumptionState(
    val instantL100: Float? = null,
    val averageL100: Float? = null,
    val sampleCount: Int = 0,
    val averageMpg: Float? = null,
    val instantMpg: Float? = null,
)

data class DashboardUiState(
    val vehicle: VehicleSpec? = null,
    val connectionState: ObdConnectionState = ObdConnectionState.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val metrics: List<LiveMetric> = emptyList(),
    val dtcCount: Int = 0,
    val errorMessage: String? = null,
    val ssmFallback: Boolean = false,
    val ambientTemp: Float? = null,
    val fuelConsumption: FuelConsumptionState = FuelConsumptionState(),
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf("010C", "010D", "0105", "0111"))

    private val _editingSlot = MutableStateFlow<Int?>(null)
    val editingSlot: StateFlow<Int?> = _editingSlot.asStateFlow()

    // ── Fuel consumption accumulator ──────────────────────────────────────────

    private val _fuelSamples = MutableStateFlow<List<Float>>(emptyList())
    private val _fuelResetTs = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            userPreferences.fuelAvgResetTs.collect { _fuelResetTs.value = it }
        }
        // Clear fuel samples on disconnect
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { state ->
                if (state !is BluetoothConnectionState.Connected) {
                    _fuelSamples.value = emptyList()
                }
            }
        }
        // Accumulate fuel samples
        viewModelScope.launch {
            obdEngine.sensorValues.collect { values ->
                val maf   = values[ObdPids.MAF.cmd]   ?: return@collect
                val speed = values[ObdPids.SPEED.cmd] ?: return@collect
                if (speed < 2f) return@collect  // ignore at near-zero speed
                val instant = (maf * 3600f) / (14.7f * 0.745f * speed * 10f)
                if (instant in 0.1f..99f) {
                    _fuelSamples.update { it + instant }
                }
            }
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        selectedVehicle,
        bluetoothManager.connectionState,
        obdEngine.sensorValues,
        obdEngine.dtcCount,
        userPreferences.displayUnits,
    ) { vehicle, btState, sensorValues, dtcCount, units ->
        val obdState  = btState.toObdState()
        val connected = obdState == ObdConnectionState.CONNECTED
        val ssmFallback = vehicle?.ssmSupported == true
        val slots = gaugeSlots.value
        DashboardUiState(
            vehicle             = vehicle,
            connectionState     = obdState,
            connectedDeviceName = (btState as? BluetoothConnectionState.Connected)?.deviceName,
            metrics             = if (connected) sensorValues.toSlotMetrics(slots, units) else emptySlotMetrics(slots),
            dtcCount            = if (connected) dtcCount else 0,
            errorMessage        = (btState as? BluetoothConnectionState.Error)?.message,
            ssmFallback         = connected && ssmFallback,
            ambientTemp         = if (connected) sensorValues[ObdPids.AMBIENT_TEMP.cmd] else null,
            fuelConsumption     = computeFuelConsumption(sensorValues, units),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private fun computeFuelConsumption(values: Map<String, Float>, units: DisplayUnits): FuelConsumptionState {
        val maf   = values[ObdPids.MAF.cmd]
        val speed = values[ObdPids.SPEED.cmd]
        val instant = if (maf != null && speed != null && speed >= 2f)
            (maf * 3600f) / (14.7f * 0.745f * speed * 10f)
        else null
        val samples = _fuelSamples.value
        val avg = if (samples.size >= 3) samples.average().toFloat() else null
        val usePsi = units.pressureUnit == "psi"
        return FuelConsumptionState(
            instantL100  = instant?.takeIf { it in 0.1f..99f },
            averageL100  = avg?.takeIf { it in 0.1f..99f },
            sampleCount  = samples.size,
            instantMpg   = instant?.let { if (usePsi && it > 0f) 235.214f / it else null },
            averageMpg   = avg?.let { if (usePsi && it > 0f) 235.214f / it else null },
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

    // ── Gauge editing ─────────────────────────────────────────────────────────

    fun openGaugeEditor(slot: Int) { _editingSlot.value = slot }
    fun closeGaugeEditor() { _editingSlot.value = null }

    fun setGaugeSlot(slot: Int, pidCmd: String) {
        _editingSlot.value = null
        viewModelScope.launch { userPreferences.setGaugeSlot(slot, pidCmd) }
    }

    // ── Fuel avg reset ────────────────────────────────────────────────────────

    fun resetFuelAvg() {
        _fuelSamples.value = emptyList()
        viewModelScope.launch { userPreferences.resetFuelAvg() }
    }

    // ── Expose configurable PIDs ──────────────────────────────────────────────

    val vehicle: StateFlow<VehicleSpec?> = selectedVehicle

    val configurablePids: List<ObdPid> = ObdPids.CONFIGURABLE

    val currentGaugeSlots: StateFlow<List<String>> = gaugeSlots
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

internal fun emptySlotMetrics(slots: List<String>): List<LiveMetric> =
    slots.mapIndexed { i, cmd ->
        val pid = ObdPids.CONFIGURABLE.find { it.cmd == cmd } ?: ObdPids.RPM
        LiveMetric(
            id       = "slot_$i",
            label    = pid.name,
            value    = "--",
            unit     = pid.unit,
            iconRes  = pid.toMetricIcon(),
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
    val fraction = rawVal?.let { ((it - pid.minVal) / (pid.maxVal - pid.minVal)).coerceIn(0f, 1f) }

    return LiveMetric(
        id        = "slot_$idx",
        label     = pid.name,
        value     = displayVal,
        unit      = displayUnit,
        iconRes   = pid.toMetricIcon(),
        highlight = highlight,
        fraction  = fraction,
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
    ObdPids.ENGINE_LOAD.cmd  -> MetricIcon.ENGINE_LOAD
    ObdPids.MAP.cmd          -> MetricIcon.MAP
    ObdPids.MAF.cmd          -> MetricIcon.MAF
    else                     -> MetricIcon.RPM
}

internal fun emptyMetrics() = listOf(
    LiveMetric("rpm",      "Engine RPM",    "--", "rpm", MetricIcon.RPM),
    LiveMetric("speed",    "Vehicle Speed", "--", "km/h", MetricIcon.SPEED),
    LiveMetric("coolant",  "Coolant Temp",  "--", "°C",  MetricIcon.TEMP),
    LiveMetric("throttle", "Throttle Pos",  "--", "%",   MetricIcon.THROTTLE),
)
