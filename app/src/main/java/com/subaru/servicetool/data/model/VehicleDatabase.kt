package com.subaru.servicetool.data.model

object VehicleDatabase {

    private fun gen(
        years: IntRange,
        model: String,
        code: String,
        display: String,
        turbo: Boolean,
        ssm: Boolean = true,
    ): List<VehicleSpec> = years.map { VehicleSpec(it, model, code, display, turbo, ssmSupported = ssm) }

    val ALL_VEHICLES: List<VehicleSpec> = buildList {

        // ── Impreza (2013-2026) ───────────────────────────────────────────
        addAll(gen(2013..2016, "Impreza", "FB20B", "2.0L DOHC NA", false))
        addAll(gen(2017..2026, "Impreza", "FB20",  "2.0L DOHC NA", false))

        // ── WRX (2015-2026) ───────────────────────────────────────────────
        addAll(gen(2015..2021, "WRX", "FA20DIT", "2.0L DOHC Direct Injection Turbo", true))
        addAll(gen(2022..2026, "WRX", "FA24F",   "2.4L DOHC Turbo", true))

        // ── WRX STI (2013-2021, discontinued) ────────────────────────────
        addAll(gen(2013..2021, "WRX STI", "EJ257", "2.5L DOHC EJ-Series Turbo", true))

        // ── Crosstrek (2013-2026) ─────────────────────────────────────────
        addAll(gen(2013..2017, "Crosstrek", "FB20B", "2.0L DOHC NA", false))
        addAll(gen(2018..2026, "Crosstrek", "FB20",  "2.0L DOHC NA", false))

        // ── Forester (2013-2026) ──────────────────────────────────────────
        addAll(gen(2013..2018, "Forester", "FB25",    "2.5L DOHC NA", false))
        addAll(gen(2013..2018, "Forester", "FA20DIT", "2.0L DOHC Direct Injection Turbo (XT)", true))
        addAll(gen(2019..2026, "Forester", "FB25",    "2.5L DOHC NA", false))

        // ── Outback (2013-2026) ───────────────────────────────────────────
        addAll(gen(2013..2019, "Outback", "FB25",  "2.5L DOHC NA", false))
        addAll(gen(2013..2019, "Outback", "EZ36D", "3.6L DOHC NA (3.6R)", false))
        addAll(gen(2020..2026, "Outback", "FB25",  "2.5L DOHC NA", false))
        addAll(gen(2020..2026, "Outback", "FA24F", "2.4L DOHC Turbo (XT)", true))

        // ── Legacy (2013-2024, discontinued) ─────────────────────────────
        addAll(gen(2013..2019, "Legacy", "FB25",  "2.5L DOHC NA", false))
        addAll(gen(2013..2019, "Legacy", "EZ36D", "3.6L DOHC NA (3.6R)", false))
        addAll(gen(2020..2024, "Legacy", "FB25",  "2.5L DOHC NA", false))
        addAll(gen(2020..2024, "Legacy", "FA24F", "2.4L DOHC Turbo (XT)", true))

        // ── BRZ (2013-2020, 2022-2026 — no 2021) ─────────────────────────
        addAll(gen(2013..2020, "BRZ", "FA20D", "2.0L DOHC NA", false))
        addAll(gen(2022..2026, "BRZ", "FA24",  "2.4L DOHC NA", false))

        // ── Ascent (2019-2026) ────────────────────────────────────────────
        addAll(gen(2019..2026, "Ascent", "FA24F", "2.4L DOHC Turbo", true))

        // ── Solterra (2023-2026) ──────────────────────────────────────────
        addAll(gen(2023..2026, "Solterra", "BEV", "Electric AWD Motor", false, ssm = false))

        // ── Levorg (2015-2026) ────────────────────────────────────────────
        addAll(gen(2015..2020, "Levorg", "FB16E",    "1.6L DOHC Turbo", true))
        addAll(gen(2015..2020, "Levorg", "FA20DIT",  "2.0L DOHC Direct Injection Turbo", true))
        addAll(gen(2021..2026, "Levorg", "CB18",     "1.8L DOHC Turbo", true))
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
