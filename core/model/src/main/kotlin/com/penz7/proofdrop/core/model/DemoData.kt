package com.penz7.proofdrop.core.model

import kotlin.math.cos
import kotlin.math.sin

/**
 * Demo data shared by the server and the app's offline mode, so the app is fully
 * usable without a backend (e.g. for Play Store reviewers).
 */
object DemoData {
    const val HUB_LAT = 10.7769
    const val HUB_LNG = 106.7009

    val streets = listOf("Nguyễn Huệ", "Lê Lợi", "Đồng Khởi", "Hai Bà Trưng", "Pasteur", "Lý Tự Trọng")
    val customers = listOf("Nguyễn An", "Trần Bình", "Lê Chi", "Phạm Dũng", "Hoàng Em", "Võ Giang")
    val items = listOf("2x Cà phê sữa đá", "Hồ sơ hợp đồng (A4)", "Laptop sửa chữa", "Thuốc kê đơn", "1x Bánh mì đặc biệt")

    fun orders(now: Long) = listOf(
        Order("PD-1001", "Nguyễn An", "12 Nguyễn Huệ, Q.1, TP.HCM", 10.7743, 106.7038, "2x Cà phê sữa đá", OrderStatus.ASSIGNED, "PD-BEACON-01", now - 600_000),
        Order("PD-1002", "Trần Bình", "45 Lê Lợi, Q.1, TP.HCM", 10.7726, 106.6990, "Hồ sơ hợp đồng (A4)", OrderStatus.ASSIGNED, null, now - 480_000),
        Order("PD-1003", "Lê Chi", "88 Đồng Khởi, Q.1, TP.HCM", 10.7764, 106.7032, "Laptop sửa chữa", OrderStatus.PICKED_UP, "PD-BEACON-02", now - 360_000),
        Order("PD-1004", "Phạm Dũng", "101 Hai Bà Trưng, Q.1, TP.HCM", 10.7805, 106.7012, "Thuốc kê đơn", OrderStatus.ASSIGNED, null, now - 240_000),
        Order("PD-1005", "Hoàng Em", "7 Pasteur, Q.1, TP.HCM", 10.7790, 106.6955, "1x Bánh mì đặc biệt", OrderStatus.ASSIGNED, null, now - 120_000),
    )

    fun devices() = listOf(
        Device("DEV-001", "Zebra TC22 Scanner", DeviceType.SCANNER, "ZT22-88341", 92),
        Device("DEV-002", "Body Cam BC-4", DeviceType.BODY_CAM, "BC4-10022", 76),
        Device("DEV-003", "Label Printer ZQ220", DeviceType.PRINTER, "ZQ2-55120", 64),
        Device("DEV-004", "E-Scooter 07", DeviceType.VEHICLE, "VN-59X1-0707", 81),
        Device("DEV-005", "Body Cam BC-4", DeviceType.BODY_CAM, "BC4-10023", 18, "sim-2", "Courier #02", null),
        Device("DEV-006", "Zebra TC22 Scanner", DeviceType.SCANNER, "ZT22-88342", 55),
    )

    val courierIds = (1..5).map { "sim-$it" }

    /** Deterministic movement on small loops around the hub, one step per second. */
    fun simulatedPosition(courierId: String, tick: Int, now: Long): CourierPosition {
        val seed = courierId.hashCode().mod(7)
        val phase = tick / 20.0 + seed
        val radius = 0.004 + seed.mod(3) * 0.002
        return CourierPosition(
            courierId = courierId,
            name = "Courier #0" + courierId.removePrefix("sim-"),
            latitude = HUB_LAT + radius * sin(phase),
            longitude = HUB_LNG + radius * cos(phase),
            status = if ((tick + seed * 4) % 30 < 25) CourierStatus.EN_ROUTE else CourierStatus.DELIVERING,
            updatedAt = now,
        )
    }
}
