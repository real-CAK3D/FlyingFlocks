package com.cak3d.biscuitmesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps a wardrive alive while the app is backgrounded or
 * the screen is off — Android throttles background location/scanning unless a
 * foreground service with the location type is running. It owns no scanning logic
 * (the app-scoped WardriveManager does that); it just keeps the process foreground
 * and shows a live "scanning" notification, stopping itself when the drive ends.
 */
class WardriveService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(build("Scanning…"))
        val wd = (application as? BiscuitMeshApp)?.wardrive
        job?.cancel()
        job = scope.launch {
            wd?.state?.collect { s ->
                if (!s.active) { stopSelf(); return@collect }
                val text = "${s.wifiCount} Wi-Fi · ${s.btCount} BT" +
                    if (s.inDeadZone) " · paused (dead zone)" else ""
                runCatching { notifManager().notify(NOTIF_ID, build(text)) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun notifManager() = getSystemService(NotificationManager::class.java)

    private fun build(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Wardrive", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
            notifManager().createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Flying Flocks is scanning")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL = "wardrive"
        private const val NOTIF_ID = 1001

        fun start(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, WardriveService::class.java)) }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, WardriveService::class.java)) }
        }
    }
}
