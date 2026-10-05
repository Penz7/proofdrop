package com.penz7.proofdrop.feature.checkout

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Battery3Bar
import androidx.compose.material.icons.outlined.ElectricScooter
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.penz7.proofdrop.core.designsystem.component.PermissionGate
import com.penz7.proofdrop.core.designsystem.component.StatusChip
import com.penz7.proofdrop.core.designsystem.theme.DangerRed
import com.penz7.proofdrop.core.designsystem.theme.InfoBlue
import com.penz7.proofdrop.core.designsystem.theme.SuccessGreen
import com.penz7.proofdrop.core.model.Device
import com.penz7.proofdrop.core.model.DeviceType
import kotlinx.serialization.Serializable

@Serializable data object DevicesDestination

fun NavGraphBuilder.checkoutScreens() {
    composable<DevicesDestination> { DevicesRoute() }
}

@Composable
internal fun DevicesRoute(viewModel: DevicesViewModel = hiltViewModel()) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    if (scanning) {
        BackHandler(onBack = viewModel::stopScan)
        Box(Modifier.fillMaxSize()) {
            PermissionGate(
                required = listOf(Manifest.permission.CAMERA),
                rationale = "Camera access is needed to scan the QR sticker on a device.",
            ) {
                QrScanner(onCode = viewModel::onCodeScanned, modifier = Modifier.fillMaxSize())
            }
            IconButton(onClick = viewModel::stopScan, modifier = Modifier.align(Alignment.TopEnd).padding(24.dp)) {
                Icon(Icons.Filled.Close, "Close scanner")
            }
        }
    } else {
        DevicesScreen(devices, viewModel.myId, snackbar, onScan = viewModel::startScan, onToggle = viewModel::toggle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicesScreen(
    devices: List<Device>,
    myId: String?,
    snackbar: SnackbarHostState,
    onScan: () -> Unit,
    onToggle: (String) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Device checkout") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Filled.QrCodeScanner, null) },
                text = { Text("Scan QR") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val mine = devices.count { it.holderId != null && it.holderId == myId }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "You hold $mine device(s). Scan a device's QR sticker to check it out or return it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            items(devices, key = { it.id }) { DeviceCard(it, mine = it.holderId != null && it.holderId == myId, onToggle) }
        }
    }
}

@Composable
private fun DeviceCard(device: Device, mine: Boolean, onToggle: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(device.type.icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(device.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("${device.id} · S/N ${device.serial}", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Battery3Bar, null, Modifier.size(14.dp), tint = if (device.batteryPct < 20) DangerRed else MaterialTheme.colorScheme.secondary)
                    Text("${device.batteryPct}%  ", style = MaterialTheme.typography.labelSmall)
                    when {
                        mine -> StatusChip("With you", SuccessGreen)
                        device.isAvailable -> StatusChip("Available", InfoBlue)
                        else -> StatusChip("With ${device.holderName}", MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            when {
                mine -> OutlinedButton(onClick = { onToggle(device.id) }) { Text("Return") }
                device.isAvailable -> FilledTonalButton(onClick = { onToggle(device.id) }) { Text("Check out") }
            }
        }
    }
}

private val DeviceType.icon: ImageVector
    get() = when (this) {
        DeviceType.SCANNER -> Icons.Outlined.QrCode2
        DeviceType.BODY_CAM -> Icons.Outlined.Videocam
        DeviceType.PRINTER -> Icons.Outlined.Print
        DeviceType.VEHICLE -> Icons.Outlined.ElectricScooter
    }
