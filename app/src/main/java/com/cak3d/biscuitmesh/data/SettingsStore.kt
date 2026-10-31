package com.cak3d.biscuitmesh.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** App-level preferences. Currently just the authorized-use acknowledgment. */
class SettingsStore(private val context: Context) {

    private val authorizedUseKey = booleanPreferencesKey("authorized_use_accepted")

    /** True once the operator has acknowledged the authorized-use-only terms. */
    val authorizedUse: Flow<Boolean> =
        context.settingsDataStore.data.map { it[authorizedUseKey] ?: false }

    suspend fun setAuthorizedUse(accepted: Boolean) {
        context.settingsDataStore.edit { it[authorizedUseKey] = accepted }
    }
}
