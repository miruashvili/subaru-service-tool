package com.subaru.servicetool.data.bluetooth.adapter

import android.util.Log
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.obd.ObdCapabilityProber
import com.subaru.servicetool.data.obd.ObdParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AdapterProfileManager"

/**
 * Manages the runtime adapter profile and implements the SSM batch read fallback chain.
 *
 * ## Adapter detection
 * [detect] runs once after ELM327 initialisation and identifies the connected hardware.
 * The detected [AdapterCapabilities] governs timeouts, retry counts, batch limits, and
 * settle delays for the rest of the session.
 *
 * ## Batch fallback chain
 * [readSsmWithFallback] always attempts the highest-throughput tier first and degrades
 * gracefully without ever disabling a module:
 *
 * ```
 * FULL_BATCH (up to maxBatchAddresses)
 *   → HALF_BATCH (chunks of maxBatchAddresses/2)
 *     → SINGLE_READ (one address per command)
 * ```
 *
 * A module is never suspended due to a timeout. Timeouts trigger retries and fallback;
 * only genuine NO-DATA responses (address not supported) count toward UNSUPPORTED status.
 *
 * ## Adaptive timeout
 * [sendWithRetry] wraps [OBDBluetoothManager.sendCommand] with [AdapterCapabilities.retryCount]
 * retries on null (timeout). Retries are logged in [diagnostics].
 *
 * ## Diagnostics
 * All events (commands, timeouts, retries, batch tiers) are recorded in [diagnostics] and
 * emitted as a live [DiagnosticsSnapshot] for display or logging.
 */
