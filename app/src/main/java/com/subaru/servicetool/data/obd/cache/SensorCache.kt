package com.subaru.servicetool.data.obd.cache

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SensorCache"

/**
 * Retains the last-known value and running min/max/peak for every sensor seen during a session.
 *
 * ActiveOBD-style behaviour:
 *  - Last-known values survive a momentary reconnect (the live `sensorValues` flow is cleared by
 *    the engine on pause, but the cache keeps the prior reading so gauges don't blank).
 *  - Per-sensor min/max/sample-count is exposed for peak-hold gauges and session summaries.
 *
 * Thread-safe: [update] may be called from the engine's IO collector while the UI reads [stats].
 */
@Singleton
class SensorCache @Inject constructor() {

    private val store = ConcurrentHashMap<String, SensorStat>()

    private val _stats = MutableStateFlow<Map<String, SensorStat>>(emptyMap())
    /** Live per-sensor statistics, keyed by PID command. */
    val stats: StateFlow<Map<String, SensorStat>> = _stats.asStateFlow()

    /** Most recent value for [cmd], or null if never seen this session. */
    fun lastValue(cmd: String): Float? = store[cmd]?.last

    /** Snapshot of all last-known values (survives reconnect). */
    fun lastKnownValues(): Map<String, Float> = store.mapValues { it.value.last }

    /**
     * Merges a batch of fresh sensor values into the cache, updating min/max/last/samples.
     * Only changed or new entries trigger a new [stats] emission.
     */
    fun update(values: Map<String, Float>) {
        if (values.isEmpty()) return
        val now = System.currentTimeMillis()
        var changed = false
        for ((cmd, value) in values) {
            val existing = store[cmd]
            store[cmd] = existing?.withSample(value, now) ?: SensorStat.first(cmd, value, now)
            changed = true
        }
        if (changed) _stats.value = HashMap(store)
    }

    /** Clears min/max accumulation but keeps last values (peak-hold reset). */
    fun resetPeaks() {
        val now = System.currentTimeMillis()
        store.replaceAll { _, s -> SensorStat.first(s.cmd, s.last, now) }
        _stats.value = HashMap(store)
        Log.i(TAG, "Peaks reset for ${store.size} sensors")
    }

    /** Fully clears the cache (called on terminal disconnect). */
    fun clear() {
        val n = store.size
        store.clear()
        _stats.value = emptyMap()
        Log.i(TAG, "Cache cleared ($n sensors)")
    }
}
