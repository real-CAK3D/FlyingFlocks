package com.cak3d.biscuitmesh.progress

import com.cak3d.biscuitmesh.geo.Hazard
import com.cak3d.biscuitmesh.geo.HazardKind
import com.cak3d.biscuitmesh.geo.HazardManager
import com.cak3d.biscuitmesh.wardrive.SampleKind
import com.cak3d.biscuitmesh.wardrive.WardriveManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * The levelling engine. Every unique Wi-Fi/BLE hit and every discovered camera
 * scores XP exactly once (ever); the rank is your level for all that XP together,
 * so Wi-Fi/BT volume drives it and each camera is a one-time boost. Persistence is
 * debounced so a busy drive doesn't hammer disk.
 */
class ProgressManager(
    private val scope: CoroutineScope,
    private val store: ProgressStore,
    wardrive: WardriveManager?,
    hazards: HazardManager?,
    rareBirds: com.cak3d.biscuitmesh.hunts.RareBirdManager? = null,
) {
    private val xp = HashMap<ProgressTrack, Int>()
    private val count = HashMap<ProgressTrack, Int>()
    private val awarded = LinkedHashSet<String>()

    /** Each captured rare bird is worth a full extra rank. */
    @Volatile private var bonusLevels = 0

    private val _state = MutableStateFlow(rebuild())
    val state: StateFlow<Progress> = _state.asStateFlow()

    private val dirty = MutableStateFlow(0L)

    init {
        scope.launch {
            val d = store.load()
            xp.putAll(d.xp); count.putAll(d.count); awarded.addAll(d.awarded)
            _state.value = rebuild()

            wardrive?.let { wd ->
                // Samples emit on every merge; awardSamples re-scans the whole set, so
                // sample() at ~2s caps that to one pass per window instead of per ping
                // (turns an O(n²)-over-a-drive scan into a bounded, periodic one).
                launch { wd.samples.sample(2000).collect { map -> awardSamples(map.values) } }
            }
            hazards?.let { hz ->
                launch { hz.hazards.collect { list -> awardHazards(list) } }
            }
            rareBirds?.let { rb ->
                launch {
                    rb.birds.collect { birds ->
                        val captured = birds.count { it.captured }
                        if (captured != bonusLevels) { bonusLevels = captured; commit() }
                    }
                }
            }
        }
        // Debounced persistence: coalesce bursts of awards into one write.
        scope.launch {
            dirty.collectLatest {
                if (it == 0L) return@collectLatest
                delay(2500)
                store.save(ProgressData(HashMap(xp), HashMap(count), LinkedHashSet(awarded)))
            }
        }
    }

    private fun awardSamples(samples: Collection<com.cak3d.biscuitmesh.wardrive.WardriveSample>) {
        var changed = false
        samples.forEach { s ->
            val track = when (s.kind) {
                SampleKind.AP, SampleKind.STATION -> wifiTrack(s.is5GHz)
                SampleKind.BT -> ProgressTrack.BLUETOOTH
                SampleKind.RF -> return@forEach   // sub-GHz band activity isn't a levelling find
            }
            val bonus = if (s.label.isBlank()) 7 else 0   // hidden networks / unnamed beacons are juicier
            if (award(track, "s:${s.id}", bonus)) changed = true
        }
        if (changed) commit()
    }

    private fun awardHazards(list: List<Hazard>) {
        var changed = false
        list.forEach { h ->
            val track = when (h.kind) {
                HazardKind.FLOCK, HazardKind.CAMERA -> ProgressTrack.FLOCK
                HazardKind.SPEED_CAMERA -> ProgressTrack.SPEED_CAM
                HazardKind.RED_LIGHT -> ProgressTrack.REDLIGHT
            }
            if (award(track, "h:${h.id}", 0)) changed = true
        }
        if (changed) commit()
    }

    /** Standalone phone scans (WiFi/BT tabs) feed the same tracks. */
    fun recordPhoneHit(kind: SampleKind, id: String, label: String, channel: Int = 0) {
        val track = when (kind) {
            SampleKind.AP, SampleKind.STATION -> wifiTrack(channel >= 32)
            SampleKind.BT -> ProgressTrack.BLUETOOTH
            SampleKind.RF -> return   // phone radios never emit sub-GHz
        }
        if (award(track, "s:$id", if (label.isBlank()) 7 else 0)) commit()
    }

    /** 5 GHz networks (channel >= 32) grind the rarer WIFI_5 track; everything else 2.4 GHz. */
    private fun wifiTrack(is5GHz: Boolean) = if (is5GHz) ProgressTrack.WIFI_5 else ProgressTrack.WIFI_24

    private fun award(track: ProgressTrack, id: String, bonus: Int): Boolean {
        if (!awarded.add(id)) return false
        if (awarded.size > store.awardedCap) {
            val it = awarded.iterator()
            if (it.hasNext()) { it.next(); it.remove() }
        }
        xp[track] = (xp[track] ?: 0) + track.xpPerFind + bonus
        count[track] = (count[track] ?: 0) + 1
        return true
    }

    private fun commit() {
        _state.value = rebuild()
        dirty.value = System.currentTimeMillis()
    }

    private fun rebuild(): Progress =
        progressFrom(ProgressTrack.entries.map { TrackState(it, xp[it] ?: 0, count[it] ?: 0) }, bonusLevels)

    /** Camera count so far; drives how far afield the "find cameras" sweep reaches. */
    fun cameraFinds(): Int =
        (count[ProgressTrack.FLOCK] ?: 0) + (count[ProgressTrack.SPEED_CAM] ?: 0) + (count[ProgressTrack.REDLIGHT] ?: 0)

    /** Widening reach: each camera you bag extends the next sweep, up to a cap. */
    fun cameraScanRadiusM(): Int = (4000 + cameraFinds() * 250).coerceAtMost(12000)
}
