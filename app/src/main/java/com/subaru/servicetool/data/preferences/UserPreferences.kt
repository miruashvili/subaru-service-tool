package com.subaru.servicetool.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.subaru.servicetool.data.bluetooth.OBDConnectionType
import com.subaru.servicetool.data.model.Market
import com.subaru.servicetool.data.model.VehicleDatabase
import com.subaru.servicetool.data.model.VehicleSpec
import com.subaru.servicetool.data.service.ServiceEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class DisplayUnits(
    val temperatureUnit: String = "celsius",  // "celsius" | "fahrenheit"
    val pressureUnit: String = "kpa",          // "kpa" | "bar" | "psi"
    val fuelUnit: String = "L100",             // "L100" | "MPG" | "KML"
)

@Singleton
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val KEY_ONBOARDING_DONE  = booleanPreferencesKey("onboarding_complete")
        private val KEY_SELECTED_YEAR    = intPreferencesKey("selected_year")
        private val KEY_SELECTED_MODEL   = stringPreferencesKey("selected_model")
        private val KEY_SELECTED_ENGINE  = stringPreferencesKey("selected_engine_code")
        private val KEY_LAST_DEVICE_MAC  = stringPreferencesKey("last_device_mac")
        private val KEY_LAST_DEVICE_TYPE = stringPreferencesKey("last_device_type")
        private val KEY_SERVICE_LOG      = stringPreferencesKey("service_log")
        private val KEY_THEME            = stringPreferencesKey("theme")
        private val KEY_TEMP_UNIT        = stringPreferencesKey("temp_unit")
        private val KEY_PRESS_UNIT       = stringPreferencesKey("press_unit")
        private val KEY_LANGUAGE         = stringPreferencesKey("language")
        private val KEY_LANDSCAPE        = booleanPreferencesKey("landscape")
        private val KEY_SELECTED_MARKET  = stringPreferencesKey("selected_market")
        private val KEY_GAUGE_SLOT_0     = stringPreferencesKey("gauge_slot_0")
        private val KEY_GAUGE_SLOT_1     = stringPreferencesKey("gauge_slot_1")
        private val KEY_GAUGE_SLOT_2     = stringPreferencesKey("gauge_slot_2")
        private val KEY_GAUGE_SLOT_3     = stringPreferencesKey("gauge_slot_3")
        private val KEY_FUEL_AVG_RESET   = stringPreferencesKey("fuel_avg_reset_ts")
        private val KEY_FUEL_UNIT        = stringPreferencesKey("fuel_unit")
        private val KEY_GAUGE_WIDE_0     = stringPreferencesKey("gauge_wide_0")
        private val KEY_GAUGE_WIDE_1     = stringPreferencesKey("gauge_wide_1")
        private val KEY_LS_BOTTOM_LAYOUT = stringPreferencesKey("landscape_bottom_layout")
        private val KEY_LS_WIDE_0        = stringPreferencesKey("ls_wide_0")
        private val KEY_LS_WIDE_1        = stringPreferencesKey("ls_wide_1")
        private val KEY_LS_SQ_0          = stringPreferencesKey("ls_sq_0")
        private val KEY_LS_SQ_1          = stringPreferencesKey("ls_sq_1")
        private val KEY_LS_SQ_2          = stringPreferencesKey("ls_sq_2")
        private val KEY_LS_SQ_3          = stringPreferencesKey("ls_sq_3")

        // ── New 3-row landscape system ─────────────────────────────────────────
        private val KEY_LS_TOP_0      = stringPreferencesKey("ls_top_0")
        private val KEY_LS_TOP_1      = stringPreferencesKey("ls_top_1")
        private val KEY_LS_TOP_2      = stringPreferencesKey("ls_top_2")
        private val KEY_LS_TOP_3      = stringPreferencesKey("ls_top_3")
        private val KEY_LS_MID_0      = stringPreferencesKey("ls_mid_0")
        private val KEY_LS_MID_1      = stringPreferencesKey("ls_mid_1")
        private val KEY_LS_MID_2      = stringPreferencesKey("ls_mid_2")
        private val KEY_LS_BOT_0      = stringPreferencesKey("ls_bot_0")
        private val KEY_LS_BOT_1      = stringPreferencesKey("ls_bot_1")
        private val KEY_LS_BOT_2      = stringPreferencesKey("ls_bot_2")
        private val KEY_LS_BOT_3      = stringPreferencesKey("ls_bot_3")
        private val KEY_LS_BOT_WIDE_0 = stringPreferencesKey("ls_bot_wide_0")
        private val KEY_LS_BOT_WIDE_1 = stringPreferencesKey("ls_bot_wide_1")
        private val KEY_LS_BOT_MODE   = stringPreferencesKey("ls_bot_mode")
    }

    // ── Vehicle & onboarding ──────────────────────────────────────────────────

    val isOnboardingComplete: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_DONE] ?: false
    }

    val selectedVehicle: Flow<VehicleSpec?> = dataStore.data.map { prefs ->
        val year   = prefs[KEY_SELECTED_YEAR]   ?: return@map null
        val model  = prefs[KEY_SELECTED_MODEL]  ?: return@map null
        val engine = prefs[KEY_SELECTED_ENGINE] ?: return@map null
        val market = prefs[KEY_SELECTED_MARKET]?.let { runCatching { Market.valueOf(it) }.getOrNull() } ?: Market.GLOBAL
        VehicleDatabase.findVehicle(year, model, engine, market)
    }

    suspend fun saveVehicle(vehicle: VehicleSpec) {
        dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_DONE]  = true
            prefs[KEY_SELECTED_YEAR]    = vehicle.year
            prefs[KEY_SELECTED_MODEL]   = vehicle.modelName
            prefs[KEY_SELECTED_ENGINE]  = vehicle.engineCode
            prefs[KEY_SELECTED_MARKET]  = vehicle.market.name
        }
    }

    // ── Bluetooth ─────────────────────────────────────────────────────────────

    val lastDeviceMac: Flow<String?> = dataStore.data.map { it[KEY_LAST_DEVICE_MAC] }
    val lastDeviceType: Flow<String?> = dataStore.data.map { it[KEY_LAST_DEVICE_TYPE] }

    suspend fun saveLastDevice(mac: String, type: OBDConnectionType) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_DEVICE_MAC]  = mac
            prefs[KEY_LAST_DEVICE_TYPE] = type.name
        }
    }

    // ── Appearance ────────────────────────────────────────────────────────────

    val themeMode: Flow<String> = dataStore.data.map { it[KEY_THEME] ?: "system" }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[KEY_THEME] = mode }
    }

    val landscapeEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_LANDSCAPE] ?: false }

    suspend fun setLandscapeEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_LANDSCAPE] = enabled }
    }

    // ── Units ─────────────────────────────────────────────────────────────────

    val temperatureUnit: Flow<String> = dataStore.data.map { it[KEY_TEMP_UNIT] ?: "celsius" }
    val pressureUnit: Flow<String>    = dataStore.data.map { it[KEY_PRESS_UNIT] ?: "kpa" }

    val fuelUnit: Flow<String> = dataStore.data.map { it[KEY_FUEL_UNIT] ?: "L100" }

    val displayUnits: Flow<DisplayUnits> = combine(temperatureUnit, pressureUnit, fuelUnit) { t, p, f ->
        DisplayUnits(t, p, f)
    }

    suspend fun setTemperatureUnit(unit: String) {
        dataStore.edit { it[KEY_TEMP_UNIT] = unit }
    }

    suspend fun setPressureUnit(unit: String) {
        dataStore.edit { it[KEY_PRESS_UNIT] = unit }
    }

    suspend fun setFuelUnit(unit: String) {
        dataStore.edit { it[KEY_FUEL_UNIT] = unit }
    }

    // ── Language ──────────────────────────────────────────────────────────────

    val language: Flow<String> = dataStore.data.map { it[KEY_LANGUAGE] ?: "" }

    suspend fun setLanguage(tag: String) {
        dataStore.edit { it[KEY_LANGUAGE] = tag }
    }

    // ── Service log ───────────────────────────────────────────────────────────

    val serviceLog: Flow<List<ServiceEvent>> = dataStore.data.map { prefs ->
        (prefs[KEY_SERVICE_LOG] ?: "")
            .split("\n")
            .filter { it.isNotBlank() }
            .mapNotNull { ServiceEvent.fromStorageString(it) }
            .sortedByDescending { it.dateMs }
    }

    suspend fun addServiceEvent(event: ServiceEvent) {
        dataStore.edit { prefs ->
            val existing = prefs[KEY_SERVICE_LOG] ?: ""
            val lines = existing.split("\n").filter { it.isNotBlank() }.toMutableList()
            lines.add(0, event.toStorageString())
            prefs[KEY_SERVICE_LOG] = lines.joinToString("\n")
        }
    }

    suspend fun removeServiceEvent(id: String) {
        dataStore.edit { prefs ->
            val existing = prefs[KEY_SERVICE_LOG] ?: ""
            val lines = existing.split("\n")
                .filter { it.isNotBlank() && !it.startsWith("$id|") }
            prefs[KEY_SERVICE_LOG] = lines.joinToString("\n")
        }
    }

    // ── Gauge slots ───────────────────────────────────────────────────────────

    private val defaultSlots = listOf("0105", "221017", "010C", "010D")

    val gaugeSlots: Flow<List<String>> = dataStore.data.map { prefs ->
        listOf(
            prefs[KEY_GAUGE_SLOT_0] ?: defaultSlots[0],
            prefs[KEY_GAUGE_SLOT_1] ?: defaultSlots[1],
            prefs[KEY_GAUGE_SLOT_2] ?: defaultSlots[2],
            prefs[KEY_GAUGE_SLOT_3] ?: defaultSlots[3],
        )
    }

    suspend fun setGaugeSlot(index: Int, pidCmd: String) {
        dataStore.edit { prefs ->
            when (index) {
                0 -> prefs[KEY_GAUGE_SLOT_0] = pidCmd
                1 -> prefs[KEY_GAUGE_SLOT_1] = pidCmd
                2 -> prefs[KEY_GAUGE_SLOT_2] = pidCmd
                3 -> prefs[KEY_GAUGE_SLOT_3] = pidCmd
            }
        }
    }

    // ── Wide gauge slots (portrait full-width cards) ──────────────────────────

    val wideGaugeSlots: Flow<List<String>> = dataStore.data.map { prefs ->
        listOf(
            prefs[KEY_GAUGE_WIDE_0] ?: "221018",
            prefs[KEY_GAUGE_WIDE_1] ?: "01C1",
        )
    }

    suspend fun setWideGaugeSlot(index: Int, pidCmd: String) {
        dataStore.edit { prefs ->
            when (index) {
                0 -> prefs[KEY_GAUGE_WIDE_0] = pidCmd
                1 -> prefs[KEY_GAUGE_WIDE_1] = pidCmd
            }
        }
    }

    // ── Landscape bottom layout ───────────────────────────────────────────────

    val landscapeBottomLayout: Flow<String> = dataStore.data.map { it[KEY_LS_BOTTOM_LAYOUT] ?: "wide" }

    suspend fun setLandscapeBottomLayout(layout: String) {
        dataStore.edit { it[KEY_LS_BOTTOM_LAYOUT] = layout }
    }

    // All 6 landscape bottom slots: index 0..1 = "wide" layout, 2..5 = "square" layout
    val landscapeBottomSlots: Flow<List<String>> = dataStore.data.map { prefs ->
        listOf(
            prefs[KEY_LS_WIDE_0] ?: "0110",
            prefs[KEY_LS_WIDE_1] ?: "012F",
            prefs[KEY_LS_SQ_0]   ?: "0104",
            prefs[KEY_LS_SQ_1]   ?: "010B",
            prefs[KEY_LS_SQ_2]   ?: "0106",
            prefs[KEY_LS_SQ_3]   ?: "0107",
        )
    }

    suspend fun setLandscapeSlot(index: Int, pidCmd: String) {
        dataStore.edit { prefs ->
            when (index) {
                0 -> prefs[KEY_LS_WIDE_0] = pidCmd
                1 -> prefs[KEY_LS_WIDE_1] = pidCmd
                2 -> prefs[KEY_LS_SQ_0] = pidCmd
                3 -> prefs[KEY_LS_SQ_1] = pidCmd
                4 -> prefs[KEY_LS_SQ_2] = pidCmd
                5 -> prefs[KEY_LS_SQ_3] = pidCmd
            }
        }
    }

    // ── New 3-row landscape slots ─────────────────────────────────────────────

    val lsTopSlots: Flow<List<String>> = dataStore.data.map { prefs ->
        listOf(
            prefs[KEY_LS_TOP_0] ?: "0105",    // Coolant Temp
            prefs[KEY_LS_TOP_1] ?: "221017",  // CVT Fluid Temp
            prefs[KEY_LS_TOP_2] ?: "010C",    // Engine RPM
            prefs[KEY_LS_TOP_3] ?: "010D",    // Vehicle Speed
        )
    }

    val lsMidSlots: Flow<List<String>> = dataStore.data.map { prefs ->
        listOf(
            prefs[KEY_LS_MID_0] ?: "221018",   // AWD Distribution
            prefs[KEY_LS_MID_1] ?: "FUEL_CONS", // Fuel Consumption
            prefs[KEY_LS_MID_2] ?: "010D",      // Vehicle Speed
        )
    }

    val lsBotSlots: Flow<List<String>> = dataStore.data.map { prefs ->
        listOf(
            prefs[KEY_LS_BOT_0] ?: "0104",   // Engine Load
            prefs[KEY_LS_BOT_1] ?: "010C",   // Engine RPM
            prefs[KEY_LS_BOT_2] ?: "ATRV",   // Battery Voltage
            prefs[KEY_LS_BOT_3] ?: "221017", // CVT Fluid Temp
        )
    }

    val lsBotWideSlots: Flow<List<String>> = dataStore.data.map { prefs ->
        listOf(
            prefs[KEY_LS_BOT_WIDE_0] ?: "TPMS_ALL", // TPMS All Tires
            prefs[KEY_LS_BOT_WIDE_1] ?: "221018",   // AWD Distribution
        )
    }

    val lsBotMode: Flow<String> = dataStore.data.map { it[KEY_LS_BOT_MODE] ?: "square" }

    suspend fun setLsTopSlot(index: Int, cmd: String) {
        dataStore.edit { prefs ->
            when (index) {
                0 -> prefs[KEY_LS_TOP_0] = cmd
                1 -> prefs[KEY_LS_TOP_1] = cmd
                2 -> prefs[KEY_LS_TOP_2] = cmd
                3 -> prefs[KEY_LS_TOP_3] = cmd
            }
        }
    }

    suspend fun setLsMidSlot(index: Int, cmd: String) {
        dataStore.edit { prefs ->
            when (index) {
                0 -> prefs[KEY_LS_MID_0] = cmd
                1 -> prefs[KEY_LS_MID_1] = cmd
                2 -> prefs[KEY_LS_MID_2] = cmd
            }
        }
    }

    suspend fun setLsBotSlot(index: Int, cmd: String) {
        dataStore.edit { prefs ->
            when (index) {
                0 -> prefs[KEY_LS_BOT_0] = cmd
                1 -> prefs[KEY_LS_BOT_1] = cmd
                2 -> prefs[KEY_LS_BOT_2] = cmd
                3 -> prefs[KEY_LS_BOT_3] = cmd
            }
        }
    }

    suspend fun setLsBotWideSlot(index: Int, cmd: String) {
        dataStore.edit { prefs ->
            when (index) {
                0 -> prefs[KEY_LS_BOT_WIDE_0] = cmd
                1 -> prefs[KEY_LS_BOT_WIDE_1] = cmd
            }
        }
    }

    suspend fun setLsBotMode(mode: String) {
        dataStore.edit { it[KEY_LS_BOT_MODE] = mode }
    }

    // ── Fuel avg reset timestamp ──────────────────────────────────────────────

    val fuelAvgResetTs: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_FUEL_AVG_RESET]?.toLongOrNull() ?: 0L
    }

    suspend fun resetFuelAvg() {
        dataStore.edit { it[KEY_FUEL_AVG_RESET] = System.currentTimeMillis().toString() }
    }
}
