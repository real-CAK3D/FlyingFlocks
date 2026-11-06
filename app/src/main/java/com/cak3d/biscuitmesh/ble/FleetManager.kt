package com.cak3d.biscuitmesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import com.cak3d.biscuitmesh.data.KnownDevice
import com.cak3d.biscuitmesh.data.KnownDevicesStore
import com.cak3d.biscuitmesh.model.BiscuitDevice
import com.cak3d.biscuitmesh.model.ConnectionState
import com.cak3d.biscuitmesh.model.DeviceStatus
import com.cak3d.biscuitmesh.model.LogEntry
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The fleet registry: the heart of what makes this app different from the stock
 * one-device-at-a-time manager. It keeps every known / discovered / connected
 * board in a single map and holds multiple simultaneous [DeviceConnection]s.
 *
 * Android caps concurrent GATT links (commonly ~4-7 depending on the phone), so
 * [maxConnections] guards against exceeding it.
 */
@SuppressLint("MissingPermission")
class FleetManager(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val scope: CoroutineScope,
    private val knownStore: KnownDevicesStore,
    val maxConnections: Int = 7,
) : DeviceConnection.Listener {

    // Mutated from the main thread (connect/disconnect) and the GATT binder thread
    // (onStateChanged → remove), so it must be concurrent to avoid HashMap corruption.
    private val connections = java.util.concurrent.ConcurrentHashMap<String, DeviceConnection>()

    private val _devices = MutableStateFlow<Map<String, BiscuitDevice>>(emptyMap())
    val devices: StateFlow<Map<String, BiscuitDevice>> = _devices.asStateFlow()

    /** Rolling, timestamped log of protocol lines across the whole fleet. */
    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    val connectedCount: Int
        get() = _devices.value.values.count { it.state == ConnectionState.READY }

    val liveCount: Int
        get() = _devices.value.values.count { it.isLive }

    init {
        // Seed the fleet with saved boards so they appear (Out of range) immediately.
        scope.launch {
            val known = knownStore.load()
            if (known.isNotEmpty()) {
                _devices.update { current ->
                    val merged = current.toMutableMap()
                    known.forEach { k ->
                        if (!merged.containsKey(k.address)) {
                            merged[k.address] = BiscuitDevice(
                                address = k.address,
                                displayName = k.name,
                                state = ConnectionState.DISCONNECTED,
                                known = true,
                            )
                        }
                    }
                    merged
                }
            }
        }
    }

    // --- Scanning ----------------------------------------------------------

    /** Call when a fresh scan sweep starts, so stale "in range" flags clear. */
    fun onScanStart() {
        _devices.update { current ->
            current.mapValues { (_, d) -> if (d.isLive) d else d.copy(inRange = false) }
        }
    }

    fun onScanFound(found: BleScanner.Found) {
        _devices.update { current ->
            val existing = current[found.address]
            val updated = existing?.copy(
                rssi = found.rssi,
                inRange = true,
                lastSeenMs = System.currentTimeMillis(),
                state = if (existing.isLive) existing.state else ConnectionState.DISCOVERED,
            ) ?: BiscuitDevice(
                address = found.address,
                displayName = found.name ?: shortName(found.address),
                rssi = found.rssi,
                state = ConnectionState.DISCOVERED,
                inRange = true,
                lastSeenMs = System.currentTimeMillis(),
            )
            current + (found.address to updated)
        }
    }

    // --- Connection --------------------------------------------------------

    /** Connect one board and remember it as part of the fleet. */
    fun connect(address: String): Boolean {
        markKnown(address)
        if (connections.containsKey(address)) return true
        if (liveCount >= maxConnections) return false
        val remote = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return false
        val conn = DeviceConnection(context, remote, this)
        connections[address] = conn
        conn.connect()
        return true
    }

    fun disconnect(address: String) {
        connections[address]?.disconnect()
    }

    /** Connect every in-range board not already live, up to the concurrency cap. */
    fun connectAll() {
        _devices.value.values
            .filter { it.inRange && !it.isLive }
            .sortedByDescending { it.rssi }
            .forEach { if (liveCount < maxConnections) connect(it.address) }
    }

    fun disconnectAll() {
        connections.keys.toList().forEach { connections[it]?.disconnect() }
    }

    /** Remove a board from the saved fleet (and drop it if it's not in range). */
    fun forget(address: String) {
        connections[address]?.disconnect()
        _devices.update { current ->
            val d = current[address]
            if (d != null && d.inRange) {
                current + (address to d.copy(known = false))
            } else {
                current - address
            }
        }
        persistKnown()
    }

    fun rename(address: String, name: String) {
        _devices.update { it.mutate(address) { d -> d.copy(displayName = name) } }
        persistKnown()
    }

    // --- Commands ----------------------------------------------------------

    fun sendCommand(address: String, bytes: ByteArray) {
        connections[address]?.writeCommand(bytes)
    }

    /** Fan a command out to every READY board at once — the fleet's party trick. */
    fun broadcast(bytes: ByteArray) {
        _devices.value.forEach { (addr, d) ->
            if (d.state == ConnectionState.READY) connections[addr]?.writeCommand(bytes)
        }
    }

    fun refreshStatus(address: String) {
        connections[address]?.requestStatus()
    }

    // --- External transport (USB-serial board) -----------------------------

    /** Append a protocol line from a non-BLE transport (a USB-attached board) to the fleet log. */
    fun ingestLine(address: String, raw: String) {
        val msg = BiscuitMessage.parse(raw)
        _log.update { (it + LogEntry(address, msg, System.currentTimeMillis())).takeLast(500) }
    }

    /** Show/hide a USB-attached board in the fleet registry (it has no BLE GATT connection). */
    fun setUsbBoardPresent(present: Boolean, name: String?) {
        val addr = USB_BOARD_ADDR
        _devices.update { cur ->
            if (present) {
                val existing = cur[addr] ?: BiscuitDevice(addr, name ?: "Biscuit board (USB)")
                cur + (addr to existing.copy(
                    displayName = name ?: existing.displayName,
                    state = ConnectionState.READY,
                    inRange = true,
                    lastSeenMs = System.currentTimeMillis(),
                ))
            } else {
                cur - addr
            }
        }
    }

    // --- DeviceConnection.Listener ----------------------------------------

    override fun onStateChanged(address: String, state: ConnectionState, error: String?) {
        if (state == ConnectionState.DISCONNECTED || state == ConnectionState.FAILED) {
            connections.remove(address)
        }
        _devices.update { it.mutate(address) { d -> d.copy(state = state, lastError = error ?: d.lastError) } }
        if (state == ConnectionState.READY) persistKnown()
    }

    override fun onMessage(address: String, message: BiscuitMessage) {
        _log.update { (it + LogEntry(address, message, System.currentTimeMillis())).takeLast(500) }
    }

    /** Short display name for a board address (used when tagging fleet-wide results). */
    fun nameFor(address: String): String =
        _devices.value[address]?.displayName ?: shortName(address)

    override fun onStatus(address: String, status: DeviceStatus) {
        _devices.update { it.mutate(address) { d -> d.copy(status = status) } }
    }

    override fun onDeviceInfo(address: String, model: String?, firmware: String?) {
        _devices.update { it.mutate(address) { d -> d.copy(model = model ?: d.model, firmware = firmware ?: d.firmware) } }
        persistKnown()
    }

    // --- Persistence -------------------------------------------------------

    private fun markKnown(address: String) {
        _devices.update { it.mutate(address) { d -> if (d.known) d else d.copy(known = true) } }
        persistKnown()
    }

    private fun persistKnown() {
        val known = _devices.value.values
            .filter { it.known }
            .map { KnownDevice(it.address, it.displayName) }
        scope.launch { knownStore.save(known) }
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

    companion object {
        /** Registry key for a board attached over USB-serial rather than BLE. */
        const val USB_BOARD_ADDR = "usb:biscuit"
    }
}
