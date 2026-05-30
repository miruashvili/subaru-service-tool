package com.subaru.servicetool.data.bluetooth.adapter

/**
 * Result of a single SSM batch read attempt, including which fallback tier was used.
 *
 * @param values   Address → raw data-byte map for every address that responded.
 * @param tier     Which tier of the fallback chain delivered the result.
 * @param missing  Addresses that returned no data at any tier (genuinely unsupported).
 */
data class BatchFallbackResult(
    val values: Map<Int, Int>,
    val tier: BatchTier,
    val missing: Set<Int> = emptySet(),
) {
    companion object {
        val EMPTY = BatchFallbackResult(emptyMap(), BatchTier.SINGLE_READ, emptySet())
    }
}
