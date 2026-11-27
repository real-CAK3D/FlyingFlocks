package com.cak3d.biscuitmesh.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cak3d.biscuitmesh.nav.NavCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.navLayoutDataStore by preferencesDataStore(name = "nav_layout")

/** The user's chosen bottom tabs and top-bar icons, each an ordered list of destination ids. */
data class NavLayout(
    val bottom: List<String> = NavCatalog.DEFAULT_BOTTOM,
    val top: List<String> = NavCatalog.DEFAULT_TOP,
) {
    val isDefault: Boolean
        get() = bottom == NavCatalog.DEFAULT_BOTTOM && top == NavCatalog.DEFAULT_TOP
}

/**
 * Persists how the user has arranged their navigation — which feature sits on each bottom tab and
 * top-bar icon, and in what order. Settings and Help are always kept present (re-added if a saved
 * layout somehow dropped them) but may be placed anywhere. Local only.
 */
class NavLayoutStore(private val context: Context) {
    private val bottomKey = stringPreferencesKey("bottom")
    private val topKey = stringPreferencesKey("top")

    val layout: Flow<NavLayout> = context.navLayoutDataStore.data.map { prefs ->
        val bottom = prefs[bottomKey]?.let(::parse) ?: NavCatalog.DEFAULT_BOTTOM
        val top = prefs[topKey]?.let(::parse) ?: NavCatalog.DEFAULT_TOP
        sanitize(NavLayout(bottom, top))
    }

    suspend fun setBottom(ids: List<String>) = context.navLayoutDataStore.edit {
        it[bottomKey] = serialize(sanitize(NavLayout(ids, it[topKey]?.let(::parse) ?: NavCatalog.DEFAULT_TOP)).bottom)
    }

    suspend fun setTop(ids: List<String>) = context.navLayoutDataStore.edit {
        it[topKey] = serialize(sanitize(NavLayout(it[bottomKey]?.let(::parse) ?: NavCatalog.DEFAULT_BOTTOM, ids)).top)
    }

    suspend fun reset() = context.navLayoutDataStore.edit {
        it.remove(bottomKey); it.remove(topKey)
    }

    companion object {
        /**
         * Keep a layout usable: drop unknown or duplicate ids, cap each bar to a sane size, and make
         * sure Settings and Help are reachable somewhere (re-added to the top bar if missing).
         */
        fun sanitize(l: NavLayout): NavLayout {
            fun clean(ids: List<String>) = ids.filter { it == NavCatalog.HELP || NavCatalog.byId(it) != null }.distinct()
            // Help is an action, not a route — it can never be a bottom tab.
            var bottom = clean(l.bottom).filter { it != NavCatalog.HELP }.take(6)
            var top = clean(l.top).take(8)
            if (bottom.isEmpty()) bottom = NavCatalog.DEFAULT_BOTTOM
            val present = (bottom + top).toSet()
            val mustHave = listOf("settings", NavCatalog.HELP).filter { it !in present }
            if (mustHave.isNotEmpty()) top = (top + mustHave).take(8 + mustHave.size)
            return NavLayout(bottom, top)
        }

        private fun parse(json: String): List<String> = runCatching {
            val arr = JSONArray(json); (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())

        private fun serialize(ids: List<String>): String = JSONArray(ids).toString()
    }
}
