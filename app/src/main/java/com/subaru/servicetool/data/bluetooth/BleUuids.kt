package com.subaru.servicetool.data.bluetooth

import java.util.UUID

internal object BleUuids {
    // Vgate iCar Pro
    val VGATE_SERVICE  = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val VGATE_WRITE    = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb")
    val VGATE_NOTIFY   = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

    // OBDLink CX
    val OBDLINK_SERVICE = UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2")

    // Generic BLE OBD
    val GENERIC_SERVICE = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val GENERIC_WRITE   = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    val GENERIC_NOTIFY  = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")

    // Classic SPP
    val SPP = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    // Client Characteristic Configuration Descriptor
    val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
