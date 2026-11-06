package com.cak3d.biscuitmesh.wardrive

import android.annotation.SuppressLint
import android.location.Location
import com.cak3d.biscuitmesh.ble.FleetManager
import com.cak3d.biscuitmesh.data.DeadZone
import com.cak3d.biscuitmesh.data.DeadZonesStore
import com.cak3d.biscuitmesh.location.LocationProvider
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import com.cak3d.biscuitmesh.protocol.command
import com.cak3d.biscuitmesh.sensors.PhoneSensors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Source id used for the phone's own radios in a sample's [WardriveSample.sources]. */
const val PHONE_SOURCE = "phone"

// GPS glitch-rejection thresholds (see WardriveManager.isPlausibleFix).
private const val MAX_ACCURACY_M = 60f      // ignore fixes fuzzier than this
private const val MIN_MOVE_M = 25f          // moves under this are always accepted
private const val MAX_SPEED_MPS = 75.0      // ~168 mph; anything faster is a teleport

enum class WardriveMode { ALL, WIFI, BT }

enum class SampleKind { AP, STATION, BT, RF }

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
    /** Wi-Fi channel (0 = unknown / not Wi-Fi). Channel >= 32 means the 5 GHz band. */
    val channel: Int = 0,
    /** Which board(s) — or the phone — saw this point. >1 means a shared sighting. */
    val sources: Set<String> = emptySet(),
) {
    /** True for a 5 GHz Wi-Fi network (what the wtBLEep node uniquely catches). */
    val is5GHz: Boolean get() = channel >= 32
}

