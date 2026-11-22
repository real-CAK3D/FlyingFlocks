package com.cak3d.biscuitmesh.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.visionDataStore by preferencesDataStore(name = "vision")

/** One external "Vision" web portal — a God's Eye View style globe/app hosted somewhere
 *  the phone can reach (your own Tailscale node, a LAN box, or a public URL), opened in a
 *  full-screen in-app browser. [builtIn] portals are the ones the app ships with. */
data class VisionPortal(
    val name: String,
    val url: String,
    val builtIn: Boolean = false,
)

/**
 * The user's list of Vision portals. Ships seeded with the owner's CAK3Dvision fork; anyone
 * who runs the app can point their own fork of God's Eye View (reachable over Tailscale or a
 * URL) at it by adding a row. Purely local — the URLs never leave the phone.
 */
class VisionStore(private val context: Context) {
    private val key = stringPreferencesKey("portals_json")

    val portals: Flow<List<VisionPortal>> =
        context.visionDataStore.data.map { prefs ->
            prefs[key]?.let(::parse) ?: DEFAULTS
        }

    /** Add a new portal (user portals are never built-in). No-ops on a blank url. */
    suspend fun add(name: String, url: String) {
        val u = normalizeUrl(url)
        if (u.isBlank()) return
        val label = name.trim().ifBlank { hostOf(u) }
        mutate { list -> list + VisionPortal(label, u, builtIn = false) }
    }

    /** Replace the portal at [index] with new values (keeps its built-in flag). */
    suspend fun update(index: Int, name: String, url: String) {
        val u = normalizeUrl(url)
        if (u.isBlank()) return
        mutate { list ->
            if (index !in list.indices) return@mutate list
            val old = list[index]
            list.toMutableList().apply {
                this[index] = old.copy(name = name.trim().ifBlank { hostOf(u) }, url = u)
            }
        }
    }

    suspend fun remove(index: Int) {
        mutate { list -> if (index in list.indices) list.toMutableList().apply { removeAt(index) } else list }
    }

    /** Restore the shipped portals on top of whatever the user has (de-duplicated by url). */
    suspend fun restoreDefaults() {
        mutate { list ->
            val have = list.map { it.url }.toSet()
            list + DEFAULTS.filter { it.url !in have }
        }
    }

    private suspend fun mutate(f: (List<VisionPortal>) -> List<VisionPortal>) {
        context.visionDataStore.edit { prefs ->
            val current = prefs[key]?.let(::parse) ?: DEFAULTS
            prefs[key] = serialize(f(current))
        }
    }

    companion object {
        /** Shipped portals. The owner's fork is the first; others replace/extend it in-app. */
        val DEFAULTS = listOf(
            VisionPortal("CAK3Dvision", "https://nukebox.tailac984b.ts.net:4173", builtIn = true),
            VisionPortal("Conflictly", "https://www.conflictly.app/", builtIn = true),
        )

        /** Make a user-typed address into a loadable URL: default to https:// when no scheme. */
        fun normalizeUrl(raw: String): String {
            val t = raw.trim()
            if (t.isEmpty()) return ""
            return if (t.contains("://")) t else "https://$t"
        }

        fun hostOf(url: String): String =
            runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)

        private fun parse(json: String): List<VisionPortal> = runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                VisionPortal(o.optString("name"), o.optString("url"), o.optBoolean("builtIn", false))
            }
        }.getOrDefault(DEFAULTS)

        private fun serialize(list: List<VisionPortal>): String {
            val arr = JSONArray()
            list.forEach {
                arr.put(JSONObject().put("name", it.name).put("url", it.url).put("builtIn", it.builtIn))
            }
            return arr.toString()
        }
    }
}
