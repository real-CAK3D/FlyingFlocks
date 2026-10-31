package com.cak3d.biscuitmesh.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A board the user has saved to the fleet, so it persists across app restarts. */
data class KnownDevice(val address: String, val name: String)

private val Context.knownDevicesDataStore by preferencesDataStore(name = "known_devices")

/**
 * Persists the saved-board list. The stock app "remembers" boards and shows them
 * as Out of range until seen; we do the same so the fleet survives restarts.
 */
class KnownDevicesStore(private val context: Context) {

    private val key = stringPreferencesKey("devices_json")

    suspend fun load(): List<KnownDevice> = withContext(Dispatchers.IO) {
        val json = context.knownDevicesDataStore.data.first()[key] ?: return@withContext emptyList()
        parse(json)
    }

    suspend fun save(devices: List<KnownDevice>) = withContext(Dispatchers.IO) {
        context.knownDevicesDataStore.edit { it[key] = toJson(devices) }
    }

    private fun toJson(devices: List<KnownDevice>): String {
        val arr = JSONArray()
        devices.forEach { d ->
            arr.put(JSONObject().put("address", d.address).put("name", d.name))
        }
        return arr.toString()
    }

    private fun parse(json: String): List<KnownDevice> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val addr = o.optString("address").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            KnownDevice(addr, o.optString("name", addr))
        }
    }.getOrDefault(emptyList())
}
