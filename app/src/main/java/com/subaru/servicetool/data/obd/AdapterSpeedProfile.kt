package com.subaru.servicetool.data.obd

enum class AdapterSpeedProfile(
    val label: String,
    val delayBetweenPidsMs: Long,
    val delayBetweenCyclesMs: Long,
    val tier2Every: Int,
    val tier3Every: Int,
    val tier4Every: Int,
    val commandTimeoutMs: Long,
    val keepAliveIntervalMs: Long,
) {
    FAST(
        label                = "Fast",
        delayBetweenPidsMs   = 0L,
        delayBetweenCyclesMs = 50L,
        tier2Every           = 2,
        tier3Every           = 2,
        tier4Every           = 8,
        commandTimeoutMs     = 800L,
        keepAliveIntervalMs  = 3_000L,
    ),
    MEDIUM(
        label                = "Medium",
        delayBetweenPidsMs   = 20L,
        delayBetweenCyclesMs = 100L,
        tier2Every           = 3,
        tier3Every           = 3,
        tier4Every           = 8,
        commandTimeoutMs     = 1_200L,
        keepAliveIntervalMs  = 2_000L,
    ),
    SLOW(
        label                = "Slow",
        delayBetweenPidsMs   = 50L,
        delayBetweenCyclesMs = 200L,
        tier2Every           = 3,
        tier3Every           = 5,
        tier4Every           = 8,
        commandTimeoutMs     = 2_000L,
        keepAliveIntervalMs  = 1_500L,
    ),
    MINIMAL(
        label                = "Minimal",
        delayBetweenPidsMs   = 100L,
        delayBetweenCyclesMs = 500L,
        tier2Every           = 5,
        tier3Every           = 8,
        tier4Every           = 10,
        commandTimeoutMs     = 2_500L,
        keepAliveIntervalMs  = 1_000L,
    );

    fun downgrade(): AdapterSpeedProfile = when (this) {
        FAST    -> MEDIUM
        MEDIUM  -> SLOW
        SLOW    -> MINIMAL
        MINIMAL -> MINIMAL
    }

    fun upgrade(): AdapterSpeedProfile = when (this) {
        FAST    -> FAST
        MEDIUM  -> FAST
        SLOW    -> MEDIUM
        MINIMAL -> SLOW
    }
}
