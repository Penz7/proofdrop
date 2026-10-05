package com.penz7.proofdrop.feature.capture

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data class CaptureDestination(val orderId: String)

@Serializable data object LedgerDestination

fun NavGraphBuilder.captureScreens(
    onDone: () -> Unit,
    onBack: () -> Unit,
    showDebugTools: Boolean,
) {
    composable<CaptureDestination> { CaptureRoute(onDone = onDone, onBack = onBack) }
    composable<LedgerDestination> { LedgerRoute(showDebugTools = showDebugTools) }
}
