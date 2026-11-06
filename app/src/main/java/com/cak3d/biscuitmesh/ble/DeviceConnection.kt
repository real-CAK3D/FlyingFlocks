package com.cak3d.biscuitmesh.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.cak3d.biscuitmesh.model.ConnectionState
import com.cak3d.biscuitmesh.model.DeviceStatus
import com.cak3d.biscuitmesh.protocol.BiscuitGatt
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One board's live BLE connection and its GATT state machine.
 *
 * BLE requires GATT operations (write, descriptor write, MTU request, reads) to be
 * serialized one at a time, so all of them go through a small FIFO queue; the next
 * op starts only when the previous one's callback fires.
 *
 * Incoming RESPONSE notifications are buffered and split on `\n` because the device
 * packs several DATA records into one notification.
 */
@SuppressLint("MissingPermission")
class DeviceConnection(
    private val context: Context,
    val device: BluetoothDevice,
    private val listener: Listener,
) {
    interface Listener {
        fun onStateChanged(address: String, state: ConnectionState, error: String?)
        fun onMessage(address: String, message: BiscuitMessage)
        fun onStatus(address: String, status: DeviceStatus)
        fun onDeviceInfo(address: String, model: String?, firmware: String?)
    }

    val address: String get() = device.address

    private var gatt: BluetoothGatt? = null
    private var cmdChar: BluetoothGattCharacteristic? = null

    private val opQueue = ArrayDeque<() -> Unit>()
    private val opRunning = AtomicBoolean(false)
    private val lineBuffer = StringBuilder()
    private val closed = AtomicBoolean(false)

    // A stalled connect (e.g. a spurious CONNECTED with status 133) must not sit forever
    // holding an open GATT client interface — Android caps those per app, and leaking them
    // is what makes every later connect fail with 133 ("out of range"). This watchdog
    // force-fails a connect that never reaches READY.
    private val watchdog = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { fail("Connection timed out") }

    fun connect() {
        closed.set(false)
        emit(ConnectionState.CONNECTING)
        watchdog.postDelayed(timeoutRunnable, CONNECT_TIMEOUT_MS)
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
    }

    /** Close the GATT and report FAILED exactly once, so no failure path leaks the client interface. */
    private fun fail(error: String) {
        if (!closed.compareAndSet(false, true)) return
        cleanup()
        emit(ConnectionState.FAILED, error)
    }

    /** Sends a raw `CMD:...` line (already framed with a trailing `\n`). */
    fun writeCommand(bytes: ByteArray) {
        val g = gatt ?: return
        val c = cmdChar ?: return
        enqueue {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                c.value = bytes
                @Suppress("DEPRECATION")
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
        }
    }

    /** Replies to the device's TIME:REQUEST with the phone's local time + offset. */
    private fun respondTimeSync() {
        val iso = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))
        writeCommand("TIME:SYNC:$iso:\n".toByteArray(Charsets.UTF_8))
    }

    fun requestStatus() {
        val g = gatt ?: return
        val svc = g.getService(BiscuitGatt.SERVICE) ?: return
        val statusChar = svc.getCharacteristic(BiscuitGatt.STATUS) ?: return
        enqueue { g.readCharacteristic(statusChar) }
    }

    // --- GATT op queue -----------------------------------------------------

    private fun enqueue(op: () -> Unit) {
        synchronized(opQueue) { opQueue.add(op) }
        pump()
    }

    private fun pump() {
        if (opRunning.compareAndSet(false, true)) {
            val op = synchronized(opQueue) { opQueue.poll() }
            if (op == null) {
                opRunning.set(false)
            } else {
                op()
            }
        }
    }

    private fun opComplete() {
        opRunning.set(false)
        pump()
    }

    // --- GATT callbacks ----------------------------------------------------

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    // A CONNECTED event with a non-success status is a failed link (commonly 133);
                    // proceeding would hang in PROVISIONING with the GATT held open. Fail cleanly.
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        fail("Connect failed (status $status)")
                        return
                    }
                    emit(ConnectionState.PROVISIONING)
                    g.requestMtu(BiscuitGatt.PREFERRED_MTU)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (!closed.compareAndSet(false, true)) return
                    cleanup()
                    emit(ConnectionState.DISCONNECTED, if (status != 0) "GATT status $status" else null)
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            // Proceed regardless; a smaller MTU only means more fragmentation.
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("Service discovery failed ($status)")
                return
            }
            val svc = g.getService(BiscuitGatt.SERVICE)
            if (svc == null) {
                fail("Biscuit service not found")
                return
            }
            cmdChar = svc.getCharacteristic(BiscuitGatt.CMD)
            readDeviceInfo(g)
            enableResponseNotifications(g, svc)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            // Notifications are now live; the device will answer commands. Connected for real.
            watchdog.removeCallbacks(timeoutRunnable)
            emit(ConnectionState.READY)
            requestStatus()
            opComplete()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            opComplete()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            handleNotification(c.uuid, c.value)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleNotification(c.uuid, value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            handleRead(c, c.value, status)
            opComplete()
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            handleRead(c, value, status)
            opComplete()
        }
    }

    private fun readDeviceInfo(g: BluetoothGatt) {
        val info = g.getService(BiscuitGatt.DEVICE_INFO_SERVICE) ?: return
        info.getCharacteristic(BiscuitGatt.MODEL_NUMBER)?.let { ch -> enqueue { g.readCharacteristic(ch) } }
        info.getCharacteristic(BiscuitGatt.FIRMWARE_REV)?.let { ch -> enqueue { g.readCharacteristic(ch) } }
    }

    private fun enableResponseNotifications(g: BluetoothGatt, svc: android.bluetooth.BluetoothGattService) {
        val response = svc.getCharacteristic(BiscuitGatt.RESPONSE) ?: run {
            fail("Response characteristic missing")
            return
        }
        g.setCharacteristicNotification(response, true)
        val cccd = response.getDescriptor(BiscuitGatt.CCCD) ?: run {
            fail("CCCD missing")
            return
        }
        enqueue {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }
    }

    private var pendingModel: String? = null
    private var pendingFirmware: String? = null

    private fun handleRead(c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) return
        when (c.uuid) {
            BiscuitGatt.MODEL_NUMBER -> {
                pendingModel = value.toString(Charsets.UTF_8).trim()
                listener.onDeviceInfo(address, pendingModel, pendingFirmware)
            }
            BiscuitGatt.FIRMWARE_REV -> {
                pendingFirmware = value.toString(Charsets.UTF_8).trim()
                listener.onDeviceInfo(address, pendingModel, pendingFirmware)
            }
            BiscuitGatt.STATUS -> parseStatus(value.toString(Charsets.UTF_8))
        }
    }

    private fun handleNotification(uuid: java.util.UUID, value: ByteArray?) {
        if (uuid != BiscuitGatt.RESPONSE || value == null) return
        lineBuffer.append(value.toString(Charsets.UTF_8))
        var newline = lineBuffer.indexOf("\n")
        while (newline >= 0) {
            val line = lineBuffer.substring(0, newline)
            lineBuffer.delete(0, newline + 1)
            if (line.isNotBlank()) {
                val msg = BiscuitMessage.parse(line)
                // The device asks the phone for the time; answer so its DATA records
                // and logs are timestamped correctly. Documented integration surface.
                if (msg.type == BiscuitMessage.Type.TIME && msg.subtype == "REQUEST") {
                    respondTimeSync()
                }
                listener.onMessage(address, msg)
            }
            newline = lineBuffer.indexOf("\n")
        }
    }

    private fun parseStatus(json: String) {
        val status = runCatching {
            val o = JSONObject(json)
            DeviceStatus(
                statusCode = o.optInt("status", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
                battery = o.optInt("battery", Int.MIN_VALUE)
                    .takeIf { it != Int.MIN_VALUE && it >= 0 },
                temperatureC = if (o.has("temperatureC") && !o.isNull("temperatureC"))
                    o.optDouble("temperatureC") else null,
                sdMounted = if (o.has("sd")) o.optBoolean("sd") else null,
                activeOp = o.optString("op").takeIf { it.isNotEmpty() },
                rawJson = json,
            )
        }.getOrNull() ?: DeviceStatus(rawJson = json)
        listener.onStatus(address, status)
    }

    private fun cleanup() {
        watchdog.removeCallbacks(timeoutRunnable)
        synchronized(opQueue) { opQueue.clear() }
        opRunning.set(false)
        cmdChar = null
        gatt?.close()
        gatt = null
    }

    private fun emit(state: ConnectionState, error: String? = null) =
        listener.onStateChanged(address, state, error)

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
    }
}
