package com.subaru.servicetool.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.subaru.servicetool.data.model.IssueSeverity
import com.subaru.servicetool.data.model.KnownIssue
import com.subaru.servicetool.data.model.VehicleSpec
import com.subaru.servicetool.data.model.forLocale
import com.subaru.servicetool.data.service.ServiceEvent
import com.subaru.servicetool.data.service.ServiceEventType
import com.subaru.servicetool.ui.service.ActiveProcedure
import com.subaru.servicetool.ui.service.CoolantSample
import com.subaru.servicetool.ui.service.CvtConditions
import com.subaru.servicetool.ui.service.DtcResult
import com.subaru.servicetool.ui.service.DtcScanState
import com.subaru.servicetool.ui.service.ProcedureState
import com.subaru.servicetool.ui.service.ServiceViewModel
import com.subaru.servicetool.ui.service.TcvMonitorState
import com.subaru.servicetool.ui.service.TcvWarning
import com.subaru.servicetool.ui.theme.DarkError
import com.subaru.servicetool.ui.theme.DarkPrimary
import com.subaru.servicetool.ui.theme.DarkSuccess
import com.subaru.servicetool.ui.theme.DarkWarning

@Composable
fun ServiceScreen(
    paddingValues: PaddingValues = PaddingValues(),
    viewModel: ServiceViewModel = hiltViewModel(),
) {
    val vehicle       by viewModel.selectedVehicle.collectAsState()
    val dtcState      by viewModel.dtcScanState.collectAsState()
    val procState     by viewModel.procedureState.collectAsState()
    val activeProc    by viewModel.activeProcedure.collectAsState()
    val cvtConds      by viewModel.cvtConditions.collectAsState()
    val tcvMon        by viewModel.tcvMonitor.collectAsState()
    val showConfirm       by viewModel.showClearConfirm.collectAsState()
    val showEcuRelearn    by viewModel.showEcuRelearnDialog.collectAsState()
    val cvtRelearnStep    by viewModel.cvtRelearnStep.collectAsState()
    val serviceLog        by viewModel.serviceLog.collectAsState()
    val showAddSvc        by viewModel.showAddService.collectAsState()

    if (showAddSvc) {
        AddServiceDialog(
            onConfirm = viewModel::logServiceEvent,
            onDismiss = viewModel::dismissAddService,
        )
    }

    if (showEcuRelearn) {
        EcuRelearnDialog(onDismiss = viewModel::dismissEcuRelearnDialog)
    }

    // CVT guided gear cycle overlay
    cvtRelearnStep?.let { step ->
        CvtRelearnStepDialog(
            step      = step,
            onConfirm = viewModel::advanceCvtRelearnStep,
            onCancel  = viewModel::cancelCvtRelearn,
        )
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearConfirm,
            title = { Text("Clear All Fault Codes?") },
            text  = { Text("This will erase all stored DTCs from the ECU. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmClearDtcs,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkError),
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissClearConfirm) { Text("Cancel") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Service", style = MaterialTheme.typography.headlineMedium) }

        // ── Maintenance Log ────────────────────────────────────────────────
        item {
            MaintenanceLogCard(
                events    = serviceLog,
                onAdd     = viewModel::requestAddService,
                onRemove  = viewModel::removeServiceEvent,
            )
        }

        // ── Vehicle Health ─────────────────────────────────────────────────
        vehicle?.let { v ->
            if (v.knownIssueIds.isNotEmpty()) {
                item {
                    VehicleHealthCard(
                        vehicle    = v,
                        tcvMonitor = tcvMon,
                        activeDtcChecker = viewModel::isDtcActive,
                        onToggleTcv = viewModel::toggleTcvMonitor,
                    )
                }
            }
        }

        // ── Card 1: Diagnostics ────────────────────────────────────────────
        item {
            DiagnosticsCard(
                dtcState   = dtcState,
                onScan     = viewModel::scanDtcs,
                onClear    = viewModel::requestClearDtcs,
                onReset    = viewModel::resetDtcState,
            )
        }

        // ── Card 2: Engine Adaptation Reset ───────────────────────────────
        item {
            ProcedureCard(
                title        = "Engine Adaptation Reset",
                description  = "Clears ECU adaptive fuel trims. Required after engine work, injector replacement, or rough idle.",
                icon         = Icons.Filled.Settings,
                buttonLabel  = "Run Reset",
                buttonColor  = DarkPrimary,
                isActive     = activeProc == ActiveProcedure.ENGINE_ADAPT,
                procState    = if (activeProc == ActiveProcedure.ENGINE_ADAPT || procState is ProcedureState.Success) procState else ProcedureState.Idle,
                onStart      = viewModel::startEngineAdaptationReset,
                onDismiss    = viewModel::resetProcedure,
            )
        }

        // ── Card 3: Throttle Body Relearn ─────────────────────────────────
        item {
            ProcedureCard(
                title        = "Throttle Body Relearn",
                description  = "Resets throttle position baseline. Required after cleaning throttle body or replacing the unit.",
                icon         = Icons.Filled.Speed,
                buttonLabel  = "Run Relearn",
                buttonColor  = DarkPrimary,
                isActive     = activeProc == ActiveProcedure.THROTTLE_RELEARN,
                procState    = if (activeProc == ActiveProcedure.THROTTLE_RELEARN || procState is ProcedureState.Success) procState else ProcedureState.Idle,
                onStart      = viewModel::startThrottleBodyRelearn,
                onDismiss    = viewModel::resetProcedure,
            )
        }

        // ── Card 4: CVT Reset & Learning ──────────────────────────────────
        item {
            CvtResetCard(
                conditions = cvtConds,
                isActive   = activeProc == ActiveProcedure.CVT_RESET,
                procState  = if (activeProc == ActiveProcedure.CVT_RESET || procState is ProcedureState.Success) procState else ProcedureState.Idle,
                onTogglePark        = { viewModel.toggleCvtManualCondition(park = !cvtConds.inPark) },
                onToggleAccessories = { viewModel.toggleCvtManualCondition(accessories = !cvtConds.accessoriesOff) },
                onStart  = viewModel::startCvtReset,
                onDismiss = viewModel::resetProcedure,
            )
        }

        // ── Recommendations ────────────────────────────────────────────────
        item {
            RecommendationsCard()
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ── Vehicle Health Card ───────────────────────────────────────────────────────

@Composable
private fun VehicleHealthCard(
    vehicle: VehicleSpec,
    tcvMonitor: TcvMonitorState,
    activeDtcChecker: (String) -> Boolean,
    onToggleTcv: () -> Unit,
) {
    ServiceCard(title = "Vehicle Health", icon = Icons.Filled.Warning, iconTint = DarkWarning) {
        Text(
            "${vehicle.year} Subaru ${vehicle.modelName} · ${vehicle.generation}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
        )
        Spacer(Modifier.height(10.dp))

        vehicle.knownIssueIds.forEachIndexed { idx, issueId ->
            val issue = com.subaru.servicetool.data.model.KnownIssueRegistry.findById(issueId) ?: return@forEachIndexed
            val anyActive = issue.dtcCodes.any { activeDtcChecker(it) }
            KnownIssueRow(
                issue = issue,
                detected = anyActive,
                showTcvMonitor = issue.requiresTcvMonitor,
                tcvMonitor = tcvMonitor,
                onToggleTcv = onToggleTcv,
            )
            if (idx < vehicle.knownIssueIds.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(0.12f),
                )
            }
        }
    }
}

@Composable
private fun KnownIssueRow(
    issue: KnownIssue,
    detected: Boolean,
    showTcvMonitor: Boolean,
    tcvMonitor: TcvMonitorState,
    onToggleTcv: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = issue.severity.color.copy(0.15f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    issue.severity.name,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = issue.severity.color,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                issue.name.forLocale(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (detected) {
                Surface(
                    color = DarkError.copy(0.15f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text("DETECTED",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkError, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        AnimatedVisibility(visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit  = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                InfoSection("DTCs", issue.dtcCodes.joinToString(", "))
                InfoSection("Symptoms", issue.symptoms.forLocale())
                InfoSection("Fix", issue.fix.forLocale())
                if (showTcvMonitor) {
                    Spacer(Modifier.height(8.dp))
                    TcvMonitorSection(tcvMonitor, onToggleTcv)
                }
            }
        }
    }
}

// ── TCV Monitor ───────────────────────────────────────────────────────────────

@Composable
private fun TcvMonitorSection(state: TcvMonitorState, onToggle: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Thermostat, contentDescription = null,
                    tint = if (state.warning != TcvWarning.NONE) DarkWarning else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("TCV Coolant Monitor",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = onToggle,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp),
                ) {
                    Text(
                        if (state.isActive) "Stop" else "Monitor",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            if (state.isActive) {
                Spacer(Modifier.height(10.dp))
                if (state.history.size >= 2) {
                    CoolantGraph(history = state.history)
                    Spacer(Modifier.height(6.dp))
                    val lastTemp = state.history.lastOrNull()?.tempC
                    if (lastTemp != null) {
                        Row {
                            Text("Current: ${lastTemp.toInt()}°C",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (lastTemp >= 80f) DarkSuccess else DarkWarning)
                            state.warmupMinutes?.let {
                                Spacer(Modifier.width(12.dp))
                                Text("Warmup: ${"%.1f".format(it)} min",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (it <= 8f) DarkSuccess else DarkError)
                            }
                        }
                    }
                } else {
                    Text("Collecting data…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                }

                if (state.warning != TcvWarning.NONE) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = DarkWarning.copy(0.15f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null,
                                tint = DarkWarning, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(state.warningMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = DarkWarning)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoolantGraph(history: List<CoolantSample>) {
    val primary = DarkPrimary
    val warning = DarkWarning
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val temps = history.map { it.tempC }
            val minT = (temps.minOrNull() ?: 0f) - 5f
            val maxT = (temps.maxOrNull() ?: 100f) + 5f
            val range = (maxT - minT).coerceAtLeast(1f)
            val w = size.width
            val h = size.height

            // 80°C line
            val y80 = h - ((80f - minT) / range) * h
            if (y80 in 0f..h) {
                drawLine(
                    color = warning.copy(0.3f),
                    start = androidx.compose.ui.geometry.Offset(0f, y80),
                    end   = androidx.compose.ui.geometry.Offset(w, y80),
                    strokeWidth = 1.5f,
                )
            }

            // Temp path
            if (history.size >= 2) {
                val path = Path()
                history.forEachIndexed { i, s ->
                    val x = (i.toFloat() / (history.size - 1)) * w
                    val y = h - ((s.tempC - minT) / range) * h
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

// ── Diagnostics Card ──────────────────────────────────────────────────────────

@Composable
private fun DiagnosticsCard(
    dtcState: DtcScanState,
    onScan:  () -> Unit,
    onClear: () -> Unit,
    onReset: () -> Unit,
) {
    ServiceCard(title = "Fault Code Diagnostics", icon = Icons.Filled.Search) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onScan,
                enabled = dtcState !is DtcScanState.Scanning && dtcState !is DtcScanState.Clearing,
                colors  = ButtonDefaults.buttonColors(containerColor = DarkPrimary),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Scan System")
            }
            Button(
                onClick = onClear,
                enabled = dtcState !is DtcScanState.Scanning && dtcState !is DtcScanState.Clearing,
                colors  = ButtonDefaults.buttonColors(containerColor = DarkError),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Clear All")
            }
        }

        Spacer(Modifier.height(12.dp))

        AnimatedContent(
            targetState = dtcState,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "dtcState",
        ) { state ->
            when (state) {
                is DtcScanState.Idle -> {
                    Text(
                        "Press 'Scan System' to read fault codes from the ECU.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                    )
                }
                is DtcScanState.Scanning, is DtcScanState.Clearing -> {
                    val label = if (state is DtcScanState.Scanning) "Reading fault codes…" else "Clearing fault codes…"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                }
                is DtcScanState.NoCodes -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null,
                            tint = DarkSuccess, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("No fault codes found — system clear.",
                            style = MaterialTheme.typography.bodySmall, color = DarkSuccess)
                    }
                }
                is DtcScanState.Error -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null,
                            tint = DarkError, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(state.message, style = MaterialTheme.typography.bodySmall, color = DarkError)
                        }
                        IconButton(onClick = onReset, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }
                is DtcScanState.Results -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null,
                                tint = DarkError, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${state.codes.size} fault code(s) detected",
                                style = MaterialTheme.typography.labelMedium,
                                color = DarkError, fontWeight = FontWeight.SemiBold)
                        }
                        state.codes.forEach { result -> DtcResultCard(result) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DtcResultCard(result: DtcResult) {
    var expanded by remember { mutableStateOf(result.knownIssue != null) }

    Surface(
        color = if (result.knownIssue != null)
            DarkError.copy(0.06f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    result.code,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Bold,
                    color = if (result.knownIssue != null) DarkError else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    result.entry?.shortDescription?.forLocale() ?: "Unknown fault code",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface.copy(0.8f),
                )
                if (result.knownIssue != null || result.entry != null) {
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit  = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    result.entry?.let { entry ->
                        InfoSection("Description", entry.fullDescription.forLocale())
                        InfoSection("Possible Causes", entry.possibleCauses.forLocale())
                        InfoSection("How to Fix", entry.howToFix.forLocale())
                    }
                    result.knownIssue?.let { issue ->
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = issue.severity.color.copy(0.12f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Info, contentDescription = null,
                                        tint = issue.severity.color, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Known Issue: ${issue.name.forLocale()}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = issue.severity.color, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(6.dp))
                                InfoSection("Diagnosis", issue.diagnosticCheck.forLocale())
                                InfoSection("Repair", issue.fix.forLocale())
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Generic Procedure Card ────────────────────────────────────────────────────

@Composable
private fun ProcedureCard(
    title: String,
    description: String,
    icon: ImageVector,
    buttonLabel: String,
    buttonColor: Color,
    isActive: Boolean,
    procState: ProcedureState,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    ServiceCard(title = title, icon = icon) {
        Text(description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        Spacer(Modifier.height(12.dp))
        ProcedureStateView(
            procState    = procState,
            buttonLabel  = buttonLabel,
            buttonColor  = buttonColor,
            onStart      = onStart,
            onDismiss    = onDismiss,
        )
    }
}

// ── CVT Reset Card ────────────────────────────────────────────────────────────

@Composable
private fun CvtResetCard(
    conditions: CvtConditions,
    isActive: Boolean,
    procState: ProcedureState,
    onTogglePark: () -> Unit,
    onToggleAccessories: () -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    ServiceCard(title = "CVT Reset & Learning", icon = Icons.Filled.Refresh) {
        Text(
            "Resets CVT adaptive shift logic. Required after: CVT fluid change, valve body replacement, or solenoid replacement.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
        )
        Spacer(Modifier.height(12.dp))

        Text("Conditions", style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(0.55f))
        Spacer(Modifier.height(6.dp))

        // Auto-checked conditions
        ConditionRow("Engine warm (coolant ≥ 80°C)", conditions.engineWarm, auto = true)
        ConditionRow("Vehicle stationary (speed = 0)", conditions.stationary, auto = true)
        ConditionRow("Engine idling (600–900 RPM)", conditions.idling, auto = true)
        // Manual checkboxes
        ConditionRowCheckbox("Gear selector in P (Park)", conditions.inPark, onTogglePark)
        ConditionRowCheckbox("A/C and all accessories off", conditions.accessoriesOff, onToggleAccessories)

        Spacer(Modifier.height(12.dp))
        ProcedureStateView(
            procState   = procState,
            buttonLabel = "Initiate CVT Reset",
            buttonColor = if (conditions.allPassed) DarkPrimary else MaterialTheme.colorScheme.outline,
            onStart     = onStart,
            onDismiss   = onDismiss,
        )
    }
}

@Composable
private fun ConditionRow(label: String, passed: Boolean, auto: Boolean) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (passed) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = if (passed) DarkSuccess else MaterialTheme.colorScheme.onSurface.copy(0.35f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (auto) {
            Text("AUTO", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(0.3f))
        }
    }
}

@Composable
private fun ConditionRowCheckbox(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

// ── Procedure state widget ────────────────────────────────────────────────────

@Composable
private fun ProcedureStateView(
    procState: ProcedureState,
    buttonLabel: String,
    buttonColor: Color,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (procState) {
        is ProcedureState.Idle -> {
            Button(
                onClick = onStart,
                colors  = ButtonDefaults.buttonColors(containerColor = buttonColor),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(buttonLabel)
            }
        }
        is ProcedureState.Running -> {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(procState.label, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { procState.step.toFloat() / procState.total },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                )
                Text("Step ${procState.step} of ${procState.total}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
        is ProcedureState.Success -> {
            Surface(color = DarkSuccess.copy(0.12f), shape = RoundedCornerShape(10.dp)) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null,
                        tint = DarkSuccess, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(procState.message, style = MaterialTheme.typography.bodySmall,
                        color = DarkSuccess, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss",
                            tint = DarkSuccess.copy(0.6f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        is ProcedureState.Error -> {
            Surface(color = DarkError.copy(0.10f), shape = RoundedCornerShape(10.dp)) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null,
                        tint = DarkError, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(procState.message, style = MaterialTheme.typography.bodySmall,
                        color = DarkError, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss",
                            tint = DarkError.copy(0.6f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// ── Shared composables ────────────────────────────────────────────────────────

@Composable
private fun ServiceCard(
    title: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoSection(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

// ── Maintenance Log Card ──────────────────────────────────────────────────────

@Composable
private fun MaintenanceLogCard(
    events: List<ServiceEvent>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    ServiceCard(title = "Maintenance Log", icon = Icons.Filled.History) {
        if (events.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "No service records yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onAdd,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${events.size} record${if (events.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = onAdd,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", style = MaterialTheme.typography.labelMedium)
                }
            }

            AnimatedVisibility(visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit  = shrinkVertically() + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val shown = events.take(5)
                    shown.forEachIndexed { idx, event ->
                        ServiceEventRow(event = event, onRemove = { onRemove(event.id) })
                        if (idx < shown.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(0.1f))
                        }
                    }
                    if (events.size > 5) {
                        Text(
                            "+ ${events.size - 5} more records",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceEventRow(event: ServiceEvent, onRemove: () -> Unit) {
    val statusColor = when {
        event.isOverdue() -> DarkError
        event.isDueSoon() -> DarkWarning
        else -> MaterialTheme.colorScheme.onSurface.copy(0.6f)
    }

    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            event.type.icon,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(event.type.displayName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Row {
                Text(
                    event.daysAgoLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                )
                event.mileageKm?.let {
                    Text(
                        " · ${"%,d".format(it)} km",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                    )
                }
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// ── Add Service Dialog ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddServiceDialog(
    onConfirm: (ServiceEventType, Int?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedType by remember { mutableStateOf(ServiceEventType.OIL_CHANGE) }
    var mileageText  by remember { mutableStateOf("") }
    var notes        by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Service Event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Service type selection
                Text("Service Type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.55f))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement   = Arrangement.spacedBy(4.dp),
                ) {
                    ServiceEventType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick  = { selectedType = type },
                            label    = {
                                Text(type.displayName,
                                    style = MaterialTheme.typography.labelSmall)
                            },
                            leadingIcon = {
                                Icon(type.icon, contentDescription = null,
                                    modifier = Modifier.size(12.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DarkPrimary.copy(0.18f),
                                selectedLabelColor     = DarkPrimary,
                                selectedLeadingIconColor = DarkPrimary,
                            ),
                        )
                    }
                }

                // Mileage (optional)
                OutlinedTextField(
                    value = mileageText,
                    onValueChange = { mileageText = it.filter { c -> c.isDigit() } },
                    label    = { Text("Odometer (km, optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier   = Modifier.fillMaxWidth(),
                )

                // Notes (optional)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label    = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier   = Modifier.fillMaxWidth(),
                )

                // Interval hint
                selectedType.intervalHint?.let { hint ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                            modifier = Modifier.padding(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(selectedType, mileageText.toIntOrNull(), notes.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── Extension ─────────────────────────────────────────────────────────────────

private val IssueSeverity.color: Color
    get() = when (this) {
        IssueSeverity.CRITICAL -> DarkError
        IssueSeverity.HIGH     -> DarkWarning
        IssueSeverity.MEDIUM   -> Color(0xFF5B9BD5)
    }

private val ServiceEventType.icon: ImageVector
    get() = when (this) {
        ServiceEventType.OIL_CHANGE    -> Icons.Filled.WaterDrop
        ServiceEventType.CVT_FLUID     -> Icons.Filled.Settings
        ServiceEventType.BRAKE_FLUID   -> Icons.Filled.Speed
        ServiceEventType.COOLANT       -> Icons.Filled.Thermostat
        ServiceEventType.SPARK_PLUGS   -> Icons.Filled.ElectricBolt
        ServiceEventType.AIR_FILTER    -> Icons.Filled.Air
        ServiceEventType.TIRE_ROTATION -> Icons.Filled.DirectionsCar
        ServiceEventType.OTHER         -> Icons.Filled.Build
    }

private val ServiceEventType.intervalHint: String?
    get() {
        val parts = buildList {
            intervalKm?.let  { add("Every %,d km".format(it)) }
            intervalDays?.let { add("every ${it / 30} months") }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" / ")
    }

// ── ECU Relearn Dialog ────────────────────────────────────────────────────────

@Composable
private fun EcuRelearnDialog(onDismiss: () -> Unit) {
    val msg = com.subaru.servicetool.data.model.LocalizedText(
        en = "The ECU has cleared its stored fault codes. The vehicle will now begin an adaptive relearn process. During the first ~50 km of driving, idle quality, fuel trim, and shift behaviour may be slightly irregular. This is normal. Drive gently for the first drive cycle to allow all systems to relearn their baseline parameters.",
        ka = "ECU-მ წაშალა შენახული გაუმართაობის კოდები. მანქანა ახლა დაიწყებს ადაპტური ხელახალი სწავლების პროცესს. პირველი ~50 კმ-ის განმავლობაში ნეიტრალური სვლა, საწვავის ტრიმი და გადართვის ქცევა შეიძლება ოდნავ არარეგულარული იყოს. ეს ნორმალურია. პირველი სამგზავრო ციკლისთვის ნელა იმართეთ, რათა ყველა სისტემამ ხელახლა ისწავლოს საბაზისო პარამეტრები.",
        ru = "ЭБУ очистил сохранённые коды неисправностей. Автомобиль начнёт процесс адаптивного обучения. В первые ~50 км качество холостого хода, коррекция топлива и поведение при переключениях могут быть немного нестабильными — это нормально. Двигайтесь плавно, чтобы все системы смогли заново калиброваться.",
        es = "La ECU ha borrado los códigos de falla almacenados. El vehículo iniciará un proceso de reaprendizaje adaptativo. Durante los primeros ~50 km, la ralentí, el ajuste de combustible y el comportamiento de cambio pueden ser ligeramente irregulares. Es normal. Conduzca suavemente para que todos los sistemas recalibren sus parámetros base.",
        fr = "L'ECU a effacé les codes de défaut mémorisés. Le véhicule va maintenant démarrer un processus d'apprentissage adaptatif. Pendant les premiers ~50 km, la qualité du ralenti, la correction de carburant et le comportement de passage peuvent être légèrement irréguliers. C'est normal. Conduisez doucement pour permettre à tous les systèmes de recalibrer leurs paramètres de base.",
        de = "Die ECU hat die gespeicherten Fehlercodes gelöscht. Das Fahrzeug startet nun einen adaptiven Lernprozess. Während der ersten ~50 km können Leerlaufqualität, Kraftstoffanpassung und Schaltverhalten leicht unregelmäßig sein. Das ist normal. Fahren Sie behutsam, damit alle Systeme ihre Basisparameter neu kalibrieren können.",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Info, contentDescription = null, tint = DarkPrimary) },
        title = { Text("ECU Relearn Required") },
        text  = { Text(msg.forLocale(), style = MaterialTheme.typography.bodySmall) },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary)) {
                Text("Understood")
            }
        },
    )
}

// ── CVT Relearn guided gear cycle ─────────────────────────────────────────────

@Composable
private fun CvtRelearnStepDialog(step: Int, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val steps = listOf(
        "Move gear selector to D (Drive)" to "Select Drive — then tap Done",
        "Move gear selector to R (Reverse)" to "Select Reverse — then tap Done",
        "Move gear selector to N (Neutral)" to "Select Neutral — then tap Done",
        "Turn ignition OFF for 10 seconds, then back ON" to "Key off → wait 10s → key on",
    )
    val (title, sub) = steps.getOrNull(step) ?: return

    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = DarkPrimary) },
        title = {
            Column {
                Text("CVT Relearn — Step ${step + 1} of ${steps.size}",
                    style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(
                    progress = { (step + 1).toFloat() / steps.size },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        text = {
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(sub, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary)) {
                Text(if (step < 3) "Done" else "Finish")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}

// ── Recommendations ───────────────────────────────────────────────────────────

private data class Recommendation(val title: com.subaru.servicetool.data.model.LocalizedText, val body: com.subaru.servicetool.data.model.LocalizedText)

private val RECOMMENDATIONS: List<Recommendation> = listOf(
    Recommendation(
        title = com.subaru.servicetool.data.model.LocalizedText(en = "Change CVT Fluid Every 40,000 km", ka = "CVT-ის სითხის შეცვლა ყოველ 40,000 კმ-ზე"),
        body  = com.subaru.servicetool.data.model.LocalizedText(en = "Subaru recommends CVT fluid every 40,000 km for severe duty. Despite the 'lifetime fluid' label, degraded fluid causes solenoid wear, slip, and overheating. Use only Subaru-approved Lineartronic CVT fluid (Part: SOA427V1410).", ka = "Subaru გირჩევთ CVT-ის სითხის შეცვლას ყოველ 40,000 კმ-ზე მძიმე სამუშაო პირობებისთვის. ინარჩუნებდე Subaru-ს დამტკიცებული Lineartronic CVT სითხე."),
    ),
    Recommendation(
        title = com.subaru.servicetool.data.model.LocalizedText(en = "Check and Reset TPMS After Tire Swap", ka = "TPMS-ის შემოწმება და გადაყენება საბურავის შეცვლის შემდეგ"),
        body  = com.subaru.servicetool.data.model.LocalizedText(en = "After rotating or replacing tires, recalibrate the TPMS system: park the car, start the engine, then hold the TPMS button under the steering wheel until the light blinks twice. Drive over 50 km/h for 10 minutes to complete calibration.", ka = "საბურავის როტაციის ან შეცვლის შემდეგ, გადაყენეთ TPMS სისტემა: გააჩერეთ მანქანა, ჩართეთ ძრავა, შემდეგ დაიჭირეთ TPMS ღილაკი საჭის ქვეშ, სანამ შუქი ორჯერ ციმციმებს."),
    ),
    Recommendation(
        title = com.subaru.servicetool.data.model.LocalizedText(en = "EyeSight Camera Windshield Cleaning", ka = "EyeSight კამერის მინდვრის გაწმენდა"),
        body  = com.subaru.servicetool.data.model.LocalizedText(en = "The EyeSight stereo cameras sit behind the rearview mirror. Keep the windshield clean in that area — use a microfiber cloth and glass cleaner, never ammonia-based products. Contamination causes false alerts and disables the system.", ka = "EyeSight სტერეო კამერები მდებარეობს უკანა სარკის უკან. შეინარჩუნეთ მინდვრი სუფთა — გამოიყენეთ მიკროფიბრის ქსოვილი, არ გამოიყენოთ ამიაკის შემცველი პროდუქტები."),
    ),
    Recommendation(
        title = com.subaru.servicetool.data.model.LocalizedText(en = "AWD System: Check Tire Circumference Match", ka = "AWD სისტემა: საბურავის გარშემოწერილობის შესაბამისობის შემოწმება"),
        body  = com.subaru.servicetool.data.model.LocalizedText(en = "Subaru's symmetrical AWD is sensitive to mismatched tire sizes. Tires must be within 1/16 inch (1.6 mm) circumference of each other across all four. Replacing only one or two tires can damage the AWD center coupling and is not covered by warranty.", ka = "Subaru-ს სიმეტრიული AWD მგრძნობიარეა საბურავის ზომის შეუთავსებლობის მიმართ. ყველა ოთხი საბურავი უნდა იყოს 1.6 მმ-ის ფარგლებში ერთმანეთთან."),
    ),
    Recommendation(
        title = com.subaru.servicetool.data.model.LocalizedText(en = "Spark Plugs: Use Iridium, Not Copper", ka = "სანთლები: გამოიყენეთ ირიდიუმი, არ სპილენძი"),
        body  = com.subaru.servicetool.data.model.LocalizedText(en = "Subaru FA/FB engines require iridium spark plugs. Copper plugs may cause misfires and ECU relearn issues. OEM spec: NGK ILKAR7L11 (FA20/FB20) or DILFR6J11 (FA24). Change every 60,000 km or earlier if rough idle appears.", ka = "Subaru FA/FB ძრავები საჭიროებს ირიდიუმის სანთლებს. OEM სპეციფ.: NGK ILKAR7L11 (FA20/FB20) ან DILFR6J11 (FA24). შეცვალეთ ყოველ 60,000 კმ-ზე."),
    ),
    Recommendation(
        title = com.subaru.servicetool.data.model.LocalizedText(en = "Coolant: Use Subaru Super Coolant (Blue)", ka = "გამაგრილებელი: გამოიყენეთ Subaru Super Coolant (ლურჯი)"),
        body  = com.subaru.servicetool.data.model.LocalizedText(en = "Subaru requires its own Super Coolant (blue). Standard green antifreeze is not compatible and may damage aluminum engine components over time. Change interval: 135,000 km or 11 years, whichever comes first. Part: SOA868V9210.", ka = "Subaru საჭიროებს საკუთარ Super Coolant-ს (ლურჯი). სტანდარტული მწვანე ანტიფრიზი არ არის თავსებადი. შეცვლის ინტერვალი: 135,000 კმ ან 11 წელი."),
    ),
    Recommendation(
        title = com.subaru.servicetool.data.model.LocalizedText(en = "Throttle Body Cleaning Every 60,000 km", ka = "სამხრეთის ნაწილის გაწმენდა ყოველ 60,000 კმ-ზე"),
        body  = com.subaru.servicetool.data.model.LocalizedText(en = "Carbon buildup on the throttle plate causes rough idle and throttle response issues. Clean with a throttle body cleaner and a soft cloth. After cleaning, always perform a Throttle Body Relearn procedure (available in this app) to reset the position baseline.", ka = "ნახშირბადის დაგროვება სამხრეთის ფირფიტაზე იწვევს ნეიტრალური სვლის პრობლემებს. გაწმენდის შემდეგ, ჩაუტარეთ Throttle Body Relearn პროცედურა ამ აპლიკაციაში."),
    ),
    Recommendation(
        title = com.subaru.servicetool.data.model.LocalizedText(en = "Transmission Oil Pan Magnet Check", ka = "გადამცემის ზეთის ვარდსაკიდის მაგნიტის შემოწმება"),
        body  = com.subaru.servicetool.data.model.LocalizedText(en = "When draining CVT fluid, always inspect the drain plug magnet. A thin metallic film is normal (fine particles). Metallic chunks or shavings indicate internal wear — do not refill until you diagnose the source. This can save a CVT replacement.", ka = "CVT-ის სითხის ამოსხმისას, ყოველთვის შეამოწმეთ სარქვლის მაგნიტი. თხელი ლითონის ფენა ნორმალურია. ლითონის ნაჭრები მიუთითებს შიდა ცვეთაზე."),
    ),
    Recommendation(
        title = com.subaru.servicetool.data.model.LocalizedText(en = "Oil Change: Use 0W-20 Full Synthetic Only", ka = "ზეთის შეცვლა: გამოიყენეთ 0W-20 სრულ სინთეტიკა"),
        body  = com.subaru.servicetool.data.model.LocalizedText(en = "All FB/FA series engines require 0W-20 full synthetic oil. Using heavier viscosity oil (5W-30, 5W-40) increases oil pressure and can mask leaks. It also reduces fuel economy. Change every 6,000–8,000 km on Subaru's FB-series engines.", ka = "ყველა FB/FA სერიის ძრავა საჭიროებს 0W-20 სრულ სინთეტიკური ზეთს. შეცვალეთ ყოველ 6,000–8,000 კმ-ზე."),
    ),
    Recommendation(
        title = com.subaru.servicetool.data.model.LocalizedText(en = "Brake Fluid: Change Every 2 Years", ka = "სამუხრუჭე სითხე: შეცვლა ყოველ 2 წელიწადში"),
        body  = com.subaru.servicetool.data.model.LocalizedText(en = "Brake fluid is hygroscopic — it absorbs moisture over time, lowering its boiling point. Subaru recommends DOT 3 or DOT 4 fluid replacement every 2 years regardless of mileage. Degraded fluid can cause vapor lock and brake fade during hard stops.", ka = "სამუხრუჭე სითხე შთანთქავს ტენს და შეამცირებს დუღილის წერტილს. Subaru გირჩევს DOT 3 ან DOT 4 სითხის შეცვლას ყოველ 2 წელიწადში, მაშინაც კი, თუ გარბენი მცირეა."),
    ),
)

@Composable
private fun RecommendationsCard() {
    ServiceCard(title = "Maintenance Tips", icon = Icons.Filled.Info, iconTint = DarkPrimary) {
        Text(
            "Subaru-specific maintenance advice",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
        )
        Spacer(Modifier.height(10.dp))
        RECOMMENDATIONS.forEachIndexed { idx, rec ->
            RecommendationRow(rec)
            if (idx < RECOMMENDATIONS.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outline.copy(0.1f),
                )
            }
        }
    }
}

@Composable
private fun RecommendationRow(rec: Recommendation) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                rec.title.forLocale(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit  = shrinkVertically() + fadeOut(),
        ) {
            Text(
                rec.body.forLocale(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
            )
        }
    }
}

private val ServiceEvent.daysAgoLabel: String
    get() = when (val d = daysSince) {
        0L   -> "Today"
        1L   -> "Yesterday"
        in 2..13  -> "$d days ago"
        in 14..59 -> "${d / 7} weeks ago"
        in 60..364 -> "${d / 30} months ago"
        else -> "${d / 365} year${if (d >= 730) "s" else ""} ago"
    }
