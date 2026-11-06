package com.cak3d.biscuitmesh.geo

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One aircraft currently in the air near the phone, from live ADS-B. */
data class Aircraft(
    val hex: String,
    val flight: String,
    val type: String,
    val lat: Double,
    val lon: Double,
    val altFt: Int,
    val gsKt: Int,
    val trackDeg: Int,
    val distanceKm: Double,
    val bearingDeg: Float,
)

/**
 * Live ADS-B over the public airplanes.live feed — aircraft broadcasting their position
 * right now, near a point. Free, no key. (Attribution: airplanes.live.)
 */
class AdsbClient(private val base: String = "https://api.airplanes.live/v2") {

    suspend fun nearby(lat: Double, lon: Double, radiusNm: Int = 100): List<Aircraft> =
        withContext(Dispatchers.IO) {
            runCatching { parse(get("$base/point/$lat/$lon/$radiusNm"), lat, lon) }.getOrDefault(emptyList())
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

    private fun parse(body: String, lat: Double, lon: Double): List<Aircraft> {
        val arr = JSONObject(body).optJSONArray("ac") ?: return emptyList()
        val out = ArrayList<Aircraft>()
        val res = FloatArray(2)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val alat = o.optDouble("lat", Double.NaN)
            val alon = o.optDouble("lon", Double.NaN)
            if (alat.isNaN() || alon.isNaN()) continue
            val alt = (o.opt("alt_baro") as? Number)?.toInt() ?: o.optInt("alt_geom", 0)
            Location.distanceBetween(lat, lon, alat, alon, res)
            out.add(
                Aircraft(
                    hex = o.optString("hex"),
                    flight = o.optString("flight").trim().ifBlank { o.optString("r").trim() },
                    type = o.optString("t").trim(),
                    lat = alat, lon = alon, altFt = alt,
                    gsKt = o.optDouble("gs", 0.0).toInt(),
                    trackDeg = o.optDouble("track", 0.0).toInt(),
                    distanceKm = res[0] / 1000.0,
                    bearingDeg = res[1],
                ),
            )
        }
        return out.sortedBy { it.distanceKm }
    }
}
