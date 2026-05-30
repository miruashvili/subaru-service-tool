package com.subaru.servicetool.data.obd

/**
 * A registered sensor in the [SensorRegistry].
 *
 * Every sensor is polled every session regardless of dashboard configuration.
 * Runtime status is tracked separately in [SensorRegistry.statuses].
 *
 * @param sensorId   Stable unique identifier, e.g. "ENGINE_RPM".
 * @param module     Physical ECU/module that owns this sensor.
 * @param protocol   Wire protocol used to request the value.
 * @param priority   Polling tier — maps to the adaptive cycle scheduler.
 * @param decoder    Converts raw byte list to a physical float value (delegates to [obdPid.parse]).
 * @param obdPid     Underlying [ObdPid] used by the polling engine for command routing.
 */
data class SensorEntry(
    val sensorId: String,
    val module: SensorModule,
    val protocol: SensorProtocol,
    val priority: SensorPriority,
    val decoder: (List<Int>) -> Float?,
    val obdPid: ObdPid,
)
