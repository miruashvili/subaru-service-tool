package com.subaru.servicetool.data.obd.logging

/**
 * Current state of the [DataLogger] session recorder.
 *
 * @param active     True while a session is recording.
 * @param filePath   Absolute path of the active/last CSV file, or null.
 * @param rowCount   Number of sample rows written this session.
 * @param startedMs  Epoch-ms when the session started (0 if never).
 */
data class LoggingState(
    val active: Boolean = false,
    val filePath: String? = null,
    val rowCount: Long = 0L,
    val startedMs: Long = 0L,
) {
    val durationMs: Long get() = if (startedMs == 0L) 0L else System.currentTimeMillis() - startedMs
}
