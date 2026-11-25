package com.cak3d.biscuitmesh.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches TLE sets from Celestrak (keyless, public). Used by the native Orbit Globe preview to
 * plot notable satellites with real SGP4 positions. Groups are small, curated catalogues — this
 * is a preview, not the full 32k set — so one fetch per group, cached in memory for the session.
 */
class TleClient {
    private val cache = HashMap<String, List<Tle>>()

    /** Celestrak groups worth showing in the preview. */
    companion object {
        const val STATIONS = "stations"     // ISS, Tiangong/CSS, etc.
        const val VISUAL = "visual"          // ~160 brightest, easily-seen satellites
        private const val BASE = "https://celestrak.org/NORAD/elements/gp.php?FORMAT=tle&GROUP="
    }

    /** Fetch (or return cached) TLEs for a Celestrak group. Retries a couple of times before giving
     *  up, since Celestrak occasionally rate-limits or returns a transient error page. */
    suspend fun group(name: String): List<Tle> = withContext(Dispatchers.IO) {
        cache[name]?.let { return@withContext it }
        var parsed: List<Tle> = emptyList()
        for (attempt in 0 until 3) {
            val txt = runCatching {
                val conn = URL(BASE + name).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 20000; conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "FlockedUp-OrbitGlobe")
                conn.setRequestProperty("Accept", "text/plain")
                if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
            }.getOrNull()
            parsed = txt?.let(::parse).orEmpty()
            if (parsed.isNotEmpty()) break
            kotlinx.coroutines.delay(1200)
        }
        if (parsed.isNotEmpty()) cache[name] = parsed
        parsed
    }

    /** Parse Celestrak 3-line TLE text into [Tle]s. */
    private fun parse(text: String): List<Tle> {
        val lines = text.lineSequence().map { it.trimEnd() }.filter { it.isNotBlank() }.toList()
        val out = ArrayList<Tle>()
        var i = 0
        while (i + 2 < lines.size) {
            val name = lines[i]; val l1 = lines[i + 1]; val l2 = lines[i + 2]
            if (l1.startsWith("1 ") && l2.startsWith("2 ")) {
                out.add(Tle(name.removePrefix("0 ").trim(), l1, l2))
            }
            i += 3
        }
        return out
    }
}
