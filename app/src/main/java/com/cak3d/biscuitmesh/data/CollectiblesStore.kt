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

/** The four collectible families. */
enum class StampKind(val label: String, val glyph: String) {
    LOCATION("Places", "📍"),
    SPECIES("Species", "🐦"),
    SKY("Sky", "🛰️"),
    EVENT("Events", "✦"),
}

/** One collected stamp in the passport. [id] is unique so each is earned once. */
data class Stamp(
    val id: String,
    val kind: StampKind,
    val title: String,
    val subtitle: String,
    val data: String,
    val ms: Long,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
)

/**
 * The passport — every collectible you've stamped, kept forever. Collecting a new one is
 * de-duplicated by id, so you can't double-stamp the same place/species/catch.
 */
class CollectiblesStore(private val context: Context) {

    private val key = stringPreferencesKey("stamps_json")

    val stamps: Flow<List<Stamp>> =
        context.collectiblesDataStore.data.map { parse(it[key] ?: "[]") }

    private suspend fun current(): List<Stamp> =
        parse(context.collectiblesDataStore.data.first()[key] ?: "[]")

    /** Collect [stamp]; returns true if it was new (not already in the passport). */
    suspend fun collect(stamp: Stamp): Boolean {
        val have = current()
        if (have.any { it.id == stamp.id }) return false
        context.collectiblesDataStore.edit { it[key] = toJson(have + stamp) }
        return true
    }

    suspend fun reset() {
        context.collectiblesDataStore.edit { it[key] = "[]" }
    }

    private fun toJson(list: List<Stamp>): String {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().put("id", s.id).put("kind", s.kind.name).put("title", s.title)
                .put("sub", s.subtitle).put("data", s.data).put("ms", s.ms).put("lat", s.lat).put("lon", s.lon))
        }
        return arr.toString()
    }

    private fun parse(json: String): List<Stamp> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Stamp(
                id = id,
                kind = runCatching { StampKind.valueOf(o.optString("kind", "EVENT")) }.getOrDefault(StampKind.EVENT),
                title = o.optString("title", "Stamp"),
                subtitle = o.optString("sub", ""),
                data = o.optString("data", ""),
                ms = o.optLong("ms"),
                lat = o.optDouble("lat"), lon = o.optDouble("lon"),
            )
        }
    }.getOrDefault(emptyList())
}

private val Context.collectiblesDataStore by androidx.datastore.preferences.preferencesDataStore(name = "collectibles")
