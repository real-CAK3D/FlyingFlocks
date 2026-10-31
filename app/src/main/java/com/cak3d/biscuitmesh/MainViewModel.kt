package com.cak3d.biscuitmesh

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cak3d.biscuitmesh.ble.FleetManager
import com.cak3d.biscuitmesh.data.DeadZone
import com.cak3d.biscuitmesh.protocol.command
import com.cak3d.biscuitmesh.wardrive.LatLon
import com.cak3d.biscuitmesh.wardrive.WardriveMode
import com.cak3d.biscuitmesh.wdg.WdgClient
import com.cak3d.biscuitmesh.wdg.WigleExport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val appRef = app as BiscuitMeshApp
    val fleet: FleetManager? get() = appRef.fleet

    val devices get() = fleet?.devices
    val log get() = fleet?.log

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** Address of the board whose detail screen is open. */
    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    fun open(address: String?) { _selected.value = address }

    fun toggleScan() {
        val scanner = appRef.scanner ?: return
        val fleet = appRef.fleet ?: return
        if (scanner.isScanning) {
            scanner.stop()
            _scanning.value = false
        } else {
            fleet.onScanStart()
            scanner.start { found -> fleet.onScanFound(found) }
            _scanning.value = true
        }
    }

    fun stopScan() {
        appRef.scanner?.stop()
        _scanning.value = false
    }

    fun connect(address: String): Boolean = fleet?.connect(address) ?: false
    fun disconnect(address: String) = fleet?.disconnect(address)
    fun connectAll() = fleet?.connectAll()
    fun disconnectAll() = fleet?.disconnectAll()
    fun forget(address: String) = fleet?.forget(address)
    fun rename(address: String, name: String) = fleet?.rename(address, name)
    fun refreshStatus(address: String) = fleet?.refreshStatus(address)

    fun nameFor(address: String): String = fleet?.nameFor(address) ?: address

    // --- Wardrive ----------------------------------------------------------
    val wardriveState get() = appRef.wardrive?.state
    val wardriveSamples get() = appRef.wardrive?.samples
    val wardriveTrack get() = appRef.wardrive?.track

    fun startWardrive(mode: WardriveMode) { appRef.wardrive?.start(mode) }

    fun stopWardrive() {
        appRef.wardrive?.stop()
        // Fire-and-forget upload when the operator has opted into auto-upload.
        if (wdgAutoUpload.value && wdgApiKey.value.isNotBlank()) uploadToWdg()
    }

    fun setWardriveMode(mode: WardriveMode) { appRef.wardrive?.setMode(mode) }

    /** Authorized-use acknowledgment, persisted. Gates the offensive tool modules. */
    val authorizedUse: StateFlow<Boolean> =
        (appRef.settingsStore.authorizedUse).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun acceptAuthorizedUse() {
        viewModelScope.launch { appRef.settingsStore.setAuthorizedUse(true) }
    }

    fun revokeAuthorizedUse() {
        viewModelScope.launch { appRef.settingsStore.setAuthorizedUse(false) }
    }

    // --- Dead zones --------------------------------------------------------
    /** Persisted scanning dead zones, surfaced for the settings screen. */
    val deadZones: Flow<List<DeadZone>> = appRef.deadZonesStore.zones

    fun addDeadZone(name: String, lat: Double, lon: Double, radiusM: Double) {
        viewModelScope.launch {
            appRef.deadZonesStore.add(
                DeadZone(
                    id = UUID.randomUUID().toString(),
                    name = name.ifBlank { "Dead zone" },
                    lat = lat,
                    lon = lon,
                    radiusM = radiusM.coerceIn(20.0, 5000.0),
                ),
            )
        }
    }

    fun removeDeadZone(id: String) {
        viewModelScope.launch { appRef.deadZonesStore.remove(id) }
    }

    /**
     * Grabs a single GPS fix for "use my current location" when adding a zone.
     * Caller must already hold a location permission; null on timeout.
     */
    suspend fun oneShotLocation(): LatLon? = withTimeoutOrNull(12_000) {
        val loc = appRef.locationProvider.locationUpdates(1000L).first()
        LatLon(loc.latitude, loc.longitude)
    }

    // --- WDGWars feeder ----------------------------------------------------
    private val wdg = WdgClient()

    val wdgApiKey: StateFlow<String> =
        appRef.settingsStore.wdgApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val wdgAutoUpload: StateFlow<Boolean> =
        appRef.settingsStore.wdgAutoUpload.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setWdgApiKey(key: String) {
        viewModelScope.launch { appRef.settingsStore.setWdgApiKey(key) }
    }

    fun setWdgAutoUpload(enabled: Boolean) {
        viewModelScope.launch { appRef.settingsStore.setWdgAutoUpload(enabled) }
    }

    sealed interface WdgStatus {
        data object Idle : WdgStatus
        data class Working(val message: String) : WdgStatus
        data class Done(val message: String) : WdgStatus
        data class Error(val message: String) : WdgStatus
    }

    private val _wdgStatus = MutableStateFlow<WdgStatus>(WdgStatus.Idle)
    val wdgStatus: StateFlow<WdgStatus> = _wdgStatus.asStateFlow()

    /** Checks a key against /api/me; [key] lets the settings screen test before saving. */
    fun validateWdgKey(key: String = wdgApiKey.value) {
        val k = key.trim()
        if (k.isEmpty()) { _wdgStatus.value = WdgStatus.Error("Enter an API key first."); return }
        _wdgStatus.value = WdgStatus.Working("Checking key…")
        viewModelScope.launch {
            _wdgStatus.value = when (val r = wdg.validate(k)) {
                is WdgClient.Result.Ok -> WdgStatus.Done("Key is valid.")
                is WdgClient.Result.HttpError ->
                    WdgStatus.Error(if (r.code == 401 || r.code == 403) "Key rejected (${r.code})." else "Server error ${r.code}.")
                is WdgClient.Result.Failure -> WdgStatus.Error("Network error: ${r.message}")
            }
        }
    }

    /** Builds a WiGLE CSV from the current capture and uploads it to WDGWars. */
    fun uploadToWdg() {
        val key = wdgApiKey.value.trim()
        if (key.isEmpty()) { _wdgStatus.value = WdgStatus.Error("Set your WDG API key in Settings first."); return }
        val samples = appRef.wardrive?.samples?.value?.values ?: emptyList()
        val count = WigleExport.uploadableCount(samples)
        if (count == 0) { _wdgStatus.value = WdgStatus.Error("Nothing to upload yet."); return }
        _wdgStatus.value = WdgStatus.Working("Uploading $count networks…")
        viewModelScope.launch {
            val csv = WigleExport.toCsv(samples)
            val fileName = "biscuit-mesh-${System.currentTimeMillis()}.csv"
            _wdgStatus.value = when (val r = wdg.uploadCsv(key, csv, fileName)) {
                is WdgClient.Result.Ok -> {
                    val parts = buildList {
                        r.accepted?.let { add("$it accepted") }
                        r.merged?.let { add("$it merged") }
                    }
                    WdgStatus.Done("Uploaded $count networks" + if (parts.isNotEmpty()) " (${parts.joinToString(", ")})." else ".")
                }
                is WdgClient.Result.HttpError ->
                    WdgStatus.Error(when (r.code) {
                        401, 403 -> "Key rejected (${r.code})."
                        413 -> "Capture too large (${r.code})."
                        429 -> "Rate limited — try again later (429)."
                        else -> "Upload failed (${r.code})."
                    })
                is WdgClient.Result.Failure -> WdgStatus.Error("Network error: ${r.message}")
            }
        }
    }

    fun clearWdgStatus() { _wdgStatus.value = WdgStatus.Idle }

    /** Frames the part-after-CMD text and fans it out to every READY board. */
    fun broadcast(text: String) {
        val bytes = frame(text) ?: return
        fleet?.broadcast(bytes)
    }

    /** Stop any running scan/attack/portal across the whole fleet. */
    fun stopAllBoards() {
        fleet?.broadcast(command("stopscan"))
    }

    private fun frame(text: String): ByteArray? {
        val body = text.trim()
        if (body.isEmpty()) return null
        var line = if (body.startsWith("CMD:")) body else "CMD:$body"
        if (line.count { it == ':' } < 2) line = "$line:"
        return (line + "\n").toByteArray(Charsets.UTF_8)
    }

    /**
     * Frames and sends a raw console line to one board. Users type the part after
     * `CMD:` (e.g. `scanap` or `attack:type=deauth`); we add the `CMD:` prefix and
     * the required trailing colon when only a bare command name was given.
     */
    fun send(address: String, text: String) {
        val bytes = frame(text) ?: return
        fleet?.sendCommand(address, bytes)
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}
