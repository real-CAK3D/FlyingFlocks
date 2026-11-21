package com.cak3d.biscuitmesh.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** How loud an alert is — drives the colour and the sort order. */
enum class AlertSeverity(val rank: Int) { EXTREME(4), SEVERE(3), MODERATE(2), MINOR(1), INFO(0) }

/** One active warning / event, normalised across every source. */
data class ThreatAlert(
    val source: String,          // "NWS", "USGS", "GDACS"
    val kind: String,            // "Tornado Warning", "M4.2 Earthquake", "Tropical Cyclone"…
    val severity: AlertSeverity,
    val title: String,
    val area: String,            // areaDesc / place / country
    val detail: String,          // headline / description, trimmed
    val lat: Double? = null,
    val lon: Double? = null,
    val timeMs: Long = 0L,
    val url: String? = null,
    val distanceKm: Double? = null,
)

/** The alerts split into what's on top of you vs. what's happening elsewhere. */
data class AlertsBundle(
    val local: List<ThreatAlert>,    // NWS warnings for your point + quakes/events near you
    val global: List<ThreatAlert>,   // significant quakes + global disasters further out
)

/**
 * Free, no-key threat feed for a security dashboard:
 *  - NWS (api.weather.gov) active warnings for your exact point — tornado, flood, winter, heat…
 *  - USGS earthquakes (past day, M2.5+), worldwide, distance-ranked to you.
 *  - GDACS global disasters — tropical cyclones, floods, volcanoes, droughts, wildfires.
 * Each source is fetched independently; one failing never blanks the others.
 */
class AlertsClient {

    /** Everything, split into "your area" and "global", most-severe first. */
    suspend fun all(lat: Double?, lon: Double?): AlertsBundle = withContext(Dispatchers.IO) {
        val nws = if (lat != null && lon != null) runCatching { nwsAlerts(lat, lon) }.getOrDefault(emptyList()) else emptyList()
        val quakes = runCatching { earthquakes(lat, lon) }.getOrDefault(emptyList())
        val gdacs = runCatching { globalDisasters(lat, lon) }.getOrDefault(emptyList())

        // Local = every NWS warning (they're already for your point) + anything within ~400 km.
        val nearCut = 400.0
        val local = (nws + quakes.filter { (it.distanceKm ?: 9e9) <= nearCut } +
            gdacs.filter { (it.distanceKm ?: 9e9) <= nearCut })
            .sortedWith(compareByDescending<ThreatAlert> { it.severity.rank }.thenByDescending { it.timeMs })
        val global = (quakes.filter { (it.distanceKm ?: 9e9) > nearCut } +
            gdacs.filter { (it.distanceKm ?: 9e9) > nearCut })
            .sortedWith(compareByDescending<ThreatAlert> { it.severity.rank }.thenByDescending { it.timeMs })
        AlertsBundle(local, global)
    }

    private fun nwsAlerts(lat: Double, lon: Double): List<ThreatAlert> {
        val j = JSONObject(get("https://api.weather.gov/alerts/active?point=$lat,$lon", geoJson = true))
        val feats = j.optJSONArray("features") ?: return emptyList()
        val out = ArrayList<ThreatAlert>()
        for (i in 0 until feats.length()) {
            val p = feats.getJSONObject(i).optJSONObject("properties") ?: continue
            val sev = when (p.optString("severity")) {
                "Extreme" -> AlertSeverity.EXTREME
                "Severe" -> AlertSeverity.SEVERE
                "Moderate" -> AlertSeverity.MODERATE
                "Minor" -> AlertSeverity.MINOR
                else -> AlertSeverity.INFO
            }
            out.add(
                ThreatAlert(
                    source = "NWS",
                    kind = p.optString("event", "Weather Alert"),
                    severity = sev,
                    title = p.optString("event", "Weather Alert"),
                    area = p.optString("areaDesc", "your area"),
                    detail = p.optString("headline").ifBlank { p.optString("description") }.take(900),
                    timeMs = parseIso(p.optString("effective")),
                    url = p.optString("id").ifBlank { null },
                    distanceKm = 0.0,
                ),
            )
        }
        return out
    }

