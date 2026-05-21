package com.subaru.servicetool.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.stringResource
import com.subaru.servicetool.R
import com.subaru.servicetool.data.bluetooth.BluetoothConnectionState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.subaru.servicetool.data.model.IssueSeverity
import com.subaru.servicetool.data.model.KnownIssue
import com.subaru.servicetool.data.model.LocalizedText
import com.subaru.servicetool.data.model.VehicleSpec
import com.subaru.servicetool.data.model.forLocale
import com.subaru.servicetool.data.service.ServiceEvent
import com.subaru.servicetool.data.service.ServiceEventType
import com.subaru.servicetool.ui.service.ActiveProcedure
import com.subaru.servicetool.ui.service.DtcResult
import com.subaru.servicetool.ui.service.DtcScanState
import com.subaru.servicetool.ui.service.IssueCheckState
import com.subaru.servicetool.ui.service.ProcedureState
import com.subaru.servicetool.ui.service.ServiceViewModel
import com.subaru.servicetool.ui.theme.DarkError
import com.subaru.servicetool.ui.theme.DarkPrimary
import com.subaru.servicetool.ui.theme.DarkSuccess
import com.subaru.servicetool.ui.theme.DarkWarning

@Composable
fun ServiceScreen(
    paddingValues: PaddingValues = PaddingValues(),
    viewModel: ServiceViewModel = hiltViewModel(),
) {
    val vehicle           by viewModel.selectedVehicle.collectAsState()
    val dtcState          by viewModel.dtcScanState.collectAsState()
    val procState         by viewModel.procedureState.collectAsState()
    val activeProc        by viewModel.activeProcedure.collectAsState()
    val issueCheckStates  by viewModel.issueCheckStates.collectAsState()
    val connectionState   by viewModel.connectionState.collectAsState()
    val isConnected       = connectionState is BluetoothConnectionState.Connected
    val showConfirm       by viewModel.showClearConfirm.collectAsState()
    val showEcuRelearn    by viewModel.showEcuRelearnDialog.collectAsState()
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

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearConfirm,
            title = { Text("Clear All Fault Codes?") },
            text  = { Text("This will erase all stored DTCs from every module (ECM, TCM, ABS, SRS, BCM). This action cannot be undone.") },
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

        // ── Card 1: Diagnostics ────────────────────────────────────────────
        item {
            DiagnosticsCard(
                dtcState   = dtcState,
                onScan     = viewModel::scanDtcs,
                onClear    = viewModel::requestClearDtcs,
                onReset    = viewModel::resetDtcState,
            )
        }

        // ── Vehicle Health (Known Issues) ─────────────────────────────────
        vehicle?.let { v ->
            if (v.knownIssueIds.isNotEmpty()) {
                item {
                    VehicleHealthCard(
                        vehicle            = v,
                        issueCheckStates   = issueCheckStates,
                        activeDtcChecker   = viewModel::isDtcActive,
                        isConnected        = isConnected,
                        onCheckLiveStatus  = viewModel::scanDtcs,
                        onStartIssueCheck  = viewModel::startIssueCheck,
                        onResetIssueCheck  = viewModel::resetIssueCheck,
                    )
                }
            }
        }

        // ── Global ECU Error Sweep & Reset ────────────────────────────────
        item {
            ProcedureCard(
                title        = "Global ECU Error Sweep & Reset",
                description  = "Sequentially targets all 5 Subaru modules (ECM, TCM, ABS, SRS, BCM) and issues Mode 04 + UDS 14FFFFFF clear commands with a 150 ms EEPROM settling delay between each module.",
                icon         = Icons.Filled.Settings,
                buttonLabel  = "Run Global Reset",
                buttonColor  = DarkPrimary,
                isActive     = activeProc == ActiveProcedure.GLOBAL_SWEEP,
                procState    = if (activeProc == ActiveProcedure.GLOBAL_SWEEP || procState is ProcedureState.Success) procState else ProcedureState.Idle,
                onStart      = viewModel::startGlobalEcuSweep,
                onDismiss    = viewModel::resetProcedure,
            )
        }

        // ── Maintenance Tips ───────────────────────────────────────────────
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
    issueCheckStates: Map<String, IssueCheckState>,
    activeDtcChecker: (String) -> Boolean,
    isConnected: Boolean,
    onCheckLiveStatus: () -> Unit,
    onStartIssueCheck: (com.subaru.servicetool.data.model.KnownIssue) -> Unit,
    onResetIssueCheck: (String) -> Unit,
) {
    ServiceCard(title = "Known Issues for This Vehicle", icon = Icons.Filled.Warning, iconTint = DarkWarning) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${vehicle.year} Subaru ${vehicle.modelName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                )
                if (vehicle.generationBadge.isNotEmpty()) {
                    Surface(
                        color = DarkPrimary.copy(0.12f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(top = 3.dp),
                    ) {
                        Text(
                            vehicle.generationBadge + " Generation",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Button(
                onClick = onCheckLiveStatus,
                enabled = isConnected,
                colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Check Live Status", style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(10.dp))

        vehicle.knownIssueIds.forEachIndexed { idx, issueId ->
            val issue = com.subaru.servicetool.data.model.KnownIssueRegistry.findById(issueId) ?: return@forEachIndexed
            val anyActive = issue.dtcCodes.any { activeDtcChecker(it) }
            KnownIssueRow(
                issue        = issue,
                detected     = anyActive,
                checkState   = issueCheckStates[issue.id] ?: IssueCheckState.Idle,
                isConnected  = isConnected,
                onStartCheck = { onStartIssueCheck(issue) },
                onResetCheck = { onResetIssueCheck(issue.id) },
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
    checkState: IssueCheckState,
    isConnected: Boolean,
    onStartCheck: () -> Unit,
    onResetCheck: () -> Unit,
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
                if (issue.checkHeader != null) {
                    Spacer(Modifier.height(8.dp))
                    IssueCheckSection(
                        state       = checkState,
                        isConnected = isConnected,
                        onStart     = onStartCheck,
                        onReset     = onResetCheck,
                    )
                }
            }
        }
    }
}

// ── Issue Check Section ───────────────────────────────────────────────────────

@Composable
private fun IssueCheckSection(
    state: IssueCheckState,
    isConnected: Boolean,
    onStart: () -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Search, contentDescription = null,
                    tint = when (state) {
                        is IssueCheckState.FaultFound -> DarkError
                        is IssueCheckState.Ok         -> DarkSuccess
                        else                          -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Active Fault Code Check",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            when (state) {
                is IssueCheckState.Idle -> {
                    Button(
                        onClick  = onStart,
                        enabled  = isConnected,
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = DarkPrimary),
                        shape    = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start Check", style = MaterialTheme.typography.labelMedium)
                    }
                    if (!isConnected) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Connect OBD adapter to run diagnostic check",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                        )
                    }
                }
                is IssueCheckState.Scanning -> {
                    Text(
                        "Querying module for fault codes…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                }
                is IssueCheckState.FaultFound -> {
                    Surface(
                        color    = DarkError.copy(0.12f),
                        shape    = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, contentDescription = null,
                                    tint = DarkError, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("⚠ Fault Code(s) Detected",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold, color = DarkError)
                            }
                            Spacer(Modifier.height(8.dp))
                            state.codes.forEach { code ->
                                Text(code,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.85f),
                                    modifier = Modifier.padding(bottom = 2.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)) {
                        Text("Check Again", style = MaterialTheme.typography.labelMedium)
                    }
                }
                is IssueCheckState.Ok -> {
                    Surface(
                        color    = DarkSuccess.copy(0.12f),
                        shape    = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null,
                                tint = DarkSuccess, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("✓ No fault codes detected",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold, color = DarkSuccess)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)) {
                        Text("Check Again", style = MaterialTheme.typography.labelMedium)
                    }
                }
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
                is DtcScanState.Scanning -> {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                    }
                }
                is DtcScanState.Clearing -> {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
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

// ── Recommendations ───────────────────────────────────────────────────────────

private val TIP_RES_IDS: List<Int> = listOf(
    R.string.tip_1,
    R.string.tip_2,
    R.string.tip_3,
    R.string.tip_4,
    R.string.tip_5,
    R.string.tip_6,
    R.string.tip_7,
    R.string.tip_8,
    R.string.tip_9,
    R.string.tip_10,
)

@Composable
private fun RecommendationsCard() {
    ServiceCard(title = stringResource(R.string.recommendations_title), icon = Icons.Filled.Info, iconTint = DarkPrimary) {
        Text(
            "Subaru-specific maintenance advice",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
        )
        Spacer(Modifier.height(10.dp))
        TIP_RES_IDS.forEachIndexed { idx, textRes ->
            RecommendationRow(text = stringResource(textRes))
            if (idx < TIP_RES_IDS.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outline.copy(0.1f),
                )
            }
        }
    }
}

@Composable
private fun RecommendationRow(text: String) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
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
