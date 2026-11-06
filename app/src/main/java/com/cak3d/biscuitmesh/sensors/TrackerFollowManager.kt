package com.cak3d.biscuitmesh.sensors

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A BLE device that has stayed with you across several places and time — a possible follower. */
data class Follower(
    val mac: String,
    val name: String,
    val places: Int,
    val firstMs: Long,
    val lastMs: Long,
    val spanM: Int,
    val isKnownTracker: Boolean,
    val rssi: Int,
    /** 0–100: how strongly this looks like a follower (more places, more spread, longer, known-tracker). */
    val threat: Int,
)

/**
 * Best-effort "is a tracker following me" detector. It watches BLE devices and flags any
 * with a STABLE address that keeps reappearing across multiple places over time while you
 * move — the signature of an unwanted tracker riding along.
 *
 * Honest limits: AirTags / Find My and most phones rotate their address every ~15 min, so
 * they look like many short-lived devices and won't accumulate here. This catches stable-MAC
 * trackers (many Tiles/SmartTags, some beacons) and any fixed-address device that trails you.
 */
class TrackerFollowManager {

    private class Rec(
        var name: String,
        var rssi: Int,
        val firstMs: Long,
        var lastMs: Long,
        val buckets: LinkedHashMap<String, DoubleArray> = LinkedHashMap(),
    )

    private val recs = HashMap<String, Rec>()
    private val _followers = MutableStateFlow<List<Follower>>(emptyList())
    val followers: StateFlow<List<Follower>> = _followers.asStateFlow()

    private val minPlaces = 3
    private val minSpanMs = 8 * 60 * 1000L
    private val minSpanM = 300f

    fun record(mac: String, name: String, rssi: Int, lat: Double, lon: Double, timeMs: Long) {
        val r = recs.getOrPut(mac) { Rec(name, rssi, timeMs, timeMs) }
        if (name.isNotBlank()) r.name = name
        r.rssi = rssi
        r.lastMs = timeMs
        // ~110 m location bucket, so the same spot doesn't count as many places.
        val key = "${Math.round(lat * 1000)},${Math.round(lon * 1000)}"
        r.buckets.getOrPut(key) { doubleArrayOf(lat, lon) }
        recompute()
    }

    fun clear() {
        recs.clear()
        _followers.value = emptyList()
    }

    private fun recompute() {
        val out = ArrayList<Follower>()
        val res = FloatArray(1)
        for ((mac, r) in recs) {
            if (r.buckets.size < minPlaces) continue
            if (r.lastMs - r.firstMs < minSpanMs) continue
            val first = r.buckets.values.first()
            var maxD = 0f
            for (b in r.buckets.values) {
                Location.distanceBetween(first[0], first[1], b[0], b[1], res)
                if (res[0] > maxD) maxD = res[0]
            }
            if (maxD < minSpanM) continue
            val known = isKnownTracker(r.name)
            val durMin = (r.lastMs - r.firstMs) / 60000.0
            val threat = (r.buckets.size * 8 + maxD / 25 + durMin / 4 + (if (known) 40 else 0))
                .coerceIn(0.0, 100.0).toInt()
            out.add(Follower(mac, r.name, r.buckets.size, r.firstMs, r.lastMs, maxD.toInt(), known, r.rssi, threat))
        }
        _followers.value = out.sortedByDescending { it.threat }
    }

    private fun isKnownTracker(name: String): Boolean {
        val n = name.lowercase()
        return listOf("airtag", "tile", "smarttag", "smart tag", "chipolo", "find my", "findmy",
            "trackr", "pebblebee", "cube tracker", "item").any { n.contains(it) }
    }
}
