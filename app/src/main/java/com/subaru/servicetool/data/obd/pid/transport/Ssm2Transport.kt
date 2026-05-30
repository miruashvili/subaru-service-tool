package com.subaru.servicetool.data.obd.pid.transport

import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.obd.pid.PidAddress
import com.subaru.servicetool.data.obd.pid.PidProtocol

/**
 * Transport for Subaru SSM2 — A8 memory-read service over CAN ISO-TP, 3-byte addresses.
 *
 * Command format: `05 A8 00 [addr_hi] [addr_mid] [addr_lo]`
 *  - `05`    = ISO 15765-4 single-frame length (5 data bytes follow)
 *  - `A8`    = Subaru SSM read-address service code
 *  - `00`    = read mode (single address)
 *  - 3 bytes = 24-bit memory address, big-endian
 *
 * The ELM327 adapter wraps these bytes in a CAN frame directed to the header set via ATSH.
 * The ECU responds with service code `E8` followed by the data byte(s).
 *
 * Example: address 0x0000AF (oil temperature) → command "05A8000000AF".
 */
object Ssm2Transport : PidTransport {

    override val protocol = PidProtocol.SSM2

    override suspend fun send(
        address: PidAddress,
        btManager: OBDBluetoothManager,
        timeoutMs: Long,
    ): String? {
        val addr = address as? PidAddress.Ssm2 ?: return null
        return btManager.sendCommand(buildCmd(addr.address), timeoutMs)
    }

    override fun buildCommand(address: PidAddress): String? =
        (address as? PidAddress.Ssm2)?.let { buildCmd(it.address) }

    private fun buildCmd(address: Int): String =
        "05A800%02X%02X%02X".format(
            (address shr 16) and 0xFF,
            (address shr 8) and 0xFF,
            address and 0xFF,
        )
}
