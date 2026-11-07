package com.cak3d.biscuitmesh

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Lets the user stop (and restart) a scan straight from the notification shade — the
 * safety valve for when a scan bogs the UI down and the app can't be driven normally.
 * The buttons fire broadcasts here, which run independently of the Compose UI thread,
 * so Stop lands even when the screen is unresponsive.
 */
class ScanControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? BiscuitMeshApp ?: return
        when (intent.action) {
            ACTION_STOP -> app.stopScanFromNotification()
            ACTION_START -> app.startScanFromNotification()
        }
    }

    companion object {
        const val ACTION_STOP = "com.cak3d.biscuitmesh.action.STOP_SCAN"
        const val ACTION_START = "com.cak3d.biscuitmesh.action.START_SCAN"

        /** PendingIntent for the notification's Stop button. */
        fun stopIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
            ctx, 2001,
            Intent(ctx, ScanControlReceiver::class.java).setAction(ACTION_STOP).setPackage(ctx.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** PendingIntent for the notification's Start button. */
        fun startIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
            ctx, 2002,
            Intent(ctx, ScanControlReceiver::class.java).setAction(ACTION_START).setPackage(ctx.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** The "scan stopped — tap to start again" notice shown after a Stop, so Start is one tap away. */
object ScanControlNotification {
    private const val CHANNEL = "wardrive"
    const val STOPPED_ID = 1005

    @SuppressLint("MissingPermission")
    fun showStopped(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Wardrive", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setContentTitle("Scan stopped")
            .setContentText("Tap Start to scan again.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "▶ Start scan", ScanControlReceiver.startIntent(ctx))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(STOPPED_ID, n) }
    }

    fun clearStopped(ctx: Context) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(STOPPED_ID) }
    }
}
