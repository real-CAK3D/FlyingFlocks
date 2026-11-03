package com.cak3d.biscuitmesh.geo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.hazardDataStore by preferencesDataStore(name = "hazards")

/** Accumulates cameras we've seen so alerts work even when offline. */
class HazardStore(private val context: Context) {

    private val key = stringPreferencesKey("hazards_json")

    val hazards: Flow<List<Hazard>> =
        context.hazardDataStore.data.map { parse(it[key] ?: "[]") }

    private suspend fun current(): List<Hazard> =
        parse(context.hazardDataStore.data.first()[key] ?: "[]")

    /** Adds any hazards we don't already have (by id); keeps the most recent 2000. */
    suspend fun merge(incoming: List<Hazard>) {
        if (incoming.isEmpty()) return
        val have = current()
        val ids = have.mapTo(HashSet()) { it.id }
        val added = incoming.filter { it.id !in ids }
        if (added.isEmpty()) return
        context.hazardDataStore.edit { it[key] = toJson((have + added).takeLast(2000)) }
    }

    private fun toJson(list: List<Hazard>): String {
        val arr = JSONArray()
        list.forEach { h ->
            arr.put(JSONObject().put("id", h.id).put("kind", h.kind.name).put("lat", h.lat).put("lon", h.lon).put("name", h.name))
        }
        return arr.toString()
    }

    private fun parse(json: String): List<Hazard> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Hazard(
                id = id,
                kind = runCatching { HazardKind.valueOf(o.optString("kind", "CAMERA")) }.getOrDefault(HazardKind.CAMERA),
                lat = o.optDouble("lat"),
                lon = o.optDouble("lon"),
                name = o.optString("name", ""),
            )
        }
    }.getOrDefault(emptyList())
}
