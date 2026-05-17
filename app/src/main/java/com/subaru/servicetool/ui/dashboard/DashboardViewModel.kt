package com.subaru.servicetool.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subaru.servicetool.data.bluetooth.BluetoothConnectionState
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.model.VehicleSpec
import com.subaru.servicetool.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
import kotlin.random.Random

enum class ObdConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class LiveMetric(
    val id: String,
    val label: String,
    val value: String,
    val unit: String,
    val iconRes: MetricIcon,
    val highlight: Boolean = false,
)

enum class MetricIcon { RPM, SPEED, TEMP, THROTTLE, VOLTAGE, INTAKE }

data class DashboardUiState(
    val vehicle: VehicleSpec? = null,
    val connectionState: ObdConnectionState = ObdConnectionState.DISCONNECTED,
    val connectedDeviceName: String? = null,
    val metrics: List<LiveMetric> = emptyMetrics(),
    val dtcCount: Int = 0,
    val errorMessage: String? = null,
)

private fun emptyMetrics() = listOf(
    LiveMetric("rpm",      "Engine RPM",    "--",   "rpm",  MetricIcon.RPM),
    LiveMetric("speed",    "Vehicle Speed", "--",   "km/h", MetricIcon.SPEED),
    LiveMetric("coolant",  "Coolant Temp",  "--",   "°C",   MetricIcon.TEMP),
    LiveMetric("throttle", "Throttle Pos",  "--",   "%",    MetricIcon.THROTTLE),
    LiveMetric("voltage",  "Battery",       "--",   "V",    MetricIcon.VOLTAGE),
    LiveMetric("intake",   "Intake Temp",   "--",   "°C",   MetricIcon.INTAKE),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    userPreferences: UserPreferences,
    private val bluetoothManager: OBDBluetoothManager,
) : ViewModel() {

    private val _metricsOverride = MutableStateFlow<List<LiveMetric>?>(null)
    private var simulationJob: Job? = null

    private val selectedVehicle: StateFlow<VehicleSpec?> = userPreferences.selectedVehicle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<DashboardUiState> = combine(
        selectedVehicle,
        bluetoothManager.connectionState,
        _metricsOverride,
    ) { vehicle, btState, metricsOverride ->
        val obdState = when (btState) {
            is BluetoothConnectionState.Disconnected -> ObdConnectionState.DISCONNECTED
            is BluetoothConnectionState.Connecting   -> ObdConnectionState.CONNECTING
            is BluetoothConnectionState.Connected    -> ObdConnectionState.CONNECTED
            is BluetoothConnectionState.Reconnecting -> ObdConnectionState.CONNECTING
            is BluetoothConnectionState.Error        -> ObdConnectionState.ERROR
        }
        val deviceName = (btState as? BluetoothConnectionState.Connected)?.deviceName
        val errorMsg = (btState as? BluetoothConnectionState.Error)?.message
        DashboardUiState(
            vehicle = vehicle,
            connectionState = obdState,
            connectedDeviceName = deviceName,
            metrics = if (obdState == ObdConnectionState.CONNECTED)
                metricsOverride ?: emptyMetrics()
            else emptyMetrics(),
            errorMessage = errorMsg,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardUiState(),
    )

    // Navigate-to-bluetooth event (emitted when no last device is known)
    private val _navigateToBluetooth = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToBluetooth: SharedFlow<Unit> = _navigateToBluetooth.asSharedFlow()

    init {
        // Start simulated metrics when connected (replaced by real OBD data in Phase 5)
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { state ->
                if (state is BluetoothConnectionState.Connected) {
                    startSimulation()
                } else {
                    simulationJob?.cancel()
                    simulationJob = null
                    _metricsOverride.value = null
                }
            }
        }
    }

    fun connect() {
        if (bluetoothManager.lastDeviceMac != null) {
            bluetoothManager.reconnectToLastDevice()
        } else {
            _navigateToBluetooth.tryEmit(Unit)
        }
    }

    fun disconnect() = bluetoothManager.disconnect()

    private fun startSimulation() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            while (true) {
                delay(900)
                _metricsOverride.value = simulatedMetrics()
            }
        }
    }

    private fun simulatedMetrics(): List<LiveMetric> {
        val isTurbo = selectedVehicle.value?.isTurbo ?: false
        val rpm = Random.nextInt(750, if (isTurbo) 6500 else 7200)
        return listOf(
            LiveMetric("rpm",      "Engine RPM",    "%,d".format(rpm),                              "rpm",  MetricIcon.RPM,  rpm > 4000),
            LiveMetric("speed",    "Vehicle Speed", Random.nextInt(0, 140).toString(),              "km/h", MetricIcon.SPEED),
            LiveMetric("coolant",  "Coolant Temp",  Random.nextInt(85, 98).toString(),              "°C",   MetricIcon.TEMP),
            LiveMetric("throttle", "Throttle Pos",  Random.nextInt(5, 85).toString(),               "%",    MetricIcon.THROTTLE),
            LiveMetric("voltage",  "Battery",       "%.1f".format(Random.nextDouble(13.8, 14.6)),  "V",    MetricIcon.VOLTAGE),
            LiveMetric("intake",   "Intake Temp",   Random.nextInt(20, 45).toString(),              "°C",   MetricIcon.INTAKE),
        )
    }

    override fun onCleared() {
        simulationJob?.cancel()
        super.onCleared()
    }
}
