package com.subaru.servicetool.data.obd

/** Physical ECU/module that owns a sensor's memory address. */
enum class SensorModule {
    /** Standard OBD-II — any ELM327 adapter, no ATSH required. */
    OBD,
    /** Subaru Engine Control Unit — ATSH7E0, SSM A8 or UDS Mode 22. */
    ECU,
    /** Subaru Transmission Control Unit — ATSH7E1, SSM A8 or UDS Mode 22. */
    TCU,
    /** Body Control Module — ATSH7D4, UDS Mode 22 (TPMS). */
    BCM,
}
