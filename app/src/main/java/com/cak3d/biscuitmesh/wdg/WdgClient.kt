package com.cak3d.biscuitmesh.wdg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * One territory cell owned by the operator, as returned by the WDGWars pull API.
 * WDGWars does not publish a response schema, so parsing is tolerant: we accept
 * several common spellings for each field and skip cells without coordinates.
 */
data class WdgCell(
    val id: String,
    val lat: Double,
    val lon: Double,
    val owner: String?,
    val gang: String?,
    val apCount: Int?,
)

/**
 * Thin client for the WDGWars feeder API. We use the CSV upload route
 * (POST /api/upload-csv) — the path the site recommends for firmware/feeders —
 * which takes a WiGLE-1.6 file in a multipart `file` field and an X-API-Key
 * header. The signed-JSON route is only needed for aircraft/mesh data.
 */
class WdgClient(private val base: String = "https://wdgwars.pl") {

    sealed interface Result {
        data class Ok(val accepted: Int?, val merged: Int?, val raw: String) : Result
        data class HttpError(val code: Int, val body: String) : Result
        data class Failure(val message: String) : Result
    }

    sealed interface CellsResult {
        data class Ok(val cells: List<WdgCell>, val raw: String) : CellsResult
        data class HttpError(val code: Int, val body: String) : CellsResult
        data class Failure(val message: String) : CellsResult
    }

    /** Validates a key against GET /api/me and returns the display name/status text. */
    suspend fun validate(apiKey: String): Result = withContext(Dispatchers.IO) {
        get("/api/me", apiKey) { code, body ->
            if (code in 200..299) parseOk(body) else Result.HttpError(code, body)
        }
    }

    /** Fetches the operator's owned territory cells from GET /api/me/cells. */
    suspend fun fetchMyCells(apiKey: String): CellsResult = withContext(Dispatchers.IO) {
        runCatching {
            get("/api/me/cells", apiKey) { code, body ->
                if (code in 200..299) CellsResult.Ok(parseCells(body), body)
                else CellsResult.HttpError(code, body)
            }
        }.getOrElse { CellsResult.Failure(it.message ?: it.javaClass.simpleName) }
    }

    private inline fun <T> get(path: String, apiKey: String, map: (Int, String) -> T): T {
        val conn = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("X-API-Key", apiKey)
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        val code = conn.responseCode
        val body = conn.readAll()
        conn.disconnect()
        return map(code, body)
    }

    private fun parseCells(body: String): List<WdgCell> {
        val arr = asArray(body) ?: return emptyList()
        val out = ArrayList<WdgCell>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val lat = firstDouble(o, "lat", "latitude", "rlat", "center_lat", "clat", "y")
            val lon = firstDouble(o, "lon", "lng", "longitude", "rlng", "center_lon", "clon", "x")
            if (lat == null || lon == null) continue
            out.add(
                WdgCell(
                    id = firstString(o, "cell_key", "id", "cell", "key") ?: "$lat,$lon",
                    lat = lat,
                    lon = lon,
                    owner = firstString(o, "owner", "holder", "owner_name", "player"),
                    gang = firstString(o, "gang", "gang_id", "team", "team_id"),
                    apCount = firstInt(o, "my_aps", "aps", "ap_count", "count"),
                ),
            )
        }
        return out
    }

    /** Accepts either a bare JSON array or an object wrapping the array in a common key. */
    private fun asArray(body: String): JSONArray? {
        runCatching { return JSONArray(body) }
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return null
        for (k in listOf("cells", "territories", "data", "results", "items")) {
            o.optJSONArray(k)?.let { return it }
        }
        return null
    }

    private fun firstDouble(o: JSONObject, vararg keys: String): Double? {
        for (k in keys) if (o.has(k) && !o.isNull(k)) {
            val d = o.optDouble(k, Double.NaN)
            if (!d.isNaN()) return d
        }
        return null
    }

    private fun firstString(o: JSONObject, vararg keys: String): String? {
        for (k in keys) if (o.has(k) && !o.isNull(k)) {
            val s = o.optString(k, "")
            if (s.isNotEmpty()) return s
        }
        return null
    }

    private fun firstInt(o: JSONObject, vararg keys: String): Int? {
        for (k in keys) if (o.has(k) && !o.isNull(k)) {
            val v = o.optInt(k, Int.MIN_VALUE)
            if (v != Int.MIN_VALUE) return v
        }
        return null
    }

    /** Uploads a WiGLE CSV body to POST /api/upload-csv. */
    suspend fun uploadCsv(apiKey: String, csv: String, fileName: String = "biscuit-mesh.csv"): Result =
        withContext(Dispatchers.IO) {
            runCatching {
                val boundary = "----biscuitmesh${UUID.randomUUID().toString().replace("-", "")}"
                val conn = (URL("$base/api/upload-csv").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("X-API-Key", apiKey)
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    connectTimeout = 20_000
                    readTimeout = 120_000
                }
                DataOutputStream(conn.outputStream).use { out ->
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
                    out.writeBytes("Content-Type: text/csv\r\n\r\n")
                    out.write(csv.toByteArray(Charsets.UTF_8))
                    out.writeBytes("\r\n--$boundary--\r\n")
                    out.flush()
                }
                val code = conn.responseCode
                val body = conn.readAll()
                conn.disconnect()
                if (code in 200..299) parseOk(body) else Result.HttpError(code, body)
            }.getOrElse { Result.Failure(it.message ?: it.javaClass.simpleName) }
        }

    private fun parseOk(body: String): Result.Ok {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val accepted = json?.let {
            when {
                it.has("accepted") -> it.optInt("accepted")
                it.has("new_networks") -> it.optInt("new_networks")
                it.has("networks") -> it.optInt("networks")
                else -> null
            }
        }
        val merged = json?.takeIf { it.has("merged_samples") }?.optInt("merged_samples")
        return Result.Ok(accepted, merged, body)
    }

    private fun HttpURLConnection.readAll(): String {
        val stream = runCatching { inputStream }.getOrElse { errorStream }
        return stream?.bufferedReader()?.use { it.readText() } ?: ""
    }
}
