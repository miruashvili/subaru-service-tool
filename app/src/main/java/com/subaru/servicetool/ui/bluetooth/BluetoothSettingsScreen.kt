package com.subaru.servicetool.ui.bluetooth

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhonelinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.subaru.servicetool.data.bluetooth.BluetoothConnectionState
import com.subaru.servicetool.data.bluetooth.OBDConnectionType
import com.subaru.servicetool.ui.theme.DarkError
import com.subaru.servicetool.ui.theme.DarkPrimary
import com.subaru.servicetool.ui.theme.DarkSuccess
import com.subaru.servicetool.ui.theme.DarkWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothSettingsScreen(
    onBack: () -> Unit,
    viewModel: BluetoothSettingsViewModel = hiltViewModel(),
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val connectedMac = viewModel.connectedMac()
    val context = LocalContext.current

    var permissionsGranted by remember { mutableStateOf(false) }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        permissionPermanentlyDenied = !permissionsGranted
        if (permissionsGranted) viewModel.refreshPairedDevices()
    }

    LaunchedEffect(Unit) {
        val perms = viewModel.requiredPermissions()
        permissionLauncher.launch(perms)
    }

    val bluetoothEnabled = viewModel.isBluetoothEnabled()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Connection status banner ─────────────────────────────────────
            item {
                ConnectionStatusBanner(connectionState) { viewModel.disconnect() }
                Spacer(Modifier.height(8.dp))
            }

            // ── Edge-case banners ────────────────────────────────────────────
            if (!bluetoothEnabled) {
                item {
                    EdgeCaseBanner(
                        icon = Icons.Filled.BluetoothDisabled,
                        title = "Bluetooth is turned off",
                        subtitle = "Enable Bluetooth to connect your OBD adapter",
                        actionLabel = "Open Settings",
                        onAction = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (permissionPermanentlyDenied) {
                item {
                    EdgeCaseBanner(
                        icon = Icons.Filled.Lock,
                        title = "Bluetooth permission required",
                        subtitle = "This app needs Bluetooth access to communicate with your OBD adapter. Grant it in App Settings.",
                        actionLabel = "Open App Settings",
                        onAction = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            })
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── Paired devices ───────────────────────────────────────────────
            item {
                SectionHeader("PAIRED DEVICES")
                Spacer(Modifier.height(6.dp))
            }

            if (pairedDevices.isEmpty() && bluetoothEnabled && permissionsGranted) {
                item {
                    EdgeCaseBanner(
                        icon = Icons.Filled.PhonelinkOff,
                        title = "No OBD adapters paired",
                        subtitle = "Open Android Bluetooth Settings to pair your OBD adapter first.",
                        actionLabel = "Open Bluetooth Settings",
                        onAction = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    )
                }
            } else if (pairedDevices.isEmpty()) {
                item { EmptyHint("No paired Bluetooth devices found") }
            } else {
                items(pairedDevices, key = { it.address }) { item ->
                    DeviceRow(
                        item = item,
                        isConnected = item.address == connectedMac,
                        isConnecting = item.address == viewModel.connectingAddress,
                        onConnect = { viewModel.connect(item) },
                        onDisconnect = { viewModel.disconnect() },
                    )
                }
            }

            // ── Scan for nearby BLE devices ──────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SectionHeader("NEARBY BLE DEVICES")
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
                Spacer(Modifier.height(6.dp))

                if (!isScanning) {
                    Button(
                        onClick = { viewModel.startScan() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary),
                        enabled = permissionsGranted && viewModel.isBluetoothEnabled(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scan for Devices", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.stopScan() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Stop Scanning", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (scanResults.isNotEmpty()) {
                items(scanResults, key = { "scan_${it.address}" }) { item ->
                    DeviceRow(
                        item = item,
                        isConnected = item.address == connectedMac,
                        isConnecting = item.address == viewModel.connectingAddress,
                        onConnect = { viewModel.connect(item) },
                        onDisconnect = { viewModel.disconnect() },
                    )
                }
            } else if (isScanning) {
                item { EmptyHint("Scanning for OBD adapters…") }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ── Connection status banner ──────────────────────────────────────────────────

@Composable
private fun ConnectionStatusBanner(
    state: BluetoothConnectionState,
    onDisconnect: () -> Unit,
) {
    val (bg, label, showDisconnect) = when (state) {
        is BluetoothConnectionState.Connected ->
            Triple(DarkSuccess.copy(0.12f), "Connected · ${state.deviceName}", true)
        is BluetoothConnectionState.Connecting ->
            Triple(DarkWarning.copy(0.12f), "Connecting…", false)
        is BluetoothConnectionState.Reconnecting ->
            Triple(DarkWarning.copy(0.12f), "Reconnecting…", false)
        is BluetoothConnectionState.Error ->
            Triple(DarkError.copy(0.12f), "Error: ${state.message}", false)
        is BluetoothConnectionState.Disconnected ->
            Triple(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f), "Not connected", false)
    }

    Surface(color = bg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = when (state) {
                    is BluetoothConnectionState.Connected -> DarkSuccess
                    is BluetoothConnectionState.Connecting,
                    is BluetoothConnectionState.Reconnecting -> DarkWarning
                    is BluetoothConnectionState.Error -> DarkError
                    else -> MaterialTheme.colorScheme.onSurface.copy(0.4f)
                },
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (state is BluetoothConnectionState.Connecting ||
                state is BluetoothConnectionState.Reconnecting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            if (showDisconnect) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onDisconnect,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Disconnect", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// ── Device row ────────────────────────────────────────────────────────────────

@Composable
private fun DeviceRow(
    item: BtDeviceItem,
    isConnected: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (isConnected) 4.dp else 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = item.address,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                )
            }

            // Type badge
            TypeBadge(item.connectionType)

            Spacer(Modifier.width(8.dp))

            // Connect / disconnect button
            if (isConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text("Disconnect", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = !isConnecting,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary),
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Connect", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ── Type badge ────────────────────────────────────────────────────────────────

@Composable
private fun TypeBadge(type: OBDConnectionType) {
    val (label, color) = when (type) {
        OBDConnectionType.BLE -> "BLE" to DarkPrimary
        OBDConnectionType.SPP -> "SPP" to DarkWarning
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
    }
}

@Composable
private fun EdgeCaseBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.6f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.65f))
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
