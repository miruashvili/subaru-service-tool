package com.subaru.servicetool.data.obd

object ObdPids {

    // ── Dashboard (polled every round) ────────────────────────────────────────

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

    val INTAKE_TEMP = ObdPid(
        cmd = "010F", name = "Intake Air Temp", unit = "°C",
        minVal = -40f, maxVal = 215f, group = PidGroup.TEMPERATURE,
    ) { b -> if (b.isNotEmpty()) (b[0] - 40).toFloat() else null }

    val OIL_TEMP = ObdPid(
        cmd = "015C", name = "Engine Oil Temp", unit = "°C",
        minVal = -40f, maxVal = 215f, group = PidGroup.TEMPERATURE,
    ) { b -> if (b.isNotEmpty()) (b[0] - 40).toFloat() else null }

    // ATRV is an ELM327 AT command, not a standard PID; parsed via ObdParser.parseVoltage
    val VOLTAGE = ObdPid(
        cmd = "ATRV", name = "Battery Voltage", unit = "V",
        minVal = 10f, maxVal = 16f, group = PidGroup.MISC,
    ) { _ -> null }

    // ── Extended (polled every 3rd round) ────────────────────────────────────

    val ENGINE_LOAD = ObdPid(
        cmd = "0104", name = "Engine Load", unit = "%",
        minVal = 0f, maxVal = 100f, group = PidGroup.ENGINE,
    ) { b -> if (b.isNotEmpty()) b[0] / 255f * 100f else null }

    val MAP = ObdPid(
        cmd = "010B", name = "Manifold Pressure", unit = "kPa",
        minVal = 0f, maxVal = 255f, group = PidGroup.ENGINE,
    ) { b -> if (b.isNotEmpty()) b[0].toFloat() else null }

    val MAF = ObdPid(
        cmd = "0110", name = "MAF Air Flow", unit = "g/s",
        minVal = 0f, maxVal = 655f, group = PidGroup.ENGINE,
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() / 100f else null }

    val FUEL_LEVEL = ObdPid(
        cmd = "012F", name = "Fuel Level", unit = "%",
        minVal = 0f, maxVal = 100f, group = PidGroup.FUEL,
    ) { b -> if (b.isNotEmpty()) b[0] / 255f * 100f else null }

    val FUEL_TRIM_ST = ObdPid(
        cmd = "0106", name = "Fuel Trim (Short)", unit = "%",
        minVal = -100f, maxVal = 99.2f, group = PidGroup.FUEL,
    ) { b -> if (b.isNotEmpty()) (b[0] - 128) * 100f / 128f else null }

    val FUEL_TRIM_LT = ObdPid(
        cmd = "0107", name = "Fuel Trim (Long)", unit = "%",
        minVal = -100f, maxVal = 99.2f, group = PidGroup.FUEL,
    ) { b -> if (b.isNotEmpty()) (b[0] - 128) * 100f / 128f else null }

    val REL_THROTTLE = ObdPid(
        cmd = "0145", name = "Rel. Throttle Pos", unit = "%",
        minVal = 0f, maxVal = 100f, group = PidGroup.ENGINE,
    ) { b -> if (b.isNotEmpty()) b[0] / 2.55f else null }

    val ABS_LOAD = ObdPid(
        cmd = "0143", name = "Absolute Load", unit = "%",
        minVal = 0f, maxVal = 25700f, group = PidGroup.ENGINE,
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() / 2.55f else null }

    val RUN_TIME = ObdPid(
        cmd = "011F", name = "Run Time", unit = "s",
        minVal = 0f, maxVal = 65535f, group = PidGroup.MISC,
    ) { b -> if (b.size >= 2) (b[0] * 256 + b[1]).toFloat() else null }

    val AMBIENT_TEMP = ObdPid(
        cmd = "0146", name = "Ambient Air Temp", unit = "°C",
        minVal = -40f, maxVal = 215f, group = PidGroup.TEMPERATURE,
    ) { b -> if (b.isNotEmpty()) (b[0] - 40).toFloat() else null }

    // ── TPMS (polled every 5th round, non-standard extended PIDs) ─────────────
    // Pressure in kPa = byte * 3 (typical OBD encoding for TPMS)

    val TPMS_FL = ObdPid(
        cmd = "01C1", name = "Tire Pressure FL", unit = "kPa",
        minVal = 0f, maxVal = 500f, group = PidGroup.MISC,
    ) { b -> if (b.isNotEmpty()) b[0] * 3f else null }

    val TPMS_FR = ObdPid(
        cmd = "01C2", name = "Tire Pressure FR", unit = "kPa",
        minVal = 0f, maxVal = 500f, group = PidGroup.MISC,
    ) { b -> if (b.isNotEmpty()) b[0] * 3f else null }

    val TPMS_RL = ObdPid(
        cmd = "01C3", name = "Tire Pressure RL", unit = "kPa",
        minVal = 0f, maxVal = 500f, group = PidGroup.MISC,
    ) { b -> if (b.isNotEmpty()) b[0] * 3f else null }

    val TPMS_RR = ObdPid(
        cmd = "01C4", name = "Tire Pressure RR", unit = "kPa",
        minVal = 0f, maxVal = 500f, group = PidGroup.MISC,
    ) { b -> if (b.isNotEmpty()) b[0] * 3f else null }

    // ── Grouped sets ──────────────────────────────────────────────────────────

    val DASHBOARD = listOf(RPM, SPEED, COOLANT_TEMP, THROTTLE, INTAKE_TEMP, VOLTAGE)

    val EXTENDED = listOf(
        ENGINE_LOAD, MAP, MAF,
        FUEL_LEVEL, FUEL_TRIM_ST, FUEL_TRIM_LT,
        REL_THROTTLE, ABS_LOAD, RUN_TIME,
        OIL_TEMP, AMBIENT_TEMP,
    )

    val TPMS = listOf(TPMS_FL, TPMS_FR, TPMS_RL, TPMS_RR)

    val ALL: List<ObdPid> = DASHBOARD + EXTENDED + TPMS

    // PIDs available for gauge configuration (excludes ATRV and TPMS)
    val CONFIGURABLE: List<ObdPid> = (DASHBOARD.filter { it.cmd != "ATRV" } + EXTENDED).distinctBy { it.cmd }
}
