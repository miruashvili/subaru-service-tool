package com.subaru.servicetool.ui.sensors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subaru.servicetool.data.bluetooth.BluetoothConnectionState
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.obd.ObdPid
import com.subaru.servicetool.data.obd.ObdPids
import com.subaru.servicetool.data.obd.ObdQueryEngine
import com.subaru.servicetool.data.obd.PidGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SensorItem(
    val pid: ObdPid,
    val value: Float?,
    val displayValue: String,
)

data class SensorGroup(
    val group: PidGroup,
    val label: String,
    val items: List<SensorItem>,
)

@HiltViewModel
class SensorsViewModel @Inject constructor(
    val btManager: OBDBluetoothManager,
    private val obdEngine: ObdQueryEngine,
) : ViewModel() {

    val connectionState: StateFlow<BluetoothConnectionState> = btManager.connectionState

    val sensorGroups: StateFlow<List<SensorGroup>> = obdEngine.sensorValues
        .map { values -> buildGroups(values) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), buildGroups(emptyMap()))

    val dtcCount: StateFlow<Int> = obdEngine.dtcCount

    private fun buildGroups(values: Map<String, Float>): List<SensorGroup> =
        PidGroup.entries.mapNotNull { group ->
            val items = ObdPids.ALL
                .filter { it.group == group }
                .map { pid -> pid.toItem(values[pid.cmd]) }
            if (items.isEmpty()) null
            else SensorGroup(group, group.displayLabel, items)
        }

    private fun ObdPid.toItem(value: Float?): SensorItem = SensorItem(
        pid = this,
        value = value,
        displayValue = if (value == null) "--" else formatValue(value, unit),
    )

    private fun formatValue(v: Float, unit: String): String = when (unit) {
        "rpm" -> "%,.0f".format(v)
        "V"   -> "%.1f".format(v)
        "g/s" -> "%.2f".format(v)
        "s"   -> formatRunTime(v)
        else  -> "%.1f".format(v)
    }

    private fun formatRunTime(s: Float): String {
        val t = s.toInt()
        return when {
            t < 60   -> "${t}s"
            t < 3600 -> "${t / 60}m ${t % 60}s"
            else     -> "${t / 3600}h ${(t % 3600) / 60}m"
        }
    }
}

private val PidGroup.displayLabel
    get() = when (this) {
        PidGroup.ENGINE      -> "ENGINE"
        PidGroup.TEMPERATURE -> "TEMPERATURES"
        PidGroup.FUEL        -> "FUEL"
        PidGroup.MISC        -> "MISC"
    }
