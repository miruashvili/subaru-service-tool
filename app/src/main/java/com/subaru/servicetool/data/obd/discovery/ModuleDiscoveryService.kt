package com.subaru.servicetool.data.obd.discovery

import android.util.Log
import com.subaru.servicetool.data.bluetooth.OBDBluetoothManager
import com.subaru.servicetool.data.obd.ObdCapabilityProber
import com.subaru.servicetool.data.obd.ObdParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ModuleDiscovery"

/**
 * Dynamically discovers which Subaru CAN modules are present on the connected vehicle
 * and sweeps a comprehensive set of SSM memory addresses within each module.
 *
 * This service replaces the narrow hardcoded address lists with a two-phase approach:
 *
 *   Phase 1 — Module presence: probe each CAN header (7E0, 7E1, 7D4, 7E2, 7E6) to
 *             determine which physical ECUs are on the bus.
 *
 *   Phase 2 — Address sweep: for each PRESENT module, read every candidate SSM A8
 *             address from a comprehensive list. Addresses that return valid data are
 *             stored in [ModuleInfo.respondingAddresses].
 *
 * Results are stored in the [modules] StateFlow and are advisory only — the polling
 * engine continues regardless of discovery outcome. [discover] must be called while
 * no pollers are active (i.e., before [ObdQueryEngine] launches its supervisor scope)
 * to avoid header-switch conflicts.
 *
 * Comprehensive candidate address lists are sourced from Subaru SSM documentation,
 * RomRaider/EcuFlash ROM definitions, and empirical testing on FA/FB/EJ engine families.
 */
