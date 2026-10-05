package com.penz7.proofdrop.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.penz7.proofdrop.BuildConfig
import com.penz7.proofdrop.feature.capture.CaptureDestination
import com.penz7.proofdrop.feature.capture.LedgerDestination
import com.penz7.proofdrop.feature.capture.captureScreens
import com.penz7.proofdrop.feature.checkout.DevicesDestination
import com.penz7.proofdrop.feature.checkout.checkoutScreens
import com.penz7.proofdrop.feature.fleet.FleetDestination
import com.penz7.proofdrop.feature.fleet.fleetScreens
import com.penz7.proofdrop.feature.orders.OrderDetailDestination
import com.penz7.proofdrop.feature.orders.OrdersDestination
import com.penz7.proofdrop.feature.orders.ordersScreens
import kotlin.reflect.KClass

private enum class TopLevel(val label: String, val icon: ImageVector, val route: Any, val routeClass: KClass<*>) {
    ORDERS("Deliveries", Icons.Outlined.LocalShipping, OrdersDestination, OrdersDestination::class),
    LEDGER("Ledger", Icons.Outlined.ReceiptLong, LedgerDestination, LedgerDestination::class),
    DEVICES("Devices", Icons.Outlined.Devices, DevicesDestination, DevicesDestination::class),
    FLEET("Fleet", Icons.Outlined.Map, FleetDestination, FleetDestination::class),
}

@Composable
fun ProofDropApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = TopLevel.entries.any { top -> destination?.hasRoute(top.routeClass) == true }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevel.entries.forEach { top ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.hasRoute(top.routeClass) } == true,
                            onClick = {
                                navController.navigate(top.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(top.icon, null) },
                            label = { Text(top.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = OrdersDestination,
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(padding),
        ) {
            ordersScreens(
                onOpenOrder = { navController.navigate(OrderDetailDestination(it)) },
                onCaptureProof = { navController.navigate(CaptureDestination(it)) },
                onBack = navController::popBackStack,
            )
            captureScreens(
                onDone = { navController.popBackStack(OrdersDestination, inclusive = false) },
                onBack = navController::popBackStack,
                showDebugTools = BuildConfig.DEBUG,
            )
            checkoutScreens()
            fleetScreens()
        }
    }
}
