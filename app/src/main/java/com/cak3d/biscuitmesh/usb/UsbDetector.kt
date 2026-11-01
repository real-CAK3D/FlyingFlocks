package com.cak3d.biscuitmesh.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UsbDeviceInfo(
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val kind: String,
    /** True when it's a USB-serial bridge we could talk to a board through. */
    val serialCapable: Boolean,
)

/**
 * Watches for USB-OTG devices being attached or detached and classifies known
 * USB-serial bridge chips — the kind an ESP32 Biscuit presents over a cable.
 *
 * A plain USB Wi-Fi dongle is surfaced too, but an app can't drive it as a radio
 * (that needs kernel drivers Android doesn't expose), so the path forward for
 * "plug in and go" is a USB-serial transport to a board, which this sets up for.
 */
class UsbDetector(context: Context) {

    private val appContext = context.applicationContext
    private val usb = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _devices = MutableStateFlow<List<UsbDeviceInfo>>(emptyList())
    val devices: StateFlow<List<UsbDeviceInfo>> = _devices.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = refresh()
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }
        }
        refresh()
    }

    fun stop() {
        runCatching { appContext.unregisterReceiver(receiver) }
    }

    private fun refresh() {
        _devices.value = usb?.deviceList?.values?.map { describe(it) } ?: emptyList()
    }

    private fun describe(d: UsbDevice): UsbDeviceInfo {
        val serial = d.vendorId in SERIAL_VENDORS
        val kind = when (d.vendorId) {
            0x10C4 -> "USB-serial (CP210x)"
            0x1A86 -> "USB-serial (CH340/CH9102)"
            0x0403 -> "USB-serial (FTDI)"
            0x067B -> "USB-serial (Prolific)"
            0x303A -> "Espressif USB"
            else -> "USB device"
        }
        val name = runCatching { d.productName }.getOrNull()?.takeIf { it.isNotBlank() } ?: d.deviceName
        return UsbDeviceInfo(name, d.vendorId, d.productId, kind, serial)
    }

    private companion object {
        val SERIAL_VENDORS = setOf(0x10C4, 0x1A86, 0x0403, 0x067B, 0x303A)
    }
}
