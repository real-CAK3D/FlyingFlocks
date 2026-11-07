package com.cak3d.biscuitmesh.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A non-2xx HTTP response from Supabase. `code` lets callers tell auth failures (4xx) from server errors. */
class SbHttpException(val code: Int, message: String) : RuntimeException(message)

/** A signed-in (anonymous) Supabase session. */
data class SbSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val expiresAtMs: Long,
)

/**
 * Minimal Supabase REST/Auth client for the Flying Flocks online backend (theGarden),
 * built on plain HttpURLConnection to match the rest of the app. Publishable key only.
 */
object SupabaseClient {
    const val URL_BASE = "https://rmshsswlodfvdffnlqdy.supabase.co"
    const val KEY = "sb_publishable__aWybeZVozOcTHsjHq9jGg_HrQXqSpU"

    suspend fun signUpAnonymous(): SbSession = withContext(Dispatchers.IO) {
        parseSession(post("$URL_BASE/auth/v1/signup", "{}", null))
    }

    suspend fun refresh(refreshToken: String): SbSession = withContext(Dispatchers.IO) {
        parseSession(post("$URL_BASE/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", refreshToken).toString(), null))
    }

    /** Call a Postgres function; returns its raw JSON result. */
    suspend fun rpc(fn: String, body: String, token: String): String = withContext(Dispatchers.IO) {
        post("$URL_BASE/rest/v1/rpc/$fn", body, token)
    }

    /** Insert a row (no conflict handling); returns nothing. */
    suspend fun insert(table: String, body: String, token: String): Unit = withContext(Dispatchers.IO) {
        post("$URL_BASE/rest/v1/$table", body, token, prefer = "return=minimal")
    }

    /** Upsert a row (merge on conflict). */
    suspend fun upsert(table: String, body: String, token: String, onConflict: String? = null): Unit = withContext(Dispatchers.IO) {
        val u = "$URL_BASE/rest/v1/$table" + (if (onConflict != null) "?on_conflict=$onConflict" else "")
        post(u, body, token, prefer = "resolution=merge-duplicates,return=minimal")
    }

    suspend fun delete(table: String, query: String, token: String): Unit = withContext(Dispatchers.IO) {
        req("DELETE", "$URL_BASE/rest/v1/$table?$query", null, token, null)
    }

    /** Select rows; `query` is a PostgREST query string (e.g. "select=*"). */
    suspend fun select(table: String, query: String, token: String): String = withContext(Dispatchers.IO) {
        req("GET", "$URL_BASE/rest/v1/$table?$query", null, token, null)
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun post(u: String, body: String, token: String?, prefer: String? = null): String =
        req("POST", u, body, token, prefer)

    private fun req(method: String, u: String, body: String?, token: String?, prefer: String?): String {
        val c = (URL(u).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("apikey", KEY)
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (prefer != null) setRequestProperty("Prefer", prefer)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        if (body != null) c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        if (code !in 200..299) throw SbHttpException(code, "Supabase HTTP $code: ${text.take(200)}")
        return text
    }

    private fun parseSession(body: String): SbSession {
        val o = JSONObject(body)
        val at = o.optString("access_token")
        if (at.isEmpty()) throw RuntimeException("no session: ${body.take(160)}")
        val uid = o.optJSONObject("user")?.optString("id") ?: ""
        val expIn = o.optLong("expires_in", 3600)
        return SbSession(at, o.optString("refresh_token"), uid, System.currentTimeMillis() + expIn * 1000)
    }
}
