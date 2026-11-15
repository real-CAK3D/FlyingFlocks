package com.cak3d.biscuitmesh.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.targetsDataStore by preferencesDataStore(name = "targets")

enum class TargetKind { NETWORK, DEVICE, LOCATION }

/**
 * A saved target you want to locate or keep an eye on. [value] is an SSID or MAC for
 * NETWORK/DEVICE, and "lat,lon" for LOCATION. [canScan] is the per-item "scan for this"
 * toggle. [id] is stable so toggles/removes find the right row.
 */
data class Target(
    val kind: TargetKind,
    val value: String,
    val label: String,
    val canScan: Boolean,
) {
    val id: String get() = "${kind.name}:$value"

    /** For a LOCATION target, the parsed lat/lon (or null if it isn't a location / is malformed). */
    val latLon: Pair<Double, Double>? get() {
        if (kind != TargetKind.LOCATION) return null
        val p = value.split(',')
        val la = p.getOrNull(0)?.trim()?.toDoubleOrNull() ?: return null
        val lo = p.getOrNull(1)?.trim()?.toDoubleOrNull() ?: return null
        return la to lo
    }
}

/**
 * Local-only list of targets (networks / devices / locations) to locate or watch for, each
 * with a "can scan" flag. Nothing here is EVER uploaded anywhere — no WiGLE, no cloud. It
 * lives only in this app's private storage. Entries are encoded one-per-line in a string set.
 */
class TargetsStore(private val context: Context) {
    private val key = stringSetPreferencesKey("targets")
    private val sep = "\u001f"

    val targets: Flow<List<Target>> =
        context.targetsDataStore.data.map { prefs ->
            (prefs[key] ?: emptySet()).mapNotNull { decode(it) }
                .sortedWith(compareBy({ it.kind.ordinal }, { it.label.lowercase() }))
        }

    suspend fun add(t: Target) {
        if (t.value.isBlank()) return
        context.targetsDataStore.edit { prefs ->
            val others = (prefs[key] ?: emptySet()).filter { decode(it)?.id != t.id }.toSet()
            prefs[key] = others + encode(t)
        }
    }

    suspend fun remove(id: String) {
        context.targetsDataStore.edit { prefs ->
            prefs[key] = (prefs[key] ?: emptySet()).filter { decode(it)?.id != id }.toSet()
        }
    }

    suspend fun setCanScan(id: String, canScan: Boolean) {
        context.targetsDataStore.edit { prefs ->
            prefs[key] = (prefs[key] ?: emptySet()).map { enc ->
                val t = decode(enc)
                if (t != null && t.id == id) encode(t.copy(canScan = canScan)) else enc
            }.toSet()
        }
    }

    private fun encode(t: Target): String =
        listOf(t.kind.name, t.value, t.label, if (t.canScan) "1" else "0").joinToString(sep)

    private fun decode(s: String): Target? {
        val p = s.split(sep)
        if (p.size < 4) return null
        val kind = runCatching { TargetKind.valueOf(p[0]) }.getOrNull() ?: return null
        return Target(kind, p[1], p[2], p[3] == "1")
    }
}
