package com.subaru.servicetool.data.obd

enum class PidGroup { ENGINE, TEMPERATURE, FUEL, MISC, TRANSMISSION }

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
    val header: String? = null,   // non-null → switch ATSH before query, restore 7E0 after
    val parse: (List<Int>) -> Float?,
)
