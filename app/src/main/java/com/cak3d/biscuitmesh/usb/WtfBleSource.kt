package com.cak3d.biscuitmesh.usb

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Talks to a What the Flock XIAO ESP32-S3 dongle over BLE — the wireless twin of
 * [WtfSerialSource]. The dongle's firmware mirrors its USB newline-JSON stream out
 * a notify characteristic and accepts `{"cmd":...}` lines on a write characteristic,
 * so this class scans for the WTF GATT service, connects, and funnels every line
 * through the shared [WtfProtocol] parser — identical sightings/GPS to the cable.
 *
 * Its public surface matches [WtfSerialSource] so the app treats either the same
 * way; the two can even run at once (USB tether + BLE), which only means a sighting
 * dedupes to one map pin by MAC.
 *
 * BLE rules followed (see DeviceConnection): one GATT op at a time via a FIFO, and
 * notifications are reassembled on `\n` because the board packs several JSON lines
 * into one notification.
 */
@SuppressLint("MissingPermission")
class WtfBleSource(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _link = MutableStateFlow(WtfLinkState.DISCONNECTED)
    val link: StateFlow<WtfLinkState> = _link.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _gps = MutableStateFlow<WtfGps?>(null)
    val gps: StateFlow<WtfGps?> = _gps.asStateFlow()

    private val _detections = MutableSharedFlow<WtfDetection>(replay = 0, extraBufferCapacity = 64)
    val detections: SharedFlow<WtfDetection> = _detections.asSharedFlow()

    /** Decoded QR/barcode results from the dongle's camera scan mode. */
    private val _scans = MutableSharedFlow<WtfEvent.Scan>(replay = 0, extraBufferCapacity = 8)
    val scans: SharedFlow<WtfEvent.Scan> = _scans.asSharedFlow()

    private var scanner: BluetoothLeScanner? = null
    private var scanCb: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null

    private val lineBuffer = StringBuilder()
    private val opQueue = ArrayDeque<() -> Unit>()
    private val opRunning = AtomicBoolean(false)

    /** True between connect() and disconnect(): keep scanning / reconnecting across drops. */
    private val wantConnected = AtomicBoolean(false)

    /** Scan for a WTF dongle and connect to the first one found. Safe to call twice. */
    fun connect() {
        val a = adapter ?: return
        if (!a.isEnabled) return
        if (!wantConnected.compareAndSet(false, true)) return
        startScan()
    }

    fun disconnect() {
        wantConnected.set(false)
        stopScan()
        gatt?.let { runCatching { it.disconnect() } }
        _link.value = WtfLinkState.DISCONNECTED
    }

    val isWanted: Boolean get() = wantConnected.get()

    // --- scanning ----------------------------------------------------------

    private fun startScan() {
        if (scanCb != null) return
        val s = adapter?.bluetoothLeScanner ?: return
        _link.value = WtfLinkState.CONNECTING
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(WTF_SVC)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                stopScan()
                openGatt(dev)
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.firstOrNull()?.let { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }
            override fun onScanFailed(errorCode: Int) { _link.value = WtfLinkState.DISCONNECTED }
        }
        scanner = s
        scanCb = cb
        runCatching { s.startScan(filters, settings, cb) }
            .onFailure { _link.value = WtfLinkState.DISCONNECTED; scanCb = null }
    }

    private fun stopScan() {
        val cb = scanCb ?: return
        scanCb = null
        runCatching { scanner?.stopScan(cb) }
    }

    private fun openGatt(dev: BluetoothDevice) {
        _link.value = WtfLinkState.CONNECTING
        _deviceName.value = runCatching { dev.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "WhatTheFlock (BLE)"
        gatt = dev.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    // --- GATT op serialization (Android allows one op at a time) -----------

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
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status != BluetoothGatt.GATT_SUCCESS) { closeGatt(); maybeRescan(); return }
                    g.requestMtu(247)
                }
                BluetoothProfile.STATE_DISCONNECTED -> { closeGatt(); maybeRescan() }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) { g.discoverServices() }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) { g.disconnect(); return }
            val svc = g.getService(WTF_SVC) ?: run { g.disconnect(); return }
            rxChar = svc.getCharacteristic(WTF_RX)
            val tx = svc.getCharacteristic(WTF_TX) ?: run { g.disconnect(); return }
            g.setCharacteristicNotification(tx, true)
            val cccd = tx.getDescriptor(CCCD) ?: run { g.disconnect(); return }
            enqueue {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        g.writeDescriptor(cccd)
                    }
                }
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            _link.value = WtfLinkState.CONNECTED
            opComplete()
            // Same handshake as the USB connect: current config + board status (GPS),
            // then replay whatever it caught while out of range.
            send(JSONObject().put("cmd", "get_config"))
            send(JSONObject().put("cmd", "sd_info"))
            requestDump()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { opComplete() }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            if (c.uuid == WTF_TX) feed(c.value)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (c.uuid == WTF_TX) feed(value)
        }
    }

    private fun feed(value: ByteArray?) {
        if (value == null) return
        lineBuffer.append(String(value, Charsets.UTF_8))
        var nl = lineBuffer.indexOf("\n")
        while (nl >= 0) {
            val l = lineBuffer.substring(0, nl).trimEnd('\r')
            lineBuffer.delete(0, nl + 1)
            if (l.isNotEmpty()) handleLine(l)
            nl = lineBuffer.indexOf("\n")
        }
        if (lineBuffer.length > 8192) lineBuffer.setLength(0)   // runaway line without a newline
    }

    private fun handleLine(l: String) {
        when (val e = WtfProtocol.parseLine(l)) {
            is WtfEvent.Sighting -> _detections.tryEmit(e.detection)
            is WtfEvent.Gps -> _gps.value = e.gps
            is WtfEvent.Scan -> _scans.tryEmit(e)
            null -> Unit
        }
    }

    /** Ask the dongle to run a camera QR/barcode scan; the result arrives on [scans]. */
    fun requestQrScan() { send(JSONObject().put("cmd", "qrscan")) }

    fun requestDump() { send(JSONObject().put("cmd", "dump_session").put("source", "prev")) }
    fun requestSdDump() { send(JSONObject().put("cmd", "sd_dump").put("from", 0).put("max", 1000)) }
    fun requestGpsStatus() { send(JSONObject().put("cmd", "sd_info")) }

    private fun send(obj: JSONObject) {
        val g = gatt ?: return
        val c = rxChar ?: return
        val bytes = (obj.toString() + "\n").toByteArray(Charsets.UTF_8)
        enqueue {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                run {
                    c.value = bytes
                    c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    g.writeCharacteristic(c)
                }
            }
        }
    }

    private fun closeGatt() {
        synchronized(opQueue) { opQueue.clear() }
        opRunning.set(false)
        rxChar = null
        lineBuffer.setLength(0)
        runCatching { gatt?.close() }
        gatt = null
        _link.value = WtfLinkState.DISCONNECTED
    }

    /** After a drop, resume scanning if the user still wants the link. */
    private fun maybeRescan() {
        _deviceName.value = null
        if (wantConnected.get()) startScan()
    }

    private companion object {
        val WTF_SVC: UUID = UUID.fromString("f10c0001-9b1d-4b7a-8e2a-7c0ffee5f10c")
        val WTF_TX: UUID = UUID.fromString("f10c0002-9b1d-4b7a-8e2a-7c0ffee5f10c")
        val WTF_RX: UUID = UUID.fromString("f10c0003-9b1d-4b7a-8e2a-7c0ffee5f10c")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
