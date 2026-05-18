package com.subaru.servicetool.ui.dashboard

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.subaru.servicetool.data.alert.TempAlertLevel
import com.subaru.servicetool.data.model.Market
import com.subaru.servicetool.data.model.displayName
import com.subaru.servicetool.data.obd.ObdPid
import com.subaru.servicetool.data.obd.ObdPids
import com.subaru.servicetool.ui.theme.DarkError
import com.subaru.servicetool.ui.theme.DarkPrimary
import com.subaru.servicetool.ui.theme.DarkSuccess
import com.subaru.servicetool.ui.theme.DarkWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    paddingValues: PaddingValues = PaddingValues(),
    onNavigateToBluetooth: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state       by viewModel.uiState.collectAsState()
    val alertLevel  by viewModel.showAlertBanner.collectAsState()
    val connLost    by viewModel.connectionLostVisible.collectAsState()
    val editingSlot by viewModel.editingSlot.collectAsState()
    val gaugeSlots  by viewModel.currentGaugeSlots.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(Unit) {
        viewModel.navigateToBluetooth.collect { onNavigateToBluetooth() }
    }

    // ── Gauge editor bottom sheet ─────────────────────────────────────────────
    if (editingSlot != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::closeGaugeEditor,
            sheetState = sheetState,
        ) {
            GaugeSelectorSheet(
                pids       = viewModel.configurablePids,
                currentCmd = gaugeSlots.getOrNull(editingSlot!!) ?: "",
                onSelect   = { pid -> viewModel.setGaugeSlot(editingSlot!!, pid.cmd) },
                onDismiss  = viewModel::closeGaugeEditor,
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        if (isLandscape) {
            // ── Landscape: no scroll, side-by-side ────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DashboardTopBar(
                    connectionState = state.connectionState,
                    ambientTemp     = state.ambientTemp,
                    connectedName   = state.connectedDeviceName,
                )
                AlertBanner(alertLevel, onDismiss = viewModel::dismissAlert)
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GaugeGrid(
                        metrics    = state.metrics,
                        onEditSlot = viewModel::openGaugeEditor,
                        modifier   = Modifier.weight(1f),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.connectionState == ObdConnectionState.CONNECTED) {
                            FuelConsumptionCard(
                                fuel    = state.fuelConsumption,
                                onReset = viewModel::resetFuelAvg,
                            )
                        }
                        DtcRow(dtcCount = state.dtcCount, connected = state.connectionState == ObdConnectionState.CONNECTED)
                    }
                }
            }
        } else {
            // ── Portrait: scrollable column ───────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashboardTopBar(
                    connectionState = state.connectionState,
                    ambientTemp     = state.ambientTemp,
                    connectedName   = state.connectedDeviceName,
                )
                AlertBanner(alertLevel, onDismiss = viewModel::dismissAlert)
                GaugeGrid(
                    metrics    = state.metrics,
                    onEditSlot = viewModel::openGaugeEditor,
                )
                if (state.connectionState == ObdConnectionState.CONNECTED) {
                    FuelConsumptionCard(
                        fuel    = state.fuelConsumption,
                        onReset = viewModel::resetFuelAvg,
                    )
                }
                DtcRow(dtcCount = state.dtcCount, connected = state.connectionState == ObdConnectionState.CONNECTED)
                Spacer(Modifier.height(8.dp))
            }
        }

        // ── Connection-lost snackbar ──────────────────────────────────────
        AnimatedVisibility(
            visible = connLost,
            enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
            exit  = slideOutVertically(tween(250)) { it } + fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = DarkWarning, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Connection lost — retrying…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface)
                }
            }
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@Composable
private fun DashboardTopBar(
    connectionState: ObdConnectionState,
    ambientTemp: Float?,
    connectedName: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
        )

        // Ambient temp chip
        ambientTemp?.let { temp ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(0.8f),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.DeviceThermostat, contentDescription = null,
                        tint = when {
                            temp > 35f -> DarkError
                            temp > 25f -> DarkWarning
                            temp < 5f  -> DarkPrimary
                            else       -> DarkSuccess
                        },
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "%.0f°C".format(temp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        // Connection status dot
        val dotColor = when (connectionState) {
            ObdConnectionState.CONNECTED   -> DarkSuccess
            ObdConnectionState.CONNECTING  -> DarkWarning
            ObdConnectionState.ERROR       -> DarkError
            ObdConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurface.copy(0.3f)
        }

        if (connectionState == ObdConnectionState.CONNECTED) {
            val scale by rememberInfiniteTransition(label = "top_dot").animateFloat(
                initialValue = 1f, targetValue = 1.4f, label = "scale",
                animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            )
            Box(Modifier.size(10.dp).scale(scale).clip(CircleShape).background(dotColor))
        } else {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
        }
    }
}

