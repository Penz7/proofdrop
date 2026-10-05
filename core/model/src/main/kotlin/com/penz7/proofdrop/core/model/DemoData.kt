package com.penz7.proofdrop.core.model

import kotlin.math.cos
import kotlin.math.sin

/**
 * Data for the app's demo mode, so it is fully usable without a backend
 * (e.g. for Play Store reviewers or a quick look by a recruiter).
 */
object DemoData {
    const val HUB_LAT = 10.7769
    const val HUB_LNG = 106.7009

    /** The courier used in demo mode (no server). */
    val demoCourier = User(id = "demo-courier", email = "demo@proofdrop.dev", name = "Demo Courier", role = Role.COURIER)

    fun orders(now: Long): List<Order> = listOf(
        demoOrder(1, "Nguyễn An", "12 Nguyễn Huệ, Q.1, TP.HCM", 10.7743, 106.7038, "2x Cà phê sữa đá", OrderStatus.ASSIGNED, "PD-BEACON-01", now - 600_000),
        demoOrder(2, "Trần Bình", "45 Lê Lợi, Q.1, TP.HCM", 10.7726, 106.6990, "Hồ sơ hợp đồng (A4)", OrderStatus.ASSIGNED, null, now - 480_000),
        demoOrder(3, "Lê Chi", "88 Đồng Khởi, Q.1, TP.HCM", 10.7764, 106.7032, "Laptop sửa chữa", OrderStatus.PICKED_UP, "PD-BEACON-02", now - 360_000),
        demoOrder(4, "Phạm Dũng", "101 Hai Bà Trưng, Q.1, TP.HCM", 10.7805, 106.7012, "Thuốc kê đơn", OrderStatus.ASSIGNED, null, now - 240_000),
        demoOrder(5, "Hoàng Em", "7 Pasteur, Q.1, TP.HCM", 10.7790, 106.6955, "1x Bánh mì đặc biệt", OrderStatus.ASSIGNED, null, now - 120_000),
    )

    private fun demoOrder(
        n: Int, customer: String, address: String, lat: Double, lng: Double,
        items: String, status: OrderStatus, beaconId: String?, assignedAt: Long,
    ) = Order(
        id = "demo-order-$n",
        code = "PD-100$n",
        customerName = customer,
        address = address,
        latitude = lat,
        longitude = lng,
        items = items,
        status = status,
        beaconId = beaconId,
        courierId = demoCourier.id,
        courierName = demoCourier.name,
        assignedAt = assignedAt,
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
