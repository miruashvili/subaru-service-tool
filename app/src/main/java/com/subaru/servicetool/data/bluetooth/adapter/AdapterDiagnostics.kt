package com.subaru.servicetool.data.bluetooth.adapter

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "AdapterDiagnostics"
private const val RTT_WINDOW = 50  // rolling window for average RTT

/**
 * Thread-safe diagnostics tracker for a single adapter session.
 *
 * All counters use [AtomicLong] for lock-free concurrent updates from multiple pollers.
 * A [DiagnosticsSnapshot] is emitted via [snapshot] after each significant event.
 *
 * Call [reset] when a new BT connection is established.
 */
class AdapterDiagnostics {

    private var adapterType = AdapterType.UNKNOWN
    private var sessionStartMs = System.currentTimeMillis()

    private val totalSent   = AtomicLong(0)
    private val timeouts    = AtomicLong(0)
    private val retries     = AtomicLong(0)
    private val fullSuccess = AtomicLong(0)
    private val halfSuccess = AtomicLong(0)
    private val singleFall  = AtomicLong(0)
    private val peakRtt     = AtomicLong(0)

    // Rolling RTT window (synchronized on rttSamples)
    private val rttSamples = ArrayDeque<Long>(RTT_WINDOW)

    private val _snapshot = MutableStateFlow(DiagnosticsSnapshot())
    val snapshot: StateFlow<DiagnosticsSnapshot> = _snapshot.asStateFlow()

    // ── Event recording ───────────────────────────────────────────────────────

    fun setAdapterType(type: AdapterType) {
        adapterType = type
        emit()
    }

    fun recordCommand(rttMs: Long) {
        totalSent.incrementAndGet()
        synchronized(rttSamples) {
            if (rttSamples.size >= RTT_WINDOW) rttSamples.removeFirst()
            rttSamples.addLast(rttMs)
        }
        if (rttMs > peakRtt.get()) peakRtt.set(rttMs)
        emit()
    }

    fun recordTimeout() {
        totalSent.incrementAndGet()
        timeouts.incrementAndGet()
        Log.d(TAG, "Timeout recorded — total=${timeouts.get()}/${totalSent.get()}")
        emit()
    }

    fun recordRetry() {
        retries.incrementAndGet()
        Log.d(TAG, "Retry recorded — total=${retries.get()}")
    }

    fun recordBatchResult(tier: BatchTier) {
        when (tier) {
            BatchTier.FULL_BATCH  -> fullSuccess.incrementAndGet()
            BatchTier.HALF_BATCH  -> {
                halfSuccess.incrementAndGet()
                Log.d(TAG, "Half-batch fallback: total=${halfSuccess.get()}")
            }
            BatchTier.SINGLE_READ -> {
                singleFall.incrementAndGet()
                Log.d(TAG, "Single-read fallback: total=${singleFall.get()}")
            }
        }
        emit()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun reset(type: AdapterType = AdapterType.UNKNOWN) {
        adapterType    = type
        sessionStartMs = System.currentTimeMillis()
        totalSent.set(0); timeouts.set(0); retries.set(0)
        fullSuccess.set(0); halfSuccess.set(0); singleFall.set(0); peakRtt.set(0)
        synchronized(rttSamples) { rttSamples.clear() }
        Log.i(TAG, "Diagnostics reset for ${type.displayName}")
        emit()
    }

    // ── Snapshot emission ─────────────────────────────────────────────────────

    private fun emit() {
        val avgRtt = synchronized(rttSamples) {
            if (rttSamples.isEmpty()) 0.0 else rttSamples.average()
        }
        val sent  = totalSent.get().coerceAtLeast(1)
        val touts = timeouts.get()
        val totalBatch = fullSuccess.get() + halfSuccess.get() + singleFall.get()
        _snapshot.value = DiagnosticsSnapshot(
            adapterType         = adapterType,
            sessionStartMs      = sessionStartMs,
            totalCommandsSent   = sent,
            timeoutCount        = touts,
            retryCount          = retries.get(),
            batchFullSuccesses  = fullSuccess.get(),
            batchHalfSuccesses  = halfSuccess.get(),
            batchSingleFallbacks = singleFall.get(),
            averageRttMs        = avgRtt,
            peakRttMs           = peakRtt.get(),
            errorRate           = touts.toDouble() / sent.toDouble(),
            batchSuccessRate    = if (totalBatch == 0L) 1.0
                                  else fullSuccess.get().toDouble() / totalBatch.toDouble(),
        )
    }
}
