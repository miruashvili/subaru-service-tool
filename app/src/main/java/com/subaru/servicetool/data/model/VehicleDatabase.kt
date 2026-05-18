package com.subaru.servicetool.data.model

object VehicleDatabase {

    private fun gen(
        years: IntRange,
        model: String,
        code: String,
        display: String,
        turbo: Boolean,
        market: Market = Market.GLOBAL,
        ssm: Boolean = true,
        generation: String = "",
        cvtType: String? = null,
        knownIssueIds: List<String> = emptyList(),
    ): List<VehicleSpec> = years.map {
        VehicleSpec(it, model, code, display, turbo, market, generation, cvtType, knownIssueIds,
            ssmSupported = ssm)
    }

    val ALL_VEHICLES: List<VehicleSpec> = buildList {

        // ── Impreza (GLOBAL) ──────────────────────────────────────────────
        addAll(gen(2013..2016, "Impreza", "FB20B", "2.0L DOHC NA", false,
            generation = "Gen 4 (GP/GJ)", cvtType = "TR580",
            knownIssueIds = listOf("CVT_AWD_SOLENOID")))
        addAll(gen(2017..2023, "Impreza", "FB20",  "2.0L DOHC NA", false,
            generation = "Gen 5 (GT/GK)", cvtType = "TR580",
            knownIssueIds = listOf("CVT_AWD_SOLENOID")))
        addAll(gen(2024..2026, "Impreza", "FB20",  "2.0L DOHC NA", false,
            generation = "Gen 6", cvtType = "TR580"))

        // ── WRX (GLOBAL) ──────────────────────────────────────────────────
        addAll(gen(2015..2021, "WRX", "FA20DIT", "2.0L DOHC Direct Injection Turbo", true,
            generation = "Gen 1 (VA)"))
        addAll(gen(2022..2026, "WRX", "FA24F",   "2.4L DOHC Turbo", true,
            generation = "Gen 2 (VB)"))

        // ── WRX S4 (JDM only) ─────────────────────────────────────────────
        addAll(gen(2014..2021, "WRX S4", "FA20DIT", "2.0L DOHC Direct Injection Turbo", true,
            market = Market.JDM, generation = "Gen 1 (VAG)"))
        addAll(gen(2022..2026, "WRX S4", "FA24F",   "2.4L DOHC Turbo", true,
            market = Market.JDM, generation = "Gen 2 (VBH)"))

        // ── WRX STI (discontinued 2021) ───────────────────────────────────
        addAll(gen(2013..2021, "WRX STI", "EJ257", "2.5L DOHC EJ-Series Turbo", true,
            generation = "VA"))

        // ── Crosstrek (GLOBAL) ────────────────────────────────────────────
        addAll(gen(2013..2017, "Crosstrek", "FB20B", "2.0L DOHC NA", false,
            generation = "Gen 1 (GP)", cvtType = "TR580",
            knownIssueIds = listOf("CVT_AWD_SOLENOID")))
        addAll(gen(2018..2023, "Crosstrek", "FB20",  "2.0L DOHC NA", false,
            generation = "Gen 2 (GT)", cvtType = "TR580",
            knownIssueIds = listOf("CVT_AWD_SOLENOID")))
        addAll(gen(2024..2026, "Crosstrek", "FB20",  "2.0L DOHC NA", false,
            generation = "Gen 3", cvtType = "TR580"))

        // ── XV (EU name for Crosstrek) ────────────────────────────────────
        addAll(gen(2013..2017, "XV", "FB20B", "2.0L DOHC NA", false,
            market = Market.EU, generation = "Gen 1 (GP)", cvtType = "TR580",
            knownIssueIds = listOf("CVT_AWD_SOLENOID")))
        addAll(gen(2018..2022, "XV", "FB20",  "2.0L DOHC NA", false,
            market = Market.EU, generation = "Gen 2 (GT)", cvtType = "TR580",
            knownIssueIds = listOf("CVT_AWD_SOLENOID")))
        addAll(gen(2018..2022, "XV", "FB20e", "2.0L e-BOXER Hybrid", false,
            market = Market.EU, generation = "Gen 2 (GT)", cvtType = "TR580"))

        // ── Forester (GLOBAL) ─────────────────────────────────────────────
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
            knownIssueIds = listOf("TCV_THERMOSTAT")))
        addAll(gen(2025..2026, "Forester", "FB25",    "2.5L DOHC NA", false,
            generation = "Gen 5 (SN)", cvtType = "TR580"))

        // ── Outback (GLOBAL) ──────────────────────────────────────────────
        addAll(gen(2013..2014, "Outback", "FB25",  "2.5L DOHC NA", false,
            generation = "Gen 4 (BR)", cvtType = "TR690",
            knownIssueIds = listOf("CVT_LOCKUP_SOLENOID")))
        addAll(gen(2013..2014, "Outback", "EZ36D", "3.6L DOHC NA (3.6R)", false,
            generation = "Gen 4 (BR)", cvtType = "TR690",
            knownIssueIds = listOf("CVT_LOCKUP_SOLENOID")))
        addAll(gen(2015..2019, "Outback", "FB25",  "2.5L DOHC NA", false,
            generation = "Gen 5 (BS)", cvtType = "TR690",
            knownIssueIds = listOf("CVT_LOCKUP_SOLENOID", "TCV_THERMOSTAT")))
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

        // ── Ascent (GLOBAL) ───────────────────────────────────────────────
        addAll(gen(2019..2023, "Ascent", "FA24F", "2.4L DOHC Turbo", true,
            generation = "Gen 1", cvtType = "CVT",
            knownIssueIds = listOf("CVT_ASCENT_PRESSURE")))
        addAll(gen(2024..2026, "Ascent", "FA24F", "2.4L DOHC Turbo", true,
            generation = "Gen 2", cvtType = "CVT"))

        // ── Solterra (GLOBAL, 2023-2026) ──────────────────────────────────
        addAll(gen(2023..2026, "Solterra", "BEV", "Electric AWD Motor", false,
            ssm = false, generation = "Gen 1"))

        // ── Levorg Gen 1 (JDM) ────────────────────────────────────────────
        addAll(gen(2015..2020, "Levorg", "FB16E",   "1.6L DOHC Turbo", true,
            market = Market.JDM, generation = "Gen 1 (VM)"))
        addAll(gen(2015..2020, "Levorg", "FA20DIT", "2.0L DOHC Direct Injection Turbo", true,
            market = Market.JDM, generation = "Gen 1 (VM)"))

        // ── Levorg Gen 1 (EU) ─────────────────────────────────────────────
        addAll(gen(2016..2021, "Levorg", "FB16E", "1.6L DOHC Turbo", true,
            market = Market.EU, generation = "Gen 1 EU"))

        // ── Levorg Gen 2 (JDM + EU) ───────────────────────────────────────
        addAll(gen(2021..2026, "Levorg", "CB18", "1.8L DOHC Turbo", true,
            market = Market.JDM, generation = "Gen 2 (VN)"))
        addAll(gen(2022..2026, "Levorg", "CB18", "1.8L DOHC Turbo", true,
            market = Market.EU, generation = "Gen 2 EU"))

        // ── Layback (JDM, 2023-2026) ──────────────────────────────────────
        addAll(gen(2023..2026, "Layback", "FB20", "2.0L e-BOXER Hybrid", false,
            market = Market.JDM, generation = "Gen 1"))
        addAll(gen(2023..2026, "Layback", "CB18", "1.8L DOHC Turbo", true,
            market = Market.JDM, generation = "Gen 1"))

        // ── Exiga (JDM, 2012-2018) ────────────────────────────────────────
        addAll(gen(2012..2018, "Exiga", "FB20",  "2.0L DOHC NA", false,
            market = Market.JDM, ssm = false, generation = "YA"))
        addAll(gen(2012..2014, "Exiga", "EJ25",  "2.5L DOHC NA", false,
            market = Market.JDM, ssm = false, generation = "YA"))
    }

    fun getAvailableYears(): List<Int> =
        ALL_VEHICLES.map { it.year }.distinct().sorted()

    fun getMarketsForYear(year: Int): List<Market> =
        ALL_VEHICLES.filter { it.year == year }.map { it.market }.distinct()
            .sortedBy { it.ordinal }

    fun getModelsForYearAndMarket(year: Int, market: Market): List<String> =
        ALL_VEHICLES.filter { it.year == year && it.market == market }
            .map { it.modelName }.distinct().sorted()

    fun getModelsForYear(year: Int): List<String> =
        ALL_VEHICLES.filter { it.year == year }.map { it.modelName }.distinct().sorted()

    fun getSpecsForYearMarketAndModel(year: Int, market: Market, model: String): List<VehicleSpec> =
        ALL_VEHICLES.filter { it.year == year && it.market == market && it.modelName == model }

    fun getSpecsForYearAndModel(year: Int, model: String): List<VehicleSpec> =
        ALL_VEHICLES.filter { it.year == year && it.modelName == model }

    fun findVehicle(year: Int, model: String, engineCode: String, market: Market = Market.GLOBAL): VehicleSpec? =
        ALL_VEHICLES.find { it.year == year && it.modelName == model && it.engineCode == engineCode && it.market == market }
            ?: ALL_VEHICLES.find { it.year == year && it.modelName == model && it.engineCode == engineCode }
}
