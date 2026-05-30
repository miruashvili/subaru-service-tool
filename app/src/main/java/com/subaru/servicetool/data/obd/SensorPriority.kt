package com.subaru.servicetool.data.obd

/**
 * Polling tier for a sensor — controls how frequently it is queried within
 * the adaptive poll cycle. Maps directly to ObdQueryEngine's tier scheduler.
 */
enum class SensorPriority {
    /** Polled every cycle — real-time readouts (RPM, speed, coolant, throttle). */
    CRITICAL,
    /** Polled every tier2Every cycles — important but not every frame. */
    HIGH,
    /** Polled every tier3Every cycles — secondary data. */
    NORMAL,
    /** Polled every tier4Every cycles — slow-changing values (TPMS, run time). */
    LOW,
}
