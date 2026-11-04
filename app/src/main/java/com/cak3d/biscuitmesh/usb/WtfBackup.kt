package com.cak3d.biscuitmesh.usb

import org.json.JSONObject

/** A camera location parsed out of a What the Flock backup (or a live dongle sighting). */
data class WtfCamera(
    val mac: String,
    val lat: Double?,
    val lon: Double?,
    val name: String,
    val tier: Int,
    val hits: Int,
)

/**
 * Parses a What the Flock backup file (the JSON the WTF app exports from
 * Settings → Export → JSON): `{ app, v, opts, devices: { mac → {...} }, hits, track }`.
 * Only the per-device best-signal location is kept — that's the camera pin. Devices
 * with no fix are returned with null lat/lon so the caller can report how many were
 * un-locatable rather than silently dropping them.
 *
 * Throws [IllegalArgumentException] if the file has no `devices` map (so it isn't a
 * WTF backup), letting the import UI show a clear message.
 */
fun parseWtfBackup(text: String): List<WtfCamera> {
    val root = JSONObject(text)
    val devices = root.optJSONObject("devices")
        ?: throw IllegalArgumentException("Not a What the Flock backup (no devices)")
    val out = ArrayList<WtfCamera>(devices.length())
    val keys = devices.keys()
    while (keys.hasNext()) {
        val mac = keys.next()
        val d = devices.optJSONObject(mac) ?: continue
        // Best-signal position is WTF's closest estimate of where the camera is;
        // fall back to the last-seen position if that's all it has.
        val loc = d.optJSONObject("bestLoc") ?: d.optJSONObject("lastLoc")
        val lat = loc?.takeIf { it.has("lat") }?.optDouble("lat")
        val lon = loc?.takeIf { it.has("lon") }?.optDouble("lon")
        out.add(
            WtfCamera(
                mac = mac.lowercase(),
                lat = lat,
                lon = lon,
                name = d.optString("ssid").ifBlank { d.optString("name") },
                tier = d.optInt("tier", 0),
                hits = d.optInt("hits", 0),
            )
        )
    }
    return out
}
