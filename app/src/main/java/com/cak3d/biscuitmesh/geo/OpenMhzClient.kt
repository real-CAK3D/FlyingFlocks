package com.cak3d.biscuitmesh.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenMHz is a free, community archive of public-safety radio (police / fire / EMS)
 * recordings. No key required. We list the available systems and pull a system's most
 * recent "calls" — each is a short .m4a the app can stream. Consume-only; this never
 * uploads or transmits.
 */
class OpenMhzClient(private val base: String = "https://api.openmhz.com") {

    data class System(val shortName: String, val name: String, val city: String, val state: String)
    data class Call(
        val id: String,
        val url: String,          // streamable .m4a
        val talkgroupName: String,
        val talkgroupNum: Int,
        val timeMs: Long,
        val lenSec: Int,
    )

    suspend fun systems(): List<System> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("$base/systems")
            val arr = JSONObject(body).optJSONArray("systems") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.optJSONObject(i) ?: continue
                    val short = s.optString("shortName")
                    if (short.isBlank()) continue
                    add(System(
                        short,
                        s.optString("name", short),
                        s.optString("city", ""),
                        s.optString("state", ""),
                    ))
                }
            }.sortedBy { it.state + it.name }
        }.getOrDefault(emptyList())
    }

    suspend fun calls(shortName: String): List<Call> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get("$base/$shortName/calls")
            val arr = JSONObject(body).optJSONArray("calls") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val c = arr.optJSONObject(i) ?: continue
                    val url = c.optString("url")
                    if (url.isBlank()) continue
                    val time = c.optString("time")
                    add(Call(
                        c.optString("_id", url),
                        url,
                        c.optString("talkgroupName", c.optString("talkgroupAlpha", "TG ${c.optInt("talkgroupNum")}")),
                        c.optInt("talkgroupNum"),
                        parseTime(time),
                        c.optInt("len"),
                    ))
                }
            }.sortedByDescending { it.timeMs }
        }.getOrDefault(emptyList())
    }

    private fun parseTime(iso: String): Long = runCatching {
        // OpenMHz returns ISO-8601; fall back to "now" if it won't parse.
        java.time.Instant.parse(iso).toEpochMilli()
    }.getOrDefault(System.currentTimeMillis())

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "FlyingFlocks/0.9 (scanner viewer; cak3d)")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 12_000
            readTimeout = 20_000
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) throw RuntimeException("OpenMHz $code")
        return text
    }
}
