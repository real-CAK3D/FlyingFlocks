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

/**
 * Talks to a plugged-in Biscuit board (XIAO ESP32-C5 running c5biscuit.ino) over USB-OTG
 * as a CDC-ACM serial device — the same board Flying Flocks otherwise reaches over BLE.
 *
 * The board mirrors its BLE line protocol to USB-CDC: `DATA:`/`STATUS:` lines stream out,
 * and `CMD:` lines are accepted in. This source opens the CDC data interface at 115200 8N1
 * (DTR asserted, RTS low so the C5 isn't held in reset), reads newline-delimited lines onto
 * [lines], and [send]s command lines back. Callers feed [lines] into the fleet log, so a
 * USB-attached board's Wi-Fi/sub-GHz results appear everywhere a BLE board's would.
 *
 * Reuses the proven CDC plumbing of [WtfSerialSource]; the difference is the payload
 * (plain Biscuit text, not the WTF dongle's JSON).
 */
class BiscuitSerialSource(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val usb = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _link = MutableStateFlow(WtfLinkState.DISCONNECTED)
    val link: StateFlow<WtfLinkState> = _link.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    /** Each complete protocol line read from the board (newline-stripped). */
    private val _lines = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 128)
    val lines: SharedFlow<String> = _lines.asSharedFlow()

    private var connection: UsbDeviceConnection? = null
    private var dataIntf: UsbInterface? = null
    private var ctrlIntf: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null
    private var readJob: Job? = null
    private var permReceiver: BroadcastReceiver? = null

    /** Connect to [device], or the first Espressif board if null. Safe to call twice. */
    fun connect(device: UsbDevice? = null) {
        if (_link.value != WtfLinkState.DISCONNECTED) return
        val mgr = usb ?: return
        val dev = device ?: mgr.deviceList.values.firstOrNull { it.vendorId == ESPRESSIF_VID } ?: return
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
            val pi = PendingIntent.getBroadcast(appContext, 1, Intent(ACTION_PERM).setPackage(appContext.packageName), flags)
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

        var data: UsbInterface? = null
        var ctrl: UsbInterface? = null
        for (i in 0 until dev.interfaceCount) {
            val intf = dev.getInterface(i)
            when (intf.interfaceClass) {
                UsbConstants.USB_CLASS_CDC_DATA -> if (data == null) data = intf
                UsbConstants.USB_CLASS_COMM -> if (ctrl == null) ctrl = intf
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

        val index = (ctrl ?: data).id
        val lineCoding = byteArrayOf(0x00, 0xC2.toByte(), 0x01, 0x00, 0x00, 0x00, 0x08) // 115200 8N1
        conn.controlTransfer(REQ_TYPE_OUT, SET_LINE_CODING, 0, index, lineCoding, lineCoding.size, CTRL_TIMEOUT)
        conn.controlTransfer(REQ_TYPE_OUT, SET_CONTROL_LINE_STATE, 0x01, index, null, 0, CTRL_TIMEOUT)

        connection = conn
        dataIntf = data
        ctrlIntf = ctrl
        epIn = inEp
        epOut = outEp
        _deviceName.value = runCatching { dev.productName }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Biscuit board (USB)"
        _link.value = WtfLinkState.CONNECTED
        startReadLoop(inEp)
    }

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
                        if (l.isNotEmpty()) _lines.tryEmit(l)
                    } else if (line.length < 8192) {
                        line.append(ch)
                    } else {
                        line.setLength(0)
                    }
                }
            }
        }
    }

    /** Send a framed command line (e.g. from [com.cak3d.biscuitmesh.protocol.command]). No-op if not connected. */
    fun send(bytes: ByteArray) {
        val conn = connection ?: return
        val out = epOut ?: return
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
        const val ESPRESSIF_VID = 0x303A
        const val ACTION_PERM = "com.cak3d.biscuitmesh.USB_PERMISSION_BISCUIT"
        const val REQ_TYPE_OUT = UsbConstants.USB_DIR_OUT or 0x20 or 0x01
        const val SET_LINE_CODING = 0x20
        const val SET_CONTROL_LINE_STATE = 0x22
        const val CTRL_TIMEOUT = 400
        const val READ_TIMEOUT = 300
    }
}
