package com.cak3d.biscuitmesh.progress

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.progressDataStore by preferencesDataStore(name = "progress")

/** Snapshot persisted between sessions: XP + count per track, and which find ids have already scored. */
data class ProgressData(
    val xp: Map<ProgressTrack, Int> = emptyMap(),
    val count: Map<ProgressTrack, Int> = emptyMap(),
    val awarded: Set<String> = emptySet(),
)

/**
 * Persists the levelling progress. The awarded-id set keeps the same network or
 * camera from scoring twice across sessions; it's capped so it can't grow without
 * bound on a long-lived install.
 */
class ProgressStore(private val context: Context) {

    private val key = stringPreferencesKey("progress_json")

    /** Most-recent awarded ids kept; older ones drop off (they rarely reappear). */
    val awardedCap = 8000

    suspend fun load(): ProgressData {
        val json = context.progressDataStore.data.first()[key] ?: return ProgressData()
        return runCatching { parse(json) }.getOrDefault(ProgressData())
    }

    suspend fun save(data: ProgressData) {
        context.progressDataStore.edit { it[key] = toJson(data) }
    }

    private fun toJson(d: ProgressData): String {
        val xp = JSONObject()
        d.xp.forEach { (t, v) -> xp.put(t.name, v) }
        val count = JSONObject()
        d.count.forEach { (t, v) -> count.put(t.name, v) }
        val awarded = JSONArray()
        // Keep only the newest ids if we're over the cap (LinkedHashSet preserves order).
        d.awarded.toList().takeLast(awardedCap).forEach { awarded.put(it) }
        return JSONObject().put("xp", xp).put("count", count).put("awarded", awarded).toString()
    }

    private fun parse(json: String): ProgressData {
        val o = JSONObject(json)
        val xp = HashMap<ProgressTrack, Int>()
        val count = HashMap<ProgressTrack, Int>()
        o.optJSONObject("xp")?.let { j ->
            j.keys().forEach { k -> trackOf(k)?.let { xp[it] = j.optInt(k) } }
        }
        o.optJSONObject("count")?.let { j ->
            j.keys().forEach { k -> trackOf(k)?.let { count[it] = j.optInt(k) } }
        }
        val awarded = LinkedHashSet<String>()
        o.optJSONArray("awarded")?.let { a ->
            for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotEmpty() }?.let(awarded::add)
        }
        return ProgressData(xp, count, awarded)
    }

    private fun trackOf(name: String): ProgressTrack? =
        // Legacy saves used a single "WIFI" track before the 2.4/5 GHz split; fold that
        // grind into the 2.4 GHz track so existing progress isn't lost.
        if (name == "WIFI") ProgressTrack.WIFI_24
        else runCatching { ProgressTrack.valueOf(name) }.getOrNull()
}
