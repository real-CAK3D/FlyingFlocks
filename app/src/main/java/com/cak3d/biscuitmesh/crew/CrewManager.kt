package com.cak3d.biscuitmesh.crew

import com.cak3d.biscuitmesh.data.SettingsStore
import com.cak3d.biscuitmesh.location.LocationProvider
import com.cak3d.biscuitmesh.net.SbSession
import com.cak3d.biscuitmesh.net.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** A wing (another bird) whose live position you're allowed to see. */
data class CrewMember(val userId: String, val lat: Double, val lon: Double, val updatedAt: String)

/** Everyone's all-time totals across the whole flock. */
data class FlockTotals(val wings: Int, val wifi: Long, val bt: Long, val cameras: Long, val drives: Long, val km: Double)

/**
 * The "Flock" online layer on theGarden Supabase: an anonymous identity per install, one-time
 * band codes to let a wing tail you, live positions, operator count and all-time totals.
 *
 * Self-running once [activate]d: a 6s poll keeps the flock + counts fresh app-wide (so the map
 * can show tails even when the Flock screen is closed), and location publishes in the background
 * while "share my position" is on.
 */
class CrewManager(
    private val scope: CoroutineScope,
    private val settings: SettingsStore,
    private val location: LocationProvider,
) {
    @Volatile private var session: SbSession? = null
    @Volatile private var activated = false

    private val _flock = MutableStateFlow<List<CrewMember>>(emptyList())
    val flock: StateFlow<List<CrewMember>> = _flock.asStateFlow()
    private val _live = MutableStateFlow(0)
    val live: StateFlow<Int> = _live.asStateFlow()
    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online.asStateFlow()
    private val _totals = MutableStateFlow<FlockTotals?>(null)
    val totals: StateFlow<FlockTotals?> = _totals.asStateFlow()

    val myUserId: String? get() = session?.userId

    /** Begin signing in, polling, and (if enabled) sharing. Idempotent. */
    @Synchronized fun activate() {
        if (activated) return
        activated = true
        scope.launch { pollLoop() }
        scope.launch { shareLoop() }
    }

    private suspend fun pollLoop() {
        while (true) {
            runCatching {
                ensure(); _online.value = true
                touch()
                _live.value = liveCount()
                _flock.value = tracked()
                _totals.value = globalTotals()
            }.onFailure { _online.value = false }
            delay(6000)
        }
    }

    private suspend fun shareLoop() {
        settings.crewShare.collectLatest { on ->
            if (on) {
                runCatching {
                    location.locationUpdates(5000).collect { loc ->
                        runCatching {
                            publishLocation(loc.latitude, loc.longitude,
                                if (loc.hasBearing()) loc.bearing else null, if (loc.hasSpeed()) loc.speed else null)
                        }
                    }
                }
            } else runCatching { stopSharing() }
        }
    }

    suspend fun ensure(): SbSession {
        var s = session ?: settings.sbSession().takeIf { it.isNotEmpty() }?.let { runCatching { fromJson(it) }.getOrNull() }
        s = when {
            s == null -> SupabaseClient.signUpAnonymous()
            s.expiresAtMs - System.currentTimeMillis() < 60_000 ->
                runCatching { SupabaseClient.refresh(s!!.refreshToken) }.getOrElse { SupabaseClient.signUpAnonymous() }
            else -> s
        }
        session = s
        settings.setSbSession(toJson(s))
        runCatching {
            SupabaseClient.upsert("ff_profiles", JSONObject().put("id", s.userId).toString(), s.accessToken, onConflict = "id")
        }
        return s
    }

    suspend fun newCode(): String {
        val s = ensure()
        return SupabaseClient.rpc("ff_new_code", "{}", s.accessToken).trim().trim('"')
    }

    /** Claim someone's band so they can tail you. Returns null on success, else an error. */
    suspend fun claim(code: String): String? {
        val s = ensure()
        val r = SupabaseClient.rpc("ff_claim_code", JSONObject().put("p_code", code.trim()).toString(), s.accessToken)
        val o = JSONObject(r)
        return if (o.optBoolean("ok")) null else o.optString("error", "Couldn't claim that band")
    }

    suspend fun pushStats(wifi: Long, bt: Long, cameras: Long, drives: Long, km: Double) {
        val s = ensure()
        val body = JSONObject().put("p_wifi", wifi).put("p_bt", bt).put("p_cameras", cameras)
            .put("p_drives", drives).put("p_km", km)
        runCatching { SupabaseClient.rpc("ff_push_stats", body.toString(), s.accessToken) }
    }

    private suspend fun liveCount(): Int {
        val s = ensure()
        return SupabaseClient.rpc("ff_live_count", "{}", s.accessToken).trim().toIntOrNull() ?: 0
    }

    private suspend fun touch() {
        val s = session ?: return
        runCatching { SupabaseClient.rpc("ff_touch", "{}", s.accessToken) }
    }

    private suspend fun globalTotals(): FlockTotals {
        val s = ensure()
        val o = JSONObject(SupabaseClient.rpc("ff_global_totals", "{}", s.accessToken))
        return FlockTotals(o.optInt("wings"), o.optLong("wifi"), o.optLong("bt"), o.optLong("cameras"),
            o.optLong("drives"), o.optDouble("km"))
    }

    private suspend fun publishLocation(lat: Double, lon: Double, heading: Float?, speed: Float?) {
        val s = ensure()
        val body = JSONObject().put("device", s.userId).put("lat", lat).put("lon", lon)
            .put("updated_at", Instant.now().toString())
        if (heading != null) body.put("heading", heading.toDouble())
        if (speed != null) body.put("speed", speed.toDouble())
        SupabaseClient.upsert("ff_locations", body.toString(), s.accessToken, onConflict = "device")
    }

    private suspend fun stopSharing() {
        val s = session ?: return
        runCatching { SupabaseClient.delete("ff_locations", "device=eq.${SupabaseClient.enc(s.userId)}", s.accessToken) }
    }

    private suspend fun tracked(): List<CrewMember> {
        val s = ensure()
        val arr = JSONArray(SupabaseClient.select("ff_locations", "select=device,lat,lon,updated_at", s.accessToken))
        val out = ArrayList<CrewMember>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val dev = o.optString("device")
            if (dev == s.userId || dev.isEmpty()) continue
            out.add(CrewMember(dev, o.optDouble("lat"), o.optDouble("lon"), o.optString("updated_at")))
        }
        return out
    }

    private fun toJson(s: SbSession): String = JSONObject()
        .put("at", s.accessToken).put("rt", s.refreshToken).put("uid", s.userId).put("exp", s.expiresAtMs).toString()

    private fun fromJson(j: String): SbSession {
        val o = JSONObject(j)
        return SbSession(o.optString("at"), o.optString("rt"), o.optString("uid"), o.optLong("exp"))
    }
}
