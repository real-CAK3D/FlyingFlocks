package com.cak3d.biscuitmesh.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Streams device location for wardriving. Normally the phone's fused GPS, but when
 * [useBoard] is on and an external fix is available (a Meshtastic node's GPS, or the
 * dongle's), that is used instead — so you can wardrive off a board's GPS, not the
 * phone's. The phone fix is always kept as a fallback so location never goes dead.
 * Callers must hold a location permission.
 */
class LocationProvider(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    /** When true, prefer [externalFix] over the phone's GPS. */
    @Volatile var useBoard: Boolean = false

    /** Supplies an external (lat, lon) fix from a board, or null if none is current. */
    @Volatile var externalFix: (() -> Pair<Double, Double>?)? = null

    private fun boardLocation(fix: Pair<Double, Double>): Location =
        Location("board").apply {
            latitude = fix.first; longitude = fix.second; accuracy = 8f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
        }

    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMs: Long = 2000L): Flow<Location> = callbackFlow {
        var lastFused: Location? = null
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    lastFused = loc
                    // Only forward the phone fix when we aren't actively using a board fix.
                    if (!useBoard || externalFix?.invoke() == null) trySend(loc)
                }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        // Board ticker: when board mode is on and a fix exists, emit it; otherwise fall back.
        val job = launch {
            while (true) {
                if (useBoard) {
                    val f = externalFix?.invoke()
                    if (f != null) trySend(boardLocation(f)) else lastFused?.let { trySend(it) }
                }
                delay(intervalMs)
            }
        }
        awaitClose { client.removeLocationUpdates(callback); job.cancel() }
    }

    /** A single best-effort fix: the board's when board mode is on and one is available. */
    fun oneShotBoardFix(): Location? {
        if (!useBoard) return null
        return externalFix?.invoke()?.let { boardLocation(it) }
    }
}
