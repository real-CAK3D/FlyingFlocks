package com.cak3d.biscuitmesh.wdg

import com.cak3d.biscuitmesh.wardrive.SampleKind
import com.cak3d.biscuitmesh.wardrive.WardriveSample
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Renders wardrive samples as a WigleWifi-1.6 CSV — the format WDGWars (and
 * WiGLE) ingest. Two header lines then one row per observation. Access points
 * map to Type WIFI and Bluetooth devices to BLE; client stations are skipped
 * because the leaderboard scores networks, not clients.
 */
object WigleExport {

    private const val HEADER_META = "WigleWifi-1.6,appRelease=0.8.0,model=FlyingFlocks,release=0.8.0,device=flyingflocks,display=flyingflocks,board=esp32,brand=cak3d"
    private const val HEADER_COLS =
        "MAC,SSID,AuthMode,FirstSeen,Channel,Frequency,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,RCOIs,MfgrId,Type"

    private fun stamp(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    /** How many samples would actually be uploaded (AP + BT). */
    fun uploadableCount(samples: Collection<WardriveSample>): Int =
        samples.count { it.kind == SampleKind.AP || it.kind == SampleKind.BT }

    fun toCsv(samples: Collection<WardriveSample>): String {
        val fmt = stamp()
        val sb = StringBuilder()
        sb.append(HEADER_META).append('\n')
        sb.append(HEADER_COLS).append('\n')
        samples.forEach { s ->
            val type = when (s.kind) {
                SampleKind.AP -> "WIFI"
                SampleKind.BT -> "BLE"
                SampleKind.STATION -> return@forEach
                SampleKind.RF -> return@forEach   // sub-GHz activity isn't a WiGLE AP/BLE row
            }
            val first = fmt.format(Date(s.timeMs))
            val acc = if (s.accuracyM > 0f) s.accuracyM.toInt().toString() else ""
            // MAC,SSID,AuthMode,FirstSeen,Channel,Frequency,RSSI,Lat,Lon,Alt,Acc,RCOIs,MfgrId,Type
            sb.append(csv(s.id)).append(',')
                .append(csv(s.label)).append(',')
                .append(',')                       // AuthMode — not captured
                .append(csv(first)).append(',')
                .append(',')                       // Channel
                .append(',')                       // Frequency
                .append(s.rssi).append(',')
                .append("%.6f".format(Locale.US, s.lat)).append(',')
                .append("%.6f".format(Locale.US, s.lon)).append(',')
                .append("0").append(',')           // AltitudeMeters
                .append(acc).append(',')           // AccuracyMeters
                .append(',')                       // RCOIs
                .append(',')                       // MfgrId
                .append(type).append('\n')
        }
        return sb.toString()
    }

    /** Minimal CSV field escaping: quote when the value holds a comma, quote or newline. */
    private fun csv(raw: String): String {
        if (raw.isEmpty()) return ""
        val needsQuote = raw.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuote) return raw
        return "\"" + raw.replace("\"", "\"\"") + "\""
    }
}
