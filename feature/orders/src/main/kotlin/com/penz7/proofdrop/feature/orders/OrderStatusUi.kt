package com.penz7.proofdrop.feature.orders

import androidx.compose.ui.graphics.Color
import com.penz7.proofdrop.core.designsystem.theme.DangerRed
import com.penz7.proofdrop.core.designsystem.theme.InfoBlue
import com.penz7.proofdrop.core.designsystem.theme.SuccessGreen
import com.penz7.proofdrop.core.model.OrderStatus

internal val OrderStatus.label: String
    get() = when (this) {
        OrderStatus.CREATED -> "New"
        OrderStatus.ASSIGNED -> "Assigned"
        OrderStatus.PICKED_UP -> "Picked up"
        OrderStatus.DELIVERED -> "Delivered"
        OrderStatus.FAILED -> "Failed"
        OrderStatus.CANCELLED -> "Cancelled"
    }

internal val OrderStatus.color: Color
    get() = when (this) {
        OrderStatus.CREATED, OrderStatus.ASSIGNED -> InfoBlue
        OrderStatus.PICKED_UP -> Color(0xFFC98A00)
        OrderStatus.DELIVERED -> SuccessGreen
        OrderStatus.FAILED -> DangerRed
        OrderStatus.CANCELLED -> Color(0xFF8A94A6)
    }
