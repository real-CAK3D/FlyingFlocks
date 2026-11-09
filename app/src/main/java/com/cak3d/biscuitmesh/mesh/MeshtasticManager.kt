package com.cak3d.biscuitmesh.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** One node in the Meshtastic mesh, as read from the connected node's NodeDB. */
data class MeshNode(
    val num: Long,
    val name: String,
    val lat: Double?,
    val lon: Double?,
    val battery: Int? = null,
)

enum class MeshLinkState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

/** A mesh text message — received from the mesh, or one you sent. */
data class MeshMessage(val text: String, val mine: Boolean, val timeMs: Long)

/**
 * Connects to a Meshtastic node over BLE and reads its mesh NodeDB so the whole mesh
 * (your nodes and anyone you can hear) can be shown on the Flying Flocks map.
 *
 * Meshtastic's BLE API is protobuf, not the Biscuits' plain text: you enable notify
 * on `fromNum`, write a `want_config_id` ToRadio to make the node dump its config +
 * node list, then drain `fromRadio` (each read = one FromRadio packet; an empty read
 * means "nothing more for now"). We hand-parse only the NodeInfo fields we need.
 *
 * Note: Meshtastic allows a single BLE client, so the official app must release the
 * node first, and the first connect may require BLE bonding with the node's PIN.
 */
@SuppressLint("MissingPermission")
class MeshtasticManager(private val context: Context, private val adapter: BluetoothAdapter?) {

    private val _link = MutableStateFlow(MeshLinkState.DISCONNECTED)
    val link: StateFlow<MeshLinkState> = _link.asStateFlow()

    private val _messages = MutableStateFlow<List<MeshMessage>>(emptyList())
    val messages: StateFlow<List<MeshMessage>> = _messages.asStateFlow()
    private val _nodes = MutableStateFlow<Map<Long, MeshNode>>(emptyMap())
    /** Live mesh node database, keyed by node number. */
    val nodes: StateFlow<Map<Long, MeshNode>> = _nodes.asStateFlow()

    // A human-readable account of what the last connect attempt did, so the chat screen
    // can explain a no-connect instead of just sitting on "Not connected".
    private val _diag = MutableStateFlow<String?>(null)
    val diag: StateFlow<String?> = _diag.asStateFlow()

    private fun failScan(msg: String) {
        _diag.value = msg
        stopScan()
        cleanup()
        _link.value = MeshLinkState.DISCONNECTED
    }

    // The connected (local) node's number, from MyNodeInfo — so we can read OUR node's GPS
    // out of the NodeDB and use it as the phone's location when board-GPS mode is on.
    @Volatile private var myNodeNum: Long = 0L

    /** OUR node's current GPS fix, or null (not connected / no my_info / node has no position). */
    fun myGpsFix(): Pair<Double, Double>? {
        if (_link.value != MeshLinkState.CONNECTED) return null
        val n = _nodes.value[myNodeNum] ?: return null
        val la = n.lat; val lo = n.lon
        return if (la != null && lo != null) la to lo else null
    }

    private var gatt: BluetoothGatt? = null
    private var toRadio: BluetoothGattCharacteristic? = null
    private var fromRadio: BluetoothGattCharacteristic? = null
    private val opQueue = ArrayDeque<() -> Unit>()
    private val opRunning = AtomicBoolean(false)

