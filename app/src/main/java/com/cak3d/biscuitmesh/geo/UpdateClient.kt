package com.cak3d.biscuitmesh.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The latest published app release on GitHub. */
data class AppRelease(
    val tag: String,
    val version: String,     // tag without a leading "v"
    val pageUrl: String,     // the release's GitHub page
    val apkUrl: String?,     // direct .apk asset, when attached
    val notes: String,
)

/**
 * Checks GitHub Releases for a newer build than the one installed. The CI attaches a
 * biscuit-mesh-vX.Y.Z.apk to each release, so a friend who sideloaded the APK can be told when a
 * newer one is out and sent straight to the download. Keyless, public endpoint.
 */
class UpdateClient(private val repo: String = "real-CAK3D/FlyingFlocks") {

    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val j = JSONObject(get("https://api.github.com/repos/$repo/releases/latest"))
            val tag = j.optString("tag_name").ifBlank { return@runCatching null }
            var apk: String? = null
            j.optJSONArray("assets")?.let { assets ->
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apk = a.optString("browser_download_url"); break
                    }
                }
            }
            AppRelease(
                tag = tag,
                version = tag.removePrefix("v").removePrefix("V"),
                pageUrl = j.optString("html_url"),
                apkUrl = apk,
                notes = j.optString("body"),
            )
        }.getOrNull()
    }

    private fun get(u: String): String {
        val c = (URL(u).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "FlockedUp-Updater")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }
}

/** True if [latest] (e.g. "0.187.0") is a higher semantic version than [current]. */
fun isNewerVersion(latest: String, current: String): Boolean {
    fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
        .split('.', '-').map { it.toIntOrNull() ?: 0 }
    val a = parts(latest); val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
