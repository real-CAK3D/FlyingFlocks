package com.cak3d.biscuitmesh.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Current air quality + UV at the operator's location. Any field may be null if unreported. */
data class AirQuality(
    val usAqi: Int?,
    val pm25: Double?,
    val pm10: Double?,
    val ozone: Double?,
    val no2: Double?,
    val so2: Double?,
    val co: Double?,
    val uvIndex: Double?,
)

/**
 * Free, keyless air quality + UV from the Open-Meteo air-quality API — the environmental companion
 * to [WeatherClient]. One current reading per screen open (cached briefly), same open path the
 * weather and sky screens use.
 */
class AirQualityClient {
    private var cache: Pair<Long, AirQuality>? = null

    suspend fun air(lat: Double, lon: Double): AirQuality? = withContext(Dispatchers.IO) {
        cache?.let { (t, v) -> if (System.currentTimeMillis() - t < 120_000) return@withContext v }
        runCatching {
            val u = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon" +
                "&current=us_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,sulphur_dioxide,carbon_monoxide,uv_index" +
                "&timezone=auto"
            val c = JSONObject(get(u)).getJSONObject("current")
            fun i(k: String): Int? = if (c.has(k) && !c.isNull(k)) c.optInt(k) else null
            fun d(k: String): Double? = if (c.has(k) && !c.isNull(k)) c.optDouble(k) else null
            AirQuality(
                usAqi = i("us_aqi"),
                pm25 = d("pm2_5"),
                pm10 = d("pm10"),
                ozone = d("ozone"),
                no2 = d("nitrogen_dioxide"),
                so2 = d("sulphur_dioxide"),
                co = d("carbon_monoxide"),
                uvIndex = d("uv_index"),
            ).also { cache = System.currentTimeMillis() to it }
        }.getOrNull()
    }

    private fun get(u: String): String {
        val c = (URL(u).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "FlockedUp-AirQuality")
        }
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }
}
