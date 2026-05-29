package com.subaru.servicetool.data.obd

object ObdPids {

    // ── Standard OBD-II Mode 01 — work on ALL ELM327 adapters ────────────────

    val RPM = ObdPid(
        cmd = "010C", name = "Engine RPM", unit = "rpm",
        minVal = 0f, maxVal = 8000f, group = PidGroup.ENGINE,
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() / 4f else null }

    val SPEED = ObdPid(
        cmd = "010D", name = "Vehicle Speed", unit = "km/h",
        minVal = 0f, maxVal = 240f, group = PidGroup.ENGINE,
    ) { b -> if (b.isNotEmpty()) b[0].toFloat() else null }

    val COOLANT_TEMP = ObdPid(
        cmd = "0105", name = "Coolant Temp", unit = "°C",
        minVal = -40f, maxVal = 215f, group = PidGroup.TEMPERATURE,
    ) { b -> if (b.isNotEmpty()) (b[0] - 40).toFloat() else null }

    val THROTTLE = ObdPid(
        cmd = "0111", name = "Throttle Position", unit = "%",
        minVal = 0f, maxVal = 100f, group = PidGroup.ENGINE,
    ) { b -> if (b.isNotEmpty()) b[0] / 255f * 100f else null }

    val ENGINE_LOAD = ObdPid(
        cmd = "0104", name = "Engine Load", unit = "%",
        minVal = 0f, maxVal = 100f, group = PidGroup.ENGINE,
    ) { b -> if (b.isNotEmpty()) b[0] / 255f * 100f else null }

    val INTAKE_TEMP = ObdPid(
        cmd = "010F", name = "Intake Air Temp", unit = "°C",
        minVal = -40f, maxVal = 215f, group = PidGroup.TEMPERATURE,
    ) { b -> if (b.isNotEmpty()) (b[0] - 40).toFloat() else null }

    val MAF = ObdPid(
        cmd = "0110", name = "MAF Air Flow", unit = "g/s",
        minVal = 0f, maxVal = 655f, group = PidGroup.ENGINE,
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() / 100f else null }

    val MAP = ObdPid(
        cmd = "010B", name = "Manifold Pressure", unit = "kPa",
        minVal = 0f, maxVal = 255f, group = PidGroup.ENGINE,
    ) { b -> if (b.isNotEmpty()) b[0].toFloat() else null }

    val FUEL_LEVEL = ObdPid(
        cmd = "012F", name = "Fuel Level", unit = "%",
        minVal = 0f, maxVal = 100f, group = PidGroup.FUEL,
    ) { b -> if (b.isNotEmpty()) b[0] / 255f * 100f else null }

    // Mode 01 PID 42 — control module voltage (replaces ELM ATRV AT command)
    val VOLTAGE = ObdPid(
        cmd = "0142", name = "Battery Voltage", unit = "V",
        minVal = 10f, maxVal = 16f, group = PidGroup.MISC,
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() / 1000f else null }

    val FUEL_TRIM_ST = ObdPid(
        cmd = "0106", name = "Fuel Trim (Short)", unit = "%",
        minVal = -100f, maxVal = 99.2f, group = PidGroup.FUEL,
    ) { b -> if (b.isNotEmpty()) (b[0] - 128) * 100f / 128f else null }

    val FUEL_TRIM_LT = ObdPid(
        cmd = "0107", name = "Fuel Trim (Long)", unit = "%",
        minVal = -100f, maxVal = 99.2f, group = PidGroup.FUEL,
    ) { b -> if (b.isNotEmpty()) (b[0] - 128) * 100f / 128f else null }

    val AMBIENT_TEMP = ObdPid(
        cmd = "0146", name = "Ambient Air Temp", unit = "°C",
        minVal = -40f, maxVal = 215f, group = PidGroup.TEMPERATURE,
    ) { b -> if (b.isNotEmpty()) (b[0] - 40).toFloat() else null }

    val O2_VOLTAGE = ObdPid(
        cmd = "0114", name = "O2 Sensor Voltage", unit = "V",
        minVal = 0f, maxVal = 1.275f, group = PidGroup.ENGINE,
    ) { b -> if (b.isNotEmpty()) b[0].toFloat() / 200f else null }

    val RUN_TIME = ObdPid(
        cmd = "011F", name = "Run Time", unit = "s",
        minVal = 0f, maxVal = 65535f, group = PidGroup.MISC,
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() else null }

    // Mode 01 PID 24 — O2 sensor equivalence ratio (bank 1, sensor 1) → stoich AFR for gasoline
    val AFR = ObdPid(
        cmd = "0124", name = "Air-Fuel Ratio", unit = "AFR",
        minVal = 7f, maxVal = 22f, group = PidGroup.FUEL,
    ) { b -> if (b.size >= 2) 2f * (b[0] * 256 + b[1]).toFloat() / 65536f * 14.7f else null }

