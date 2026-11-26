package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.wardrive.GeoPt
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.TilesOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveReplayScreen(vm: MainViewModel, driveId: String, onBack: () -> Unit) {
    val coverage = driveId == "all"
    val context = LocalContext.current
    var pts by remember { mutableStateOf<List<GeoPt>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(driveId) {
        pts = (if (coverage) vm.allCaptureSamples() else vm.captureSamples(driveId)).sortedBy { it.timeMs }
        loading = false
    }

    // Coverage time window — Today / This month / This year / All time.
    var range by remember { mutableStateOf(CoverageRange.ALL) }
    val coveragePts = remember(pts, range) {
        if (!coverage) pts else {
            val from = range.fromMs()
            if (from == 0L) pts else pts.filter { it.timeMs >= from }
        }
    }

    var frac by remember { mutableFloatStateOf(1f) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(playing, pts.size) {
        if (playing && pts.isNotEmpty()) {
            if (frac >= 1f) frac = 0f
            while (playing && frac < 1f) { frac = (frac + 0.01f).coerceAtMost(1f); delay(60) }
            playing = false
        }
    }

    val minT = pts.firstOrNull()?.timeMs ?: 0L
    val maxT = pts.lastOrNull()?.timeMs ?: 0L
    val cutoff = if (maxT > minT) minT + ((maxT - minT) * frac).toLong() else maxT
    val shown = if (coverage) coveragePts else pts.filter { it.timeMs <= cutoff }

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
            controller.setZoom(15.0)
        }
    }
    var framed by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (coverage) "All-drive coverage" else "Drive replay") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        if (coverage) "All-drive coverage" else "Drive replay",
                        "Plays your drive back on the map, dropping finds in the order you caught them. " +
                            "Coverage mode instead shows everywhere you've driven across all your drives at once.",
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { mapView }, update = { map ->
                    map.overlays.clear()
                    if (coverage) {
                        map.overlays.add(ReplayHeat(coveragePts.map { GeoPoint(it.lat, it.lon) }))
                    } else {
                        map.overlays.add(ReplayDots(shown))
                    }
                    if (!framed && pts.isNotEmpty()) {
                        val lats = pts.map { it.lat }; val lons = pts.map { it.lon }
                        runCatching {
                            map.zoomToBoundingBox(
                                BoundingBox(lats.max(), lons.max(), lats.min(), lons.min()), false, 80,
                            )
                        }
                        framed = true
                    }
                    map.invalidate()
                })
                if (loading) Text("Loading drive…", Modifier.padding(16.dp), color = Color.White)
                else if (pts.isEmpty()) Text("No saved points for this drive.", Modifier.padding(16.dp), color = Color.White)
            }
            if (coverage && pts.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverageRange.entries.forEach { r ->
                        androidx.compose.material3.FilterChip(selected = range == r, onClick = { range = r; framed = false },
                            label = { Text(r.label) })
                    }
                    Text("${coveragePts.size}", fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (!coverage && pts.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledIconButton(onClick = { playing = !playing }) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play")
                    }
                    Slider(value = frac, onValueChange = { frac = it; playing = false }, modifier = Modifier.weight(1f))
                    Text("${shown.size}/${pts.size}", fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/** Draws replay points up to the scrub time as small coloured dots. */
private class ReplayDots(private val pts: List<GeoPt>) : Overlay() {
    private val pt = android.graphics.Point()
    private val paint = android.graphics.Paint().apply { isAntiAlias = true }
    override fun draw(canvas: android.graphics.Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val proj = mapView.projection
        pts.forEach { p ->
            proj.toPixels(GeoPoint(p.lat, p.lon), pt)
            paint.color = when (p.type) {
                "BT" -> android.graphics.Color.parseColor("#7C9CFF")
                "RF" -> android.graphics.Color.parseColor("#FF8A3D")
                else -> android.graphics.Color.parseColor("#22D3EE")
            }
            canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 5f, paint)
        }
    }
}

/** Coverage time windows for the all-drive heatmap. */
enum class CoverageRange(val label: String) {
    TODAY("Today"), MONTH("Month"), YEAR("Year"), ALL("All time");

    /** Start-of-window in epoch ms (0 = no limit). */
    fun fromMs(): Long {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
        return when (this) {
            TODAY -> c.timeInMillis
            MONTH -> { c.set(java.util.Calendar.DAY_OF_MONTH, 1); c.timeInMillis }
            YEAR -> { c.set(java.util.Calendar.DAY_OF_YEAR, 1); c.timeInMillis }
            ALL -> 0L
        }
    }
}

/** Blue → cyan → green → yellow → orange → red, for t in 0..1. Returns 0xRRGGBB. */
private fun heatColor(t: Float): Int {
    val stops = intArrayOf(0x2166AC, 0x22D3EE, 0x34D399, 0xFDE047, 0xFB923C, 0xEF4444)
    val x = t.coerceIn(0f, 1f) * (stops.size - 1)
    val i = x.toInt().coerceIn(0, stops.size - 2)
    val f = x - i
    val a = stops[i]; val b = stops[i + 1]
    fun lerp(s: Int, e: Int) = (s + (e - s) * f).toInt()
    val r = lerp((a shr 16) and 0xFF, (b shr 16) and 0xFF)
    val g = lerp((a shr 8) and 0xFF, (b shr 8) and 0xFF)
    val bl = lerp(a and 0xFF, b and 0xFF)
    return (r shl 16) or (g shl 8) or bl
}

/** A density heatmap: each point coloured by how many others fall in its ~50 m cell, so busy
 *  roads glow warm and the odd lone catch stays cool. */
private class ReplayHeat(private val points: List<GeoPoint>) : Overlay() {
    private val paint = android.graphics.Paint().apply { isAntiAlias = true }
    private val pt = android.graphics.Point()

    private val cell = 0.00045  // ~50 m
    private fun key(gp: GeoPoint): Long {
        val la = Math.floor(gp.latitude / cell).toLong(); val lo = Math.floor(gp.longitude / cell).toLong()
        return (la shl 32) xor (lo and 0xffffffffL)
    }
    private val density: IntArray = run {
        if (points.isEmpty()) IntArray(0) else {
            val counts = HashMap<Long, Int>()
            points.forEach { counts[key(it)] = (counts[key(it)] ?: 0) + 1 }
            IntArray(points.size) { counts[key(points[it])] ?: 1 }
        }
    }
    private val maxD = (density.maxOrNull() ?: 1).coerceAtLeast(1)

    override fun draw(canvas: android.graphics.Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val proj = mapView.projection
        val bb = mapView.boundingBox
        val r = 36f
        points.forEachIndexed { i, gp ->
            if (gp.latitude > bb.latNorth || gp.latitude < bb.latSouth ||
                gp.longitude > bb.lonEast || gp.longitude < bb.lonWest) return@forEachIndexed
            proj.toPixels(gp, pt)
            val x = pt.x.toFloat(); val y = pt.y.toFloat()
            // Log scale so a few busy cells don't wash everything red.
            val t = (Math.log1p(density[i].toDouble()) / Math.log1p(maxD.toDouble())).toFloat()
            val rgb = heatColor(t)
            val cr = (rgb shr 16) and 0xFF; val cg = (rgb shr 8) and 0xFF; val cb = rgb and 0xFF
            val alpha = (55 + t * 150).toInt().coerceIn(45, 205)
            paint.shader = android.graphics.RadialGradient(
                x, y, r,
                intArrayOf(android.graphics.Color.argb(alpha, cr, cg, cb), android.graphics.Color.argb(0, cr, cg, cb)),
                null, android.graphics.Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(x, y, r, paint)
        }
    }
}
