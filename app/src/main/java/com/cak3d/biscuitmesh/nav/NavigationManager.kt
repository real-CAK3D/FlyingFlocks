package com.cak3d.biscuitmesh.nav

import android.location.Location
import com.cak3d.biscuitmesh.audio.Speaker
import com.cak3d.biscuitmesh.location.LocationProvider
import com.cak3d.biscuitmesh.wardrive.LatLon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Live turn-by-turn state surfaced to the map banner. */
data class NavState(
    val destName: String,
    val instruction: String,
    val arrow: String,
    val distToManeuverM: Double,
    val thenInstruction: String?,
    val distRemainingM: Double,
    val etaS: Double,
    val offRoute: Boolean,
    val arrived: Boolean = false,
)

/**
 * In-app turn-by-turn. A route is fetched once (OSRM, with steps); guidance then
 * runs locally off the geometry so it keeps directing with no signal. Only
 * re-routing after a wrong turn needs the network. Ported from the What the Flock
 * web app's local-guidance approach.
 */
class NavigationManager(
    private val scope: CoroutineScope,
    private val location: LocationProvider,
    private val osrm: OsrmClient,
    private val speaker: Speaker,
) {
    private val _state = MutableStateFlow<NavState?>(null)
    val state: StateFlow<NavState?> = _state.asStateFlow()

    private val _routePoints = MutableStateFlow<List<LatLon>>(emptyList())
    val routePoints: StateFlow<List<LatLon>> = _routePoints.asStateFlow()

    /** The route as an ActiveRoute, so the existing map polyline/dest drawing just works. */
    private val _route = MutableStateFlow<ActiveRoute?>(null)
    val route: StateFlow<ActiveRoute?> = _route.asStateFlow()

    private var job: Job? = null
    private var destName = ""
    private var destLat = 0.0
    private var destLon = 0.0

    // Prepared route, in a local flat metric projection.
    private var xy: Array<DoubleArray> = emptyArray()
    private var cum: DoubleArray = DoubleArray(0)
    private var total = 0.0
    private var duration = 0.0
    private var kx = 1.0
    private var ky = 110540.0
    private var steps: List<PreppedStep> = emptyList()

    private var offRouteCount = 0
    private var rerouting = false
    private var lastRerouteMs = 0L

    private data class PreppedStep(val alongM: Double, val instruction: String, val arrow: String, val type: String)

    /** Begins navigating to a destination; cancels any current route. */
    fun start(name: String, lat: Double, lon: Double) {
        stop()
        destName = name; destLat = lat; destLon = lon
        job = scope.launch {
            val here = oneFix()
            if (here == null) { _state.value = arrivedState("No location fix — try again"); return@launch }
            if (!loadRoute(here.first, here.second)) {
                _state.value = NavState(name, "No route found", "⚠", 0.0, null, 0.0, 0.0, false)
                return@launch
            }
            speaker.say("Starting route to $name")
            location.locationUpdates(1000L).collect { onFix(it) }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        _state.value = null
        _routePoints.value = emptyList()
        _route.value = null
        steps = emptyList()
        offRouteCount = 0
        speaker.stop()
    }

    private suspend fun oneFix(): Pair<Double, Double>? =
        withTimeoutOrNull(12_000) {
            val loc = location.locationUpdates(1000L).first()
            loc.latitude to loc.longitude
        }

    private suspend fun loadRoute(fromLat: Double, fromLon: Double): Boolean {
        val r = osrm.routeDetailed(fromLat, fromLon, destLat, destLon) ?: return false
        prep(r)
        _routePoints.value = r.points
        _route.value = ActiveRoute(destName, destLat, destLon, r.points, r.distanceM, r.durationS)
        return steps.isNotEmpty() || r.points.isNotEmpty()
    }

    private fun prep(r: DetailedRoute) {
        val pts = r.points
        kx = 111320.0 * cos(pts[0].lat * Math.PI / 180.0)
        ky = 110540.0
        xy = Array(pts.size) { doubleArrayOf(pts[it].lon * kx, pts[it].lat * ky) }
        cum = DoubleArray(pts.size)
        for (i in 1 until pts.size) {
            cum[i] = cum[i - 1] + hypot(xy[i][0] - xy[i - 1][0], xy[i][1] - xy[i - 1][1])
        }
        total = cum.last()
        duration = r.durationS
        // Snap each maneuver to the nearest route vertex to get its along-distance.
        var from = 0
        steps = r.steps.map { s ->
            val q = doubleArrayOf(s.lon * kx, s.lat * ky)
            var best = from; var bd = Double.MAX_VALUE
            var i = from
            while (i < xy.size) {
                val d = hypot(xy[i][0] - q[0], xy[i][1] - q[1])
                if (d < bd) { bd = d; best = i }
                if (bd < 3 && d > 50) break
                i++
            }
            from = best
            PreppedStep(cum[best], stepText(s), stepArrow(s), s.type)
        }
    }

    private fun onFix(loc: Location) {
        if (xy.isEmpty()) return
        val q = doubleArrayOf(loc.longitude * kx, loc.latitude * ky)
        // Snap to the polyline: nearest segment → along-distance + perpendicular offset.
        var bestD = Double.MAX_VALUE
        var along = 0.0
        for (i in 0 until xy.size - 1) {
            val (d, t) = segDist(q, xy[i], xy[i + 1])
            if (d < bestD) { bestD = d; along = cum[i] + t * (cum[i + 1] - cum[i]) }
        }
        val offRoute = bestD > OFF_ROUTE_M

        // Arrival.
        val remaining = (total - along).coerceAtLeast(0.0)
        if (remaining <= ARRIVE_M) {
            speaker.say("You have arrived at $destName", flush = true)
            _state.value = arrivedState("Arrived at $destName")
            job?.cancel(); job = null
            return
        }

        // Off-route → re-route after a few consecutive bad fixes.
        if (offRoute) {
            offRouteCount++
            if (offRouteCount >= OFF_ROUTE_FIXES && !rerouting &&
                System.currentTimeMillis() - lastRerouteMs > REROUTE_GAP_MS
            ) {
                reroute(loc.latitude, loc.longitude)
            }
        } else {
            offRouteCount = 0
        }

        // Next maneuver = first step whose along-distance is ahead of us.
        var nextIdx = steps.indexOfFirst { it.alongM > along + 5 }
        if (nextIdx < 0) nextIdx = steps.lastIndex
        val next = steps.getOrNull(nextIdx)
        val then = steps.getOrNull(nextIdx + 1)
        val distToManeuver = ((next?.alongM ?: total) - along).coerceAtLeast(0.0)

        announce(nextIdx, next, distToManeuver)

        _state.value = NavState(
            destName = destName,
            instruction = next?.instruction ?: "Continue",
            arrow = next?.arrow ?: "↑",
            distToManeuverM = distToManeuver,
            thenInstruction = then?.instruction,
            distRemainingM = remaining,
            etaS = duration * (remaining / total.coerceAtLeast(1.0)),
            offRoute = offRoute,
        )
    }

    // Per-step announcement latches so we speak each maneuver at most twice (far + near).
    private var announcedFarFor = -1
    private var announcedNearFor = -1

    private fun announce(idx: Int, step: PreppedStep?, dist: Double) {
        if (step == null) return
        if (dist <= NEAR_M && announcedNearFor != idx) {
            announcedNearFor = idx
            announcedFarFor = idx
            speaker.say(step.instruction, flush = true)
        } else if (dist in NEAR_M..FAR_M && announcedFarFor != idx) {
            announcedFarFor = idx
            speaker.say("In ${spokenDist(dist)}, ${step.instruction.replaceFirstChar { it.lowercase() }}")
        }
    }

    private fun reroute(lat: Double, lon: Double) {
        rerouting = true
        lastRerouteMs = System.currentTimeMillis()
        speaker.say("Recalculating", flush = true)
        scope.launch {
            val ok = loadRoute(lat, lon)
            offRouteCount = 0
            announcedFarFor = -1; announcedNearFor = -1
            rerouting = false
            if (!ok) _state.value = _state.value?.copy(offRoute = true)
        }
    }

    private fun arrivedState(msg: String) =
        NavState(destName, msg, "🏁", 0.0, null, 0.0, 0.0, false, arrived = true)

    companion object {
        private const val OFF_ROUTE_M = 45.0
        private const val OFF_ROUTE_FIXES = 3
        private const val REROUTE_GAP_MS = 15_000L
        private const val ARRIVE_M = 30.0
        private const val NEAR_M = 70.0
        private const val FAR_M = 500.0

        /** Closest distance from p to segment a-b, and the clamped parameter t. */
        private fun segDist(p: DoubleArray, a: DoubleArray, b: DoubleArray): Pair<Double, Double> {
            val dx = b[0] - a[0]; val dy = b[1] - a[1]; val l2 = dx * dx + dy * dy
            var t = if (l2 != 0.0) ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / l2 else 0.0
            t = t.coerceIn(0.0, 1.0)
            return hypot(p[0] - a[0] - t * dx, p[1] - a[1] - t * dy) to t
        }

        /** A spoken distance in imperial units. */
        fun spokenDist(m: Double): String {
            val miles = m / 1609.34
            return when {
                miles >= 0.9 -> "%.0f miles".format(miles)
                miles >= 0.55 -> "a half mile"
                miles >= 0.3 -> "a quarter mile"
                else -> {
                    val feet = (m * 3.28084 / 50).roundToInt() * 50
                    "$feet feet"
                }
            }
        }

        private val ARROWS = mapOf(
            "straight" to "↑", "slight left" to "↖", "left" to "←", "sharp left" to "↙",
            "slight right" to "↗", "right" to "→", "sharp right" to "↘", "uturn" to "↶",
        )

        fun stepArrow(s: RawStep): String {
            val t = s.type
            if (t == "arrive") return "🏁"
            if (t.contains("roundabout") || t.contains("rotary")) return "⟳"
            return ARROWS[s.modifier] ?: "↑"
        }

        private val COMPASS8 = listOf("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest")

        private fun ordinal(n: Int): String {
            val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
                1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th"
            }
            return "$n$suffix"
        }

        /** Maneuver → spoken/displayed instruction (ported from What the Flock's stepText). */
        fun stepText(s: RawStep): String {
            val mod = s.modifier
            val road = s.name
            val onto = if (road.isNotBlank()) " onto $road" else ""
            val side = if (mod.contains("left")) "left" else "right"
            return when (s.type) {
                "depart" -> "Head ${COMPASS8[((s.bearingAfter + 22) / 45) % 8]}" + if (road.isNotBlank()) " on $road" else ""
                "arrive" -> if (mod == "left" || mod == "right") "Arrive at your destination on the $mod" else "Arrive at your destination"
                "turn", "end of road" -> when (mod) {
                    "uturn" -> "Make a U-turn$onto"
                    "straight" -> "Continue straight$onto"
                    else -> "Turn $mod$onto"
                }
                "new name", "continue" -> if (mod.isNotBlank() && mod != "straight") "Keep $mod$onto" else "Continue$onto"
                "merge" -> "Merge $side$onto"
                "on ramp" -> "Take the ramp on the $side" + if (road.isNotBlank()) " to $road" else ""
                "off ramp" -> "Take the exit on the $side"
                "fork" -> "Keep $side at the fork$onto"
                "roundabout", "rotary", "roundabout turn" ->
                    if (s.exit > 0) "At the roundabout, take the ${ordinal(s.exit)} exit$onto" else "Enter the roundabout$onto"
                "exit roundabout", "exit rotary" -> "Exit the roundabout$onto"
                else -> "Continue$onto"
            }
        }
    }
}

/** Formats metres as an imperial distance for the banner. */
fun fmtDistImperial(m: Double): String {
    val miles = m / 1609.34
    return if (miles >= 0.2) "%.1f mi".format(miles) else "${(m * 3.28084 / 10).roundToInt() * 10} ft"
}

/** Formats seconds as a short ETA string. */
fun fmtEta(seconds: Double): String {
    val mins = (seconds / 60).roundToInt()
    return if (mins < 60) "$mins min" else "${mins / 60} h ${mins % 60} min"
}
