package com.cak3d.biscuitmesh.crew

import com.cak3d.biscuitmesh.data.SettingsStore
import com.cak3d.biscuitmesh.location.LocationProvider
import com.cak3d.biscuitmesh.net.SbHttpException
import com.cak3d.biscuitmesh.net.SbSession
import com.cak3d.biscuitmesh.net.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** A wing (another bird) whose live position you're allowed to see. */
data class CrewMember(val userId: String, val lat: Double, val lon: Double, val updatedAt: String, val handle: String? = null)

/** Everyone's all-time totals across the whole flock. */
data class FlockTotals(val wings: Int, val wifi: Long, val bt: Long, val cameras: Long, val drives: Long, val km: Double)

/** A claimed map cell: who owns it, how strongly, and whether it's yours. */
data class TerritoryCell(val cellId: String, val lat: Double, val lon: Double, val owner: String?, val strength: Int, val mine: Boolean)

/** Your territory standing across the whole flock. */
data class TerritoryStanding(val owned: Int, val total: Int, val strength: Int)

/** One wing's standing on the flock leaderboard. */
data class LeaderRow(val rank: Int, val handle: String, val score: Long, val wifi: Long, val km: Double, val me: Boolean)

/** The flock leaderboard: the top wings, plus where you sit. */
data class Leaderboard(val total: Int, val top: List<LeaderRow>, val me: LeaderRow?)

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
    private val deadZones: com.cak3d.biscuitmesh.data.DeadZonesStore,
) {
    @Volatile private var session: SbSession? = null
    @Volatile private var activated = false
    @Volatile private var zones: List<com.cak3d.biscuitmesh.data.DeadZone> = emptyList()
    private val authMutex = Mutex()

    private val _flock = MutableStateFlow<List<CrewMember>>(emptyList())
    val flock: StateFlow<List<CrewMember>> = _flock.asStateFlow()
    private val _live = MutableStateFlow(0)
    val live: StateFlow<Int> = _live.asStateFlow()
    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online.asStateFlow()
    private val _totals = MutableStateFlow<FlockTotals?>(null)
    val totals: StateFlow<FlockTotals?> = _totals.asStateFlow()
    private val _board = MutableStateFlow<Leaderboard?>(null)
    val board: StateFlow<Leaderboard?> = _board.asStateFlow()
    private val _handle = MutableStateFlow<String?>(null)
    val handle: StateFlow<String?> = _handle.asStateFlow()

    val myUserId: String? get() = session?.userId

    /** Begin signing in, polling, and (if enabled) sharing. Idempotent. */
    @Synchronized fun activate() {
        if (activated) return
        activated = true
        scope.launch { deadZones.zones.collect { zones = it } }
        scope.launch { pollLoop() }
        scope.launch { shareLoop() }
    }

    /** True if a point sits inside any dead zone — used to keep your live pin out of home/work. */
    private fun inDeadZone(lat: Double, lon: Double): Boolean {
        val zs = zones
        if (zs.isEmpty()) return false
        val out = FloatArray(1)
        return zs.any {
            android.location.Location.distanceBetween(lat, lon, it.lat, it.lon, out)
            out[0] <= it.radiusM
        }
    }

    private suspend fun pollLoop() {
        var failures = 0
        while (true) {
            val ok = runCatching {
                ensure(); _online.value = true
                touch()
                _live.value = liveCount()
                _flock.value = tracked()
                _totals.value = globalTotals()
                _board.value = leaderboard()
            }.isSuccess
            if (ok) {
                failures = 0
                delay(6000)
            } else {
                _online.value = false
                // Back off on sustained failure (offline) instead of hammering every 6s.
                failures = (failures + 1).coerceAtMost(5)
                delay(6000L * (1 shl (failures - 1)))  // 6s,12s,24s,48s,96s cap
            }
        }
    }

    private suspend fun shareLoop() {
        settings.crewShare.collectLatest { on ->
            if (on) {
                var hiddenForZone = false
                runCatching {
                    location.locationUpdates(5000).collect { loc ->
                        // Never broadcast your position from inside a dead zone (home/work).
                        // Drop the published row once on entry so tails can't see you there.
                        if (inDeadZone(loc.latitude, loc.longitude)) {
                            if (!hiddenForZone) { runCatching { stopSharing() }; hiddenForZone = true }
                            return@collect
                        }
                        hiddenForZone = false
                        runCatching {
                            publishLocation(loc.latitude, loc.longitude,
                                if (loc.hasBearing()) loc.bearing else null, if (loc.hasSpeed()) loc.speed else null)
                        }
                    }
                }
            } else runCatching { stopSharing() }
        }
    }

    suspend fun ensure(): SbSession = authMutex.withLock {
        if (_handle.value == null) runCatching { settings.flockHandle() }.getOrNull()
            ?.takeIf { it.isNotBlank() }?.let { _handle.value = it }

        val cur = session ?: settings.sbSession().takeIf { it.isNotEmpty() }?.let { runCatching { fromJson(it) }.getOrNull() }
        // A still-valid session needs no network — this short-circuit stops the ~5-6 redundant
        // refresh/upsert/DataStore writes that otherwise fired every poll cycle.
        if (cur != null && cur.expiresAtMs - System.currentTimeMillis() >= 60_000) {
            if (session == null) session = cur
            return@withLock cur
        }

        val fresh = when {
            cur == null -> SupabaseClient.signUpAnonymous()
            else -> try {
                SupabaseClient.refresh(cur.refreshToken)
            } catch (e: SbHttpException) {
                // The refresh token is genuinely dead (4xx) → a new identity is the only option.
                // A 5xx/network error is transient, so rethrow and keep the identity for next time.
                if (e.code in 400..403) SupabaseClient.signUpAnonymous() else throw e
            }
        }
        session = fresh
        settings.setSbSession(toJson(fresh))
        runCatching {
            val prof = JSONObject().put("id", fresh.userId)
            _handle.value?.let { prof.put("handle", it) }
            SupabaseClient.upsert("ff_profiles", prof.toString(), fresh.accessToken, onConflict = "id")
        }
        fresh
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

    // --- Territory ---------------------------------------------------------
    private val cellDeg = 0.004  // ~400m grid cells
    private fun cellId(lat: Double, lon: Double) =
        "${kotlin.math.floor(lat / cellDeg).toInt()}_${kotlin.math.floor(lon / cellDeg).toInt()}"
    private fun cellCenter(lat: Double, lon: Double) =
        (kotlin.math.floor(lat / cellDeg) * cellDeg + cellDeg / 2) to (kotlin.math.floor(lon / cellDeg) * cellDeg + cellDeg / 2)

    /** Claim the cell at this location for your flock. Skips dead zones. Returns true if it's now yours. */
    suspend fun claimCell(lat: Double, lon: Double): Boolean {
        if (inDeadZone(lat, lon)) return false
        val s = ensure()
        val (cl, co) = cellCenter(lat, lon)
        val body = JSONObject().put("p_cell", cellId(lat, lon)).put("p_lat", cl).put("p_lon", co)
        val r = JSONObject(SupabaseClient.rpc("ff_claim_cell", body.toString(), s.accessToken))
        return r.optBoolean("mine")
    }

    suspend fun cellsNear(lat: Double, lon: Double): List<TerritoryCell> {
        val s = ensure()
        val body = JSONObject().put("p_lat", lat).put("p_lon", lon)
        val arr = JSONArray(SupabaseClient.rpc("ff_cells_near", body.toString(), s.accessToken))
        val out = ArrayList<TerritoryCell>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(TerritoryCell(o.optString("cell"), o.optDouble("lat"), o.optDouble("lon"),
                o.optString("owner").takeIf { it.isNotEmpty() && it != "null" }, o.optInt("strength"), o.optBoolean("mine")))
        }
        return out
    }

    suspend fun territoryMine(): TerritoryStanding {
        val s = ensure()
        val o = JSONObject(SupabaseClient.rpc("ff_territory_mine", "{}", s.accessToken))
        return TerritoryStanding(o.optInt("owned"), o.optInt("total"), o.optInt("strength"))
    }

    suspend fun pushStats(wifi: Long, bt: Long, cameras: Long, drives: Long, km: Double) {
        val s = ensure()
        val body = JSONObject().put("p_wifi", wifi).put("p_bt", bt).put("p_cameras", cameras)
            .put("p_drives", drives).put("p_km", km)
        runCatching { SupabaseClient.rpc("ff_push_stats", body.toString(), s.accessToken) }
    }

    /** Set this wing's public handle (what other wings and the leaderboard show). */
    suspend fun setHandle(name: String) {
        val h = name.trim().take(24)
        val s = ensure()
        SupabaseClient.upsert("ff_profiles", JSONObject().put("id", s.userId).put("handle", h).toString(),
            s.accessToken, onConflict = "id")
        settings.setFlockHandle(h)
        _handle.value = h
    }

    private suspend fun leaderboard(): Leaderboard {
        val s = ensure()
        val o = JSONObject(SupabaseClient.rpc("ff_leaderboard", "{}", s.accessToken))
        fun row(j: JSONObject) = LeaderRow(j.optInt("rank"), j.optString("handle"), j.optLong("score"),
            j.optLong("wifi"), j.optDouble("km"), j.optBoolean("me"))
        val top = ArrayList<LeaderRow>()
        val arr = o.optJSONArray("top")
        if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { top.add(row(it)) }
        val me = o.optJSONObject("me")?.let { row(it) }
        return Leaderboard(o.optInt("total"), top, me)
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
        if (out.isEmpty()) return out
        // Fold in each wing's public handle, so the map/list name them instead of showing a device id.
        val handles = runCatching {
            val ids = out.joinToString(",") { it.userId }
            val pr = JSONArray(SupabaseClient.select("ff_profiles", "select=id,handle&id=in.($ids)", s.accessToken))
            HashMap<String, String>().apply {
                for (i in 0 until pr.length()) {
                    val o = pr.optJSONObject(i) ?: continue
                    val h = o.optString("handle")
                    if (h.isNotBlank()) put(o.optString("id"), h)
                }
            }
        }.getOrNull() ?: return out
        return out.map { it.copy(handle = handles[it.userId]) }
    }

    private fun toJson(s: SbSession): String = JSONObject()
        .put("at", s.accessToken).put("rt", s.refreshToken).put("uid", s.userId).put("exp", s.expiresAtMs).toString()

    private fun fromJson(j: String): SbSession {
        val o = JSONObject(j)
        return SbSession(o.optString("at"), o.optString("rt"), o.optString("uid"), o.optLong("exp"))
    }
}
