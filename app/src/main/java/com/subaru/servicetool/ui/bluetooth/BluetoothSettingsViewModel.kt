package com.subaru.servicetool.ui.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subaru.servicetool.data.bluetooth.BleUuids
import com.subaru.servicetool.data.bluetooth.BluetoothConnectionState
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.bluetooth.OBDConnectionType
import com.subaru.servicetool.data.obd.AdapterSpeedProfile
import com.subaru.servicetool.data.obd.ObdQueryEngine
import com.subaru.servicetool.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BtDeviceItem(
    val device: BluetoothDevice,
    val displayName: String,
    val address: String,
    val connectionType: OBDConnectionType,
)

@HiltViewModel
class BluetoothSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val obdManager: OBDBluetoothManager,
    private val userPreferences: UserPreferences,
    private val queryEngine: ObdQueryEngine,
) : ViewModel() {

    val connectionState: StateFlow<BluetoothConnectionState> = obdManager.connectionState
    val adapterSpeedProfile: StateFlow<AdapterSpeedProfile> = obdManager.adapterSpeedProfile

    val showRawObd: StateFlow<Boolean> = userPreferences.showRawObd
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Number of SSM sensors detected by the capability probe (ECU + TCU). */
    val detectedSensorCount: StateFlow<Int> = queryEngine.detectedSensorCount

    // ── ActiveOBD diagnostics surface ──────────────────────────────────────────
    val adapterType        = queryEngine.adapterType
    val adapterDiagnostics = queryEngine.adapterDiagnostics
    val discoveredModules  = queryEngine.discoveredModules
    val dynamicPidCount    = queryEngine.dynamicPidCount
    val loggingState       = queryEngine.loggingState

    /** Curated + dynamically-discovered PID total in the extensible registry. */
    fun totalPidCount(): Int = queryEngine.totalPidCount

    fun toggleLogging() {
        if (queryEngine.loggingState.value.active) queryEngine.stopLogging()
        else queryEngine.startLogging()
    }

    private val _rawObdLog = MutableStateFlow<List<String>>(emptyList())
    val rawObdLog: StateFlow<List<String>> = _rawObdLog.asStateFlow()

    var connectingAddress by mutableStateOf<String?>(null)
        private set

    private val _pairedDevices = MutableStateFlow<List<BtDeviceItem>>(emptyList())
    val pairedDevices: StateFlow<List<BtDeviceItem>> = _pairedDevices.asStateFlow()

    private val _scanResults = MutableStateFlow<List<BtDeviceItem>>(emptyList())
    val scanResults: StateFlow<List<BtDeviceItem>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null
    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val leScanner get() = btManager?.adapter?.bluetoothLeScanner

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: return  // Ignore nameless devices
            val item = BtDeviceItem(device, name, device.address, OBDConnectionType.BLE)
            val current = _scanResults.value
            if (current.none { it.address == device.address }) {
                _scanResults.value = current + item
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    init {
        // Silently ignored if BT permission not yet granted; composable re-calls after permission grant
        try { refreshPairedDevices() } catch (_: Exception) { }
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state is BluetoothConnectionState.Connected ||
                    state is BluetoothConnectionState.Disconnected ||
                    state is BluetoothConnectionState.Error) {
                    connectingAddress = null
                }
            }
        }
        viewModelScope.launch {
            obdManager.rawObdLog.collect { line ->
                _rawObdLog.value = (_rawObdLog.value + line).takeLast(30)
            }
        }
    }

    fun setShowRawObd(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setShowRawObd(enabled) }
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        val bonded = try { btManager?.adapter?.bondedDevices } catch (_: Exception) { null } ?: return
        _pairedDevices.value = bonded.mapNotNull { device ->
            val name = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val type = when (device.type) {
                BluetoothDevice.DEVICE_TYPE_LE -> OBDConnectionType.BLE
                else -> OBDConnectionType.SPP
            }
            BtDeviceItem(device, name, device.address, type)
        }.sortedBy { it.displayName }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return
        _scanResults.value = emptyList()
        _isScanning.value = true

        val filters = listOf(
            BleUuids.VGATE_SERVICE,
            BleUuids.OBDLINK_SERVICE,
            BleUuids.GENERIC_SERVICE,
        ).map { uuid ->
            ScanFilter.Builder().setServiceUuid(ParcelUuid(uuid)).build()
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            leScanner?.startScan(filters, settings, scanCallback)
        } catch (_: Exception) {
            _isScanning.value = false
            return
        }

        // Stop scan automatically after 10 s
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            delay(10_000)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        try { leScanner?.stopScan(scanCallback) } catch (_: Exception) { }
        _isScanning.value = false
    }

    fun connect(item: BtDeviceItem) {
        connectingAddress = item.address
        when (item.connectionType) {
            OBDConnectionType.BLE -> obdManager.connectBle(item.device)
            OBDConnectionType.SPP -> obdManager.connectSpp(item.device)
        }
    }

    fun disconnect() = obdManager.disconnect()

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }

    /** Returns the MAC address of the currently connected device, if any. */
    fun connectedMac(): String? =
        if (connectionState.value is BluetoothConnectionState.Connected)
            obdManager.lastDeviceMac
        else null

    /** Whether the adapter is available and enabled. */
    fun isBluetoothEnabled(): Boolean = try { btManager?.adapter?.isEnabled == true } catch (_: Exception) { false }

    /** Permissions needed at runtime depending on SDK version. */
    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
