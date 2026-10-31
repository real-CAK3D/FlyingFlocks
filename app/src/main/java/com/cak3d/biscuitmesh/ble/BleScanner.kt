package com.cak3d.biscuitmesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import com.cak3d.biscuitmesh.protocol.BiscuitGatt

/**
 * Scans for Biscuit boards. Filters on the service UUID (the advertised name is
 * identical across devices and useless as a discriminator).
 *
 * Callers must hold BLUETOOTH_SCAN (API 31+) or location (<=30) before starting.
 */
class BleScanner(private val adapter: BluetoothAdapter) {

    data class Found(val address: String, val name: String?, val rssi: Int)

    private var callback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    fun start(onFound: (Found) -> Unit) {
        if (callback != null) return
        val scanner = adapter.bluetoothLeScanner ?: return

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BiscuitGatt.SERVICE))
                .build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                onFound(Found(device.address, device.name, result.rssi))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }
        }
        callback = cb
        scanner.startScan(filters, settings, cb)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val cb = callback ?: return
        callback = null
        adapter.bluetoothLeScanner?.stopScan(cb)
    }

    val isScanning: Boolean get() = callback != null
}
