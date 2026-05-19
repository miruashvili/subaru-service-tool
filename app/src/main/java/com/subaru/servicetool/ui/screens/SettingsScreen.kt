package com.subaru.servicetool.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.subaru.servicetool.BuildConfig
import com.subaru.servicetool.R
import com.subaru.servicetool.data.model.VehicleSpec
import com.subaru.servicetool.ui.theme.DarkPrimary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    paddingValues: PaddingValues = PaddingValues(),
    onChangeVehicle: () -> Unit = {},
    onOpenBluetooth: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val vehicle       by viewModel.selectedVehicle.collectAsState()
    val themeMode     by viewModel.themeMode.collectAsState()
    val landscape     by viewModel.landscapeEnabled.collectAsState()
    val tempUnit      by viewModel.temperatureUnit.collectAsState()
    val pressUnit     by viewModel.pressureUnit.collectAsState()
    val fuelUnit      by viewModel.fuelUnit.collectAsState()
    val lsBottomLayout by viewModel.landscapeBottomLayout.collectAsState()
    val lsBotMode      by viewModel.lsBotMode.collectAsState()
    val language      by viewModel.language.collectAsState()
    val context       = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        // ── Vehicle ───────────────────────────────────────────────────────────
        SectionLabel(stringResource(R.string.settings_vehicle_section))
        SettingsCard {
            if (vehicle != null) VehicleInfoRow(vehicle!!)
            else Text(
                stringResource(R.string.settings_vehicle_no),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onChangeVehicle,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(0.15f),
                    contentColor   = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_change_vehicle), style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Bluetooth ─────────────────────────────────────────────────────────
        SectionLabel(stringResource(R.string.settings_bluetooth_section))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenBluetooth,
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bluetooth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_obd_adapter), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.settings_obd_adapter_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                }
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Appearance ────────────────────────────────────────────────────────
        SectionLabel(stringResource(R.string.settings_appearance_section))
        SettingsCard {
            SettingsRowLabel(stringResource(R.string.settings_theme))
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("dark" to stringResource(R.string.settings_dark),
                       "light" to stringResource(R.string.settings_light),
                       "system" to stringResource(R.string.settings_system)).forEachIndexed { i, (key, label) ->
                    SegmentedButton(
                        selected = themeMode == key,
                        onClick  = { viewModel.setThemeMode(key) },
                        shape    = SegmentedButtonDefaults.itemShape(i, 3),
                        label    = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_landscape), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.settings_landscape_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }
                Switch(checked = landscape, onCheckedChange = viewModel::setLandscapeEnabled)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
            Spacer(Modifier.height(12.dp))
            SettingsRowLabel(stringResource(R.string.settings_landscape_bottom))
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("square" to stringResource(R.string.settings_landscape_bottom_square),
                       "wide"   to stringResource(R.string.settings_landscape_bottom_wide)).forEachIndexed { i, (key, label) ->
                    SegmentedButton(
                        selected = lsBotMode == key,
                        onClick  = { viewModel.setLsBotMode(key) },
                        shape    = SegmentedButtonDefaults.itemShape(i, 2),
                        label    = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Units ─────────────────────────────────────────────────────────────
        SectionLabel(stringResource(R.string.settings_units_section))
        SettingsCard {
            SettingsRowLabel(stringResource(R.string.settings_temp_unit))
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("celsius" to stringResource(R.string.settings_celsius),
                       "fahrenheit" to stringResource(R.string.settings_fahrenheit)).forEachIndexed { i, (key, label) ->
                    SegmentedButton(
                        selected = tempUnit == key,
                        onClick  = { viewModel.setTemperatureUnit(key) },
                        shape    = SegmentedButtonDefaults.itemShape(i, 2),
                        label    = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
            Spacer(Modifier.height(14.dp))
            SettingsRowLabel(stringResource(R.string.settings_press_unit))
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("kpa" to stringResource(R.string.settings_kpa),
                       "bar" to stringResource(R.string.settings_bar),
                       "psi" to stringResource(R.string.settings_psi)).forEachIndexed { i, (key, label) ->
                    SegmentedButton(
                        selected = pressUnit == key,
                        onClick  = { viewModel.setPressureUnit(key) },
                        shape    = SegmentedButtonDefaults.itemShape(i, 3),
                        label    = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.15f))
            Spacer(Modifier.height(14.dp))
            SettingsRowLabel(stringResource(R.string.settings_fuel_unit))
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("L100" to stringResource(R.string.fuel_l100km),
                       "MPG"  to stringResource(R.string.fuel_mpg),
                       "KML"  to stringResource(R.string.fuel_kml)).forEachIndexed { i, (key, label) ->
                    SegmentedButton(
                        selected = fuelUnit == key,
                        onClick  = { viewModel.setFuelUnit(key) },
                        shape    = SegmentedButtonDefaults.itemShape(i, 3),
                        label    = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Language ──────────────────────────────────────────────────────────
        SectionLabel(stringResource(R.string.settings_language_section))
        SettingsCard {
            val languages = listOf(
                "" to "English", "es" to "Español", "fr" to "Français",
                "de" to "Deutsch", "ka" to "ქართული", "ru" to "Русский",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                languages.forEach { (tag, label) ->
                    val selected = language == tag
                    FilterChip(
                        selected = selected,
                        onClick  = {
                            viewModel.setLanguage(tag)
                            (context as? Activity)?.recreate()
                        },
                        label    = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor   = DarkPrimary.copy(0.18f),
                            selectedLabelColor       = DarkPrimary,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── App info ──────────────────────────────────────────────────────────
        SectionLabel(stringResource(R.string.settings_app_section))
        SettingsCard {
            InfoRow(stringResource(R.string.settings_version), BuildConfig.VERSION_NAME)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(0.2f))
            InfoRow(stringResource(R.string.settings_obd_protocol), vehicle?.obdProtocol ?: "—")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(0.2f))
            InfoRow(stringResource(R.string.settings_ssm), when (vehicle?.ssmSupported) {
                true  -> stringResource(R.string.settings_ssm_enabled)
                false -> stringResource(R.string.settings_ssm_not_supported)
                null  -> "—"
            })
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SettingsRowLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.55f), fontWeight = FontWeight.SemiBold)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
    }
}

@Composable
private fun VehicleInfoRow(vehicle: VehicleSpec) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.DirectionsCar, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("${vehicle.year} Subaru ${vehicle.modelName}", style = MaterialTheme.typography.titleMedium)
            Text("${vehicle.engineDisplayName} · ${vehicle.engineCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            if (vehicle.cvtType != null) {
                Text("CVT: ${vehicle.cvtType}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(0.8f))
            }
        }
    }
}
