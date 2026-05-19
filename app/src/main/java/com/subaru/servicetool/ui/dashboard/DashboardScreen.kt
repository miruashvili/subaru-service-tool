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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewStream
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.subaru.servicetool.data.alert.TempAlertLevel
import com.subaru.servicetool.data.model.Market
import com.subaru.servicetool.data.model.displayName
import com.subaru.servicetool.data.obd.ObdPid
import com.subaru.servicetool.data.obd.ObdPids
import com.subaru.servicetool.data.preferences.DisplayUnits
import com.subaru.servicetool.ui.theme.DarkError
import com.subaru.servicetool.ui.theme.DarkPrimary
import com.subaru.servicetool.ui.theme.DarkSuccess
import com.subaru.servicetool.ui.theme.DarkWarning
import com.subaru.servicetool.ui.theme.GaugeTempCrit
import com.subaru.servicetool.ui.theme.GaugeTempWarn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    paddingValues: PaddingValues = PaddingValues(),
    onNavigateToBluetooth: () -> Unit = {},
    onNavigateToSportGauge: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state              by viewModel.uiState.collectAsState()
    val alertLevel         by viewModel.showAlertBanner.collectAsState()
    val connLost           by viewModel.connectionLostVisible.collectAsState()
    val editingSlot        by viewModel.editingSlot.collectAsState()
    val editingWideSlot    by viewModel.editingWideSlot.collectAsState()
    val gaugeSlots         by viewModel.currentGaugeSlots.collectAsState()
    val wideSlots          by viewModel.currentWideGaugeSlots.collectAsState()
    // New landscape row state
    val lsBotMode          by viewModel.lsBotMode.collectAsState()
    val lsTopMetrics       by viewModel.lsTopMetrics.collectAsState()
    val lsMidMetrics       by viewModel.lsMidMetrics.collectAsState()
    val lsMidSlotCmds      by viewModel.lsMidSlotCmds.collectAsState()
    val lsBotMetrics       by viewModel.lsBotMetrics.collectAsState()
    val lsBotWideMetrics   by viewModel.lsBotWideMetrics.collectAsState()
    val lsBotWideSlotCmds  by viewModel.lsBotWideSlotCmds.collectAsState()
    val lsTopSlotCmds      by viewModel.lsTopSlotCmds.collectAsState()
    val lsBotSlotCmds      by viewModel.lsBotSlotCmds.collectAsState()
    val editingLsTopSlot   by viewModel.editingLsTopSlot.collectAsState()
    val editingLsMidSlot   by viewModel.editingLsMidSlot.collectAsState()
    val editingLsBotSlot   by viewModel.editingLsBotSlot.collectAsState()
    val editingLsBotWideSlot by viewModel.editingLsBotWideSlot.collectAsState()
    val configuration      = LocalConfiguration.current
    val isLandscape        = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenWidthDp      = configuration.screenWidthDp

    val gaugeValueSp: TextUnit = when {
        screenWidthDp < 600  -> 28.sp
        screenWidthDp <= 840 -> 36.sp
        else                 -> 44.sp
    }
    val gaugeLabelSp: TextUnit = when {
        screenWidthDp < 600  -> 11.sp
        screenWidthDp <= 840 -> 13.sp
        else                 -> 15.sp
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToBluetooth.collect { onNavigateToBluetooth() }
    }

    // ── Gauge editor bottom sheets ────────────────────────────────────────────
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

    if (editingWideSlot != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::closeWideGaugeEditor,
            sheetState = sheetState,
        ) {
            GaugeSelectorSheet(
                pids       = viewModel.configurableWidePids,
                currentCmd = wideSlots.getOrNull(editingWideSlot!!) ?: "",
                onSelect   = { pid -> viewModel.setWideGaugeSlot(editingWideSlot!!, pid.cmd) },
                onDismiss  = viewModel::closeWideGaugeEditor,
            )
        }
    }

    if (editingLsTopSlot != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeLsTopEditor,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            GaugeSelectorSheet(
                pids       = viewModel.configurablePids,
                currentCmd = lsTopSlotCmds.getOrNull(editingLsTopSlot!!) ?: "",
                onSelect   = { pid -> viewModel.setLsTopSlot(editingLsTopSlot!!, pid.cmd) },
                onDismiss  = viewModel::closeLsTopEditor,
            )
        }
    }

    if (editingLsMidSlot != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeLsMidEditor,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            GaugeSelectorSheet(
                pids       = viewModel.configurableMidPids,
                currentCmd = lsMidSlotCmds.getOrNull(editingLsMidSlot!!) ?: "",
                onSelect   = { pid -> viewModel.setLsMidSlot(editingLsMidSlot!!, pid.cmd) },
                onDismiss  = viewModel::closeLsMidEditor,
            )
        }
    }

    if (editingLsBotSlot != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeLsBotEditor,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            GaugeSelectorSheet(
                pids       = viewModel.configurablePids,
                currentCmd = lsBotSlotCmds.getOrNull(editingLsBotSlot!!) ?: "",
                onSelect   = { pid -> viewModel.setLsBotSlot(editingLsBotSlot!!, pid.cmd) },
                onDismiss  = viewModel::closeLsBotEditor,
            )
        }
    }

    if (editingLsBotWideSlot != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeLsBotWideEditor,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            GaugeSelectorSheet(
                pids       = viewModel.configurableBotWidePids,
                currentCmd = lsBotWideSlotCmds.getOrNull(editingLsBotWideSlot!!) ?: "",
                onSelect   = { pid -> viewModel.setLsBotWideSlot(editingLsBotWideSlot!!, pid.cmd) },
                onDismiss  = viewModel::closeLsBotWideEditor,
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues),
    ) {
        if (isLandscape) {
            // ── Landscape: 3-row layout (40% / 20% / 40%), no scroll ─────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AlertBanner(alertLevel, onDismiss = viewModel::dismissAlert)

                // Row 1: 4 square gauge cards (~40%)
                Row(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (i in 0..3) {
                        MetricCard(
                            metric         = lsTopMetrics.getOrElse(i) { lsTopMetrics.firstOrNull() ?: return@Row },
                            onEdit         = { viewModel.openLsTopEditor(i) },
                            modifier       = Modifier.weight(1f),
                            useAspectRatio = false,
                            valueFontSize  = gaugeValueSp,
                            labelFontSize  = gaugeLabelSp,
                        )
                    }
                }

                // Row 2: 3 wide sensor bars (~20%)
                Row(
                    modifier = Modifier
                        .weight(0.2f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (i in 0..2) {
                        LandscapeMidBar(
                            cmd     = lsMidSlotCmds.getOrElse(i) { "010D" },
                            metric  = lsMidMetrics.getOrNull(i),
                            state   = state,
                            onEdit  = { viewModel.openLsMidEditor(i) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }

                // Row 3: bottom section (~40%) — square or wide mode
                Box(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (lsBotMode == "square") {
                            for (i in 0..3) {
                                MetricCard(
                                    metric         = lsBotMetrics.getOrElse(i) { lsBotMetrics.firstOrNull() ?: return@Row },
                                    onEdit         = { viewModel.openLsBotEditor(i) },
                                    modifier       = Modifier.weight(1f),
                                    useAspectRatio = false,
                                    valueFontSize  = gaugeValueSp,
                                    labelFontSize  = gaugeLabelSp,
                                )
                            }
                        } else {
                            for (i in 0..1) {
                                LandscapeBotWideCard(
                                    cmd      = lsBotWideSlotCmds.getOrElse(i) { "221018" },
                                    metric   = lsBotWideMetrics.getOrNull(i),
                                    state    = state,
                                    onEdit   = { viewModel.openLsBotWideEditor(i) },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                        }
                    }
                    // Layout toggle button — bottom-left corner of Row 3
                    IconButton(
                        onClick  = viewModel::toggleLsBotMode,
                        modifier = Modifier.align(Alignment.BottomStart).size(28.dp),
                    ) {
                        Icon(
                            imageVector        = if (lsBotMode == "square") Icons.Filled.ViewStream else Icons.Filled.GridView,
                            contentDescription = "Toggle layout",
                            tint               = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                            modifier           = Modifier.size(15.dp),
                        )
                    }
                }
            }
        } else {
            // ── Portrait: scrollable column with wide widgets ─────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashboardTopBar(
                    connectionState    = state.connectionState,
                    ambientTemp        = state.ambientTemp,
                    connectedName      = state.connectedDeviceName,
                    onNavigateToSport  = onNavigateToSportGauge,
                )
                AlertBanner(alertLevel, onDismiss = viewModel::dismissAlert)
                GaugeGrid(
                    metrics    = state.metrics,
                    onEditSlot = viewModel::openGaugeEditor,
                )

                // Wide gauge cards below the 2×2 grid
                state.wideMetrics.forEachIndexed { i, metric ->
                    WideGaugeCard(
                        metric       = metric,
                        onEdit       = { viewModel.openWideGaugeEditor(i) },
                        awdDuty      = state.awdDuty,
                        tpmsData     = state.tpmsData,
                        displayUnits = state.displayUnits,
                    )
                }

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
    dtcCount: Int = 0,
    onNavigateToSport: () -> Unit = {},
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

        // DTC badge (shown when faults detected)
        if (dtcCount > 0) {
            Surface(
                color = DarkError.copy(0.15f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    "$dtcCount DTC${if (dtcCount > 1) "s" else ""}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkError,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(8.dp))
        }

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

        // Sport gauge button
        IconButton(onClick = onNavigateToSport, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Speed, contentDescription = "Sport Gauge",
                tint = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(4.dp))

        // Connection status dot
        val dotColor = when (connectionState) {
            ObdConnectionState.CONNECTED    -> DarkSuccess
            ObdConnectionState.CONNECTING   -> DarkWarning
            ObdConnectionState.ERROR        -> DarkError
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

// ── 2×2 Gauge grid ────────────────────────────────────────────────────────────

@Composable
private fun GaugeGrid(metrics: List<LiveMetric>, onEditSlot: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(metric = metrics.getOrElse(0) { metrics[0] }, onEdit = { onEditSlot(0) }, modifier = Modifier.weight(1f))
            MetricCard(metric = metrics.getOrElse(1) { metrics[0] }, onEdit = { onEditSlot(1) }, modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(metric = metrics.getOrElse(2) { metrics[0] }, onEdit = { onEditSlot(2) }, modifier = Modifier.weight(1f))
            MetricCard(metric = metrics.getOrElse(3) { metrics[0] }, onEdit = { onEditSlot(3) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCard(
    metric: LiveMetric,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    useAspectRatio: Boolean = true,
    valueFontSize: TextUnit = 36.sp,
    labelFontSize: TextUnit = 13.sp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "card_${metric.id}")
    val shouldFlash = metric.highlight || metric.alertLevel == MetricAlertLevel.CRITICAL
    val flashAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (shouldFlash) 0.3f else 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "flash_${metric.id}",
    )
    val animatedFraction by animateFloatAsState(
        targetValue = metric.fraction ?: 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "arc_${metric.id}",
    )

    val arcColor  = when {
        metric.alertLevel == MetricAlertLevel.CRITICAL -> GaugeTempCrit
        metric.alertLevel == MetricAlertLevel.WARNING  -> GaugeTempWarn
        metric.highlight                               -> DarkWarning
        else                                           -> MaterialTheme.colorScheme.primary
    }
    val arcBg      = MaterialTheme.colorScheme.onSurface.copy(0.1f)
    val cardBg     = MaterialTheme.colorScheme.surface
    val valueColor = MaterialTheme.colorScheme.onSurface
    val unitColor  = MaterialTheme.colorScheme.onSurface.copy(0.5f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(0.4f)

    val surfaceMod = if (useAspectRatio) modifier.aspectRatio(1f).alpha(flashAlpha)
                     else modifier.alpha(flashAlpha)

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp,
        modifier = surfaceMod,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
        ) {
            // Arc drawn in full-card canvas; arc radius derived from min(w,h) to prevent overflow
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val sz = minOf(size.width, size.height)
                val arcSz = sz * 0.82f
                val tlX = (size.width - arcSz) / 2f
                val tlY = (size.height - arcSz) / 2f * 0.7f
                val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                val startAngle = 150f
                val sweepTotal = 240f
                drawArc(color = arcBg, topLeft = Offset(tlX, tlY), size = Size(arcSz, arcSz),
                    startAngle = startAngle, sweepAngle = sweepTotal, useCenter = false, style = stroke)
                if (metric.fraction != null && animatedFraction > 0f) {
                    drawArc(color = arcColor, topLeft = Offset(tlX, tlY), size = Size(arcSz, arcSz),
                        startAngle = startAngle, sweepAngle = sweepTotal * animatedFraction,
                        useCenter = false, style = stroke)
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(8.dp),
            ) {
                Icon(imageVector = metricIcon(metric.iconRes), contentDescription = null, tint = arcColor, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(2.dp))
                AnimatedContent(
                    targetState = metric.value,
                    transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) },
                    label = metric.id,
                ) { v ->
                    Text(
                        text  = v,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = valueFontSize),
                        color = valueColor,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(metric.unit, style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp), color = unitColor)
                Text(metric.label, style = MaterialTheme.typography.labelSmall.copy(fontSize = labelFontSize),
                    color = labelColor, textAlign = TextAlign.Center, maxLines = 1)
            }
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

// ── Wide gauge card (portrait full-width, ~90dp) ──────────────────────────────

@Composable
private fun WideGaugeCard(
    metric: LiveMetric,
    onEdit: () -> Unit,
    awdDuty: Float?,
    tpmsData: TpmsData,
    displayUnits: DisplayUnits,
    modifier: Modifier = Modifier,
) {
    val cardBg  = MaterialTheme.colorScheme.surface

    Surface(
        color          = cardBg,
        shape          = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp,
        modifier       = modifier.fillMaxWidth().height(90.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                metric.cmd == ObdPids.AWD_DUTY.cmd ->
                    AwdContent(rearDuty = awdDuty ?: 0f, compact = true)

                metric.cmd == ObdPids.TPMS_FL.cmd ->
                    TpmsContent(data = tpmsData, units = displayUnits)

                else ->
                    WideMetricContent(metric = metric)
            }
            IconButton(
                onClick  = onEdit,
                modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.25f),
                    modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun WideMetricContent(metric: LiveMetric) {
    val arcColor   = when {
        metric.alertLevel == MetricAlertLevel.CRITICAL -> GaugeTempCrit
        metric.alertLevel == MetricAlertLevel.WARNING  -> GaugeTempWarn
        metric.highlight                               -> DarkWarning
        else                                           -> MaterialTheme.colorScheme.primary
    }
    val valueColor = MaterialTheme.colorScheme.onSurface
    val unitColor  = MaterialTheme.colorScheme.onSurface.copy(0.5f)
    val labelColor = MaterialTheme.colorScheme.onSurface.copy(0.4f)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(imageVector = metricIcon(metric.iconRes), contentDescription = null,
            tint = arcColor, modifier = Modifier.size(28.dp))
        Column {
            Text(
                metric.value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp),
                color = valueColor,
            )
            Row {
                Text(metric.unit, style = MaterialTheme.typography.labelMedium, color = unitColor)
                Spacer(Modifier.width(6.dp))
                Text(metric.label, style = MaterialTheme.typography.labelMedium, color = labelColor)
            }
        }
    }
}

@Composable
private fun TpmsContent(data: TpmsData, units: DisplayUnits) {
    fun fmt(kpa: Float?): String {
        if (kpa == null) return "--"
        return when (units.pressureUnit) {
            "psi" -> "%.0f".format(kpa * 0.145038f)
            "bar" -> "%.2f".format(kpa / 100f)
            else  -> "%.0f".format(kpa)
        }
    }
    val unit = when (units.pressureUnit) { "psi" -> "psi"; "bar" -> "bar"; else -> "kPa" }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        TireCell("FL", fmt(data.fl), unit)
        TireCell("FR", fmt(data.fr), unit)
        TireCell("RL", fmt(data.rl), unit)
        TireCell("RR", fmt(data.rr), unit)
    }
}

@Composable
private fun TireCell(pos: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        Text(pos, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
    }
}

// ── Landscape mid-row bar (wide sensor card, ~20% height) ─────────────────────

@Composable
private fun LandscapeMidBar(
    cmd: String,
    metric: LiveMetric?,
    state: DashboardUiState,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardBg = MaterialTheme.colorScheme.surface

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (cmd) {
                ObdPids.AWD_DUTY.cmd -> AwdContent(rearDuty = state.awdDuty ?: 0f, compact = true)
                "FUEL_CONS"          -> FuelMidContent(fuel = state.fuelConsumption)
                "TPMS_ALL"           -> TpmsContent(data = state.tpmsData, units = state.displayUnits)
                else                 -> if (metric != null) WideSensorBarContent(metric = metric)
            }
            IconButton(onClick = onEdit, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.25f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun WideSensorBarContent(metric: LiveMetric) {
    val arcColor   = when {
        metric.alertLevel == MetricAlertLevel.CRITICAL -> GaugeTempCrit
        metric.alertLevel == MetricAlertLevel.WARNING  -> GaugeTempWarn
        metric.highlight                               -> DarkWarning
        else                                           -> MaterialTheme.colorScheme.primary
    }
    val valueColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = metricIcon(metric.iconRes), contentDescription = null,
            tint = arcColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(metric.label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.4f), modifier = Modifier.weight(1f), maxLines = 1)
        Text(metric.value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = valueColor)
        Spacer(Modifier.width(4.dp))
        Text(metric.unit, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
    }
}

@Composable
private fun FuelMidContent(fuel: FuelConsumptionState) {
    val valueColor = MaterialTheme.colorScheme.onSurface
    val instant = fuel.instantMpg?.let { "%.1f mpg".format(it) }
        ?: fuel.instantKml?.let { "%.1f km/L".format(it) }
        ?: fuel.instantL100?.let { "%.1f".format(it) }
        ?: "--"
    val unit = if (fuel.instantL100 != null) "L/100" else ""

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.LocalGasStation, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Fuel", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.4f), modifier = Modifier.weight(1f))
        Text(instant,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = valueColor)
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(unit, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
        }
    }
}

// ── Landscape bottom-row wide card (MODE B, ~40% height) ──────────────────────

@Composable
private fun LandscapeBotWideCard(
    cmd: String,
    metric: LiveMetric?,
    state: DashboardUiState,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardBg = MaterialTheme.colorScheme.surface

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (cmd) {
                ObdPids.AWD_DUTY.cmd -> AwdContent(rearDuty = state.awdDuty ?: 0f, compact = false)
                "TPMS_ALL"           -> TpmsContent(data = state.tpmsData, units = state.displayUnits)
                else                 -> if (metric != null) WideMetricContent(metric = metric)
            }
            IconButton(onClick = onEdit, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit",
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
                            tint = DarkPrimary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pid.name, style = MaterialTheme.typography.bodyMedium,
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
    ObdPids.AWD_DUTY.cmd     -> MetricIcon.AWD
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
                    Text("Fuel Consumption", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
                            ?: fuel.instantKml?.let { "%.1f km/L".format(it) }
                            ?: fuel.instantL100?.let { "%.1f L/100".format(it) }
                            ?: "--",
                        modifier = Modifier.weight(1f),
                    )
                    FuelStatBox(
                        label = "Average (${fuel.sampleCount} samples)",
                        value = fuel.averageMpg?.let { "%.1f mpg".format(it) }
                            ?: fuel.averageKml?.let { "%.1f km/L".format(it) }
                            ?: fuel.averageL100?.let { "%.1f L/100".format(it) }
                            ?: "--",
                        modifier = Modifier.weight(1f),
                    )
                }
                if (fuel.instantL100 == null && fuel.instantMpg == null && fuel.instantKml == null) {
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
    MetricIcon.AWD         -> Icons.Filled.DirectionsCar
    MetricIcon.ENGINE_LOAD -> Icons.Filled.Speed
    MetricIcon.MAP         -> Icons.Filled.Compress
    MetricIcon.MAF         -> Icons.Filled.Air
}

// ── AWD torque distribution (shared content) ──────────────────────────────────

@Composable
private fun AwdContent(rearDuty: Float, compact: Boolean = false) {
    val frontPct = (100f - rearDuty).coerceIn(0f, 100f)
    val rearPct  = rearDuty.coerceIn(0f, 100f)
    val animatedFrontFraction by animateFloatAsState(
        targetValue   = frontPct / 100f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label         = "awd_front",
    )

    val lang = java.util.Locale.getDefault().language
    val title = when (lang) {
        "ka" -> "AWD გადანაწილება"
        "ru" -> "Распределение AWD"
        "es" -> "Distribución AWD"
        "fr" -> "Répartition AWD"
        "de" -> "AWD-Verteilung"
        else -> "AWD Torque Distribution"
    }
    val frontLabel = when (lang) {
        "ka" -> "წინა"; "ru" -> "ПЕРЕД"; "es" -> "DELAN."
        "fr" -> "AVANT"; "de" -> "VORNE"; else -> "FRONT"
    }
    val rearLabel = when (lang) {
        "ka" -> "უკანა"; "ru" -> "ЗАД"; "es" -> "TRAS."
        "fr" -> "ARR."; "de" -> "HINTEN"; else -> "REAR"
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = if (compact) 6.dp else 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DirectionsCar, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface.copy(0.65f),
            )
        }
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
                Text("${frontPct.toInt()}%",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary)
                Text(frontLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
            Box(modifier = Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp))) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.onSurface.copy(0.1f)))
                Box(modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFrontFraction)
                    .background(MaterialTheme.colorScheme.primary))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
                Text("${rearPct.toInt()}%",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = DarkWarning)
                Text(rearLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
            }
        }
    }
}

private val Market.badgeColor: androidx.compose.ui.graphics.Color
    get() = when (this) {
        Market.GLOBAL -> DarkPrimary
        Market.JDM    -> DarkWarning
        Market.EU     -> DarkSuccess
    }
