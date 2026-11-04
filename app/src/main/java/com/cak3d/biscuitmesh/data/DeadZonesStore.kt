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

/**
 * A geofenced area in which the app refuses to scan. While the phone is inside
 * any dead zone, wardriving stops broadcasting scan commands and records no
 * samples — so places like home or work never end up in the capture.
 */
data class DeadZone(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Double,
)

private val Context.deadZonesDataStore by preferencesDataStore(name = "dead_zones")

/** Persists the operator's scanning dead zones. */
class DeadZonesStore(private val context: Context) {

    private val key = stringPreferencesKey("zones_json")

    /** Reactive list, so the wardrive engine and the settings UI stay in sync. */
    val zones: Flow<List<DeadZone>> =
        context.deadZonesDataStore.data.map { parse(it[key] ?: "[]") }

    suspend fun add(zone: DeadZone) {
        val next = current() + zone
        write(next)
    }

    suspend fun remove(id: String) {
        write(current().filterNot { it.id == id })
    }

    /** Change an existing zone's radius (metres), clamped to a sane range. */
    suspend fun setRadius(id: String, radiusM: Double) {
        write(current().map { if (it.id == id) it.copy(radiusM = radiusM.coerceIn(20.0, 5000.0)) else it })
    }

    private suspend fun current(): List<DeadZone> =
        parse(context.deadZonesDataStore.data.first()[key] ?: "[]")

    private suspend fun write(zones: List<DeadZone>) {
        context.deadZonesDataStore.edit { it[key] = toJson(zones) }
    }

    private fun toJson(zones: List<DeadZone>): String {
        val arr = JSONArray()
        zones.forEach { z ->
            arr.put(
                JSONObject()
                    .put("id", z.id)
                    .put("name", z.name)
                    .put("lat", z.lat)
                    .put("lon", z.lon)
                    .put("radiusM", z.radiusM),
            )
        }
        return arr.toString()
    }

    private fun parse(json: String): List<DeadZone> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            DeadZone(
                id = id,
                name = o.optString("name", "Dead zone"),
                lat = o.optDouble("lat"),
                lon = o.optDouble("lon"),
                radiusM = o.optDouble("radiusM", 150.0),
            )
        }
    }.getOrDefault(emptyList())
}
