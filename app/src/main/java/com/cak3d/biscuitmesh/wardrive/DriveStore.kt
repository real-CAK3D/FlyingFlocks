package com.cak3d.biscuitmesh.wardrive

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Context.drivesDataStore by preferencesDataStore(name = "drives")

/**
 * Saved wardrive history. Session metadata lives in DataStore; each drive's capture
 * CSV (for re-upload) is written as a file under filesDir/drives, so a long capture
 * doesn't bloat the preferences store. Capped so history can't grow without bound.
 */
class DriveStore(private val context: Context) {

    private val key = stringPreferencesKey("drives_json")
    private val cap = 40
    private val dir: File by lazy { File(context.filesDir, "drives").apply { mkdirs() } }

    val drives: Flow<List<DriveSession>> =
        context.drivesDataStore.data.map { parse(it[key] ?: "[]") }

    private suspend fun current(): List<DriveSession> =
        parse(context.drivesDataStore.data.first()[key] ?: "[]")

    /** Saves a finished drive (newest first), with its capture CSV if one was provided. */
    suspend fun save(session: DriveSession, csv: String?) {
        withContext(Dispatchers.IO) {
            val hasCapture = !csv.isNullOrBlank()
            if (hasCapture) runCatching { File(dir, "${session.id}.csv").writeText(csv!!) }
            val next = (listOf(session.copy(hasCapture = hasCapture)) + current()).take(cap)
            // Drop capture files for drives that fell off the end of history.
            val keep = next.mapTo(HashSet()) { it.id }
            runCatching { dir.listFiles()?.forEach { f -> if (f.nameWithoutExtension !in keep) f.delete() } }
            context.drivesDataStore.edit { it[key] = toJson(next) }
        }
    }

    suspend fun remove(id: String) {
        runCatching { File(dir, "$id.csv").delete() }
        context.drivesDataStore.edit { it[key] = toJson(current().filterNot { d -> d.id == id }) }
    }

    suspend fun markUploaded(id: String) {
        context.drivesDataStore.edit {
            it[key] = toJson(current().map { d -> if (d.id == id) d.copy(uploaded = true) else d })
        }
    }

    suspend fun captureCsv(id: String): String? =
        withContext(Dispatchers.IO) { runCatching { File(dir, "$id.csv").readText() }.getOrNull() }

    private fun toJson(list: List<DriveSession>): String {
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(
                JSONObject()
                    .put("id", d.id).put("startMs", d.startMs).put("endMs", d.endMs)
                    .put("wifi", d.wifiCount).put("bt", d.btCount).put("cam", d.cameraCount)
                    .put("dist", d.distanceM).put("uploaded", d.uploaded).put("cap", d.hasCapture),
            )
        }
        return arr.toString()
    }

    private fun parse(json: String): List<DriveSession> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            DriveSession(
                id = id,
                startMs = o.optLong("startMs"),
                endMs = o.optLong("endMs"),
                wifiCount = o.optInt("wifi"),
                btCount = o.optInt("bt"),
                cameraCount = o.optInt("cam"),
                distanceM = o.optDouble("dist"),
                uploaded = o.optBoolean("uploaded", false),
                hasCapture = o.optBoolean("cap", false),
            )
        }
    }.getOrDefault(emptyList())
}
