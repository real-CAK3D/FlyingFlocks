package com.cak3d.biscuitmesh.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.cak3d.biscuitmesh.BiscuitMeshApp
import com.cak3d.biscuitmesh.R
import com.cak3d.biscuitmesh.wardrive.WardriveMode

private const val ACTION_TOGGLE = "com.cak3d.biscuitmesh.widget.TOGGLE"
private val GREEN = 0xFF34D399.toInt()
private val GRAY = 0xFF8FA189.toInt()

/**
 * A home-screen toggle for one kind of scan. Tap it to start/stop a background scan without
 * opening the app; the label shows whether it's running. Subclasses pick the kind.
 */
abstract class ScanWidgetBase : AppWidgetProvider() {
    /** The wardrive mode this widget toggles, or null for the board sub-GHz sweep. */
    abstract val mode: WardriveMode?
    abstract val title: String

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, mgr, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            val app = context.applicationContext as? BiscuitMeshApp ?: return
            val m = mode
            if (m == null) app.widgetToggleSubGhz() else app.widgetToggleScan(m)
            val mgr = AppWidgetManager.getInstance(context)
            mgr.getAppWidgetIds(ComponentName(context, javaClass)).forEach { render(context, mgr, it) }
        }
    }

    private fun isOn(app: BiscuitMeshApp?): Boolean {
        app ?: return false
        return when (val m = mode) {
            null -> app.subGhzSweeping
            else -> app.wardriveActive && app.wardriveMode == m
        }
    }

    private fun render(context: Context, mgr: AppWidgetManager, id: Int) {
        val app = context.applicationContext as? BiscuitMeshApp
        val on = isOn(app)
        val rv = RemoteViews(context.packageName, R.layout.widget_scan)
        rv.setTextViewText(R.id.w_title, title)
        rv.setTextViewText(R.id.w_state, if (on) "● scanning" else "○ tap to scan")
        rv.setTextColor(R.id.w_state, if (on) GREEN else GRAY)
        val toggle = PendingIntent.getBroadcast(
            context, javaClass.name.hashCode(),
            Intent(context, javaClass).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        rv.setOnClickPendingIntent(R.id.w_root, toggle)
        mgr.updateAppWidget(id, rv)
    }

    companion object {
        private val PROVIDERS = listOf(
            WardriveWidget::class.java, WifiWidget::class.java, BtWidget::class.java, SubGhzWidget::class.java,
        )

        /** Repaint every scan widget (and the stats widget) — call when scan state changes. */
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            PROVIDERS.forEach { cls ->
                val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
                if (ids.isNotEmpty()) {
                    context.sendBroadcast(
                        Intent(context, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                    )
                }
            }
            StatsWidget.refresh(context)
        }
    }
}

class WardriveWidget : ScanWidgetBase() {
    override val mode: WardriveMode? = WardriveMode.ALL
    override val title = "Wardrive"
}

class WifiWidget : ScanWidgetBase() {
    override val mode: WardriveMode? = WardriveMode.WIFI
    override val title = "Wi-Fi scan"
}

class BtWidget : ScanWidgetBase() {
    override val mode: WardriveMode? = WardriveMode.BT
    override val title = "BT scan"
}

class SubGhzWidget : ScanWidgetBase() {
    override val mode: WardriveMode? = null
    override val title = "Sub-GHz"
}