// ── Alert banner ──────────────────────────────────────────────────────────────

@Composable
private fun AlertBanner(level: TempAlertLevel, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = level != TempAlertLevel.NONE,
        enter = slideInVertically(tween(300)) { -it } + fadeIn(tween(300)),
        exit  = slideOutVertically(tween(250)) { -it } + fadeOut(tween(200)),
    ) {
        val (bg, msg) = when (level) {
            TempAlertLevel.COOLANT_CRITICAL -> DarkError   to "Coolant temp critical (≥120°C) — pull over immediately"
            TempAlertLevel.OIL_HOT          -> DarkWarning to "Oil temp high (≥127°C) — reduce engine load"
            TempAlertLevel.CVT_HOT          -> DarkWarning to "CVT running hot (≥121°C) — ease off throttle"
            TempAlertLevel.NONE             -> DarkSuccess to ""
        }
        val pulse = rememberInfiniteTransition(label = "banner_pulse")
        val bannerAlpha by pulse.animateFloat(
            initialValue = 1f, targetValue = 0.7f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
            label = "banner_alpha",
        )
        Surface(
            color = bg.copy(alpha = 0.92f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().alpha(bannerAlpha),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(msg, color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Color.White.copy(0.8f), modifier = Modifier.size(16.dp))
                }
            }
        }
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
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f), modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text("No vehicle selected — go to Settings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
            }
        } else {
            val v = state.vehicle
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(DarkPrimary, DarkPrimary.copy(0.6f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.DirectionsCar, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${v.year} Subaru ${v.modelName}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${v.engineDisplayName} · ${v.engineCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                }
                if (v.isTurbo) {
                    Surface(color = DarkPrimary.copy(0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text("TURBO", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall, color = DarkPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                if (state.ssmFallback) {
                    Spacer(Modifier.width(6.dp))
                    Surface(color = DarkWarning.copy(0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text("SSM↓OBD", modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = DarkWarning, fontWeight = FontWeight.Bold)
                    }
                }
                // Market badge
                val mktColor = v.market.badgeColor
                Spacer(Modifier.width(4.dp))
                Surface(color = mktColor.copy(0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(v.market.displayName,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = mktColor, fontWeight = FontWeight.Bold)
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
    val connLabel = if (state.connectedDeviceName != null) "Connected · ${state.connectedDeviceName}"
                    else "OBD Connected"
    val (dotColor, label) = when (cs) {
        ObdConnectionState.DISCONNECTED -> DarkError.copy(0.7f) to "OBD Disconnected"
        ObdConnectionState.CONNECTING   -> DarkWarning          to "Connecting"
        ObdConnectionState.CONNECTED    -> DarkSuccess          to connLabel
        ObdConnectionState.ERROR        -> DarkError            to (state.errorMessage?.let { "Error: $it" } ?: "Connection Failed")
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            when (cs) {
                ObdConnectionState.CONNECTING -> {
                    val pulse by rememberInfiniteTransition(label = "dot_pulse").animateFloat(
                        initialValue = 0.35f, targetValue = 1f, label = "alpha",
                        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
                    )
                    Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor.copy(alpha = pulse)))
                }
                ObdConnectionState.CONNECTED -> {
                    val scale by rememberInfiniteTransition(label = "dot_scale").animateFloat(
                        initialValue = 1f, targetValue = 1.3f, label = "scale",
                        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    )
                    Box(Modifier.size(10.dp).scale(scale).clip(CircleShape).background(dotColor))
                }
                else -> Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
            }
            Spacer(Modifier.width(10.dp))
            if (cs == ObdConnectionState.CONNECTING) {
                val frame by rememberInfiniteTransition(label = "dots").animateFloat(
                    initialValue = 0f, targetValue = 4f, label = "frame",
                    animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
                )
                Text(label + ".".repeat(frame.toInt() % 4), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            } else {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            when (cs) {
                ObdConnectionState.CONNECTING -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = DarkPrimary)
                ObdConnectionState.CONNECTED  -> OutlinedButton(
                    onClick = onDisconnect,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Disconnect", style = MaterialTheme.typography.labelSmall)
                }
                else -> Button(
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

// ── 2×2 Gauge grid ────────────────────────────────────────────────────────────

@Composable
private fun GaugeGrid(metrics: List<LiveMetric>, onEditSlot: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricCard(metric = metrics.getOrElse(0) { metrics[0] }, onEdit = { onEditSlot(0) }, modifier = Modifier.weight(1f))
            MetricCard(metric = metrics.getOrElse(1) { metrics[0] }, onEdit = { onEditSlot(1) }, modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricCard(metric = metrics.getOrElse(2) { metrics[0] }, onEdit = { onEditSlot(2) }, modifier = Modifier.weight(1f))
            MetricCard(metric = metrics.getOrElse(3) { metrics[0] }, onEdit = { onEditSlot(3) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCard(metric: LiveMetric, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "card_${metric.id}")
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (metric.highlight) 0.3f else 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "flash_${metric.id}",
    )
    val animatedFraction by animateFloatAsState(
        targetValue = metric.fraction ?: 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "arc_${metric.id}",
    )

    val accentColor = if (metric.highlight) DarkWarning else MaterialTheme.colorScheme.primary
    val arcBg = MaterialTheme.colorScheme.onSurface.copy(0.08f)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp,
        modifier = modifier.aspectRatio(1f).alpha(flashAlpha),
    ) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val canvasSize = maxWidth * 0.85f
            androidx.compose.foundation.Canvas(modifier = Modifier.size(canvasSize)) {
                val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                val startAngle = 150f
                val sweepTotal = 240f
                drawArc(color = arcBg, startAngle = startAngle, sweepAngle = sweepTotal, useCenter = false, style = stroke)
                if (metric.fraction != null && animatedFraction > 0f) {
                    drawArc(color = accentColor, startAngle = startAngle, sweepAngle = sweepTotal * animatedFraction, useCenter = false, style = stroke)
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(8.dp),
            ) {
                Icon(imageVector = metricIcon(metric.iconRes), contentDescription = null, tint = accentColor, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(2.dp))
                AnimatedContent(
                    targetState = metric.value,
                    transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) },
                    label = metric.id,
                ) { v ->
                    Text(text = v,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 36.sp),
                        color = accentColor, textAlign = TextAlign.Center)
                }
                Text(metric.unit, style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                Text(metric.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f), textAlign = TextAlign.Center, maxLines = 1)
            }

            // Edit pencil button
            IconButton(
                onClick = onEdit,
                modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit gauge",
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.25f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── Gauge selector bottom sheet ───────────────────────────────────────────────

@Composable
private fun GaugeSelectorSheet(
    pids: List<ObdPid>,
    currentCmd: String,
    onSelect: (ObdPid) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        Text(
            "Select Sensor",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        HorizontalDivider()
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(pids) { pid ->
                val isSelected = pid.cmd == currentCmd

                Surface(
                    onClick = { onSelect(pid) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isSelected) DarkPrimary.copy(0.08f) else Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(metricIcon(pid.toMetricIconEnum()), contentDescription = null,
                            tint = DarkPrimary,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pid.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(pid.unit, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                        if (isSelected) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = DarkPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun ObdPid.toMetricIconEnum(): MetricIcon = when (cmd) {
    ObdPids.RPM.cmd          -> MetricIcon.RPM
    ObdPids.SPEED.cmd        -> MetricIcon.SPEED
    ObdPids.COOLANT_TEMP.cmd -> MetricIcon.TEMP
    ObdPids.THROTTLE.cmd     -> MetricIcon.THROTTLE
    ObdPids.VOLTAGE.cmd      -> MetricIcon.VOLTAGE
    ObdPids.INTAKE_TEMP.cmd  -> MetricIcon.INTAKE
    ObdPids.AMBIENT_TEMP.cmd -> MetricIcon.AMBIENT
    ObdPids.FUEL_LEVEL.cmd   -> MetricIcon.FUEL
    ObdPids.OIL_TEMP.cmd     -> MetricIcon.OIL
    ObdPids.CVT_TEMP.cmd     -> MetricIcon.CVT
    ObdPids.ENGINE_LOAD.cmd  -> MetricIcon.ENGINE_LOAD
    ObdPids.MAP.cmd          -> MetricIcon.MAP
    ObdPids.MAF.cmd          -> MetricIcon.MAF
    else                     -> MetricIcon.RPM
}

// ── Fuel consumption card ─────────────────────────────────────────────────────

@Composable
private fun FuelConsumptionCard(fuel: FuelConsumptionState, onReset: () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter = expandVertically() + fadeIn(),
        exit  = shrinkVertically() + fadeOut(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalGasStation, contentDescription = null,
                        tint = DarkPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Fuel Consumption", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onReset,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp),
                    ) {
                        Text("Reset avg", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FuelStatBox(
                        label = "Instant",
                        value = fuel.instantMpg?.let { "%.1f mpg".format(it) }
                            ?: fuel.instantL100?.let { "%.1f L/100".format(it) }
                            ?: "--",
                        modifier = Modifier.weight(1f),
                    )
                    FuelStatBox(
                        label = "Average (${fuel.sampleCount} samples)",
                        value = fuel.averageMpg?.let { "%.1f mpg".format(it) }
                            ?: fuel.averageL100?.let { "%.1f L/100".format(it) }
                            ?: "--",
                        modifier = Modifier.weight(1f),
                    )
                }
                if (fuel.instantL100 == null) {
                    Spacer(Modifier.height(6.dp))
                    Text("MAF and speed data needed for fuel calculation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                }
            }
        }
    }
}

@Composable
private fun FuelStatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = DarkPrimary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f), textAlign = TextAlign.Center)
        }
    }
}

// ── DTC fault-code row ────────────────────────────────────────────────────────

@Composable
private fun DtcRow(dtcCount: Int, connected: Boolean) {
    Surface(
        color = if (dtcCount == 0) DarkSuccess.copy(0.10f) else DarkError.copy(0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                )
                if (connected && dtcCount == 0) {
                    Text("System nominal", style = MaterialTheme.typography.labelSmall, color = DarkSuccess.copy(0.8f))
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun metricIcon(icon: MetricIcon): ImageVector = when (icon) {
    MetricIcon.RPM         -> Icons.Filled.Speed
    MetricIcon.SPEED       -> Icons.Filled.DirectionsCar
    MetricIcon.TEMP        -> Icons.Filled.DeviceThermostat
    MetricIcon.THROTTLE    -> Icons.Filled.Tune
    MetricIcon.VOLTAGE     -> Icons.Filled.BatteryFull
    MetricIcon.INTAKE      -> Icons.Filled.Compress
    MetricIcon.AMBIENT     -> Icons.Filled.Air
    MetricIcon.FUEL        -> Icons.Filled.LocalGasStation
    MetricIcon.OIL         -> Icons.Filled.WaterDrop
    MetricIcon.CVT         -> Icons.Filled.WaterDrop
    MetricIcon.ENGINE_LOAD -> Icons.Filled.Speed
    MetricIcon.MAP         -> Icons.Filled.Compress
    MetricIcon.MAF         -> Icons.Filled.Air
}

private val Market.badgeColor: androidx.compose.ui.graphics.Color
    get() = when (this) {
        Market.GLOBAL -> DarkPrimary
        Market.JDM    -> DarkWarning
        Market.EU     -> DarkSuccess
    }