    // Mode 01 PID 33 — absolute barometric pressure; used to derive manifold boost
    val BAROMETRIC_PRESS = ObdPid(
        cmd = "0133", name = "Barometric Press", unit = "kPa",
        minVal = 70f, maxVal = 110f, group = PidGroup.ENGINE,
    ) { b -> if (b.isNotEmpty()) b[0].toFloat() else null }

    val REL_THROTTLE = ObdPid(
        cmd = "0145", name = "Rel. Throttle Pos", unit = "%",
        minVal = 0f, maxVal = 100f, group = PidGroup.ENGINE,
    ) { b -> if (b.isNotEmpty()) b[0] / 2.55f else null }

    val ABS_LOAD = ObdPid(
        cmd = "0143", name = "Absolute Load", unit = "%",
        minVal = 0f, maxVal = 25700f, group = PidGroup.ENGINE,
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() / 2.55f else null }

    // ── Subaru ECU SSM (Header: 7E0, Mode: 21/22) ────────────────────────────
    // Init sets ATSH7E0; restored automatically after every non-ECM batch.
    // Mode 21 request/response: 21 XX  →  61 XX <data>
    // Mode 22 request/response: 22 XX YY  →  62 XX YY <data>

    val ECM_COOLANT_TEMP = ObdPid(
        cmd = "2101", name = "ECM Coolant Temp", unit = "°C",
        minVal = -40f, maxVal = 215f, group = PidGroup.TEMPERATURE,
        header = "7E0",
    ) { b -> if (b.isNotEmpty()) (b[0] - 40).toFloat() else null }

    // SSM A8 address 0x0000AF; primary probe address for engine oil temp
    // Probe order: 1) OBD-II PID 5C (015C), 2) SSM 0x0000AF, 3) SSM 0x009D5C
    val OIL_TEMP = ObdPid(
        cmd = "2200AF", name = "Engine Oil Temp", unit = "°C",
        minVal = -40f, maxVal = 215f, group = PidGroup.TEMPERATURE,
        header = "7E0", ssmAddress = 0x0000AF,
    ) { b -> if (b.isNotEmpty()) { val v = b[0] - 40; if (v < -40 || v > 215) null else v.toFloat() } else null }

    val BATTERY_TEMP = ObdPid(
        cmd = "221136", name = "Battery Temperature", unit = "°C",
        minVal = -40f, maxVal = 100f, group = PidGroup.TEMPERATURE,
        header = "7E0", ssmAddress = 0x001136,
    ) { b -> if (b.isNotEmpty()) (b[0] - 40).toFloat() else null }

    val RADIATOR_FAN = ObdPid(
        cmd = "2210E3", name = "Radiator Fan Control", unit = "%",
        minVal = 0f, maxVal = 100f, group = PidGroup.MISC,
    ) { b -> if (b.isNotEmpty()) b[0].toFloat() else null }

    // Feedback Knock Correction — turbo engines only
    val KNOCK_CORRECTION = ObdPid(
        cmd = "2105", name = "Knock Correction", unit = "°",
        minVal = -64f, maxVal = 63.5f, group = PidGroup.ENGINE,
        header = "7E0", isTurboOnly = true,
    ) { b -> if (b.isNotEmpty()) (b[0].toFloat() - 128f) * 0.5f else null }

    // Subaru SSM Mode 22 — exhaust gas temperature; equation: ((A×256)+B) × 0.1 − 40  (°C)
    val EGT = ObdPid(
        cmd = "221155", name = "Exhaust Gas Temp", unit = "°C",
        minVal = -40f, maxVal = 1000f, group = PidGroup.TEMPERATURE,
        header = "7E0",
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() * 0.1f - 40f else null }

    val WASTEGATE = ObdPid(
        cmd = "2210C9", name = "Wastegate Control", unit = "%",
        minVal = 0f, maxVal = 100f, group = PidGroup.ENGINE,
        isTurboOnly = true,
    ) { b -> if (b.isNotEmpty()) b[0].toFloat() else null }

    // Throttle motor duty — available on all electronic throttle engines
    val THROTTLE_MOTOR = ObdPid(
        cmd = "22105F", name = "Throttle Motor Duty", unit = "%",
        minVal = -50f, maxVal = 50f, group = PidGroup.ENGINE,
        header = "7E0", ssmAddress = 0x00105F,
    ) { b -> if (b.isNotEmpty()) b[0].toFloat() * 100f / 255f - 50f else null }

