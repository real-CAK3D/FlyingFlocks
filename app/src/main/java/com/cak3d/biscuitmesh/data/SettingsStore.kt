package com.cak3d.biscuitmesh.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** App-level preferences: authorized-use flag and the WDGWars feeder config. */
class SettingsStore(private val context: Context) {

    private val authorizedUseKey = booleanPreferencesKey("authorized_use_accepted")
    private val wdgApiKeyKey = stringPreferencesKey("wdg_api_key")
    private val wdgAutoUploadKey = booleanPreferencesKey("wdg_auto_upload")
    private val wdgLastUploadKey = stringPreferencesKey("wdg_last_upload")
    private val phoneSensorsKey = booleanPreferencesKey("phone_sensors_enabled")
    private val hudEnabledKey = booleanPreferencesKey("hud_enabled")
    private val useMphKey = booleanPreferencesKey("use_mph")

    /** True once the operator has acknowledged the authorized-use-only terms. */
    val authorizedUse: Flow<Boolean> =
        context.settingsDataStore.data.map { it[authorizedUseKey] ?: false }

    suspend fun setAuthorizedUse(accepted: Boolean) {
        context.settingsDataStore.edit { it[authorizedUseKey] = accepted }
    }

    /** The WDGWars API key (64-char hex), or empty when not configured. */
    val wdgApiKey: Flow<String> =
        context.settingsDataStore.data.map { it[wdgApiKeyKey] ?: "" }

    suspend fun setWdgApiKey(key: String) {
        context.settingsDataStore.edit { it[wdgApiKeyKey] = key.trim() }
    }

    /** Upload the capture to WDGWars automatically when a wardrive session stops. */
    val wdgAutoUpload: Flow<Boolean> =
        context.settingsDataStore.data.map { it[wdgAutoUploadKey] ?: false }

    suspend fun setWdgAutoUpload(enabled: Boolean) {
        context.settingsDataStore.edit { it[wdgAutoUploadKey] = enabled }
    }

    /** Human summary of the last successful WDG upload, for at-a-glance confirmation. */
    val wdgLastUpload: Flow<String> =
        context.settingsDataStore.data.map { it[wdgLastUploadKey] ?: "" }

    suspend fun setWdgLastUpload(summary: String) {
        context.settingsDataStore.edit { it[wdgLastUploadKey] = summary }
    }

    /** Use the phone's own Wi-Fi/BLE radios as wardrive sensors. On by default. */
    val phoneSensors: Flow<Boolean> =
        context.settingsDataStore.data.map { it[phoneSensorsKey] ?: true }

    suspend fun setPhoneSensors(enabled: Boolean) {
        context.settingsDataStore.edit { it[phoneSensorsKey] = enabled }
    }

    /** Show the speed + speed-limit HUD on the wardrive map. On by default. */
    val hudEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[hudEnabledKey] ?: true }

    suspend fun setHudEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[hudEnabledKey] = enabled }
    }

    /** Display speeds in mph (true) or km/h (false). Defaults to mph. */
    val useMph: Flow<Boolean> =
        context.settingsDataStore.data.map { it[useMphKey] ?: true }

    suspend fun setUseMph(useMph: Boolean) {
        context.settingsDataStore.edit { it[useMphKey] = useMph }
    }
}
