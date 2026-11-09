package com.cak3d.biscuitmesh

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import com.cak3d.biscuitmesh.audio.Speaker
import com.cak3d.biscuitmesh.ble.BleScanner
import com.cak3d.biscuitmesh.ble.FleetManager
import com.cak3d.biscuitmesh.data.DeadZonesStore
import com.cak3d.biscuitmesh.data.KnownDevicesStore
import com.cak3d.biscuitmesh.data.SettingsStore
import com.cak3d.biscuitmesh.geo.HazardKind
import com.cak3d.biscuitmesh.geo.HazardManager
import com.cak3d.biscuitmesh.geo.HazardStore
import com.cak3d.biscuitmesh.geo.OverpassClient
import com.cak3d.biscuitmesh.hunts.RareBirdManager
import com.cak3d.biscuitmesh.hunts.RareBirdStore
import com.cak3d.biscuitmesh.nav.NavigationManager
import com.cak3d.biscuitmesh.nav.OsrmClient
import com.cak3d.biscuitmesh.nav.PlacesStore
import com.cak3d.biscuitmesh.location.LocationProvider
import com.cak3d.biscuitmesh.progress.ProgressManager
import com.cak3d.biscuitmesh.progress.ProgressStore
import com.cak3d.biscuitmesh.sensors.PhoneScanManager
import com.cak3d.biscuitmesh.sensors.PhoneSensors
import com.cak3d.biscuitmesh.geo.Hazard
import com.cak3d.biscuitmesh.usb.BiscuitSerialSource
import com.cak3d.biscuitmesh.usb.UsbDetector
import com.cak3d.biscuitmesh.usb.WtfCamera
import com.cak3d.biscuitmesh.usb.WtfLinkState
import com.cak3d.biscuitmesh.usb.WtfSerialSource
import com.cak3d.biscuitmesh.wardrive.DriveStore
import com.cak3d.biscuitmesh.wardrive.WardriveManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * App-wide container. The fleet manager and scanner outlive any single screen,
 * so they live here rather than in a ViewModel that could be recreated.
 */
class BiscuitMeshApp : Application() {

    /** Lives as long as the process; owns fleet persistence work. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val bluetoothManager: BluetoothManager? by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val knownStore: KnownDevicesStore by lazy { KnownDevicesStore(this) }

    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    val deadZonesStore: DeadZonesStore by lazy { DeadZonesStore(this) }

    /** Local-only watchlist of MACs/names to flag when seen. */
    val watchlistStore: com.cak3d.biscuitmesh.data.WatchlistStore by lazy {
        com.cak3d.biscuitmesh.data.WatchlistStore(this)
    }

    /** Learned TPMS sensor-to-tire assignments for the tire dashboard. */
    val tpmsStore: com.cak3d.biscuitmesh.data.TpmsStore by lazy {
        com.cak3d.biscuitmesh.data.TpmsStore(this)
    }

    /** Local-only log of devices seen inside dead zones — never uploaded. */
    val deadZoneLogStore: com.cak3d.biscuitmesh.data.DeadZoneLogStore by lazy {
        com.cak3d.biscuitmesh.data.DeadZoneLogStore(this)
    }

    /** Live GNSS satellite status (which sats the GPS fix is using). */
    val gnss: com.cak3d.biscuitmesh.sensors.GnssManager by lazy {
        com.cak3d.biscuitmesh.sensors.GnssManager(this)
    }

    /** Live cell-tower status (which cells the modem sees, how strong). */
    val cell: com.cak3d.biscuitmesh.sensors.CellManager by lazy {
        com.cak3d.biscuitmesh.sensors.CellManager(this)
    }

    /** Best-effort "is a BLE tracker following me" detector. */
    val trackerFollow: com.cak3d.biscuitmesh.sensors.TrackerFollowManager by lazy {
        com.cak3d.biscuitmesh.sensors.TrackerFollowManager()
    }

    /** Online "Flock" layer (theGarden Supabase): anon identity, band codes, live locations. */
    val crew: com.cak3d.biscuitmesh.crew.CrewManager by lazy {
        com.cak3d.biscuitmesh.crew.CrewManager(appScope, settingsStore, locationProvider, deadZonesStore)
    }

    val fleet: FleetManager? by lazy {
        val adapter = bluetoothManager?.adapter ?: return@lazy null
        FleetManager(this, adapter, appScope, knownStore)
    }

    val scanner: BleScanner? by lazy {
        val adapter = bluetoothManager?.adapter ?: return@lazy null
        BleScanner(adapter)
    }

    val locationProvider: LocationProvider by lazy { LocationProvider(this) }

    private val phoneSensors: PhoneSensors by lazy {
        PhoneSensors(this, bluetoothManager?.adapter)
    }

