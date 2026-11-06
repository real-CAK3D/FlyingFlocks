package com.cak3d.biscuitmesh

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cak3d.biscuitmesh.ble.FleetManager
import com.cak3d.biscuitmesh.data.DeadZone
import com.cak3d.biscuitmesh.geo.Hazard
import com.cak3d.biscuitmesh.geo.HazardAlert
import com.cak3d.biscuitmesh.geo.SpeedLimit
import com.cak3d.biscuitmesh.hunts.RareBird
import com.cak3d.biscuitmesh.nav.ActiveRoute
import com.cak3d.biscuitmesh.nav.NavState
import com.cak3d.biscuitmesh.nav.Place
import com.cak3d.biscuitmesh.nav.PlaceKind
import com.cak3d.biscuitmesh.progress.Progress
import com.cak3d.biscuitmesh.protocol.command
import com.cak3d.biscuitmesh.usb.UsbDeviceInfo
import com.cak3d.biscuitmesh.wardrive.LatLon
import com.cak3d.biscuitmesh.wardrive.WardriveMode
import com.cak3d.biscuitmesh.wdg.WdgCell
import com.cak3d.biscuitmesh.wdg.WdgClient
import com.cak3d.biscuitmesh.wdg.WigleExport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val appRef = app as BiscuitMeshApp
    val fleet: FleetManager? get() = appRef.fleet

    val devices get() = fleet?.devices
    val log get() = fleet?.log

    /** USB-OTG devices currently attached (serial boards, dongles). */
    val usbDevices: kotlinx.coroutines.flow.Flow<List<UsbDeviceInfo>> get() = appRef.usbDetector.devices

    // --- Cameras / HUD / speed -------------------------------------------
    val hazards: kotlinx.coroutines.flow.Flow<List<Hazard>> =
        appRef.hazards?.hazards ?: kotlinx.coroutines.flow.flowOf(emptyList())
    val speedLimit: StateFlow<SpeedLimit?> = appRef.hazards?.speedLimit ?: MutableStateFlow(null)
    val hazardAlert: StateFlow<HazardAlert?> = appRef.hazards?.alert ?: MutableStateFlow(null)

    val hudEnabled: StateFlow<Boolean> =
        appRef.settingsStore.hudEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val useMph: StateFlow<Boolean> =
        appRef.settingsStore.useMph.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setHudEnabled(enabled: Boolean) { viewModelScope.launch { appRef.settingsStore.setHudEnabled(enabled) } }
    fun setUseMph(useMph: Boolean) { viewModelScope.launch { appRef.settingsStore.setUseMph(useMph) } }

    /** Speak camera/speed alerts and turn-by-turn directions. On by default. */
    val voiceAlerts: StateFlow<Boolean> =
        appRef.settingsStore.voiceAlerts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setVoiceAlerts(enabled: Boolean) { viewModelScope.launch { appRef.settingsStore.setVoiceAlerts(enabled) } }

    /** Remembered map style (DARK/LIGHT/TOPO/SATELLITE). */
    val mapStyle: StateFlow<String> =
        appRef.settingsStore.mapStyle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DARK")

    fun setMapStyle(style: String) { viewModelScope.launch { appRef.settingsStore.setMapStyle(style) } }

    // --- What the Flock dongle (USB-serial) ------------------------------
    /** Link state of the plugged-in WTF dongle. */
    val wtfLink: StateFlow<com.cak3d.biscuitmesh.usb.WtfLinkState> = appRef.wtfSerial.link
    val wtfDeviceName: StateFlow<String?> = appRef.wtfSerial.deviceName

    /** Whether the dongle's live sightings are plotted on the map. On by default. */
    val wtfSourceEnabled: StateFlow<Boolean> =
        appRef.settingsStore.wtfSourceEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setWtfSourceEnabled(on: Boolean) { viewModelScope.launch { appRef.settingsStore.setWtfSourceEnabled(on) } }

    // --- Screen / alerts / export ----------------------------------------
    val keepAwake: StateFlow<Boolean> =
        appRef.settingsStore.keepAwake.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setKeepAwake(on: Boolean) { viewModelScope.launch { appRef.settingsStore.setKeepAwake(on) } }

    val vibrateAlerts: StateFlow<Boolean> =
        appRef.settingsStore.vibrateAlerts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setVibrateAlerts(on: Boolean) { viewModelScope.launch { appRef.settingsStore.setVibrateAlerts(on) } }

    /** All cameras currently on the map (Overpass + dongle + imported), for export. */
    val hazardsState: StateFlow<List<Hazard>> =
        hazards.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Cameras as CSV — the same shape as a What the Flock camera export. */
    fun camerasCsv(): String {
        val sb = StringBuilder("id,kind,lat,lon,name\n")
        hazardsState.value.forEach { h ->
            fun cell(v: String) = if (v.any { it == ',' || it == '"' || it == '\n' }) "\"${v.replace("\"", "\"\"")}\"" else v
            sb.append("${cell(h.id)},${h.kind.name},${h.lat},${h.lon},${cell(h.name)}\n")
        }
        return sb.toString()
    }

    /** Cameras as KML, so they open straight into Google Earth / Maps. */
    fun camerasKml(): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val pins = hazardsState.value.joinToString("") { h ->
            "<Placemark><name>${esc(h.name.ifBlank { h.id })}</name>" +
                "<description>${esc(h.kind.name)}</description>" +
                "<Point><coordinates>${h.lon},${h.lat},0</coordinates></Point></Placemark>"
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document><name>Flying Flocks cameras</name>$pins</Document></kml>"
    }
    fun connectWtf() { appRef.wtfSerial.connect() }
    fun disconnectWtf() { appRef.wtfSerial.disconnect() }
    fun pullWtfSaved() { appRef.wtfSerial.requestDump() }
    fun loadWtfSdLog() { appRef.wtfSerial.requestSdDump() }
    val wtfGps: StateFlow<com.cak3d.biscuitmesh.usb.WtfGps?> = appRef.wtfSerial.gps
    fun refreshWtfGps() { appRef.wtfSerial.requestGpsStatus() }

    // --- Meshtastic ------------------------------------------------------
    val meshLink: StateFlow<com.cak3d.biscuitmesh.mesh.MeshLinkState> = appRef.mesh.link
    val meshNodes: StateFlow<List<com.cak3d.biscuitmesh.mesh.MeshNode>> =
        appRef.mesh.nodes
            .map { it.values.sortedBy { n -> n.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun connectMesh() { appRef.mesh.scanAndConnect() }
    fun disconnectMesh() { appRef.mesh.disconnect() }

    /** Manual map locations for GPS-less mesh nodes. */
    val meshPlacements: StateFlow<Map<Long, Pair<Double, Double>>> =
        appRef.meshPlacementStore.placements.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    fun placeMeshNode(num: Long, lat: Double, lon: Double) {
        viewModelScope.launch { appRef.meshPlacementStore.place(num, lat, lon) }
    }

    /**
     * Imports a What the Flock backup file's text: parses it and drops every located
     * camera onto the map. Reports the outcome through [importResult] for a toast.
     */
    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult.asStateFlow()
    fun clearImportResult() { _importResult.value = null }

    fun importWtfBackup(text: String) {
        viewModelScope.launch {
            val cams = runCatching { com.cak3d.biscuitmesh.usb.parseWtfBackup(text) }
                .getOrElse { _importResult.value = "Import failed: ${it.message}"; return@launch }
            val located = appRef.ingestWtfCameras(cams)
            val skipped = cams.size - located
            _importResult.value = buildString {
                append("Imported $located camera")
                if (located != 1) append("s")
                if (skipped > 0) append(" ($skipped had no location)")
            }
        }
    }

    // --- Navigation / saved places ---------------------------------------
    val places: Flow<List<Place>> = appRef.placesStore.places

    fun addPlace(name: String, kind: PlaceKind, lat: Double, lon: Double) {
        viewModelScope.launch {
            appRef.placesStore.add(Place(UUID.randomUUID().toString(), name.ifBlank { "Place" }, kind, lat, lon))
        }
    }

    fun removePlace(id: String) { viewModelScope.launch { appRef.placesStore.remove(id) } }

    /** Remove one camera (e.g. a stray test pin) tapped on the map. */
    fun removeHazard(id: String) { viewModelScope.launch { appRef.hazards?.remove(id) } }

    /** The active route geometry for the map (set by the navigation manager). */
    val activeRoute: StateFlow<ActiveRoute?> get() = appRef.navigation.route

    /** Live turn-by-turn guidance state, or null when not navigating. */
    val navState: StateFlow<NavState?> get() = appRef.navigation.state

    private val _routeStatus = MutableStateFlow<String?>(null)
    val routeStatus: StateFlow<String?> = _routeStatus.asStateFlow()

    /** Starts in-app turn-by-turn navigation to a saved place. */
    fun navigateTo(place: Place) = navigateToTarget(place.name, place.lat, place.lon)

    /** Starts in-app turn-by-turn navigation to any point (camera, bird, place). */
    fun navigateToTarget(name: String, lat: Double, lon: Double) {
        _routeStatus.value = "Routing to $name…"
        appRef.navigation.start(name, lat, lon)
        viewModelScope.launch {
            appRef.navigation.state.first { it != null }
            _routeStatus.value = null
        }
    }

    fun clearRoute() { appRef.navigation.stop(); _routeStatus.value = null }
    fun stopNavigation() = clearRoute()

    private suspend fun currentHere(): LatLon? {
        val s = appRef.wardrive?.state?.value
        if (s?.lastLat != null && s.lastLon != null) return LatLon(s.lastLat!!, s.lastLon!!)
        return oneShotLocation()
    }

    // --- Rare Birds hunt ---------------------------------------------------
    val rareBirds: StateFlow<List<RareBird>> get() = appRef.rareBirds.birds
    val rareBirdHere: StateFlow<com.cak3d.biscuitmesh.wardrive.LatLon?> get() = appRef.rareBirds.here
    fun rareBirdOnLocation(lat: Double, lon: Double) = appRef.rareBirds.onLocation(lat, lon)

    // --- Progression / Field Guide ----------------------------------------
    /** Per-track XP and the gated overall rank. */
    val progress: StateFlow<Progress> get() = appRef.progress.state

    private val _cameraSweep = MutableStateFlow<String?>(null)
    val cameraSweep: StateFlow<String?> = _cameraSweep.asStateFlow()

    /** On-demand sweep for Flock/speed/red-light cameras around the current fix. */
    fun sweepCamerasHere() {
        viewModelScope.launch {
            val here = currentHere()
            if (here == null) { _cameraSweep.value = "No location fix yet — try again."; return@launch }
            _cameraSweep.value = "Searching for cameras nearby…"
            val n = runCatching { appRef.sweepCameras(here.lat, here.lon) }.getOrDefault(0)
            _cameraSweep.value = if (n == 0) "No cameras found in range yet." else "Found $n cameras in range."
        }
    }

    fun clearCameraSweep() { _cameraSweep.value = null }

    // --- Phone-only live scanning (WiFi / Bluetooth tabs) ------------------
    val phoneScanResults get() = appRef.phoneScan.results
    val phoneScanning get() = appRef.phoneScan.scanning
    fun startPhoneScan(wifi: Boolean, ble: Boolean) = appRef.phoneScan.start(wifi, ble)
    fun stopPhoneScan() = appRef.phoneScan.stop()
    fun clearPhoneScan() = appRef.phoneScan.clear()

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

    /** Start a scan if one isn't already running (the Connect page auto-scans while open). */
    fun startScan() {
        val scanner = appRef.scanner ?: return
        val fleet = appRef.fleet ?: return
        if (scanner.isScanning) return
        fleet.onScanStart()
        scanner.start { found -> fleet.onScanFound(found) }
        _scanning.value = true
    }

    fun stopScan() {
        appRef.scanner?.stop()
        _scanning.value = false
    }

    fun connect(address: String): Boolean = fleet?.connect(address) ?: false
    fun disconnect(address: String) = fleet?.disconnect(address)

    /** Tell connected boards to start a full sweep — WiFi + sub-GHz — which drives DATA:RF. */
    fun startSubGhzSweep() { fleet?.broadcast(command("scanall")) }
    fun stopSubGhzSweep() { fleet?.broadcast(command("stopscan")) }
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

    /** Use the phone's own radios as sensors. Off by default; the boards are primary. */
    val phoneSensors: StateFlow<Boolean> =
        appRef.settingsStore.phoneSensors.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setPhoneSensors(enabled: Boolean) {
        viewModelScope.launch { appRef.settingsStore.setPhoneSensors(enabled) }
    }

    fun startWardrive(mode: WardriveMode) {
        appRef.wardrive?.start(mode, phoneSensors.value)
        // Keep scanning alive with the screen off / app backgrounded.
        WardriveService.start(appRef)
    }

    fun stopWardrive() {
        // Capture the finished drive before stopping (stop keeps samples/track).
        val st = appRef.wardrive?.state?.value
        val samples = appRef.wardrive?.samples?.value?.values?.toList() ?: emptyList()
        val track = appRef.wardrive?.track?.value ?: emptyList()
        appRef.wardrive?.stop()
        WardriveService.stop(appRef)
        if (st != null && st.startMs > 0L && (st.wifiCount + st.btCount) > 0) {
            viewModelScope.launch { saveDrive(st.startMs, st.wifiCount, st.btCount, samples, track) }
        }
        // Fire-and-forget upload when the operator has opted into auto-upload.
        if (wdgAutoUpload.value && wdgApiKey.value.isNotBlank()) uploadToWdg()
    }

    private suspend fun saveDrive(
        startMs: Long,
        wifi: Int,
        bt: Int,
        samples: List<com.cak3d.biscuitmesh.wardrive.WardriveSample>,
        track: List<LatLon>,
    ) {
        val csv = if (WigleExport.uploadableCount(samples) > 0) WigleExport.toCsv(samples) else null
        val session = com.cak3d.biscuitmesh.wardrive.DriveSession(
            id = UUID.randomUUID().toString(),
            startMs = startMs,
            endMs = System.currentTimeMillis(),
            wifiCount = wifi,
            btCount = bt,
            cameraCount = appRef.hazards?.hazards?.value?.size ?: 0,
            distanceM = trackDistance(track),
        )
        appRef.drivesStore.save(session, csv)
    }

    private fun trackDistance(track: List<LatLon>): Double {
        if (track.size < 2) return 0.0
        val out = FloatArray(1)
        var sum = 0.0
        for (i in 1 until track.size) {
            android.location.Location.distanceBetween(
                track[i - 1].lat, track[i - 1].lon, track[i].lat, track[i].lon, out,
            )
            sum += out[0]
        }
        return sum
    }

    // --- Drive history -----------------------------------------------------
    val drives: Flow<List<com.cak3d.biscuitmesh.wardrive.DriveSession>> = appRef.drivesStore.drives

    /** Snapshot of drives for the Stats screen and backup export. */
    val drivesState: StateFlow<List<com.cak3d.biscuitmesh.wardrive.DriveSession>> =
        drives.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * A full Flying Flocks data backup: cameras on the map, saved drives, and the
     * progression summary — the "all the data" export, as JSON text.
     */
    fun backupJson(): String {
        val root = org.json.JSONObject()
        root.put("app", "flying-flocks")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val cams = org.json.JSONArray()
        hazardsState.value.forEach { h ->
            cams.put(org.json.JSONObject().put("id", h.id).put("kind", h.kind.name)
                .put("lat", h.lat).put("lon", h.lon).put("name", h.name))
        }
        root.put("cameras", cams)

        val dr = org.json.JSONArray()
        drivesState.value.forEach { d ->
            dr.put(org.json.JSONObject().put("id", d.id).put("start", d.startMs).put("end", d.endMs)
                .put("wifi", d.wifiCount).put("bt", d.btCount).put("cameras", d.cameraCount)
                .put("distanceM", d.distanceM).put("uploaded", d.uploaded))
        }
        root.put("drives", dr)

        val p = progress.value
        root.put("progress", org.json.JSONObject().put("rank", p.rank).put("rankName", p.rankName)
            .put("levelXp", p.levelXp).put("totalFinds", p.totalFinds).put("cameraBoostXp", p.cameraBoostXp)
            .put("bonusLevels", p.bonusLevels))
        return root.toString(2)
    }

    fun deleteDrive(id: String) { viewModelScope.launch { appRef.drivesStore.remove(id) } }

    /** Re-uploads a saved drive's capture to WDGWars. */
    fun reuploadDrive(id: String) {
        val key = wdgApiKey.value.trim()
        if (key.isEmpty()) { _wdgStatus.value = WdgStatus.Error("Set your WDG API key in Settings first."); return }
        _wdgStatus.value = WdgStatus.Working("Re-uploading drive…")
        viewModelScope.launch {
            val csv = appRef.drivesStore.captureCsv(id)
            if (csv.isNullOrBlank()) { _wdgStatus.value = WdgStatus.Error("No capture saved for that drive."); return@launch }
            val fileName = "biscuit-mesh-drive-$id.csv"
            _wdgStatus.value = when (val r = wdg.uploadCsv(key, csv, fileName)) {
                is WdgClient.Result.Ok -> { appRef.drivesStore.markUploaded(id); WdgStatus.Done("Drive re-uploaded.") }
                is WdgClient.Result.HttpError -> WdgStatus.Error("Upload failed (${r.code}).")
                is WdgClient.Result.Failure -> WdgStatus.Error("Network error: ${r.message}")
            }
        }
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

    // --- Dead-zone device log (LOCAL ONLY — never uploaded) ----------------
    /** zoneId -> devices seen there, kept only on this phone. */
    val deadZoneLog: Flow<Map<String, List<com.cak3d.biscuitmesh.data.DeadZoneDevice>>> =
        appRef.deadZoneLogStore.log

    fun recordDeadZoneDevices(zoneId: String, devices: List<com.cak3d.biscuitmesh.data.DeadZoneDevice>) {
        viewModelScope.launch { appRef.deadZoneLogStore.record(zoneId, devices) }
    }

    fun clearDeadZoneLog(zoneId: String) {
        viewModelScope.launch { appRef.deadZoneLogStore.clear(zoneId) }
    }

    // --- GNSS satellites ---------------------------------------------------
    val gnssState: StateFlow<com.cak3d.biscuitmesh.sensors.GnssSnapshot> = appRef.gnss.state
    fun startGnss() = appRef.gnss.start()
    fun stopGnss() = appRef.gnss.stop()

    // --- Location passthrough (for the live aircraft view) -----------------
    fun locationUpdates() = appRef.locationProvider.locationUpdates()

    // --- WiGLE account stats -----------------------------------------------
    val wigleAuth: Flow<String> = appRef.settingsStore.wigleAuth
    fun setWigleAuth(auth: String) { viewModelScope.launch { appRef.settingsStore.setWigleAuth(auth) } }

    // --- Tracker-following detector ----------------------------------------
    val trackerFollowers: StateFlow<List<com.cak3d.biscuitmesh.sensors.Follower>> = appRef.trackerFollow.followers
    fun recordTrackerSighting(mac: String, name: String, rssi: Int, lat: Double, lon: Double, timeMs: Long) =
        appRef.trackerFollow.record(mac, name, rssi, lat, lon, timeMs)
    fun clearTrackers() = appRef.trackerFollow.clear()

    fun setDeadZoneRadius(id: String, radiusM: Double) {
        viewModelScope.launch { appRef.deadZonesStore.setRadius(id, radiusM) }
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

    /** Summary of the last successful WDG upload — the "did my capture go up?" confirmation. */
    val wdgLastUpload: StateFlow<String> =
        appRef.settingsStore.wdgLastUpload.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

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
                    val detail = buildString {
                        append("$count networks")
                        if (parts.isNotEmpty()) append(" (${parts.joinToString(", ")})")
                    }
                    val stamp = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.US).format(java.util.Date())
                    appRef.settingsStore.setWdgLastUpload("$detail · $stamp")
                    WdgStatus.Done("Uploaded $detail.")
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

    // Owned-territory overlay (pull side). Schema is undocumented, so this is
    // experimental: the tolerant parser skips cells it can't geolocate.
    private val _wdgCells = MutableStateFlow<List<WdgCell>>(emptyList())
    val wdgCells: StateFlow<List<WdgCell>> = _wdgCells.asStateFlow()

    fun fetchOwnedCells() {
        val key = wdgApiKey.value.trim()
        if (key.isEmpty()) { _wdgStatus.value = WdgStatus.Error("Set your WDG API key in Settings first."); return }
        _wdgStatus.value = WdgStatus.Working("Loading territory…")
        viewModelScope.launch {
            _wdgStatus.value = when (val r = wdg.fetchMyCells(key)) {
                is WdgClient.CellsResult.Ok -> {
                    _wdgCells.value = r.cells
                    if (r.cells.isEmpty())
                        WdgStatus.Done("No mappable territory returned (${r.raw.length} bytes).")
                    else WdgStatus.Done("Loaded ${r.cells.size} territory cells.")
                }
                is WdgClient.CellsResult.HttpError ->
                    WdgStatus.Error(if (r.code == 401 || r.code == 403) "Key rejected (${r.code})." else "Territory load failed (${r.code}).")
                is WdgClient.CellsResult.Failure -> WdgStatus.Error("Network error: ${r.message}")
            }
        }
    }

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
