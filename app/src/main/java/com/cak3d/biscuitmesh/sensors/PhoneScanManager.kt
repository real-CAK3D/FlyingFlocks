package com.cak3d.biscuitmesh.sensors

import com.cak3d.biscuitmesh.progress.ProgressManager
import com.cak3d.biscuitmesh.wardrive.SampleKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One row in the live phone-scan list. */
data class PhoneScanRow(
    val kind: SampleKind,
    val id: String,
    val label: String,
    val rssi: Int,
    val lastSeenMs: Long,
)

/**
 * Drives a standalone Wi-Fi / BLE scan with the phone's own radios for the WiFi and
 * Bluetooth tabs — so you can actually sense the air with just the phone, no board
 * required. Everything it sees also scores on the levelling tracks.
 */
class PhoneScanManager(
    private val sensors: PhoneSensors,
    private val progress: ProgressManager?,
) {
    private val rows = LinkedHashMap<String, PhoneScanRow>()

    private val _results = MutableStateFlow<List<PhoneScanRow>>(emptyList())
    val results: StateFlow<List<PhoneScanRow>> = _results.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    fun start(wifi: Boolean, ble: Boolean) {
        if (_scanning.value) return
        _scanning.value = true
        sensors.start(wifi, ble) { hit ->
            rows[hit.id] = PhoneScanRow(hit.kind, hit.id, hit.label, hit.rssi, System.currentTimeMillis())
            _results.value = rows.values.sortedByDescending { it.rssi }
            progress?.recordPhoneHit(hit.kind, hit.id, hit.label)
        }
    }

    fun stop() {
        if (!_scanning.value) return
        sensors.stop()
        _scanning.value = false
    }

    fun clear() {
        rows.clear()
        _results.value = emptyList()
    }
}
