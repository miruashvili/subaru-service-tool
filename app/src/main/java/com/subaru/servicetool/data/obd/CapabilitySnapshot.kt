package com.subaru.servicetool.data.obd

/**
 * Immutable result of the first-connect capability probe.
 * Advisory only — never used to gate which sensors are polled.
 */
data class CapabilitySnapshot(
    val oilTempSource: OilTempSource,
    val ecuStates: Map<Int, CapabilityState>,
    val tcuStates: Map<Int, CapabilityState>,
)
