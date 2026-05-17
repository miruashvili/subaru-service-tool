package com.subaru.servicetool.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.subaru.servicetool.data.model.Market
import com.subaru.servicetool.data.model.VehicleSpec
import com.subaru.servicetool.data.model.displayName
import com.subaru.servicetool.ui.theme.DarkPrimary
import com.subaru.servicetool.ui.theme.DarkSuccess
import com.subaru.servicetool.ui.theme.DarkWarning
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.Canvas as ComposeCanvas

@Composable
fun OnboardingScreen(
    isChangingVehicle: Boolean = false,
    onComplete: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val selectedYear   by viewModel.selectedYear.collectAsState()
    val selectedMarket by viewModel.selectedMarket.collectAsState()
    val selectedModel  by viewModel.selectedModel.collectAsState()
    val selectedSpec   by viewModel.selectedSpec.collectAsState()
    val markets        by viewModel.availableMarkets.collectAsState()
    val models         by viewModel.availableModels.collectAsState()
    val specs          by viewModel.availableSpecs.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Subaru star-cluster logo ──────────────────────────────────
            SubaruStarLogo(modifier = Modifier.size(140.dp))

            Spacer(Modifier.height(24.dp))

            Text(
                text = if (isChangingVehicle) "Change Vehicle" else "Welcome to",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!isChangingVehicle) {
                Text(
                    text = "Subaru Service Tool",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Select your vehicle to get started",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            } else {
                Text(
                    text = "Pick a different vehicle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            Spacer(Modifier.height(36.dp))

            // ── Year ──────────────────────────────────────────────────────
            VehicleDropdown(
                label = "Year",
                selected = selectedYear,
                options = viewModel.availableYears,
                optionLabel = { it.toString() },
                onSelected = viewModel::selectYear,
                enabled = true,
            )

            Spacer(Modifier.height(14.dp))

            // ── Market ────────────────────────────────────────────────────
            VehicleDropdown(
                label = "Market",
                selected = selectedMarket,
                options = markets,
                optionLabel = { it.displayName },
                onSelected = viewModel::selectMarket,
                enabled = selectedYear != null,
                placeholder = if (selectedYear == null) "Select a year first" else "Select market",
            )

            Spacer(Modifier.height(14.dp))

            // ── Model ─────────────────────────────────────────────────────
            VehicleDropdown(
                label = "Model",
                selected = selectedModel,
                options = models,
                optionLabel = { it },
                onSelected = viewModel::selectModel,
                enabled = selectedMarket != null,
                placeholder = if (selectedMarket == null) "Select a market first" else "Select model",
            )

            Spacer(Modifier.height(14.dp))

            // ── Engine variant ────────────────────────────────────────────
            VehicleDropdown(
                label = "Engine variant",
                selected = selectedSpec,
                options = specs,
                optionLabel = { "${it.engineDisplayName} (${it.engineCode})" },
                onSelected = viewModel::selectSpec,
                enabled = selectedModel != null,
                placeholder = if (selectedModel == null) "Select a model first" else "Select engine",
            )

            // Market badge
            selectedMarket?.let { market ->
                val marketColor = when (market) {
                    Market.GLOBAL -> DarkPrimary
                    Market.JDM    -> DarkWarning
                    Market.EU     -> DarkSuccess
                }
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = marketColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = market.displayName,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = marketColor,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Protocol badge ────────────────────────────────────────────
            AnimatedVisibility(
                visible = selectedSpec != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                selectedSpec?.let { spec ->
                    Surface(
                        color = DarkSuccess.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = DarkSuccess,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "OBD Protocol detected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DarkSuccess,
                                )
                                Text(
                                    spec.obdProtocol,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                )
                                if (!spec.ssmSupported) {
                                    Text(
                                        "SSM not supported on this model",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Continue / Save button ────────────────────────────────────
            Button(
                onClick = { viewModel.saveAndContinue(onComplete) },
                enabled = selectedSpec != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = if (isChangingVehicle) "Save Vehicle" else "Continue",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// ── Reusable dropdown ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> VehicleDropdown(
    label: String,
    selected: T?,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    enabled: Boolean = true,
    placeholder: String = "Select $label",
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected?.let { optionLabel(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            enabled = enabled,
            singleLine = true,
        )
        if (options.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

// ── Subaru Pleiades star-cluster logo (Canvas) ────────────────────────────────

@Composable
private fun SubaruStarLogo(modifier: Modifier = Modifier) {
    val primaryBlue = MaterialTheme.colorScheme.primary
    val gold = Color(0xFFE8B84B)

    ComposeCanvas(modifier = modifier) {
        val s = minOf(size.width, size.height)

        fun DrawScope.star(cx: Float, cy: Float, r: Float, color: Color) {
            val inner = r * 0.42f
            val path = Path()
            for (i in 0 until 10) {
                val radius = if (i % 2 == 0) r else inner
                val angle = (PI * i / 5 - PI / 2).toFloat()
                val x = cx + radius * cos(angle)
                val y = cy + radius * sin(angle)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color)
        }

        // Two large blue stars (Fuji Heavy Industries group)
        star(s * 0.65f, s * 0.30f, s * 0.19f, primaryBlue)
        star(s * 0.50f, s * 0.55f, s * 0.15f, primaryBlue)

        // Four small gold stars (merged companies)
        star(s * 0.22f, s * 0.22f, s * 0.10f, gold)
        star(s * 0.36f, s * 0.43f, s * 0.09f, gold)
        star(s * 0.17f, s * 0.57f, s * 0.09f, gold)
        star(s * 0.31f, s * 0.70f, s * 0.08f, gold)
    }
}
