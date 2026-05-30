package com.subaru.servicetool.data.bluetooth.adapter

/**
 * Static capability profile for a specific adapter type.
 *
 * These are baseline values tuned per hardware. At runtime [AdapterProfileManager]
 * may tighten timeouts further based on measured RTT.
 *
 * @param adapterType           Adapter hardware classification.
 * @param defaultTimeoutMs      Per-command BT timeout in milliseconds.
 * @param maxBatchAddresses     Max SSM A8 addresses in a single multi-frame request.
 *                              Determines the first tier of [BatchFallbackResult].
 * @param retryCount            Command retries on timeout (not on NO DATA).
 * @param interCommandDelayMs   Pause between consecutive ELM commands.
 * @param atshSettleMs          Settle time after an ATSH header switch.
 * @param supportsStCommands    OBDLink ST extended AT command set available.
 * @param batchReliable         Adapter reliably handles multi-frame ISO-TP requests.
 *                              When false, fallback starts at HALF_BATCH immediately.
 */
data class AdapterCapabilities(
    val adapterType: AdapterType,
    val defaultTimeoutMs: Long,
    val maxBatchAddresses: Int,
    val retryCount: Int,
    val interCommandDelayMs: Long,
    val atshSettleMs: Long,
    val supportsStCommands: Boolean,
    val batchReliable: Boolean,
) {
    companion object {

        val OBDLINK = AdapterCapabilities(
            adapterType          = AdapterType.OBDLINK,
            defaultTimeoutMs     = 500L,
            maxBatchAddresses    = 12,
            retryCount           = 1,
            interCommandDelayMs  = 0L,
            atshSettleMs         = 100L,
            supportsStCommands   = true,
            batchReliable        = true,
        )

        val VGATE = AdapterCapabilities(
            adapterType          = AdapterType.VGATE,
            defaultTimeoutMs     = 800L,
            maxBatchAddresses    = 8,
            retryCount           = 1,
            interCommandDelayMs  = 0L,
            atshSettleMs         = 200L,
            supportsStCommands   = false,
            batchReliable        = true,
        )

        val ELM327_GENUINE = AdapterCapabilities(
            adapterType          = AdapterType.ELM327_GENUINE,
            defaultTimeoutMs     = 1200L,
            maxBatchAddresses    = 6,
            retryCount           = 1,
            interCommandDelayMs  = 20L,
            atshSettleMs         = 300L,
            supportsStCommands   = false,
            batchReliable        = true,
        )

        val ELM327_CLONE = AdapterCapabilities(
            adapterType          = AdapterType.ELM327_CLONE,
            defaultTimeoutMs     = 2500L,
            maxBatchAddresses    = 3,
            retryCount           = 2,
            interCommandDelayMs  = 50L,
            atshSettleMs         = 400L,
            supportsStCommands   = false,
            batchReliable        = false,
        )

        val UNKNOWN = AdapterCapabilities(
            adapterType          = AdapterType.UNKNOWN,
            defaultTimeoutMs     = 2000L,
            maxBatchAddresses    = 6,
            retryCount           = 1,
            interCommandDelayMs  = 20L,
            atshSettleMs         = 300L,
            supportsStCommands   = false,
            batchReliable        = true,
        )

        fun forType(type: AdapterType): AdapterCapabilities = when (type) {
            AdapterType.OBDLINK         -> OBDLINK
            AdapterType.VGATE           -> VGATE
            AdapterType.ELM327_GENUINE  -> ELM327_GENUINE
            AdapterType.ELM327_CLONE    -> ELM327_CLONE
            AdapterType.UNKNOWN         -> UNKNOWN
        }
    }
}
