package com.cak3d.biscuitmesh.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.fingerprintDataStore by preferencesDataStore(name = "fingerprints")

/** A saved snapshot of the radios heard at one place — its "RF fingerprint". */
data class Fingerprint(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val savedMs: Long,
    /** device id (BSSID/MAC) → human label (SSID/name), as heard when saved. */
    val devices: Map<String, String>,
)

/**
 * Persists RF fingerprints of places. Snapshot every Wi-Fi/BT device heard at a spot; on return,
 * compare the live air to the snapshot to see what's new, what's gone and what's still here — a
 * change-detector for a location. Local-only, capped so it can't grow without bound.
 */
class FingerprintStore(private val context: Context) {

    private val key = stringPreferencesKey("fingerprints_json")
    private val cap = 60

    val fingerprints: Flow<List<Fingerprint>> =
        context.fingerprintDataStore.data.map { parse(it[key] ?: "[]") }

    private suspend fun current(): List<Fingerprint> =
        parse(context.fingerprintDataStore.data.first()[key] ?: "[]")

    suspend fun add(fp: Fingerprint) {
        val next = (listOf(fp) + current()).take(cap)
        context.fingerprintDataStore.edit { it[key] = toJson(next) }
    }

    suspend fun remove(id: String) {
        context.fingerprintDataStore.edit { it[key] = toJson(current().filterNot { f -> f.id == id }) }
    }

    private fun toJson(list: List<Fingerprint>): String {
        val arr = JSONArray()
        list.forEach { f ->
            val devs = JSONObject()
            f.devices.forEach { (k, v) -> devs.put(k, v) }
            arr.put(JSONObject().put("id", f.id).put("name", f.name).put("lat", f.lat)
                .put("lon", f.lon).put("saved", f.savedMs).put("devices", devs))
        }
        return arr.toString()
    }

    private fun parse(json: String): List<Fingerprint> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val devs = HashMap<String, String>()
            o.optJSONObject("devices")?.let { d ->
                d.keys().forEach { k -> devs[k] = d.optString(k) }
            }
            Fingerprint(
                id = id,
                name = o.optString("name", "Place"),
                lat = o.optDouble("lat"),
                lon = o.optDouble("lon"),
                savedMs = o.optLong("saved"),
                devices = devs,
            )
        }
    }.getOrDefault(emptyList())
}
