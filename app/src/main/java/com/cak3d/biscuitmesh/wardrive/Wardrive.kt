package com.cak3d.biscuitmesh.wardrive

import android.annotation.SuppressLint
import com.cak3d.biscuitmesh.ble.FleetManager
import com.cak3d.biscuitmesh.location.LocationProvider
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import com.cak3d.biscuitmesh.protocol.command
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WardriveMode { ALL, WIFI, BT }

enum class SampleKind { AP, STATION, BT }

data class LatLon(val lat: Double, val lon: Double)

data class WardriveSample(
    val kind: SampleKind,
    val id: String,       // BSSID / MAC — the dedup key
    val label: String,    // SSID / device name
    val lat: Double,
    val lon: Double,
    val rssi: Int,
    val timeMs: Long,
)

data class WardriveState(
    val active: Boolean = false,
    val mode: WardriveMode = WardriveMode.ALL,
    val wifiCount: Int = 0,
    val btCount: Int = 0,
    val startMs: Long = 0L,
    val lastLat: Double? = null,
    val lastLon: Double? = null,
    val accuracy: Float? = null,
)

/**
 * Drives a wardrive session: the phone supplies GPS, the fleet supplies scan
 * results, and each new AP/station/BT record is geo-tagged at the current
 * location into a deduplicated sample set and plotted on the map.
 */
class WardriveManager(
    private val fleet: FleetManager,
    private val location: LocationProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(WardriveState())
    val state: StateFlow<WardriveState> = _state.asStateFlow()

    private val _samples = MutableStateFlow<Map<String, WardriveSample>>(emptyMap())
    val samples: StateFlow<Map<String, WardriveSample>> = _samples.asStateFlow()

    private val _track = MutableStateFlow<List<LatLon>>(emptyList())
    val track: StateFlow<List<LatLon>> = _track.asStateFlow()

    private var locJob: Job? = null
    private var logJob: Job? = null
    private var lastProcessedMs = 0L

    @Volatile private var curLat: Double? = null
    @Volatile private var curLon: Double? = null

    @SuppressLint("MissingPermission")
    fun start(mode: WardriveMode) {
        if (_state.value.active) return
        lastProcessedMs = System.currentTimeMillis()
        _samples.value = emptyMap()
        _track.value = emptyList()
        _state.update {
            it.copy(active = true, mode = mode, startMs = System.currentTimeMillis(), wifiCount = 0, btCount = 0)
        }

        locJob = scope.launch {
            location.locationUpdates().collect { loc ->
                curLat = loc.latitude
                curLon = loc.longitude
                _track.update { (it + LatLon(loc.latitude, loc.longitude)).takeLast(5000) }
                _state.update { it.copy(lastLat = loc.latitude, lastLon = loc.longitude, accuracy = loc.accuracy) }
            }
        }

        logJob = scope.launch {
            fleet.log.collect { entries ->
                val lat = curLat
                val lon = curLon
                val newEntries = entries.filter { it.timeMs > lastProcessedMs }
                if (newEntries.isEmpty()) return@collect
                lastProcessedMs = newEntries.maxOf { it.timeMs }
                if (lat == null || lon == null) return@collect
                var added = false
                newEntries.forEach { e ->
                    val sample = parseSample(e.message, lat, lon, e.timeMs) ?: return@forEach
                    if (!matchesMode(sample.kind, _state.value.mode)) return@forEach
                    if (!_samples.value.containsKey(sample.id)) {
                        _samples.update { it + (sample.id to sample) }
                        added = true
                    }
                }
                if (added) recount()
            }
        }

        fleet.broadcast(scanCommand(mode))
    }

    fun stop() {
        if (!_state.value.active) return
        fleet.broadcast(command("stopscan"))
        locJob?.cancel(); locJob = null
        logJob?.cancel(); logJob = null
        _state.update { it.copy(active = false) }
    }

    fun setMode(mode: WardriveMode) {
        _state.update { it.copy(mode = mode) }
        if (_state.value.active) fleet.broadcast(scanCommand(mode))
    }

    private fun recount() {
        val vals = _samples.value.values
        val wifi = vals.count { it.kind == SampleKind.AP || it.kind == SampleKind.STATION }
        val bt = vals.count { it.kind == SampleKind.BT }
        _state.update { it.copy(wifiCount = wifi, btCount = bt) }
    }

    private fun scanCommand(mode: WardriveMode): ByteArray = when (mode) {
        WardriveMode.ALL -> command("scanall")
        WardriveMode.WIFI -> command("scanap")
        WardriveMode.BT -> command("sniffbt", "type=all")
    }

    private fun matchesMode(kind: SampleKind, mode: WardriveMode): Boolean = when (mode) {
        WardriveMode.ALL -> true
        WardriveMode.WIFI -> kind == SampleKind.AP || kind == SampleKind.STATION
        WardriveMode.BT -> kind == SampleKind.BT
    }

    private fun parseSample(msg: BiscuitMessage, lat: Double, lon: Double, timeMs: Long): WardriveSample? {
        if (msg.type != BiscuitMessage.Type.DATA) return null
        val f = msg.payload.split(",")
        return when (msg.subtype) {
            "AP" -> if (f.size >= 4) WardriveSample(SampleKind.AP, f[1], f[0], lat, lon, f[3].toIntOrNull() ?: 0, timeMs) else null
            "STA" -> if (f.size >= 4) WardriveSample(SampleKind.STATION, f[0], f[0], lat, lon, f[3].toIntOrNull() ?: 0, timeMs) else null
            "BT" -> if (f.size >= 3) WardriveSample(SampleKind.BT, f[1], f[0], lat, lon, f[2].toIntOrNull() ?: 0, timeMs) else null
            else -> null
        }
    }
}
