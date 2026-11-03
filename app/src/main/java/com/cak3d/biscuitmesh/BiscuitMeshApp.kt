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
import com.cak3d.biscuitmesh.usb.UsbDetector
import com.cak3d.biscuitmesh.wardrive.DriveStore
import com.cak3d.biscuitmesh.wardrive.WardriveManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        ProgressManager(appScope, progressStore, wardrive, hazards)
    }

    /** Separate phone-radio instance for the standalone WiFi/BT tab scans. */
    private val scanSensors: PhoneSensors by lazy {
        PhoneSensors(this, bluetoothManager?.adapter)
    }

    /** Live phone Wi-Fi/BLE scanning for the WiFi and Bluetooth tabs. */
    val phoneScan: PhoneScanManager by lazy { PhoneScanManager(scanSensors, progress) }

    /** On-demand camera sweep around a point (Field Guide "find cameras near me"). */
    suspend fun sweepCameras(lat: Double, lon: Double): Int {
        val radius = progress.cameraScanRadiusM()
        val found = runCatching { overpass.cameras(lat, lon, radius) }.getOrNull() ?: return 0
        hazardStore.merge(found)
        return found.size
    }

    override fun onCreate() {
        super.onCreate()
        usbDetector.start()
        // Touch the progress manager so it loads saved XP and starts scoring finds
        // from launch, even before the Field Guide screen is opened.
        progress

        // Voice: honour the on/off setting, and speak a camera alert once per hazard.
        appScope.launch { settingsStore.voiceAlerts.collect { speaker.enabled = it } }
        hazards?.let { hz ->
            appScope.launch {
                var lastId: String? = null
                hz.alert.collect { a ->
                    if (a == null) { lastId = null; return@collect }
                    if (a.hazard.id == lastId) return@collect
                    lastId = a.hazard.id
                    val kind = when (a.hazard.kind) {
                        HazardKind.FLOCK -> "Flock camera"
                        HazardKind.SPEED_CAMERA -> "Speed camera"
                        HazardKind.RED_LIGHT -> "Red light camera"
                        HazardKind.CAMERA -> "Camera"
                    }
                    val dist = NavigationManager.spokenDist(a.distanceM.toDouble())
                    val over = a.limitMph?.let { " You're over the $it limit." } ?: ""
                    speaker.say("$kind ahead, $dist.$over")
                }
            }
        }
    }
}
