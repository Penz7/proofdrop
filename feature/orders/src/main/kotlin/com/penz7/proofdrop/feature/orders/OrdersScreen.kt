package com.penz7.proofdrop.feature.orders

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.penz7.proofdrop.core.designsystem.component.EmptyState
import com.penz7.proofdrop.core.designsystem.component.StatusChip
import com.penz7.proofdrop.core.designsystem.theme.DangerRed
import com.penz7.proofdrop.core.designsystem.theme.SuccessGreen
import com.penz7.proofdrop.core.model.Order

private val Amber = Color(0xFFC98A00)

private val shiftPermissions = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
internal fun OrdersRoute(
    onOpenOrder: (String) -> Unit,
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val location = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (location) viewModel.startShift() else viewModel.onLocationDenied()
    }

    OrdersScreen(
        state = state,
        snackbar = snackbar,
        onRefresh = viewModel::refresh,
        onOpenOrder = onOpenOrder,
        onToggleShift = { if (state.onShift) viewModel.endShift() else permissionLauncher.launch(shiftPermissions) },
        onLogout = viewModel::requestLogout,
    )

    state.unsyncedOnLogout?.let { count ->
        AlertDialog(
            onDismissRequest = viewModel::dismissLogout,
            title = { Text("Sign out with unsynced proof?") },
            text = {
                Text(
                    "$count proof-of-delivery record(s) have not reached the server yet and will be deleted " +
                        "from this phone. Connect to the internet and wait for them to sync first.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmLogout) { Text("Sign out anyway", color = DangerRed) } },
            dismissButton = { TextButton(onClick = viewModel::dismissLogout) { Text("Stay signed in") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrdersScreen(
    state: OrdersUiState,
    snackbar: SnackbarHostState,
    onRefresh: () -> Unit,
    onOpenOrder: (String) -> Unit,
    onToggleShift: () -> Unit,
    onLogout: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My deliveries")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                state.userName.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Spacer(Modifier.width(8.dp))
                            when {
                                state.demo -> StatusChip("Demo", Amber)
                                state.online == true -> StatusChip("Online", SuccessGreen)
                                state.online == false -> StatusChip("Offline", Amber)
                            }
                        }
                    }
                },
                actions = {
                    FilterChip(
                        selected = state.onShift,
                        onClick = onToggleShift,
                        label = { Text(if (state.onShift) "On shift" else "Start shift") },
                        leadingIcon = {
                            Icon(
                                if (state.onShift) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                null,
                                Modifier.size(18.dp),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SuccessGreen.copy(alpha = 0.2f),
                            selectedLabelColor = SuccessGreen,
                            selectedLeadingIconColor = SuccessGreen,
                        ),
                    )
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Sign out") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
                                onClick = {
                                    menuOpen = false
                                    onLogout()
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.isLoading) {
                Unit
            } else if (state.orders.isEmpty() && !state.isRefreshing) {
                EmptyState(
                    Icons.Outlined.Inventory2,
                    "No deliveries",
                    if (state.onShift) "You're on shift. New assignments will appear here."
                    else "Start your shift to receive assignments, or pull down to refresh.",
                )
            } else {
                val listState = rememberLazyListState()
                // A new assignment is inserted at the top; LazyColumn keeps the old first item in view,
                // which would hide the new one above the fold. Follow it when the user is near the top.
                val firstId = state.orders.firstOrNull()?.id
                LaunchedEffect(firstId) {
                    if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.orders, key = { it.id }) { order ->
                        OrderCard(order, onClick = { onOpenOrder(order.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(order: Order, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(order.code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (order.beaconId != null) {
                    Icon(
                        Icons.Outlined.BluetoothSearching,
                        contentDescription = "Beacon at drop-off",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                StatusChip(order.status.label, order.status.color)
            }
            Text(order.customerName, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(4.dp))
                Text(order.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
            Text(order.items, style = MaterialTheme.typography.bodySmall)
        }
    }
}
