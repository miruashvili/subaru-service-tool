package com.subaru.servicetool.data.bluetooth.adapter

/**
 * Immutable point-in-time snapshot of adapter diagnostic statistics.
 * Emitted by [AdapterDiagnostics.snapshot] after every meaningful event.
 */
data class DiagnosticsSnapshot(
    val adapterType: AdapterType          = AdapterType.UNKNOWN,
    val sessionStartMs: Long              = System.currentTimeMillis(),

    // ── Command counters ──────────────────────────────────────────────────────
    val totalCommandsSent: Long           = 0L,
    val timeoutCount: Long                = 0L,
    val retryCount: Long                  = 0L,

    // ── Batch tier counters ───────────────────────────────────────────────────
    val batchFullSuccesses: Long          = 0L,
    val batchHalfSuccesses: Long          = 0L,
    val batchSingleFallbacks: Long        = 0L,

    // ── Latency ───────────────────────────────────────────────────────────────
    val averageRttMs: Double              = 0.0,
    val peakRttMs: Long                   = 0L,

    // ── Derived ───────────────────────────────────────────────────────────────
    val errorRate: Double                 = 0.0,  // timeoutCount / totalCommandsSent
    val batchSuccessRate: Double          = 0.0,  // fullBatch / (full+half+single)
) {
    val sessionDurationMs: Long get() = System.currentTimeMillis() - sessionStartMs

    override fun toString(): String = buildString {
        append("AdapterDiagnostics(${adapterType.displayName}")
        append(" cmds=$totalCommandsSent timeouts=$timeoutCount retries=$retryCount")
        append(" fullBatch=$batchFullSuccesses halfBatch=$batchHalfSuccesses single=$batchSingleFallbacks")
        append(" avgRtt=${"%.0f".format(averageRttMs)}ms errRate=${"%.1f".format(errorRate * 100)}%)")
    }
}
