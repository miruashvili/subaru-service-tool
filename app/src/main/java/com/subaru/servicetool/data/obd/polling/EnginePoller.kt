package com.subaru.servicetool.data.obd.polling

import android.util.Log
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.bluetooth.adapter.AdapterProfileManager
import com.subaru.servicetool.data.obd.AdapterSpeedProfile
import com.subaru.servicetool.data.obd.CapabilitySnapshot
import com.subaru.servicetool.data.obd.CapabilityState
import com.subaru.servicetool.data.obd.ObdCapabilityProber
import com.subaru.servicetool.data.obd.ObdParser
import com.subaru.servicetool.data.obd.ObdPid
import com.subaru.servicetool.data.obd.ObdPids
import com.subaru.servicetool.data.obd.OilTempSource
import com.subaru.servicetool.data.obd.SensorProtocol
import com.subaru.servicetool.data.obd.SensorRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Polls all OBD-module and ECU-module sensors independently.
 *
 * Priority queues:
 *   HIGH   — CRITICAL sensors (RPM, speed, coolant, throttle) — every iteration
 *   MEDIUM — HIGH + NORMAL sensors — every [MEDIUM_EVERY] HIGH iterations
 *   LOW    — LOW sensors — every [LOW_EVERY] HIGH iterations
 *
 * Target: HIGH queue updates multiple times per second (~5 Hz with typical BT adapters).
 *
 * Oil-temperature routing follows [CapabilitySnapshot.oilTempSource].
 * Adaptive speed-profile adjustments are performed here since EnginePoller owns the
 * highest-frequency cycle timing.
 */
