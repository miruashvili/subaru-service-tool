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

    val displayUnits: Flow<DisplayUnits> = combine(temperatureUnit, pressureUnit) { t, p ->
        DisplayUnits(t, p)
    }

    suspend fun setTemperatureUnit(unit: String) {
        dataStore.edit { it[KEY_TEMP_UNIT] = unit }
    }

    suspend fun setPressureUnit(unit: String) {
        dataStore.edit { it[KEY_PRESS_UNIT] = unit }
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

    private val defaultSlots = listOf("010C", "010D", "0105", "0111")

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

    // ── Fuel avg reset timestamp ──────────────────────────────────────────────

    val fuelAvgResetTs: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_FUEL_AVG_RESET]?.toLongOrNull() ?: 0L
    }

    suspend fun resetFuelAvg() {
        dataStore.edit { it[KEY_FUEL_AVG_RESET] = System.currentTimeMillis().toString() }
    }
}
