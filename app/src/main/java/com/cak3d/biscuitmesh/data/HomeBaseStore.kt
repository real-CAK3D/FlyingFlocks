package com.cak3d.biscuitmesh.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.homeBaseDataStore by preferencesDataStore(name = "home_base")

/**
 * The designated stationary "home base" mesh node and its fixed map location. Unlike a
 * mobile node (which reports its own GPS), the base sits in one place, so we pin it: once
 * a location is set it always draws on the map at that spot, in every view, whether or not
 * the node is currently heard.
 */
data class HomeBase(val num: Long, val label: String, val lat: Double?, val lon: Double?)

class HomeBaseStore(private val context: Context) {

    private val key = stringPreferencesKey("home_base_json")

    val homeBase: Flow<HomeBase?> = context.homeBaseDataStore.data.map { parse(it[key]) }

    private suspend fun current(): HomeBase? = parse(context.homeBaseDataStore.data.first()[key])

    /** Designate [num] as home. Keeps the pinned location only if it's the same node. */
    suspend fun setNode(num: Long, label: String) {
        val same = current()?.takeIf { it.num == num }
        write(HomeBase(num, label, same?.lat, same?.lon))
    }

    /** Pin (or re-pin) the current home node to a fixed location. No-op if no home is set. */
    suspend fun setLocation(lat: Double, lon: Double) {
        val prev = current() ?: return
        write(prev.copy(lat = lat, lon = lon))
    }

    suspend fun clear() {
        context.homeBaseDataStore.edit { it.remove(key) }
    }

    private suspend fun write(home: HomeBase) {
        val o = JSONObject().put("num", home.num).put("label", home.label)
        home.lat?.let { o.put("lat", it) }
        home.lon?.let { o.put("lon", it) }
        context.homeBaseDataStore.edit { it[key] = o.toString() }
    }

    private fun parse(json: String?): HomeBase? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(json)
            val num = o.optLong("num", 0L)
            if (num == 0L) return null
            HomeBase(
                num = num,
                label = o.optString("label", "Home base"),
                lat = if (o.has("lat")) o.optDouble("lat") else null,
                lon = if (o.has("lon")) o.optDouble("lon") else null,
            )
        }.getOrNull()
    }
}
