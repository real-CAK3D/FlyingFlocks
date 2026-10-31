package com.cak3d.biscuitmesh

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import com.cak3d.biscuitmesh.ble.BleScanner
import com.cak3d.biscuitmesh.ble.FleetManager
import com.cak3d.biscuitmesh.data.KnownDevicesStore
import com.cak3d.biscuitmesh.data.SettingsStore
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

    val fleet: FleetManager? by lazy {
        val adapter = bluetoothManager?.adapter ?: return@lazy null
        FleetManager(this, adapter, appScope, knownStore)
    }

    val scanner: BleScanner? by lazy {
        val adapter = bluetoothManager?.adapter ?: return@lazy null
        BleScanner(adapter)
    }
}
