package com.subaru.servicetool.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.subaru.servicetool.data.model.VehicleDatabase
import com.subaru.servicetool.data.model.VehicleSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val KEY_ONBOARDING_DONE  = booleanPreferencesKey("onboarding_complete")
        private val KEY_SELECTED_YEAR    = intPreferencesKey("selected_year")
        private val KEY_SELECTED_MODEL   = stringPreferencesKey("selected_model")
        private val KEY_SELECTED_ENGINE  = stringPreferencesKey("selected_engine_code")
    }

    val isOnboardingComplete: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_DONE] ?: false
    }

    val selectedVehicle: Flow<VehicleSpec?> = dataStore.data.map { prefs ->
        val year   = prefs[KEY_SELECTED_YEAR]   ?: return@map null
        val model  = prefs[KEY_SELECTED_MODEL]  ?: return@map null
        val engine = prefs[KEY_SELECTED_ENGINE] ?: return@map null
        VehicleDatabase.findVehicle(year, model, engine)
    }

    suspend fun saveVehicle(vehicle: VehicleSpec) {
        dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_DONE] = true
            prefs[KEY_SELECTED_YEAR]   = vehicle.year
            prefs[KEY_SELECTED_MODEL]  = vehicle.modelName
            prefs[KEY_SELECTED_ENGINE] = vehicle.engineCode
        }
    }
}
