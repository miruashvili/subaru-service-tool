package com.subaru.servicetool.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subaru.servicetool.data.alert.AlertManager
import com.subaru.servicetool.data.alert.TempAlertLevel
import com.subaru.servicetool.data.bluetooth.BluetoothConnectionState
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.model.VehicleSpec
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

enum class MetricIcon { RPM, SPEED, TEMP, THROTTLE, VOLTAGE, INTAKE }

data class DashboardUiState(
    val vehicle: VehicleSpec? = null,
    val connectionState: ObdConnectionState = ObdConnectionState.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val metrics: List<LiveMetric> = emptyMetrics(),
    val dtcCount: Int = 0,
    val errorMessage: String? = null,
    val ssmFallback: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    userPreferences: UserPreferences,
    private val bluetoothManager: OBDBluetoothManager,
    private val obdEngine: ObdQueryEngine,
    private val alertManager: AlertManager,
) : ViewModel() {

    private val selectedVehicle: StateFlow<VehicleSpec?> = userPreferences.selectedVehicle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<DashboardUiState> = combine(
        selectedVehicle,
        bluetoothManager.connectionState,
        obdEngine.sensorValues,
        obdEngine.dtcCount,
        userPreferences.displayUnits,
    ) { vehicle, btState, sensorValues, dtcCount, units ->
        val obdState = btState.toObdState()
        val connected = obdState == ObdConnectionState.CONNECTED
        val ssmFallback = vehicle?.ssmSupported == true  // SSM protocol not yet implemented
        DashboardUiState(
            vehicle             = vehicle,
            connectionState     = obdState,
            connectedDeviceName = (btState as? BluetoothConnectionState.Connected)?.deviceName,
            metrics             = if (connected) sensorValues.toDashboardMetrics(units) else emptyMetrics(),
            dtcCount            = if (connected) dtcCount else 0,
            errorMessage        = (btState as? BluetoothConnectionState.Error)?.message,
            ssmFallback         = connected && ssmFallback,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    // ── Alert banner ──────────────────────────────────────────────────────────

    private val _dismissedLevel = MutableStateFlow<TempAlertLevel?>(null)

    val showAlertBanner: StateFlow<TempAlertLevel> = combine(
        alertManager.alertLevel,
        _dismissedLevel,
    ) { level, dismissed ->
        if (level != TempAlertLevel.NONE && level != dismissed) level else TempAlertLevel.NONE
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TempAlertLevel.NONE)

    fun dismissAlert() { _dismissedLevel.value = alertManager.alertLevel.value }

    // Reset dismiss when alert changes (new alert type shows again)
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
}

// ── Extension helpers ─────────────────────────────────────────────────────────

private fun BluetoothConnectionState.toObdState() = when (this) {
    is BluetoothConnectionState.Disconnected -> ObdConnectionState.DISCONNECTED
    is BluetoothConnectionState.Connecting   -> ObdConnectionState.CONNECTING
    is BluetoothConnectionState.Connected    -> ObdConnectionState.CONNECTED
    is BluetoothConnectionState.Reconnecting -> ObdConnectionState.CONNECTING
    is BluetoothConnectionState.Error        -> ObdConnectionState.ERROR
}

private fun Map<String, Float>.toDashboardMetrics(units: DisplayUnits): List<LiveMetric> {
    val rpm      = this[ObdPids.RPM.cmd]
    val speed    = this[ObdPids.SPEED.cmd]
    val coolant  = this[ObdPids.COOLANT_TEMP.cmd]
    val throttle = this[ObdPids.THROTTLE.cmd]
    val voltage  = this[ObdPids.VOLTAGE.cmd]
    val intake   = this[ObdPids.INTAKE_TEMP.cmd]

    val (coolantVal, coolantUnit) = formatTemp(coolant, units.temperatureUnit)
    val (intakeVal, intakeUnit)   = formatTemp(intake, units.temperatureUnit)

    return listOf(
        LiveMetric("rpm",      "Engine RPM",    rpm?.let     { "%,.0f".format(it) } ?: "--", "rpm",        MetricIcon.RPM,      rpm != null && rpm > 4000f, fraction(rpm, 0f, 8000f)),
        LiveMetric("speed",    "Vehicle Speed", speed?.let   { "%.0f".format(it)  } ?: "--", "km/h",       MetricIcon.SPEED,    false,                       fraction(speed, 0f, 240f)),
        LiveMetric("coolant",  "Coolant Temp",  coolantVal,                                   coolantUnit,  MetricIcon.TEMP,     coolant != null && coolant >= 110f, fraction(coolant, 0f, 130f)),
        LiveMetric("throttle", "Throttle Pos",  throttle?.let{ "%.0f".format(it)  } ?: "--", "%",          MetricIcon.THROTTLE, false,                       fraction(throttle, 0f, 100f)),
        LiveMetric("voltage",  "Battery",       voltage?.let { "%.1f".format(it)  } ?: "--", "V",          MetricIcon.VOLTAGE,  voltage != null && voltage < 11.8f, fraction(voltage, 10f, 16f)),
        LiveMetric("intake",   "Intake Temp",   intakeVal,                                    intakeUnit,   MetricIcon.INTAKE,   false,                       fraction(intake, -20f, 80f)),
    )
}

private fun formatTemp(celsius: Float?, unit: String): Pair<String, String> {
    if (celsius == null) return "--" to if (unit == "fahrenheit") "°F" else "°C"
    return if (unit == "fahrenheit") {
        "%.0f".format(UnitConverter.celsiusToFahrenheit(celsius.toDouble())) to "°F"
    } else {
        "%.0f".format(celsius) to "°C"
    }
}

private fun fraction(v: Float?, min: Float, max: Float): Float? =
    v?.let { ((it - min) / (max - min)).coerceIn(0f, 1f) }

internal fun emptyMetrics() = listOf(
    LiveMetric("rpm",      "Engine RPM",    "--", "rpm",  MetricIcon.RPM),
    LiveMetric("speed",    "Vehicle Speed", "--", "km/h", MetricIcon.SPEED),
    LiveMetric("coolant",  "Coolant Temp",  "--", "°C",   MetricIcon.TEMP),
    LiveMetric("throttle", "Throttle Pos",  "--", "%",    MetricIcon.THROTTLE),
    LiveMetric("voltage",  "Battery",       "--", "V",    MetricIcon.VOLTAGE),
    LiveMetric("intake",   "Intake Temp",   "--", "°C",   MetricIcon.INTAKE),
)
