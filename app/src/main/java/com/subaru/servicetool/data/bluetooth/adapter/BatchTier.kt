package com.subaru.servicetool.data.bluetooth.adapter

/**
 * Which tier of the SSM batch fallback chain succeeded for a given read.
 *
 * The fallback chain always tries the highest (fastest) tier first.
 * On failure it degrades to the next tier without disabling the module.
 */
enum class BatchTier {
    /** Full batch succeeded: all addresses sent in one multi-frame ISO-TP request. */
    FULL_BATCH,
    /** Full batch failed; half-size chunks succeeded. */
    HALF_BATCH,
    /** All batch variants failed; single-address reads used. Never disables a module. */
    SINGLE_READ,
}
