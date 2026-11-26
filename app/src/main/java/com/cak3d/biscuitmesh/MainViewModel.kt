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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val appRef = app as BiscuitMeshApp
    val fleet: FleetManager? get() = appRef.fleet

    val devices get() = fleet?.devices
    val log get() = fleet?.log
    fun clearLog() { fleet?.clearLog() }

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

    // --- What the Flock dongle over BLE (wireless, no USB tether) ----------
    val wtfBleLink: StateFlow<com.cak3d.biscuitmesh.usb.WtfLinkState> = appRef.wtfBle.link
    val wtfBleDeviceName: StateFlow<String?> = appRef.wtfBle.deviceName
    /** Start (and remember) a wireless link to a WTF dongle. */
    fun connectWtfBle() {
        viewModelScope.launch { appRef.settingsStore.setWtfBleEnabled(true) }
        appRef.wtfBle.connect()
    }
    /** Drop the wireless link and stop reconnecting. */
    fun disconnectWtfBle() {
        viewModelScope.launch { appRef.settingsStore.setWtfBleEnabled(false) }
        appRef.wtfBle.disconnect()
    }

    /** QR/barcode scan results from the WTF dongle's camera (USB or BLE). */
    val wtfScans: kotlinx.coroutines.flow.Flow<com.cak3d.biscuitmesh.usb.WtfEvent.Scan> =
        kotlinx.coroutines.flow.merge(appRef.wtfSerial.scans, appRef.wtfBle.scans)

    /** Ask the connected dongle to run a camera QR/barcode scan; the result arrives on [wtfScans]. */
    fun scanWtf() {
        if (appRef.wtfBle.link.value == com.cak3d.biscuitmesh.usb.WtfLinkState.CONNECTED) appRef.wtfBle.requestQrScan()
        if (appRef.wtfSerial.link.value == com.cak3d.biscuitmesh.usb.WtfLinkState.CONNECTED) appRef.wtfSerial.requestQrScan()
    }

    /** Link state / name of a plugged-in ESP Marauder. */
    val marauderLink: StateFlow<com.cak3d.biscuitmesh.usb.WtfLinkState> = appRef.marauderSerial.link
    val marauderDeviceName: StateFlow<String?> = appRef.marauderSerial.deviceName

    /** Whether the Marauder's live sightings are plotted on the map. On by default. */
    val marauderSourceEnabled: StateFlow<Boolean> =
        appRef.settingsStore.marauderSourceEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setMarauderSourceEnabled(on: Boolean) { viewModelScope.launch { appRef.settingsStore.setMarauderSourceEnabled(on) } }
    fun connectMarauder() { appRef.marauderSerial.connect() }
    fun disconnectMarauder() { appRef.marauderSerial.disconnect() }
    fun rescanMarauder() { appRef.marauderSerial.sendCmd("scanall") }
    fun stopMarauderScan() { appRef.marauderSerial.sendCmd("stopscan") }

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

    // --- Biscuit board over USB-serial -----------------------------------
    /** Link state of a Biscuit board attached over USB-serial (the same protocol as BLE). */
    val usbBoardLink: StateFlow<com.cak3d.biscuitmesh.usb.WtfLinkState> = appRef.biscuitSerial.link
    val usbBoardName: StateFlow<String?> = appRef.biscuitSerial.deviceName
    fun connectUsbBoard() { appRef.biscuitSerial.connect() }
    fun disconnectUsbBoard() { appRef.biscuitSerial.disconnect() }

    // --- Meshtastic ------------------------------------------------------
    val meshLink: StateFlow<com.cak3d.biscuitmesh.mesh.MeshLinkState> = appRef.mesh.link
    /** Human-readable status of the last Caw connect attempt (why it did/didn't connect). */
    val meshDiag: StateFlow<String?> = appRef.mesh.diag
    val meshNodes: StateFlow<List<com.cak3d.biscuitmesh.mesh.MeshNode>> =
        appRef.mesh.nodes
            .map { it.values.sortedBy { n -> n.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun connectMesh() { appRef.mesh.scanAndConnect() }
    fun disconnectMesh() { appRef.mesh.disconnect() }
    val meshMessages: StateFlow<List<com.cak3d.biscuitmesh.mesh.MeshMessage>> = appRef.mesh.messages
    fun sendMeshText(text: String) = appRef.mesh.sendText(text)

    /** Manual map locations for GPS-less mesh nodes. */
    val meshPlacements: StateFlow<Map<Long, Pair<Double, Double>>> =
        appRef.meshPlacementStore.placements.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    fun placeMeshNode(num: Long, lat: Double, lon: Double) {
        viewModelScope.launch { appRef.meshPlacementStore.place(num, lat, lon) }
    }

    // --- Home base node ----------------------------------------------------
    val homeBase: StateFlow<com.cak3d.biscuitmesh.data.HomeBase?> =
        appRef.homeBaseStore.homeBase.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun setHomeBaseNode(num: Long, label: String) {
        viewModelScope.launch { appRef.homeBaseStore.setNode(num, label) }
    }
    fun setHomeBaseLocation(lat: Double, lon: Double) {
        viewModelScope.launch { appRef.homeBaseStore.setLocation(lat, lon) }
    }
    fun clearHomeBase() { viewModelScope.launch { appRef.homeBaseStore.clear() } }

    /** Live phone fix (lat, lon) — only active while something subscribes (e.g. a "use current
     *  location" dialog), so it doesn't hold GPS open the rest of the time. */
    val currentFix: StateFlow<Pair<Double, Double>?> =
        appRef.locationProvider.locationUpdates()
            .map { it.latitude to it.longitude }
            .catch { }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // --- Town / county / state crossing alerts --------------------------------
    val crossingAlerts: StateFlow<Boolean> =
        appRef.settingsStore.crossingAlerts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setCrossingAlerts(on: Boolean) = viewModelScope.launch { appRef.settingsStore.setCrossingAlerts(on) }

    val newDeviceAlerts: StateFlow<Boolean> =
        appRef.settingsStore.newDeviceAlerts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setNewDeviceAlerts(on: Boolean) = viewModelScope.launch { appRef.settingsStore.setNewDeviceAlerts(on) }

    val nightVision: StateFlow<Boolean> =
        appRef.settingsStore.nightVision.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setNightVision(on: Boolean) = viewModelScope.launch { appRef.settingsStore.setNightVision(on) }

    val skyAlerts: StateFlow<Boolean> =
        appRef.settingsStore.skyAlerts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setSkyAlerts(on: Boolean) = viewModelScope.launch {
        appRef.settingsStore.setSkyAlerts(on)
        com.cak3d.biscuitmesh.sky.SkyAlertScheduler.apply(appRef, on)
    }

    val carAutoStart: StateFlow<Boolean> =
        appRef.settingsStore.carAutoStart.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setCarAutoStart(on: Boolean) = viewModelScope.launch { appRef.settingsStore.setCarAutoStart(on) }
    val carBtName: StateFlow<String> =
        appRef.settingsStore.carBtName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    fun setCarBtName(name: String) = viewModelScope.launch { appRef.settingsStore.setCarBtName(name) }

    private var lastPlace: PlaceContext? = null
    private var lastGeoMs = 0L
    private var lastGeoLat = 0.0
    private var lastGeoLon = 0.0
    private val visitedTowns = HashSet<String>()

    private val seenDeviceIds = HashSet<String>()

    init {
        viewModelScope.launch { runCatching { visitedTowns.addAll(appRef.settingsStore.visitedTowns.first()) } }
        viewModelScope.launch { watchNewDevices() }
        // Captured rare birds become passport "species" stamps.
        viewModelScope.launch {
            appRef.rareBirds.birds.collect { birds ->
                birds.filter { it.captured }.forEach { b ->
                    grantStamp(com.cak3d.biscuitmesh.data.Stamp(
                        "species_${b.id}", com.cak3d.biscuitmesh.data.StampKind.SPECIES, b.name,
                        "Rare bird captured", "", System.currentTimeMillis()))
                }
            }
        }
    }

    /**
     * Buzz + speak the first time a never-before-seen device appears while wardriving. Seeds the
     * "seen" set from every past drive's capture so only genuinely-new MACs fire; bulk scan dumps
     * (many new at once) are absorbed silently so it can't spam.
     */
    private suspend fun watchNewDevices() {
        runCatching { captureSamplesByDrive(60) }.getOrDefault(emptyList())
            .forEach { (_, pts) -> pts.forEach { if (it.id.isNotBlank()) seenDeviceIds.add(it.id) } }
        val wd = appRef.wardrive ?: return
        wd.samples.collect { map ->
            if (!newDeviceAlerts.value || wd.state.value.active != true) {
                map.keys.forEach { seenDeviceIds.add(it) }   // keep the set current but stay quiet
                return@collect
            }
            val fresh = map.entries.filter { it.key.isNotBlank() && it.key !in seenDeviceIds }
            fresh.forEach { seenDeviceIds.add(it.key) }
            if (fresh.isEmpty() || fresh.size > 5) return@collect   // a bulk dump = not a live arrival
            val s = fresh.first().value
            val what = s.label.ifBlank { s.id.ifBlank { s.kind.name } }
            runCatching {
                val vib = appRef.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                vib?.vibrate(android.os.VibrationEffect.createOneShot(70, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            }
            speak("New device nearby: $what", flush = true)
        }
    }

    /** How many distinct towns you've ever been in. */
    val townsVisited: StateFlow<Int> =
        appRef.settingsStore.visitedTowns.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Emits a [BoundaryCrossing] whenever the operator drives into a new town, county or state.
     * Reverse-geocodes the live fix (throttled to ~150 m / 20 s) and only fires on a real change,
     * flagging the first time you've reached a given town this session. Rides the shared location
     * flow, so it costs nothing extra while the map/dashboard already have GPS up.
     */
    val boundaryCrossings: Flow<BoundaryCrossing> =
        currentFix.filterNotNull().mapNotNull { (lat, lon) ->
            val now = System.currentTimeMillis()
            if (lastPlace != null && now - lastGeoMs < 20_000L && haversineM(lat, lon, lastGeoLat, lastGeoLon) < 150.0) {
                return@mapNotNull null
            }
            lastGeoMs = now; lastGeoLat = lat; lastGeoLon = lon
            val place = reverseGeocode(lat, lon) ?: return@mapNotNull null
            val prev = lastPlace
            lastPlace = place
            if (prev == null) {
                if (place.town.isNotBlank() && visitedTowns.add(place.town)) appRef.settingsStore.addVisitedTown(place.town)
                return@mapNotNull null
            }
            val firstVisit = place.town.isNotBlank() && visitedTowns.add(place.town)
            if (firstVisit) appRef.settingsStore.addVisitedTown(place.town)
            val count = visitedTowns.size
            when {
                place.state.isNotBlank() && place.state != prev.state ->
                    BoundaryCrossing("state", place.state, place, firstVisit, count)
                place.county.isNotBlank() && place.county != prev.county ->
                    BoundaryCrossing("county", place.county, place, firstVisit, count)
                place.town.isNotBlank() && place.town != prev.town ->
                    BoundaryCrossing("town", place.town, place, firstVisit, count)
                else -> null
            }
        }.flowOn(Dispatchers.IO)

    private fun reverseGeocode(lat: Double, lon: Double): PlaceContext? = runCatching {
        @Suppress("DEPRECATION")
        val a = android.location.Geocoder(appRef, java.util.Locale.US).getFromLocation(lat, lon, 1)?.firstOrNull()
            ?: return null
        PlaceContext(
            town = a.locality ?: a.subAdminArea ?: "",
            county = a.subAdminArea ?: "",
            state = a.adminArea ?: "",
        )
    }.getOrNull()

    private fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2).let { it * it }
        return r * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
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
            // Reflect whether a scan actually started — start() no-ops when BT is off,
            // so hardcoding true left the UI stuck on "Scanning…" with nothing running.
            _scanning.value = scanner.isScanning
        }
    }

    /** Start a scan if one isn't already running (the Connect page auto-scans while open). */
    fun startScan() {
        val scanner = appRef.scanner ?: return
        val fleet = appRef.fleet ?: return
        if (scanner.isScanning) return
        fleet.onScanStart()
        scanner.start { found -> fleet.onScanFound(found) }
        _scanning.value = scanner.isScanning
    }

    fun stopScan() {
        appRef.scanner?.stop()
        _scanning.value = false
    }

    fun connect(address: String): Boolean = fleet?.connect(address) ?: false
    fun disconnect(address: String) = fleet?.disconnect(address)

    /** Tell connected boards to start a full sweep — WiFi + sub-GHz — which drives DATA:RF. */
    fun startSubGhzSweep() { fleet?.broadcast(command("scanall")); appRef.biscuitSerial.send(command("scanall")) }
    fun stopSubGhzSweep() { fleet?.broadcast(command("stopscan")); appRef.biscuitSerial.send(command("stopscan")) }
    /** Transmit a captured OOK burst (your OWN devices only). Firmware gates this behind its own flag. */
    fun transmitSubGhz(freqMhz: String, bits: String) {
        fleet?.broadcast(command("tx", "$freqMhz,$bits"))
        appRef.biscuitSerial.send(command("tx", "$freqMhz,$bits"))
    }

    // --- Maintenance: OTA firmware + importer ------------------------------
    /** Pull a new firmware .bin over Wi-Fi to the SD (bare = dev channel). Device reboots. */
    fun updateFirmware(url: String = "") {
        fleet?.broadcast(command("fetchfw", url)); appRef.biscuitSerial.send(command("fetchfw", url))
    }
    /** Run the on-device importer over /flockedup/import (emits DATA:IMPORT per file). */
    fun runImport() {
        fleet?.broadcast(command("import")); appRef.biscuitSerial.send(command("import"))
    }
    /** Push a file (.sub/.ir/.fu) from the phone to the node's /flockedup/import over BLE,
     *  base64-chunked on 4-char boundaries so each chunk decodes independently, then import. */
    fun pushFileToNode(name: String, bytes: ByteArray, autoImport: Boolean = true) = viewModelScope.launch {
        val safe = name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "pushed.sub" }
        fun send(n: String, p: String = "") { fleet?.broadcast(command(n, p)); appRef.biscuitSerial.send(command(n, p)) }
        send("putbegin", safe)
        kotlinx.coroutines.delay(150)
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        var i = 0
        while (i < b64.length) {
            val end = minOf(i + 300, b64.length)   // 300 is a multiple of 4 → whole base64 groups
            send("putb64", b64.substring(i, end))
            i = end
            kotlinx.coroutines.delay(30)
        }
        send("putend", if (autoImport) "import" else "")
    }

    private val _fwPush = kotlinx.coroutines.flow.MutableStateFlow(-1)   // -1 idle, else 0..100 %
    val fwPush: StateFlow<Int> = _fwPush.asStateFlow()

    /** Push a firmware .bin from the phone straight onto the node's SD /downloads over BLE
     *  (CMD:putfw), so the on-device bmorcelli Launcher can flash it — no pulling the SD.
     *  Progress 0..100 streams in [fwPush]. ~1.5 MB over BLE takes a few minutes. */
    fun pushFirmwareToNode(name: String, bytes: ByteArray) = viewModelScope.launch {
        fun send(n: String, p: String = "") { fleet?.broadcast(command(n, p)); appRef.biscuitSerial.send(command(n, p)) }
        val total = bytes.size.toLong().coerceAtLeast(1)
        val safe = name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "FlockedUp.bin" }
            .let { if (it.endsWith(".bin")) it else "$it.bin" }
        // The board writes each chunk to the SD and reports DATA:PUT:ack,<bytesWritten>; it
        // finishes with DATA:PUT:done. The sender outruns the board (chunks queue in the BLE
        // buffer), so progress MUST track the board's acks, not bytes queued — otherwise it
        // reads 100% while the file is still half-written, and a flash then would be corrupt.
        fun latestPut(): String =
            log?.value?.lastOrNull {
                it.message.type == com.cak3d.biscuitmesh.protocol.BiscuitMessage.Type.DATA &&
                    it.message.subtype == "PUT"
            }?.message?.payload ?: ""
        fun ackBytes(): Long {
            val p = latestPut()
            return if (p.startsWith("ack,")) p.removePrefix("ack,").trim().toLongOrNull() ?: 0L else 0L
        }
        _fwPush.value = 0
        send("putfw", safe)
        kotlinx.coroutines.delay(200)
        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        var i = 0
        while (i < b64.length) {
            val end = minOf(i + 300, b64.length)
            send("putb64", b64.substring(i, end))
            i = end
            _fwPush.value = (ackBytes() * 100 / total).toInt().coerceIn(0, 98)
            kotlinx.coroutines.delay(25)
        }
        send("putend", "")
        // Follow the board's acks until the file is fully written (done / acked ≥ total) or it
        // stalls — only THEN is it safe to flash.
        val deadline = System.currentTimeMillis() + 12 * 60 * 1000
        var lastA = -1L; var lastChange = System.currentTimeMillis()
        while (System.currentTimeMillis() < deadline) {
            val p = latestPut()
            if (p.startsWith("done")) break
            val a = ackBytes()
            if (a != lastA) { lastA = a; lastChange = System.currentTimeMillis() }
            else if (System.currentTimeMillis() - lastChange > 30000) break   // stalled 30 s
            _fwPush.value = (a * 100 / total).toInt().coerceIn(0, 99)
            if (a >= total) break
            kotlinx.coroutines.delay(400)
        }
        _fwPush.value = 100
        kotlinx.coroutines.delay(3000)
        _fwPush.value = -1
    }

    // --- On-board audio (speaker beep / mic) -------------------------------
    fun beep(freqHz: Int = 1600, ms: Int = 120) {
        val p = "$freqHz:$ms"
        fleet?.broadcast(command("beep", p)); appRef.biscuitSerial.send(command("beep", p))
    }
    fun speakerClick() { fleet?.broadcast(command("click")); appRef.biscuitSerial.send(command("click")) }
    fun speakerAlert() { fleet?.broadcast(command("alert")); appRef.biscuitSerial.send(command("alert")) }
    /** The crow's call on the node's speaker. */
    fun caw() { fleet?.broadcast(command("caw")); appRef.biscuitSerial.send(command("caw")) }
    /** Any named call/cue on the node: caw, chirp, trill, error, success, capture, scan, chime. */
    fun playSound(name: String) { fleet?.broadcast(command("sound", name)); appRef.biscuitSerial.send(command("sound", name)) }

    // --- Universal IR TV remote (fires the node's IR LED) ------------------
    /** Blast a universal-TV button across common brands. fn: power/volup/voldn/mute/chup/chdn. */
    fun irTv(fn: String) { fleet?.broadcast(command("irtv", fn)); appRef.biscuitSerial.send(command("irtv", fn)) }
    /** Sweep every common TV power code in turn (TV-B-Gone) until one matches. */
    fun tvbGone() { fleet?.broadcast(command("tvbgone")); appRef.biscuitSerial.send(command("tvbgone")) }
    /** Start/stop the node's live audio stream (DATA:MIC + DATA:ASPEC) for the Audio screen. */
    fun audioStart() { fleet?.broadcast(command("audio")); appRef.biscuitSerial.send(command("audio")) }
    fun audioStop() { fleet?.broadcast(command("audiostop")); appRef.biscuitSerial.send(command("audiostop")) }

    // --- Saved sub-GHz devices (named device -> labeled buttons) -----------
    /** Ask the node to stream its saved device/button inventory (DATA:DEV / DATA:DEVEND). */
    fun requestSavedDevices() {
        fleet?.broadcast(command("dev", "list"))
        appRef.biscuitSerial.send(command("dev", "list"))
    }
    /** Guided capture: the node auto-detects the band, rejects noise, saves <device>/<button>.sub. */
    fun addDeviceButton(device: String, button: String) {
        val p = "add:$device|$button"
        fleet?.broadcast(command("dev", p))
        appRef.biscuitSerial.send(command("dev", p))
    }
    /** Learn an IR frame into a device button (fires through the same Saved Devices list). */
    fun addIrButton(device: String, button: String) {
        val p = "addir:$device|$button"
        fleet?.broadcast(command("dev", p))
        appRef.biscuitSerial.send(command("dev", p))
    }
    /** Fire a saved button (re-transmit its captured .sub). Own-device use only. */
    fun playDeviceButton(device: String, button: String) {
        val p = "play:$device|$button"
        fleet?.broadcast(command("dev", p))
        appRef.biscuitSerial.send(command("dev", p))
    }
    /** Rename a whole device. */
    fun renameDevice(oldName: String, newName: String) {
        val p = "rename:$oldName|$newName"
        fleet?.broadcast(command("dev", p)); appRef.biscuitSerial.send(command("dev", p))
    }
    /** Rename a button within a device. */
    fun renameButton(device: String, oldBtn: String, newBtn: String) {
        val p = "btnrename:$device|$oldBtn|$newBtn"
        fleet?.broadcast(command("dev", p)); appRef.biscuitSerial.send(command("dev", p))
    }
    /** Delete a saved button, or the whole device if button is blank. */
    fun deleteDeviceButton(device: String, button: String = "") {
        val p = if (button.isBlank()) "del:$device" else "del:$device|$button"
        fleet?.broadcast(command("dev", p))
        appRef.biscuitSerial.send(command("dev", p))
    }
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

    /** Testing aid: ignore dead zones so scanning never pauses (e.g. bench-testing at home). */
    val ignoreDeadZones: StateFlow<Boolean> =
        appRef.settingsStore.ignoreDeadZones.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setIgnoreDeadZones(enabled: Boolean) {
        viewModelScope.launch { appRef.settingsStore.setIgnoreDeadZones(enabled) }
    }

    /** Use a connected board's GPS (Meshtastic node / dongle) as the wardrive fix. */
    val boardGps: Flow<Boolean> = appRef.settingsStore.boardGps
    fun setBoardGps(enabled: Boolean) { viewModelScope.launch { appRef.settingsStore.setBoardGps(enabled) } }
    /** Whether a board fix is live right now (for the Settings status line). */
    fun boardGpsFixLive(): Boolean = appRef.locationProvider.oneShotBoardFix() != null ||
        appRef.mesh.myGpsFix() != null

    fun startWardrive(mode: WardriveMode) {
        appRef.wardrive?.start(mode, phoneSensors.value)
        // Keep scanning alive with the screen off / app backgrounded.
        WardriveService.start(appRef)
    }

    /**
     * Phone-only background collection: forces the phone's own radios on, starts an ALL-mode
     * wardrive, and keeps it alive with the screen off via the foreground service (which pings
     * notification updates as the count climbs). Lock the phone and it keeps catching Wi-Fi/BT.
     */
    fun startBackgroundCollect() {
        viewModelScope.launch { appRef.settingsStore.setPhoneSensors(true) }
        appRef.wardrive?.start(WardriveMode.ALL, true)
        WardriveService.start(appRef)
    }

    fun stopBackgroundCollect() = stopWardrive()

    fun stopWardrive() {
        // Capture the finished drive before stopping (stop keeps samples/track).
        val st = appRef.wardrive?.state?.value
        val samples = appRef.wardrive?.samples?.value?.values?.toList() ?: emptyList()
        val track = appRef.wardrive?.track?.value ?: emptyList()
        appRef.wardrive?.stop()
        WardriveService.stop(appRef)
        if (st != null && st.startMs > 0L && (st.wifiCount + st.btCount) > 0) {
            viewModelScope.launch { saveDrive(st.startMs, st.wifiCount, st.btCount, samples, track) }
            // Auto spoken recap when the master voice toggle is on (Speaker honours it internally).
            val mins = ((System.currentTimeMillis() - st.startMs) / 60000L).coerceAtLeast(1)
            // Award Feathers for the drive: catches + distance, a first-of-day bonus and streak multiplier.
            viewModelScope.launch {
                // Your very first saved drive earns a passport "First Flight" event stamp.
                if (appRef.drivesStore.drives.first().isEmpty()) {
                    grantStamp(com.cak3d.biscuitmesh.data.Stamp("event_first_flight",
                        com.cak3d.biscuitmesh.data.StampKind.EVENT, "First Flight",
                        "Your first wardrive", "", System.currentTimeMillis()))
                }
                val cams = camerasAlongTrack(track)
                val miles = trackDistance(track) / 1609.344
                val base = st.wifiCount + st.btCount + cams * 2 + (miles * 2).toInt()
                val (firstBonus, mult) = appRef.feathersStore.recordDriveDay()
                val earned = ((base + firstBonus) * mult).toInt().coerceAtLeast(0)
                if (earned > 0) {
                    appRef.feathersStore.award(earned, "Drive · ${st.wifiCount + st.btCount} catches", fromDrive = true)
                    _lastFeathers.value = earned
                }
                speak("Drive saved. ${mins} minute${if (mins == 1L) "" else "s"}, " +
                    "${st.wifiCount} Wi-Fi and ${st.btCount} Bluetooth. Earned $earned feathers.")
            }
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
            cameraCount = camerasAlongTrack(track),
            distanceM = trackDistance(track),
        )
        appRef.drivesStore.save(session, csv)
    }

    /** Cameras actually passed on this drive — ones within ~150 m of the track, not the whole DB. */
    private fun camerasAlongTrack(track: List<LatLon>): Int {
        val hz = appRef.hazards?.hazards?.value ?: return 0
        if (hz.isEmpty() || track.isEmpty()) return 0
        val out = FloatArray(1)
        return hz.count { h ->
            track.any { p ->
                android.location.Location.distanceBetween(p.lat, p.lon, h.lat, h.lon, out)
                out[0] <= 150f
            }
        }
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

    // --- Cell towers -------------------------------------------------------
    val cellState: StateFlow<com.cak3d.biscuitmesh.sensors.CellSnapshot> = appRef.cell.state
    fun startCell() = appRef.cell.start()
    fun stopCell() = appRef.cell.stop()

    // --- Location passthrough (for the live aircraft view) -----------------
    fun locationUpdates() = appRef.locationProvider.locationUpdates()

    /** Speak a line through the TTS engine. Honours the master voice toggle (Settings → voice). */
    fun speak(text: String, flush: Boolean = false) = appRef.speaker.say(text, flush)

    // --- Talk-back voice --------------------------------------------------
    /** English voices the installed TTS engine offers, for the picker. */
    fun ttsVoices(): List<com.cak3d.biscuitmesh.audio.VoiceInfo> = appRef.speaker.availableVoices()
    val ttsVoice: StateFlow<String> =
        appRef.settingsStore.ttsVoice.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    fun setTtsVoice(name: String) = viewModelScope.launch {
        appRef.settingsStore.setTtsVoice(name); appRef.speaker.setVoice(name)
    }
    val ttsRate: StateFlow<Float> =
        appRef.settingsStore.ttsRate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)
    fun setTtsRate(rate: Float) = viewModelScope.launch {
        appRef.settingsStore.setTtsRate(rate); appRef.speaker.setRate(rate)
    }
    /** Speak a sample line with the current voice/rate (for the "Test" button). */
    fun testVoice() = appRef.speaker.say("Flocked Up talk-back. New device nearby, bright pass soon.", flush = true)

    // --- Place RF fingerprints --------------------------------------------
    val fingerprints: Flow<List<com.cak3d.biscuitmesh.data.Fingerprint>> = appRef.fingerprintStore.fingerprints
    fun saveFingerprint(name: String, lat: Double, lon: Double, devices: Map<String, String>) =
        viewModelScope.launch {
            appRef.fingerprintStore.add(com.cak3d.biscuitmesh.data.Fingerprint(
                java.util.UUID.randomUUID().toString(), name, lat, lon, System.currentTimeMillis(), devices))
        }
    fun removeFingerprint(id: String) = viewModelScope.launch { appRef.fingerprintStore.remove(id) }

    // --- Feathers (gamification currency) ---------------------------------
    val feathers: StateFlow<Long> =
        appRef.feathersStore.balance.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val feathersStreak: StateFlow<Int> =
        appRef.feathersStore.streak.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val feathersLedger: StateFlow<List<com.cak3d.biscuitmesh.data.FeatherEntry>> =
        appRef.feathersStore.ledger.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** The most recent drive's Feather award, for a one-shot "+N" cue (null once consumed). */
    private val _lastFeathers = MutableStateFlow<Int?>(null)
    val lastFeathers: StateFlow<Int?> = _lastFeathers
    fun clearLastFeathers() { _lastFeathers.value = null }

    /** Award Feathers for driving into a new town/county/state (called from the crossing collector). */
    fun awardCrossing(c: BoundaryCrossing) = viewModelScope.launch {
        val amt = when (c.kind) { "state" -> 150; "county" -> 50; else -> if (c.firstVisit) 25 else 10 }
        appRef.feathersStore.award(amt, "Entered ${c.label}", fromDrive = true)
        // First visit to a town/state earns a passport location stamp.
        if (c.firstVisit || c.kind == "state") {
            grantStamp(com.cak3d.biscuitmesh.data.Stamp(
                "loc_${c.kind}_${c.label}", com.cak3d.biscuitmesh.data.StampKind.LOCATION, c.label,
                c.kind.replaceFirstChar { it.uppercase() },
                listOfNotNull(c.place.county.ifBlank { null }, c.place.state.ifBlank { null }).joinToString(" · "),
                System.currentTimeMillis()))
        }
    }

    // --- Store / inventory / vanity ---------------------------------------
    val owned: StateFlow<Set<String>> =
        appRef.inventoryStore.owned.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val equippedTitle: StateFlow<String> =
        appRef.inventoryStore.equippedTitle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val equippedAccent: StateFlow<String> =
        appRef.inventoryStore.equippedAccent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val flockName: StateFlow<String> =
        appRef.settingsStore.flockName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _storeMsg = MutableStateFlow<String?>(null)
    val storeMsg: StateFlow<String?> = _storeMsg
    fun clearStoreMsg() { _storeMsg.value = null }

    private fun equipLocal(item: com.cak3d.biscuitmesh.data.StoreItem) = viewModelScope.launch {
        when (item.kind) {
            com.cak3d.biscuitmesh.data.StoreKind.TITLE -> appRef.inventoryStore.equipTitle(item.value)
            com.cak3d.biscuitmesh.data.StoreKind.ACCENT -> appRef.inventoryStore.equipAccent(item.value)
        }
    }

    fun buyItem(item: com.cak3d.biscuitmesh.data.StoreItem) = viewModelScope.launch {
        if (appRef.inventoryStore.owned.first().contains(item.id)) { equipLocal(item); return@launch }
        if (appRef.feathersStore.spend(item.price, "Bought ${item.name}")) {
            appRef.inventoryStore.own(item.id)
            equipLocal(item)
            _storeMsg.value = "Bought & equipped ${item.name}"
        } else _storeMsg.value = "Not enough Feathers for ${item.name}"
    }

    fun equipItem(item: com.cak3d.biscuitmesh.data.StoreItem) = equipLocal(item)
    fun unequipTitle() = viewModelScope.launch { appRef.inventoryStore.equipTitle("") }
    fun unequipAccent() = viewModelScope.launch { appRef.inventoryStore.equipAccent("") }

    /** First-time, free flock naming (only when none set). */
    fun setFlockNameFree(name: String) = viewModelScope.launch { appRef.settingsStore.setFlockName(name) }

    fun renameFlock(name: String) = viewModelScope.launch {
        if (appRef.feathersStore.spend(com.cak3d.biscuitmesh.data.StoreCatalog.RENAME_COST, "Flock rename")) {
            appRef.settingsStore.setFlockName(name); _storeMsg.value = "Flock renamed to $name"
        } else _storeMsg.value = "Rename needs ${com.cak3d.biscuitmesh.data.StoreCatalog.RENAME_COST} Feathers"
    }

    fun renameUsername(name: String) = viewModelScope.launch {
        if (appRef.feathersStore.spend(com.cak3d.biscuitmesh.data.StoreCatalog.RENAME_COST, "Username change")) {
            runCatching { appRef.crew.setHandle(name) }; _storeMsg.value = "Username changed to $name"
        } else _storeMsg.value = "Change needs ${com.cak3d.biscuitmesh.data.StoreCatalog.RENAME_COST} Feathers"
    }

    // --- Collectibles / Passport ------------------------------------------
    val stamps: StateFlow<List<com.cak3d.biscuitmesh.data.Stamp>> =
        appRef.collectiblesStore.stamps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** The just-collected stamp, for the animated passport overlay (null once dismissed). */
    private val _newStamp = MutableStateFlow<com.cak3d.biscuitmesh.data.Stamp?>(null)
    val newStamp: StateFlow<com.cak3d.biscuitmesh.data.Stamp?> = _newStamp
    fun clearNewStamp() { _newStamp.value = null }

    private fun stampBonus(kind: com.cak3d.biscuitmesh.data.StampKind): Int = when (kind) {
        com.cak3d.biscuitmesh.data.StampKind.SPECIES -> 200
        com.cak3d.biscuitmesh.data.StampKind.SKY -> 150
        com.cak3d.biscuitmesh.data.StampKind.LOCATION -> 20
        com.cak3d.biscuitmesh.data.StampKind.EVENT -> 100
    }

    private fun grantStamp(stamp: com.cak3d.biscuitmesh.data.Stamp) = viewModelScope.launch {
        if (appRef.collectiblesStore.collect(stamp)) {
            _newStamp.value = stamp
            appRef.feathersStore.award(stampBonus(stamp.kind), "Collected ${stamp.title}", fromDrive = false)
        }
    }

    /** Stamp a sky catch (from the Visible Passes / Starlink screens when something is overhead). */
    fun logSkyCatch(id: String, title: String, detail: String) = grantStamp(
        com.cak3d.biscuitmesh.data.Stamp("sky_$id", com.cak3d.biscuitmesh.data.StampKind.SKY, title,
            "Sky catch", detail, System.currentTimeMillis()))

    // --- Danger Zone ------------------------------------------------------
    fun resetProgress() = viewModelScope.launch {
        appRef.feathersStore.reset()
        appRef.inventoryStore.reset()
        appRef.collectiblesStore.reset()
        appRef.progress.reset()
    }
    fun wipeHistory() = viewModelScope.launch {
        appRef.drivesStore.clearAll()
        appRef.fingerprintStore.clearAll()
        appRef.settingsStore.clearVisitedTowns()
    }
    fun signOutRedoOnboarding() = viewModelScope.launch {
        appRef.settingsStore.setFlockEnabled(false)
        appRef.settingsStore.setFlockHandle("")
        appRef.settingsStore.resetTour()
    }
    /** Local account deletion: wipes everything on-device. (Server-side removal lands in Phase 4.) */
    fun deleteAccountLocal() = viewModelScope.launch {
        appRef.feathersStore.reset()
        appRef.inventoryStore.reset()
        appRef.collectiblesStore.reset()
        appRef.settingsStore.setFlockName("")
        appRef.progress.reset()
        appRef.drivesStore.clearAll()
        appRef.fingerprintStore.clearAll()
        appRef.settingsStore.clearVisitedTowns()
        appRef.settingsStore.setFlockEnabled(false)
        appRef.settingsStore.setFlockHandle("")
        appRef.settingsStore.resetTour()
    }

    // --- WiGLE account stats -----------------------------------------------
    val wigleAuth: Flow<String> = appRef.settingsStore.wigleAuth
    fun setWigleAuth(auth: String) { viewModelScope.launch { appRef.settingsStore.setWigleAuth(auth) } }

    // --- API keys (one store for every optional third-party credential) ------
    val apiKeys: StateFlow<Map<String, String>> =
        appRef.settingsStore.allApiKeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    fun setApiKey(id: String, value: String) { viewModelScope.launch { appRef.settingsStore.setApiKey(id, value) } }

    // --- Vision portals (external God's Eye View web apps) ------------------
    val visionPortals: StateFlow<List<com.cak3d.biscuitmesh.data.VisionPortal>> =
        appRef.visionStore.portals.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            com.cak3d.biscuitmesh.data.VisionStore.DEFAULTS,
        )
    fun addVisionPortal(name: String, url: String) =
        viewModelScope.launch { appRef.visionStore.add(name, url) }
    fun updateVisionPortal(index: Int, name: String, url: String) =
        viewModelScope.launch { appRef.visionStore.update(index, name, url) }
    fun removeVisionPortal(index: Int) =
        viewModelScope.launch { appRef.visionStore.remove(index) }
    fun restoreVisionDefaults() =
        viewModelScope.launch { appRef.visionStore.restoreDefaults() }

    // --- Tracker-following detector ----------------------------------------
    val trackerFollowers: StateFlow<List<com.cak3d.biscuitmesh.sensors.Follower>> = appRef.trackerFollow.followers
    fun recordTrackerSighting(mac: String, name: String, rssi: Int, lat: Double, lon: Double, timeMs: Long) =
        appRef.trackerFollow.record(mac, name, rssi, lat, lon, timeMs)
    fun clearTrackers() = appRef.trackerFollow.clear()

    // --- Flock (online) ----------------------------------------------------
    val crew: com.cak3d.biscuitmesh.crew.CrewManager get() = appRef.crew
    val crewShare: Flow<Boolean> = appRef.settingsStore.crewShare
    val flockEnabled: Flow<Boolean> = appRef.settingsStore.flockEnabled
    fun setCrewShare(on: Boolean) = viewModelScope.launch { appRef.settingsStore.setCrewShare(on) }
    fun enableFlock() {
        appRef.crew.activate()
        viewModelScope.launch { appRef.settingsStore.setFlockEnabled(true) }
    }
    val flockHandle: Flow<String> = appRef.settingsStore.flockHandleFlow
    fun setFlockHandle(name: String) = viewModelScope.launch { runCatching { appRef.crew.setHandle(name) } }

    /** Saved Broadcastify feed ID (numeric), for the live-scanner launcher in Feeds → Scanner. */
    val broadcastifyFeed: Flow<String> = appRef.settingsStore.broadcastifyFeed
    fun setBroadcastifyFeed(id: String) = viewModelScope.launch { appRef.settingsStore.setBroadcastifyFeed(id) }

    init {
        // Push the phone's wall clock to every connected board every 20s, so the T-Embed's
        // own top bar shows the right time/date even with no Wi-Fi/NTP. We send *local* epoch
        // (UTC + tz offset); the firmware formats it straight, no TZ handling on-device.
        viewModelScope.launch {
            while (true) {
                devices?.value?.values
                    ?.filter { it.state == com.cak3d.biscuitmesh.model.ConnectionState.READY }
                    ?.forEach { dev ->
                        val nowMs = System.currentTimeMillis()
                        val localEpoch = nowMs / 1000 + java.util.TimeZone.getDefault().getOffset(nowMs) / 1000
                        send(dev.address, "settime:$localEpoch")
                    }
                kotlinx.coroutines.delay(20_000)
            }
        }

        // Report lifetime totals to the flock whenever a saved drive changes the numbers — not
        // only while the Flock screen happens to be open. Gated on Flock being enabled, and
        // deduped by a signature so it pushes only when a total actually moves.
        viewModelScope.launch {
            var lastSig = ""
            kotlinx.coroutines.flow.combine(flockEnabled, drivesState) { on, d -> on to d }
                .collect { (on, drives) ->
                    if (!on || drives.isEmpty()) return@collect
                    val wifi = drives.sumOf { it.wifiCount.toLong() }
                    val bt = drives.sumOf { it.btCount.toLong() }
                    val cameras = drives.sumOf { it.cameraCount.toLong() }
                    val km = drives.sumOf { it.distanceM } / 1000.0
                    val sig = "$wifi/$bt/$cameras/${drives.size}/$km"
                    if (sig == lastSig) return@collect
                    lastSig = sig
                    runCatching { appRef.crew.pushStats(wifi, bt, cameras, drives.size.toLong(), km) }
                }
        }
    }

    /** Start the online layer (idempotent) so the flock chat polls even when sharing is off. */
    fun activateCrew() = appRef.crew.activate()
    fun sendFlockChat(text: String) = viewModelScope.launch { runCatching { appRef.crew.sendMessage(text) } }
    fun sendWhisper(otherId: String, text: String) = viewModelScope.launch { runCatching { appRef.crew.sendDm(otherId, text) } }

    // --- Device detail -----------------------------------------------------
    fun deviceHistory(id: String): com.cak3d.biscuitmesh.wardrive.DeviceHistory? = appRef.wardrive?.history(id)

    // --- Drive replay / coverage ------------------------------------------
    suspend fun captureSamples(id: String): List<com.cak3d.biscuitmesh.wardrive.GeoPt> =
        com.cak3d.biscuitmesh.wardrive.CaptureParse.parse(appRef.drivesStore.captureCsv(id) ?: "")

    /** Every saved drive's points, for a cumulative coverage heatmap (newest [limit] drives). */
    suspend fun allCaptureSamples(limit: Int = 40): List<com.cak3d.biscuitmesh.wardrive.GeoPt> {
        val ids = appRef.drivesStore.drives.first().filter { it.hasCapture }.take(limit).map { it.id }
        return ids.flatMap { captureSamples(it) }
    }

    /** Each saved drive's points, grouped by drive id — for the cross-drive device ledger. */
    suspend fun captureSamplesByDrive(limit: Int = 60): List<Pair<String, List<com.cak3d.biscuitmesh.wardrive.GeoPt>>> {
        val ds = appRef.drivesStore.drives.first().filter { it.hasCapture }.take(limit)
        return ds.map { it.id to captureSamples(it.id) }
    }

    // --- TPMS tire dashboard ----------------------------------------------
    val tpmsAssignments: Flow<Map<com.cak3d.biscuitmesh.data.TirePos, String>> = appRef.tpmsStore.assignments
    val tpmsLowPsi: Flow<Int> = appRef.tpmsStore.lowPsi
    fun assignTire(pos: com.cak3d.biscuitmesh.data.TirePos, id: String) = viewModelScope.launch { appRef.tpmsStore.assign(pos, id) }
    fun clearTire(pos: com.cak3d.biscuitmesh.data.TirePos) = viewModelScope.launch { appRef.tpmsStore.clear(pos) }
    fun setTpmsLowPsi(psi: Int) = viewModelScope.launch { appRef.tpmsStore.setLowPsi(psi) }

    // --- Watchlist ---------------------------------------------------------
    val watchlist: Flow<List<String>> = appRef.watchlistStore.entries
    fun addWatch(entry: String) = viewModelScope.launch { appRef.watchlistStore.add(entry) }
    fun removeWatch(entry: String) = viewModelScope.launch { appRef.watchlistStore.remove(entry) }

    // Local-only saved targets (networks / devices / locations) — never uploaded anywhere.
    val targets: Flow<List<com.cak3d.biscuitmesh.data.Target>> = appRef.targetsStore.targets
    fun addTarget(t: com.cak3d.biscuitmesh.data.Target) = viewModelScope.launch { appRef.targetsStore.add(t) }
    fun removeTarget(id: String) = viewModelScope.launch { appRef.targetsStore.remove(id) }
    fun setTargetScan(id: String, canScan: Boolean) =
        viewModelScope.launch { appRef.targetsStore.setCanScan(id, canScan) }

    // --- Appearance (theme system) -----------------------------------------
    val appFeel: Flow<String> = appRef.settingsStore.appFeel
    val appAccent: Flow<String> = appRef.settingsStore.appAccent

    // --- First-run tour ----------------------------------------------------
    val tourSeen: Flow<Boolean> = appRef.settingsStore.tourSeen
    fun dismissTour() = viewModelScope.launch { appRef.settingsStore.setTourSeen() }
    /** Show the onboarding wizard again (e.g. from Settings → Profile → Run setup again). */
    fun rerunSetup() = viewModelScope.launch { appRef.settingsStore.resetTour() }

    // --- In-app update check (GitHub Releases) -----------------------------
    private val _update = kotlinx.coroutines.flow.MutableStateFlow<com.cak3d.biscuitmesh.geo.AppRelease?>(null)
    /** The newest release when it's newer than the installed build, else null. */
    val update: kotlinx.coroutines.flow.StateFlow<com.cak3d.biscuitmesh.geo.AppRelease?> = _update
    @Volatile private var updateChecked = false
    fun checkForUpdate() {
        if (updateChecked) return
        updateChecked = true
        viewModelScope.launch {
            val rel = com.cak3d.biscuitmesh.geo.UpdateClient().latest() ?: return@launch
            if (com.cak3d.biscuitmesh.geo.isNewerVersion(rel.version, com.cak3d.biscuitmesh.BuildConfig.VERSION_NAME)) {
                _update.value = rel
            }
        }
    }
    fun dismissUpdate() { _update.value = null }
    fun setAppFeel(name: String) = viewModelScope.launch { appRef.settingsStore.setAppFeel(name) }
    fun setAppAccent(name: String) = viewModelScope.launch { appRef.settingsStore.setAppAccent(name) }

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

/** The town / county / state the operator is in, from reverse-geocoding the fix. */
data class PlaceContext(val town: String, val county: String, val state: String)

/** Fired when the operator drives into a new [kind] (town / county / state), named [label]. */
data class BoundaryCrossing(
    val kind: String,
    val label: String,
    val place: PlaceContext,
    val firstVisit: Boolean,
    val townCount: Int = 0,
)
