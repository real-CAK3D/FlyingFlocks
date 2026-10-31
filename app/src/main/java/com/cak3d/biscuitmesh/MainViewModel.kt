package com.cak3d.biscuitmesh

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cak3d.biscuitmesh.ble.FleetManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val appRef = app as BiscuitMeshApp
    val fleet: FleetManager? get() = appRef.fleet

    val devices get() = fleet?.devices
    val log get() = fleet?.log

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

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

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}
