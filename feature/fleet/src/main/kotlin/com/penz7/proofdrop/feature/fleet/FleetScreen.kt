package com.penz7.proofdrop.feature.fleet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.penz7.proofdrop.core.data.repository.FleetSource
import com.penz7.proofdrop.core.data.repository.FleetState
import com.penz7.proofdrop.core.designsystem.component.StatusChip
import com.penz7.proofdrop.core.designsystem.theme.InfoBlue
import com.penz7.proofdrop.core.designsystem.theme.SuccessGreen
import com.penz7.proofdrop.core.model.CourierPosition
import com.penz7.proofdrop.core.model.CourierStatus
import com.penz7.proofdrop.core.model.CurrentCourier
import com.penz7.proofdrop.core.model.DemoData
import kotlinx.serialization.Serializable

@Serializable data object FleetDestination

fun NavGraphBuilder.fleetScreens() {
    composable<FleetDestination> { FleetRoute() }
}

private val Amber = Color(0xFFC98A00)

private val CourierStatus.color: Color
    get() = when (this) {
        CourierStatus.IDLE -> Color.Gray
        CourierStatus.EN_ROUTE -> InfoBlue
        CourierStatus.DELIVERING -> SuccessGreen
        CourierStatus.OFFLINE -> Color.DarkGray
    }

@Composable
internal fun FleetRoute(viewModel: FleetViewModel = hiltViewModel()) {
    val fleet by viewModel.fleet.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    FleetScreen(fleet, serverUrl, viewModel::updateServerUrl)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FleetScreen(fleet: FleetState?, serverUrl: String, onUpdateUrl: (String) -> Boolean) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fleet dashboard") },
                actions = {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Settings, "Server settings") }
                },
            )
        },
    ) { padding ->
        val couriers = fleet?.couriers.orEmpty().sortedBy { it.name }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (fleet?.source) {
                        FleetSource.LIVE -> StatusChip("● LIVE · WebSocket", SuccessGreen)
                        FleetSource.SIMULATED -> StatusChip("Offline · local simulation", Amber)
                        null -> StatusChip("Connecting…", MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(serverUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            item { FleetMap(couriers) }
            items(couriers, key = { it.courierId }) { CourierRow(it) }
        }
    }

    if (editing) {
        ServerUrlDialog(
            current = serverUrl,
            onDismiss = { editing = false },
            onSave = { if (onUpdateUrl(it)) editing = false },
        )
    }
}

/** A lightweight "map": couriers plotted relative to the hub. No Maps SDK key required. */
@Composable
private fun FleetMap(couriers: List<CourierPosition>) {
    val measurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurface
    val hubColor = MaterialTheme.colorScheme.primary
    Card(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.2f).padding(12.dp)) {
            val span = 0.025 // degrees shown edge to edge (~2.7 km)
            fun project(lat: Double, lng: Double) = Offset(
                x = (size.width / 2 + (lng - DemoData.HUB_LNG) / span * size.width).toFloat(),
                y = (size.height / 2 - (lat - DemoData.HUB_LAT) / span * size.height).toFloat(),
            )
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            for (i in 1..3) {
                drawLine(gridColor, Offset(size.width * i / 4, 0f), Offset(size.width * i / 4, size.height), pathEffect = dash)
                drawLine(gridColor, Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4), pathEffect = dash)
            }
            val hub = project(DemoData.HUB_LAT, DemoData.HUB_LNG)
            drawCircle(hubColor, radius = 10f, center = hub)
            drawCircle(hubColor.copy(alpha = 0.25f), radius = 26f, center = hub, style = Stroke(3f))
            drawText(measurer, "HUB", hub + Offset(14f, -30f), TextStyle(color = labelColor, fontSize = 10.sp))

            couriers.forEach { c ->
                val p = project(c.latitude, c.longitude)
                if (p.x !in 0f..size.width || p.y !in 0f..size.height) return@forEach
                val isMe = c.courierId == CurrentCourier.ID
                if (isMe) drawCircle(hubColor, radius = 18f, center = p, style = Stroke(4f))
                drawCircle(c.status.color, radius = 11f, center = p)
                drawText(
                    measurer,
                    if (isMe) "You" else c.name.removePrefix("Courier "),
                    p + Offset(14f, -8f),
                    TextStyle(color = labelColor, fontSize = 10.sp),
                )
            }
        }
    }
}

@Composable
private fun CourierRow(c: CourierPosition) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(12.dp), shape = CircleShape, color = c.status.color) {}
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (c.courierId == CurrentCourier.ID) "${c.name} (you)" else c.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "%.5f, %.5f".format(c.latitude, c.longitude),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(c.status.name.replace('_', ' ').lowercase(), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ServerUrlDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dispatch server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Emulator: http://10.0.2.2:8080\nPhone on same Wi-Fi: http://<your-PC-IP>:8080",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Server URL") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
