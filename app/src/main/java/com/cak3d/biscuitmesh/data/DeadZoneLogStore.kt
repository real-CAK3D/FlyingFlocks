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

/** One device observed inside a dead zone. Lives only on this phone. */
data class DeadZoneDevice(
    val mac: String,
    val name: String,
    val kind: String,   // "WiFi", "WiFi 5G", "BLE"
    val rssi: Int,
    val lastMs: Long,
)

private val Context.deadZoneLogDataStore by preferencesDataStore(name = "deadzone_devices")

/**
 * A strictly LOCAL-ONLY log of the devices seen inside each dead zone, keyed by zone id.
 *
 * This store is deliberately isolated: nothing in the capture/export/upload path ever
 * reads it, so these devices are shown on screen but never leave the phone. It exists so
 * the operator can see what's broadcasting around their home/work without that data ever
 * ending up in a wardrive upload.
 */
class DeadZoneLogStore(private val context: Context) {

    private val key = stringPreferencesKey("log_json")
    private val cap = 500  // per-zone device cap

    /** zoneId -> devices seen there, newest activity last. */
    val log: Flow<Map<String, List<DeadZoneDevice>>> =
        context.deadZoneLogDataStore.data.map { parse(it[key] ?: "{}") }

    /** Merge a batch of sightings into one zone (dedup by MAC, keep the latest reading). */
    suspend fun record(zoneId: String, devices: List<DeadZoneDevice>) {
        if (devices.isEmpty()) return
        val all = parse(context.deadZoneLogDataStore.data.first()[key] ?: "{}").toMutableMap()
        val byMac = LinkedHashMap<String, DeadZoneDevice>()
        all[zoneId]?.forEach { byMac[it.mac] = it }
        devices.forEach { byMac[it.mac] = it }
        all[zoneId] = byMac.values.toList().takeLast(cap)
        context.deadZoneLogDataStore.edit { it[key] = toJson(all) }
    }

    suspend fun clear(zoneId: String) {
        val all = parse(context.deadZoneLogDataStore.data.first()[key] ?: "{}").toMutableMap()
        all.remove(zoneId)
        context.deadZoneLogDataStore.edit { it[key] = toJson(all) }
    }

    private fun toJson(map: Map<String, List<DeadZoneDevice>>): String {
        val root = JSONObject()
        map.forEach { (zid, devs) ->
            val arr = JSONArray()
            devs.forEach { d ->
                arr.put(JSONObject().put("mac", d.mac).put("name", d.name)
                    .put("kind", d.kind).put("rssi", d.rssi).put("lastMs", d.lastMs))
            }
            root.put(zid, arr)
        }
        return root.toString()
    }

    private fun parse(json: String): Map<String, List<DeadZoneDevice>> = runCatching {
        val root = JSONObject(json)
        val out = HashMap<String, List<DeadZoneDevice>>()
        root.keys().forEach { zid ->
            val arr = root.optJSONArray(zid) ?: return@forEach
            out[zid] = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val mac = o.optString("mac").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                DeadZoneDevice(mac, o.optString("name"), o.optString("kind", "BLE"),
                    o.optInt("rssi"), o.optLong("lastMs"))
            }
        }
        out
    }.getOrDefault(emptyMap())
}