@Singleton
class ModuleDiscoveryService @Inject constructor(
    private val btManager: OBDBluetoothManager,
    private val capabilityProber: ObdCapabilityProber,
) {

    // ── Candidate address tables ──────────────────────────────────────────────

    companion object {
        private const val HEADER_TIMEOUT_MS = 2_000L
        private const val SETTLE_MS         = 200L
        private const val PROBE_TIMEOUT_MS  = 800L
        private const val ADDR_GAP_MS       = 20L

        /**
         * Comprehensive ECU (7E0) SSM candidate addresses.
         *
         * Covers: coolant, oil temp, RPM, MAF, MAP, throttle, O2, fuel trim, VVT,
         * alternator, battery temp, EGT, knock, wastegate, injector, throttle motor,
         * and ISC/boost circuits across FA/FB/EJ/EZ engine families.
         */
        val ECU_CANDIDATES: List<Int> = listOf(
            // ── Basic sensor block (0x000000–0x0000FF) ────────────────────────
            0x000000, // Engine status bit flags
            0x000001, // Engine status bit flags 2
            0x000002, // Engine status bit flags 3
            0x000003, // Fan / AC relay status
            0x000007, // Fuel pump relay
            0x000008, // Coolant temperature          A − 40 °C
            0x000009, // Coolant temperature 2
            0x00000A, // Air flow sensor voltage
            0x00000B, // MAF sensor #2 voltage
            0x00000C, // Engine RPM (high byte)
            0x00000D, // Engine RPM (low byte)
            0x000010, // Vehicle speed
            0x000011, // Battery voltage (raw)
            0x000012, // Front O2 sensor voltage
            0x000013, // Rear O2 sensor voltage
            0x000014, // O2 sensor 2 (bank 2 front)
            0x000015, // O2 sensor 2 (bank 2 rear)
            0x00001C, // ISC valve duty cycle
            0x000020, // Mass air flow (raw)
            0x000021, // Manifold pressure (raw)
            0x000023, // Manifold absolute pressure
            0x000025, // Barometric pressure
            0x00002C, // Throttle sensor voltage
            0x00002D, // Throttle sensor 2 (DBW)
            0x00003B, // Short-term fuel trim (B1)
            0x00003C, // Long-term fuel trim (B1)
            0x00003D, // Short-term fuel trim (B2)
            0x00003E, // Long-term fuel trim (B2)
            0x000044, // Boost solenoid duty
            0x000045, // Intake air temperature
            0x0000AF, // Engine oil temperature       A − 40 °C  (primary)
            0x0000B0, // Engine oil temperature 2
            0x0000CE, // Engine load (calculated)
            // ── Extended sensor block (0x001000–0x001FFF) ─────────────────────
            0x001000, // Boost pressure (absolute)
            0x001001, // MAF sensor frequency (Hz)
            0x001002, // MAP sensor 2
            0x001005, // Knock correction (summary)
            0x00105E, // Throttle motor angle
            0x00105F, // Throttle motor duty          A × 100 / 255 − 50 %
            0x001060, // Throttle opening speed
            0x0010A1, // MAF sensor voltage           A × 0.02 V
            0x0010A2, // Injector duty cycle
            0x0010A3, // Injector 1 pulse width
            0x0010A4, // Ignition timing advance
            0x0010A5, // Learned ignition timing
            0x0010A6, // Accelerator pedal angle      A × 100 / 255 %
            0x0010A7, // IACV duty
            0x0010A8, // Cam position intake (FA/FB)
            0x0010A9, // Cam position exhaust
            0x0010B0, // Front wideband O2 output
            0x0010B1, // Rear O2 output
            0x0010B2, // Alternator duty              A × 100 / 255 %
            0x0010B3, // Fuel pump duty
            0x0010B4, // VVT advance (right/intake)   (A − 128) × 0.5 °
            0x0010B5, // VVT advance (left/exhaust)   (A − 128) × 0.5 °
            0x0010B6, // Wastegate valve duty (turbo)
            0x0010B7, // Boost target (turbo)
            0x0010C9, // Wastegate solenoid duty (EJ turbo)
            0x001136, // Battery temperature          A − 40 °C
            0x001155, // Exhaust gas temperature (EGT)
            0x001200, // Throttle position (absolute)
            0x001201, // Relative throttle position
            0x001210, // MAP sensor 3 (AVCS)
            0x001220, // EGR valve position
            // ── Knock and timing block (0x003000–0x003FFF, turbo) ─────────────
            0x003017, // Feedback knock correction 1
            0x003018, // Feedback knock correction    (turbo primary)
            0x003019, // Fine knock learning
            0x00301A, // Knock advance correction
            0x00301B, // Dynamic advance correction
            // ── Fuel corrections (0x002000–) ─────────────────────────────────
            0x002000, // Fuel injection end angle
            0x002001, // Air-fuel ratio correction
            0x002010, // Target boost pressure
            0x002011, // Wastegate opening angle
        )

        /**
         * Comprehensive TCU (7E1) SSM candidate addresses.
         *
         * Covers: CVT fluid temp, AWD duty, lockup duty, gear ratios, pulley speeds,
         * turbine speed, line pressure, solenoid duties, and shift quality metrics
         * across TR580/TR690 CVT and TZ1 AWD transfer families.
         */
        val TCU_CANDIDATES: List<Int> = listOf(
            // ── CVT fluid and temperatures ────────────────────────────────────
            0x001017, // CVT fluid temperature        A − 40 °C  (key CVT indicator)
            0x001018, // CVT fluid temperature 2
            0x001019, // Oil cooler outlet temperature
            // ── AWD / transfer ────────────────────────────────────────────────
            0x001065, // AWD transfer duty            A × 100 / 255 %
            0x001066, // AWD actual torque split
            0x001067, // AWD target torque split
            // ── CVT lockup and pressure ───────────────────────────────────────
            0x001045, // CVT lockup clutch duty       A × 100 / 255 %
            0x001046, // CVT lockup slip speed
            0x001047, // CVT lockup engage/disengage flag
            // ── Pulley speeds and ratios ──────────────────────────────────────
            0x001080, // Primary pulley revolution (hi)
            0x001081, // Primary pulley revolution (lo)
            0x001082, // Secondary pulley revolution (hi)
            0x001083, // Secondary pulley revolution (lo)
            0x001084, // Turbine revolution (hi)
            0x001085, // Turbine revolution (lo)
            0x001090, // CVT target ratio (hi)
            0x001091, // CVT target ratio (lo)
            0x001092, // CVT actual ratio (hi)
            0x001093, // CVT actual ratio (lo)
            // ── Line pressure and solenoids ───────────────────────────────────
            0x001100, // Line pressure duty cycle
            0x001101, // Primary pressure duty cycle
            0x001102, // Secondary pressure duty cycle
            0x001110, // Shift solenoid A duty
            0x001111, // Shift solenoid B duty
            0x001112, // Solenoid C duty
            // ── Gear and mode selection ───────────────────────────────────────
            0x001120, // Selected gear (D/N/R/P byte)
            0x001121, // Actual gear ratio step
            0x001122, // Mode selection (Sport/Normal/Manual)
            // ── AT-specific (non-CVT models) ──────────────────────────────────
            0x001130, // ATF temperature
            0x001131, // Torque converter slip speed
            0x001140, // Torque converter lockup status
            0x001150, // Input shaft speed
            0x001160, // Output shaft speed
        )

        /**
         * Body Control Module (7E2) SSM candidate addresses.
         *
         * Covers: AC system, ambient sensors, door/window status, and comfort systems.
         */
        val BODY_CANDIDATES: List<Int> = listOf(
            0x002000, // Door/window status bit field
            0x002001, // Trunk / tailgate status
            0x002010, // AC evaporator temperature
            0x002011, // AC refrigerant pressure (hi)
            0x002012, // AC refrigerant pressure (lo)
            0x002013, // AC compressor speed
            0x002020, // Ambient temperature (BCM)
            0x002021, // Solar load sensor
            0x002030, // Rain sensor intensity
            0x002040, // Interior temperature
            0x002050, // Blower motor duty
            0x002060, // Rear defroster status
        )

        /**
         * TPMS module (7D4) UDS DID candidates (Mode 22 data identifiers).
         * Used for presence detection — a 0x62 response means the DID is supported.
         */
        val TPMS_DIDS: List<Int> = listOf(
            0x1501, // FL tire pressure
            0x1502, // FR tire pressure
            0x1503, // RL tire pressure
            0x1504, // RR tire pressure
            0x1505, // Spare tire pressure
            0x1510, // TPMS warning status
            0x1511, // Sensor battery status
        )

        /**
         * Hybrid/e-BOXER Battery Management (7E6) SSM candidate addresses.
         * Only present on Subaru PHEV (Outback PHEV, XV/Crosstrek PHEV) and e-BOXER models.
         */
        val HYBRID_CANDIDATES: List<Int> = listOf(
            0x003000, // HV battery state of charge (%)
            0x003001, // HV battery temperature
            0x003002, // HV battery voltage (hi)
            0x003003, // HV battery voltage (lo)
            0x003004, // HV battery current (hi)
            0x003005, // HV battery current (lo)
            0x003010, // Electric motor torque
            0x003011, // Generator torque
            0x003012, // Motor speed
            0x003020, // EV mode status
            0x003021, // Charge mode status
            0x003030, // DC-DC converter output voltage
        )

        // Addresses that definitively confirm a CVT sub-module within TCU
        private val CVT_MARKER_ADDRESSES = setOf(0x001017, 0x001045)

        // Addresses that definitively confirm an AWD sub-module within TCU
        private val AWD_MARKER_ADDRESSES = setOf(0x001065)
    }

    // ── Runtime module map ────────────────────────────────────────────────────

    private val _modules = MutableStateFlow<Map<SubaruModule, ModuleInfo>>(emptyMap())

    /**
     * Live runtime module map, populated after each [discover] call.
     * Keys are present for every probed [SubaruModule]; absent modules have
     * [ModuleInfo.status] == [ModuleStatus.ABSENT].
     */
    val modules: StateFlow<Map<SubaruModule, ModuleInfo>> = _modules.asStateFlow()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Runs a full module discovery sweep and updates [modules].
     *
     * This function is sequential — it restores ATSH7E0 before returning.
     * It must be called while no other component is sending BT commands.
     * Typically called from [ObdQueryEngine] before the polling supervisor scope starts.
     *
     * @return The complete module map after discovery.
     */
    suspend fun discover(): Map<SubaruModule, ModuleInfo> {
        Log.i(TAG, "════ MODULE DISCOVERY START ════")
        Log.i(TAG, "Probing ${SubaruModule.entries.size} module types — advisory only, pollers unaffected")
        _modules.value = emptyMap()

        val result = mutableMapOf<SubaruModule, ModuleInfo>()

        // ECU — always attempted first (7E0 is already active after ELM init)
        result[SubaruModule.ECU] = discoverEcu()

        // TCU / CVT / AWD — all share header 7E1
        val tcuResult = discoverTcu()
        result[SubaruModule.TCU] = tcuResult
        result[SubaruModule.CVT] = deriveCvt(tcuResult)
        result[SubaruModule.AWD] = deriveAwd(tcuResult)

        // TPMS — header 7D4, UDS DIDs
        result[SubaruModule.TPMS] = discoverTpms()

        // Body Control Module — header 7E2
        result[SubaruModule.BODY] = discoverBody()

        // Hybrid BMS — header 7E6
        result[SubaruModule.HYBRID] = discoverHybrid()

        // Ensure default header is restored
        btManager.sendCommand("ATSH7E0", HEADER_TIMEOUT_MS)

        _modules.value = result

        Log.i(TAG, "════ MODULE DISCOVERY COMPLETE ════")
        for ((module, info) in result) {
            Log.i(TAG, "  ${module.name.padEnd(7)} ${module.canHeader} → ${info.status}" +
                if (info.respondingAddresses.isNotEmpty())
                    " [${info.respondingAddresses.size} SSM addresses, ${info.probeMs}ms]"
                else if (info.respondingDids.isNotEmpty())
                    " [${info.respondingDids.size} UDS DIDs, ${info.probeMs}ms]"
                else
                    " [${info.probeMs}ms]")
        }
        return result
    }

    /** Resets the module map to an empty state (e.g., on disconnect). */
    fun reset() {
        _modules.value = emptyMap()
        Log.d(TAG, "Module map cleared")
    }

    // ── Module discovery implementations ─────────────────────────────────────

    private suspend fun discoverEcu(): ModuleInfo {
        Log.i(TAG, "── ECU (7E0) ────────────────────────────────────────")
        val start = System.currentTimeMillis()

        // Mode 01 PID 00 is the universal OBD-II ECU presence check.
        val ping = btManager.sendCommand("0100", PROBE_TIMEOUT_MS)
        val present = ping != null && ObdParser.parseStandard(ping, "0100") != null
        Log.d(TAG, "ECU ping (0100): ${if (present) "PRESENT" else "NO DATA"} — resp=${ping?.take(40)?.trim()}")

        if (!present) {
            val elapsed = System.currentTimeMillis() - start
            return ModuleInfo(SubaruModule.ECU, "7E0", "7E8", ModuleStatus.ABSENT, probeMs = elapsed)
        }

        Log.d(TAG, "ECU PRESENT — sweeping ${ECU_CANDIDATES.size} SSM addresses")
        val responding = sweepSsmAddresses("7E0", ECU_CANDIDATES)
        val elapsed = System.currentTimeMillis() - start
        Log.i(TAG, "ECU sweep done: ${responding.size}/${ECU_CANDIDATES.size} addresses responded in ${elapsed}ms")
        return ModuleInfo(SubaruModule.ECU, "7E0", "7E8", ModuleStatus.PRESENT, responding, probeMs = elapsed)
    }

    private suspend fun discoverTcu(): ModuleInfo {
        Log.i(TAG, "── TCU (7E1) ────────────────────────────────────────")
        val start = System.currentTimeMillis()

        val headerOk = btManager.sendCommand("ATSH7E1", HEADER_TIMEOUT_MS) != null
        if (!headerOk) {
            Log.w(TAG, "ATSH7E1 timeout — TCU discovery skipped")
            val elapsed = System.currentTimeMillis() - start
            btManager.sendCommand("ATSH7E0", HEADER_TIMEOUT_MS)
            return ModuleInfo(SubaruModule.TCU, "7E1", "7E9", ModuleStatus.ERROR, probeMs = elapsed)
        }
        delay(SETTLE_MS)

        // Ping via Tester Present (UDS service 0x3E, sub-function 0x00 = response required)
        val ping = btManager.sendCommand("3E00", PROBE_TIMEOUT_MS)
        val pingPresent = ping != null && "7E" in ping.uppercase() && "NO DATA" !in ping.uppercase()
        Log.d(TAG, "TCU Tester Present (3E00): ${if (pingPresent) "responded" else "no response"} — ${ping?.take(40)?.trim()}")

        if (!pingPresent) {
            // Try SSM read of CVT fluid temp as an alternative presence check
            val altPing = btManager.sendCommand(capabilityProber.buildSsmA8Single(0x001017), PROBE_TIMEOUT_MS)
            val altPresent = altPing != null && com.subaru.servicetool.data.obd.ObdParser.parseSsmResponse(altPing) != null
            Log.d(TAG, "TCU SSM alt ping (0x001017): ${if (altPresent) "responded" else "no response"}")

            if (!altPresent) {
                Log.i(TAG, "TCU ABSENT — no response to Tester Present or SSM read")
                val elapsed = System.currentTimeMillis() - start
                btManager.sendCommand("ATSH7E0", HEADER_TIMEOUT_MS)
                return ModuleInfo(SubaruModule.TCU, "7E1", "7E9", ModuleStatus.ABSENT, probeMs = elapsed)
            }
        }

        Log.d(TAG, "TCU PRESENT — sweeping ${TCU_CANDIDATES.size} SSM addresses")
        val responding = sweepSsmAddressesCurrentHeader(TCU_CANDIDATES)
        val elapsed = System.currentTimeMillis() - start
        Log.i(TAG, "TCU sweep done: ${responding.size}/${TCU_CANDIDATES.size} addresses in ${elapsed}ms")
        Log.d(TAG, "TCU responding: ${responding.joinToString { "0x%06X".format(it) }}")

        btManager.sendCommand("ATSH7E0", HEADER_TIMEOUT_MS)
        return ModuleInfo(SubaruModule.TCU, "7E1", "7E9", ModuleStatus.PRESENT, responding, probeMs = elapsed)
    }

    /**
     * Derives CVT module presence from the TCU result.
     * CVT is PRESENT when the TCU is PRESENT and at least one CVT marker address responded.
     */
    private fun deriveCvt(tcu: ModuleInfo): ModuleInfo {
        if (!tcu.isPresent) {
            Log.i(TAG, "CVT ABSENT (TCU not present)")
            return ModuleInfo(SubaruModule.CVT, "7E1", "7E9", ModuleStatus.ABSENT)
        }
        val cvtAddresses = tcu.respondingAddresses.intersect(CVT_MARKER_ADDRESSES)
        val status = if (cvtAddresses.isNotEmpty()) ModuleStatus.PRESENT else ModuleStatus.ABSENT
        Log.i(TAG, "CVT $status — marker addresses found: ${cvtAddresses.joinToString { "0x%06X".format(it) }}")
        return ModuleInfo(SubaruModule.CVT, "7E1", "7E9", status, tcu.respondingAddresses, probeMs = tcu.probeMs)
    }

    /**
     * Derives AWD module presence from the TCU result.
     * AWD is PRESENT when the TCU is PRESENT and at least one AWD marker address responded.
     */
    private fun deriveAwd(tcu: ModuleInfo): ModuleInfo {
        if (!tcu.isPresent) {
            Log.i(TAG, "AWD ABSENT (TCU not present)")
            return ModuleInfo(SubaruModule.AWD, "7E1", "7E9", ModuleStatus.ABSENT)
        }
        val awdAddresses = tcu.respondingAddresses.intersect(AWD_MARKER_ADDRESSES)
        val status = if (awdAddresses.isNotEmpty()) ModuleStatus.PRESENT else ModuleStatus.ABSENT
        Log.i(TAG, "AWD $status — marker addresses: ${awdAddresses.joinToString { "0x%06X".format(it) }}")
        return ModuleInfo(SubaruModule.AWD, "7E1", "7E9", status, awdAddresses, probeMs = tcu.probeMs)
    }

    private suspend fun discoverTpms(): ModuleInfo {
        Log.i(TAG, "── TPMS (7D4) ───────────────────────────────────────")
        val start = System.currentTimeMillis()

        val headerOk = btManager.sendCommand("ATSH7D4", HEADER_TIMEOUT_MS) != null
        if (!headerOk) {
            Log.w(TAG, "ATSH7D4 timeout — TPMS discovery skipped")
            val elapsed = System.currentTimeMillis() - start
            btManager.sendCommand("ATSH7E0", HEADER_TIMEOUT_MS)
            return ModuleInfo(SubaruModule.TPMS, "7D4", "7DC", ModuleStatus.ERROR, probeMs = elapsed)
        }
        delay(SETTLE_MS)

        Log.d(TAG, "Probing ${TPMS_DIDS.size} TPMS UDS DIDs")
        val respondingDids = mutableSetOf<Int>()
        for (did in TPMS_DIDS) {
            val cmd  = "22%04X".format(did)
            val resp = btManager.sendCommand(cmd, PROBE_TIMEOUT_MS)
            val ok   = resp != null && com.subaru.servicetool.data.obd.ObdParser.parseUdsResponse(resp, cmd) != null
            Log.d(TAG, "  TPMS DID 0x%04X → %s".format(did, if (ok) "OK" else "NO DATA"))
            if (ok) respondingDids += did
            delay(ADDR_GAP_MS)
        }

        val elapsed = System.currentTimeMillis() - start
        val status  = if (respondingDids.isNotEmpty()) ModuleStatus.PRESENT else ModuleStatus.ABSENT
        Log.i(TAG, "TPMS $status — ${respondingDids.size}/${TPMS_DIDS.size} DIDs responded in ${elapsed}ms")
        btManager.sendCommand("ATSH7E0", HEADER_TIMEOUT_MS)
        return ModuleInfo(SubaruModule.TPMS, "7D4", "7DC", status, respondingDids = respondingDids, probeMs = elapsed)
    }

    private suspend fun discoverBody(): ModuleInfo {
        Log.i(TAG, "── BODY BCM (7E2) ───────────────────────────────────")
        val start = System.currentTimeMillis()

        val headerOk = btManager.sendCommand("ATSH7E2", HEADER_TIMEOUT_MS) != null
        if (!headerOk) {
            Log.w(TAG, "ATSH7E2 timeout — BODY discovery skipped")
            val elapsed = System.currentTimeMillis() - start
            btManager.sendCommand("ATSH7E0", HEADER_TIMEOUT_MS)
            return ModuleInfo(SubaruModule.BODY, "7E2", "7EA", ModuleStatus.ERROR, probeMs = elapsed)
        }
        delay(SETTLE_MS)

        // Ping with Tester Present first — many BCMs respond to this even if they reject other services
        val ping = btManager.sendCommand("3E00", PROBE_TIMEOUT_MS)
        val pingOk = ping != null && "NO DATA" !in ping.uppercase() && "ERROR" !in ping.uppercase()
        Log.d(TAG, "BODY Tester Present (3E00): ${if (pingOk) "responded" else "silent"} — ${ping?.take(40)?.trim()}")

        if (!pingOk) {
            Log.i(TAG, "BODY ABSENT — no response to Tester Present")
            val elapsed = System.currentTimeMillis() - start
            btManager.sendCommand("ATSH7E0", HEADER_TIMEOUT_MS)
            return ModuleInfo(SubaruModule.BODY, "7E2", "7EA", ModuleStatus.ABSENT, probeMs = elapsed)
        }

        Log.d(TAG, "BODY PRESENT — sweeping ${BODY_CANDIDATES.size} SSM addresses")
        val responding = sweepSsmAddressesCurrentHeader(BODY_CANDIDATES)
        val elapsed = System.currentTimeMillis() - start
        Log.i(TAG, "BODY sweep: ${responding.size}/${BODY_CANDIDATES.size} addresses in ${elapsed}ms")

        btManager.sendCommand("ATSH7E0", HEADER_TIMEOUT_MS)
        return ModuleInfo(SubaruModule.BODY, "7E2", "7EA", ModuleStatus.PRESENT, responding, probeMs = elapsed)
    }

    private suspend fun discoverHybrid(): ModuleInfo {
        Log.i(TAG, "── HYBRID BMS (7E6) ─────────────────────────────────")
        val start = System.currentTimeMillis()

        val headerOk = btManager.sendCommand("ATSH7E6", HEADER_TIMEOUT_MS) != null
        if (!headerOk) {
            Log.w(TAG, "ATSH7E6 timeout — HYBRID discovery skipped")
            val elapsed = System.currentTimeMillis() - start
            btManager.sendCommand("ATSH7E0", HEADER_TIMEOUT_MS)
            return ModuleInfo(SubaruModule.HYBRID, "7E6", "7EE", ModuleStatus.ERROR, probeMs = elapsed)
        }
        delay(SETTLE_MS)

        val ping = btManager.sendCommand("3E00", PROBE_TIMEOUT_MS)
        val pingOk = ping != null && "NO DATA" !in ping.uppercase() && "ERROR" !in ping.uppercase()
        Log.d(TAG, "HYBRID Tester Present: ${if (pingOk) "responded" else "silent"} — ${ping?.take(40)?.trim()}")

        if (!pingOk) {
            Log.i(TAG, "HYBRID ABSENT — not a hybrid or PHEV vehicle")
            val elapsed = System.currentTimeMillis() - start
            btManager.sendCommand("ATSH7E0", HEADER_TIMEOUT_MS)
            return ModuleInfo(SubaruModule.HYBRID, "7E6", "7EE", ModuleStatus.ABSENT, probeMs = elapsed)
        }

        Log.d(TAG, "HYBRID PRESENT — sweeping ${HYBRID_CANDIDATES.size} addresses")
        val responding = sweepSsmAddressesCurrentHeader(HYBRID_CANDIDATES)
        val elapsed = System.currentTimeMillis() - start
        Log.i(TAG, "HYBRID sweep: ${responding.size}/${HYBRID_CANDIDATES.size} addresses in ${elapsed}ms")
        Log.d(TAG, "Responding: ${responding.joinToString { "0x%06X".format(it) }}")

        btManager.sendCommand("ATSH7E0", HEADER_TIMEOUT_MS)
        return ModuleInfo(SubaruModule.HYBRID, "7E6", "7EE", ModuleStatus.PRESENT, responding, probeMs = elapsed)
    }

    // ── SSM address sweep helpers ─────────────────────────────────────────────

    /**
     * Switches to [header], settles, sweeps [candidates] via SSM A8 single reads,
     * and returns the set of addresses that returned valid data.
     * Restores ATSH7E0 when done.
     */
    private suspend fun sweepSsmAddresses(header: String, candidates: List<Int>): Set<Int> {
        val headerOk = btManager.sendCommand("ATSH$header", HEADER_TIMEOUT_MS) != null
        if (!headerOk) {
            Log.w(TAG, "ATSH$header failed — sweep aborted")
            return emptySet()
        }
        delay(SETTLE_MS)
        val found = sweepSsmAddressesCurrentHeader(candidates)
        btManager.sendCommand("ATSH7E0", HEADER_TIMEOUT_MS)
        return found
    }

    /**
     * Sweeps [candidates] with SSM A8 single reads against the **currently active** header.
     * The caller is responsible for setting the correct ATSH before calling this.
     */
    private suspend fun sweepSsmAddressesCurrentHeader(candidates: List<Int>): Set<Int> {
        val responding = linkedSetOf<Int>()
        for (addr in candidates) {
            val cmd  = capabilityProber.buildSsmA8Single(addr)
            val resp = btManager.sendCommand(cmd, PROBE_TIMEOUT_MS)
            val hit  = resp != null && com.subaru.servicetool.data.obd.ObdParser.parseSsmResponse(resp) != null
            if (hit) {
                responding += addr
                Log.d(TAG, "  0x%06X → HIT".format(addr))
            } else {
                Log.d(TAG, "  0x%06X → miss".format(addr))
            }
            delay(ADDR_GAP_MS)
        }
        return responding
    }
}
