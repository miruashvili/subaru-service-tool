package com.subaru.servicetool.data.obd.pid.parser

import com.subaru.servicetool.data.obd.pid.PidAddress
import com.subaru.servicetool.data.obd.pid.PidProtocol

/**
 * Extracts a list of raw data bytes from a raw ELM327 adapter response string.
 *
 * The parser is the second stage of the value-acquisition pipeline:
 *
 *   [transport.PidTransport] → raw String → **[ResponseParser]** → List<Int> → [PidDecoder]
 *
 * Each [PidProtocol] has one [ResponseParser] implementation. Implementations are stateless
 * and carry no BT dependencies — they operate purely on the string returned by the transport.
 */
interface ResponseParser {

    /** The protocol this parser handles. */
    val protocol: PidProtocol

    /**
     * Parses [raw] (the ELM327 adapter response) and extracts the data bytes for [address].
     *
     * @return List of data bytes as unsigned ints (0–255), or null if the response signals
     *         an error (NO DATA, UNABLE TO CONNECT, etc.) or cannot be parsed.
     */
    fun parse(raw: String, address: PidAddress): List<Int>?
}
