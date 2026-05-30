package com.subaru.servicetool.data.obd.pid.transport

import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.obd.pid.PidAddress
import com.subaru.servicetool.data.obd.pid.PidProtocol

/**
 * Transport for Subaru SSM4 — A8 memory-read service over CAN ISO-TP, 4-byte addresses.
 *
 * Command format: `07 A8 00 [b3] [b2] [b1] [b0]`
 *  - `07`    = ISO 15765-4 single-frame length (7 data bytes follow)
 *  - `A8`    = Subaru SSM read-address service code
 *  - `00`    = read mode (single address)
 *  - 4 bytes = 32-bit memory address, big-endian
 *
 * SSM4 extends the 24-bit SSM2 address space to 32 bits for Gen5+ Subaru ECUs.
 * The ECU responds with service code `E8` followed by the data byte(s), identical to SSM2.
 *
 * Example: address 0x00102030 → command "07A800 00 10 20 30".
 */
object Ssm4Transport : PidTransport {

    override val protocol = PidProtocol.SSM4

    override suspend fun send(
        address: PidAddress,
        btManager: OBDBluetoothManager,
        timeoutMs: Long,
    ): String? {
        val addr = address as? PidAddress.Ssm4 ?: return null
        return btManager.sendCommand(buildCmd(addr.address), timeoutMs)
    }

    override fun buildCommand(address: PidAddress): String? =
        (address as? PidAddress.Ssm4)?.let { buildCmd(it.address) }

    private fun buildCmd(address: Long): String =
        "07A800%02X%02X%02X%02X".format(
            (address shr 24) and 0xFF,
            (address shr 16) and 0xFF,
            (address shr 8) and 0xFF,
            address and 0xFF,
        )
}