    val ALTERNATOR_DUTY = ObdPid(
        cmd = "2210B2", name = "Alternator Duty", unit = "%",
        minVal = 0f, maxVal = 100f, group = PidGroup.MISC,
        header = "7E0", ssmAddress = 0x0010B2,
    ) { b -> if (b.isNotEmpty()) b[0].toFloat() * 100f / 255f else null }

    val FUEL_PUMP = ObdPid(
        cmd = "2210B3", name = "Fuel Pump Duty", unit = "%",
        minVal = 0f, maxVal = 100f, group = PidGroup.FUEL,
    ) { b -> if (b.isNotEmpty()) b[0].toFloat() * 100f / 255f else null }

    // VVT advance angles — SSM A8, equation: A × 0.5 (signed, 0x80 = 0°)
    val VVT_LEFT = ObdPid(
        cmd = "2210B5", name = "VVT Advance L", unit = "°",
        minVal = -50f, maxVal = 50f, group = PidGroup.ENGINE,
        header = "7E0", ssmAddress = 0x0010B5,
    ) { b -> if (b.isNotEmpty()) (b[0].toFloat() - 128f) * 0.5f else null }

    val VVT_RIGHT = ObdPid(
        cmd = "2210B4", name = "VVT Advance R", unit = "°",
        minVal = -50f, maxVal = 50f, group = PidGroup.ENGINE,
        header = "7E0", ssmAddress = 0x0010B4,
    ) { b -> if (b.isNotEmpty()) (b[0].toFloat() - 128f) * 0.5f else null }

    // ── Subaru TCU SSM (Header: 7E1, Mode: 21/22) ────────────────────────────
    // Engine sends ATSH7E1 before batch, restores ATSH7E0 after.
    // Mode 21 responses arrive from 7E9.

    // SSM A8 to TCU (7E1); equation: A−40, valid range −40 to 150°C
    val CVT_TEMP = ObdPid(
        cmd = "221017", name = "CVT Fluid Temp", unit = "°C",
        minVal = -40f, maxVal = 150f, group = PidGroup.TEMPERATURE,
        header = "7E1", ssmAddress = 0x001017,
    ) { b -> if (b.isNotEmpty()) { val v = b[0] - 40; if (v < -40 || v > 150) null else v.toFloat() } else null }

    // 0 % = FWD coast, >0 % = AWD engaged — SSM A8 to TCU, equation: A×100/255
    val AWD_DUTY = ObdPid(
        cmd = "221065", name = "AWD Transfer Duty", unit = "%",
        minVal = 0f, maxVal = 100f, group = PidGroup.TRANSMISSION,
        header = "7E1", ssmAddress = 0x001065,
    ) { b -> if (b.isNotEmpty()) b[0].toFloat() * 100f / 255f else null }

    val LOCKUP_DUTY = ObdPid(
        cmd = "221045", name = "CVT Lock-Up Duty", unit = "%",
        minVal = 0f, maxVal = 100f, group = PidGroup.TRANSMISSION,
        header = "7E1", ssmAddress = 0x001045,
    ) { b -> if (b.isNotEmpty()) b[0].toFloat() * 100f / 255f else null }

    // CVT actual gear ratio — equation: ((A × 256) + B) / 1000
    val CVT_RATIO_ACTUAL = ObdPid(
        cmd = "2140", name = "CVT Gear Ratio", unit = "ratio",
        minVal = 0f, maxVal = 6f, group = PidGroup.TRANSMISSION,
        header = "7E1",
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() / 1000f else null }

    // CVT target ratio — separate SSM address; same ratio scale
    val CVT_RATIO_TARGET = ObdPid(
        cmd = "2230F8", name = "CVT Target Ratio", unit = "ratio",
        minVal = 0f, maxVal = 6f, group = PidGroup.TRANSMISSION,
        header = "7E1",
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() / 1000f else null }

    // Pulley and turbine speeds — equation: (A × 256) + B  (rpm)
    val PRIMARY_PULLEY_SPEED = ObdPid(
        cmd = "2102", name = "Primary Pulley Speed", unit = "rpm",
        minVal = 0f, maxVal = 8000f, group = PidGroup.TRANSMISSION,
        header = "7E1",
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() else null }

    val SECONDARY_PULLEY_SPEED = ObdPid(
        cmd = "2103", name = "Secondary Pulley Speed", unit = "rpm",
        minVal = 0f, maxVal = 8000f, group = PidGroup.TRANSMISSION,
        header = "7E1",
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() else null }

    val TURBINE_RPM = ObdPid(
        cmd = "2104", name = "Turbine Revolution", unit = "rpm",
        minVal = 0f, maxVal = 8000f, group = PidGroup.TRANSMISSION,
        header = "7E1",
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() else null }