    private var scanning = false
    private val scanner get() = adapter?.bluetoothLeScanner
    // A low-latency scan is power-hungry; stop it if no node turns up instead of running forever.
    private val scanWatchdog = Handler(Looper.getMainLooper())
    private val scanTimeout = Runnable {
        if (_link.value == MeshLinkState.SCANNING) failScan(
            "No Meshtastic node found nearby. Caw talks to a Meshtastic device (Heltec / T-Beam / RAK) " +
                "over BLE — check it's powered, in range, has Bluetooth enabled, and isn't already " +
                "connected in the official Meshtastic app (it allows only one BLE client). " +
                "Note: the wtBLEep board is not a Meshtastic node, so Caw can't connect to it.",
        )
    }
    // Collect candidates briefly, then connect to the strongest — so we pick the nearest
    // (usually Home Base) when several mesh nodes are in range.
    // Written on the BLE scanner thread, read on the main thread in connectBest() — so it
    // must be concurrency-safe or maxByOrNull races an insert into a ConcurrentModificationException.
    private val candidates = java.util.concurrent.ConcurrentHashMap<android.bluetooth.BluetoothDevice, Int>()
    private val picker = Runnable { connectBest() }
    // Fail the connect if it hasn't reached CONNECTED in time (these radios often emit a
    // spurious status 133 or stall before service discovery; without this the UI hangs on
    // "Connecting…" forever).
    private val connectTimeout = Runnable {
        if (_link.value == MeshLinkState.CONNECTING) {
            Log.w(TAG, "mesh connect timed out")
            failConnect("Couldn't connect to the node — timed out. Tap Caw again to retry.")
        }
    }
    private fun failConnect(msg: String) {
        scanWatchdog.removeCallbacks(connectTimeout)
        if (_link.value == MeshLinkState.DISCONNECTED) return
        _diag.value = msg
        runCatching { gatt?.disconnect() }
        cleanup()
        _link.value = MeshLinkState.DISCONNECTED
    }
    // Arm the picker ONCE, on the first candidate. A node beacons every ~60 ms, so
    // re-posting it on every advertisement (the old behaviour) reset the 2 s timer
    // forever and it never connected — it just scanned until the watchdog gave up.
    private var pickScheduled = false

    /** Scan for any Meshtastic node and connect to the strongest one in range. */
    fun scanAndConnect() {
        if (_link.value != MeshLinkState.DISCONNECTED) return
        val s = scanner ?: run {
            _diag.value = "No Bluetooth adapter available — Caw needs BLE."
            return
        }
        _diag.value = "Scanning for a Meshtastic node…"
        _link.value = MeshLinkState.SCANNING
        scanning = true
        candidates.clear()
        pickScheduled = false
        // NOTE: we deliberately scan UNFILTERED and match in code. Depending on the node's
        // firmware/hardware the 128-bit Meshtastic service UUID is carried in the scan
        // response (or lives only in the GATT table), so a hard ScanFilter.setServiceUuid
        // silently misses real nodes. We accept a device by advertised service UUID OR by a
        // "Meshtastic*" advertised name, which matches every stock node.
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { s.startScan(emptyList(), settings, scanCb) }
            .onSuccess { scanWatchdog.postDelayed(scanTimeout, 20_000) }
            .onFailure { failScan("Couldn't start a BLE scan — is Bluetooth on and the scan permission granted?") }
    }

    private fun looksLikeMesh(result: ScanResult): Boolean {
        val rec = result.scanRecord
        if (rec?.serviceUuids?.any { it.uuid == SERVICE } == true) return true
        val name = rec?.deviceName ?: return false
        return name.startsWith("Meshtastic", ignoreCase = true)
    }

