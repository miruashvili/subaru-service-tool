package com.subaru.servicetool.data.obd.cache

/**
 * Cached statistics for a single sensor, accumulated across a connection session.
 *
 * @param cmd          PID command / sensor key (e.g. "010C").
 * @param last         Most recent value.
 * @param min          Lowest value seen this session.
 * @param max          Highest value seen this session.
 * @param samples      Number of values recorded.
 * @param firstSeenMs  Epoch-ms of the first sample.
 * @param lastUpdateMs Epoch-ms of the most recent sample.
 */
data class SensorStat(
    val cmd: String,
    val last: Float,
    val min: Float,
    val max: Float,
    val samples: Long,
    val firstSeenMs: Long,
    val lastUpdateMs: Long,
) {
    /** True if no new value has arrived within [staleAfterMs]. */
    fun isStale(staleAfterMs: Long = 5_000L): Boolean =
        System.currentTimeMillis() - lastUpdateMs > staleAfterMs

    fun withSample(value: Float, now: Long = System.currentTimeMillis()): SensorStat = copy(
        last = value,
        min = if (value < min) value else min,
        max = if (value > max) value else max,
        samples = samples + 1,
        lastUpdateMs = now,
    )

    companion object {
        fun first(cmd: String, value: Float, now: Long = System.currentTimeMillis()) = SensorStat(
            cmd = cmd, last = value, min = value, max = value,
            samples = 1, firstSeenMs = now, lastUpdateMs = now,
        )
    }
}
