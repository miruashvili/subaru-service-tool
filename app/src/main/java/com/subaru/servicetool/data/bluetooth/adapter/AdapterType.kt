package com.subaru.servicetool.data.bluetooth.adapter

/**
 * Classification of the connected OBD-II adapter hardware.
 * Detected by [AdapterDetector] after ELM327 initialisation.
 */
enum class AdapterType(val displayName: String) {
    /** OBDLink EX / MX+ / CX — professional hardware, fast, supports ST command set. */
    OBDLINK        ("OBDLink"),
    /** Vgate iCar Pro BLE — reliable BLE adapter, medium throughput. */
    VGATE          ("Vgate iCar Pro"),
    /** Genuine ELM327 chip from ELM Electronics / Microchip. */
    ELM327_GENUINE ("ELM327 (Genuine)"),
    /** Counterfeit / clone ELM327 — slow, buggy multi-frame handling. */
    ELM327_CLONE   ("ELM327 (Clone)"),
    /** Not yet detected — uses conservative defaults. */
    UNKNOWN        ("Unknown Adapter"),
}
