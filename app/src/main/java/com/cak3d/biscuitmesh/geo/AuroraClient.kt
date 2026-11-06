package com.cak3d.biscuitmesh.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/** Current geomagnetic activity, from NOAA's planetary K-index feed. */
data class AuroraNow(
    val kp: Double,
    val timeTag: String,
    /** The most recent readings (oldest→newest), for a small trend sparkline. */
    val recent: List<Double>,
) {
    /** NOAA G-scale storm label for the current Kp. */
    val stormLabel: String get() = when {
        kp >= 9.0 -> "G5 · extreme storm"
        kp >= 8.0 -> "G4 · severe storm"
        kp >= 7.0 -> "G3 · strong storm"
        kp >= 6.0 -> "G2 · moderate storm"
        kp >= 5.0 -> "G1 · minor storm"
        kp >= 4.0 -> "active"
        kp >= 3.0 -> "unsettled"
        else -> "quiet"
    }
}

/**
 * Live space-weather over NOAA SWPC's public planetary K-index product — the global
 * geomagnetic activity index (Kp 0–9) that drives how far from the poles the aurora
 * reaches. Free, no key. (Source: NOAA Space Weather Prediction Center.)
 */
class AuroraClient(private val base: String = "https://services.swpc.noaa.gov") {

    suspend fun current(): AuroraNow? = withContext(Dispatchers.IO) {
        runCatching { parse(get("$base/products/noaa-planetary-k-index.json")) }.getOrNull()
    }

    private fun get(u: String): String {
        val c = (URL(u).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "FlyingFlocks/1.0")
            setRequestProperty("Accept", "application/json")
        }
        return try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    // The product is an array of rows; row 0 is a header: ["time_tag","Kp","a_running","station_count"].
    private fun parse(body: String): AuroraNow? {
        val arr = JSONArray(body)
        if (arr.length() < 2) return null
        val kps = ArrayList<Pair<String, Double>>()
        for (i in 1 until arr.length()) {
            val row = arr.optJSONArray(i) ?: continue
            val time = row.optString(0)
            val kp = row.optString(1).toDoubleOrNull() ?: continue
            kps.add(time to kp)
        }
        if (kps.isEmpty()) return null
        val last = kps.last()
        return AuroraNow(last.second, last.first, kps.takeLast(8).map { it.second })
    }

    companion object {
        /**
         * A rough lowest latitude (degrees, N or S) where aurora may appear overhead for a
         * given Kp. Approximate — true visibility depends on geomagnetic latitude, darkness
         * and cloud — but good enough for a "could I see it tonight?" hint.
         */
        fun viewingLatForKp(kp: Double): Double = (67.0 - kp * 2.0).coerceIn(45.0, 67.0)

        /** A plain-language chance for a viewer at [lat], or null if we have no fix. */
        fun chanceAt(lat: Double?, kp: Double): String {
            if (lat == null) return "Waiting for a GPS fix to judge your latitude…"
            val need = viewingLatForKp(kp)
            val mine = abs(lat)
            return when {
                mine >= need -> "At your latitude (${"%.0f".format(mine)}°), aurora could be overhead tonight if skies are dark and clear."
                mine >= need - 5 -> "You're close — aurora may sit low on the poleward horizon from ${"%.0f".format(mine)}°."
                else -> "Unlikely at your latitude (${"%.0f".format(mine)}°) — Kp would need to climb to about ${"%.0f".format((67.0 - mine) / 2.0)} to reach you."
            }
        }
    }
}