    private fun connectBest() {
        val best = candidates.maxByOrNull { it.value }?.key ?: return
        stopScan()
        _diag.value = "Found ${candidates.size} node${if (candidates.size == 1) "" else "s"} — connecting to the strongest…"
        _link.value = MeshLinkState.CONNECTING
        Log.d(TAG, "connecting to mesh node ${best.address} (${candidates.size} candidate(s))")
        gatt = best.connectGatt(context, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
        scanWatchdog.postDelayed(connectTimeout, 15_000)
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            if (!looksLikeMesh(result)) return
            Log.d(TAG, "candidate mesh node ${dev.address} rssi=${result.rssi} name=${result.scanRecord?.deviceName}")
            candidates[dev] = result.rssi
            // Give nearby nodes ~2s to show up, then connect to the strongest — but arm
            // this ONCE, or a node beaconing every ~60 ms keeps pushing it out forever.
            if (!pickScheduled) {
                pickScheduled = true
                scanWatchdog.postDelayed(picker, 2_000)
            }
        }
        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "mesh scan failed $errorCode")
            failScan("BLE scan failed (error $errorCode). Try toggling Bluetooth off and on.")
        }
    }

    private fun stopScan() {
        scanWatchdog.removeCallbacks(scanTimeout)
        scanWatchdog.removeCallbacks(picker)
        pickScheduled = false
        if (scanning) { runCatching { scanner?.stopScan(scanCb) }; scanning = false }
    }

    fun disconnect() {
        stopScan()
        scanWatchdog.removeCallbacks(connectTimeout)
        runCatching { gatt?.disconnect() }
        cleanup()
        _link.value = MeshLinkState.DISCONNECTED
    }

    // --- GATT op queue (BLE ops must be serialized) ------------------------
    private fun enqueue(op: () -> Unit) { synchronized(opQueue) { opQueue.add(op) }; pump() }
    private fun pump() {
        if (opRunning.compareAndSet(false, true)) {
            val op = synchronized(opQueue) { opQueue.poll() }
            if (op == null) opRunning.set(false) else op()
        }
    }
    private fun opComplete() { opRunning.set(false); pump() }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED ->
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        failConnect("Link failed (status $status). Tap Caw again to retry.")
                    } else {
                        g.requestMtu(512)
                    }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    scanWatchdog.removeCallbacks(connectTimeout)
                    cleanup(); _link.value = MeshLinkState.DISCONNECTED
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) { g.discoverServices() }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(SERVICE)
            if (svc == null) {
                Log.w(TAG, "no mesh service")
                _diag.value = "Connected, but that device isn't a Meshtastic node (no mesh service)."
                disconnect(); return
            }
            toRadio = svc.getCharacteristic(TO_RADIO)
            fromRadio = svc.getCharacteristic(FROM_RADIO)
            val fromNum = svc.getCharacteristic(FROM_NUM)
            if (toRadio == null || fromRadio == null || fromNum == null) {
                Log.w(TAG, "mesh chars missing"); disconnect(); return
            }
            g.setCharacteristicNotification(fromNum, true)
            val cccd = fromNum.getDescriptor(CCCD)
            if (cccd == null) { Log.w(TAG, "mesh CCCD missing"); disconnect(); return }
            enqueue {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION") run { cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(cccd) }
                }
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            scanWatchdog.removeCallbacks(connectTimeout)
            _diag.value = null
            _link.value = MeshLinkState.CONNECTED
            opComplete()
            // Ask the node to stream its config + node database.
            writeToRadio(byteArrayOf(0x18, 0x2A))  // want_config_id = 42
            drain()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { opComplete() }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (c.uuid == FROM_NUM) drain()
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (c.uuid == FROM_NUM) drain()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION") onFromRadio(c.value, status); opComplete()
        }
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            onFromRadio(value, status); opComplete()
        }
    }

    private fun writeToRadio(bytes: ByteArray) {
        val g = gatt ?: return
        val c = toRadio ?: return
        enqueue {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION") run { c.value = bytes; c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; g.writeCharacteristic(c) }
            }
        }
    }

    /** Read one FromRadio packet; onCharacteristicRead schedules the next. */
    private fun drain() {
        val g = gatt ?: return
        val c = fromRadio ?: return
        enqueue { g.readCharacteristic(c) }
    }

    private fun onFromRadio(value: ByteArray?, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS || value == null || value.isEmpty()) return  // empty = no more for now
        Log.d(TAG, "fromRadio ${value.size}B: ${value.joinToString("") { "%02x".format(it) }.take(160)}")
        runCatching { parseFromRadio(value) }.onFailure { Log.w(TAG, "parse failed: ${it.message}") }
        drain()  // keep reading until an empty packet
    }

    // --- Minimal protobuf decode (only the NodeInfo fields we plot) --------

    private fun parseFromRadio(buf: ByteArray) {
        val r = Pb(buf)
        while (r.has()) {
            val (field, wire) = r.tag()
            when {
                field == 3 && wire == 2 -> parseMyInfo(r.lenBytes())    // my_info (MyNodeInfo)
                field == 4 && wire == 2 -> parseNodeInfo(r.lenBytes())  // node_info
                field == 2 && wire == 2 -> parsePacket(r.lenBytes())    // packet (MeshPacket) — FromRadio.packet is field 2
                else -> r.skip(wire)
            }
        }
    }

    // A live MeshPacket: carries the sender (from) and a decoded Data payload. We handle
    // TEXT_MESSAGE_APP (Caw chat) and POSITION_APP (so the moving node's pin tracks live,
    // not just when a full NodeInfo is rebroadcast).
    private fun parsePacket(buf: ByteArray) {
        val r = Pb(buf)
        var from = 0L
        var data: ByteArray? = null
        while (r.has()) {
            val (field, wire) = r.tag()
            when {
                field == 1 && wire == 5 -> from = r.fixed32()      // from (node num, fixed32)
                field == 4 && wire == 2 -> data = r.lenBytes()     // decoded (Data)
                else -> r.skip(wire)
            }
        }
        data?.let { handleData(from, it) }
    }

    private fun handleData(from: Long, buf: ByteArray) {
        val r = Pb(buf)
        var portnum = -1L; var payload = ByteArray(0)
        while (r.has()) {
            val (field, wire) = r.tag()
            when {
                field == 1 && wire == 0 -> portnum = r.varint()  // portnum
                field == 2 && wire == 2 -> payload = r.lenBytes() // payload
                else -> r.skip(wire)
            }
        }
        when (portnum) {
            1L -> { // TEXT_MESSAGE_APP
                val t = if (payload.isNotEmpty()) String(payload, Charsets.UTF_8) else ""
                if (t.isNotBlank()) {
                    _messages.value = (_messages.value + MeshMessage(t, false, System.currentTimeMillis())).takeLast(100)
                }
            }
            3L -> { // POSITION_APP — live position update for node `from`
                if (from != 0L && payload.isNotEmpty()) {
                    val (lat, lon) = parsePosition(payload)
                    if (lat != null && lon != null) {
                        val existing = _nodes.value[from]
                        val node = existing?.copy(lat = lat, lon = lon)
                            ?: MeshNode(from, "!%08x".format(from), lat, lon, null)
                        _nodes.value = _nodes.value + (from to node)
                    }
                }
            }
        }
    }

    /** Send a broadcast text message over the mesh. */
    fun sendText(text: String) {
        val t = text.trim()
        if (t.isEmpty() || _link.value != MeshLinkState.CONNECTED) return
        val payload = t.toByteArray(Charsets.UTF_8)
        // Data { portnum=1 (TEXT_MESSAGE_APP), payload }
        val data = java.io.ByteArrayOutputStream().apply {
            pbVarint(this, 1, 1)            // portnum
            pbBytes(this, 2, payload)       // payload
        }.toByteArray()
        // MeshPacket { to=0xffffffff (broadcast), decoded=data }
        val packet = java.io.ByteArrayOutputStream().apply {
            pbFixed32(this, 2, 0xFFFFFFFFL) // to = broadcast
            pbBytes(this, 4, data)          // decoded
        }.toByteArray()
        // ToRadio { packet }
        val toRadioMsg = java.io.ByteArrayOutputStream().apply { pbBytes(this, 1, packet) }.toByteArray()
        writeToRadio(toRadioMsg)
        _messages.value = (_messages.value + MeshMessage(t, true, System.currentTimeMillis())).takeLast(100)
    }

    private fun pbTag(o: java.io.ByteArrayOutputStream, field: Int, wire: Int) = pbRawVarint(o, ((field shl 3) or wire).toLong())
    private fun pbRawVarint(o: java.io.ByteArrayOutputStream, value: Long) {
        var x = value
        while (true) { val b = (x and 0x7F); x = x ushr 7; if (x != 0L) o.write((b or 0x80).toInt()) else { o.write(b.toInt()); break } }
    }
    private fun pbVarint(o: java.io.ByteArrayOutputStream, field: Int, value: Long) { pbTag(o, field, 0); pbRawVarint(o, value) }
    private fun pbBytes(o: java.io.ByteArrayOutputStream, field: Int, bytes: ByteArray) { pbTag(o, field, 2); pbRawVarint(o, bytes.size.toLong()); o.write(bytes) }
    private fun pbFixed32(o: java.io.ByteArrayOutputStream, field: Int, value: Long) {
        pbTag(o, field, 5)
        o.write((value and 0xFF).toInt()); o.write(((value ushr 8) and 0xFF).toInt())
        o.write(((value ushr 16) and 0xFF).toInt()); o.write(((value ushr 24) and 0xFF).toInt())
    }

    // MyNodeInfo { my_node_num = 1 } — identifies which NodeDB entry is us.
    private fun parseMyInfo(buf: ByteArray) {
        val r = Pb(buf)
        while (r.has()) {
            val (field, wire) = r.tag()
            when {
                field == 1 && wire == 0 -> myNodeNum = r.varint()
                else -> r.skip(wire)
            }
        }
    }

    private fun parseNodeInfo(buf: ByteArray) {
        val r = Pb(buf)
        var num = 0L; var name = ""; var lat: Double? = null; var lon: Double? = null; var batt: Int? = null
        while (r.has()) {
            val (field, wire) = r.tag()
            when {
                field == 1 && wire == 0 -> num = r.varint()                 // num
                field == 2 && wire == 2 -> name = parseUserName(r.lenBytes()) // user
                field == 3 && wire == 2 -> {                               // position
                    val p = parsePosition(r.lenBytes()); lat = p.first; lon = p.second
                }
                field == 6 && wire == 2 -> batt = parseBattery(r.lenBytes()) // device_metrics
                else -> r.skip(wire)
            }
        }
        if (num != 0L) {
            val node = MeshNode(num, name.ifBlank { "!%08x".format(num) }, lat, lon, batt)
            _nodes.value = _nodes.value + (num to node)
            Log.d(TAG, "node $node")
        }
    }

    private fun parseUserName(buf: ByteArray): String {
        val r = Pb(buf); var long = ""; var short = ""
        while (r.has()) {
            val (field, wire) = r.tag()
            when {
                field == 2 && wire == 2 -> long = String(r.lenBytes(), Charsets.UTF_8)
                field == 3 && wire == 2 -> short = String(r.lenBytes(), Charsets.UTF_8)
                else -> r.skip(wire)
            }
        }
        return long.ifBlank { short }
    }

    /** Returns lat, lon in degrees, handling both sfixed32 and varint encodings. */
    private fun parsePosition(buf: ByteArray): Pair<Double?, Double?> {
        val r = Pb(buf); var lat: Double? = null; var lon: Double? = null
        while (r.has()) {
            val (field, wire) = r.tag()
            when {
                field == 1 -> lat = r.scaledInt(wire)
                field == 2 -> lon = r.scaledInt(wire)
                else -> r.skip(wire)
            }
        }
        return lat to lon
    }

    private fun parseBattery(buf: ByteArray): Int? {
        val r = Pb(buf); var b: Int? = null
        while (r.has()) {
            val (field, wire) = r.tag()
            when {
                field == 1 && wire == 0 -> b = r.varint().toInt()  // battery_level
                else -> r.skip(wire)
            }
        }
        return b
    }

    private fun cleanup() {
        synchronized(opQueue) { opQueue.clear() }
        opRunning.set(false)
        toRadio = null; fromRadio = null
        runCatching { gatt?.close() }
        gatt = null
    }

    /** Tiny protobuf field reader over a byte array. */
    private class Pb(val b: ByteArray) {
        var i = 0
        fun has() = i < b.size
        fun tag(): Pair<Int, Int> { val t = varint().toInt(); return (t ushr 3) to (t and 7) }
        fun varint(): Long {
            var result = 0L; var shift = 0
            while (i < b.size) {
                val x = b[i++].toInt() and 0xFF
                result = result or ((x and 0x7F).toLong() shl shift)
                if (x < 0x80) break
                shift += 7
            }
            return result
        }
        fun lenBytes(): ByteArray { val n = varint().toInt(); val out = b.copyOfRange(i, minOf(i + n, b.size)); i += n; return out }
        /** Little-endian unsigned 32-bit (wire type 5), as a Long so node numbers don't go negative. */
        fun fixed32(): Long {
            val v = (b[i].toLong() and 0xFF) or ((b[i + 1].toLong() and 0xFF) shl 8) or
                ((b[i + 2].toLong() and 0xFF) shl 16) or ((b[i + 3].toLong() and 0xFF) shl 24)
            i += 4; return v
        }
        /** A lat/lon field: sfixed32 (wire 5) or varint int32 (wire 0), scaled by 1e-7. */
        fun scaledInt(wire: Int): Double? = when (wire) {
            5 -> { val v = (b[i].toInt() and 0xFF) or ((b[i+1].toInt() and 0xFF) shl 8) or
                ((b[i+2].toInt() and 0xFF) shl 16) or ((b[i+3].toInt() and 0xFF) shl 24); i += 4; v / 1e7 }
            0 -> varint().toInt() / 1e7
            else -> { skip(wire); null }
        }
        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> i += 8
                2 -> { val n = varint().toInt(); i += n }
                5 -> i += 4
                else -> i = b.size
            }
        }
    }

    companion object {
        private const val TAG = "MeshBLE"
        val SERVICE: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
        val TO_RADIO: UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        val FROM_RADIO: UUID = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")
        val FROM_NUM: UUID = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
