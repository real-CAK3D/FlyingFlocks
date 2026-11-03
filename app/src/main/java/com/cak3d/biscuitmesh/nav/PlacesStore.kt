package com.cak3d.biscuitmesh.nav

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.placesDataStore by preferencesDataStore(name = "places")

/** Persists saved destinations (Home, Work, custom). */
class PlacesStore(private val context: Context) {

    private val key = stringPreferencesKey("places_json")

    val places: Flow<List<Place>> =
        context.placesDataStore.data.map { parse(it[key] ?: "[]") }

    // Read the stored list WITHOUT opening an edit{}: DataStore forbids a nested
    // updateData (calling edit{} from inside another edit{}), which is what crashed
    // add()/remove() before. Snapshot via data.first(), then write in one edit{}.
    private suspend fun current(): List<Place> =
        parse(context.placesDataStore.data.first()[key] ?: "[]")

    suspend fun add(place: Place) {
        val next = current() + place
        context.placesDataStore.edit { it[key] = toJson(next) }
    }

    suspend fun remove(id: String) {
        val next = current().filterNot { p -> p.id == id }
        context.placesDataStore.edit { it[key] = toJson(next) }
    }

    private fun toJson(list: List<Place>): String {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("kind", p.kind.name).put("lat", p.lat).put("lon", p.lon))
        }
        return arr.toString()
    }

    private fun parse(json: String): List<Place> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Place(
                id = id,
                name = o.optString("name", "Place"),
                kind = runCatching { PlaceKind.valueOf(o.optString("kind", "CUSTOM")) }.getOrDefault(PlaceKind.CUSTOM),
                lat = o.optDouble("lat"),
                lon = o.optDouble("lon"),
            )
        }
    }.getOrDefault(emptyList())
}
