package com.subaru.servicetool.data.obd.pid

/**
 * Protocol-specific address of a PID.
 *
 * The address encodes both *how to reach* the data (the protocol) and *where* the
 * data lives (the mode/PID/memory address). It also carries the CAN header required
 * to route the request to the correct ECU on the vehicle CAN bus.
 *
 * Adding a new address type requires:
 *   1. Adding a subclass here.
 *   2. Implementing [transport.PidTransport] and [parser.ResponseParser] for the protocol.
 *   No other changes are needed in core polling code.
 */
sealed class PidAddress {

    /** CAN header to set with ATSH before sending this request. Null = use ELM327 default (7E0). */
    abstract val canHeader: String?

    /**
     * SAE J1979 Mode 01 address.
     * @param mode OBD service (e.g. 0x01 for current data, 0x03 for DTCs).
     * @param pid  Parameter ID byte (e.g. 0x0C for RPM).
     */
    data class Obd2(
        val mode: Int,
        val pid: Int,
        override val canHeader: String? = null,
    ) : PidAddress() {
        val commandString: String get() = "%02X%02X".format(mode, pid)
    }

    /**
     * Subaru SSM2 — 24-bit (3-byte) memory address, A8 service over CAN ISO-TP.
     * @param address 24-bit ECU/TCU memory address (e.g. 0x0000AF for oil temperature).
     */
    data class Ssm2(
        val address: Int,
        override val canHeader: String? = null,
    ) : PidAddress() {
        init { require(address in 0..0xFFFFFF) { "SSM2 address must fit in 24 bits: 0x%X".format(address) } }
    }

    /**
     * Subaru SSM4 — 32-bit (4-byte) extended memory address, A8 service over CAN ISO-TP.
     * Used for Gen5+ ECUs where the address space exceeds 24 bits.
     * @param address 32-bit ECU memory address.
     */
    data class Ssm4(
        val address: Long,
        override val canHeader: String? = null,
    ) : PidAddress() {
        init { require(address in 0L..0xFFFFFFFFL) { "SSM4 address must fit in 32 bits: 0x%X".format(address) } }
    }

    /**
     * ISO 14229 UDS ReadDataByIdentifier (Mode 22).
     * @param did 16-bit data identifier (e.g. 0x1017 for CVT fluid temperature).
     */
    data class Uds22(
        val did: Int,
        override val canHeader: String? = null,
    ) : PidAddress() {
        init { require(did in 0..0xFFFF) { "UDS DID must fit in 16 bits: 0x%X".format(did) } }
        val commandString: String get() = "22%04X".format(did)
    }
}
