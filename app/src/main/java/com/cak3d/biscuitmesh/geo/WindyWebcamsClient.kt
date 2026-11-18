package com.cak3d.biscuitmesh.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Windy Webcams (v3) — public live webcams near a point, many of them traffic / DOT
 * cams. Needs a free API key (stored on-device under the "windy_webcams" slot). Returns
 * each cam's latest still plus its location. Consume-only.
 */
class WindyWebcamsClient {

    data class Webcam(
        val id: String,
        val title: String,
        val lat: Double,
        val lon: Double,
        val previewUrl: String,   // latest still image
    )

    suspend fun nearby(lat: Double, lon: Double, radiusKm: Int, apiKey: String): List<Webcam> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext emptyList()
            runCatching {
                val url = "https://api.windy.com/webcams/api/v3/webcams" +
                    "?nearby=$lat,$lon,$radiusKm&limit=40&include=images,location"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("x-windy-api-key", apiKey)
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 12_000
                    readTimeout = 20_000
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code !in 200..299) throw RuntimeException("Windy $code")
                val arr = JSONObject(body).optJSONArray("webcams") ?: return@runCatching emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val c = arr.optJSONObject(i) ?: continue
                        val loc = c.optJSONObject("location")
                        val imgs = c.optJSONObject("images")?.optJSONObject("current")
                        val preview = imgs?.optString("preview") ?: imgs?.optString("thumbnail") ?: ""
                        add(Webcam(
                            c.optString("webcamId", c.optString("id", i.toString())),
                            c.optString("title", "Webcam"),
                            loc?.optDouble("latitude", lat) ?: lat,
                            loc?.optDouble("longitude", lon) ?: lon,
                            preview,
                        ))
                    }
                }.filter { it.previewUrl.isNotBlank() }
            }.getOrDefault(emptyList())
        }
}
