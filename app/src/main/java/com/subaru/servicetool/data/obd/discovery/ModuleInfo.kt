package com.subaru.servicetool.data.obd.discovery

/**
 * Runtime discovery result for a single [SubaruModule].
 *
 * @param module             The module type this result describes.
 * @param canHeader          CAN header used during discovery (e.g. "7E0").
 * @param responseHeader     Expected response CAN ID (e.g. "7E8").
 * @param status             Whether the module was found, absent, or could not be probed.
 * @param respondingAddresses SSM A8 memory addresses that returned valid data during the
 *                            discovery sweep. Empty for absent modules or modules with no
 *                            SSM address candidates (e.g. TPMS uses UDS DIDs only).
 * @param respondingDids      UDS Mode-22 DIDs (as raw ints, e.g. 0x1501) that returned a
 *                            positive 62-response during discovery. Empty for SSM-only modules.
 * @param probeMs             Wall-clock time the discovery sweep took in milliseconds.
 * @param discoveredAt        Epoch-ms timestamp when discovery completed.
 */
data class ModuleInfo(
    val module: SubaruModule,
    val canHeader: String,
    val responseHeader: String,
    val status: ModuleStatus,
    val respondingAddresses: Set<Int> = emptySet(),
    val respondingDids: Set<Int> = emptySet(),
    val probeMs: Long = 0L,
    val discoveredAt: Long = System.currentTimeMillis(),
) {
    val isPresent: Boolean get() = status == ModuleStatus.PRESENT

    override fun toString(): String = buildString {
        append("ModuleInfo(${module.displayName} ${module.canHeader}→${module.responseHeader}")
        append(" status=$status")
        if (respondingAddresses.isNotEmpty())
            append(" addresses=${respondingAddresses.size}:${respondingAddresses.joinToString(",") { "0x%06X".format(it) }}")
        if (respondingDids.isNotEmpty())
            append(" dids=${respondingDids.size}:${respondingDids.joinToString(",") { "0x%04X".format(it) }}")
        append(" probeMs=$probeMs)")
    }
}