class EnginePoller(
    btManager: OBDBluetoothManager,
    capabilityProber: ObdCapabilityProber,
    sensorRegistry: SensorRegistry,
    sensorValues: MutableStateFlow<Map<String, Float>>,
    private val singleReadMode: AtomicBoolean,
    private val onBatchFailed: () -> Unit,
    profileManager: AdapterProfileManager? = null,
) : BasePoller(btManager, capabilityProber, sensorRegistry, sensorValues, profileManager) {

    override val tag = "EnginePoller"

    companion object {
        private const val MEDIUM_EVERY   = 3
        private const val LOW_EVERY      = 10
        private const val HIGH_CYCLE_MIN = 50L   // minimum ms between HIGH cycles (~20 Hz cap)
        private const val TIMEOUT_REINIT = 5     // consecutive timeouts before ELM reinit
    }

    suspend fun run(
        snapshot: CapabilitySnapshot,
        isTurbo: Boolean,
        carActivePids: Set<ObdPid>,
    ) {
        // Build priority queues from SensorRegistry (OBD + ECU module sensors)
        val allObdEcu = sensorRegistry.allSensors()
            .filter { it.module.name in setOf("OBD", "ECU") }
            .filter { !it.obdPid.isTurboOnly || isTurbo }
            .map { it.obdPid }

        val highPids   = ObdPids.TIER1.filter { it in allObdEcu }
        val mediumPids = (ObdPids.TIER2 + ObdPids.TIER3)
            .filter { it in allObdEcu && it !in highPids }
            .plus(carActivePids.filter { it !in highPids })
            .distinct()
        val lowPids    = ObdPids.TIER4
            .filter { it in allObdEcu }

        Log.i(tag, "Queues built — HIGH:${highPids.size} MEDIUM:${mediumPids.size} LOW:${lowPids.size}")
        Log.i(tag, "HIGH : ${highPids.joinToString { it.name }}")
        Log.i(tag, "MEDIUM: ${mediumPids.joinToString { it.name }}")
        Log.i(tag, "LOW  : ${lowPids.joinToString { it.name }}")

        var iteration          = 0
        var consecutiveTimeouts = 0
        var baselineCycleTime  = -1L
        var fastCycleCount     = 0
        val recentCycleTimes   = ArrayDeque<Long>()

        while (isConnected()) {
            val profile    = btManager.adapterSpeedProfile.value
            val cycleStart = System.currentTimeMillis()

            tickSkipCounters()

            // ── HIGH queue — every iteration ──────────────────────────────────
            Log.d(tag, "--- iteration=$iteration HIGH ---")
            var timeoutsThisCycle = 0
            for (pid in highPids) {
                if (!isConnected()) break
                if (isSkipped(pid)) continue
                val value = queryObd(pid, profile)
                if (value == null) timeoutsThisCycle++
                emitValue(pid, value)
            }

            // IAT fallback: 010F → 0168 if no data
            val iatPid = ObdPids.INTAKE_TEMP
            if (iatPid in mediumPids && !isSkipped(iatPid)) {
                // Handled in medium; no fallback needed in high
            }

            consecutiveTimeouts = if (timeoutsThisCycle > 0)
                consecutiveTimeouts + timeoutsThisCycle
            else
                0
            if (consecutiveTimeouts >= TIMEOUT_REINIT) {
                Log.w(tag, "$consecutiveTimeouts consecutive timeouts — reinitialising ELM327")
                btManager.reinitializeElm327()
                consecutiveTimeouts = 0
            }

            // ── MEDIUM queue — every MEDIUM_EVERY iterations ──────────────────
            if (iteration % MEDIUM_EVERY == 0 && isConnected()) {
                Log.d(tag, "--- MEDIUM (${mediumPids.size} PIDs) ---")
                processMixedQueue(mediumPids, profile, snapshot)
            }

            // ── LOW queue — every LOW_EVERY iterations ────────────────────────
            if (iteration % LOW_EVERY == 0 && isConnected()) {
                Log.d(tag, "--- LOW (${lowPids.size} PIDs) ---")
                processMixedQueue(lowPids, profile, snapshot)
            }

            // ── Adaptive speed-profile adjustment ─────────────────────────────
            val cycleTime = System.currentTimeMillis() - cycleStart
            recentCycleTimes.addLast(cycleTime)
            if (recentCycleTimes.size > 10) recentCycleTimes.removeFirst()

            if (baselineCycleTime < 0 && recentCycleTimes.size >= 5) {
                baselineCycleTime = recentCycleTimes.average().toLong()
                Log.d(tag, "Baseline established: ${baselineCycleTime}ms profile=${profile.label}")
            }
            if (baselineCycleTime >= 0) {
                when {
                    cycleTime > baselineCycleTime * 2 -> {
                        Log.w(tag, "Cycle ${cycleTime}ms > 2× baseline — downgrading profile")
                        btManager.downgradeProfile()
                        fastCycleCount = 0; baselineCycleTime = -1L; recentCycleTimes.clear()
                    }
                    cycleTime <= baselineCycleTime -> {
                        fastCycleCount++
                        if (fastCycleCount >= 5) {
                            Log.d(tag, "5 fast cycles — upgrading profile")
                            btManager.upgradeProfile()
                            fastCycleCount = 0; baselineCycleTime = -1L; recentCycleTimes.clear()
                        }
                    }
                    else -> fastCycleCount = 0
                }
            }

            // ── Throttle — maintain HIGH_CYCLE_MIN floor ──────────────────────
            val elapsed = System.currentTimeMillis() - cycleStart
            if (elapsed < HIGH_CYCLE_MIN) delay(HIGH_CYCLE_MIN - elapsed)

            iteration++
        }
        Log.i(tag, "Exiting — not connected")
    }

    // ── Mixed-protocol queue processing ───────────────────────────────────────

    /**
     * Processes a mixed list of OBD-standard, ECU-SSM-A8, and ECU-UDS-22 PIDs.
     * SSM A8 sensors are batched together for efficiency. OIL_TEMP is routed
     * through [processOilTemp] which handles all three source variants.
     */
    private suspend fun processMixedQueue(
        pids: List<ObdPid>,
        profile: AdapterSpeedProfile,
        snapshot: CapabilitySnapshot,
    ) {
        val obdPids = pids.filter { it.ssmAddress == null && isObd(it) && it != ObdPids.OIL_TEMP && !isSkipped(it) }
        val ssmPids = pids.filter { it.ssmAddress != null && it != ObdPids.OIL_TEMP && !isSkipped(it) }
        val udsPids = pids.filter { it.ssmAddress == null && !isObd(it) && it != ObdPids.OIL_TEMP && !isSkipped(it) }

        // OBD standard — individual queries
        for (pid in obdPids) {
            if (!isConnected()) return
            var value = queryObd(pid, profile)
            // IAT fallback
            if (pid == ObdPids.INTAKE_TEMP && value == null && isConnected()) {
                Log.d(tag, "IAT 010F null — trying fallback 0168")
                val fb = btManager.sendCommand("0168", profile.commandTimeoutMs)
                if (fb != null) {
                    value = ObdParser.parseStandard(fb, "0168")
                        ?.let { b -> if (b.size >= 2) (b[1] - 40).toFloat() else null }
                    Log.d(tag, "IAT fallback → $value")
                }
            }
            emitValue(pid, value)
        }

        // OIL_TEMP — special source-dependent routing
        if (ObdPids.OIL_TEMP in pids && !isSkipped(ObdPids.OIL_TEMP) && isConnected()) {
            emitValue(ObdPids.OIL_TEMP, processOilTemp(snapshot))
        }

        // ECU SSM A8 — batch read
        if (ssmPids.isNotEmpty() && isConnected()) {
            val results = querySsmBatch(ssmPids, !singleReadMode.get()) {
                singleReadMode.set(true)
                onBatchFailed()
            }
            for ((pid, value) in results) emitValue(pid, value)
        }

        // ECU UDS Mode 22 — individual queries (already on 7E0, no header switch)
        for (pid in udsPids) {
            if (!isConnected()) return
            emitValue(pid, queryUds(pid))
        }
    }

    private suspend fun processOilTemp(snapshot: CapabilitySnapshot): Float? {
        val state = snapshot.ecuStates[0x0000AF] ?: CapabilityState.UNKNOWN
        return when (snapshot.oilTempSource) {
            OilTempSource.OBD_STANDARD -> {
                Log.d(tag, "OIL_TEMP: OBD_STANDARD (015C)")
                val resp = btManager.sendCommand("015C", SSM_TIMEOUT_MS) ?: return null
                ObdParser.parseStandard(resp, "015C")
                    ?.let { b -> if (b.isNotEmpty()) (b[0] - 40).toFloat() else null }
            }
            OilTempSource.SSM_ECU -> {
                Log.d(tag, "OIL_TEMP: SSM_ECU 0x0000AF [advisory=$state]")
                querySsmSingle(ObdPids.OIL_TEMP)
            }
            OilTempSource.SSM_ECU_ALT -> {
                Log.d(tag, "OIL_TEMP: SSM_ECU_ALT 0x009D5C")
                val resp = btManager.sendCommand(capabilityProber.buildSsmA8Single(0x009D5C), SSM_TIMEOUT_MS) ?: return null
                ObdParser.parseSsmResponse(resp)?.let { v -> ObdPids.OIL_TEMP.parse(listOf(v)) }
            }
            OilTempSource.NONE -> {
                Log.d(tag, "OIL_TEMP: source=NONE — defaulting to SSM_ECU (0x0000AF) [advisory=$state]")
                querySsmSingle(ObdPids.OIL_TEMP)
            }
        }
    }

    private fun isObd(pid: ObdPid): Boolean = pid.cmd.startsWith("01") || pid.cmd.startsWith("03")
}
