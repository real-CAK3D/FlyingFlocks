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

    /**
     * Frames and sends a raw console line to one board. Users type the part after
     * `CMD:` (e.g. `scanap` or `attack:type=deauth`); we add the `CMD:` prefix and
     * the required trailing colon when only a bare command name was given.
     */
    fun send(address: String, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return
        var line = if (body.startsWith("CMD:")) body else "CMD:$body"
        if (line.count { it == ':' } < 2) line = "$line:"
        fleet?.sendCommand(address, (line + "\n").toByteArray(Charsets.UTF_8))
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}
