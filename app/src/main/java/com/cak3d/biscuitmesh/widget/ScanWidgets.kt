package com.cak3d.biscuitmesh.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.cak3d.biscuitmesh.BiscuitMeshApp
import com.cak3d.biscuitmesh.R
import com.cak3d.biscuitmesh.progress.ProgressTrack
import com.cak3d.biscuitmesh.wardrive.SampleKind
import com.cak3d.biscuitmesh.wardrive.WardriveMode

private const val ACTION_TOGGLE = "com.cak3d.biscuitmesh.widget.TOGGLE"
private val GREEN = 0xFF34D399.toInt()
private val GRAY = 0xFF8FA189.toInt()

private fun fmtElapsed(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

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
        fillInfo(app, rv)
        val toggle = PendingIntent.getBroadcast(
            context, javaClass.name.hashCode(),
            Intent(context, javaClass).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        rv.setOnClickPendingIntent(R.id.w_root, toggle)
        mgr.updateAppWidget(id, rv)
    }

    /** Fill the live-info lines for this widget's mode — session counts while scanning,
     *  lifetime totals when idle, and everything at once for the Wardrive widget. */
    private fun fillInfo(app: BiscuitMeshApp?, rv: RemoteViews) {
        val st = app?.wardrive?.state?.value
        val active = st?.active == true
        val rf = app?.wardrive?.samples?.value?.values?.count { it.kind == SampleKind.RF } ?: 0
        val p = app?.progress?.state?.value
        val lifeWifi = p?.radios?.filter { it.track == ProgressTrack.WIFI_24 || it.track == ProgressTrack.WIFI_5 }?.sumOf { it.count } ?: 0
        val lifeBt = p?.radios?.firstOrNull { it.track == ProgressTrack.BLUETOOTH }?.count ?: 0

        var primary = ""
        var secondary = ""
        when (mode) {
            WardriveMode.ALL -> if (active) {
                primary = "${st?.wifiCount ?: 0} Wi-Fi · ${st?.btCount ?: 0} BT"
                val t = st?.startMs?.takeIf { it > 0 }?.let { fmtElapsed(System.currentTimeMillis() - it) } ?: "0:00"
                val src = st?.sourceCounts?.size ?: 0
                secondary = "$rf sub-GHz · $t" + if (src > 1) " · $src src" else ""
            } else {
                primary = "$lifeWifi Wi-Fi · $lifeBt BT"
                secondary = if (p != null) "all time · ${p.rankName}" else "tap to wardrive"
            }
            WardriveMode.WIFI -> primary = if (active) "${st?.wifiCount ?: 0} networks" else "$lifeWifi all-time"
            WardriveMode.BT -> primary = if (active) "${st?.btCount ?: 0} devices" else "$lifeBt all-time"
            null -> {
                primary = if (rf > 0) "$rf sub-GHz" else ""
                secondary = if (app?.subGhzSweeping == true) "board sweeping" else ""
            }
        }
        rv.setTextViewText(R.id.w_primary, primary)
        rv.setViewVisibility(R.id.w_primary, if (primary.isBlank()) View.GONE else View.VISIBLE)
        rv.setTextViewText(R.id.w_secondary, secondary)
        rv.setViewVisibility(R.id.w_secondary, if (secondary.isBlank()) View.GONE else View.VISIBLE)
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