    val wardrive: WardriveManager? by lazy {
        val f = fleet ?: return@lazy null
        WardriveManager(f, locationProvider, appScope, deadZonesStore, phoneSensors)
    }

    /** Watches for USB-OTG devices (serial boards, dongles) being plugged in. */
    val usbDetector: UsbDetector by lazy { UsbDetector(this) }

    /** The plugged-in What the Flock dongle (USB-serial), when connected. */
    val wtfSerial: WtfSerialSource by lazy { WtfSerialSource(this, appScope) }

    /** A Biscuit board attached over USB-serial (speaks the same CMD:/DATA: protocol as BLE). */
    val biscuitSerial: BiscuitSerialSource by lazy { BiscuitSerialSource(this, appScope) }

    /** An ESP Marauder attached over USB-serial; its scan output drops onto the wardrive map. */
    val marauderSerial: com.cak3d.biscuitmesh.usb.MarauderSerialSource by lazy {
        com.cak3d.biscuitmesh.usb.MarauderSerialSource(this, appScope)
    }

    /** Meshtastic mesh — connects to a node over BLE and reads its node database. */
    val mesh: com.cak3d.biscuitmesh.mesh.MeshtasticManager by lazy {
        com.cak3d.biscuitmesh.mesh.MeshtasticManager(this, bluetoothManager?.adapter)
    }

    /** Manual map locations for GPS-less mesh nodes (e.g. a home node). */
    val meshPlacementStore: com.cak3d.biscuitmesh.data.MeshPlacementStore by lazy {
        com.cak3d.biscuitmesh.data.MeshPlacementStore(this)
    }

    private val rareBirdStore: RareBirdStore by lazy { RareBirdStore(this) }

    val rareBirds: RareBirdManager by lazy { RareBirdManager(appScope, rareBirdStore, wardrive) }

    val placesStore: PlacesStore by lazy { PlacesStore(this) }

    /** Saved wardrive history (review + re-upload). */
    val drivesStore: DriveStore by lazy { DriveStore(this) }

    /** Text-to-speech for camera/speed alerts and turn-by-turn directions. */
    val speaker: Speaker by lazy { Speaker(this) }

    private val osrm: OsrmClient by lazy { OsrmClient() }

    /** In-app turn-by-turn navigation. */
    val navigation: NavigationManager by lazy {
        NavigationManager(appScope, locationProvider, osrm, speaker)
    }

    private val overpass: OverpassClient by lazy { OverpassClient() }
    private val hazardStore: HazardStore by lazy { HazardStore(this) }

    /** Camera hazards + speed limits + over-speed alerts (null if no BT adapter). */
    val hazards: HazardManager? by lazy {
        val wd = wardrive ?: return@lazy null
        HazardManager(appScope, wd, overpass, hazardStore)
    }

    // --- Levelling / progression -------------------------------------------
    private val progressStore: ProgressStore by lazy { ProgressStore(this) }

    /** Scores every Wi-Fi/BLE find and camera onto its track; gates the overall rank. */
    val progress: ProgressManager by lazy {
        ProgressManager(appScope, progressStore, wardrive, hazards, rareBirds)
    }

    /** Separate phone-radio instance for the standalone WiFi/BT tab scans. */
    private val scanSensors: PhoneSensors by lazy {
        PhoneSensors(this, bluetoothManager?.adapter)
    }

    /** Live phone Wi-Fi/BLE scanning for the WiFi and Bluetooth tabs. */
    val phoneScan: PhoneScanManager by lazy { PhoneScanManager(scanSensors, progress) }

    // --- Home-screen widget control ---------------------------------------
    @Volatile var subGhzSweeping = false
        private set

    val wardriveActive: Boolean get() = wardrive?.state?.value?.active == true
    val wardriveMode: com.cak3d.biscuitmesh.wardrive.WardriveMode?
        get() = wardrive?.state?.value?.let { if (it.active) it.mode else null }

    /** Toggle a background wardrive in [mode] (phone radios on) from a home-screen widget. */
    fun widgetToggleScan(mode: com.cak3d.biscuitmesh.wardrive.WardriveMode) {
        val wd = wardrive ?: return
        if (wd.state.value.active) {
            wd.stop(); WardriveService.stop(this)
        } else {
            appScope.launch { settingsStore.setPhoneSensors(true) }
            wd.start(mode, true); WardriveService.start(this)
            ScanControlNotification.clearStopped(this)
        }
        com.cak3d.biscuitmesh.widget.ScanWidgetBase.refreshAll(this)
    }

    /** Stop every phone-driven scan from the notification shade (the frozen-UI kill switch). */
    fun stopScanFromNotification() {
        runCatching { wardrive?.stop() }
        runCatching { phoneScan.stop() }
        WardriveService.stop(this)
        com.cak3d.biscuitmesh.widget.ScanWidgetBase.refreshAll(this)
        // Leave a one-tap "Start scan" notice behind so restarting is easy.
        ScanControlNotification.showStopped(this)
    }

