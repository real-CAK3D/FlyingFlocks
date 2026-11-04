package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import kotlin.math.cos

/** Zoom depth presets: how fine the downloaded map goes (more detail = many more tiles). */
enum class OfflineDetail(val label: String, val maxZoom: Int) {
    STANDARD("Standard", 16),
    DETAILED("Detailed", 18),
}

/** A square bounding box about [radiusKm] in each direction from a center point. */
fun offlineBoundingBox(lat: Double, lon: Double, radiusKm: Double): BoundingBox {
    val dLat = radiusKm / 111.0
    val dLon = radiusKm / (111.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
    return BoundingBox(lat + dLat, lon + dLon, lat - dLat, lon - dLon)
}

private const val MIN_ZOOM = 10
private const val TILE_CAP = 150_000           // refuse absurd downloads
private const val AVG_TILE_BYTES = 20_000L     // raster tiles average ~20 KB

/**
 * Downloads every map tile for an area around the current map centre, across a range
 * of zoom levels, into osmdroid's on-disk cache — so the map then draws with no
 * connection. Caches the *current* map style's tiles (switch style first to grab a
 * different one). Shows a live tile/size estimate before committing and a progress
 * bar while running.
 */
@Composable
fun OfflineMapDialog(mapView: MapView, styleLabel: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cacheManager = remember { CacheManager(mapView) }

    var radiusKm by remember { mutableStateOf(10) }
    var detail by remember { mutableStateOf(OfflineDetail.STANDARD) }
    var progress by remember { mutableStateOf<Float?>(null) }   // null = idle, else 0..1
    var status by remember { mutableStateOf<String?>(null) }

    val center = mapView.mapCenter
    val srcMax = mapView.tileProvider.tileSource.maximumZoomLevel
    val zMax = minOf(detail.maxZoom, srcMax)
    val bb = remember(radiusKm, center.latitude, center.longitude) {
        offlineBoundingBox(center.latitude, center.longitude, radiusKm.toDouble())
    }
    val tiles = remember(radiusKm, detail) {
        runCatching { cacheManager.possibleTilesInArea(bb, MIN_ZOOM, zMax) }.getOrDefault(0)
    }
    val mb = tiles * AVG_TILE_BYTES / (1024 * 1024)
    val tooBig = tiles > TILE_CAP
    val running = progress != null

    fun start() {
        // Keep the downloaded tiles from being evicted by the default cache trim.
        Configuration.getInstance().apply {
            tileFileSystemCacheMaxBytes = 4L * 1024 * 1024 * 1024
            tileFileSystemCacheTrimBytes = 36L * 100 * 1024 * 1024
        }
        progress = 0f
        status = "Starting…"
        runCatching {
            cacheManager.downloadAreaAsync(context, bb, MIN_ZOOM, zMax, object : CacheManager.CacheManagerCallback {
                override fun onTaskComplete() { progress = null; status = "Saved $tiles tiles — available offline" }
                override fun onTaskFailed(errors: Int) { progress = null; status = "Finished with $errors failed tiles" }
                override fun updateProgress(p: Int, currentZoom: Int, zMin: Int, zMax: Int) {
                    progress = if (tiles > 0) (p.toFloat() / tiles).coerceIn(0f, 1f) else 0f
                    status = "Downloading… $p / $tiles"
                }
                override fun downloadStarted() {}
                override fun setPossibleTilesInArea(total: Int) {}
            })
        }.onFailure { progress = null; status = "Couldn't start: ${it.message}" }
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text("Offline map") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Save the \"$styleLabel\" map around where the map is centred, so it works " +
                        "with no signal. Pan/zoom the map to the area first.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Area", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 25).forEach { r ->
                        FilterChip(selected = radiusKm == r, onClick = { radiusKm = r },
                            enabled = !running, label = { Text("${r} km") })
                    }
                }
                Text("Detail", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OfflineDetail.entries.forEach { d ->
                        FilterChip(selected = detail == d, onClick = { detail = d },
                            enabled = !running, label = { Text(d.label) })
                    }
                }
                Text(
                    if (tooBig) "~$tiles tiles — too large, pick a smaller area or Standard detail"
                    else "~$tiles tiles · ~$mb MB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (tooBig) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
            }
        },
        confirmButton = {
            Button(onClick = { start() }, enabled = !running && !tooBig && tiles > 0) {
                Text(if (running) "Downloading…" else "Download")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = !running,
                    onClick = {
                        runCatching { cacheManager.cleanAreaAsync(context, bb, MIN_ZOOM, zMax) }
                        status = "Cleared this area's tiles"
                    },
                ) { Text("Clear area") }
                TextButton(enabled = !running, onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
