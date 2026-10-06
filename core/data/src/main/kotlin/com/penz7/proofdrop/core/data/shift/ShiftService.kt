package com.penz7.proofdrop.core.data.shift

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.penz7.proofdrop.core.data.LocationTracker
import com.penz7.proofdrop.core.data.R
import com.penz7.proofdrop.core.data.repository.FleetRepository
import com.penz7.proofdrop.core.data.repository.OrderRepository
import com.penz7.proofdrop.core.network.session.SessionStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Runs while the courier is on shift: streams GPS to dispatch over the fleet WebSocket and
 * turns pushed assignments (SSE) into notifications, even when the app is in the background.
 */
@AndroidEntryPoint
class ShiftService : Service() {

    @Inject lateinit var controller: ShiftController
    @Inject lateinit var location: LocationTracker
    @Inject lateinit var fleet: FleetRepository
    @Inject lateinit var orders: OrderRepository
    @Inject lateinit var session: SessionStore

    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (scope != null) return START_NOT_STICKY
        if (session.session.value == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannels()
        try {
            ServiceCompat.startForeground(
                this,
                ONGOING_ID,
                ongoingNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: Exception) {
            // Permission revoked, or Android refused a background start (ForegroundServiceStartNotAllowedException).
            stopSelf()
            return START_NOT_STICKY
        }
        location.start()
        controller.setRunning(true)

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).apply {
            // Collecting the fleet keeps the WebSocket open, which reports our position.
            launch { fleet.observeFleet().collect { } }
            launch { orders.liveAssignments().collect { order -> notifyAssignment(order.id, order.code, order.address) } }
        }
        // Not sticky: Android 14+ forbids restarting a location service from the background,
        // so after the process dies the courier resumes the shift from the app.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        location.stop()
        controller.setRunning(false)
        super.onDestroy()
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SHIFT, "Shift status", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ASSIGNMENTS, "New deliveries", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun openAppIntent(orderId: String? = null): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            orderId?.let { intent.putExtra(EXTRA_ORDER_ID, it) }
            PendingIntent.getActivity(
                this, orderId?.hashCode() ?: 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

    private fun ongoingNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_SHIFT)
        .setSmallIcon(R.drawable.ic_stat_proofdrop)
        .setContentTitle("On shift")
        .setContentText("Sharing your location with dispatch")
        .setOngoing(true)
        .setContentIntent(openAppIntent())
        .build()

    private fun notifyAssignment(orderId: String, code: String, address: String) {
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!canNotify) return
        val notification = NotificationCompat.Builder(this, CHANNEL_ASSIGNMENTS)
            .setSmallIcon(R.drawable.ic_stat_proofdrop)
            .setContentTitle("New delivery $code")
            .setContentText(address)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(orderId))
            .build()
        NotificationManagerCompat.from(this).notify(code.hashCode(), notification)
    }

    companion object {
        /** Extra on the launch intent: open this order's detail screen. */
        const val EXTRA_ORDER_ID = "com.penz7.proofdrop.ORDER_ID"
        private const val ONGOING_ID = 1
        private const val CHANNEL_SHIFT = "shift"
        private const val CHANNEL_ASSIGNMENTS = "assignments"
    }
}
