package com.cak3d.biscuitmesh.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Source id used for the plugged-in What the Flock dongle in sightings and toggles. */
const val WTF_SOURCE = "wtf"

/** One camera sighting reported by the dongle (live over USB, or from its saved session). */
data class WtfDetection(
    val mac: String,
    val tier: Int,
    val method: String,
    val lat: Double?,
    val lon: Double?,
    val rssi: Int,
    val ssid: String,
    val name: String,
    val protocol: String,
    val channel: Int?,
    /** True for a live `detection`; false for a replayed `session_det` from the board's store. */
    val live: Boolean,
)

/** The dongle's GPS state, from its board-info report. */
data class WtfGps(val present: Boolean, val fix: Boolean, val sats: Int)

/** Connection state of the dongle link, surfaced to the UI. */
enum class WtfLinkState { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * Talks to a What the Flock XIAO ESP32-S3 dongle over USB-OTG as a CDC-ACM serial
 * device — the same wire protocol the What the Flock web app drives over WebUSB.
 *
 * It opens the CDC data interface, sets 115200 8N1 with DTR asserted (RTS low, so
 * the S3 isn't held in reset), then reads newline-delimited JSON events. `detection`
 * and `session_det` lines become [WtfDetection]s on [detections]; everything else
 * (config, session boundaries) is handled quietly. Callers turn detections into map
 * hazards; this class owns only the link.
 */
class WtfSerialSource(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val usb = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _link = MutableStateFlow(WtfLinkState.DISCONNECTED)
    val link: StateFlow<WtfLinkState> = _link.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    /** Dongle GPS status, once the board has reported it. null until queried. */
    private val _gps = MutableStateFlow<WtfGps?>(null)
    val gps: StateFlow<WtfGps?> = _gps.asStateFlow()

    // Replay buffer 0: a late collector shouldn't re-see old sightings; extra 64 so a
    // burst of session_det lines during a dump isn't dropped before ingestion.
    private val _detections = MutableSharedFlow<WtfDetection>(replay = 0, extraBufferCapacity = 64)
    val detections: SharedFlow<WtfDetection> = _detections.asSharedFlow()

    private var connection: UsbDeviceConnection? = null
    private var dataIntf: UsbInterface? = null
    private var ctrlIntf: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null
    private var readJob: Job? = null

    private var permReceiver: BroadcastReceiver? = null

    /** Connect to [device], or find the first Espressif dongle if null. Safe to call twice. */
    fun connect(device: UsbDevice? = null) {
        if (_link.value != WtfLinkState.DISCONNECTED) return
        val mgr = usb ?: return
        val dev = device ?: mgr.deviceList.values.firstOrNull { it.vendorId == ESPRESSIF_VID } ?: return
        if (mgr.hasPermission(dev)) {
            openAndRead(dev)
        } else {
            requestPermission(mgr, dev)
        }
    }

    private fun requestPermission(mgr: UsbManager, dev: UsbDevice) {
        _link.value = WtfLinkState.CONNECTING
        val filter = IntentFilter(ACTION_PERM)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                if (i?.action != ACTION_PERM) return
                unregisterPerm()
                val granted = i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (granted) openAndRead(dev) else _link.value = WtfLinkState.DISCONNECTED
            }
        }
        permReceiver = receiver
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_MUTABLE else 0
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }
            val pi = PendingIntent.getBroadcast(appContext, 0, Intent(ACTION_PERM).setPackage(appContext.packageName), flags)
            mgr.requestPermission(dev, pi)
        }.onFailure { _link.value = WtfLinkState.DISCONNECTED }
    }

    private fun unregisterPerm() {
        permReceiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        permReceiver = null
    }

    private fun openAndRead(dev: UsbDevice) {
        val mgr = usb ?: return
        _link.value = WtfLinkState.CONNECTING
        val conn = runCatching { mgr.openDevice(dev) }.getOrNull()
        if (conn == null) { _link.value = WtfLinkState.DISCONNECTED; return }

        // Find the CDC data interface (class 0x0A) for the bulk endpoints, and the
        // communications/control interface (class 0x02) for the line-coding transfers.
        var data: UsbInterface? = null
        var ctrl: UsbInterface? = null
        for (i in 0 until dev.interfaceCount) {
            val intf = dev.getInterface(i)
            when (intf.interfaceClass) {
                UsbConstants.USB_CLASS_CDC_DATA -> if (data == null) data = intf       // 0x0A
                UsbConstants.USB_CLASS_COMM -> if (ctrl == null) ctrl = intf           // 0x02
            }
        }
        if (data == null) { conn.close(); _link.value = WtfLinkState.DISCONNECTED; return }

        var inEp: UsbEndpoint? = null
        var outEp: UsbEndpoint? = null
        for (e in 0 until data.endpointCount) {
            val ep = data.getEndpoint(e)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep else outEp = ep
            }
        }
        if (inEp == null) { conn.close(); _link.value = WtfLinkState.DISCONNECTED; return }

        ctrl?.let { conn.claimInterface(it, true) }
        conn.claimInterface(data, true)

        // CDC SET_LINE_CODING: 115200 baud, 1 stop bit, no parity, 8 data bits.
        val index = (ctrl ?: data).id
        val lineCoding = byteArrayOf(0x00, 0xC2.toByte(), 0x01, 0x00, 0x00, 0x00, 0x08) // 115200 LE, 0, 0, 8
        conn.controlTransfer(REQ_TYPE_OUT, SET_LINE_CODING, 0, index, lineCoding, lineCoding.size, CTRL_TIMEOUT)
        // SET_CONTROL_LINE_STATE: DTR on (0x01), RTS off. Other combos drive S3 reset/boot.
        conn.controlTransfer(REQ_TYPE_OUT, SET_CONTROL_LINE_STATE, 0x01, index, null, 0, CTRL_TIMEOUT)

        connection = conn
        dataIntf = data
        ctrlIntf = ctrl
        epIn = inEp
        epOut = outEp
        _deviceName.value = runCatching { dev.productName }.getOrNull()?.takeIf { it.isNotBlank() } ?: "What the Flock dongle"
        _link.value = WtfLinkState.CONNECTED
        android.util.Log.d(TAG, "connected ${_deviceName.value} dataIf=${data.id} in=${inEp.endpointNumber} out=${outEp?.endpointNumber}")

        // Ask for its current beep config + board status (GPS), then (shortly after)
        // replay anything it caught while unplugged — the WTF app's connect handshake.
        send(JSONObject().put("cmd", "get_config"))
        send(JSONObject().put("cmd", "sd_info"))
        startReadLoop(inEp)
    }

    private fun startReadLoop(inEp: UsbEndpoint) {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val buf = ByteArray(512)
            val line = StringBuilder()
            var dumpedOnce = false
            while (isActive && _link.value == WtfLinkState.CONNECTED) {
                val n = runCatching { conn.bulkTransfer(inEp, buf, buf.size, READ_TIMEOUT) }.getOrDefault(-1)
                if (n < 0) {
                    // Timeout is normal when idle; only a real error ends the loop. bulkTransfer
                    // can't distinguish, so treat a long quiet spell as still-connected and retry.
                    if (!isActive || _link.value != WtfLinkState.CONNECTED) break
                    if (!dumpedOnce) { dumpedOnce = true; requestDump() }
                    continue
                }
                if (n == 0) continue
                val text = String(buf, 0, n, Charsets.UTF_8)
                for (ch in text) {
                    if (ch == '\n') {
                        val l = line.toString().trimEnd('\r')
                        line.setLength(0)
                        if (l.isNotEmpty()) handleLine(l)
                    } else if (line.length < 8192) {
                        line.append(ch)
                    } else {
                        line.setLength(0) // runaway line without a newline; drop it
                    }
                }
            }
        }
    }

    private fun handleLine(l: String) {
        if (l.firstOrNull() != '{') return
        val ev = runCatching { JSONObject(l) }.getOrNull() ?: return
        when (ev.optString("event")) {
            "detection" -> emit(ev, live = true)
            "session_det" -> emit(ev, live = false)
            "sd_det" -> emit(ev, live = false)   // a line of the board's microSD detection log
            "sd_info" -> {                        // board status, incl. GPS module/fix/sats
                if (ev.has("gps") || ev.has("gps_fix") || ev.has("sats")) {
                    val g = WtfGps(ev.optInt("gps") == 1, ev.optInt("gps_fix") == 1, ev.optInt("sats"))
                    _gps.value = g
                    android.util.Log.d(TAG, "GPS present=${g.present} fix=${g.fix} sats=${g.sats}")
                }
            }
            // session_begin/end, sd_end, config, *_error carry no sighting.
            else -> Unit
        }
    }

    private fun emit(ev: JSONObject, live: Boolean) {
        val mac = (ev.optString("mac_address").ifEmpty { ev.optString("mac") }).lowercase()
        if (mac.isEmpty()) return
        val det = WtfDetection(
            mac = mac,
            tier = ev.optInt("detection_tier", ev.optInt("tier", 0)),
            method = ev.optString("detection_method", ev.optString("method", "unknown")),
            lat = if (ev.has("lat") && !ev.isNull("lat")) ev.optDouble("lat") else null,
            lon = if (ev.has("lon") && !ev.isNull("lon")) ev.optDouble("lon") else null,
            rssi = ev.optInt("rssi", -127),
            ssid = ev.optString("ssid", ""),
            name = ev.optString("device_name", ""),
            protocol = ev.optString("protocol", ""),
            channel = if (ev.has("channel")) ev.optInt("channel") else if (ev.has("ch")) ev.optInt("ch") else null,
            live = live,
        )
        android.util.Log.d(TAG, "sighting mac=${det.mac} tier=${det.tier} loc=${det.lat},${det.lon} live=$live")
        _detections.tryEmit(det)
    }

    /** Pull the sightings the dongle saved while it was unplugged. */
    fun requestDump() {
        send(JSONObject().put("cmd", "dump_session").put("source", "prev"))
    }

    /** Load the board's full microSD detection log (XIAO Sense) onto the map. */
    fun requestSdDump() {
        send(JSONObject().put("cmd", "sd_dump").put("from", 0).put("max", 1000))
    }

    /** Re-query the board's status (including GPS module presence / fix / satellites). */
    fun requestGpsStatus() {
        send(JSONObject().put("cmd", "sd_info"))
    }

    private fun send(obj: JSONObject) {
        val conn = connection ?: return
        val out = epOut ?: return
        val bytes = (obj.toString() + "\n").toByteArray(Charsets.UTF_8)
        scope.launch(Dispatchers.IO) {
            runCatching { conn.bulkTransfer(out, bytes, bytes.size, CTRL_TIMEOUT) }
        }
    }

    fun disconnect() {
        _link.value = WtfLinkState.DISCONNECTED
        readJob?.cancel(); readJob = null
        unregisterPerm()
        val conn = connection
        ctrlIntf?.let { runCatching { conn?.releaseInterface(it) } }
        dataIntf?.let { runCatching { conn?.releaseInterface(it) } }
        runCatching { conn?.close() }
        connection = null; dataIntf = null; ctrlIntf = null; epIn = null; epOut = null
        _deviceName.value = null
    }

    private companion object {
        const val TAG = "WtfSerial"
        const val ESPRESSIF_VID = 0x303A
        const val ACTION_PERM = "com.cak3d.biscuitmesh.USB_PERMISSION"
        // bmRequestType = host-to-device | class | interface
        const val REQ_TYPE_OUT = UsbConstants.USB_DIR_OUT or 0x20 or 0x01
        const val SET_LINE_CODING = 0x20
        const val SET_CONTROL_LINE_STATE = 0x22
        const val CTRL_TIMEOUT = 400
        const val READ_TIMEOUT = 300
    }
}
