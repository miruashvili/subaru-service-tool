package com.subaru.servicetool.data.obd.pid.parser

import com.subaru.servicetool.data.obd.ObdParser
import com.subaru.servicetool.data.obd.pid.PidAddress
import com.subaru.servicetool.data.obd.pid.PidProtocol

/**
 * Parses SAE J1979 Mode 01 OBD-II responses.
 *
 * Delegates to [ObdParser.parseStandard] which locates the `4{mode}` reply byte and PID echo
 * in the adapter response and returns all subsequent bytes as data.
 *
 * Input example:  `"7E8 04 41 0C 1A F8 >"`
 * Parsed command: `"010C"` (mode=01, PID=0C)
 * Output:         `[0x1A, 0xF8]` → RPM = (26×256 + 248) / 4 = 1726 rpm
 */
object Obd2ResponseParser : ResponseParser {

    override val protocol = PidProtocol.OBD2

    override fun parse(raw: String, address: PidAddress): List<Int>? {
        val addr = address as? PidAddress.Obd2 ?: return null
        return ObdParser.parseStandard(raw, addr.commandString)
    }
}
