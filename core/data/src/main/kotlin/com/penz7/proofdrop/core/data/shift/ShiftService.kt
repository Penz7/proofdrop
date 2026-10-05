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

    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (scope != null) return START_STICKY
        createChannels()
        try {
            ServiceCompat.startForeground(
                this,
                ONGOING_ID,
                ongoingNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: SecurityException) {
            // Location permission was revoked; a location service may not start without it.
            stopSelf()
            return START_NOT_STICKY
        }
        location.start()
        controller.setRunning(true)

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).apply {
            // Collecting the fleet keeps the WebSocket open, which reports our position.
            launch { fleet.observeFleet().collect { } }
            launch { orders.liveAssignments().collect { order -> notifyAssignment(order.code, order.address) } }
        }
        return START_STICKY
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

    private fun openAppIntent(): PendingIntent? = packageManager.getLaunchIntentForPackage(packageName)?.let {
        PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun ongoingNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_SHIFT)
        .setSmallIcon(R.drawable.ic_stat_proofdrop)
        .setContentTitle("On shift")
        .setContentText("Sharing your location with dispatch")
        .setOngoing(true)
        .setContentIntent(openAppIntent())
        .build()

    private fun notifyAssignment(code: String, address: String) {
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!canNotify) return
        val notification = NotificationCompat.Builder(this, CHANNEL_ASSIGNMENTS)
            .setSmallIcon(R.drawable.ic_stat_proofdrop)
            .setContentTitle("New delivery $code")
            .setContentText(address)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        NotificationManagerCompat.from(this).notify(code.hashCode(), notification)
    }

    private companion object {
        const val ONGOING_ID = 1
        const val CHANNEL_SHIFT = "shift"
        const val CHANNEL_ASSIGNMENTS = "assignments"
    }
}
