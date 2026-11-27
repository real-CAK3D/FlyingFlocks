package com.cak3d.biscuitmesh.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.hubLayoutDataStore by preferencesDataStore(name = "hub_layout")

/**
 * The user's own arrangement of the Sensors & Sky hub — the order of the sections, the order of the
 * cards within each section, and which cards they've hidden. Everything is keyed by stable strings
 * (section titles and route ids) so it survives new features being added: anything the app ships
 * that the saved layout doesn't mention is simply appended in its default spot and stays visible.
 * Purely local.
 */
data class HubLayout(
    /** Section title -> ordered route ids. A section missing here keeps its shipped order. */
    val order: Map<String, List<String>> = emptyMap(),
    /** Route ids the user has hidden from the normal (non-edit) view. */
    val hidden: Set<String> = emptySet(),
    /** Ordered section titles. Empty = shipped order. */
    val sections: List<String> = emptyList(),
) {
    val isDefault: Boolean get() = order.isEmpty() && hidden.isEmpty() && sections.isEmpty()
}

class HubLayoutStore(private val context: Context) {
    private val key = stringPreferencesKey("layout_json")

    val layout: Flow<HubLayout> =
        context.hubLayoutDataStore.data.map { prefs -> prefs[key]?.let(::parse) ?: HubLayout() }

    /** Persist a section's new card order (route ids, in the order to show them). */
    suspend fun setSectionOrder(section: String, routes: List<String>) = mutate { l ->
        l.copy(order = l.order.toMutableMap().apply { this[section] = routes })
    }

    /** Persist the new order of the sections themselves. */
    suspend fun setSections(sections: List<String>) = mutate { l -> l.copy(sections = sections) }

    /** Show or hide one card. */
    suspend fun setHidden(route: String, hidden: Boolean) = mutate { l ->
        l.copy(hidden = l.hidden.toMutableSet().apply { if (hidden) add(route) else remove(route) })
    }

    /** Back to the shipped layout. */
    suspend fun reset() {
        context.hubLayoutDataStore.edit { it.remove(key) }
    }

    private suspend fun mutate(f: (HubLayout) -> HubLayout) {
        context.hubLayoutDataStore.edit { prefs ->
            prefs[key] = serialize(f(prefs[key]?.let(::parse) ?: HubLayout()))
        }
    }

    companion object {
        private fun parse(json: String): HubLayout = runCatching {
            val o = JSONObject(json)
            val orderObj = o.optJSONObject("order") ?: JSONObject()
            val order = buildMap {
                orderObj.keys().forEach { k ->
                    val arr = orderObj.getJSONArray(k)
                    put(k, (0 until arr.length()).map { arr.getString(it) })
                }
            }
            val hiddenArr = o.optJSONArray("hidden") ?: JSONArray()
            val hidden = (0 until hiddenArr.length()).map { hiddenArr.getString(it) }.toSet()
            val secArr = o.optJSONArray("sections") ?: JSONArray()
            val sections = (0 until secArr.length()).map { secArr.getString(it) }
            HubLayout(order, hidden, sections)
        }.getOrDefault(HubLayout())

        private fun serialize(l: HubLayout): String {
            val orderObj = JSONObject()
            l.order.forEach { (k, v) -> orderObj.put(k, JSONArray(v)) }
            return JSONObject()
                .put("order", orderObj)
                .put("hidden", JSONArray(l.hidden.toList()))
                .put("sections", JSONArray(l.sections))
                .toString()
        }

        /**
         * Apply a saved [layout] over a shipped list of sections, each paired with its routes in
         * ship order. Returns the effective (section, routes) list — saved order first, anything new
         * appended, nothing ever lost. Hiding is applied by the caller so edit mode can still show
         * hidden cards.
         */
        fun <T> arrange(
            shipped: List<Pair<String, List<T>>>,
            routeOf: (T) -> String,
            layout: HubLayout,
        ): List<Pair<String, List<T>>> {
            // Section order: saved titles first (that still exist), then any new sections.
            val byTitle = shipped.toMap()
            val titles = LinkedHashSet<String>().apply {
                layout.sections.forEach { if (it in byTitle) add(it) }
                shipped.forEach { add(it.first) }
            }
            return titles.map { title ->
                val entries = byTitle[title] ?: emptyList()
                val saved = layout.order[title]
                val arranged = if (saved == null) entries else {
                    val byRoute = entries.associateBy(routeOf)
                    val used = LinkedHashSet<T>()
                    saved.forEach { r -> byRoute[r]?.let { used.add(it) } }
                    entries.forEach { used.add(it) }   // new entries keep their ship position at the end
                    used.toList()
                }
                title to arranged
            }
        }
    }
}
