package com.cak3d.biscuitmesh.data

import android.content.Context
import com.cak3d.biscuitmesh.sensors.GSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Per-drive g-force series, stored as a small CSV file per drive id (ms,g). */
class GForceStore(context: Context) {
    private val dir: File by lazy { File(context.filesDir, "gforce").apply { mkdirs() } }
    private val cap = 40

    suspend fun save(driveId: String, samples: List<GSample>) = withContext(Dispatchers.IO) {
        if (samples.isEmpty()) return@withContext
        runCatching {
            File(dir, "$driveId.csv").writeText(samples.joinToString("\n") { "${it.ms},${it.g}" })
            // Keep only the most recent [cap] files.
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(cap)?.forEach { it.delete() }
        }
    }

    suspend fun load(driveId: String): List<GSample> = withContext(Dispatchers.IO) {
        runCatching {
            File(dir, "$driveId.csv").readLines().mapNotNull { line ->
                val p = line.split(','); if (p.size < 2) return@mapNotNull null
                val ms = p[0].toLongOrNull() ?: return@mapNotNull null
                val g = p[1].toFloatOrNull() ?: return@mapNotNull null
                GSample(ms, g)
            }
        }.getOrDefault(emptyList())
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
        Unit
    }
}
