package com.subaru.servicetool.data.obd.pid.transport

import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.obd.pid.PidAddress
import com.subaru.servicetool.data.obd.pid.PidProtocol

/**
 * Transport for SAE J1979 Mode 01 (standard OBD-II) PIDs.
 *
 * Command format: `{mode:02X}{pid:02X}` (e.g. "010C" for RPM).
 * The ELM327 adapter routes the request to all ECUs on the bus (functional address 0x7DF)
 * unless a specific ATSH header is set. Most OBD-II Mode 01 PIDs work without a header.
 */
object Obd2Transport : PidTransport {

    override val protocol = PidProtocol.OBD2

    override suspend fun send(
        address: PidAddress,
        btManager: OBDBluetoothManager,
        timeoutMs: Long,
    ): String? {
        val addr = address as? PidAddress.Obd2 ?: return null
        return btManager.sendCommand(addr.commandString, timeoutMs)
    }

    override fun buildCommand(address: PidAddress): String? =
        (address as? PidAddress.Obd2)?.commandString
}
