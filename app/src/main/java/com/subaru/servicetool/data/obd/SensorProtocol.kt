package com.subaru.servicetool.data.obd

/** Wire protocol used to request a sensor's value. */
enum class SensorProtocol {
    /** SAE J1979 Mode 01 (e.g. 010C for RPM). Works on any ELM327 adapter. */
    OBD_STANDARD,
    /** Subaru SSM A8 service — direct memory read via ISO-TP CAN (ssmAddress != null). */
    SSM_A8,
    /** ISO 14229 UDS ReadDataByIdentifier — Mode 22 request/62 response. */
    UDS_22,
}
