package com.cak3d.biscuitmesh.nav

import com.cak3d.biscuitmesh.wardrive.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A driving route: the geometry to draw plus distance (m) and duration (s). */
data class Route(val points: List<LatLon>, val distanceM: Double, val durationS: Double)

/** One OSRM maneuver along the route, before guidance snaps it to the line. */
data class RawStep(
    val lat: Double,
    val lon: Double,
    val type: String,
    val modifier: String,
    val name: String,
    val bearingAfter: Int,
    val exit: Int,
)

/** A route plus its turn-by-turn maneuvers, for in-app guidance. */
data class DetailedRoute(
    val points: List<LatLon>,
    val steps: List<RawStep>,
    val distanceM: Double,
    val durationS: Double,
)

/**
 * Routing via the public OSRM server (FOSSGIS). Free for reasonable, non-commercial
 * use at ≤1 req/s with ODbL attribution and a User-Agent — we only call it on a
 * deliberate "navigate" tap. Returns the full geometry as lat/lon points.
 */
class OsrmClient(private val base: String = "https://router.project-osrm.org") {

    suspend fun route(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Route? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$base/route/v1/driving/$fromLon,$fromLat;$toLon,$toLat?overview=full&geometries=geojson"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "FlyingFlocks/0.9 (OSRM; cak3d)")
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code !in 200..299) return@runCatching null
                parse(body)
            }.getOrNull()
        }

    /** Same as [route] but asks OSRM for step-by-step maneuvers for turn-by-turn. */
    suspend fun routeDetailed(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): DetailedRoute? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$base/route/v1/driving/$fromLon,$fromLat;$toLon,$toLat" +
                    "?overview=full&geometries=geojson&steps=true"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "FlyingFlocks/0.11 (OSRM; cak3d)")
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code !in 200..299) return@runCatching null
                parseDetailed(body)
            }.getOrNull()
        }

    private fun parseDetailed(body: String): DetailedRoute? {
        val json = JSONObject(body)
        if (json.optString("code") != "Ok") return null
        val r = json.optJSONArray("routes")?.optJSONObject(0) ?: return null
        val coords = r.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        val pts = ArrayList<LatLon>(coords.length())
        for (i in 0 until coords.length()) {
            val c = coords.optJSONArray(i) ?: continue
            pts.add(LatLon(c.optDouble(1), c.optDouble(0)))
        }
        if (pts.isEmpty()) return null
        val steps = ArrayList<RawStep>()
        val legs = r.optJSONArray("legs")
        if (legs != null) {
            for (li in 0 until legs.length()) {
                val legSteps = legs.optJSONObject(li)?.optJSONArray("steps") ?: continue
                for (si in 0 until legSteps.length()) {
                    val s = legSteps.optJSONObject(si) ?: continue
                    val m = s.optJSONObject("maneuver") ?: continue
                    val loc = m.optJSONArray("location") ?: continue
                    steps.add(
                        RawStep(
                            lat = loc.optDouble(1),
                            lon = loc.optDouble(0),
                            type = m.optString("type", "continue"),
                            modifier = m.optString("modifier", ""),
                            name = s.optString("name", ""),
                            bearingAfter = m.optInt("bearing_after", 0),
                            exit = m.optInt("exit", 0),
                        ),
                    )
                }
            }
        }
        return DetailedRoute(pts, steps, r.optDouble("distance", 0.0), r.optDouble("duration", 0.0))
    }

    private fun parse(body: String): Route? {
        val json = JSONObject(body)
        if (json.optString("code") != "Ok") return null
        val routes = json.optJSONArray("routes") ?: return null
        val r = routes.optJSONObject(0) ?: return null
        val coords = r.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        val pts = ArrayList<LatLon>(coords.length())
        for (i in 0 until coords.length()) {
            val c = coords.optJSONArray(i) ?: continue
            // GeoJSON is [lon, lat].
            pts.add(LatLon(c.optDouble(1), c.optDouble(0)))
        }
        if (pts.isEmpty()) return null
        return Route(pts, r.optDouble("distance", 0.0), r.optDouble("duration", 0.0))
    }
}
