package com.subaru.servicetool.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.subaru.servicetool.ui.theme.DarkError
import com.subaru.servicetool.ui.theme.DarkPrimary
import com.subaru.servicetool.ui.theme.DarkSuccess
import com.subaru.servicetool.ui.theme.DarkWarning

@Composable
fun DashboardScreen(
    paddingValues: PaddingValues = PaddingValues(),
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Text(
            text = "Dashboard",
            style = MaterialTheme.typography.headlineMedium,
        )

        // ── Vehicle summary card ──────────────────────────────────────────
        VehicleCard(state)

        // ── OBD connection card ───────────────────────────────────────────
        ObdConnectionCard(
            state = state,
            onConnect = viewModel::connect,
            onDisconnect = viewModel::disconnect,
        )

        // ── Metrics grid ──────────────────────────────────────────────────
        MetricsGrid(metrics = state.metrics)

        // ── DTC row ───────────────────────────────────────────────────────
        DtcRow(dtcCount = state.dtcCount, connected = state.connectionState == ObdConnectionState.CONNECTED)

        Spacer(Modifier.height(8.dp))
    }
}

// ── Vehicle summary card ──────────────────────────────────────────────────────

@Composable
private fun VehicleCard(state: DashboardUiState) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.vehicle == null) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f), modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("No vehicle selected — go to Settings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
        } else {
            val v = state.vehicle
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(listOf(DarkPrimary, DarkPrimary.copy(0.6f)))
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.DirectionsCar, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${v.year} Subaru ${v.modelName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${v.engineDisplayName} · ${v.engineCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                    )
                }
                if (v.isTurbo) {
                    Surface(
                        color = DarkPrimary.copy(0.15f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text("TURBO",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

// ── OBD connection card ───────────────────────────────────────────────────────

@Composable
private fun ObdConnectionCard(
    state: DashboardUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val cs = state.connectionState
    val (cardColor, dotColor, label) = when (cs) {
        ObdConnectionState.DISCONNECTED -> Triple(MaterialTheme.colorScheme.surface, DarkError.copy(0.7f), "OBD Disconnected")
        ObdConnectionState.CONNECTING   -> Triple(MaterialTheme.colorScheme.surface, DarkWarning, "Connecting…")
        ObdConnectionState.CONNECTED    -> Triple(MaterialTheme.colorScheme.surface, DarkSuccess, "OBD Connected")
        ObdConnectionState.ERROR        -> Triple(MaterialTheme.colorScheme.surface, DarkError, "Connection Failed")
    }

    Surface(
        color = cardColor,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Animated status dot
            if (cs == ObdConnectionState.CONNECTING) {
                val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
                    initialValue = 0.4f, targetValue = 1f, label = "alpha",
                    animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = pulse))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))

            if (cs == ObdConnectionState.CONNECTING) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (cs == ObdConnectionState.CONNECTED) {
                OutlinedButton(
                    onClick = onDisconnect,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Disconnect", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Button(
                    onClick = onConnect,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary),
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Connect", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ── Metrics grid ──────────────────────────────────────────────────────────────

@Composable
private fun MetricsGrid(metrics: List<LiveMetric>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            // Fixed height: 2 rows × ~110dp each
            .height(232.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(metrics) { metric ->
            MetricCard(metric)
        }
    }
}

@Composable
private fun MetricCard(metric: LiveMetric) {
    val accentColor = if (metric.highlight) DarkWarning else MaterialTheme.colorScheme.primary
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = metricIcon(metric.iconRes),
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(6.dp))
            AnimatedContent(
                targetState = metric.value,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = metric.id,
            ) { v ->
                Text(
                    text = v,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (v.length > 4) 14.sp else 18.sp,
                    ),
                    color = accentColor,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = metric.unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

private fun metricIcon(icon: MetricIcon): ImageVector = when (icon) {
    MetricIcon.RPM      -> Icons.Filled.Speed
    MetricIcon.SPEED    -> Icons.Filled.DirectionsCar
    MetricIcon.TEMP     -> Icons.Filled.DeviceThermostat
    MetricIcon.THROTTLE -> Icons.Filled.Tune
    MetricIcon.VOLTAGE  -> Icons.Filled.BatteryFull
    MetricIcon.INTAKE   -> Icons.Filled.Compress
}

// ── DTC fault-code row ────────────────────────────────────────────────────────

@Composable
private fun DtcRow(dtcCount: Int, connected: Boolean) {
    Surface(
        color = if (dtcCount == 0) DarkSuccess.copy(0.10f) else DarkError.copy(0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (dtcCount == 0) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = if (dtcCount == 0) DarkSuccess else DarkError,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = if (!connected) "Connect OBD to scan for fault codes"
                           else if (dtcCount == 0) "No fault codes detected"
                           else "$dtcCount fault code(s) detected",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (connected && dtcCount == 0) {
                    Text(
                        "System nominal",
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkSuccess.copy(0.8f),
                    )
                }
            }
        }
    }
}
