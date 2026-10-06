package com.penz7.proofdrop.feature.capture

import android.Manifest
import android.os.Build
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.penz7.proofdrop.core.designsystem.camera.useBestAvailableCamera
import com.penz7.proofdrop.core.designsystem.component.PermissionGate
import com.penz7.proofdrop.core.designsystem.component.StatusChip
import com.penz7.proofdrop.core.designsystem.theme.DangerRed
import com.penz7.proofdrop.core.designsystem.theme.InfoBlue
import com.penz7.proofdrop.core.designsystem.theme.SuccessGreen
import com.penz7.proofdrop.core.model.EvidenceRecord

private val bluetoothPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN
    else Manifest.permission.ACCESS_FINE_LOCATION

@Composable
internal fun CaptureRoute(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    PermissionGate(
        required = listOf(Manifest.permission.CAMERA),
        optional = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, bluetoothPermission),
        rationale = "ProofDrop needs the camera to photograph the delivery. Location and Bluetooth are optional and " +
            "strengthen the proof with GPS and drop-off beacon verification.",
    ) {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        CaptureScreen(
            state = state,
            newFile = viewModel::newCaptureFile,
            onPhotoSaved = viewModel::onPhotoSaved,
            onCaptureError = viewModel::onCaptureError,
            onRetry = viewModel::retry,
            onDone = onDone,
            onBack = onBack,
        )
    }
}

@Composable
private fun CaptureScreen(
    state: CaptureUiState,
    newFile: () -> java.io.File,
    onPhotoSaved: (java.io.File) -> Unit,
    onCaptureError: (String?) -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) }
    }
    LaunchedEffect(lifecycleOwner) {
        controller.useBestAvailableCamera(context)
        controller.bindToLifecycle(lifecycleOwner)
    }

    var capturing by remember { mutableStateOf(false) }

    fun takePhoto() {
        if (capturing) return
        capturing = true
        val file = newFile()
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    onPhotoSaved(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    file.delete()
                    onCaptureError("Could not take the photo. Is a camera available? (${exception.imageCaptureError})")
                }
            },
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { PreviewView(it).apply { this.controller = controller } },
            modifier = Modifier.fillMaxSize(),
        )

        // Top overlay: order + beacon status
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Spacer(Modifier.width(8.dp))
            Surface(color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(state.order?.code ?: "…", fontWeight = FontWeight.Bold)
                    Text(state.order?.address ?: "", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.weight(1f))
            BeaconBadge(state.beacon)
        }

        // Bottom: shutter or result
        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp)) {
            when (val phase = state.phase) {
                CapturePhase.Ready -> FilledIconButton(
                    onClick = ::takePhoto,
                    enabled = !capturing,
                    modifier = Modifier.size(76.dp),
                    shape = CircleShape,
                ) { Icon(Icons.Filled.CameraAlt, "Capture proof", Modifier.size(34.dp)) }
                CapturePhase.Sealing -> CircularProgressIndicator(color = Color.White)
                is CapturePhase.Sealed -> SealedCard(phase.record, onDone)
                is CapturePhase.Failed -> Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(phase.message, color = DangerRed)
                        Button(onClick = onRetry) { Text("Try again") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BeaconBadge(beacon: BeaconUi) {
    when (beacon) {
        BeaconUi.NotRequired -> Unit
        BeaconUi.BluetoothOff -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BluetoothDisabled, null, tint = DangerRed)
            Spacer(Modifier.width(4.dp))
            StatusChip("Bluetooth off", DangerRed)
        }
        BeaconUi.NoPermission -> StatusChip("No BLE permission", DangerRed)
        is BeaconUi.Searching -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BluetoothSearching, null, tint = Color.White)
            Spacer(Modifier.width(4.dp))
            StatusChip("Beacon… ${beacon.nearbyDevices} nearby", InfoBlue)
        }
        is BeaconUi.Verified -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bluetooth, null, tint = SuccessGreen)
            Spacer(Modifier.width(4.dp))
            StatusChip("Beacon ✓ ${beacon.rssi} dBm", SuccessGreen)
        }
    }
}

@Composable
private fun SealedCard(record: EvidenceRecord, onDone: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Verified, null, tint = SuccessGreen)
                Spacer(Modifier.width(8.dp))
                Text("Delivery sealed as evidence #${record.sequence}", fontWeight = FontWeight.Bold)
            }
            HashLine("Photo SHA-256", record.fileSha256)
            HashLine("Record hash", record.recordHash)
            Text(
                buildString {
                    append(if (record.latitude != null) "GPS tagged" else "No GPS fix")
                    append(" · ")
                    append(if (record.bleVerified) "Beacon verified" else "No beacon proof")
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

@Composable
internal fun HashLine(label: String, hash: String) {
    Row {
        Text("$label  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Text(hash.take(16) + "…", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
    }
}
