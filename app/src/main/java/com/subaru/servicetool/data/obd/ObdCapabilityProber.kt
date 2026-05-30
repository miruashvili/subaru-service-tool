package com.subaru.servicetool.data.obd

import android.util.Log
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ObdCapabilityProber"

/**
 * Implements the ActiveOBD-style capability probe + SSM A8 multi-address batch read.
 *
 * Subaru ECUs do not advertise a fixed PID list — instead the tool probes each candidate
 * SSM memory address once, marks the ones that respond, and afterwards reads only those
 * addresses. Multiple addresses are read in a single A8 "read-block" command (E8 response),
 * with a safe fall-back to one-address-per-command for adapters that can't segment the
 * multi-frame request.
 *
 * Header switching (ATSH) is performed by the probe entry points but NOT by [readSsmBatch] —
 * the polling engine owns the header for the rest of the poll cycle.
 */
@Singleton
class ObdCapabilityProber @Inject constructor(
    private val btManager: OBDBluetoothManager,
) {
    companion object {
        /**
         * FB25 NA (non-turbo) verified ECU memory addresses (header 7E0).
         * Turbo-only addresses (0x003018 knock, 0x0010C9 wastegate, 0x00105F throttle motor)
         * are intentionally excluded — they don't exist on the NA boxer.
         */
        val CANDIDATE_ECU_ADDRESSES = listOf(
            0x000008,  // Coolant Temp (SSM backup)        A − 40
            0x0000AF,  // Engine Oil Temp                  A − 40
            0x0010A1,  // MAF Sensor Voltage               A × 0.02
            0x0010A3,  // Injector 1 Pulse Width
            0x0010A5,  // Learned Ignition Timing
            0x0010A6,  // Accelerator Pedal Angle          A × 100 / 255
            0x0010B2,  // Alternator Duty                  A × 100 / 255
            0x0010B4,  // VVT Advance Right                (A − 128) × 0.5
            0x0010B5,  // VVT Advance Left                 (A − 128) × 0.5
            0x001136,  // Battery Temperature              A − 40
        )

        /** FB25 NA verified TCU memory addresses (header 7E1). */
        val CANDIDATE_TCU_ADDRESSES = listOf(
            0x001017,  // CVT Fluid Temp                   A − 40
            0x001065,  // AWD Transfer Duty                A × 100 / 255
            0x001045,  // CVT Lock-Up Duty                 A × 100 / 255
        )

        private const val PROBE_TIMEOUT_MS  = 800L
        private const val SINGLE_TIMEOUT_MS = 2_000L
        private const val BATCH_TIMEOUT_MS  = 2_500L
        private const val HEADER_TIMEOUT_MS = 2_000L
        private const val SETTLE_MS         = 300L
        private const val PROBE_GAP_MS      = 30L
    }

    /** Result of a (possibly batched) SSM address read. */
    data class BatchReadResult(
        val values: Map<Int, Int>,
        val batchFailed: Boolean,
    )

    // ── Capability probe ──────────────────────────────────────────────────────

    /** Probes ECU (7E0) candidate addresses; returns the set that responded. Leaves header on 7E0. */
    suspend fun probeEcuCapabilities(): Set<Int> {
        val supported = probeModule("7E0", CANDIDATE_ECU_ADDRESSES)
        Log.i(TAG, "ECU caps (${supported.size}): ${supported.toHexList()}")
        return supported
    }

    /** Probes TCU (7E1) candidate addresses; restores header to 7E0 afterwards. */
    suspend fun probeTcuCapabilities(): Set<Int> {
        val supported = probeModule("7E1", CANDIDATE_TCU_ADDRESSES)
        btManager.sendCommand("ATSH7E0", HEADER_TIMEOUT_MS)
        Log.i(TAG, "TCU caps (${supported.size}): ${supported.toHexList()}")
        return supported
    }

    private suspend fun probeModule(header: String, addresses: List<Int>): Set<Int> {
        if (btManager.sendCommand("ATSH$header", HEADER_TIMEOUT_MS) == null) {
            Log.w(TAG, "ATSH$header timed out — capability probe skipped")
            return emptySet()
        }
        delay(SETTLE_MS)
        val supported = linkedSetOf<Int>()
        for (addr in addresses) {
            val resp = btManager.sendCommand(buildSsmA8Single(addr), PROBE_TIMEOUT_MS)
            if (resp != null && ObdParser.parseSsmResponse(resp) != null) supported += addr
            delay(PROBE_GAP_MS)
        }
        return supported
    }

    // ── Batch read ────────────────────────────────────────────────────────────

    /**
     * Reads [addresses] from the currently-selected module. When [allowBatch] is true and more
     * than one address is requested, a single A8 multi-address command is tried first; if the
     * E8 response doesn't contain one data byte per address, [BatchReadResult.batchFailed] is set
     * and the read is completed with reliable single-address commands.
     *
     * The caller is responsible for having set the correct ATSH header beforehand.
     */
    suspend fun readSsmBatch(addresses: List<Int>, allowBatch: Boolean): BatchReadResult {
        if (addresses.isEmpty()) return BatchReadResult(emptyMap(), batchFailed = false)

        if (allowBatch && addresses.size > 1) {
            val resp = btManager.sendCommand(buildSsmA8Batch(addresses), BATCH_TIMEOUT_MS)
            val parsed = parseSsmBatchResponse(resp, addresses)
            if (parsed.size == addresses.size) {
                return BatchReadResult(parsed, batchFailed = false)
            }
            Log.w(TAG, "A8 batch read returned ${parsed.size}/${addresses.size} bytes — falling back to single reads")
            return BatchReadResult(readSingles(addresses), batchFailed = true)
        }

        return BatchReadResult(readSingles(addresses), batchFailed = false)
    }

    private suspend fun readSingles(addresses: List<Int>): Map<Int, Int> {
        val out = LinkedHashMap<Int, Int>()
        for (addr in addresses) {
            val resp = btManager.sendCommand(buildSsmA8Single(addr), SINGLE_TIMEOUT_MS) ?: continue
            ObdParser.parseSsmResponse(resp)?.let { out[addr] = it }
        }
        return out
    }

    // ── Command builders ──────────────────────────────────────────────────────

    /**
     * Single SSM-over-CAN read: `05 A8 00 <addr_hi> <addr_mid> <addr_lo>`.
     * `05` = ISO 15765-4 single-frame length (5 bytes follow). `A8` = SSM read-address service.
     */
    fun buildSsmA8Single(address: Int): String =
        "05A800%02X%02X%02X".format(
            (address shr 16) and 0xFF,
            (address shr 8) and 0xFF,
            address and 0xFF,
        )

    /**
     * Multi-address SSM read: `<len> A8 00 <addr1×3> <addr2×3> …`.
     * The leading byte is the ISO-TP payload length; the ELM327 segments the request into
     * multiple CAN frames when it exceeds a single frame. The ECU replies with `E8` followed
     * by one data byte per requested address, in order.
     */
    fun buildSsmA8Batch(addresses: List<Int>): String {
        val payload = StringBuilder("A800")
        for (addr in addresses) {
            payload.append(
                "%02X%02X%02X".format(
                    (addr shr 16) and 0xFF,
                    (addr shr 8) and 0xFF,
                    addr and 0xFF,
                )
            )
        }
        val lenBytes = payload.length / 2
        return "%02X".format(lenBytes) + payload
    }

    /**
     * Parses an A8 batch (E8) response. Each data byte after the E8 marker maps to the requested
     * address at the same index. Address echoes are not expected in the multi-read response.
     */
    fun parseSsmBatchResponse(raw: String?, addresses: List<Int>): Map<Int, Int> {
        if (raw == null) return emptyMap()
        val clean = raw.replace(" ", "").replace("\r", "").replace("\n", "")
            .replace(">", "").uppercase()
        for (noise in listOf("NODATA", "ERROR", "UNABLE", "BUSBUSY", "BUFFERFULL")) {
            if (noise in clean) return emptyMap()
        }
        val e8idx = clean.indexOf("E8")
        if (e8idx < 0) return emptyMap()
        val dataHex = clean.substring(e8idx + 2)
        val result = LinkedHashMap<Int, Int>()
        for ((i, addr) in addresses.withIndex()) {
            val pos = i * 2
            if (pos + 2 > dataHex.length) break
            dataHex.substring(pos, pos + 2).toIntOrNull(16)?.let { result[addr] = it }
        }
        return result
    }

    private fun Set<Int>.toHexList(): String =
        joinToString(", ") { "0x%06X".format(it) }
}
