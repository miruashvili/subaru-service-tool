package com.subaru.servicetool.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subaru.servicetool.data.model.VehicleSpec
import com.subaru.servicetool.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val selectedVehicle: StateFlow<VehicleSpec?> = userPreferences.selectedVehicle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val themeMode: StateFlow<String> = userPreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")

    val landscapeEnabled: StateFlow<Boolean> = userPreferences.landscapeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val temperatureUnit: StateFlow<String> = userPreferences.temperatureUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "celsius")

    val pressureUnit: StateFlow<String> = userPreferences.pressureUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "kpa")

    val fuelUnit: StateFlow<String> = userPreferences.fuelUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "L100")

    val landscapeBottomLayout: StateFlow<String> = userPreferences.landscapeBottomLayout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "wide")

    val lsBotMode: StateFlow<String> = userPreferences.lsBotMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "square")

    val language: StateFlow<String> = userPreferences.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setThemeMode(mode: String) {
        viewModelScope.launch { userPreferences.setThemeMode(mode) }
    }

    fun setLandscapeEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setLandscapeEnabled(enabled) }
    }

    fun setTemperatureUnit(unit: String) {
        viewModelScope.launch { userPreferences.setTemperatureUnit(unit) }
    }

    fun setPressureUnit(unit: String) {
        viewModelScope.launch { userPreferences.setPressureUnit(unit) }
    }

    fun setFuelUnit(unit: String) {
        viewModelScope.launch { userPreferences.setFuelUnit(unit) }
    }

    fun setLandscapeBottomLayout(layout: String) {
        viewModelScope.launch { userPreferences.setLandscapeBottomLayout(layout) }
    }

    fun setLsBotMode(mode: String) {
        viewModelScope.launch { userPreferences.setLsBotMode(mode) }
    }

    fun setLanguage(tag: String) {
        // Write to SharedPreferences synchronously so attachBaseContext picks it up after recreate
        appContext.getSharedPreferences("locale_pref", Context.MODE_PRIVATE)
            .edit()
            .putString("language", tag)
            .commit()
        viewModelScope.launch { userPreferences.setLanguage(tag) }
    }
}
