package com.subaru.servicetool.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.subaru.servicetool.R
import com.subaru.servicetool.data.bluetooth.BluetoothConnectionState
import com.subaru.servicetool.data.obd.ObdPid
import com.subaru.servicetool.data.obd.ObdPids
import com.subaru.servicetool.data.obd.PidGroup
import kotlin.math.min
import com.subaru.servicetool.ui.sensors.SensorGroup
import com.subaru.servicetool.ui.sensors.SensorItem
import com.subaru.servicetool.ui.sensors.SensorsViewModel
import com.subaru.servicetool.ui.theme.DarkError
import com.subaru.servicetool.ui.theme.DarkPrimary
import com.subaru.servicetool.ui.theme.DarkSuccess
import com.subaru.servicetool.ui.theme.DarkWarning

@Composable
fun SensorsScreen(
    paddingValues: PaddingValues = PaddingValues(),
    viewModel: SensorsViewModel = hiltViewModel(),
) {
    val groups by viewModel.sensorGroups.collectAsState()
    val btState by viewModel.connectionState.collectAsState()
    val dtcCount by viewModel.dtcCount.collectAsState()
    val connected = btState is BluetoothConnectionState.Connected

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────
        item {
            Text("Sensors", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
        }

        // ── Connection / DTC status ───────────────────────────────────────
        item {
            StatusBar(btState, dtcCount)
            Spacer(Modifier.height(4.dp))
        }

        if (!connected) {
            val isConnecting = btState is BluetoothConnectionState.Connecting ||
                    btState is BluetoothConnectionState.Reconnecting
            if (isConnecting) {
                // Shimmer skeleton while connecting
                items(4) { SkeletonGroupCard() }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Bluetooth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.25f),
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Connect an OBD adapter to see live data",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            )
                        }
                    }
                }
            }
            return@LazyColumn
        }

        // ── TPMS widget ───────────────────────────────────────────────────
        item(key = "tpms") {
            val tpms by viewModel.tpmsValues.collectAsState()
            TpmsWidget(tpms)
            Spacer(Modifier.height(4.dp))
        }

        // ── Sensor groups ─────────────────────────────────────────────────
        groups.forEach { group ->
            item(key = group.group.name) {
                SectionHeader(group.label, group.group)
            }
            item(key = "${group.group.name}_card") {
                SensorGroupCard(group)
                Spacer(Modifier.height(4.dp))
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ── Status bar ────────────────────────────────────────────────────────────────

@Composable
private fun StatusBar(btState: BluetoothConnectionState, dtcCount: Int) {
    val (bg, label, icon, tint) = when (btState) {
        is BluetoothConnectionState.Connected ->
            if (dtcCount == 0)
                Quad(DarkSuccess.copy(0.12f), "Connected · No fault codes", Icons.Filled.CheckCircle, DarkSuccess)
            else
                Quad(DarkError.copy(0.12f), "Connected · $dtcCount fault code(s) detected", Icons.Filled.ErrorOutline, DarkError)
        is BluetoothConnectionState.Connecting, is BluetoothConnectionState.Reconnecting ->
            Quad(DarkWarning.copy(0.12f), "Connecting…", Icons.Filled.Bluetooth, DarkWarning)
        is BluetoothConnectionState.Error ->
            Quad(DarkError.copy(0.12f), "Error: ${(btState as BluetoothConnectionState.Error).message}", Icons.Filled.ErrorOutline, DarkError)
        else ->
            Quad(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f), "Not connected", Icons.Filled.Bluetooth, MaterialTheme.colorScheme.onSurface.copy(0.4f))
    }

    Surface(color = bg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(label: String, group: PidGroup) {
    Row(
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = group.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = group.localizedLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PidGroup.localizedLabel(): String = when (this) {
    PidGroup.ENGINE       -> stringResource(R.string.group_engine)
    PidGroup.TEMPERATURE  -> stringResource(R.string.group_temperatures)
    PidGroup.FUEL         -> stringResource(R.string.group_fuel)
    PidGroup.MISC         -> stringResource(R.string.group_misc)
    PidGroup.TRANSMISSION -> stringResource(R.string.group_transmission)
}

// ── Sensor group card ─────────────────────────────────────────────────────────

@Composable
private fun SensorGroupCard(group: SensorGroup) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            group.items.forEachIndexed { index, item ->
                SensorRow(item)
                if (index < group.items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(0.12f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorRow(item: SensorItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.pid.localizedName(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = item.displayUnit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
            )
        }

        AnimatedContent(
            targetState = item.displayValue,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = item.pid.cmd,
        ) { value ->
            val isLive = value != "--"
            val highlight = isLive && item.pid.cmd == "010C" && (item.value ?: 0f) > 4000f
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = if (value.length > 6) 14.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = when {
                    highlight -> DarkWarning
                    isLive    -> DarkPrimary
                    else      -> MaterialTheme.colorScheme.onSurface.copy(0.3f)
                },
            )
        }
    }
}

// ── Localization helpers ──────────────────────────────────────────────────────

@Composable
private fun ObdPid.localizedName(): String = when (cmd) {
    ObdPids.RPM.cmd          -> stringResource(R.string.sensor_rpm)
    ObdPids.SPEED.cmd        -> stringResource(R.string.sensor_speed)
    ObdPids.COOLANT_TEMP.cmd -> stringResource(R.string.sensor_coolant_temp)
    ObdPids.THROTTLE.cmd     -> stringResource(R.string.sensor_throttle)
    ObdPids.INTAKE_TEMP.cmd  -> stringResource(R.string.sensor_intake_temp)
    ObdPids.VOLTAGE.cmd      -> stringResource(R.string.sensor_battery)
    ObdPids.ENGINE_LOAD.cmd  -> stringResource(R.string.sensor_engine_load)
    ObdPids.MAP.cmd          -> stringResource(R.string.sensor_map)
    ObdPids.MAF.cmd          -> stringResource(R.string.sensor_maf)
    ObdPids.FUEL_LEVEL.cmd   -> stringResource(R.string.sensor_fuel_level)
    ObdPids.FUEL_TRIM_ST.cmd -> stringResource(R.string.sensor_fuel_trim_st)
    ObdPids.FUEL_TRIM_LT.cmd -> stringResource(R.string.sensor_fuel_trim_lt)
    ObdPids.REL_THROTTLE.cmd -> stringResource(R.string.sensor_rel_throttle)
    ObdPids.ABS_LOAD.cmd     -> stringResource(R.string.sensor_abs_load)
    ObdPids.RUN_TIME.cmd     -> stringResource(R.string.sensor_run_time)
    ObdPids.OIL_TEMP.cmd     -> stringResource(R.string.sensor_oil_temp)
    else                     -> name
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private val PidGroup.icon: ImageVector
    get() = when (this) {
        PidGroup.ENGINE       -> Icons.Filled.Speed
        PidGroup.TEMPERATURE  -> Icons.Filled.DeviceThermostat
        PidGroup.FUEL         -> Icons.Filled.LocalGasStation
        PidGroup.MISC         -> Icons.Filled.BatteryFull
        PidGroup.TRANSMISSION -> Icons.Filled.DirectionsCar
    }

// ── Shimmer skeleton ──────────────────────────────────────────────────────────

@Composable
private fun SkeletonGroupCard() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue  = 0.55f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmer_alpha",
    )
    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header placeholder
            Box(
                modifier = Modifier
                    .height(10.dp)
                    .fillMaxWidth(0.3f)
                    .background(shimmerColor, RoundedCornerShape(4.dp)),
            )
            repeat(3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.height(12.dp).fillMaxWidth(0.45f).background(shimmerColor, RoundedCornerShape(4.dp)))
                    Box(modifier = Modifier.height(16.dp).width(56.dp).background(shimmerColor, RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}

// ── TPMS Widget ───────────────────────────────────────────────────────────────

@Composable
private fun TpmsWidget(tpms: Map<String, Float>) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Speed, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("TIRE PRESSURE", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))

            // Car silhouette with 4 tire positions
            Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val surfaceColor = MaterialTheme.colorScheme.onSurface.copy(0.15f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    // Simple car outline (top-down)
                    val carLeft  = w * 0.3f; val carRight = w * 0.7f
                    val carTop   = h * 0.15f; val carBot   = h * 0.85f
                    val rw = (carRight - carLeft) * 0.18f
                    val rh = (carBot - carTop) * 0.15f
                    // Car body
                    drawRoundRect(
                        color = surfaceColor,
                        topLeft = Offset(carLeft, carTop),
                        size = Size(carRight - carLeft, carBot - carTop),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                }
                // 4 tire badges
                TireBadge("FL", tpms["FL"] ?: 0f, Modifier.align(Alignment.TopStart))
                TireBadge("FR", tpms["FR"] ?: 0f, Modifier.align(Alignment.TopEnd))
                TireBadge("RL", tpms["RL"] ?: 0f, Modifier.align(Alignment.BottomStart))
                TireBadge("RR", tpms["RR"] ?: 0f, Modifier.align(Alignment.BottomEnd))
            }
            if (tpms.values.all { it == 0f }) {
                Spacer(Modifier.height(6.dp))
                Text("TPMS data unavailable (not supported on all vehicles)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        }
    }
}

@Composable
private fun TireBadge(label: String, kpa: Float, modifier: Modifier = Modifier) {
    val bar = kpa / 100f
    val color = when {
        kpa == 0f    -> MaterialTheme.colorScheme.onSurface.copy(0.3f)
        bar < 1.5f   -> DarkWarning
        bar < 1.0f   -> DarkError
        bar > 3.5f   -> Color(0xFFFF9800)
        else         -> DarkSuccess
    }
    Surface(
        color = color.copy(0.15f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.padding(4.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = color)
            Text(
                if (kpa == 0f) "--" else "%.1f".format(bar),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = color,
            )
            if (kpa != 0f) {
                Text("bar", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = color.copy(0.7f))
            }
        }
    }
}

/** Tiny helper to keep the 4-element destructuring in StatusBar clean. */
private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private operator fun <A, B, C, D> Quad<A, B, C, D>.component1() = a
private operator fun <A, B, C, D> Quad<A, B, C, D>.component2() = b
private operator fun <A, B, C, D> Quad<A, B, C, D>.component3() = c
private operator fun <A, B, C, D> Quad<A, B, C, D>.component4() = d
