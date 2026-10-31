package com.cak3d.biscuitmesh

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cak3d.biscuitmesh.ble.FleetManager
import com.cak3d.biscuitmesh.protocol.command
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    /** Authorized-use acknowledgment, persisted. Gates the offensive tool modules. */
    val authorizedUse: StateFlow<Boolean> =
        (appRef.settingsStore.authorizedUse).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun acceptAuthorizedUse() {
        viewModelScope.launch { appRef.settingsStore.setAuthorizedUse(true) }
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
