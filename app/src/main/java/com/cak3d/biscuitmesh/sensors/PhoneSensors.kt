package com.cak3d.biscuitmesh.sensors

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.cak3d.biscuitmesh.wardrive.SampleKind

/**
 * Turns the phone itself into a wardrive sensor using its own Wi-Fi and BLE
 * radios, so Flying Flocks can scan, map, and feed WDG with no ESP32 board
 * attached. Each observation is handed back as a [Hit]; the wardrive engine
 * geo-tags and dedups it exactly like a board's DATA record.
 */
class PhoneSensors(context: Context, private val adapter: BluetoothAdapter?) {

    private val appContext = context.applicationContext
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    data class Hit(val kind: SampleKind, val id: String, val label: String, val rssi: Int, val channel: Int = 0, val security: String = "")

    private var onHit: ((Hit) -> Unit)? = null
    private var scanningWifi = false
    private var scanningBle = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            emitWifi()
            // Re-arm. Modern Android throttles startScan(), but the cached
            // results still refresh as the phone moves.
            if (scanningWifi) runCatching { @Suppress("DEPRECATION") wifi?.startScan() }
        }
    }

    // Belt-and-suspenders: the SCAN_RESULTS broadcast can be throttled or dropped on
    // newer Android, so also poll the cached results on a timer while scanning.
    private val wifiPoller = object : Runnable {
        override fun run() {
            if (!scanningWifi) return
            emitWifi()
            runCatching { @Suppress("DEPRECATION") wifi?.startScan() }
            mainHandler.postDelayed(this, 6000)
        }
    }

    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            val name = result.scanRecord?.deviceName ?: ""
            onHit?.invoke(Hit(SampleKind.BT, dev.address, name, result.rssi))
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }
    }

    @SuppressLint("MissingPermission")
    fun start(wifiOn: Boolean, bleOn: Boolean, onHit: (Hit) -> Unit) {
        this.onHit = onHit
        if (wifiOn && wifi != null && !scanningWifi) {
            scanningWifi = true
            runCatching {
                val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                // SCAN_RESULTS_AVAILABLE is a SYSTEM broadcast — it must be EXPORTED, or
                // Android 13+ silently drops it and the list never updates.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(wifiReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    appContext.registerReceiver(wifiReceiver, filter)
                }
            }
            emitWifi() // seed from the last cached scan right away
            runCatching { @Suppress("DEPRECATION") wifi?.startScan() }
            mainHandler.postDelayed(wifiPoller, 6000)
        }
        if (bleOn && !scanningBle) {
            val scanner = adapter?.bluetoothLeScanner
            if (scanner != null) {
                scanningBle = true
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                runCatching { scanner.startScan(null, settings, bleCallback) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (scanningWifi) {
            scanningWifi = false
            mainHandler.removeCallbacks(wifiPoller)
            runCatching { appContext.unregisterReceiver(wifiReceiver) }
        }
        if (scanningBle) {
            runCatching { adapter?.bluetoothLeScanner?.stopScan(bleCallback) }
            scanningBle = false
        }
        onHit = null
    }

    @SuppressLint("MissingPermission")
    private fun emitWifi() {
        val results = runCatching { @Suppress("DEPRECATION") wifi?.scanResults }.getOrNull() ?: return
        val cb = onHit ?: return
        results.forEach { r ->
            val bssid = r.BSSID ?: return@forEach
            @Suppress("DEPRECATION")
            val ssid = r.SSID ?: ""
            cb(Hit(SampleKind.AP, bssid, ssid, r.level, channelFromFreq(r.frequency), securityOf(r.capabilities ?: "")))
        }
    }

    /** Bucket a Wi-Fi ScanResult.capabilities string into a single security label. */
    private fun securityOf(caps: String): String {
        val c = caps.uppercase()
        return when {
            "WPA3" in c || "SAE" in c -> "WPA3"
            "EAP" in c || "802.1X" in c -> "Ent"
            "WPA2" in c || "RSN" in c -> "WPA2"
            "WPA" in c -> "WPA"
            "WEP" in c -> "WEP"
            else -> "Open"
        }
    }

    /** Wi-Fi channel number from a centre frequency in MHz (2.4 GHz and 5 GHz). */
    private fun channelFromFreq(mhz: Int): Int = when {
        mhz == 2484 -> 14
        mhz in 2412..2472 -> (mhz - 2407) / 5      // 2.4 GHz: ch 1-13
        mhz in 5000..5900 -> (mhz - 5000) / 5       // 5 GHz: ch 32+
        else -> 0
    }
}
