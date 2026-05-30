package com.subaru.servicetool.data.obd

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SensorRegistry"

/**
 * Central registry of every known Subaru sensor.
 *
 * All sensors are registered at startup and remain in the registry for the lifetime of the
 * application. The polling engine ([ObdQueryEngine]) uses the registry to determine which
 * PIDs to poll — polling is independent from dashboard visibility.
 *
 * Runtime polling status per sensor is tracked in [statuses] and updated by the engine
 * after each poll attempt. The status transitions are:
 *
 *   UNKNOWN → ACTIVE      (first successful read)
 *   UNKNOWN → ERROR       (first failure, engine will retry)
 *   ERROR   → ACTIVE      (recovered on next successful read)
 *   ERROR   → UNSUPPORTED (SSM sensor: 3 consecutive failures; session-permanent)
 *
 * Dashboard visibility is purely a UI concern and does not affect which sensors are polled.
 */
@Singleton
class SensorRegistry @Inject constructor() {

    private val _entries: List<SensorEntry> = buildRegistry()

    /** All registered sensors, in definition order. */
    val allEntries: List<SensorEntry> get() = _entries

    private val byPidCmd: Map<String, SensorEntry> = _entries.associateBy { it.obdPid.cmd }
    private val byId:     Map<String, SensorEntry> = _entries.associateBy { it.sensorId }

    private val _statuses: MutableStateFlow<Map<String, SensorStatus>> =
        MutableStateFlow(_entries.associate { it.sensorId to SensorStatus.UNKNOWN })

    /** Live runtime status for every registered sensor, keyed by [SensorEntry.sensorId]. */
    val statuses: StateFlow<Map<String, SensorStatus>> = _statuses.asStateFlow()

