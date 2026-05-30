package com.subaru.servicetool.data.obd.pid

import android.util.Log
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.obd.SensorModule
import com.subaru.servicetool.data.obd.SensorPriority
import com.subaru.servicetool.data.obd.pid.parser.Obd2ResponseParser
import com.subaru.servicetool.data.obd.pid.parser.ResponseParser
import com.subaru.servicetool.data.obd.pid.parser.Ssm4ResponseParser
import com.subaru.servicetool.data.obd.pid.parser.SsmResponseParser
import com.subaru.servicetool.data.obd.pid.parser.Uds22ResponseParser
import com.subaru.servicetool.data.obd.pid.transport.Obd2Transport
import com.subaru.servicetool.data.obd.pid.transport.PidTransport
import com.subaru.servicetool.data.obd.pid.transport.Ssm2Transport
import com.subaru.servicetool.data.obd.pid.transport.Ssm4Transport
import com.subaru.servicetool.data.obd.pid.transport.Uds22Transport
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SubaruPidRegistry"

/**
 * Central registry for all known Subaru vehicle PIDs.
 *
 * ## Extension without changing core code
 * Future PIDs — from dynamic discovery, third-party plugins, or new model support —
 * can be added at any time by calling [register]. No changes to pollers, parsers,
 * transports, or any other infrastructure are required.
 *
 * ```kotlin
 * registry.register(
 *     PidDefinition(
 *         name     = "My New Sensor",
 *         module   = SensorModule.ECU,
 *         protocol = PidProtocol.SSM2,
 *         address  = PidAddress.Ssm2(0x001234),
 *         length   = 1,
 *         scaling  = PidScaling.Linear(0.02f),
 *         unit     = "V",
 *         priority = SensorPriority.NORMAL,
 *         decoder  = PidDecoder.linear(0.02f),
 *     )
 * )
 * ```
 *
 * ## Execution pipeline
 * Use [execute] to run the full Transport → Parser → Decoder pipeline for a PID:
 *
 * ```kotlin
 * val value: Float? = registry.execute(definition, btManager, timeoutMs = 2000L)
 * ```
 *
 * ## Pre-registered PIDs
 * All standard Subaru PIDs are registered at init via [SubaruPidDefinitions.ALL].
 */
@Singleton
class SubaruPidRegistry @Inject constructor() {

    private val _definitions = mutableListOf<PidDefinition>()

    // Protocol → Transport map (one transport per protocol)
    private val transports: Map<PidProtocol, PidTransport> = mapOf(
        PidProtocol.OBD2  to Obd2Transport,
        PidProtocol.SSM2  to Ssm2Transport,
        PidProtocol.SSM4  to Ssm4Transport,
        PidProtocol.UDS22 to Uds22Transport,
    )

    // Protocol → Parser map (one parser per protocol)
    private val parsers: Map<PidProtocol, ResponseParser> = mapOf(
        PidProtocol.OBD2  to Obd2ResponseParser,
        PidProtocol.SSM2  to SsmResponseParser,
        PidProtocol.SSM4  to Ssm4ResponseParser,
        PidProtocol.UDS22 to Uds22ResponseParser,
    )

    // Lookup indices (rebuilt on each register call)
    private val byKey      = mutableMapOf<String, PidDefinition>()
    private val byModule   = mutableMapOf<SensorModule, MutableList<PidDefinition>>()
    private val byProtocol = mutableMapOf<PidProtocol, MutableList<PidDefinition>>()
    private val byPriority = mutableMapOf<SensorPriority, MutableList<PidDefinition>>()

    init {
        // Register all known Subaru PIDs at startup
        register(*SubaruPidDefinitions.ALL.toTypedArray())
        Log.i(TAG, "Registry initialised: ${_definitions.size} PIDs across " +
            "${byProtocol.entries.joinToString { "${it.key}=${it.value.size}" }}")
        for (proto in PidProtocol.entries) {
            val defs = byProtocol[proto] ?: continue
            Log.d(TAG, "  $proto (${defs.size}): ${defs.joinToString { it.name }}")
        }
    }

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Registers one or more [PidDefinition]s.
     * Duplicate keys (same protocol + address) are silently replaced.
     * Thread-safe for single-threaded initialization; external callers should synchronize
     * if registering from multiple coroutines simultaneously.
     */
    fun register(vararg pids: PidDefinition) {
        for (pid in pids) {
            val existing = byKey[pid.key]
            if (existing != null) {
                Log.d(TAG, "Replacing ${existing.name} with ${pid.name} (key=${pid.key})")
                _definitions.remove(existing)
                removeFromIndices(existing)
            }
            _definitions += pid
            byKey[pid.key] = pid
            byModule.getOrPut(pid.module) { mutableListOf() } += pid
            byProtocol.getOrPut(pid.protocol) { mutableListOf() } += pid
            byPriority.getOrPut(pid.priority) { mutableListOf() } += pid
        }
    }

    private fun removeFromIndices(pid: PidDefinition) {
        byModule[pid.module]?.remove(pid)
        byProtocol[pid.protocol]?.remove(pid)
        byPriority[pid.priority]?.remove(pid)
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /** All registered PIDs in registration order. */
    val all: List<PidDefinition> get() = _definitions.toList()

    /** Total number of registered PIDs. */
    val size: Int get() = _definitions.size

    fun byModule(module: SensorModule): List<PidDefinition> =
        byModule[module]?.toList() ?: emptyList()

    fun byProtocol(protocol: PidProtocol): List<PidDefinition> =
        byProtocol[protocol]?.toList() ?: emptyList()

    fun byPriority(priority: SensorPriority): List<PidDefinition> =
        byPriority[priority]?.toList() ?: emptyList()

    fun find(key: String): PidDefinition? = byKey[key]

    fun findByAddress(address: PidAddress): PidDefinition? =
        _definitions.firstOrNull { it.address == address }

    fun findByName(name: String): PidDefinition? =
        _definitions.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** PIDs for [modules], filtered by [isTurbo] (excludes turbo-only on NA engines). */
    fun forModules(vararg modules: SensorModule, isTurbo: Boolean = true): List<PidDefinition> =
        _definitions.filter {
            it.module in modules && (!it.isTurboOnly || isTurbo)
        }

    // ── Execution pipeline ────────────────────────────────────────────────────

    /**
     * Executes the full Transport → Parser → Decoder pipeline for [definition].
     *
     * Caller is responsible for setting the correct ATSH header before calling this
     * if [definition.address.canHeader] differs from the current adapter default.
     *
     * @return Physical float value, or null on timeout / parse error.
     */
    suspend fun execute(
        definition: PidDefinition,
        btManager: OBDBluetoothManager,
        timeoutMs: Long = 2_000L,
    ): Float? {
        val transport = transports[definition.protocol] ?: run {
            Log.w(TAG, "No transport for protocol ${definition.protocol} (${definition.name})")
            return null
        }
        val parser = parsers[definition.protocol] ?: run {
            Log.w(TAG, "No parser for protocol ${definition.protocol} (${definition.name})")
            return null
        }

        val raw = transport.send(definition.address, btManager, timeoutMs) ?: return null
        val bytes = parser.parse(raw, definition.address) ?: return null
        return definition.decoder.decode(bytes)
    }

    // ── Protocol info ─────────────────────────────────────────────────────────

    /** Returns the transport registered for [protocol], or null if unsupported. */
    fun transportFor(protocol: PidProtocol): PidTransport? = transports[protocol]

    /** Returns the parser registered for [protocol], or null if unsupported. */
    fun parserFor(protocol: PidProtocol): ResponseParser? = parsers[protocol]
}
