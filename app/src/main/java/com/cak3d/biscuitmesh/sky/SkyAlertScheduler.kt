package com.cak3d.biscuitmesh.sky

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Schedules a lightweight hourly check for bright satellite passes coming up soon, delivered as a
 * notification even when the app is closed. Uses an inexact repeating alarm (no special permission,
 * battery-friendly). Re-applied on every app start, so it survives a reboot once the app opens.
 */
object SkyAlertScheduler {
    private const val REQ = 7731
    const val ACTION = "com.cak3d.biscuitmesh.action.SKY_CHECK"

    fun apply(context: Context, enabled: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context)
        runCatching {
            if (enabled) {
                am.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 60_000L,
                    AlarmManager.INTERVAL_HOUR,
                    pi,
                )
            } else {
                am.cancel(pi)
            }
        }
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQ,
        Intent(context, SkyAlertReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
