package com.subaru.servicetool.data.bluetooth

sealed class BluetoothConnectionState {
    object Disconnected : BluetoothConnectionState()
    object Connecting : BluetoothConnectionState()
    data class Connected(val deviceName: String, val type: OBDConnectionType) : BluetoothConnectionState()
    object Reconnecting : BluetoothConnectionState()
    data class Error(val message: String) : BluetoothConnectionState()
}
