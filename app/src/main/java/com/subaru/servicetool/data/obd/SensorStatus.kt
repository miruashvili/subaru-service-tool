package com.subaru.servicetool.data.obd

/**
 * Runtime polling status of a registered sensor.
 * Updated by [ObdQueryEngine] as poll results arrive; exposed via [SensorRegistry.statuses].
 */
enum class SensorStatus {
    /** Sensor has not been queried yet this session. */
    UNKNOWN,
    /** Sensor is returning valid data. */
    ACTIVE,
    /** Sensor returned an error or no data in the last attempt (will be retried). */
    ERROR,
    /**
     * Sensor confirmed unsupported by this ECU/TCU — 3 consecutive failures on an SSM
     * address caused the polling engine to suspend it for the session.
     * Not the same as capability-probe UNSUPPORTED; this is determined at runtime.
     */
    UNSUPPORTED,
}
