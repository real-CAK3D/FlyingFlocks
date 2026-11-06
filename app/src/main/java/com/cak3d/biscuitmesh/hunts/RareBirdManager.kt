package com.cak3d.biscuitmesh.hunts

import android.location.Location
import com.cak3d.biscuitmesh.wardrive.LatLon
import com.cak3d.biscuitmesh.wardrive.SampleKind
import com.cak3d.biscuitmesh.wardrive.WardriveManager
import com.cak3d.biscuitmesh.wardrive.WardriveSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Runs the Rare Birds hunt. Unusual wardrive hits are promoted to RARE_FIND
 * birds; a handful of COLLECTIBLE birds are seeded near the operator the first
 * time we know where they are; coming within range captures one. The screen
 * feeds location in via [onLocation] so GPS only runs while the hunt is open.
 */
class RareBirdManager(
    private val scope: CoroutineScope,
    private val store: RareBirdStore,
    wardrive: WardriveManager?,
) {
    val birds: StateFlow<List<RareBird>> =
        store.birds.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _here = MutableStateFlow<LatLon?>(null)
    val here: StateFlow<LatLon?> = _here.asStateFlow()

    private val promoted = HashSet<String>()

    init {
        wardrive?.let { wd ->
            scope.launch {
                wd.samples.collect { map -> map.values.forEach { promoteIfRare(it) } }
            }
        }
    }

    private fun promoteIfRare(s: WardriveSample) {
        if (!promoted.add(s.id)) return
        // A "rare bird" must stay actually rare. Hidden networks and lone beacons are
        // everywhere, so a blanket promotion floods the guide with hundreds of them. We
        // gate each category behind a hash so only a sparse sample ever takes wing, and
        // cap the number of uncaught finds in flight so a long drive can't swamp the list.
        val h = s.id.hashCode()
        val label = when {
            s.kind == SampleKind.AP && s.label.isBlank() && (h and 0x3F) == 0 -> "Hidden Network"  // ~1 in 64
            s.kind == SampleKind.BT && s.label.isBlank() && (h and 0x3F) == 0 -> "Unknown Beacon"   // ~1 in 64
            (h and 0xFF) == 0 -> "Rare Signal"                                                      // ~1 in 256
            else -> null
        } ?: return
        val activeFinds = birds.value.count { it.kind == BirdKind.RARE_FIND && !it.captured }
        if (activeFinds >= MAX_ACTIVE_FINDS) return
        scope.launch {
            store.addIfNew(
                RareBird("find-${s.id}", label, BirdKind.RARE_FIND, s.lat, s.lon, note = s.label.ifBlank { s.id }),
            )
        }
    }

    /** Fed by the hunt screen with each fresh GPS fix. */
    fun onLocation(lat: Double, lon: Double) {
        _here.value = LatLon(lat, lon)
        scope.launch {
            ensureSeeded(lat, lon)
            captureNearby(lat, lon)
        }
    }

    private suspend fun ensureSeeded(lat: Double, lon: Double) {
        if (store.birds.first().any { it.kind == BirdKind.COLLECTIBLE }) return
        val rng = Random(lat.toRawBits() xor lon.toRawBits())
        val names = listOf("Golden Finch", "Night Heron", "Grey Shrike", "White Crow", "Lark Bunting")
        val seeded = names.mapIndexed { i, name ->
            val bearing = rng.nextDouble() * 2 * Math.PI
            val dist = 120.0 + rng.nextDouble() * 350.0
            val (clat, clon) = offset(lat, lon, dist, bearing)
            RareBird("seed-$i-${lat.toRawBits()}", name, BirdKind.COLLECTIBLE, clat, clon)
        }
        store.addAll(seeded)
    }

    private suspend fun captureNearby(lat: Double, lon: Double) {
        store.birds.first()
            .filter { !it.captured && distanceM(lat, lon, it.lat, it.lon) <= 30.0 }
            .forEach { store.capture(it.id) }
    }

    companion object {
        /** Most uncaught RARE_FIND birds to keep in flight at once, so the guide can't flood. */
        private const val MAX_ACTIVE_FINDS = 15

        fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val out = FloatArray(2)
            Location.distanceBetween(lat1, lon1, lat2, lon2, out)
            return out[0].toDouble()
        }

        /** Initial bearing from point 1 to point 2, in degrees (0 = north). */
        fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val out = FloatArray(2)
            Location.distanceBetween(lat1, lon1, lat2, lon2, out)
            return ((out[1] % 360f) + 360f) % 360f
        }

        private fun offset(lat: Double, lon: Double, distM: Double, bearingRad: Double): Pair<Double, Double> {
            val dLat = distM * cos(bearingRad) / 111_320.0
            val dLon = distM * sin(bearingRad) / (111_320.0 * cos(Math.toRadians(lat)))
            return (lat + dLat) to (lon + dLon)
        }
    }
}
