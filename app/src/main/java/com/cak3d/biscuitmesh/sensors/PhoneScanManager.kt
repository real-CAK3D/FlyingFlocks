package com.cak3d.biscuitmesh.sensors

import com.cak3d.biscuitmesh.progress.ProgressManager
import com.cak3d.biscuitmesh.wardrive.SampleKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One row in the live phone-scan list. */
data class PhoneScanRow(
    val kind: SampleKind,
    val id: String,
    val label: String,
    val rssi: Int,
    val lastSeenMs: Long,
    /** Wi-Fi channel (0 = unknown / not Wi-Fi). >= 32 means the 5 GHz band. */
    val channel: Int = 0,
    /** Wi-Fi security bucket (WPA2/WPA3/WPA/WEP/Open/Ent); blank for non-Wi-Fi. */
    val security: String = "",
    /** 802.11 generation label (Wi-Fi 4/5/6/6E/7); blank if unknown or non-Wi-Fi. */
    val standard: String = "",
) {
    val is5GHz: Boolean get() = channel >= 32
}

/**
 * Drives a standalone Wi-Fi / BLE scan with the phone's own radios for the WiFi and
 * Bluetooth tabs — so you can actually sense the air with just the phone, no board
 * required. Everything it sees also scores on the levelling tracks.
 */
class PhoneScanManager(
    private val sensors: PhoneSensors,
    private val progress: ProgressManager?,
) {
    // Hits arrive on two threads — Wi-Fi on the main thread, BLE on its callback thread —
    // so every touch of [rows] is guarded. Nothing here runs the UI thread hot: hits just
    // update the map and flag it dirty; a throttled job does the sorting and the single
    // StateFlow emit. Scoring is handed to a serial background scope. Together this keeps
    // a dense area from flooding the main thread and freezing the app.
    private val rows = LinkedHashMap<String, PhoneScanRow>()
    private val lock = Any()
    @Volatile private var dirty = false
    private var pubJob: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // One-at-a-time scoring so concurrent hits never race the progress state.
    private val scoreScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private val _results = MutableStateFlow<List<PhoneScanRow>>(emptyList())
    val results: StateFlow<List<PhoneScanRow>> = _results.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    fun start(wifi: Boolean, ble: Boolean) {
        if (_scanning.value) return
        _scanning.value = true
        sensors.start(wifi, ble) { hit ->
            synchronized(lock) {
                rows[hit.id] = PhoneScanRow(hit.kind, hit.id, hit.label, hit.rssi, System.currentTimeMillis(), hit.channel, hit.security, hit.standard)
            }
            dirty = true
            progress?.let { p ->
                scoreScope.launch { p.recordPhoneHit(hit.kind, hit.id, hit.label, hit.channel, hit.standard) }
            }
        }
        pubJob = scope.launch {
            while (true) {
                delay(400)
                if (dirty) {
                    dirty = false
                    val snap = synchronized(lock) { rows.values.toList() }
                    _results.value = snap.sortedByDescending { it.rssi }
                }
            }
        }
    }

    fun stop() {
        if (!_scanning.value) return
        sensors.stop()
        pubJob?.cancel(); pubJob = null
        _scanning.value = false
        // Final flush so the list settles on the last seen state.
        val snap = synchronized(lock) { rows.values.toList() }
        _results.value = snap.sortedByDescending { it.rssi }
    }

    fun clear() {
        synchronized(lock) { rows.clear() }
        _results.value = emptyList()
    }
}