    /** Start a full wardrive (phone radios on) from the notification shade. */
    fun startScanFromNotification() {
        val wd = wardrive ?: return
        if (!wd.state.value.active) {
            appScope.launch { settingsStore.setPhoneSensors(true) }
            wd.start(com.cak3d.biscuitmesh.wardrive.WardriveMode.ALL, true)
            WardriveService.start(this)
        }
        ScanControlNotification.clearStopped(this)
        com.cak3d.biscuitmesh.widget.ScanWidgetBase.refreshAll(this)
    }

    /** Toggle the connected boards' sub-GHz sweep from a home-screen widget. */
    fun widgetToggleSubGhz() {
        val cmd = if (subGhzSweeping) "stopscan" else "scanall"
        fleet?.broadcast(com.cak3d.biscuitmesh.protocol.command(cmd))
        biscuitSerial.send(com.cak3d.biscuitmesh.protocol.command(cmd))
        subGhzSweeping = !subGhzSweeping
        com.cak3d.biscuitmesh.widget.ScanWidgetBase.refreshAll(this)
    }

    /** On-demand camera sweep around a point (Field Guide "find cameras near me"). */
    suspend fun sweepCameras(lat: Double, lon: Double): Int {
        val radius = progress.cameraScanRadiusM()
        val found = runCatching { overpass.cameras(lat, lon, radius) }.getOrNull() ?: return 0
        hazardStore.merge(found)
        return found.size
    }

    @Volatile private var wtfEnabled = true
    @Volatile private var marauderEnabled = true
    @Volatile private var vibrateEnabled = true

