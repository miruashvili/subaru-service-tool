package com.subaru.servicetool.data.obd.discovery

/**
 * Physical CAN module types present on Subaru vehicles.
 *
 * Modules that share a CAN header ([TCU], [CVT], [AWD]) are distinguished by which
 * SSM memory addresses respond within that header: CVT-specific addresses (0x001017)
 * indicate a CVT, AWD-specific addresses (0x001065) indicate an AWD controller.
 */
enum class SubaruModule(
    /** ELM327 ATSH command argument used to address this module. */
    val canHeader: String,
    /** CAN ID of the response frame (canHeader + 0x08). */
    val responseHeader: String,
    /** Human-readable name for logs and UI. */
    val displayName: String,
) {
    /** Engine Control Module — 7E0. Present on all petrol Subaru vehicles. */
    ECU    ("7E0", "7E8", "Engine ECM"),

    /**
     * Transmission Control Module — 7E1.
     * Parent module for all 7E1-addressed sensors. Present when the TCU responds
     * to at least one SSM read or UDS ping.
     */
    TCU    ("7E1", "7E9", "Transmission TCM"),

    /**
     * CVT-specific sub-module on the TCU (7E1).
     * Detected when SSM address 0x001017 (CVT fluid temperature) responds.
     */
    CVT    ("7E1", "7E9", "CVT Controller"),

    /**
     * AWD-specific sub-module on the TCU (7E1).
     * Detected when SSM address 0x001065 (AWD transfer duty) responds.
     */
    AWD    ("7E1", "7E9", "AWD Controller"),

    /** TPMS Receiver Module — 7D4. Present on Subaru models with factory TPMS. */
    TPMS   ("7D4", "7DC", "TPMS Module"),

    /** Body Control Module — 7E2. Manages lighting, AC, and comfort systems. */
    BODY   ("7E2", "7EA", "Body Control BCM"),

    /**
     * Hybrid/EV Battery Management System — 7E6.
     * Present on Subaru e-BOXER and PHEV models.
     */
    HYBRID ("7E6", "7EE", "HV Battery BMS"),
}
