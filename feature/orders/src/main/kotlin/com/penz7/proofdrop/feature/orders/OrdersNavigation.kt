package com.penz7.proofdrop.feature.orders

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object OrdersDestination

@Serializable data class OrderDetailDestination(val orderId: String)

fun NavGraphBuilder.ordersScreens(
    onOpenOrder: (String) -> Unit,
    onCaptureProof: (String) -> Unit,
    onBack: () -> Unit,
) {
    composable<OrdersDestination> { OrdersRoute(onOpenOrder = onOpenOrder) }
    composable<OrderDetailDestination> { OrderDetailRoute(onCaptureProof = onCaptureProof, onBack = onBack) }
}
