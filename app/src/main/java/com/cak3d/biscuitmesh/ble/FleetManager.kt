package com.cak3d.biscuitmesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import com.cak3d.biscuitmesh.model.BiscuitDevice
import com.cak3d.biscuitmesh.model.ConnectionState
import com.cak3d.biscuitmesh.model.DeviceStatus
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The fleet registry: the heart of what makes this app different from the stock
 * one-device-at-a-time manager. It keeps every discovered / connected board in a
 * single map and holds multiple simultaneous [DeviceConnection]s.
 *
 * Android caps concurrent GATT links (commonly ~4-7 depending on the phone), so
 * [maxConnections] guards against exceeding it.
 */
@SuppressLint("MissingPermission")
class FleetManager(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    val maxConnections: Int = 7,
) : DeviceConnection.Listener {

    private val connections = mutableMapOf<String, DeviceConnection>()

    private val _devices = MutableStateFlow<Map<String, BiscuitDevice>>(emptyMap())
    val devices: StateFlow<Map<String, BiscuitDevice>> = _devices.asStateFlow()

    /** Rolling log of protocol lines across the whole fleet (address -> message). */
    private val _log = MutableStateFlow<List<Pair<String, BiscuitMessage>>>(emptyList())
    val log: StateFlow<List<Pair<String, BiscuitMessage>>> = _log.asStateFlow()

    val connectedCount: Int get() = connections.count { (addr, _) ->
        _devices.value[addr]?.state == ConnectionState.READY
    }

    fun onScanFound(found: BleScanner.Found) {
        _devices.update { current ->
            val existing = current[found.address]
            val updated = existing?.copy(rssi = found.rssi)
                ?: BiscuitDevice(
                    address = found.address,
                    displayName = found.name ?: shortName(found.address),
                    rssi = found.rssi,
                    state = ConnectionState.DISCOVERED,
                )
            current + (found.address to updated)
        }
    }

    fun connect(address: String): Boolean {
        if (connections.containsKey(address)) return true
        if (connectedCount >= maxConnections) return false
        val remote = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return false
        val conn = DeviceConnection(context, remote, this)
        connections[address] = conn
        conn.connect()
        return true
    }

    fun disconnect(address: String) {
        connections[address]?.disconnect()
    }

    fun rename(address: String, name: String) {
        _devices.update { it.mutate(address) { d -> d.copy(displayName = name) } }
    }

    fun sendCommand(address: String, bytes: ByteArray) {
        connections[address]?.writeCommand(bytes)
    }

    /** Fan a command out to every READY board at once. */
    fun broadcast(bytes: ByteArray) {
        _devices.value.forEach { (addr, d) ->
            if (d.state == ConnectionState.READY) connections[addr]?.writeCommand(bytes)
        }
    }

    fun refreshStatus(address: String) {
        connections[address]?.requestStatus()
    }

    // --- DeviceConnection.Listener ----------------------------------------

    override fun onStateChanged(address: String, state: ConnectionState, error: String?) {
        if (state == ConnectionState.DISCONNECTED || state == ConnectionState.FAILED) {
            connections.remove(address)
        }
        _devices.update { it.mutate(address) { d -> d.copy(state = state, lastError = error ?: d.lastError) } }
    }

    override fun onMessage(address: String, message: BiscuitMessage) {
        _log.update { (it + (address to message)).takeLast(500) }
    }

    override fun onStatus(address: String, status: DeviceStatus) {
        _devices.update { it.mutate(address) { d -> d.copy(status = status) } }
    }

    override fun onDeviceInfo(address: String, model: String?, firmware: String?) {
        _devices.update { it.mutate(address) { d -> d.copy(model = model ?: d.model, firmware = firmware ?: d.firmware) } }
    }

    private inline fun Map<String, BiscuitDevice>.mutate(
        address: String,
        block: (BiscuitDevice) -> BiscuitDevice,
    ): Map<String, BiscuitDevice> {
        val existing = this[address] ?: BiscuitDevice(address, shortName(address))
        return this + (address to block(existing))
    }

    private fun shortName(address: String): String =
        "Biscuit " + address.takeLast(5).replace(":", "")
}