    /** Short buzz for a camera alert (respects the vibrate-on-alert setting at the call site). */
    private fun buzz() {
        val v = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        } ?: return
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                v.vibrate(android.os.VibrationEffect.createOneShot(250, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") v.vibrate(250)
            }
        }
    }

    /**
     * Turns a set of dongle/backup camera sightings into map hazards. Each becomes a
     * Flock/ALPR [Hazard] keyed by its MAC (so the live stream, the board's saved
     * session and an imported backup all dedup to one pin). Returns how many had a
     * usable location and were merged. Used by the import path and reusable in tests.
     */
    suspend fun ingestWtfCameras(cams: List<WtfCamera>): Int {
        val hz = cams.mapNotNull { c ->
            val lat = c.lat ?: return@mapNotNull null
            val lon = c.lon ?: return@mapNotNull null
            Hazard("wtf:${c.mac}", HazardKind.FLOCK, lat, lon, c.name.ifBlank { c.mac })
        }
        hazardStore.merge(hz)
        return hz.size
    }

    override fun onCreate() {
        super.onCreate()
        usbDetector.start()

        // Board GPS: when enabled, the wardrive takes its fix from a connected Meshtastic
        // node's GPS (or the dongle's, if its firmware reports coordinates) instead of the phone.
        locationProvider.externalFix = {
            mesh.myGpsFix() ?: wtfSerial.gps.value?.let { g ->
                val la = g.lat; val lo = g.lon
                if (la != null && lo != null) la to lo else null
            }
        }
        appScope.launch { settingsStore.boardGps.collect { locationProvider.useBoard = it } }
        // Load the bundled IEEE OUI registry off the main thread for vendor lookups.
        appScope.launch(Dispatchers.IO) { com.cak3d.biscuitmesh.sensors.OuiDb.ensureLoaded(this@BiscuitMeshApp) }
        // Resume the Flock online layer if the operator has used it before.
        appScope.launch { if (settingsStore.flockEnabled.first()) crew.activate() }

        // Keep the home-screen widgets in sync with scan state. Repaint instantly on a
        // start/stop/mode change, and while scanning repaint as the live counts climb —
        // throttled to every 2.5s so the widgets show live numbers without churn.
        wardrive?.let { wd ->
            appScope.launch {
                var lastToggle: Pair<Boolean, com.cak3d.biscuitmesh.wardrive.WardriveMode>? = null
                var lastCounts = -1 to -1
                var lastRefresh = 0L
                wd.state.collect { s ->
                    val toggle = s.active to s.mode
                    val counts = s.wifiCount to s.btCount
                    val now = android.os.SystemClock.elapsedRealtime()
                    val toggled = toggle != lastToggle
                    if (toggled || (counts != lastCounts && now - lastRefresh > 2500)) {
                        lastToggle = toggle; lastCounts = counts; lastRefresh = now
                        com.cak3d.biscuitmesh.widget.ScanWidgetBase.refreshAll(this@BiscuitMeshApp)
                    }
                }
            }
        }
        appScope.launch {
            var lastRank = -1
            progress.state.collect { p ->
                if (p.rank != lastRank) { lastRank = p.rank; com.cak3d.biscuitmesh.widget.StatsWidget.refresh(this@BiscuitMeshApp) }
            }
        }

        // Watchlist alerts: keep the wardrive's watch set current, and notify on a first-time hit.
        wardrive?.let { wd ->
            appScope.launch { watchlistStore.entries.collect { wd.watchSet = it.toSet() } }
            wd.onWatchHit = { s ->
                NotificationHelper.notify(this, 2000 + (s.id.hashCode() and 0xFFF),
                    "Watchlist hit", "${s.label.ifBlank { s.id }} is in range (${s.rssi} dBm).")
            }
        }
        // Touch the progress manager so it loads saved XP and starts scoring finds
        // from launch, even before the Field Guide screen is opened.
        progress

        // Voice: honour the on/off setting, and speak a camera alert once per hazard.
        appScope.launch { settingsStore.voiceAlerts.collect { speaker.enabled = it } }
        appScope.launch { settingsStore.vibrateAlerts.collect { vibrateEnabled = it } }

        // Mirror the dongle-source toggle, and plot its live sightings as Flock
        // hazards (using the sighting's own GPS if it has one, else the current fix).
        appScope.launch { settingsStore.wtfSourceEnabled.collect { wtfEnabled = it } }
        appScope.launch {
            wtfSerial.detections.collect { d ->
                if (!wtfEnabled) return@collect
                // A live sighting with no board GPS is happening here now, so the phone's
                // current fix is right. A replayed saved sighting was seen elsewhere earlier,
                // so only plot it if it carries its own location — never borrow the current fix.
                val lat = d.lat ?: (if (d.live) wardrive?.state?.value?.lastLat else null) ?: return@collect
                val lon = d.lon ?: (if (d.live) wardrive?.state?.value?.lastLon else null) ?: return@collect
                hazardStore.merge(listOf(
                    Hazard("wtf:${d.mac}", HazardKind.FLOCK, lat, lon, d.ssid.ifBlank { d.mac })
                ))
            }
        }

        // An ESP Marauder on USB streams scan output; each parsed sighting is geo-tagged with
        // the phone's current fix (the Marauder has no GPS) and dropped onto the wardrive map,
        // the same pipeline as a phone-radio hit. Only ingested while a scan session is active.
        appScope.launch { settingsStore.marauderSourceEnabled.collect { marauderEnabled = it } }
        appScope.launch {
            marauderSerial.hits.collect { h ->
                if (!marauderEnabled) return@collect
                val kind = if (h.kind == com.cak3d.biscuitmesh.usb.MarauderKind.BT)
                    com.cak3d.biscuitmesh.wardrive.SampleKind.BT
                else com.cak3d.biscuitmesh.wardrive.SampleKind.AP
                wardrive?.addExternalHit(kind, h.mac, h.label, h.rssi, h.channel,
                    com.cak3d.biscuitmesh.usb.MARAUDER_SOURCE)
            }
        }

        // A Biscuit board on USB feeds the fleet log and registry exactly like a BLE board,
        // so its Wi-Fi / sub-GHz results flow onto the map and the Sub-GHz page.
        appScope.launch {
            fleet?.let { f -> biscuitSerial.lines.collect { line -> f.ingestLine(FleetManager.USB_BOARD_ADDR, line) } }
        }
        appScope.launch {
            fleet?.let { f -> biscuitSerial.link.collect { st -> f.setUsbBoardPresent(st == WtfLinkState.CONNECTED, biscuitSerial.deviceName.value) } }
        }

        hazards?.let { hz ->
            appScope.launch {
                var lastId: String? = null
                hz.alert.collect { a ->
                    if (a == null) { lastId = null; return@collect }
                    if (a.hazard.id == lastId) return@collect
                    lastId = a.hazard.id
                    if (vibrateEnabled) buzz()
                    val kind = when (a.hazard.kind) {
                        HazardKind.FLOCK -> "Flock camera"
                        HazardKind.SPEED_CAMERA -> "Speed camera"
                        HazardKind.RED_LIGHT -> "Red light camera"
                        HazardKind.CAMERA -> "Camera"
                    }
                    val dist = NavigationManager.spokenDist(a.distanceM.toDouble())
                    val over = a.limitMph?.let { " You're over the $it limit." } ?: ""
                    speaker.say("$kind ahead, $dist.$over")
                    NotificationHelper.notify(this@BiscuitMeshApp, 1001, "$kind ahead",
                        "${a.distanceM} m away.${a.limitMph?.let { " Over the $it limit." } ?: ""}")
                }
            }
        }
    }
}