@Singleton
class AdapterProfileManager @Inject constructor(
    private val btManager: OBDBluetoothManager,
    private val capabilityProber: ObdCapabilityProber,
) {

    val diagnostics = AdapterDiagnostics()

    private val _adapterType = MutableStateFlow(AdapterType.UNKNOWN)
    val adapterType: StateFlow<AdapterType> = _adapterType.asStateFlow()

    private val _capabilities = MutableStateFlow(AdapterCapabilities.UNKNOWN)
    val capabilities: StateFlow<AdapterCapabilities> = _capabilities.asStateFlow()

    val activeCapabilities: AdapterCapabilities get() = _capabilities.value

    // ── Detection ─────────────────────────────────────────────────────────────

    /**
     * Detects the connected adapter type and loads its [AdapterCapabilities].
     * Must be called after ELM327 initialisation and before polling starts.
     * Safe to call multiple times (re-detects each call).
     */
    suspend fun detect(bleDeviceName: String? = btManager.lastDeviceName) {
        Log.i(TAG, "=== Adapter detection starting ===")
        val type = try {
            AdapterDetector.detect(btManager, bleDeviceName)
        } catch (e: Exception) {
            Log.e(TAG, "Detection failed: ${e.message} — using UNKNOWN capabilities", e)
            AdapterType.UNKNOWN
        }
        val caps = AdapterCapabilities.forType(type)
        _adapterType.value = type
        _capabilities.value = caps
        diagnostics.reset(type)

        Log.i(TAG, "=== Adapter identified: ${type.displayName} ===")
        Log.i(TAG, "  timeout=${caps.defaultTimeoutMs}ms " +
            "maxBatch=${caps.maxBatchAddresses} " +
            "retries=${caps.retryCount} " +
            "atshSettle=${caps.atshSettleMs}ms " +
            "batchReliable=${caps.batchReliable} " +
            "stCommands=${caps.supportsStCommands}")
    }

    fun reset() {
        _adapterType.value = AdapterType.UNKNOWN
        _capabilities.value = AdapterCapabilities.UNKNOWN
        diagnostics.reset()
        Log.i(TAG, "Profile reset to UNKNOWN")
    }

    // ── Adaptive command send with retry ──────────────────────────────────────

    /**
     * Sends [cmd] with [timeoutMs] and retries up to [AdapterCapabilities.retryCount] times
     * on null (BT timeout). Retries are not triggered by NO-DATA adapter responses.
     *
     * A module is never disabled by this function — null from the last retry simply
     * propagates to the caller which applies per-PID error tracking.
     */
    suspend fun sendWithRetry(cmd: String, timeoutMs: Long? = null): String? {
        val caps    = activeCapabilities
        val timeout = timeoutMs ?: caps.defaultTimeoutMs
        val t0      = System.currentTimeMillis()

        var resp: String? = null
        repeat(caps.retryCount + 1) { attempt ->
            resp = btManager.sendCommand(cmd, timeout)
            if (resp != null) return resp
            if (attempt < caps.retryCount) {
                Log.d(TAG, "Retry ${attempt + 1}/${caps.retryCount} for cmd=$cmd")
                diagnostics.recordRetry()
                delay(50L)
            }
        }

        diagnostics.recordTimeout()
        Log.w(TAG, "cmd=$cmd timed out after ${caps.retryCount + 1} attempts " +
            "(${System.currentTimeMillis() - t0}ms)")
        return null
    }

    // ── SSM batch read fallback chain ─────────────────────────────────────────

    /**
     * Reads [addresses] from the currently-selected CAN module using the three-tier
     * fallback chain. The adapter's CAN header must be set by the caller beforehand.
     *
     * Tier 1 — FULL_BATCH: all addresses in one multi-frame ISO-TP command.
     * Tier 2 — HALF_BATCH: addresses split into chunks of [maxBatchAddresses]/2.
     * Tier 3 — SINGLE_READ: one A8 command per address (always succeeds if address exists).
     *
     * A module is NEVER disabled due to a timeout at any tier. If all tiers return partial
     * results, [BatchFallbackResult.missing] contains the addresses that genuinely returned
     * NO DATA (i.e. are not supported by this ECU).
     */
    suspend fun readSsmWithFallback(addresses: List<Int>): BatchFallbackResult {
        if (addresses.isEmpty()) return BatchFallbackResult.EMPTY
        val caps = activeCapabilities

        // ── Tier 1: Full batch ────────────────────────────────────────────────
        if (caps.batchReliable && addresses.size <= caps.maxBatchAddresses) {
            Log.d(TAG, "SSM Tier 1 FULL_BATCH: ${addresses.size} addresses")
            val result = capabilityProber.readSsmBatch(addresses, allowBatch = true)
            if (!result.batchFailed && result.values.size == addresses.size) {
                diagnostics.recordBatchResult(BatchTier.FULL_BATCH)
                Log.d(TAG, "FULL_BATCH succeeded: ${result.values.size} values")
                return BatchFallbackResult(result.values, BatchTier.FULL_BATCH,
                    addresses.toSet() - result.values.keys)
            }
            Log.w(TAG, "FULL_BATCH failed (got ${result.values.size}/${addresses.size}) → trying HALF_BATCH")
        }

        // ── Tier 2: Half-size chunks ──────────────────────────────────────────
        val halfSize = (caps.maxBatchAddresses / 2).coerceAtLeast(1)
        if (addresses.size > 1 && halfSize > 1) {
            Log.d(TAG, "SSM Tier 2 HALF_BATCH: ${addresses.size} addresses in chunks of $halfSize")
            val combined  = mutableMapOf<Int, Int>()
            var anyFailed = false
            for (chunk in addresses.chunked(halfSize)) {
                val result = capabilityProber.readSsmBatch(chunk, allowBatch = true)
                if (result.batchFailed) { anyFailed = true; break }
                combined += result.values
                if (chunk.size > 1) delay(caps.interCommandDelayMs.coerceAtLeast(10L))
            }
            if (!anyFailed && combined.size >= (addresses.size * 0.5).toInt()) {
                diagnostics.recordBatchResult(BatchTier.HALF_BATCH)
                Log.i(TAG, "HALF_BATCH succeeded: ${combined.size}/${addresses.size} values")
                return BatchFallbackResult(combined, BatchTier.HALF_BATCH,
                    addresses.toSet() - combined.keys)
            }
            Log.w(TAG, "HALF_BATCH failed (got ${combined.size}/${addresses.size}) → single reads")
        }

        // ── Tier 3: Single reads — never disabled ─────────────────────────────
        Log.i(TAG, "SSM Tier 3 SINGLE_READ: ${addresses.size} addresses (fallback, module NOT disabled)")
        val result = capabilityProber.readSsmBatch(addresses, allowBatch = false)
        diagnostics.recordBatchResult(BatchTier.SINGLE_READ)
        Log.d(TAG, "SINGLE_READ: ${result.values.size}/${addresses.size} values")
        return BatchFallbackResult(result.values, BatchTier.SINGLE_READ,
            addresses.toSet() - result.values.keys)
    }

    // ── ATSH settle ───────────────────────────────────────────────────────────

    /** Waits [AdapterCapabilities.atshSettleMs] milliseconds after an ATSH switch. */
    suspend fun settleAfterAtsh() = delay(activeCapabilities.atshSettleMs)
}
