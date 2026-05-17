package com.subaru.servicetool.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subaru.servicetool.data.model.Market
import com.subaru.servicetool.data.model.VehicleDatabase
import com.subaru.servicetool.data.model.VehicleSpec
import com.subaru.servicetool.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {

    val availableYears: List<Int> = VehicleDatabase.getAvailableYears()

    private val _selectedYear   = MutableStateFlow<Int?>(null)
    private val _selectedMarket = MutableStateFlow<Market?>(null)
    private val _selectedModel  = MutableStateFlow<String?>(null)
    private val _selectedSpec   = MutableStateFlow<VehicleSpec?>(null)

    val selectedYear:   StateFlow<Int?>         = _selectedYear.asStateFlow()
    val selectedMarket: StateFlow<Market?>      = _selectedMarket.asStateFlow()
    val selectedModel:  StateFlow<String?>      = _selectedModel.asStateFlow()
    val selectedSpec:   StateFlow<VehicleSpec?> = _selectedSpec.asStateFlow()

    val availableMarkets: StateFlow<List<Market>> = _selectedYear
        .map { year -> year?.let { VehicleDatabase.getMarketsForYear(it) } ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableModels: StateFlow<List<String>> = _selectedYear
        .combine(_selectedMarket) { year, market ->
            if (year != null && market != null)
                VehicleDatabase.getModelsForYearAndMarket(year, market)
            else emptyList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val availableSpecs: StateFlow<List<VehicleSpec>> = _selectedYear
        .combine(_selectedMarket) { y, m -> y to m }
        .combine(_selectedModel) { (year, market), model ->
            if (year != null && market != null && model != null)
                VehicleDatabase.getSpecsForYearMarketAndModel(year, market, model)
            else emptyList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectYear(year: Int) {
        _selectedYear.value   = year
        _selectedMarket.value = null
        _selectedModel.value  = null
        _selectedSpec.value   = null
    }

    fun selectMarket(market: Market) {
        _selectedMarket.value = market
        _selectedModel.value  = null
        _selectedSpec.value   = null
    }

    fun selectModel(model: String) {
        _selectedModel.value = model
        _selectedSpec.value  = null
    }

    fun selectSpec(spec: VehicleSpec) {
        _selectedSpec.value = spec
    }

    fun saveAndContinue(onComplete: () -> Unit = {}) {
        val vehicle = _selectedSpec.value ?: return
        viewModelScope.launch {
            userPreferences.saveVehicle(vehicle)
            onComplete()
        }
    }
}
