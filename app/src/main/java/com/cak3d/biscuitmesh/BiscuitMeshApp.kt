package com.cak3d.biscuitmesh

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import com.cak3d.biscuitmesh.ble.BleScanner
import com.cak3d.biscuitmesh.ble.FleetManager
import com.cak3d.biscuitmesh.data.DeadZonesStore
import com.cak3d.biscuitmesh.data.KnownDevicesStore
import com.cak3d.biscuitmesh.data.SettingsStore
import com.cak3d.biscuitmesh.geo.HazardManager
import com.cak3d.biscuitmesh.geo.HazardStore
import com.cak3d.biscuitmesh.geo.OverpassClient
import com.cak3d.biscuitmesh.hunts.RareBirdManager
import com.cak3d.biscuitmesh.hunts.RareBirdStore
import com.cak3d.biscuitmesh.location.LocationProvider
import com.cak3d.biscuitmesh.sensors.PhoneSensors
import com.cak3d.biscuitmesh.usb.UsbDetector
import com.cak3d.biscuitmesh.wardrive.WardriveManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    private val overpass: OverpassClient by lazy { OverpassClient() }
    private val hazardStore: HazardStore by lazy { HazardStore(this) }

    /** Camera hazards + speed limits + over-speed alerts (null if no BT adapter). */
    val hazards: HazardManager? by lazy {
        val wd = wardrive ?: return@lazy null
        HazardManager(appScope, wd, overpass, hazardStore)
    }

    override fun onCreate() {
        super.onCreate()
        usbDetector.start()
    }
}
