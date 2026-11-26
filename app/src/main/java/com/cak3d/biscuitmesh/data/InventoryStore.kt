package com.cak3d.biscuitmesh.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    private val equippedAvatarKey = stringPreferencesKey("equipped_avatar")
    private val equippedOverlayKey = stringPreferencesKey("equipped_overlay")
    private val premiumKey = booleanPreferencesKey("premium")

    val owned: Flow<Set<String>> = context.inventoryDataStore.data.map { it[ownedKey] ?: emptySet() }
    /** Equipped title text ("" = none). */
    val equippedTitle: Flow<String> = context.inventoryDataStore.data.map { it[equippedTitleKey] ?: "" }
    /** Equipped accent hex ("" = use the free accent / night vision). */
    val equippedAccent: Flow<String> = context.inventoryDataStore.data.map { it[equippedAccentKey] ?: "" }
    /** Equipped avatar glyph ("" = default crow). */
    val equippedAvatar: Flow<String> = context.inventoryDataStore.data.map { it[equippedAvatarKey] ?: "" }
    /** Equipped overlay ring id ("" = none). */
    val equippedOverlay: Flow<String> = context.inventoryDataStore.data.map { it[equippedOverlayKey] ?: "" }
    /** Whether Flock Premium is unlocked. */
    val premium: Flow<Boolean> = context.inventoryDataStore.data.map { it[premiumKey] ?: false }

    suspend fun own(id: String) {
        context.inventoryDataStore.edit { it[ownedKey] = (it[ownedKey] ?: emptySet()) + id }
    }

    suspend fun equipTitle(text: String) {
        context.inventoryDataStore.edit { it[equippedTitleKey] = text }
    }

    suspend fun equipAccent(hex: String) {
        context.inventoryDataStore.edit { it[equippedAccentKey] = hex }
    }

    suspend fun equipAvatar(glyph: String) {
        context.inventoryDataStore.edit { it[equippedAvatarKey] = glyph }
    }

    suspend fun equipOverlay(id: String) {
        context.inventoryDataStore.edit { it[equippedOverlayKey] = id }
    }

    suspend fun setPremium(on: Boolean) {
        context.inventoryDataStore.edit { it[premiumKey] = on }
    }

    suspend fun reset() {
        context.inventoryDataStore.edit {
            it[ownedKey] = emptySet(); it[equippedTitleKey] = ""; it[equippedAccentKey] = ""
            it[equippedAvatarKey] = ""; it[equippedOverlayKey] = ""; it[premiumKey] = false
        }
    }
}
