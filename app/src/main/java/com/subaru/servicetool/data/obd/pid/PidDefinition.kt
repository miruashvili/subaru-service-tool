package com.subaru.servicetool.data.obd.pid

import com.subaru.servicetool.data.obd.SensorModule
import com.subaru.servicetool.data.obd.SensorPriority

/**
 * Complete, self-describing definition of a single vehicle sensor PID.
 *
 * A [PidDefinition] is purely declarative — it carries no BT dependencies and can be
 * created and registered at any time, including at runtime after dynamic module discovery.
 *
 * The three stages of value acquisition are intentionally separated:
 *  - [protocol] selects the [transport.PidTransport] that formats the ELM327 command.
 *  - [address] carries the address/DID and the CAN header needed to route the request.
 *  - [decoder] converts the raw bytes returned by the [parser.ResponseParser] to a float.
 *
 * @param name          Human-readable sensor name (e.g. "Engine RPM").
 * @param module        Physical ECU that owns this sensor.
 * @param protocol      Wire protocol to use for reading.
 * @param address       Protocol-specific address (mode/PID, SSM memory address, UDS DID).
 * @param length        Expected number of data bytes in the response (for validation/logging).
 * @param scaling       Scaling formula descriptor (metadata; actual computation in [decoder]).
 * @param unit          Engineering unit string (e.g. "rpm", "°C", "%").
 * @param priority      Polling tier — maps to the engine's HIGH/MEDIUM/LOW scheduler.
 * @param decoder       Converts raw bytes → Float (or null on error).
 * @param isTurboOnly   When true, excluded from the active PID set on NA engines.
 * @param minVal        Minimum expected physical value (for gauge display bounds).
 * @param maxVal        Maximum expected physical value (for gauge display bounds).
 * @param description   Optional human-readable description of what this PID measures.
 */
data class PidDefinition(
    val name: String,
    val module: SensorModule,
    val protocol: PidProtocol,
    val address: PidAddress,
    val length: Int,
    val scaling: PidScaling,
    val unit: String,
    val priority: SensorPriority,
    val decoder: PidDecoder,
    val isTurboOnly: Boolean = false,
    val minVal: Float = 0f,
    val maxVal: Float = 100f,
    val description: String = "",
) {
    /** Stable lookup key: protocol name + address hash. */
    val key: String get() = "${protocol.name}:${addressKey()}"

    private fun addressKey(): String = when (val a = address) {
        is PidAddress.Obd2  -> "OBD2_%02X_%02X".format(a.mode, a.pid)
        is PidAddress.Ssm2  -> "SSM2_%06X".format(a.address)
        is PidAddress.Ssm4  -> "SSM4_%08X".format(a.address)
        is PidAddress.Uds22 -> "UDS22_%04X_%s".format(a.did, a.canHeader ?: "7E0")
    }

    override fun toString(): String =
        "PidDefinition(${protocol.name} $name ${address.canHeader ?: "7E0"} unit=$unit priority=$priority)"
}
