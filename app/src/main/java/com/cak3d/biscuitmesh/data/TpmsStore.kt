package com.cak3d.biscuitmesh.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tpmsDataStore by preferencesDataStore(name = "tpms")

/** The four tire positions, in display order. */
enum class TirePos(val short: String, val label: String) {
    FL("FL", "Front Left"), FR("FR", "Front Right"), RL("RL", "Rear Left"), RR("RR", "Rear Right")
}

/**
 * Remembers which TPMS sensor id sits on which tire, plus the low-pressure threshold.
 * Learn your car's four sensors once and the dashboard tracks them from then on. Local only.
 */
class TpmsStore(private val context: Context) {
    private fun key(p: TirePos) = stringPreferencesKey("tire_${p.name}")
    private val lowKey = intPreferencesKey("low_psi")

    /** position -> assigned sensor id (absent = not learned yet). */
    val assignments: Flow<Map<TirePos, String>> = context.tpmsDataStore.data.map { prefs ->
        TirePos.entries.mapNotNull { p -> prefs[key(p)]?.let { p to it } }.toMap()
    }

    /** Low-pressure alert threshold in PSI (default 30). */
    val lowPsi: Flow<Int> = context.tpmsDataStore.data.map { it[lowKey] ?: 30 }

    suspend fun assign(pos: TirePos, sensorId: String) {
        context.tpmsDataStore.edit { prefs ->
            // A sensor belongs to one tire — clear it from any other position first.
            TirePos.entries.forEach { if (prefs[key(it)] == sensorId) prefs.remove(key(it)) }
            prefs[key(pos)] = sensorId
        }
    }

    suspend fun clear(pos: TirePos) {
        context.tpmsDataStore.edit { it.remove(key(pos)) }
    }

    suspend fun setLowPsi(psi: Int) {
        context.tpmsDataStore.edit { it[lowKey] = psi.coerceIn(10, 60) }
    }
}
