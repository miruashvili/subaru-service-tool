package com.subaru.servicetool.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subaru.servicetool.data.model.VehicleSpec
import com.subaru.servicetool.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val selectedVehicle: StateFlow<VehicleSpec?> = userPreferences.selectedVehicle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var simulationJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            selectedVehicle.collect { vehicle ->
                _uiState.value = _uiState.value.copy(vehicle = vehicle)
            }
        }
    }

    fun connect() {
        if (_uiState.value.connectionState == ObdConnectionState.CONNECTING) return
        simulationJob?.cancel()
        _uiState.value = _uiState.value.copy(
            connectionState = ObdConnectionState.CONNECTING,
            errorMessage = null,
        )
        simulationJob = viewModelScope.launch {
            delay(2_200)
            _uiState.value = _uiState.value.copy(connectionState = ObdConnectionState.CONNECTED)
            // Kick off a live-data simulation loop
            while (true) {
                delay(900)
                _uiState.value = _uiState.value.copy(metrics = simulatedMetrics())
            }
        }
    }

    fun disconnect() {
        simulationJob?.cancel()
        simulationJob = null
        _uiState.value = _uiState.value.copy(
            connectionState = ObdConnectionState.DISCONNECTED,
            metrics = emptyMetrics(),
        )
    }

    private fun simulatedMetrics(): List<LiveMetric> {
        val isTurbo = selectedVehicle.value?.isTurbo ?: false
        val maxRpm = if (isTurbo) 6500 else 7200
        val rpm = Random.nextInt(750, maxRpm)
        return listOf(
            LiveMetric("rpm",      "Engine RPM",    "%,d".format(rpm),                    "rpm",  MetricIcon.RPM,      rpm > 4000),
            LiveMetric("speed",    "Vehicle Speed", Random.nextInt(0, 140).toString(),     "km/h", MetricIcon.SPEED),
            LiveMetric("coolant",  "Coolant Temp",  Random.nextInt(85, 98).toString(),     "°C",   MetricIcon.TEMP,     false),
            LiveMetric("throttle", "Throttle Pos",  Random.nextInt(5, 85).toString(),      "%",    MetricIcon.THROTTLE),
            LiveMetric("voltage",  "Battery",       "%.1f".format(Random.nextDouble(13.8, 14.6)), "V", MetricIcon.VOLTAGE),
            LiveMetric("intake",   "Intake Temp",   Random.nextInt(20, 45).toString(),     "°C",   MetricIcon.INTAKE),
        )
    }

    override fun onCleared() {
        simulationJob?.cancel()
        super.onCleared()
    }
}