    // ── Body Control Module / TPMS (Header: 7D4, Mode: 22) ───────────────────
    // ATSH7D4 set before batch; some model years may need 7E4 instead.
    // Equation: ((A × 256) + B) / 10  (kPa)

    val TPMS_FL = ObdPid(
        cmd = "221501", name = "Tire Pressure FL", unit = "kPa",
        minVal = 0f, maxVal = 500f, group = PidGroup.MISC,
        header = "7D4",
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() / 10f else null }

    val TPMS_FR = ObdPid(
        cmd = "221502", name = "Tire Pressure FR", unit = "kPa",
        minVal = 0f, maxVal = 500f, group = PidGroup.MISC,
        header = "7D4",
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() / 10f else null }

    val TPMS_RL = ObdPid(
        cmd = "221503", name = "Tire Pressure RL", unit = "kPa",
        minVal = 0f, maxVal = 500f, group = PidGroup.MISC,
        header = "7D4",
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() / 10f else null }

    val TPMS_RR = ObdPid(
        cmd = "221504", name = "Tire Pressure RR", unit = "kPa",
        minVal = 0f, maxVal = 500f, group = PidGroup.MISC,
        header = "7D4",
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() / 10f else null }

    // ── Grouped sets ──────────────────────────────────────────────────────────

    val DASHBOARD = listOf(RPM, SPEED, COOLANT_TEMP, THROTTLE, INTAKE_TEMP, VOLTAGE)

    val EXTENDED = listOf(
        ENGINE_LOAD, MAP, MAF,
        FUEL_LEVEL, FUEL_TRIM_ST, FUEL_TRIM_LT,
        REL_THROTTLE, ABS_LOAD, RUN_TIME,
        AFR, BAROMETRIC_PRESS,
        OIL_TEMP, ECM_COOLANT_TEMP, CVT_TEMP, AMBIENT_TEMP,
        AWD_DUTY, LOCKUP_DUTY, CVT_RATIO_ACTUAL, CVT_RATIO_TARGET,
        PRIMARY_PULLEY_SPEED, SECONDARY_PULLEY_SPEED, TURBINE_RPM,
        BATTERY_TEMP, RADIATOR_FAN, KNOCK_CORRECTION, EGT, WASTEGATE,
        THROTTLE_MOTOR, ALTERNATOR_DUTY, FUEL_PUMP, VVT_LEFT, VVT_RIGHT,
        O2_VOLTAGE,
    )

    val TPMS = listOf(TPMS_FL, TPMS_FR, TPMS_RL, TPMS_RR)

    val ALL: List<ObdPid> = DASHBOARD + EXTENDED + TPMS

    // PIDs available for gauge configuration (excludes AWD_DUTY which has its own widget, and TPMS)
    val CONFIGURABLE: List<ObdPid> = (DASHBOARD + EXTENDED.filter { it != AWD_DUTY }).distinctBy { it.cmd }

    // ── Adaptive polling tiers ────────────────────────────────────────────────

    /** Polled every cycle — critical real-time readouts. */
    val TIER1 = listOf(RPM, SPEED, COOLANT_TEMP, THROTTLE)

    /** Polled every N cycles (N = profile.tier2Every). */
    val TIER2 = listOf(OIL_TEMP, CVT_TEMP, ENGINE_LOAD, VOLTAGE, INTAKE_TEMP, EGT)

    /** Polled every M cycles (M = profile.tier3Every). */
    val TIER3 = listOf(
        MAF, MAP, FUEL_TRIM_ST, FUEL_TRIM_LT, AMBIENT_TEMP, FUEL_LEVEL,
        AFR, BAROMETRIC_PRESS,
        AWD_DUTY, REL_THROTTLE, ABS_LOAD, ECM_COOLANT_TEMP,
        KNOCK_CORRECTION, WASTEGATE, VVT_LEFT, VVT_RIGHT,
        LOCKUP_DUTY, CVT_RATIO_ACTUAL, CVT_RATIO_TARGET,
        PRIMARY_PULLEY_SPEED, SECONDARY_PULLEY_SPEED, TURBINE_RPM,
        ALTERNATOR_DUTY, O2_VOLTAGE,
    )

    /** Polled every K cycles (K = profile.tier4Every) — slow-changing values. */
    val TIER4 = listOf(
        TPMS_FL, TPMS_FR, TPMS_RL, TPMS_RR, RUN_TIME,
        BATTERY_TEMP, RADIATOR_FAN, FUEL_PUMP, THROTTLE_MOTOR,
    )
}
