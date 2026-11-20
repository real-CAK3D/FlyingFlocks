package com.cak3d.biscuitmesh.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Current conditions right now at the operator's location. */
data class WeatherNow(
    val tempF: Int,
    val feelsF: Int,
    val humidity: Int,
    val windMph: Int,
    val windDir: Int,
    val code: Int,
    val isDay: Boolean,
) {
    val desc: String get() = wmoDesc(code)
    val emoji: String get() = wmoEmoji(code, isDay)
    val windCardinal: String get() {
        val d = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return d[(((windDir + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)]
    }
}

/** One hour in the forecast (local time). */
data class WeatherHour(val hourLabel: String, val tempF: Int, val precipPct: Int, val code: Int) {
    val emoji: String get() = wmoEmoji(code, true)
}

/** One day in the forecast. */
data class WeatherDay(val dayLabel: String, val hiF: Int, val loF: Int, val precipPct: Int, val code: Int) {
    val desc: String get() = wmoDesc(code)
    val emoji: String get() = wmoEmoji(code, true)
}

data class WeatherForecast(
    val now: WeatherNow,
    val hours: List<WeatherHour>,   // next ~24 h
    val days: List<WeatherDay>,     // today + next few days (includes tomorrow)
    val place: String,
)

/** One animated radar frame from RainViewer: an absolute tile-URL template with {z}/{x}/{y}. */
data class RadarFrame(val timeSec: Long, val urlTemplate: String, val nowcast: Boolean)

/**
 * Free, no-key weather: current conditions + hourly/daily forecast from Open-Meteo, and an
 * animated precipitation radar (past frames + short-term nowcast) from RainViewer — the same
 * public radar a news weather map uses. Both APIs are open and keyless.
 */
class WeatherClient {

    suspend fun forecast(lat: Double, lon: Double): WeatherForecast? = withContext(Dispatchers.IO) {
        runCatching {
            val u = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day," +
                "weather_code,wind_speed_10m,wind_direction_10m" +
                "&hourly=temperature_2m,precipitation_probability,weather_code" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&temperature_unit=fahrenheit&wind_speed_unit=mph&timezone=auto&forecast_days=5"
            parseForecast(JSONObject(get(u)), "$lat, $lon")
        }.getOrNull()
    }

    /** Past + nowcast radar frames, oldest→newest. Empty on failure (map still shows). */
    suspend fun radarFrames(): List<RadarFrame> = withContext(Dispatchers.IO) {
        runCatching {
            val j = JSONObject(get("https://api.rainviewer.com/public/weather-maps.json"))
            val host = j.getString("host")
            val radar = j.getJSONObject("radar")
            val out = ArrayList<RadarFrame>()
            fun add(arr: JSONArray?, nowcast: Boolean) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    val f = arr.getJSONObject(i)
                    // size 256, colour scheme 2 (original), smooth=1 + show-snow=1
                    out.add(RadarFrame(f.getLong("time"), "$host${f.getString("path")}/256/{z}/{x}/{y}/2/1_1.png", nowcast))
                }
            }
            add(radar.optJSONArray("past"), false)
            add(radar.optJSONArray("nowcast"), true)
            out
        }.getOrElse { emptyList() }
    }

    private fun parseForecast(j: JSONObject, place: String): WeatherForecast {
        val c = j.getJSONObject("current")
        val now = WeatherNow(
            tempF = c.optDouble("temperature_2m").toInt(),
            feelsF = c.optDouble("apparent_temperature").toInt(),
            humidity = c.optInt("relative_humidity_2m"),
            windMph = c.optDouble("wind_speed_10m").toInt(),
            windDir = c.optDouble("wind_direction_10m").toInt(),
            code = c.optInt("weather_code"),
            isDay = c.optInt("is_day", 1) == 1,
        )
        val hours = ArrayList<WeatherHour>()
        j.optJSONObject("hourly")?.let { h ->
            val t = h.getJSONArray("time"); val temp = h.getJSONArray("temperature_2m")
            val pp = h.optJSONArray("precipitation_probability"); val wc = h.getJSONArray("weather_code")
            // Find the first hour >= now, then take the next 24.
            val nowKey = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH", java.util.Locale.US)
                .format(java.util.Date())
            var start = 0
            for (i in 0 until t.length()) { if (t.getString(i) >= nowKey) { start = i; break } }
            var i = start
            while (i < t.length() && hours.size < 24) {
                hours.add(WeatherHour(
                    hourLabel = t.getString(i).substringAfter('T').let { hhmm ->
                        val hr = hhmm.substring(0, 2).toInt()
                        when { hr == 0 -> "12a"; hr < 12 -> "${hr}a"; hr == 12 -> "12p"; else -> "${hr - 12}p" }
                    },
                    tempF = temp.getDouble(i).toInt(),
                    precipPct = pp?.optInt(i) ?: 0,
                    code = wc.getInt(i),
                ))
                i++
            }
        }
        val days = ArrayList<WeatherDay>()
        j.optJSONObject("daily")?.let { d ->
            val t = d.getJSONArray("time"); val hi = d.getJSONArray("temperature_2m_max")
            val lo = d.getJSONArray("temperature_2m_min"); val wc = d.getJSONArray("weather_code")
            val pp = d.optJSONArray("precipitation_probability_max")
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val today = fmt.format(java.util.Date())
            for (i in 0 until t.length()) {
                val label = when (i) {
                    0 -> if (t.getString(i) == today) "Today" else "Tomorrow"
                    1 -> "Tomorrow"
                    else -> runCatching {
                        java.text.SimpleDateFormat("EEE", java.util.Locale.US).format(fmt.parse(t.getString(i))!!)
                    }.getOrDefault(t.getString(i))
                }
                days.add(WeatherDay(label, hi.getDouble(i).toInt(), lo.getDouble(i).toInt(),
                    pp?.optInt(i) ?: 0, wc.getInt(i)))
            }
        }
        return WeatherForecast(now, hours, days, place)
    }

    private fun get(u: String): String {
        val c = (URL(u).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "FlyingFlocks/1.0")
        }
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }
}

/** WMO weather-code → short description (Open-Meteo / WMO 4677). */
fun wmoDesc(code: Int): String = when (code) {
    0 -> "Clear"; 1 -> "Mostly clear"; 2 -> "Partly cloudy"; 3 -> "Overcast"
    45, 48 -> "Fog"; 51, 53, 55 -> "Drizzle"; 56, 57 -> "Freezing drizzle"
    61 -> "Light rain"; 63 -> "Rain"; 65 -> "Heavy rain"; 66, 67 -> "Freezing rain"
    71 -> "Light snow"; 73 -> "Snow"; 75 -> "Heavy snow"; 77 -> "Snow grains"
    80 -> "Light showers"; 81 -> "Showers"; 82 -> "Violent showers"
    85, 86 -> "Snow showers"; 95 -> "Thunderstorm"; 96, 99 -> "Thunderstorm + hail"
    else -> "—"
}

/** WMO code → emoji glyph (day/night aware for clear skies). */
fun wmoEmoji(code: Int, isDay: Boolean): String = when (code) {
    0 -> if (isDay) "☀️" else "🌙"
    1 -> if (isDay) "🌤️" else "🌙"
    2 -> "⛅"; 3 -> "☁️"
    45, 48 -> "🌫️"
    51, 53, 55, 56, 57 -> "🌦️"
    61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
    71, 73, 75, 77, 85, 86 -> "🌨️"
    95, 96, 99 -> "⛈️"
    else -> "🌡️"
}
