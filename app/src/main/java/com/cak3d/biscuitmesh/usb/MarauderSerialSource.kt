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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Source id for an ESP Marauder plugged into the phone, used in sightings and toggles. */
const val MARAUDER_SOURCE = "marauder"

/** Kind of radio sighting parsed off the Marauder's serial stream. */
enum class MarauderKind { WIFI, BT }

/**
 * One device the Marauder reported over serial. It carries no location — a Marauder on a
 * bare dev-kit has no GPS, so the app geo-tags each hit with the phone's current fix, the
 * same way it treats the phone's own Wi-Fi/BT scans.
 */
data class MarauderHit(
    val kind: MarauderKind,
    val mac: String,
    val label: String,
    val rssi: Int,
    val channel: Int,
)

/**
 * Drives an ESP Marauder over USB-OTG and turns its scan output into map sightings.
 *
 * Unlike the Biscuit / What the Flock dongles (native Espressif USB-CDC, VID 0x303a), a
 * Marauder on a classic ESP32 dev-kit speaks through a **USB-serial bridge chip** — a
 * CP210x (Silicon Labs) or a CH340 (WCH) — which needs chip-specific control transfers to
 * reach 115200 8N1, not the CDC SET_LINE_CODING the other sources use. This class handles
 * all three (native CDC, CP210x, CH340), opens the link, asks the Marauder to `scanall`,
 * and parses the human-readable lines it prints.
 *
 * The Marauder's serial output is NOT a stable machine format and varies by firmware build,
 * so [parseLine] is deliberately tolerant: it pulls a MAC, RSSI, channel and SSID out of
 * whatever shape the line takes (inline `RSSI: .. Ch: .. BSSID: .. ESSID: ..`, a WiGLE-style
 * CSV row, or a bare MAC), and quietly drops anything without a MAC.
 */
