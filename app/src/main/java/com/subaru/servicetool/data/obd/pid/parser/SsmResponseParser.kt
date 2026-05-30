package com.subaru.servicetool.data.obd.pid.parser

import com.subaru.servicetool.data.obd.ObdParser
import com.subaru.servicetool.data.obd.pid.PidAddress
import com.subaru.servicetool.data.obd.pid.PidProtocol

/**
 * Parses Subaru SSM A8 single-address read responses (used by both SSM2 and SSM4).
 *
 * The ECU responds with service code `E8` followed by optional address-echo bytes and
 * then the data byte. [ObdParser.parseSsmResponse] handles all three adapter response
 * variants (long/medium/short form — with or without address echo).
 *
 * Input example:  `"7E8 06 E8 00 00 AF 5C >"`   (long form, address echo)
 * Parsed data:    `[0x5C]` → oil temp = 0x5C − 40 = 52 °C
 *
 * The single data byte is wrapped in a list for uniform handling by [PidDecoder].
 *
 * This parser handles both [PidProtocol.SSM2] (3-byte addresses) and
 * [PidProtocol.SSM4] (4-byte addresses) since the response format is identical.
 */
object SsmResponseParser : ResponseParser {

    override val protocol = PidProtocol.SSM2 // also registered for SSM4 in SubaruPidRegistry

    fun parseForProtocol(protocol: PidProtocol) = object : ResponseParser {
        override val protocol = protocol
        override fun parse(raw: String, address: PidAddress): List<Int>? =
            ObdParser.parseSsmResponse(raw)?.let { listOf(it) }
    }

    override fun parse(raw: String, address: PidAddress): List<Int>? =
        ObdParser.parseSsmResponse(raw)?.let { listOf(it) }
}

/** Parser registered for [PidProtocol.SSM4] — identical response format to SSM2. */
object Ssm4ResponseParser : ResponseParser {
    override val protocol = PidProtocol.SSM4
    override fun parse(raw: String, address: PidAddress): List<Int>? =
        ObdParser.parseSsmResponse(raw)?.let { listOf(it) }
}