/** A device's accumulated sighting history — for the detail screen. Capped, local-only. */
data class DeviceHistory(
    val id: String,
    val firstMs: Long,
    val lastMs: Long,
    val hits: Int,
    val best: Int,
    val last: Int,
    val rssiTrail: List<Int>,
    val path: List<LatLon>,
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
    /** Current ground speed in m/s from GPS, or null when unknown. */
    val speedMps: Float? = null,
    /** Altitude in metres from GPS, or null when unknown. */
    val altitudeM: Double? = null,
    /** True while the phone sits inside a dead zone; scanning is suppressed. */
    val inDeadZone: Boolean = false,
    val deadZoneName: String? = null,
    /** Live tally of how many points each source (board address / phone) has seen. */
    val sourceCounts: Map<String, Int> = emptyMap(),
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
    private val phoneSensors: PhoneSensors? = null,
) {
    private val _state = MutableStateFlow(WardriveState())
    val state: StateFlow<WardriveState> = _state.asStateFlow()

    private val _samples = MutableStateFlow<Map<String, WardriveSample>>(emptyMap())
    val samples: StateFlow<Map<String, WardriveSample>> = _samples.asStateFlow()

    // Per-device sighting history for the detail screen. A plain map (not a flow) so
    // recording every re-sighting here never triggers a UI/map recomposition.
    private class Hist(val firstMs: Long) {
        var lastMs = firstMs
        var hits = 0
        var best = -200
        val rssi = ArrayDeque<Int>()
        val path = ArrayDeque<LatLon>()
    }
    private val histories = java.util.concurrent.ConcurrentHashMap<String, Hist>()

    private fun recordHistory(s: WardriveSample) {
        val h = histories.getOrPut(s.id) { Hist(s.timeMs) }
        synchronized(h) {
            h.lastMs = s.timeMs
            h.hits++
            if (s.rssi > h.best) h.best = s.rssi
            h.rssi.addLast(s.rssi); while (h.rssi.size > 60) h.rssi.removeFirst()
            val p = h.path.lastOrNull()
            if (p == null || hypotM(p.lat, p.lon, s.lat, s.lon) > 15.0) {
                h.path.addLast(LatLon(s.lat, s.lon)); while (h.path.size > 40) h.path.removeFirst()
            }
        }
    }

    /** Snapshot of a device's sighting history, or null if never seen. */
    fun history(id: String): DeviceHistory? {
        val h = histories[id] ?: return null
        return synchronized(h) {
            DeviceHistory(id, h.firstMs, h.lastMs, h.hits, h.best, h.rssi.lastOrNull() ?: -200,
                h.rssi.toList(), h.path.toList())
        }
    }

    private fun hypotM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val out = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, out)
        return out[0].toDouble()
    }

    private val _track = MutableStateFlow<List<LatLon>>(emptyList())
    val track: StateFlow<List<LatLon>> = _track.asStateFlow()

    private var locJob: Job? = null
    private var logJob: Job? = null
    private var rescanJob: Job? = null
    private var lastProcessedMs = 0L

    @Volatile private var curLat: Double? = null
    @Volatile private var curLon: Double? = null
    @Volatile private var curAcc: Float = 0f

    // Last fix we accepted onto the track, for rejecting GPS glitches (a bad fix
    // that teleports then snaps back, drawing a long straight line across the map).
    @Volatile private var lastTrackLat: Double? = null
    @Volatile private var lastTrackLon: Double? = null
    @Volatile private var lastTrackMs: Long = 0L

    /** Latest dead-zone list, kept current from the store for synchronous checks. */
    @Volatile private var zones: List<DeadZone> = emptyList()
    /** True while suppressed, so we only broadcast on dead-zone transitions. */
    private var suppressed = false
    /** Whether the phone's own radios are feeding this session. */
    private var phoneOn = false

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

    /**
     * Rejects GPS fixes that can't be real: very poor accuracy, or a jump from the
     * last accepted point that would imply an impossible ground speed. The first
     * fix (and the first after a dead-zone break) always seeds the track.
     */
    private fun isPlausibleFix(loc: Location): Boolean {
        val pLat = lastTrackLat
        val pLon = lastTrackLon
        if (pLat == null || pLon == null) return true           // seed point
        val acc = loc.accuracy
        if (acc > 0f && acc > MAX_ACCURACY_M) return false       // fix too fuzzy to trust
        val out = FloatArray(1)
        Location.distanceBetween(pLat, pLon, loc.latitude, loc.longitude, out)
        val dist = out[0]
        if (dist <= MIN_MOVE_M) return true                      // small move: always fine
        val dtSec = ((loc.time - lastTrackMs).coerceAtLeast(1L)) / 1000.0
        val speed = dist / dtSec
        return speed <= MAX_SPEED_MPS
    }

    @SuppressLint("MissingPermission")
    fun start(mode: WardriveMode, usePhone: Boolean = false) {
        if (_state.value.active) return
        lastProcessedMs = System.currentTimeMillis()
        suppressed = false
        lastTrackLat = null; lastTrackLon = null; lastTrackMs = 0L
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
                    // Break the track here so no line is drawn across the dead zone.
                    lastTrackLat = null; lastTrackLon = null
                    _state.update {
                        it.copy(lastLat = loc.latitude, lastLon = loc.longitude, accuracy = loc.accuracy,
                            speedMps = if (loc.hasSpeed()) loc.speed else it.speedMps,
                            inDeadZone = true, deadZoneName = zone.name)
                    }
                } else {
                    if (suppressed) {
                        suppressed = false
                        fleet.broadcast(scanCommand(_state.value.mode))
                    }
                    if (isPlausibleFix(loc)) {
                        curLat = loc.latitude
                        curLon = loc.longitude
                        curAcc = loc.accuracy
                        lastTrackLat = loc.latitude; lastTrackLon = loc.longitude; lastTrackMs = loc.time
                        _track.update { (it + LatLon(loc.latitude, loc.longitude)).takeLast(5000) }
                        _state.update {
                            it.copy(lastLat = loc.latitude, lastLon = loc.longitude, accuracy = loc.accuracy,
                                speedMps = if (loc.hasSpeed()) loc.speed else it.speedMps,
                                altitudeM = if (loc.hasAltitude()) loc.altitude else it.altitudeM,
                                inDeadZone = false, deadZoneName = null)
                        }
                    } else {
                        // Rejected glitch: keep the puck and geotag point where they were,
                        // so a bad fix neither moves the marker nor draws a straight line.
                        _state.update {
                            it.copy(speedMps = if (loc.hasSpeed()) loc.speed else it.speedMps,
                                inDeadZone = false, deadZoneName = null)
                        }
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
                var changed = false
                val acc = curAcc
                newEntries.forEach { e ->
                    val parsed = parseSample(e.message, lat, lon, acc, e.timeMs) ?: return@forEach
                    if (!matchesMode(parsed.kind, _state.value.mode)) return@forEach
                    if (mergeSample(parsed.copy(sources = setOf(e.address)))) changed = true
                }
                if (changed) recount()
            }
        }

        fleet.broadcast(scanCommand(mode))

        // Some firmware scans once per command; re-arm periodically so a long drive
        // keeps producing pings instead of going quiet after the first sweep.
        rescanJob = scope.launch {
            while (true) {
                delay(10_000)
                if (_state.value.active && !suppressed) fleet.broadcast(scanCommand(_state.value.mode))
            }
        }

        phoneOn = usePhone && phoneSensors != null
        if (phoneOn) startPhone(mode)
    }

    fun stop() {
        if (!_state.value.active) return
        fleet.broadcast(command("stopscan"))
        if (phoneOn) { phoneSensors?.stop(); phoneOn = false }
        locJob?.cancel(); locJob = null
        logJob?.cancel(); logJob = null
        rescanJob?.cancel(); rescanJob = null
        _state.update { it.copy(active = false) }
    }

    /** Adds a sample, or merges a new source into an existing one. Returns true if anything changed. */
    private fun mergeSample(s: WardriveSample): Boolean {
        recordHistory(s)   // every sighting feeds the detail-screen history (no UI emit)
        val existing = _samples.value[s.id]
        if (existing == null) {
            _samples.update { it + (s.id to s) }
            return true
        }
        if (!existing.sources.containsAll(s.sources)) {
            _samples.update { it + (s.id to existing.copy(sources = existing.sources + s.sources)) }
            return true
        }
        return false
    }

    fun setMode(mode: WardriveMode) {
        _state.update { it.copy(mode = mode) }
        // Don't wake the boards back up while we're parked in a dead zone.
        if (_state.value.active && !suppressed) fleet.broadcast(scanCommand(mode))
        // Retarget the phone's radios to match the new mode.
        if (phoneOn) { phoneSensors?.stop(); startPhone(mode) }
    }

    private fun startPhone(mode: WardriveMode) {
        val wifiOn = mode != WardriveMode.BT
        val bleOn = mode != WardriveMode.WIFI
        phoneSensors?.start(wifiOn, bleOn) { hit -> addPhoneHit(hit) }
    }

    /** Geo-tags and dedups a phone-radio observation, honoring dead zones. */
    private fun addPhoneHit(hit: PhoneSensors.Hit) {
        if (!_state.value.active) return
        val lat = curLat ?: return   // null while suppressed in a dead zone
        val lon = curLon ?: return
        if (!matchesMode(hit.kind, _state.value.mode)) return
        val sample = WardriveSample(
            hit.kind, hit.id, hit.label, lat, lon, hit.rssi, System.currentTimeMillis(), curAcc,
            channel = hit.channel, sources = setOf(PHONE_SOURCE),
        )
        if (mergeSample(sample)) recount()
    }

    private fun recount() {
        val vals = _samples.value.values
        val wifi = vals.count { it.kind == SampleKind.AP || it.kind == SampleKind.STATION }
        val bt = vals.count { it.kind == SampleKind.BT }
        val counts = HashMap<String, Int>()
        vals.forEach { s -> s.sources.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        _state.update { it.copy(wifiCount = wifi, btCount = bt, sourceCounts = counts) }
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
            "AP" -> if (f.size >= 4) WardriveSample(SampleKind.AP, f[1], f[0], lat, lon, f[3].toIntOrNull() ?: 0, timeMs, acc, channel = f[2].toIntOrNull() ?: 0) else null
            "STA" -> if (f.size >= 4) WardriveSample(SampleKind.STATION, f[0], f[0], lat, lon, f[3].toIntOrNull() ?: 0, timeMs, acc, channel = f.getOrNull(2)?.toIntOrNull() ?: 0) else null
            "BT" -> if (f.size >= 3) WardriveSample(SampleKind.BT, f[1], f[0], lat, lon, f[2].toIntOrNull() ?: 0, timeMs, acc) else null
            // Sub-GHz activity from a CC1101: DATA:RF:<freqMHz>,<rssi>. There's no device
            // id, so key each ping by frequency + a ~11 m location bucket — a spot that keeps
            // firing merges, while moving drops a new dot, giving a sub-GHz activity heatmap.
            "RF" -> if (f.size >= 2) WardriveSample(
                SampleKind.RF, "rf:${f[0]}:${(lat * 1e4).toInt()},${(lon * 1e4).toInt()}",
                "${f[0]} MHz", lat, lon, f[1].toIntOrNull() ?: 0, timeMs, acc,
            ) else null
            else -> null
        }
    }
}
