package com.subaru.servicetool.data.obd

/**
 * Parses ELM327 / OBD-II adapter responses.
 *
 * ELM327 with ATH1 (headers on, used in our init sequence):
 *   "7E8 04 41 0C 1A F8 >"
 *
 * ELM327 with ATH0 (headers off):
 *   "41 0C 1A F8 >"
 *
 * Both are handled by searching for the mode-response byte (0x40 + mode) followed
 * by the PID byte, then collecting all subsequent hex tokens as data bytes.
 */
object ObdParser {

    private val NOISE_PATTERNS = listOf(
        "NO DATA", "ERROR", "UNABLE", "SEARCHING", "BUS BUSY", "FB ERROR",
        "DATA ERROR", "BUFFER FULL", "CAN ERROR",
    )

    /**
     * Parses data bytes from a standard OBD-II response.
     * [cmd] is the command string, e.g. "010C" (mode=01, PID=0C).
     * Returns the data bytes following the mode+PID echo, or null on error/unsupported.
     */
    fun parseStandard(response: String, cmd: String): List<Int>? {
        if (cmd.length < 4) return null
        val mode = cmd.substring(0, 2).toIntOrNull(16) ?: return null
        val pidHex = cmd.substring(2, 4).uppercase()
        return parseDataBytes(response, mode, pidHex)
    }

    /**
     * Parses a battery voltage response from ATRV (e.g. "14.2V >").
     */
    fun parseVoltage(response: String): Float? =
        "(\\d{1,2}\\.\\d{1,2})".toRegex().find(response)?.value?.toFloatOrNull()

    /**
     * Parses stored DTC codes from a Mode 03 response into P/C/B/U strings (e.g. "P0971").
     * Returns an empty list if no DTCs or on parse error.
     */
    fun parseDtcCodes(response: String): List<String> {
        val tokens = tokenize(response) ?: return emptyList()
        val idx = tokens.indexOfFirst { it == "43" }
        if (idx < 0) return emptyList()
        val dtcTokens = tokens.drop(idx + 1)
        val codes = mutableListOf<String>()
        var i = 0
        while (i + 1 < dtcTokens.size) {
            val hi = dtcTokens[i].toIntOrNull(16) ?: 0
            val lo = dtcTokens[i + 1].toIntOrNull(16) ?: 0
            if (hi != 0 || lo != 0) {
                val type = when ((hi shr 6) and 0x3) { 0 -> "P"; 1 -> "C"; 2 -> "B"; else -> "U" }
                val d1 = (hi shr 4) and 0x3
                val d2 = hi and 0xF
                val d3 = (lo shr 4) and 0xF
                val d4 = lo and 0xF
                codes.add("$type$d1${d2.toString(16).uppercase()}${d3.toString(16).uppercase()}${d4.toString(16).uppercase()}")
            }
            i += 2
        }
        return codes
    }

    /**
     * Counts stored DTCs from a Mode 03 response.
     * Each non-zero 2-byte pair after the "43" marker is one DTC.
     */
    fun parseDtcCount(response: String): Int {
        val tokens = tokenize(response) ?: return 0
        val idx = tokens.indexOfFirst { it == "43" }
        if (idx < 0) return 0
        val dtcTokens = tokens.drop(idx + 1)
        var count = 0
        var i = 0
        while (i + 1 < dtcTokens.size) {
            val hi = dtcTokens[i].toIntOrNull(16) ?: 0
            val lo = dtcTokens[i + 1].toIntOrNull(16) ?: 0
            if (hi != 0 || lo != 0) count++
            i += 2
        }
        return count
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun parseDataBytes(response: String, mode: Int, pidHex: String): List<Int>? {
        val tokens = tokenize(response) ?: return null
        val replyByte = (0x40 + mode).toString(16).uppercase().padStart(2, '0')

        // Scan for: <replyByte> <pidHex> <data...>
        for (i in 0 until tokens.size - 1) {
            if (tokens[i] == replyByte && tokens[i + 1] == pidHex) {
                return tokens.drop(i + 2).mapNotNull { it.toIntOrNull(16) }
            }
        }
        return null
    }

    /**
     * Normalises a raw adapter response string and splits it into 2-char hex tokens.
     * Returns null if the response signals an error or contains no useful data.
     */
    private fun tokenize(raw: String): List<String>? {
        val upper = raw.uppercase()
            .replace(">", " ")
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()

        if (upper.isEmpty()) return null
        for (noise in NOISE_PATTERNS) {
            if (noise in upper) return null
        }

        // Keep only tokens that look like 1- to 3-digit hex values.
        // Tokens longer than 3 chars are CAN IDs (e.g. "7E8") — still included so
        // we can search through them; the reply-byte search handles the skip.
        return upper.split("\\s+".toRegex()).filter { tok ->
            tok.isNotBlank() && tok.all { c -> c in '0'..'9' || c in 'A'..'F' }
        }
    }
}
