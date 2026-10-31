package com.cak3d.biscuitmesh.wardrive

import android.annotation.SuppressLint
import android.location.Location
import com.cak3d.biscuitmesh.ble.FleetManager
import com.cak3d.biscuitmesh.data.DeadZone
import com.cak3d.biscuitmesh.data.DeadZonesStore
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
    val accuracyM: Float = 0f,
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
    /** True while the phone sits inside a dead zone; scanning is suppressed. */
    val inDeadZone: Boolean = false,
    val deadZoneName: String? = null,
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
    deadZonesStore: DeadZonesStore? = null,
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
    @Volatile private var curAcc: Float = 0f

    /** Latest dead-zone list, kept current from the store for synchronous checks. */
    @Volatile private var zones: List<DeadZone> = emptyList()
    /** True while suppressed, so we only broadcast on dead-zone transitions. */
    private var suppressed = false

    init {
        deadZonesStore?.let { store ->
            scope.launch { store.zones.collect { zones = it } }
        }
    }

    /** The dead zone the given point falls inside, or null. */
    private fun zoneAt(lat: Double, lon: Double): DeadZone? {
        val out = FloatArray(1)
        return zones.firstOrNull { z ->
            Location.distanceBetween(lat, lon, z.lat, z.lon, out)
            out[0] <= z.radiusM
        }
    }

    @SuppressLint("MissingPermission")
    fun start(mode: WardriveMode) {
        if (_state.value.active) return
        lastProcessedMs = System.currentTimeMillis()
        suppressed = false
        _samples.value = emptyMap()
        _track.value = emptyList()
        _state.update {
            it.copy(
                active = true, mode = mode, startMs = System.currentTimeMillis(),
                wifiCount = 0, btCount = 0, inDeadZone = false, deadZoneName = null,
            )
        }

        locJob = scope.launch {
            location.locationUpdates().collect { loc ->
                val zone = zoneAt(loc.latitude, loc.longitude)
                if (zone != null) {
                    // Inside a dead zone: stop the boards, record nothing, drop the
                    // tagging fix so no stray samples land, but keep the puck moving.
                    if (!suppressed) {
                        suppressed = true
                        fleet.broadcast(command("stopscan"))
                    }
                    curLat = null
                    curLon = null
                    _state.update {
                        it.copy(lastLat = loc.latitude, lastLon = loc.longitude, accuracy = loc.accuracy,
                            inDeadZone = true, deadZoneName = zone.name)
                    }
                } else {
                    if (suppressed) {
                        suppressed = false
                        fleet.broadcast(scanCommand(_state.value.mode))
                    }
                    curLat = loc.latitude
                    curLon = loc.longitude
                    curAcc = loc.accuracy
                    _track.update { (it + LatLon(loc.latitude, loc.longitude)).takeLast(5000) }
                    _state.update {
                        it.copy(lastLat = loc.latitude, lastLon = loc.longitude, accuracy = loc.accuracy,
                            inDeadZone = false, deadZoneName = null)
                    }
                }
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
                val acc = curAcc
                newEntries.forEach { e ->
                    val sample = parseSample(e.message, lat, lon, acc, e.timeMs) ?: return@forEach
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
        // Don't wake the boards back up while we're parked in a dead zone.
        if (_state.value.active && !suppressed) fleet.broadcast(scanCommand(mode))
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

    private fun parseSample(msg: BiscuitMessage, lat: Double, lon: Double, acc: Float, timeMs: Long): WardriveSample? {
        if (msg.type != BiscuitMessage.Type.DATA) return null
        val f = msg.payload.split(",")
        return when (msg.subtype) {
            "AP" -> if (f.size >= 4) WardriveSample(SampleKind.AP, f[1], f[0], lat, lon, f[3].toIntOrNull() ?: 0, timeMs, acc) else null
            "STA" -> if (f.size >= 4) WardriveSample(SampleKind.STATION, f[0], f[0], lat, lon, f[3].toIntOrNull() ?: 0, timeMs, acc) else null
            "BT" -> if (f.size >= 3) WardriveSample(SampleKind.BT, f[1], f[0], lat, lon, f[2].toIntOrNull() ?: 0, timeMs, acc) else null
            else -> null
        }
    }
}
