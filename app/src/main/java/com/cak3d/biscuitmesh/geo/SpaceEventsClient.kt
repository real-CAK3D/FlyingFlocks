package com.cak3d.biscuitmesh.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** One upcoming rocket launch. */
data class LaunchItem(
    val name: String,
    val provider: String,
    val pad: String,
    val netMs: Long,
    val status: String,
)

/**
 * Native space-events intel — upcoming launches (Launch Library 2) and the current Kp geomagnetic
 * index (NOAA SWPC). Both keyless. Launch Library's anonymous tier is rate-limited (~15/hour), so
 * results are cached in memory for the session; one fetch per screen open is plenty.
 */
class SpaceEventsClient {
    private var launchCache: List<LaunchItem>? = null

    private fun fetch(url: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 20000
            setRequestProperty("User-Agent", "FlockedUp-SpaceEvents")
        }
        conn.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()

    suspend fun launches(): List<LaunchItem> = withContext(Dispatchers.IO) {
        launchCache?.let { return@withContext it }
        val txt = fetch("https://ll.thespacedevs.com/2.2.0/launch/upcoming/?limit=15&mode=list")
            ?: return@withContext emptyList()
        val parsed = runCatching {
            val arr = JSONObject(txt).getJSONArray("results")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val net = o.optString("net").ifBlank { return@mapNotNull null }
                val netMs = runCatching { Instant.parse(net).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
                LaunchItem(
                    name = o.optString("name", "Launch"),
                    provider = o.optJSONObject("launch_service_provider")?.optString("name")
                        ?: o.optString("lsp_name", ""),
                    pad = o.optJSONObject("pad")?.let { p ->
                        p.optJSONObject("location")?.optString("name") ?: p.optString("name")
                    } ?: "",
                    netMs = netMs,
                    status = o.optJSONObject("status")?.optString("abbrev") ?: "",
                )
            }.sortedBy { it.netMs }
        }.getOrNull().orEmpty()
        if (parsed.isNotEmpty()) launchCache = parsed
        parsed
    }

    /** Latest planetary Kp index (0–9), or null. */
    suspend fun kp(): Double? = withContext(Dispatchers.IO) {
        val txt = fetch("https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json")
            ?: return@withContext null
        runCatching {
            val arr = JSONArray(txt)
            if (arr.length() < 2) return@runCatching null
            // Format A: array-of-arrays with a header row → [time, Kp, a_running, station_count].
            val first = arr.get(0)
            if (first is JSONArray) {
                val row = arr.getJSONArray(arr.length() - 1)
                row.getString(1).toDoubleOrNull()
            } else {
                // Format B: array-of-objects with "Kp".
                arr.getJSONObject(arr.length() - 1).optDouble("Kp").takeIf { !it.isNaN() }
            }
        }.getOrNull()
    }
}
