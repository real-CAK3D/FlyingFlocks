package com.cak3d.biscuitmesh.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.meshPlacementDataStore by preferencesDataStore(name = "mesh_placements")

/**
 * Manually-pinned locations for Meshtastic nodes that have no GPS of their own
 * (e.g. a stationary home node). Keyed by node number → lat/lon, so they can be
 * drawn on the map and woven into the mesh web.
 */
class MeshPlacementStore(private val context: Context) {

    private val key = stringPreferencesKey("placements_json")

    val placements: Flow<Map<Long, Pair<Double, Double>>> =
        context.meshPlacementDataStore.data.map { parse(it[key] ?: "{}") }

    private suspend fun current(): Map<Long, Pair<Double, Double>> =
        parse(context.meshPlacementDataStore.data.first()[key] ?: "{}")

    suspend fun place(num: Long, lat: Double, lon: Double) {
        val next = current() + (num to (lat to lon))
        write(next)
    }

    suspend fun clear(num: Long) {
        write(current() - num)
    }

    private suspend fun write(map: Map<Long, Pair<Double, Double>>) {
        val o = JSONObject()
        map.forEach { (num, loc) -> o.put(num.toString(), JSONObject().put("lat", loc.first).put("lon", loc.second)) }
        context.meshPlacementDataStore.edit { it[key] = o.toString() }
    }

    private fun parse(json: String): Map<Long, Pair<Double, Double>> = runCatching {
        val o = JSONObject(json)
        val out = HashMap<Long, Pair<Double, Double>>()
        o.keys().forEach { k ->
            val n = k.toLongOrNull() ?: return@forEach
            val v = o.optJSONObject(k) ?: return@forEach
            out[n] = v.optDouble("lat") to v.optDouble("lon")
        }
        out
    }.getOrDefault(emptyMap())
}
