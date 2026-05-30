package com.subaru.servicetool.data.obd.pid

/**
 * Wire protocol used to read a PID's value from the vehicle.
 *
 * Each protocol has its own [transport][transport.PidTransport] that formats the ELM327
 * command and its own [parser][parser.ResponseParser] that extracts raw bytes from the
 * adapter response. Adding support for a new protocol requires only implementing those
 * two interfaces — no other code changes are needed.
 */
enum class PidProtocol {

    /**
     * SAE J1979 Mode 01 — standard OBD-II (works on any ELM327 adapter).
     * Request: `{mode:02X}{pid:02X}` (e.g. "010C").
     * Response parsed by looking for `4{mode}` + PID echo followed by data bytes.
     */
    OBD2,

    /**
     * Subaru SSM2 — A8 memory-read service over CAN ISO-TP, 3-byte (24-bit) addresses.
     * Request format: 05 A8 00 addr_hi addr_mid addr_lo.
     * Response: E8 data... — one byte per requested address.
     * Covers all currently-known SSM addresses on FA/FB/EJ/EZ engine families.
     */
    SSM2,

    /**
     * Subaru SSM4 — A8 memory-read service over CAN ISO-TP, 4-byte (32-bit) addresses.
     * Request format: 07 A8 00 b3 b2 b1 b0.
     * Response: E8 data... (identical format to SSM2).
     * Provides access to extended ECU address space on Gen5+ Subaru ECUs where the
     * address space exceeds 24 bits.
     */
    SSM4,

    /**
     * ISO 14229 UDS ReadDataByIdentifier — Mode 22 request / 62 positive response.
     * Request format: 22 DID_HI DID_LO (e.g. "221017" for DID 0x1017).
     * Used for ECU Mode 21/22 Subaru-proprietary sensors and BCM/TPMS data access.
     */
    UDS22,
}
