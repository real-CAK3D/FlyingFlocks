package com.cak3d.biscuitmesh.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/**
 * Minimal OpenStreetMap Overpass client. Pulls nearby cameras (speed cameras and
 * Flock/ALPR surveillance, per the DeFlock tagging) and the nearest road's posted
 * speed limit. Free + ODbL — attribution shown in the app's About.
 */
class OverpassClient(private val endpoint: String = "https://overpass-api.de/api/interpreter") {

    suspend fun cameras(lat: Double, lon: Double, radiusM: Int = 4000): List<Hazard> = withContext(Dispatchers.IO) {
        val q = """
            [out:json][timeout:25];
            (
              node(around:$radiusM,$lat,$lon)["highway"="speed_camera"];
              node(around:$radiusM,$lat,$lon)["man_made"="surveillance"];
            );
            out tags 400;
        """.trimIndent()
        runCatching { parseCameras(post(q)) }.getOrDefault(emptyList())
    }

    suspend fun nearestSpeedLimit(lat: Double, lon: Double, radiusM: Int = 45): SpeedLimit? = withContext(Dispatchers.IO) {
        val q = """
            [out:json][timeout:20];
            way(around:$radiusM,$lat,$lon)["highway"]["maxspeed"];
            out tags 25;
        """.trimIndent()
        runCatching {
            val json = JSONObject(post(q))
            val els = json.optJSONArray("elements") ?: return@runCatching null
            for (i in 0 until els.length()) {
                val tags = els.optJSONObject(i)?.optJSONObject("tags") ?: continue
                parseMaxspeed(tags.optString("maxspeed"))?.let { return@runCatching it }
            }
            null
        }.getOrNull()
    }

    private fun parseCameras(body: String): List<Hazard> {
        val els = JSONObject(body).optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<Hazard>()
        for (i in 0 until els.length()) {
            val e = els.optJSONObject(i) ?: continue
            val lat = e.optDouble("lat", Double.NaN)
            val lon = e.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue
            val tags = e.optJSONObject("tags") ?: JSONObject()
            val kind = classify(tags) ?: continue
            out.add(Hazard("node/${e.optLong("id")}", kind, lat, lon, tags.optString("operator", tags.optString("manufacturer", ""))))
        }
        return out
    }

    private fun classify(tags: JSONObject): HazardKind? {
        if (tags.optString("highway") == "speed_camera") {
            return if (tags.optString("speed_camera") == "traffic_signals") HazardKind.RED_LIGHT else HazardKind.SPEED_CAMERA
        }
        if (tags.optString("man_made") == "surveillance") {
            val type = tags.optString("surveillance:type").lowercase()
            return when {
                type == "alpr" -> HazardKind.FLOCK
                type == "camera" -> HazardKind.CAMERA
                else -> null   // skip guards, domes, etc. we can't place usefully
            }
        }
        return null
    }

    private fun parseMaxspeed(raw: String): SpeedLimit? {
        val t = raw.trim().lowercase()
        Regex("""^(\d+)\s*mph$""").find(t)?.let {
            val mph = it.groupValues[1].toInt()
            return SpeedLimit(kmh = (mph * 1.60934).roundToInt(), mph = mph, posted = "$mph mph")
        }
        Regex("""^(\d+)(\s*km/?h)?$""").find(t)?.let {
            val kmh = it.groupValues[1].toInt()
            return SpeedLimit(kmh = kmh, mph = (kmh / 1.60934).roundToInt(), posted = "$kmh km/h")
        }
        return null
    }

    private fun post(query: String): String {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", "FlyingFlocks/0.9 (OSM Overpass; cak3d)")
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        DataOutputStream(conn.outputStream).use { it.writeBytes("data=" + URLEncoder.encode(query, "UTF-8")) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) throw RuntimeException("Overpass $code")
        return text
    }
}
