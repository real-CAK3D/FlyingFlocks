package com.cak3d.biscuitmesh.wdg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

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

    /** Validates a key against GET /api/me and returns the display name/status text. */
    suspend fun validate(apiKey: String): Result = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("$base/api/me").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("X-API-Key", apiKey)
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            val code = conn.responseCode
            val body = conn.readAll()
            conn.disconnect()
            if (code in 200..299) parseOk(body) else Result.HttpError(code, body)
        }.getOrElse { Result.Failure(it.message ?: it.javaClass.simpleName) }
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
