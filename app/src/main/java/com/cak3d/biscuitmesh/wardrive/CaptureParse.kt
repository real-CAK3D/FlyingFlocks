package com.cak3d.biscuitmesh.wardrive

/** A geo-located point read back from a saved drive's WiGLE capture CSV. */
data class GeoPt(
    val lat: Double,
    val lon: Double,
    val rssi: Int,
    val timeMs: Long,
    val type: String,   // WIFI / BT / RF / …
    val id: String = "",     // MAC / BSSID
    val label: String = "",  // SSID / name
    val channel: Int = 0,
)

/**
 * Parses a WigleWifi-1.6 capture CSV into geo points for replay and heatmaps.
 * Columns: MAC,SSID,AuthMode,FirstSeen,Channel,Frequency,RSSI,CurrentLatitude,CurrentLongitude,…,Type
 * SSID can contain commas, so each line is split with quote awareness.
 */
object CaptureParse {
    fun parse(csv: String): List<GeoPt> {
        val out = ArrayList<GeoPt>()
        val lines = csv.split('\n')
        for ((i, raw) in lines.withIndex()) {
            if (i < 2) continue                 // two header lines
            val line = raw.trimEnd('\r')
            if (line.isBlank()) continue
            val f = splitCsv(line)
            if (f.size < 14) continue
            val lat = f[7].toDoubleOrNull() ?: continue
            val lon = f[8].toDoubleOrNull() ?: continue
            if (lat == 0.0 && lon == 0.0) continue
            val rssi = f[6].toIntOrNull() ?: continue
            val time = parseTime(f[3])
            out.add(GeoPt(lat, lon, rssi, time, f[13].trim(), f[0].trim(), f[1], f[4].toIntOrNull() ?: 0))
        }
        return out
    }

    /** Minimal CSV splitter that respects double-quoted fields. */
    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> if (inQuotes && i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i++ } else inQuotes = !inQuotes
                c == ',' && !inQuotes -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    // FirstSeen is ISO-ish ("yyyy-MM-dd HH:mm:ss"); fall back to 0 if unparseable.
    private val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
    private fun parseTime(s: String): Long =
        runCatching { fmt.parse(s.trim())?.time ?: 0L }.getOrDefault(0L)
}