    private fun earthquakes(lat: Double?, lon: Double?): List<ThreatAlert> {
        // M2.5+ in the past day, worldwide.
        val j = JSONObject(get("https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_day.geojson"))
        val feats = j.optJSONArray("features") ?: return emptyList()
        val out = ArrayList<ThreatAlert>()
        for (i in 0 until feats.length()) {
            val f = feats.getJSONObject(i)
            val p = f.optJSONObject("properties") ?: continue
            val g = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            val qlon = g.optDouble(0); val qlat = g.optDouble(1)
            val mag = p.optDouble("mag", 0.0)
            val dist = if (lat != null && lon != null) haversineKm(lat, lon, qlat, qlon) else null
            val sev = when {
                mag >= 6.0 -> AlertSeverity.EXTREME
                mag >= 5.0 -> AlertSeverity.SEVERE
                mag >= 4.0 -> AlertSeverity.MODERATE
                dist != null && dist < 150 && mag >= 3.0 -> AlertSeverity.MODERATE
                else -> AlertSeverity.MINOR
            }
            out.add(
                ThreatAlert(
                    source = "USGS",
                    kind = "M%.1f Earthquake".format(mag),
                    severity = sev,
                    title = p.optString("title", "Earthquake"),
                    area = p.optString("place", "—"),
                    detail = "Magnitude %.1f earthquake. %s".format(mag, p.optString("place", "")),
                    lat = qlat, lon = qlon,
                    timeMs = p.optLong("time", 0L),
                    url = p.optString("url").ifBlank { null },
                    distanceKm = dist,
                ),
            )
        }
        return out
    }

    private fun globalDisasters(lat: Double?, lon: Double?): List<ThreatAlert> {
        val j = JSONObject(get("https://www.gdacs.org/gdacsapi/api/events/geteventlist/MAP"))
        val feats = j.optJSONArray("features") ?: return emptyList()
        val out = ArrayList<ThreatAlert>()
        for (i in 0 until feats.length()) {
            val f = feats.getJSONObject(i)
            val p = f.optJSONObject("properties") ?: continue
            val g = f.optJSONObject("geometry")?.optJSONArray("coordinates")
            val elon = g?.optDouble(0); val elat = g?.optDouble(1)
            val type = when (p.optString("eventtype").uppercase()) {
                "EQ" -> "Earthquake"; "TC" -> "Tropical Cyclone"; "FL" -> "Flood"
                "VO" -> "Volcano"; "DR" -> "Drought"; "WF" -> "Wildfire"; else -> "Disaster"
            }
            val sev = when (p.optString("alertlevel").lowercase()) {
                "red" -> AlertSeverity.EXTREME
                "orange" -> AlertSeverity.SEVERE
                "green" -> AlertSeverity.MINOR
                else -> AlertSeverity.MODERATE
            }
            val dist = if (lat != null && lon != null && elat != null && elon != null)
                haversineKm(lat, lon, elat, elon) else null
            val name = p.optString("eventname").ifBlank { p.optString("name") }.ifBlank { type }
            out.add(
                ThreatAlert(
                    source = "GDACS",
                    kind = type,
                    severity = sev,
                    title = name,
                    area = p.optString("country").ifBlank { "—" },
                    detail = p.optString("htmldescription").replace(Regex("<[^>]*>"), " ").trim().take(900),
                    lat = elat, lon = elon,
                    timeMs = parseIso(p.optString("fromdate")),
                    url = p.optJSONObject("url")?.optString("report")?.ifBlank { null },
                    distanceKm = dist,
                ),
            )
        }
        return out
    }

    private fun get(u: String, geoJson: Boolean = false): String {
        val c = (URL(u).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "FlyingFlocks/1.0 (security dashboard)")
            setRequestProperty("Accept", if (geoJson) "application/geo+json" else "application/json")
        }
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }
}

/** Great-circle distance in km. */
fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * asin(sqrt(a).coerceIn(0.0, 1.0))
}

/** Parse an ISO-8601 timestamp to epoch millis; 0 on failure. */
private fun parseIso(s: String): Long {
    if (s.isBlank()) return 0L
    return runCatching {
        // Handles 2026-11-21T08:00:00-05:00 and 2026-11-21T08:00:00Z.
        val fixed = s.replace(Regex("([+-]\\d{2}):(\\d{2})$"), "$1$2").replace("Z", "+0000")
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US).parse(fixed)?.time ?: 0L
    }.getOrDefault(0L)
}
