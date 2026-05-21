package com.subaru.servicetool.data.obd

enum class PidGroup { ENGINE, TEMPERATURE, FUEL, MISC, TRANSMISSION }

/** Which physical source the polling engine actually queries for engine oil temperature. */
enum class OilTempSource { OBD_STANDARD, SSM_ECU, NONE }

/** Cached result of the first-connect sensor probe. */
data class SensorProbeResult(
    val oilTempSource: OilTempSource = OilTempSource.NONE,
    val tcuAvailable: Boolean = false,
)

/**
 * Describes one OBD-II or Subaru SSM (Mode 22) command.
 * [parse] receives the raw data bytes following the mode+PID echo in the adapter response.
 */
data class ObdPid(
    val cmd: String,
    val name: String,
    val unit: String,
    val minVal: Float = 0f,
    val maxVal: Float = 100f,
    val group: PidGroup = PidGroup.MISC,
    val header: String? = null,        // non-null → switch ATSH before query, restore 7E0 after
    val isTurboOnly: Boolean = false,  // excluded from active set on NA vehicles
    val parse: (List<Int>) -> Float?,
)