    init {
        Log.i(TAG, "SensorRegistry initialised: ${_entries.size} sensors registered")
        val byModule   = _entries.groupBy { it.module }
        val byProtocol = _entries.groupBy { it.protocol }
        val byPriority = _entries.groupBy { it.priority }
        Log.d(TAG, "By module:    ${byModule.entries.joinToString { "${it.key}=${it.value.size}" }}")
        Log.d(TAG, "By protocol:  ${byProtocol.entries.joinToString { "${it.key}=${it.value.size}" }}")
        Log.d(TAG, "By priority:  ${byPriority.entries.joinToString { "${it.key}=${it.value.size}" }}")
        Log.d(TAG, "Registered IDs: ${_entries.joinToString { it.sensorId }}")
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    fun allSensors(): List<SensorEntry> = _entries

    /** Returns the sensor whose underlying PID command matches [cmd], or null. */
    fun getByPidCmd(cmd: String): SensorEntry? = byPidCmd[cmd]

    /** Returns the sensor with [sensorId], or null. */
    fun getById(sensorId: String): SensorEntry? = byId[sensorId]

    // ── Status management ─────────────────────────────────────────────────────

    /**
     * Updates the runtime status for [sensorId].
     * Emits a new [statuses] value only when the status actually changes.
     */
    fun updateStatus(sensorId: String, newStatus: SensorStatus) {
        val current = _statuses.value
        val existing = current[sensorId] ?: return
        if (existing == newStatus) return
        Log.d(TAG, "Status $sensorId: $existing → $newStatus")
        _statuses.value = current + (sensorId to newStatus)
    }

    /**
     * Resets all sensor statuses to UNKNOWN.
     * Called by [ObdQueryEngine] on disconnect so that status reflects the new session.
     */
    fun reset() {
        _statuses.value = _entries.associate { it.sensorId to SensorStatus.UNKNOWN }
        Log.i(TAG, "SensorRegistry reset — ${_entries.size} sensors → UNKNOWN")
    }

    // ── Registry definition ───────────────────────────────────────────────────

    private fun buildRegistry(): List<SensorEntry> {
        val entries = mutableListOf<SensorEntry>()

        // ── Standard OBD-II Mode 01 (SensorModule.OBD, SensorProtocol.OBD_STANDARD) ──────────

        entries += entry("ENGINE_RPM",        SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.CRITICAL, ObdPids.RPM)
        entries += entry("VEHICLE_SPEED",     SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.CRITICAL, ObdPids.SPEED)
        entries += entry("COOLANT_TEMP",      SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.CRITICAL, ObdPids.COOLANT_TEMP)
        entries += entry("THROTTLE_POS",      SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.CRITICAL, ObdPids.THROTTLE)

        entries += entry("ENGINE_LOAD",       SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.HIGH,     ObdPids.ENGINE_LOAD)
        entries += entry("BATTERY_VOLTAGE",   SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.HIGH,     ObdPids.VOLTAGE)
        entries += entry("INTAKE_AIR_TEMP",   SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.HIGH,     ObdPids.INTAKE_TEMP)

        entries += entry("MAF_AIRFLOW",       SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.NORMAL,   ObdPids.MAF)
        entries += entry("MAP_PRESSURE",      SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.NORMAL,   ObdPids.MAP)
        entries += entry("FUEL_TRIM_SHORT",   SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.NORMAL,   ObdPids.FUEL_TRIM_ST)
        entries += entry("FUEL_TRIM_LONG",    SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.NORMAL,   ObdPids.FUEL_TRIM_LT)
        entries += entry("AMBIENT_TEMP",      SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.NORMAL,   ObdPids.AMBIENT_TEMP)
        entries += entry("FUEL_LEVEL",        SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.NORMAL,   ObdPids.FUEL_LEVEL)
        entries += entry("AIR_FUEL_RATIO",    SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.NORMAL,   ObdPids.AFR)
        entries += entry("BARO_PRESSURE",     SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.NORMAL,   ObdPids.BAROMETRIC_PRESS)
        entries += entry("REL_THROTTLE_POS",  SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.NORMAL,   ObdPids.REL_THROTTLE)
        entries += entry("ABS_ENGINE_LOAD",   SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.NORMAL,   ObdPids.ABS_LOAD)
        entries += entry("O2_VOLTAGE",        SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.NORMAL,   ObdPids.O2_VOLTAGE)

        entries += entry("RUN_TIME",          SensorModule.OBD, SensorProtocol.OBD_STANDARD, SensorPriority.LOW,      ObdPids.RUN_TIME)

        // ── Subaru ECU SSM A8 (SensorModule.ECU, SensorProtocol.SSM_A8) ──────────────────────

        entries += entry("OIL_TEMP",          SensorModule.ECU, SensorProtocol.SSM_A8,       SensorPriority.HIGH,     ObdPids.OIL_TEMP)

        entries += entry("VVT_ADVANCE_LEFT",  SensorModule.ECU, SensorProtocol.SSM_A8,       SensorPriority.NORMAL,   ObdPids.VVT_LEFT)
        entries += entry("VVT_ADVANCE_RIGHT", SensorModule.ECU, SensorProtocol.SSM_A8,       SensorPriority.NORMAL,   ObdPids.VVT_RIGHT)
        entries += entry("MAF_VOLTAGE",       SensorModule.ECU, SensorProtocol.SSM_A8,       SensorPriority.NORMAL,   ObdPids.MAF_VOLTAGE)
        entries += entry("ACCEL_PEDAL_ANGLE", SensorModule.ECU, SensorProtocol.SSM_A8,       SensorPriority.NORMAL,   ObdPids.ACCEL_PEDAL)
        entries += entry("ALTERNATOR_DUTY",   SensorModule.ECU, SensorProtocol.SSM_A8,       SensorPriority.NORMAL,   ObdPids.ALTERNATOR_DUTY)

        entries += entry("BATTERY_TEMP",      SensorModule.ECU, SensorProtocol.SSM_A8,       SensorPriority.LOW,      ObdPids.BATTERY_TEMP)
        entries += entry("THROTTLE_MOTOR",    SensorModule.ECU, SensorProtocol.SSM_A8,       SensorPriority.LOW,      ObdPids.THROTTLE_MOTOR)

        // ── Subaru ECU UDS Mode 22 (SensorModule.ECU, SensorProtocol.UDS_22) ────────────────

        entries += entry("EGT",               SensorModule.ECU, SensorProtocol.UDS_22,       SensorPriority.HIGH,     ObdPids.EGT)

        entries += entry("ECM_COOLANT_TEMP",  SensorModule.ECU, SensorProtocol.UDS_22,       SensorPriority.NORMAL,   ObdPids.ECM_COOLANT_TEMP)
        entries += entry("KNOCK_CORRECTION",  SensorModule.ECU, SensorProtocol.UDS_22,       SensorPriority.NORMAL,   ObdPids.KNOCK_CORRECTION)
        entries += entry("WASTEGATE_DUTY",    SensorModule.ECU, SensorProtocol.UDS_22,       SensorPriority.NORMAL,   ObdPids.WASTEGATE)

        entries += entry("RADIATOR_FAN",      SensorModule.ECU, SensorProtocol.UDS_22,       SensorPriority.LOW,      ObdPids.RADIATOR_FAN)
        entries += entry("FUEL_PUMP_DUTY",    SensorModule.ECU, SensorProtocol.UDS_22,       SensorPriority.LOW,      ObdPids.FUEL_PUMP)

        // ── Subaru TCU SSM A8 (SensorModule.TCU, SensorProtocol.SSM_A8) ──────────────────────

        entries += entry("CVT_FLUID_TEMP",    SensorModule.TCU, SensorProtocol.SSM_A8,       SensorPriority.HIGH,     ObdPids.CVT_TEMP)

        entries += entry("AWD_TRANSFER_DUTY", SensorModule.TCU, SensorProtocol.SSM_A8,       SensorPriority.NORMAL,   ObdPids.AWD_DUTY)
        entries += entry("CVT_LOCKUP_DUTY",   SensorModule.TCU, SensorProtocol.SSM_A8,       SensorPriority.NORMAL,   ObdPids.LOCKUP_DUTY)

        // ── Subaru TCU UDS Mode 22 (SensorModule.TCU, SensorProtocol.UDS_22) ────────────────

        entries += entry("CVT_RATIO_ACTUAL",  SensorModule.TCU, SensorProtocol.UDS_22,       SensorPriority.NORMAL,   ObdPids.CVT_RATIO_ACTUAL)
        entries += entry("CVT_RATIO_TARGET",  SensorModule.TCU, SensorProtocol.UDS_22,       SensorPriority.NORMAL,   ObdPids.CVT_RATIO_TARGET)
        entries += entry("PRIMARY_PULLEY_RPM",SensorModule.TCU, SensorProtocol.UDS_22,       SensorPriority.NORMAL,   ObdPids.PRIMARY_PULLEY_SPEED)
        entries += entry("SECONDARY_PULLEY_RPM", SensorModule.TCU, SensorProtocol.UDS_22,    SensorPriority.NORMAL,   ObdPids.SECONDARY_PULLEY_SPEED)
        entries += entry("TURBINE_RPM",       SensorModule.TCU, SensorProtocol.UDS_22,       SensorPriority.NORMAL,   ObdPids.TURBINE_RPM)

        // ── Body Control Module / TPMS (SensorModule.BCM, SensorProtocol.UDS_22) ──────────

        entries += entry("TPMS_FL",           SensorModule.BCM, SensorProtocol.UDS_22,       SensorPriority.LOW,      ObdPids.TPMS_FL)
        entries += entry("TPMS_FR",           SensorModule.BCM, SensorProtocol.UDS_22,       SensorPriority.LOW,      ObdPids.TPMS_FR)
        entries += entry("TPMS_RL",           SensorModule.BCM, SensorProtocol.UDS_22,       SensorPriority.LOW,      ObdPids.TPMS_RL)
        entries += entry("TPMS_RR",           SensorModule.BCM, SensorProtocol.UDS_22,       SensorPriority.LOW,      ObdPids.TPMS_RR)

        Log.d(TAG, "buildRegistry: ${entries.size} entries created")
        return entries
    }

    private fun entry(
        id: String,
        module: SensorModule,
        protocol: SensorProtocol,
        priority: SensorPriority,
        pid: ObdPid,
    ) = SensorEntry(
        sensorId = id,
        module   = module,
        protocol = protocol,
        priority = priority,
        decoder  = pid.parse,
        obdPid   = pid,
    )
}