class MarauderSerialSource(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val usb = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _link = MutableStateFlow(WtfLinkState.DISCONNECTED)
    val link: StateFlow<WtfLinkState> = _link.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _hits = MutableSharedFlow<MarauderHit>(replay = 0, extraBufferCapacity = 128)
    val hits: SharedFlow<MarauderHit> = _hits.asSharedFlow()

    private var connection: UsbDeviceConnection? = null
    private val claimed = mutableListOf<UsbInterface>()
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null
    private var readJob: Job? = null
    private var permReceiver: BroadcastReceiver? = null
    /** CDC devices take line-state control transfers; bridges don't. */
    private var cdcCtrlIndex: Int = -1

    /** True when a plugged-in USB device looks like something we can drive as a Marauder. */
    fun candidatePresent(): Boolean = findCandidate() != null

    private fun findCandidate(): UsbDevice? {
        val mgr = usb ?: return null
        return mgr.deviceList.values.firstOrNull { d ->
            d.vendorId in BRIDGE_VIDS || d.vendorId == ESPRESSIF_VID || hasCdc(d)
        }
    }

    private fun hasCdc(d: UsbDevice): Boolean {
        for (i in 0 until d.interfaceCount) {
            if (d.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) return true
        }
        return false
    }

    /** Connect to [device], or the first likely Marauder if null. Safe to call twice. */
    fun connect(device: UsbDevice? = null) {
        if (_link.value != WtfLinkState.DISCONNECTED) return
        val mgr = usb ?: return
        val dev = device ?: findCandidate() ?: return
        if (mgr.hasPermission(dev)) openAndRead(dev) else requestPermission(mgr, dev)
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
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }
            val pi = PendingIntent.getBroadcast(appContext, 3, Intent(ACTION_PERM).setPackage(appContext.packageName), flags)
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

        // Claim every interface that carries bulk endpoints, and remember the first bulk
        // IN/OUT pair. This covers both a CDC data interface and a bridge's single vendor
        // interface. Also note a CDC comms interface, for the line-coding transfers.
        var inEp: UsbEndpoint? = null
        var outEp: UsbEndpoint? = null
        var cdcComm: UsbInterface? = null
        var cdcData: UsbInterface? = null
        claimed.clear()
        for (i in 0 until dev.interfaceCount) {
            val intf = dev.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_COMM && cdcComm == null) cdcComm = intf
            if (intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA && cdcData == null) cdcData = intf
            var hasBulk = false
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    hasBulk = true
                    if (ep.direction == UsbConstants.USB_DIR_IN && inEp == null) inEp = ep
                    if (ep.direction == UsbConstants.USB_DIR_OUT && outEp == null) outEp = ep
                }
            }
            if (hasBulk || intf.interfaceClass == UsbConstants.USB_CLASS_COMM) {
                if (conn.claimInterface(intf, true)) claimed.add(intf)
            }
        }
        if (inEp == null) { fail(conn); return }

        val ok = runCatching {
            when {
                dev.vendorId == CP210X_VID -> initCp210x(conn)
                dev.vendorId == CH340_VID -> initCh340(conn)
                else -> initCdc(conn, cdcComm, cdcData)   // native Espressif USB, or any CDC device
            }
        }.getOrDefault(false)
        if (!ok) { fail(conn); return }

        connection = conn
        epIn = inEp
        epOut = outEp
        _deviceName.value = runCatching { dev.productName }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: bridgeName(dev.vendorId)
        _link.value = WtfLinkState.CONNECTED
        android.util.Log.d(TAG, "connected ${_deviceName.value} vid=${dev.vendorId} in=${inEp.endpointNumber} out=${outEp?.endpointNumber}")

        startReadLoop(inEp)
        // Clear any scan the Marauder was already running, then start collecting APs + stations.
        scope.launch {
            delay(150); sendCmd("stopscan")
            delay(250); sendCmd("scanall")
        }
    }

    private fun fail(conn: UsbDeviceConnection) {
        claimed.forEach { runCatching { conn.releaseInterface(it) } }
        claimed.clear()
        runCatching { conn.close() }
        _link.value = WtfLinkState.DISCONNECTED
    }

    // --- Chip init ---------------------------------------------------------------

    /** Native USB-CDC (Espressif 0x303a, or any CDC device): 115200 8N1, DTR asserted. */
    private fun initCdc(conn: UsbDeviceConnection, comm: UsbInterface?, data: UsbInterface?): Boolean {
        val index = (comm ?: data)?.id ?: 0
        cdcCtrlIndex = index
        val lineCoding = byteArrayOf(0x00, 0xC2.toByte(), 0x01, 0x00, 0x00, 0x00, 0x08) // 115200 8N1
        conn.controlTransfer(CDC_OUT, CDC_SET_LINE_CODING, 0, index, lineCoding, lineCoding.size, CTRL_TIMEOUT)
        conn.controlTransfer(CDC_OUT, CDC_SET_CONTROL_LINE_STATE, 0x01, index, null, 0, CTRL_TIMEOUT)
        return true
    }

    /** CP210x (Silicon Labs) vendor init — constants per the CP210x datasheet / usb-serial-for-android. */
    private fun initCp210x(conn: UsbDeviceConnection): Boolean {
        val rt = CP_OUT // 0x41
        // IFC_ENABLE (enable UART)
        conn.controlTransfer(rt, 0x00, 0x0001, 0, null, 0, CTRL_TIMEOUT)
        // SET_BAUDRATE: 4-byte little-endian baud (115200 = 00 C2 01 00)
        val baud = byteArrayOf(0x00, 0xC2.toByte(), 0x01, 0x00)
        conn.controlTransfer(rt, 0x1E, 0, 0, baud, baud.size, CTRL_TIMEOUT)
        // SET_LINE_CTL: 8 data bits, no parity, 1 stop (0x0800)
        conn.controlTransfer(rt, 0x03, 0x0800, 0, null, 0, CTRL_TIMEOUT)
        // SET_MHS: DTR on (0x0101) | RTS on (0x0202) = 0x0303
        conn.controlTransfer(rt, 0x07, 0x0303, 0, null, 0, CTRL_TIMEOUT)
        return true
    }

    /** CH340 / CH341 (WCH) vendor init for 115200 8N1 with DTR+RTS asserted. */
    private fun initCh340(conn: UsbDeviceConnection): Boolean {
        val out = CH_OUT // 0x40
        val inn = CH_IN  // 0xC0
        val two = ByteArray(2)
        conn.controlTransfer(inn, 0x5F, 0x0000, 0x0000, two, 2, CTRL_TIMEOUT)
        conn.controlTransfer(out, 0xA1, 0x0000, 0x0000, null, 0, CTRL_TIMEOUT)
        // baud 115200: val1=0xCC83, val2=0x0A (factor 0xCC0A, divisor 3)
        conn.controlTransfer(out, 0x9A, 0x1312, 0xCC83, null, 0, CTRL_TIMEOUT)
        conn.controlTransfer(out, 0x9A, 0x0F2C, 0x000A, null, 0, CTRL_TIMEOUT)
        conn.controlTransfer(inn, 0x95, 0x2518, 0x0000, two, 2, CTRL_TIMEOUT)
        // LCR 8N1: RX|TX|CS8 = 0xC3
        conn.controlTransfer(out, 0x9A, 0x2518, 0x00C3, null, 0, CTRL_TIMEOUT)
        conn.controlTransfer(inn, 0x95, 0x0706, 0x0000, two, 2, CTRL_TIMEOUT)
        conn.controlTransfer(out, 0xA1, 0x501F, 0xD90A, null, 0, CTRL_TIMEOUT)
        // flow control: none
        conn.controlTransfer(out, 0x9A, 0x2727, 0x0000, null, 0, CTRL_TIMEOUT)
        // control lines: DTR+RTS asserted (inverted mask ~0x60 -> 0xFF9F on the wire)
        conn.controlTransfer(out, 0xA4, 0xFF9F, 0x0000, null, 0, CTRL_TIMEOUT)
        return true
    }

    private fun bridgeName(vid: Int): String = when (vid) {
        CP210X_VID -> "Marauder (CP210x)"
        CH340_VID -> "Marauder (CH340)"
        ESPRESSIF_VID -> "Marauder (USB)"
        else -> "ESP Marauder"
    }

    // --- Read / parse ------------------------------------------------------------

    private fun startReadLoop(inEp: UsbEndpoint) {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            val conn = connection ?: return@launch
            val buf = ByteArray(512)
            val line = StringBuilder()
            while (isActive && _link.value == WtfLinkState.CONNECTED) {
                val n = runCatching { conn.bulkTransfer(inEp, buf, buf.size, READ_TIMEOUT) }.getOrDefault(-1)
                if (n < 0) { if (!isActive || _link.value != WtfLinkState.CONNECTED) break else continue }
                if (n == 0) continue
                val text = String(buf, 0, n, Charsets.UTF_8)
                for (ch in text) {
                    if (ch == '\n') {
                        val l = line.toString().trimEnd('\r')
                        line.setLength(0)
                        if (l.isNotEmpty()) parseLine(l)?.let { _hits.tryEmit(it) }
                    } else if (line.length < 8192) {
                        line.append(ch)
                    } else {
                        line.setLength(0)
                    }
                }
            }
        }
    }

    /** Send a newline-terminated CLI command to the Marauder (e.g. "scanall", "stopscan"). */
    fun sendCmd(cmd: String) {
        val conn = connection ?: return
        val out = epOut ?: return
        val bytes = (cmd + "\r\n").toByteArray(Charsets.UTF_8)
        scope.launch(Dispatchers.IO) {
            runCatching { conn.bulkTransfer(out, bytes, bytes.size, CTRL_TIMEOUT) }
        }
    }

    fun disconnect() {
        if (_link.value == WtfLinkState.CONNECTED) runCatching { sendCmd("stopscan") }
        _link.value = WtfLinkState.DISCONNECTED
        readJob?.cancel(); readJob = null
        unregisterPerm()
        val conn = connection
        claimed.forEach { runCatching { conn?.releaseInterface(it) } }
        claimed.clear()
        runCatching { conn?.close() }
        connection = null; epIn = null; epOut = null; cdcCtrlIndex = -1
        _deviceName.value = null
    }

    companion object {
        private const val TAG = "MarauderSerial"
        const val ESPRESSIF_VID = 0x303A
        const val CP210X_VID = 0x10C4
        const val CH340_VID = 0x1A86
        val BRIDGE_VIDS = setOf(CP210X_VID, CH340_VID)
        private const val ACTION_PERM = "com.cak3d.biscuitmesh.USB_PERMISSION_MARAUDER"

        // CDC class-interface requests.
        private const val CDC_OUT = UsbConstants.USB_DIR_OUT or 0x20 or 0x01 // 0x21
        private const val CDC_SET_LINE_CODING = 0x20
        private const val CDC_SET_CONTROL_LINE_STATE = 0x22
        // Vendor request types.
        private const val CP_OUT = 0x41
        private const val CH_OUT = 0x40
        private const val CH_IN = 0xC0
        private const val CTRL_TIMEOUT = 400
        private const val READ_TIMEOUT = 300

        private val MAC_RE = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
        private val RSSI_RE = Regex("RSSI[:=]?\\s*(-?\\d{1,3})", RegexOption.IGNORE_CASE)
        private val CH_RE = Regex("(?:channel|ch)[:=]?\\s*(\\d{1,3})", RegexOption.IGNORE_CASE)
        private val SSID_RE = Regex("(?:ESSID|SSID)[:=]?\\s*(.*)$", RegexOption.IGNORE_CASE)

        /**
         * Pull one sighting out of a Marauder serial line, tolerant of firmware-version
         * differences. Returns null for any line with no MAC (banners, prompts, headers).
         * Visible for testing.
         */
        fun parseLine(raw: String): MarauderHit? {
            val l = raw.trim()
            val mac = MAC_RE.find(l)?.value?.lowercase() ?: return null
            val isBle = l.contains("[BLE]", true) || Regex("\\bBLE\\b").containsMatchIn(l) ||
                l.contains("Device:", true) && !l.contains("ESSID", true) && CH_RE.find(l) == null

            // WiGLE-style CSV row: MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,Lat,Lon,...
            if (l.count { it == ',' } >= 5 && l.substringBefore(',').trim().let { MAC_RE.matches(it) }) {
                val f = l.split(",")
                val auth = f.getOrNull(2)?.trim().orEmpty()
                val ble = isBle || auth.equals("[BLE]", true) || auth.contains("BLE", true)
                val channel = f.getOrNull(4)?.trim()?.toIntOrNull() ?: 0
                val rssi = f.getOrNull(5)?.trim()?.toIntOrNull() ?: -100
                val label = f.getOrNull(1)?.trim().orEmpty()
                return MarauderHit(if (ble) MarauderKind.BT else MarauderKind.WIFI, mac, label, rssi, channel)
            }

            val rssi = RSSI_RE.find(l)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("(-\\d{2,3})").find(l)?.groupValues?.get(1)?.toIntOrNull()
                ?: -100
            val channel = CH_RE.find(l)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            // SSID is everything after the ESSID/SSID label, minus a trailing MAC if the line
            // ends with one; fall back to a "Device: name" for BLE.
            var label = SSID_RE.find(l)?.groupValues?.get(1)?.trim()?.removeSurrounding("\"").orEmpty()
            if (label.isEmpty() && isBle) {
                label = Regex("Device:\\s*(.*)$", RegexOption.IGNORE_CASE).find(l)?.groupValues?.get(1)?.trim().orEmpty()
            }
            label = label.replace(MAC_RE, "").trim().trim(',', '·', '|').trim()
            return MarauderHit(if (isBle) MarauderKind.BT else MarauderKind.WIFI, mac, label, rssi, channel)
        }
    }
}
