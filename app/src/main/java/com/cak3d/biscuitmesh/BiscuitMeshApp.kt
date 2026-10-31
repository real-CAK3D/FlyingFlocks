package com.cak3d.biscuitmesh

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import com.cak3d.biscuitmesh.ble.BleScanner
import com.cak3d.biscuitmesh.ble.FleetManager

/**
 * App-wide container. The fleet manager and scanner outlive any single screen,
 * so they live here rather than in a ViewModel that could be recreated.
 */
class BiscuitMeshApp : Application() {

    val bluetoothManager: BluetoothManager? by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    val fleet: FleetManager? by lazy {
        val adapter = bluetoothManager?.adapter ?: return@lazy null
        FleetManager(this, adapter)
    }

    val scanner: BleScanner? by lazy {
        val adapter = bluetoothManager?.adapter ?: return@lazy null
        BleScanner(adapter)
    }
}
