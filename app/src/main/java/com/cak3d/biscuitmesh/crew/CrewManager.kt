package com.cak3d.biscuitmesh.crew

import com.cak3d.biscuitmesh.data.SettingsStore
import com.cak3d.biscuitmesh.net.SbSession
import com.cak3d.biscuitmesh.net.SupabaseClient
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** A crew member whose live location you're allowed to see. */
data class CrewMember(val userId: String, val lat: Double, val lon: Double, val updatedAt: String)

/**
 * The online "crew" layer on theGarden Supabase: an anonymous identity per install,
 * one-time share codes to let someone track you, and live location publish/read.
 */
class CrewManager(private val settings: SettingsStore) {

    @Volatile private var session: SbSession? = null
    val myUserId: String? get() = session?.userId

    /** Make sure we have a valid session (sign in anonymously / refresh), and a profile row. */
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

    /** Claim someone's code so they can track you. Returns null on success, else an error message. */
    suspend fun claim(code: String): String? {
        val s = ensure()
        val r = SupabaseClient.rpc("ff_claim_code", JSONObject().put("p_code", code.trim()).toString(), s.accessToken)
        val o = JSONObject(r)
        return if (o.optBoolean("ok")) null else o.optString("error", "Couldn't claim that code")
    }

    suspend fun liveCount(): Int {
        val s = ensure()
        return SupabaseClient.rpc("ff_live_count", "{}", s.accessToken).trim().toIntOrNull() ?: 0
    }

    suspend fun touch() {
        val s = session ?: return
        runCatching { SupabaseClient.rpc("ff_touch", "{}", s.accessToken) }
    }

    suspend fun publishLocation(lat: Double, lon: Double, heading: Float?, speed: Float?) {
        val s = ensure()
        val body = JSONObject()
            .put("device", s.userId).put("lat", lat).put("lon", lon)
            .put("updated_at", Instant.now().toString())
        if (heading != null) body.put("heading", heading.toDouble())
        if (speed != null) body.put("speed", speed.toDouble())
        SupabaseClient.upsert("ff_locations", body.toString(), s.accessToken, onConflict = "device")
    }

    suspend fun stopSharing() {
        val s = session ?: return
        runCatching { SupabaseClient.delete("ff_locations", "device=eq.${SupabaseClient.enc(s.userId)}", s.accessToken) }
    }

    /** People you're tracking (RLS returns your own row + your targets; we drop self). */
    suspend fun tracked(): List<CrewMember> {
        val s = ensure()
        val body = SupabaseClient.select("ff_locations", "select=device,lat,lon,updated_at", s.accessToken)
        val arr = JSONArray(body)
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
