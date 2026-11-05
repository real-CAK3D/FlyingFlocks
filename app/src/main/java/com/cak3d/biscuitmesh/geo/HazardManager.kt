package com.cak3d.biscuitmesh.geo

import android.location.Location
import com.cak3d.biscuitmesh.wardrive.WardriveManager
import com.cak3d.biscuitmesh.wardrive.WardriveState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A live warning that a camera is close and the vehicle is over the limit. */
data class HazardAlert(val hazard: Hazard, val distanceM: Int, val speedMph: Int, val limitMph: Int?)

/**
 * Pulls nearby cameras and the posted speed limit from OpenStreetMap as the
 * wardrive moves, persists the cameras, and raises an alert when one is close
 * and the vehicle is over the limit. Fetches are distance-throttled to respect
 * Overpass and the battery.
 */
class HazardManager(
    private val scope: CoroutineScope,
    wardrive: WardriveManager,
    private val overpass: OverpassClient,
    private val store: HazardStore,
) {
    val hazards: StateFlow<List<Hazard>> =
        store.hazards.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _speedLimit = MutableStateFlow<SpeedLimit?>(null)
    val speedLimit: StateFlow<SpeedLimit?> = _speedLimit.asStateFlow()

    private val _alert = MutableStateFlow<HazardAlert?>(null)
    val alert: StateFlow<HazardAlert?> = _alert.asStateFlow()

    /** Remove one camera from the map/store (the hazards flow updates automatically). */
    suspend fun remove(id: String) = store.remove(id)

    private var lastCamLat = 0.0
    private var lastCamLon = 0.0
    private var camLoaded = false
    @Volatile private var camInFlight = false

    private var lastLimitLat = 0.0
    private var lastLimitLon = 0.0
    @Volatile private var limitInFlight = false

    init {
        scope.launch { wardrive.state.collect { onState(it) } }
    }

    private fun onState(s: WardriveState) {
        if (!s.active) { _alert.value = null; return }
        val lat = s.lastLat ?: return
        val lon = s.lastLon ?: return

        // Cameras: refetch when we've moved ~800 m into new ground.
        if (!camInFlight && (!camLoaded || dist(lat, lon, lastCamLat, lastCamLon) > 800)) {
            camInFlight = true
            lastCamLat = lat; lastCamLon = lon; camLoaded = true
            scope.launch {
                runCatching { store.merge(overpass.cameras(lat, lon)) }
                camInFlight = false
            }
        }

        // Speed limit: refresh every ~120 m.
        if (!limitInFlight && dist(lat, lon, lastLimitLat, lastLimitLon) > 120) {
            limitInFlight = true
            lastLimitLat = lat; lastLimitLon = lon
            scope.launch {
                val sl = runCatching { overpass.nearestSpeedLimit(lat, lon) }.getOrNull()
                if (sl != null) _speedLimit.value = sl
                limitInFlight = false
            }
        }

        updateAlert(lat, lon, s.speedMps ?: 0f)
    }

    private fun updateAlert(lat: Double, lon: Double, speedMps: Float) {
        val speedMph = (speedMps * 2.236936f).toInt()
        val limit = _speedLimit.value?.mph
        val nearest = hazards.value.minByOrNull { dist(lat, lon, it.lat, it.lon) } ?: run { _alert.value = null; return }
        val d = dist(lat, lon, nearest.lat, nearest.lon)
        val over = if (limit != null) speedMph > limit else speedMph > 30
        _alert.value = if (d <= 300 && over) HazardAlert(nearest, d.toInt(), speedMph, limit) else null
    }

    private fun dist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val out = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, out)
        return out[0].toDouble()
    }
}
