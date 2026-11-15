package com.cak3d.biscuitmesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import com.cak3d.biscuitmesh.data.KnownDevice
import com.cak3d.biscuitmesh.data.KnownDevicesStore
import com.cak3d.biscuitmesh.model.BiscuitDevice
import com.cak3d.biscuitmesh.model.BoardHealth
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

    // Auto-reconnect/relink: a board can drop its BLE link (supervision timeout while a
    // scan contends for the shared radio). We treat an UNEXPECTED drop (not a user
    // disconnect) as recoverable — reconnect with backoff, then re-send the link code the
    // user already entered so they don't have to re-link on every blip (the code is still
    // required; we just remember it for the session).
    private val userClosing = java.util.Collections.synchronizedSet(HashSet<String>())
    private val reconnectTries = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val lastLinkCmd = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    private val lastRelinkMs = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val _devices = MutableStateFlow<Map<String, BiscuitDevice>>(emptyMap())
    val devices: StateFlow<Map<String, BiscuitDevice>> = _devices.asStateFlow()

    /** Rolling, timestamped log of protocol lines across the whole fleet. */
    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    /** Wipe the protocol log (the Debug console's clear button). */
    fun clearLog() { _log.value = emptyList() }

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
        userClosing.remove(address)
        if (connections.containsKey(address)) return true
        if (liveCount >= maxConnections) return false
        val remote = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return false
        val conn = DeviceConnection(context, remote, this)
        connections[address] = conn
        conn.connect()
        return true
    }

    fun disconnect(address: String) {
        userClosing.add(address)          // a deliberate disconnect — don't auto-reconnect it
        reconnectTries.remove(address)
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
        connections.keys.toList().forEach { userClosing.add(it); connections[it]?.disconnect() }
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
        // Remember a link command verbatim so we can re-send it automatically if the board
        // drops and comes back asking to be linked again (same session only).
        runCatching { String(bytes) }.getOrNull()?.let { s ->
            if (Regex("link:\\d{4,6}").containsMatchIn(s)) lastLinkCmd[address] = bytes
        }
        connections[address]?.writeCommand(bytes)
    }

    private fun scheduleReconnect(address: String) {
        val tries = reconnectTries.getOrDefault(address, 0)
        if (tries >= 6) { reconnectTries.remove(address); return }   // give up after ~6 attempts
        reconnectTries[address] = tries + 1
        scope.launch {
            kotlinx.coroutines.delay(1500L + tries * 1500L)          // backoff 1.5 s .. ~9 s
            val d = _devices.value[address]
            if (d != null && d.known && d.state != ConnectionState.READY &&
                address !in userClosing && !connections.containsKey(address)
            ) {
                connect(address)
            }
        }
    }

    /** When a board (re)connects and asks to be linked, re-send the code the user already
     *  entered this session — so a transient drop doesn't force a manual re-link. */
    private fun maybeAutoRelink(address: String, message: BiscuitMessage) {
        if (message.type != BiscuitMessage.Type.DATA || message.subtype != "LINK") return
        if (!message.payload.startsWith("need")) return
        val cmd = lastLinkCmd[address] ?: return
        val now = System.currentTimeMillis()
        if (now - (lastRelinkMs[address] ?: 0L) < 3000L) return      // cooldown — don't hammer
        lastRelinkMs[address] = now
        scope.launch {
            kotlinx.coroutines.delay(500)                            // let the board settle
            connections[address]?.writeCommand(cmd)
        }
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
        applyHealth(address, msg)
    }

    /** Fold a `DATA:HEALTH` heartbeat into the board's live telemetry, if present. */
    private fun applyHealth(address: String, message: BiscuitMessage) {
        if (message.type != BiscuitMessage.Type.DATA || message.subtype != "HEALTH") return
        val h = BoardHealth.parse(message.payload, System.currentTimeMillis()) ?: return
        _devices.update { it.mutate(address) { d -> d.copy(health = h, lastSeenMs = System.currentTimeMillis()) } }
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
            val deliberate = userClosing.remove(address)
            if (!deliberate) scheduleReconnect(address)              // a blip, not a user action → come back
        }
        _devices.update { it.mutate(address) { d -> d.copy(state = state, lastError = error ?: d.lastError) } }
        if (state == ConnectionState.READY) { reconnectTries.remove(address); persistKnown() }
    }

    override fun onMessage(address: String, message: BiscuitMessage) {
        _log.update { (it + LogEntry(address, message, System.currentTimeMillis())).takeLast(500) }
        applyHealth(address, message)
        maybeAutoRelink(address, message)
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
