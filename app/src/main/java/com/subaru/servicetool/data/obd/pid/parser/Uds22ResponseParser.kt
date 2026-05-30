package com.subaru.servicetool.data.obd.pid.parser

import com.subaru.servicetool.data.obd.ObdParser
import com.subaru.servicetool.data.obd.pid.PidAddress
import com.subaru.servicetool.data.obd.pid.PidProtocol

/**
 * Parses ISO 14229 UDS ReadDataByIdentifier (Mode 22 / 62) responses.
 *
 * Delegates to [ObdParser.parseUdsResponse] which scans for the `62` positive-response byte
 * followed by the DID echo, then collects all subsequent bytes as data.
 *
 * Input example:  `"7E8 06 62 10 17 3C >"`   (DID 0x1017, CVT fluid temp)
 * Parsed command: `"221017"`
 * Output:         `[0x3C]` → CVT temp = 0x3C − 40 = 20 °C
 *
 * Works for both 2-byte DIDs (Mode 22) and Subaru's Mode 21 variant (reply byte 0x61).
 */
object Uds22ResponseParser : ResponseParser {

    override val protocol = PidProtocol.UDS22

    override fun parse(raw: String, address: PidAddress): List<Int>? {
        val addr = address as? PidAddress.Uds22 ?: return null
        return ObdParser.parseUdsResponse(raw, addr.commandString)
    }
}
