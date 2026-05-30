package com.subaru.servicetool.data.obd.polling

import android.util.Log
import com.subaru.servicetool.data.bluetooth.BluetoothConnectionState
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.bluetooth.adapter.AdapterProfileManager
import com.subaru.servicetool.data.obd.AdapterSpeedProfile
import com.subaru.servicetool.data.obd.ObdCapabilityProber
import com.subaru.servicetool.data.obd.ObdParser
import com.subaru.servicetool.data.obd.ObdPid
import com.subaru.servicetool.data.obd.SensorRegistry
import com.subaru.servicetool.data.obd.SensorStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal const val SSM_TIMEOUT_MS  = 2_000L
internal const val SSM_SETTLE_MS   = 300L

/**
 * Abstract base for all independent pollers.
 *
 * Provides shared infrastructure:
 *  - Per-PID skip and error tracking (each poller instance owns its own maps)
 *  - Thread-safe value emission via [MutableStateFlow.update]
 *  - [SensorRegistry] status updates on every poll result
 *  - Common query helpers for OBD, UDS, and SSM protocols
 *
 * Each concrete poller manages its own priority queues and cycle timing.
 * Pollers run in sibling coroutines under a [kotlinx.coroutines.supervisorScope]
 * so an uncaught exception in one does not cancel the others.
 */
abstract class BasePoller(
    protected val btManager: OBDBluetoothManager,
    protected val capabilityProber: ObdCapabilityProber,
    protected val sensorRegistry: SensorRegistry,
    protected val sensorValues: MutableStateFlow<Map<String, Float>>,
    /** Null only for pollers that never use SSM batch reads (AwdPoller, TpmsPoller). */
    protected val adapterProfileManager: AdapterProfileManager? = null,
) {
    protected abstract val tag: String

    /** Per-PID remaining skip cycles; managed exclusively by this poller instance. */
    protected val skipCycles = mutableMapOf<String, Int>()

    /** Per-PID consecutive error counter; reset on any successful read. */
    protected val consecutiveErrors = mutableMapOf<String, Int>()

    // ── Skip tracking ─────────────────────────────────────────────────────────

    protected fun tickSkipCounters() {
        skipCycles.keys.toList().forEach { key ->
            val remaining = (skipCycles[key] ?: 0) - 1
            if (remaining <= 0) {
                skipCycles.remove(key)
                consecutiveErrors.remove(key)
            } else {
                skipCycles[key] = remaining
            }
        }
    }

    protected fun isSkipped(pid: ObdPid): Boolean = (skipCycles[pid.cmd] ?: 0) > 0

    // ── Value emission + registry status ─────────────────────────────────────

    /**
     * Records [value] for [pid] and updates the shared [sensorValues] flow and
     * [SensorRegistry] status atomically (from the calling coroutine's thread).
     *
     * On success: status → ACTIVE, error counter cleared.
     * On null: status → ERROR; after 3 consecutive nulls → skip + UNSUPPORTED (SSM).
     */
    protected fun emitValue(pid: ObdPid, value: Float?) {
        val entry = sensorRegistry.getByPidCmd(pid.cmd)

        if (value != null) {
            consecutiveErrors.remove(pid.cmd)
            sensorValues.update { it + (pid.cmd to value) }
            entry?.let { sensorRegistry.updateStatus(it.sensorId, SensorStatus.ACTIVE) }
            Log.d(tag, "  ${pid.name} = $value ${pid.unit}")
        } else {
            val errors = (consecutiveErrors[pid.cmd] ?: 0) + 1
            consecutiveErrors[pid.cmd] = errors
            entry?.let { sensorRegistry.updateStatus(it.sensorId, SensorStatus.ERROR) }
            Log.d(tag, "  ${pid.name} → no data (errors=$errors)")

            if (errors >= 3) {
                val skipFor = if (pid.ssmAddress != null) {
                    Log.i(tag, "${pid.name} (SSM 0x%06X) no response ×3 — suspended for session".format(pid.ssmAddress))
                    entry?.let { sensorRegistry.updateStatus(it.sensorId, SensorStatus.UNSUPPORTED) }
                    9_999
                } else {
                    Log.d(tag, "${pid.name} no response ×3 — suspended 10 cycles")
                    10
                }
                skipCycles[pid.cmd] = skipFor
            }
        }
    }

    // ── Protocol query helpers ────────────────────────────────────────────────

    /** Query a standard OBD-II Mode 01 PID. Returns parsed float or null on error/timeout. */
    protected suspend fun queryObd(pid: ObdPid, profile: AdapterSpeedProfile): Float? {
        val resp = btManager.sendCommand(pid.cmd, profile.commandTimeoutMs) ?: return null
        return ObdParser.parseStandard(resp, pid.cmd)?.let { pid.parse(it) }
    }

    /** Query a UDS Mode 22 PID (no header switch — caller must set header beforehand). */
    protected suspend fun queryUds(pid: ObdPid): Float? {
        val resp = btManager.sendCommand(pid.cmd, SSM_TIMEOUT_MS) ?: return null
        return ObdParser.parseUdsResponse(resp, pid.cmd)?.let { pid.parse(it) }
    }

    /** Single SSM A8 address read. */
    protected suspend fun querySsmSingle(pid: ObdPid): Float? {
        requireNotNull(pid.ssmAddress) { "${pid.name} has no ssmAddress" }
        val cmd  = capabilityProber.buildSsmA8Single(pid.ssmAddress!!)
        val resp = btManager.sendCommand(cmd, SSM_TIMEOUT_MS) ?: return null
        return ObdParser.parseSsmResponse(resp)?.let { v -> pid.parse(listOf(v)) }
    }

    /**
     * Batch SSM A8 read for [pids] using the three-tier fallback chain from
     * [AdapterProfileManager]: FULL_BATCH → HALF_BATCH → SINGLE_READ.
     *
     * A module is NEVER disabled due to a timeout at any tier. When no profile manager is
     * wired (pollers that never batch), a single-read fallback is used so the call is safe.
     */
    protected suspend fun querySsmBatch(pids: List<ObdPid>): Map<ObdPid, Float?> {
        if (pids.isEmpty()) return emptyMap()
        val addresses = pids.map { requireNotNull(it.ssmAddress) { "${it.name} has no ssmAddress" } }

        val values = adapterProfileManager?.let { manager ->
            val result = manager.readSsmWithFallback(addresses)
            Log.d(tag, "querySsmBatch tier=${result.tier} got=${result.values.size}/${addresses.size}")
            result.values
        } ?: capabilityProber.readSsmBatch(addresses, allowBatch = false).values

        return pids.associateWith { pid ->
            values[pid.ssmAddress]?.let { v -> pid.parse(listOf(v)) }
        }
    }

    // ── Connection check ──────────────────────────────────────────────────────

    protected fun isConnected(): Boolean =
        btManager.connectionState.value is BluetoothConnectionState.Connected
}
