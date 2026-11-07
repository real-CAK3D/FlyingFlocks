package com.cak3d.biscuitmesh.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.cak3d.biscuitmesh.BiscuitMeshApp
import com.cak3d.biscuitmesh.MainActivity
import com.cak3d.biscuitmesh.R
import com.cak3d.biscuitmesh.progress.ProgressTrack

/** Shows your rank and lifetime find counts. Tap to open the app. */
class StatsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, mgr, it) }
    }

    private fun render(context: Context, mgr: AppWidgetManager, id: Int) {
        val app = context.applicationContext as? BiscuitMeshApp
        val p = app?.progress?.state?.value
        val rv = RemoteViews(context.packageName, R.layout.widget_stats)
        if (p != null) {
            val wifi = p.radios.filter { it.track == ProgressTrack.WIFI_24 || it.track == ProgressTrack.WIFI_5 }.sumOf { it.count }
            val bt = p.radios.firstOrNull { it.track == ProgressTrack.BLUETOOTH }?.count ?: 0
            rv.setTextViewText(R.id.ws_rank, "${p.rank} · ${p.rankName}")
            rv.setTextViewText(R.id.ws_finds, "$wifi Wi-Fi · $bt BT")
        } else {
            rv.setTextViewText(R.id.ws_rank, "Flying Flocks")
            rv.setTextViewText(R.id.ws_finds, "open to start")
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        rv.setOnClickPendingIntent(R.id.ws_root, open)
        mgr.updateAppWidget(id, rv)
    }

    companion object {
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, StatsWidget::class.java))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, StatsWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                )
            }
        }
    }
}
