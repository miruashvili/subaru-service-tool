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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.subaru.servicetool.data.model.IssueSeverity
import com.subaru.servicetool.data.model.KnownIssue
import com.subaru.servicetool.data.model.VehicleSpec
import com.subaru.servicetool.data.model.forLocale
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
    val vehicle    by viewModel.selectedVehicle.collectAsState()
    val dtcState   by viewModel.dtcScanState.collectAsState()
    val procState  by viewModel.procedureState.collectAsState()
    val activeProc by viewModel.activeProcedure.collectAsState()
    val cvtConds   by viewModel.cvtConditions.collectAsState()
    val tcvMon     by viewModel.tcvMonitor.collectAsState()
    val showConfirm by viewModel.showClearConfirm.collectAsState()

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

// ── Extension ─────────────────────────────────────────────────────────────────

private val IssueSeverity.color: Color
    get() = when (this) {
        IssueSeverity.CRITICAL -> DarkError
        IssueSeverity.HIGH     -> DarkWarning
        IssueSeverity.MEDIUM   -> Color(0xFF5B9BD5)
    }
