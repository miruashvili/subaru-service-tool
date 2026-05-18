package com.subaru.servicetool.data.obd

enum class PidGroup { ENGINE, TEMPERATURE, FUEL, MISC }

/**
 * Describes one OBD-II (or ELM327 AT) command.
 *
 * [parse] receives the raw data bytes that follow the mode+PID echo in the adapter response.
 * For ATRV (voltage) this lambda is unused — [ObdParser.parseVoltage] handles that command.
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
