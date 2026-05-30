package com.subaru.servicetool.data.obd.logging

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DataLogger"

/**
 * Records live sensor samples to a CSV file for later analysis — the core ActiveOBD logging
 * feature.
 *
 * Long format (one row per value) is used so dynamically-discovered sensors can appear mid-session
 * without rewriting a fixed header:
 *
 * ```
 * timestamp_ms,elapsed_ms,sensor_cmd,value
 * 1717082400123,0,010C,1726.0
 * 1717082400140,17,0105,82.0
 * ```
 *
 * Files are written to `<filesDir>/logs/`. Recording is opt-in via [start]; the engine forwards
 * every emitted sample batch to [onSample] but writing is a no-op until a session is active.
 *
 * Thread-safe: [onSample] is called from the engine's IO collector; file access is synchronised.
 */
@Singleton
class DataLogger @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val lock = Any()
    private var writer: BufferedWriter? = null
    private var startedMs = 0L
    private var rows = 0L

    private val _state = MutableStateFlow(LoggingState())
    val state: StateFlow<LoggingState> = _state.asStateFlow()

    val isActive: Boolean get() = synchronized(lock) { writer != null }

    private val logDir: File
        get() = File(context.filesDir, "logs").apply { if (!exists()) mkdirs() }

    /** Starts a new CSV recording session. Returns the file, or null on failure. */
    fun start(): File? = synchronized(lock) {
        if (writer != null) {
            Log.w(TAG, "start() ignored — session already active")
            return _state.value.filePath?.let { File(it) }
        }
        return try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file  = File(logDir, "session_$stamp.csv")
            val w = file.bufferedWriter()
            w.write("timestamp_ms,elapsed_ms,sensor_cmd,value")
            w.newLine()
            writer = w
            startedMs = System.currentTimeMillis()
            rows = 0L
            _state.value = LoggingState(active = true, filePath = file.absolutePath, rowCount = 0L, startedMs = startedMs)
            Log.i(TAG, "Recording started → ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}", e)
            null
        }
    }

    /** Stops the active session and flushes the file. Returns the written file, or null. */
    fun stop(): File? = synchronized(lock) {
        val w = writer ?: return null
        val path = _state.value.filePath
        return try {
            w.flush(); w.close()
            Log.i(TAG, "Recording stopped — $rows rows → $path")
            File(path ?: "")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop cleanly: ${e.message}", e)
            null
        } finally {
            writer = null
            _state.value = _state.value.copy(active = false)
        }
    }

    /** Forwards a sample batch from the engine; writes only when a session is active. */
    fun onSample(values: Map<String, Float>) {
        if (values.isEmpty()) return
        synchronized(lock) {
            val w = writer ?: return
            val now = System.currentTimeMillis()
            val elapsed = now - startedMs
            try {
                for ((cmd, value) in values) {
                    w.write("$now,$elapsed,$cmd,$value")
                    w.newLine()
                    rows++
                }
                _state.value = _state.value.copy(rowCount = rows)
            } catch (e: Exception) {
                Log.e(TAG, "Write failed — stopping session: ${e.message}", e)
                runCatching { w.close() }
                writer = null
                _state.value = _state.value.copy(active = false)
            }
        }
    }

    /** Lists all recorded session files, newest first. */
    fun listSessions(): List<File> =
        logDir.listFiles { f -> f.extension == "csv" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** Closes any active session without losing data (called on terminal disconnect). */
    fun onDisconnect() {
        if (isActive) {
            Log.i(TAG, "Disconnect — closing active recording session")
            stop()
        }
    }
}
