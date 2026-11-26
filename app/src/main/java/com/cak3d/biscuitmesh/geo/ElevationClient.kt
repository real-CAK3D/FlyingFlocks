package com.cak3d.biscuitmesh.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Keyless terrain elevation for a set of points, from the Open-Meteo elevation API. */
object ElevationClient {

    /** Elevation (metres) for each lat/lon, in order. Empty on failure. Max ~100 points per call. */
    suspend fun elevations(pts: List<Pair<Double, Double>>): List<Double> = withContext(Dispatchers.IO) {
        if (pts.isEmpty()) return@withContext emptyList()
        val capped = pts.take(100)
        val lats = capped.joinToString(",") { String.format(Locale.US, "%.5f", it.first) }
        val lons = capped.joinToString(",") { String.format(Locale.US, "%.5f", it.second) }
        runCatching {
            val u = "https://api.open-meteo.com/v1/elevation?latitude=$lats&longitude=$lons"
            val c = (URL(u).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 15_000
                setRequestProperty("User-Agent", "FlyingFlocks/1.0")
            }
            val txt = c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
            val arr = JSONObject(txt).getJSONArray("elevation")
            (0 until arr.length()).map { arr.getDouble(it) }
        }.getOrDefault(emptyList())
    }
}
