package com.cak3d.biscuitmesh.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.inventoryDataStore by preferencesDataStore(name = "inventory")

/** Owned store items and what's currently equipped. */
class InventoryStore(private val context: Context) {

    private val ownedKey = stringSetPreferencesKey("owned")
    private val equippedTitleKey = stringPreferencesKey("equipped_title")
    private val equippedAccentKey = stringPreferencesKey("equipped_accent")

    val owned: Flow<Set<String>> = context.inventoryDataStore.data.map { it[ownedKey] ?: emptySet() }
    /** Equipped title text ("" = none). */
    val equippedTitle: Flow<String> = context.inventoryDataStore.data.map { it[equippedTitleKey] ?: "" }
    /** Equipped accent hex ("" = use the free accent / night vision). */
    val equippedAccent: Flow<String> = context.inventoryDataStore.data.map { it[equippedAccentKey] ?: "" }

    suspend fun own(id: String) {
        context.inventoryDataStore.edit { it[ownedKey] = (it[ownedKey] ?: emptySet()) + id }
    }

    suspend fun equipTitle(text: String) {
        context.inventoryDataStore.edit { it[equippedTitleKey] = text }
    }

    suspend fun equipAccent(hex: String) {
        context.inventoryDataStore.edit { it[equippedAccentKey] = hex }
    }

    suspend fun reset() {
        context.inventoryDataStore.edit {
            it[ownedKey] = emptySet(); it[equippedTitleKey] = ""; it[equippedAccentKey] = ""
        }
    }
}
