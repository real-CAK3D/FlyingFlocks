package com.cak3d.biscuitmesh.hunts

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.rareBirdsDataStore by preferencesDataStore(name = "rare_birds")

/** Persists the Rare Birds hunt list (seeded collectibles, promoted finds, captures). */
class RareBirdStore(private val context: Context) {

    private val key = stringPreferencesKey("birds_json")

    val birds: Flow<List<RareBird>> =
        context.rareBirdsDataStore.data.map { parse(it[key] ?: "[]") }

    suspend fun replaceAll(birds: List<RareBird>) {
        context.rareBirdsDataStore.edit { it[key] = toJson(birds) }
    }

    private suspend fun current(): List<RareBird> =
        parse(context.rareBirdsDataStore.data.first()[key] ?: "[]")

    suspend fun addIfNew(bird: RareBird) {
        val list = current()
        if (list.any { it.id == bird.id }) return
        replaceAll((list + bird).takeLast(200))
    }

    suspend fun addAll(birds: List<RareBird>) {
        val list = current()
        val have = list.mapTo(HashSet()) { it.id }
        val merged = list + birds.filter { it.id !in have }
        replaceAll(merged.takeLast(200))
    }

    suspend fun capture(id: String) {
        replaceAll(current().map { if (it.id == id) it.copy(captured = true) else it })
    }

    private fun toJson(birds: List<RareBird>): String {
        val arr = JSONArray()
        birds.forEach { b ->
            arr.put(
                JSONObject()
                    .put("id", b.id).put("name", b.name).put("kind", b.kind.name)
                    .put("lat", b.lat).put("lon", b.lon)
                    .put("captured", b.captured).put("note", b.note),
            )
        }
        return arr.toString()
    }

    private fun parse(json: String): List<RareBird> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            RareBird(
                id = id,
                name = o.optString("name", "Rare bird"),
                kind = runCatching { BirdKind.valueOf(o.optString("kind", "RARE_FIND")) }.getOrDefault(BirdKind.RARE_FIND),
                lat = o.optDouble("lat"),
                lon = o.optDouble("lon"),
                captured = o.optBoolean("captured", false),
                note = o.optString("note", ""),
            )
        }
    }.getOrDefault(emptyList())
}
