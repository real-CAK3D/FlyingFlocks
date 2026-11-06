package com.cak3d.biscuitmesh.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.watchlistDataStore by preferencesDataStore(name = "watchlist")

/**
 * The watchlist: MAC addresses or name fragments to keep an eye out for. A device matches
 * when its id or label contains any entry (case-insensitive). Purely local.
 */
class WatchlistStore(private val context: Context) {
    private val key = stringSetPreferencesKey("entries")

    val entries: Flow<List<String>> =
        context.watchlistDataStore.data.map { (it[key] ?: emptySet()).sorted() }

    suspend fun add(entry: String) {
        val e = entry.trim()
        if (e.isEmpty()) return
        context.watchlistDataStore.edit { prefs ->
            prefs[key] = (prefs[key] ?: emptySet()) + e
        }
    }

    suspend fun remove(entry: String) {
        context.watchlistDataStore.edit { prefs ->
            prefs[key] = (prefs[key] ?: emptySet()) - entry
        }
    }

    companion object {
        /** True if [id]/[label] matches any watch entry (case-insensitive substring). */
        fun matches(entries: Collection<String>, id: String, label: String): String? {
            val hay = "$id $label".lowercase()
            return entries.firstOrNull { hay.contains(it.lowercase()) }
        }
    }
}
