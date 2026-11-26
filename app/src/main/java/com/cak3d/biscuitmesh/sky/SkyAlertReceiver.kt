package com.cak3d.biscuitmesh.sky

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.cak3d.biscuitmesh.BiscuitMeshApp
import com.cak3d.biscuitmesh.NotificationHelper
import com.cak3d.biscuitmesh.geo.TleClient
import com.cak3d.biscuitmesh.geo.visiblePasses
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hourly background check (fired by [SkyAlertScheduler]): if a bright satellite will be visible
 * overhead in the next ~hour, post a heads-up notification. Keyless — uses the last known location
 * and the validated SGP4 + Sun-shadow pass math. Does nothing if sky alerts are off.
 */
class SkyAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? BiscuitMeshApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!app.settingsStore.skyAlerts.first()) return@launch
                val loc = lastKnown(context) ?: return@launch
                val now = System.currentTimeMillis()
                val tles = runCatching {
                    val c = TleClient()
                    (c.group(TleClient.STATIONS) + c.group(TleClient.VISUAL)).distinctBy { it.noradId }.take(30)
                }.getOrDefault(emptyList())
                if (tles.isEmpty()) return@launch
                val passes = visiblePasses(tles, loc.first, loc.second, now, windowHrs = 2, minPeakElDeg = 30.0)
                // A pass rising 10–70 min out — hourly checks catch each one about once.
                val soon = passes.filter { it.aosMs - now in 10 * 60_000L..70 * 60_000L }.minByOrNull { it.aosMs }
                    ?: return@launch
                val mins = ((soon.aosMs - now) / 60_000L).coerceAtLeast(1)
                NotificationHelper.notify(
                    app, 8080, "Bright pass soon",
                    "${soon.name} rises in ${mins} min — peak ${soon.peakElDeg.toInt()}° overhead.",
                )
            } finally {
                pending.finish()
            }
        }
    }

    private fun lastKnown(context: Context): Pair<Double, Double>? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return runCatching {
            val l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            l?.let { it.latitude to it.longitude }
        }.getOrNull()
    }
}
