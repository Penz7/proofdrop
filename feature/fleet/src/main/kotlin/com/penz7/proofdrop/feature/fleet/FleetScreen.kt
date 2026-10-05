package com.penz7.proofdrop.feature.fleet

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.penz7.proofdrop.core.data.repository.FleetSource
import com.penz7.proofdrop.core.data.repository.FleetState
import com.penz7.proofdrop.core.designsystem.component.StatusChip
import com.penz7.proofdrop.core.designsystem.theme.SuccessGreen
import com.penz7.proofdrop.core.model.CourierPosition
import kotlinx.serialization.Serializable
import android.graphics.Color as AndroidColor

@Serializable data object FleetDestination

fun NavGraphBuilder.fleetScreens() {
    composable<FleetDestination> { FleetRoute() }
}

private val Amber = Color(0xFFC98A00)

@Composable
internal fun FleetRoute(viewModel: FleetViewModel = hiltViewModel()) {
    val fleet by viewModel.fleet.collectAsStateWithLifecycle()
    FleetScreen(fleet)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FleetScreen(fleet: FleetState?) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fleet") },
                actions = {
                    when (fleet?.source) {
                        FleetSource.LIVE -> StatusChip("● Live", SuccessGreen)
                        FleetSource.RECONNECTING -> StatusChip("Reconnecting…", Amber)
                        FleetSource.DEMO -> StatusChip("Demo simulation", Amber)
                        null -> StatusChip("Connecting…", MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(Modifier.width(12.dp))
                },
            )
        },
    ) { padding ->
        val couriers = fleet?.couriers.orEmpty().sortedWith(compareBy({ it.courierId != fleet?.selfId }, { it.name }))
        Column(Modifier.fillMaxSize().padding(padding)) {
            FleetMap(couriers, fleet?.selfId, Modifier.fillMaxWidth().weight(1.2f))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                if (couriers.isEmpty()) {
                    item {
                        Text(
                            "No couriers online yet.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                items(couriers, key = { it.courierId }) {
                    CourierRow(it, isMe = it.courierId == fleet?.selfId)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun CourierRow(c: CourierPosition, isMe: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(12.dp), shape = CircleShape, color = Color(AndroidColor.parseColor(c.status.hex()))) {}
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (isMe) "${c.name} (you)" else c.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "%.5f, %.5f".format(c.latitude, c.longitude),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(c.status.name.replace('_', ' ').lowercase(), style = MaterialTheme.typography.labelMedium)
    }
}
