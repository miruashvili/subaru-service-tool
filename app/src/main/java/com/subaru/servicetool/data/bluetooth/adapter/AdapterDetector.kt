package com.subaru.servicetool.data.bluetooth.adapter

import android.util.Log
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager

private const val TAG = "AdapterDetector"

/**
 * Identifies the connected OBD adapter hardware by probing its responses to
 * standard and vendor-specific AT commands.
 *
 * Detection sequence (runs once after ELM327 initialisation):
 *
 *  1. Check BLE device name — Vgate iCar Pro has a recognisable name prefix.
 *  2. Send `STI` (OBDLink-specific identification command) — positive response
 *     containing "OBDLINK" confirms an OBDLink device.
 *  3. Parse `ATI` (ELM327 identification) — version string identifies genuine vs clone:
 *     - Genuine ELM327 chips ship as v1.5, v2.1, or v2.2.
 *     - Clone chips often report v1.5 but are significantly slower.
 *  4. Use RTT (from [OBDBluetoothManager.adapterSpeedProfile]) to differentiate
 *     genuine ELM327 from slow clones — clone adapters consistently miss the FAST tier.
 *
 * Detection is advisory and always produces a result; [AdapterType.UNKNOWN] is returned
 * when all heuristics are inconclusive.
 */
object AdapterDetector {

    suspend fun detect(
        btManager: OBDBluetoothManager,
        bleDeviceName: String?,
    ): AdapterType {
        Log.i(TAG, "Starting adapter detection (device='$bleDeviceName')")

        // ── 1. BLE device name heuristics ────────────────────────────────────
        if (bleDeviceName != null) {
            val name = bleDeviceName.uppercase()
            if ("ICAR" in name || "VGATE" in name || "VLINKER" in name || "FFE0" in name) {
                Log.i(TAG, "Detected VGATE by device name: '$bleDeviceName'")
                return AdapterType.VGATE
            }
            if ("OBDLINK" in name) {
                Log.i(TAG, "Detected OBDLINK by device name: '$bleDeviceName'")
                return AdapterType.OBDLINK
            }
        }

        // ── 2. OBDLink STI command ────────────────────────────────────────────
        val stiResponse = btManager.sendCommand("STI", 1_000L)
        Log.d(TAG, "STI response: ${stiResponse?.take(60)?.trim()}")
        if (stiResponse != null && "OBDLINK" in stiResponse.uppercase()) {
            Log.i(TAG, "Detected OBDLINK by STI response")
            return AdapterType.OBDLINK
        }

        // ── 3. ATI version string ─────────────────────────────────────────────
        val atiResponse = btManager.sendCommand("ATI", 1_500L)
        Log.d(TAG, "ATI response: ${atiResponse?.take(60)?.trim()}")
        val version = parseElmVersion(atiResponse)
        Log.d(TAG, "Parsed ELM version: $version")

        if (version == null) {
            Log.w(TAG, "Could not parse ATI — returning UNKNOWN")
            return AdapterType.UNKNOWN
        }

        // ── 4. RTT-based genuine vs clone discrimination ───────────────────────
        val profile = btManager.adapterSpeedProfile.value
        Log.d(TAG, "Current speed profile: ${profile.label}")

        return when {
            // OBDLink v2.2 is the most common OBDLink ELM self-identification
            version >= 2.2f && stiResponse == null -> {
                Log.i(TAG, "ELM v$version, fast profile, no STI → ELM327_GENUINE")
                AdapterType.ELM327_GENUINE
            }
            // Fast profile = genuine or high-quality clone
            profile.label == "FAST" -> {
                Log.i(TAG, "ELM v$version, FAST profile → ELM327_GENUINE")
                AdapterType.ELM327_GENUINE
            }
            // Medium profile = borderline
            profile.label == "MEDIUM" -> {
                Log.i(TAG, "ELM v$version, MEDIUM profile → ELM327_GENUINE (borderline)")
                AdapterType.ELM327_GENUINE
            }
            // Slow or minimal = clone
            else -> {
                Log.i(TAG, "ELM v$version, ${profile.label} profile → ELM327_CLONE (slow/unreliable)")
                AdapterType.ELM327_CLONE
            }
        }
    }

    private fun parseElmVersion(atiResponse: String?): Float? {
        if (atiResponse == null) return null
        val upper = atiResponse.uppercase()
        if ("ELM327" !in upper && "OBD" !in upper) return null
        // Match "v1.5", "v2.1", "v2.2", etc.
        val match = Regex("""v(\d+\.\d+)""", RegexOption.IGNORE_CASE)
            .find(atiResponse)
        return match?.groupValues?.getOrNull(1)?.toFloatOrNull()
    }
}
