package com.cak3d.biscuitmesh.geo

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A WiGLE account's headline stats. */
data class WigleStats(
    val user: String,
    val rank: Int,
    val monthRank: Int,
    val prevRank: Int,
    val wifiGps: Long,
    val cellGps: Long,
    val btGps: Long,
)

/**
 * Pulls your own WiGLE stats from the public WiGLE API using your account credential
 * (the "Encoded for use" string on your WiGLE account page). WiGLE is where Wardrive Go
 * uploads, so this shows the stats side of your wardriving. No backend needed.
 */
class WigleClient(private val base: String = "https://api.wigle.net/api/v2") {

    suspend fun userStats(auth: String): Result<WigleStats> = withContext(Dispatchers.IO) {
        runCatching {
            // Accept either the ready-to-use Base64 "Encoded for use" string, or raw name:token.
            val basic = if (auth.contains(":")) Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP) else auth.trim()
            parse(get("$base/stats/user", basic))
        }
    }

    private fun get(u: String, basic: String): String {
        val c = (URL(u).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Basic $basic")
            setRequestProperty("Accept", "application/json")
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        if (code == 401) throw RuntimeException("WiGLE rejected the key (401). Re-copy the \"Encoded for use\" string.")
        if (code !in 200..299) throw RuntimeException("WiGLE error (HTTP $code)")
        return text
    }

    private fun parse(body: String): WigleStats {
        val o = JSONObject(body)
        if (!o.optBoolean("success", false)) throw RuntimeException(o.optString("message", "WiGLE returned no stats"))
        val s = o.optJSONObject("statistics") ?: JSONObject()
        return WigleStats(
            user = o.optString("user", s.optString("userName", "")),
            rank = o.optInt("rank", s.optInt("rank", 0)),
            monthRank = o.optInt("monthRank", s.optInt("monthRank", 0)),
            prevRank = s.optInt("prevRank", 0),
            wifiGps = s.optLong("discoveredWiFiGPS", 0),
            cellGps = s.optLong("discoveredCellGPS", 0),
            btGps = s.optLong("discoveredBtGPS", 0),
        )
    }
}
