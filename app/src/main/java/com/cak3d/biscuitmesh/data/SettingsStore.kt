package com.cak3d.biscuitmesh.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    private val voiceAlertsKey = booleanPreferencesKey("voice_alerts")
    private val mapStyleKey = stringPreferencesKey("map_style")
    private val wtfSourceEnabledKey = booleanPreferencesKey("wtf_source_enabled")
    private val keepAwakeKey = booleanPreferencesKey("keep_awake")
    private val vibrateAlertsKey = booleanPreferencesKey("vibrate_alerts")
    private val wigleAuthKey = stringPreferencesKey("wigle_auth")
    private val sbSessionKey = stringPreferencesKey("sb_session_json")
    private val crewShareKey = booleanPreferencesKey("crew_share_location")
    private val flockEnabledKey = booleanPreferencesKey("flock_enabled")
    private val flockHandleKey = stringPreferencesKey("flock_handle")
    private val appFeelKey = stringPreferencesKey("app_feel")
    private val appAccentKey = stringPreferencesKey("app_accent")

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

    /** Use the phone's own Wi-Fi/BLE radios as wardrive sensors. Off by default. */
    val phoneSensors: Flow<Boolean> =
        context.settingsDataStore.data.map { it[phoneSensorsKey] ?: false }

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

    /** Speak camera/speed alerts and turn-by-turn directions aloud. On by default. */
    val voiceAlerts: Flow<Boolean> =
        context.settingsDataStore.data.map { it[voiceAlertsKey] ?: true }

    suspend fun setVoiceAlerts(enabled: Boolean) {
        context.settingsDataStore.edit { it[voiceAlertsKey] = enabled }
    }

    /** Remembered map style (DARK/LIGHT/TOPO/SATELLITE). Defaults to DARK. */
    val mapStyle: Flow<String> =
        context.settingsDataStore.data.map { it[mapStyleKey] ?: "DARK" }

    suspend fun setMapStyle(style: String) {
        context.settingsDataStore.edit { it[mapStyleKey] = style }
    }

    /** Ingest live sightings from a plugged-in What the Flock dongle. On by default. */
    val wtfSourceEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[wtfSourceEnabledKey] ?: true }

    suspend fun setWtfSourceEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[wtfSourceEnabledKey] = enabled }
    }

    /** Keep the screen on everywhere in the app, not just on the wardrive map. Off by default. */
    val keepAwake: Flow<Boolean> =
        context.settingsDataStore.data.map { it[keepAwakeKey] ?: false }

    suspend fun setKeepAwake(enabled: Boolean) {
        context.settingsDataStore.edit { it[keepAwakeKey] = enabled }
    }

    /** Buzz the phone when a camera/speed alert fires. On by default. */
    val vibrateAlerts: Flow<Boolean> =
        context.settingsDataStore.data.map { it[vibrateAlertsKey] ?: true }

    suspend fun setVibrateAlerts(enabled: Boolean) {
        context.settingsDataStore.edit { it[vibrateAlertsKey] = enabled }
    }

    /** WiGLE API credential — the "Encoded for use" string from your WiGLE account. */
    val wigleAuth: Flow<String> =
        context.settingsDataStore.data.map { it[wigleAuthKey] ?: "" }

    suspend fun setWigleAuth(auth: String) {
        context.settingsDataStore.edit { it[wigleAuthKey] = auth.trim() }
    }

    /** Persisted Supabase anonymous session (JSON), or empty. */
    suspend fun sbSession(): String =
        context.settingsDataStore.data.first()[sbSessionKey] ?: ""

    suspend fun setSbSession(json: String) {
        context.settingsDataStore.edit { it[sbSessionKey] = json }
    }

    /** Share my live location with my crew (people who claimed my code). Off by default. */
    val crewShare: Flow<Boolean> =
        context.settingsDataStore.data.map { it[crewShareKey] ?: false }

    suspend fun setCrewShare(on: Boolean) {
        context.settingsDataStore.edit { it[crewShareKey] = on }
    }

    /** True once the operator has opened the Flock (online) feature — gates the backend loop. */
    val flockEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[flockEnabledKey] ?: false }

    suspend fun setFlockEnabled(on: Boolean) {
        context.settingsDataStore.edit { it[flockEnabledKey] = on }
    }

    /** This wing's chosen public handle for the Flock, or empty. */
    val flockHandleFlow: Flow<String> =
        context.settingsDataStore.data.map { it[flockHandleKey] ?: "" }

    suspend fun flockHandle(): String =
        context.settingsDataStore.data.first()[flockHandleKey] ?: ""

    suspend fun setFlockHandle(name: String) {
        context.settingsDataStore.edit { it[flockHandleKey] = name.trim() }
    }

    /** Chosen UI "feel" (ROUNDED / CIRCUIT / NEON). Defaults to ROUNDED. */
    val appFeel: Flow<String> =
        context.settingsDataStore.data.map { it[appFeelKey] ?: "ROUNDED" }

    suspend fun setAppFeel(name: String) {
        context.settingsDataStore.edit { it[appFeelKey] = name }
    }

    /** Chosen accent colour (enum name). Defaults to GOLD. */
    val appAccent: Flow<String> =
        context.settingsDataStore.data.map { it[appAccentKey] ?: "GOLD" }

    suspend fun setAppAccent(name: String) {
        context.settingsDataStore.edit { it[appAccentKey] = name }
    }
}
