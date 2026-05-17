package com.subaru.servicetool.data.model

object VehicleDatabase {

    private fun gen(
        years: IntRange,
        model: String,
        code: String,
        display: String,
        turbo: Boolean,
        ssm: Boolean = true,
        generation: String = "",
        cvtType: String? = null,
        knownIssueIds: List<String> = emptyList(),
    ): List<VehicleSpec> = years.map {
        VehicleSpec(it, model, code, display, turbo, generation, cvtType, knownIssueIds,
            ssmSupported = ssm)
    }

    val ALL_VEHICLES: List<VehicleSpec> = buildList {

        // ── Impreza ───────────────────────────────────────────────────────
        addAll(gen(2013..2016, "Impreza", "FB20B", "2.0L DOHC NA", false,
            generation = "Gen 4 (GP/GJ)", cvtType = "TR580",
            knownIssueIds = listOf("CVT_AWD_SOLENOID")))
        addAll(gen(2017..2023, "Impreza", "FB20",  "2.0L DOHC NA", false,
            generation = "Gen 5 (GT/GK)", cvtType = "TR580",
            knownIssueIds = listOf("CVT_AWD_SOLENOID")))
        addAll(gen(2024..2026, "Impreza", "FB20",  "2.0L DOHC NA", false,
            generation = "Gen 6", cvtType = "TR580"))

        // ── WRX ───────────────────────────────────────────────────────────
        addAll(gen(2015..2021, "WRX", "FA20DIT", "2.0L DOHC Direct Injection Turbo", true,
            generation = "Gen 1 (VA)"))
        addAll(gen(2022..2026, "WRX", "FA24F",   "2.4L DOHC Turbo", true,
            generation = "Gen 2 (VB)"))

        // ── WRX STI (discontinued 2021) ───────────────────────────────────
        addAll(gen(2013..2021, "WRX STI", "EJ257", "2.5L DOHC EJ-Series Turbo", true,
            generation = "VA"))

        // ── Crosstrek ─────────────────────────────────────────────────────
        addAll(gen(2013..2017, "Crosstrek", "FB20B", "2.0L DOHC NA", false,
            generation = "Gen 1 (GP)", cvtType = "TR580",
            knownIssueIds = listOf("CVT_AWD_SOLENOID")))
        addAll(gen(2018..2023, "Crosstrek", "FB20",  "2.0L DOHC NA", false,
            generation = "Gen 2 (GT)", cvtType = "TR580",
            knownIssueIds = listOf("CVT_AWD_SOLENOID")))
        addAll(gen(2024..2026, "Crosstrek", "FB20",  "2.0L DOHC NA", false,
            generation = "Gen 3", cvtType = "TR580"))

        // ── Forester ──────────────────────────────────────────────────────
        addAll(gen(2013..2018, "Forester", "FB20",    "2.0L DOHC NA", false,
            generation = "Gen 3 (SJ)", cvtType = "TR580",
            knownIssueIds = listOf("CVT_AWD_SOLENOID", "CVT_LOCKUP_SOLENOID")))
        addAll(gen(2013..2018, "Forester", "FB25",    "2.5L DOHC NA", false,
            generation = "Gen 3 (SJ)", cvtType = "TR580",
            knownIssueIds = listOf("CVT_AWD_SOLENOID", "CVT_LOCKUP_SOLENOID")))
        addAll(gen(2013..2018, "Forester", "FA20DIT", "2.0L DOHC Direct Injection Turbo (XT)", true,
            generation = "Gen 3 (SJ)", cvtType = "TR580",
            knownIssueIds = listOf("CVT_AWD_SOLENOID", "CVT_LOCKUP_SOLENOID")))
        addAll(gen(2019..2024, "Forester", "FB25",    "2.5L DOHC NA", false,
            generation = "Gen 4 (SK)", cvtType = "TR580",
            knownIssueIds = listOf("TCV_THERMOSTAT", "CVT_AWD_SOLENOID")))
        addAll(gen(2025..2026, "Forester", "FB25",    "2.5L DOHC NA", false,
            generation = "Gen 5 (SN)", cvtType = "TR580"))

        // ── Outback ───────────────────────────────────────────────────────
        addAll(gen(2013..2014, "Outback", "FB25",  "2.5L DOHC NA", false,
            generation = "Gen 4 (BR)", cvtType = "TR690",
            knownIssueIds = listOf("CVT_LOCKUP_SOLENOID")))
        addAll(gen(2013..2014, "Outback", "EZ36D", "3.6L DOHC NA (3.6R)", false,
            generation = "Gen 4 (BR)", cvtType = "TR690",
            knownIssueIds = listOf("CVT_LOCKUP_SOLENOID")))
        addAll(gen(2015..2019, "Outback", "FB25",  "2.5L DOHC NA", false,
            generation = "Gen 5 (BS)", cvtType = "TR690",
            knownIssueIds = listOf("CVT_LOCKUP_SOLENOID")))
        addAll(gen(2015..2019, "Outback", "EZ36D", "3.6L DOHC NA (3.6R)", false,
            generation = "Gen 5 (BS)", cvtType = "TR690",
            knownIssueIds = listOf("CVT_LOCKUP_SOLENOID")))
        addAll(gen(2020..2024, "Outback", "FB25",  "2.5L DOHC NA", false,
            generation = "Gen 6 (BT)", cvtType = "TF80SC"))
        addAll(gen(2020..2024, "Outback", "FA24F", "2.4L DOHC Turbo (XT)", true,
            generation = "Gen 6 (BT)", cvtType = "TF80SC"))
        addAll(gen(2025..2026, "Outback", "FB25",  "2.5L DOHC NA", false,
            generation = "Gen 7"))
        addAll(gen(2025..2026, "Outback", "FA24F", "2.4L DOHC Turbo (XT)", true,
            generation = "Gen 7"))

        // ── Legacy (discontinued 2024) ────────────────────────────────────
        addAll(gen(2013..2014, "Legacy", "FB25",  "2.5L DOHC NA", false,
            generation = "Gen 5 (BM)", cvtType = "TR690",
            knownIssueIds = listOf("CVT_LOCKUP_SOLENOID")))
        addAll(gen(2013..2014, "Legacy", "EZ36D", "3.6L DOHC NA (3.6R)", false,
            generation = "Gen 5 (BM)", cvtType = "TR690",
            knownIssueIds = listOf("CVT_LOCKUP_SOLENOID")))
        addAll(gen(2015..2019, "Legacy", "FB25",  "2.5L DOHC NA", false,
            generation = "Gen 6 (BS)", cvtType = "TR690",
            knownIssueIds = listOf("CVT_LOCKUP_SOLENOID")))
        addAll(gen(2015..2019, "Legacy", "EZ36D", "3.6L DOHC NA (3.6R)", false,
            generation = "Gen 6 (BS)", cvtType = "TR690",
            knownIssueIds = listOf("CVT_LOCKUP_SOLENOID")))
        addAll(gen(2020..2024, "Legacy", "FB25",  "2.5L DOHC NA", false,
            generation = "Gen 7 (BT)"))
        addAll(gen(2020..2024, "Legacy", "FA24F", "2.4L DOHC Turbo (XT)", true,
            generation = "Gen 7 (BT)"))

        // ── BRZ (no 2021) ─────────────────────────────────────────────────
        addAll(gen(2013..2020, "BRZ", "FA20D", "2.0L DOHC NA", false,
            generation = "Gen 1 (ZC6)"))
        addAll(gen(2022..2026, "BRZ", "FA24",  "2.4L DOHC NA", false,
            generation = "Gen 2 (ZD8)"))

        // ── Ascent ────────────────────────────────────────────────────────
        addAll(gen(2019..2023, "Ascent", "FA24F", "2.4L DOHC Turbo", true,
            generation = "Gen 1", cvtType = "CVT",
            knownIssueIds = listOf("CVT_ASCENT_PRESSURE")))
        addAll(gen(2024..2026, "Ascent", "FA24F", "2.4L DOHC Turbo", true,
            generation = "Gen 2", cvtType = "CVT"))

        // ── Solterra (2023-2026) ──────────────────────────────────────────
        addAll(gen(2023..2026, "Solterra", "BEV", "Electric AWD Motor", false,
            ssm = false, generation = "Gen 1"))

        // ── Levorg ────────────────────────────────────────────────────────
        addAll(gen(2015..2020, "Levorg", "FB16E",   "1.6L DOHC Turbo", true,
            generation = "Gen 1"))
        addAll(gen(2015..2020, "Levorg", "FA20DIT", "2.0L DOHC Direct Injection Turbo", true,
            generation = "Gen 1"))
        addAll(gen(2021..2026, "Levorg", "CB18",    "1.8L DOHC Turbo", true,
            generation = "Gen 2"))
    }

    fun getAvailableYears(): List<Int> =
        ALL_VEHICLES.map { it.year }.distinct().sorted()

    fun getModelsForYear(year: Int): List<String> =
        ALL_VEHICLES.filter { it.year == year }.map { it.modelName }.distinct().sorted()

    fun getSpecsForYearAndModel(year: Int, model: String): List<VehicleSpec> =
        ALL_VEHICLES.filter { it.year == year && it.modelName == model }

    fun findVehicle(year: Int, model: String, engineCode: String): VehicleSpec? =
        ALL_VEHICLES.find { it.year == year && it.modelName == model && it.engineCode == engineCode }
}
