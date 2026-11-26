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

    /**
     * Current conditions + forecast. With no [provider]/[key] this is keyless Open-Meteo (the
     * default everyone gets). If the operator has entered a Windy point-forecast or OpenWeather
     * key, that provider is used instead — and if it fails for any reason (bad key, no
     * subscription, network) it silently falls back to Open-Meteo, so weather can never break.
     */
    suspend fun forecast(
        lat: Double,
        lon: Double,
        provider: String? = null,
        key: String? = null,
    ): WeatherForecast? = withContext(Dispatchers.IO) {
        if (!key.isNullOrBlank()) {
            val alt = runCatching {
                when (provider) {
                    "windy_point" -> windyForecast(lat, lon, key)
                    "openweather" -> openWeatherForecast(lat, lon, key)
                    else -> null
                }
            }.getOrNull()
            if (alt != null) return@withContext alt
        }
        openMeteoForecast(lat, lon)
    }

    private fun openMeteoForecast(lat: Double, lon: Double): WeatherForecast? = runCatching {
        val u = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day," +
            "weather_code,wind_speed_10m,wind_direction_10m" +
            "&hourly=temperature_2m,precipitation_probability,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&temperature_unit=fahrenheit&wind_speed_unit=mph&timezone=auto&forecast_days=5"
        parseForecast(JSONObject(get(u)), "$lat, $lon")
    }.getOrNull()

    /** OpenWeather One Call 3.0 → the shared WeatherForecast model (imperial units). */
    private fun openWeatherForecast(lat: Double, lon: Double, key: String): WeatherForecast {
        val u = "https://api.openweathermap.org/data/3.0/onecall?lat=$lat&lon=$lon" +
            "&units=imperial&exclude=minutely,alerts&appid=$key"
        val j = JSONObject(get(u))
        val cur = j.getJSONObject("current")
        val curCode = owmToWmo(cur.getJSONArray("weather").getJSONObject(0).optInt("id"))
        val now = WeatherNow(
            tempF = cur.optDouble("temp").toInt(),
            feelsF = cur.optDouble("feels_like").toInt(),
            humidity = cur.optInt("humidity"),
            windMph = cur.optDouble("wind_speed").toInt(),
            windDir = cur.optInt("wind_deg"),
            code = curCode,
            isDay = run {
                val dt = cur.optLong("dt"); val sr = cur.optLong("sunrise"); val ss = cur.optLong("sunset")
                if (sr > 0 && ss > 0) dt in sr..ss else true
            },
        )
        val hours = ArrayList<WeatherHour>()
        j.optJSONArray("hourly")?.let { h ->
            var i = 0
            while (i < h.length() && hours.size < 24) {
                val o = h.getJSONObject(i)
                val hr = java.util.Calendar.getInstance().apply { timeInMillis = o.getLong("dt") * 1000 }
                    .get(java.util.Calendar.HOUR_OF_DAY)
                hours.add(WeatherHour(
                    hourLabel = when { hr == 0 -> "12a"; hr < 12 -> "${hr}a"; hr == 12 -> "12p"; else -> "${hr - 12}p" },
                    tempF = o.optDouble("temp").toInt(),
                    precipPct = (o.optDouble("pop") * 100).toInt(),
                    code = owmToWmo(o.getJSONArray("weather").getJSONObject(0).optInt("id")),
                ))
                i++
            }
        }
        val days = ArrayList<WeatherDay>()
        j.optJSONArray("daily")?.let { d ->
            val fmt = java.text.SimpleDateFormat("EEE", java.util.Locale.US)
            for (i in 0 until d.length()) {
                val o = d.getJSONObject(i)
                val temp = o.getJSONObject("temp")
                val label = when (i) { 0 -> "Today"; 1 -> "Tomorrow"
                    else -> fmt.format(java.util.Date(o.getLong("dt") * 1000)) }
                days.add(WeatherDay(label, temp.optDouble("max").toInt(), temp.optDouble("min").toInt(),
                    (o.optDouble("pop") * 100).toInt(),
                    owmToWmo(o.getJSONArray("weather").getJSONObject(0).optInt("id"))))
            }
        }
        return WeatherForecast(now, hours, days, "$lat, $lon")
    }

    /** Windy point-forecast v2 (GFS, 3-hourly) → the shared WeatherForecast model. */
    private fun windyForecast(lat: Double, lon: Double, key: String): WeatherForecast {
        val body = JSONObject()
            .put("lat", lat).put("lon", lon).put("model", "gfs")
            .put("parameters", JSONArray(listOf("temp", "wind", "rh", "precip", "lclouds", "mclouds", "hclouds")))
            .put("levels", JSONArray(listOf("surface"))).put("key", key)
        val j = JSONObject(post("https://api.windy.com/api/point-forecast/v2", body.toString()))
        val ts = j.getJSONArray("ts")
        fun arr(name: String) = j.optJSONArray(name)
        val tempK = arr("temp-surface"); val u10 = arr("wind_u-surface"); val v10 = arr("wind_v-surface")
        val rh = arr("rh-surface"); val precip = arr("past3hprecip-surface")
        val lc = arr("lclouds-surface"); val mc = arr("mclouds-surface"); val hc = arr("hclouds-surface")
        fun kToF(k: Double) = ((k - 273.15) * 9.0 / 5.0 + 32.0)
        fun cloud(i: Int) = maxOf(lc?.optDouble(i) ?: 0.0, mc?.optDouble(i) ?: 0.0, hc?.optDouble(i) ?: 0.0)
        fun codeAt(i: Int): Int {
            val p = precip?.optDouble(i) ?: 0.0; val tf = kToF(tempK?.optDouble(i) ?: 283.0)
            return when {
                p >= 1.0 -> if (tf <= 34) 73 else 63
                p >= 0.1 -> if (tf <= 34) 71 else 61
                cloud(i) > 80 -> 3; cloud(i) > 40 -> 2; cloud(i) > 10 -> 1; else -> 0
            }
        }
        val uu = u10?.optDouble(0) ?: 0.0; val vv = v10?.optDouble(0) ?: 0.0
        val now = WeatherNow(
            tempF = kToF(tempK?.optDouble(0) ?: 283.0).toInt(),
            feelsF = kToF(tempK?.optDouble(0) ?: 283.0).toInt(),
            humidity = (rh?.optDouble(0) ?: 0.0).toInt(),
            windMph = (Math.hypot(uu, vv) * 2.23694).toInt(),
            windDir = ((Math.toDegrees(Math.atan2(-uu, -vv)) + 360) % 360).toInt(),
            code = codeAt(0),
            isDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) in 7..19,
        )
        val hours = ArrayList<WeatherHour>()
        var i = 0
        while (i < ts.length() && hours.size < 16) {
            val hr = java.util.Calendar.getInstance().apply { timeInMillis = ts.getLong(i) }
                .get(java.util.Calendar.HOUR_OF_DAY)
            hours.add(WeatherHour(
                hourLabel = when { hr == 0 -> "12a"; hr < 12 -> "${hr}a"; hr == 12 -> "12p"; else -> "${hr - 12}p" },
                tempF = kToF(tempK?.optDouble(i) ?: 283.0).toInt(),
                precipPct = 0, code = codeAt(i)))
            i++
        }
        val byDay = LinkedHashMap<String, MutableList<Int>>()
        val codeByDay = LinkedHashMap<String, Int>()
        val dfmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val lfmt = java.text.SimpleDateFormat("EEE", java.util.Locale.US)
        for (k in 0 until ts.length()) {
            val d = java.util.Date(ts.getLong(k)); val dk = dfmt.format(d)
            byDay.getOrPut(dk) { mutableListOf() }.add(kToF(tempK?.optDouble(k) ?: 283.0).toInt())
            codeByDay.putIfAbsent(dk, codeAt(k))
        }
        val days = ArrayList<WeatherDay>()
        val today = dfmt.format(java.util.Date())
        byDay.entries.take(5).forEachIndexed { idx, (dk, temps) ->
            val label = when { dk == today -> "Today"; idx == 1 -> "Tomorrow"
                else -> runCatching { lfmt.format(dfmt.parse(dk)!!) }.getOrDefault(dk) }
            days.add(WeatherDay(label, temps.maxOrNull() ?: 0, temps.minOrNull() ?: 0, 0, codeByDay[dk] ?: 0))
        }
        return WeatherForecast(now, hours, days, "$lat, $lon")
    }

    private fun post(u: String, json: String): String {
        val c = (URL(u).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 15_000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "FlyingFlocks/1.0")
        }
        c.outputStream.use { it.write(json.toByteArray()) }
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
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

/** OpenWeather condition id (2xx–8xx) → the nearest WMO code, so both providers share icons. */
fun owmToWmo(id: Int): Int = when (id) {
    in 200..232 -> 95            // thunderstorm
    in 300..321 -> 51           // drizzle
    in 500..504 -> 63           // rain
    511 -> 66                    // freezing rain
    in 520..531 -> 81           // showers
    in 600..602 -> 73           // snow
    in 611..616 -> 66           // sleet
    in 620..622 -> 85           // snow showers
    701, 711, 721, 731, 741, 751, 761, 762 -> 45 // mist/fog/haze
    800 -> 0                     // clear
    801 -> 1                     // few clouds
    802 -> 2                     // scattered
    803, 804 -> 3                // broken/overcast
    else -> 0
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
