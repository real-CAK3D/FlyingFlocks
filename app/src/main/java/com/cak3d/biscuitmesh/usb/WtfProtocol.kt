package com.cak3d.biscuitmesh.usb

import org.json.JSONObject

/**
 * One parsed event from a What the Flock dongle's newline-JSON stream.
 *
 * The dongle speaks the identical protocol whether it's reached over USB-CDC
 * ([WtfSerialSource]) or over BLE ([WtfBleSource]), so both sources funnel each
 * received line through [WtfProtocol.parseLine] and act on the result. Keeping
 * the parse in one place means a wire-format change is fixed once.
 */
sealed interface WtfEvent {
    /** A camera sighting — live `detection`, or a replayed `session_det` / `sd_det`. */
    data class Sighting(val detection: WtfDetection) : WtfEvent
    /** The board's GPS status, from its `sd_info` report. */
    data class Gps(val gps: WtfGps) : WtfEvent
}

object WtfProtocol {

    /**
     * Parse one newline-delimited JSON event line. Returns null for lines that
     * carry no sighting or GPS state (config, session boundaries, *_error, acks,
     * non-JSON log chatter) — exactly the lines both sources handled quietly.
     */
    fun parseLine(line: String): WtfEvent? {
        if (line.firstOrNull() != '{') return null
        val ev = runCatching { JSONObject(line) }.getOrNull() ?: return null
        return when (ev.optString("event")) {
            "detection" -> toSighting(ev, live = true)
            "session_det" -> toSighting(ev, live = false)
            "sd_det" -> toSighting(ev, live = false)   // a line of the board's microSD detection log
            "sd_info" -> {                              // board status, incl. GPS module/fix/sats
                if (ev.has("gps") || ev.has("gps_fix") || ev.has("sats")) {
                    WtfEvent.Gps(
                        WtfGps(
                            present = ev.optInt("gps") == 1,
                            fix = ev.optInt("gps_fix") == 1,
                            sats = ev.optInt("sats"),
                            lat = if (ev.has("lat") && !ev.isNull("lat")) ev.optDouble("lat") else null,
                            lon = if (ev.has("lon") && !ev.isNull("lon")) ev.optDouble("lon") else null,
                        )
                    )
                } else null
            }
            // session_begin/end, sd_end, config, *_error carry no sighting.
            else -> null
        }
    }

    private fun toSighting(ev: JSONObject, live: Boolean): WtfEvent.Sighting? {
        val mac = (ev.optString("mac_address").ifEmpty { ev.optString("mac") }).lowercase()
        if (mac.isEmpty()) return null
        return WtfEvent.Sighting(
            WtfDetection(
                mac = mac,
                tier = ev.optInt("detection_tier", ev.optInt("tier", 0)),
                method = ev.optString("detection_method", ev.optString("method", "unknown")),
                lat = if (ev.has("lat") && !ev.isNull("lat")) ev.optDouble("lat") else null,
                lon = if (ev.has("lon") && !ev.isNull("lon")) ev.optDouble("lon") else null,
                rssi = ev.optInt("rssi", -127),
                ssid = ev.optString("ssid", ""),
                name = ev.optString("device_name", ""),
                protocol = ev.optString("protocol", ""),
                channel = if (ev.has("channel")) ev.optInt("channel") else if (ev.has("ch")) ev.optInt("ch") else null,
                live = live,
            )
        )
    }
}
