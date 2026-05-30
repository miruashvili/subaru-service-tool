package com.subaru.servicetool.data.obd.pid.transport

import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.obd.pid.PidAddress
import com.subaru.servicetool.data.obd.pid.PidProtocol

/**
 * Responsible for converting a [PidAddress] into an ELM327 command string,
 * sending it via [btManager], and returning the raw adapter response.
 *
 * Each [PidProtocol] has exactly one [PidTransport] implementation.
 * Implementations must not parse the response — that is [parser.ResponseParser]'s job.
 *
 * Implementations are stateless; the same instance can be used across coroutines.
 */
interface PidTransport {

    /** The protocol this transport handles. */
    val protocol: PidProtocol

    /**
     * Builds the ELM327 command string for [address] and sends it to the adapter.
     *
     * @param address    The PID address (must be compatible with [protocol]).
     * @param btManager  Bluetooth adapter manager for sending commands.
     * @param timeoutMs  Per-command BT timeout in milliseconds.
     * @return Raw adapter response string, or null on timeout / wrong protocol type.
     */
    suspend fun send(
        address: PidAddress,
        btManager: OBDBluetoothManager,
        timeoutMs: Long,
    ): String?

    /**
     * Returns the ELM327 command string that would be sent for [address],
     * without actually sending it. Used by logging and testing.
     */
    fun buildCommand(address: PidAddress): String?
}
