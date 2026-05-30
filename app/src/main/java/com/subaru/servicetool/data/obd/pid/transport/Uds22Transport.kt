package com.subaru.servicetool.data.obd.pid.transport

import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.obd.pid.PidAddress
import com.subaru.servicetool.data.obd.pid.PidProtocol

/**
 * Transport for ISO 14229 UDS ReadDataByIdentifier (Mode 22).
 *
 * Command format: 22 DID_HI DID_LO (e.g. "221017" for DID 0x1017).
 * The ECU responds with 62 DID_HI DID_LO data... on success.
 *
 * Used for Subaru ECU Mode 21/22 proprietary sensors and BCM/TPMS data.
 * Header must be set via ATSH by the caller before sending (the transport
 * does not manage CAN header switching).
 */
object Uds22Transport : PidTransport {

    override val protocol = PidProtocol.UDS22

    override suspend fun send(
        address: PidAddress,
        btManager: OBDBluetoothManager,
        timeoutMs: Long,
    ): String? {
        val addr = address as? PidAddress.Uds22 ?: return null
        return btManager.sendCommand(addr.commandString, timeoutMs)
    }

    override fun buildCommand(address: PidAddress): String? =
        (address as? PidAddress.Uds22)?.commandString
}
