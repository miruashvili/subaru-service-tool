package com.subaru.servicetool.data.model

data class VehicleSpec(
    val year: Int,
    val modelName: String,
    val engineCode: String,
    val engineDisplayName: String,
    val isTurbo: Boolean,
    val obdProtocol: String = "ISO 15765-4 CAN 11bit 500kbps",
    val ssmSupported: Boolean = true,
)
