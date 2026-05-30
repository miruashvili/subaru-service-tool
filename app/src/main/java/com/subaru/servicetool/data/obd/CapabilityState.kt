package com.subaru.servicetool.data.obd

/**
 * Advisory result of a single SSM address probe.
 *
 * The polling engine treats this as informational only — it never blocks a sensor
 * from being polled based on this state. Per-PID error tracking ([ObdQueryEngine]
 * skipCycles) handles naturally unsupported sensors at runtime.
 */
enum class CapabilityState {
    /** Address has not been probed yet. Support is undetermined. */
    UNKNOWN,
    /** Address responded to the A8 probe on this ECU/TCU. */
    SUPPORTED,
    /** Address did not respond to the A8 probe on this ECU/TCU. */
    UNSUPPORTED,
}
