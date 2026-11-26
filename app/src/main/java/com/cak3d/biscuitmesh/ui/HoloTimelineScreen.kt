package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.wardrive.GeoPt
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// A "reality" colour per drive — each day's path gets its own hue so overlapping commutes read apart.
private val HOLO_HUES = listOf(
    Color(0xFF22D3EE), Color(0xFFFFD166), Color(0xFFFF5EA8), Color(0xFF8FD694),
    Color(0xFFA78BFA), Color(0xFFFF8A3D), Color(0xFF5FD3F0), Color(0xFF86EFAC),
    Color(0xFFF59E0B), Color(0xFF7DD3FC), Color(0xFFE879F9), Color(0xFF34D399),
)

/** One sighting placed in the hologram: normalised ground position + its time and identity. */
private data class HoloPt(
    val nx: Float, val nz: Float,
    val todMs: Long, val timeMs: Long,
    val type: String, val label: String, val id: String, val channel: Int,
    val drive: Int,
)

private data class DriveMeta(val index: Int, val startMs: Long, val label: String)
private data class HoloData(
    val pts: List<HoloPt>, val drives: Int, val minTod: Long, val maxTod: Long,
    val meta: List<DriveMeta> = emptyList(),
)

/** Time windows for the timeline. */
private enum class HoloScope(val label: String) {
    ALL("All"), DAY("Day"), WEEK("Week"), MONTH("Month");
    fun fromMs(): Long {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
        return when (this) {
            ALL -> 0L
            DAY -> c.timeInMillis
            WEEK -> { c.add(java.util.Calendar.DAY_OF_YEAR, -6); c.timeInMillis }
            MONTH -> { c.set(java.util.Calendar.DAY_OF_MONTH, 1); c.timeInMillis }
        }
    }
}

private fun todOf(timeMs: Long): Long {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = timeMs }
    return (((c.get(java.util.Calendar.HOUR_OF_DAY) * 60L) + c.get(java.util.Calendar.MINUTE)) * 60L +
        c.get(java.util.Calendar.SECOND)) * 1000L
}

private fun hhmm(todMs: Long): String {
    val m = todMs / 60000; val h = (m / 60) % 24; val mm = m % 60
    val ap = if (h < 12) "AM" else "PM"; val h12 = ((h + 11) % 12) + 1
    return "%d:%02d %s".format(h12, mm, ap)
}

private fun stamp(timeMs: Long): String =
    java.text.SimpleDateFormat("EEE MMM d · h:mm:ss a", java.util.Locale.US).format(java.util.Date(timeMs))

/**
 * Holo-Timeline — a Star Wars–style holographic table of everywhere you've driven and everything you
 * caught, laid on the real ground. Each drive is its own glowing "reality"; repeated commutes to and
 * from home/work fall on the same path and stack up. Drag to rotate, pinch to zoom, and scrub the
 * timeline at the bottom to watch sightings appear and fade by time of day — tap any point to read
 * exactly what it was and when it was seen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoloTimelineScreen(vm: MainViewModel, onBack: () -> Unit) {
    var data by remember { mutableStateOf<HoloData?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var rotY by remember { mutableFloatStateOf(0.7f) }
    var rotX by remember { mutableFloatStateOf(0.95f) }   // start tilted, looking down on the table
    var zoom by remember { mutableFloatStateOf(1f) }
    var cutoff by remember { mutableFloatStateOf(1f) }     // 0..1 across the day
    var playing by remember { mutableStateOf(false) }
    var autoSpin by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<HoloPt?>(null) }
    var flick by remember { mutableFloatStateOf(0f) }
    var scope by remember { mutableStateOf(HoloScope.ALL) }
    var driveSel by remember { mutableStateOf<Int?>(null) }
    var driveMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val byDrive = runCatching { vm.captureSamplesByDrive(limit = 24) }.getOrDefault(emptyList())
        data = buildHolo(byDrive)
        loaded = true
    }
    LaunchedEffect(autoSpin) { while (autoSpin) { rotY += 0.0035f; delay(33) } }
    LaunchedEffect(Unit) { while (true) { flick = (flick + 0.08f) % 100f; delay(60) } }
    LaunchedEffect(playing) {
        while (playing) {
            cutoff = (cutoff + 0.004f).let { if (it > 1f) 0f else it }
            delay(40)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Holo-Timeline") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { playing = !playing; if (playing) autoSpin = false }) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play timeline")
                    }
                    IconButton(onClick = { playing = false; cutoff = 1f }) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                    HelpButton("Holo-Timeline",
                        "A holographic table of everywhere you've driven, on the real ground. Each drive is its " +
                            "own colour; repeated commutes land on the same path and stack. Drag to rotate, pinch " +
                            "to zoom, and drag the timeline to scrub through the day — sightings light up and fade " +
                            "by the time they were seen. Tap a point for its name, type and exact timestamp.")
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val d = data
            when {
                !loaded -> CenterNote("Assembling the hologram…")
                d == null || d.pts.isEmpty() -> CenterNote(
                    "No located captures yet. Finish a wardrive with GPS-tagged Wi-Fi/BT and your path and " +
                        "sightings show up here as a hologram.")
                else -> {
                    val span = (d.maxTod - d.minTod).coerceAtLeast(1)
                    val cutTod = d.minTod + (span * cutoff).toLong()
                    val hits = remember { ArrayList<Triple<Float, Float, HoloPt>>() }
                    val fromMs = scope.fromMs()
                    val shownPts = remember(d, scope, driveSel) {
                        d.pts.filter { (fromMs == 0L || it.timeMs >= fromMs) && (driveSel == null || it.drive == driveSel) }
                    }
                    val shownDrives = remember(shownPts) { shownPts.map { it.drive }.distinct().size }

                    Canvas(
                        Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, panChange, zoomChange, _ ->
                                    autoSpin = false
                                    rotY += panChange.x * 0.006f
                                    rotX = (rotX + panChange.y * 0.006f).coerceIn(0.1f, 1.45f)
                                    zoom = (zoom * zoomChange).coerceIn(0.4f, 10f)
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { tap ->
                                    selected = hits.minByOrNull { (x, y, _) ->
                                        val dx = x - tap.x; val dy = y - tap.y; dx * dx + dy * dy
                                    }?.takeIf { (x, y, _) ->
                                        val dx = x - tap.x; val dy = y - tap.y; dx * dx + dy * dy < 70f * 70f
                                    }?.third
                                }
                            },
                    ) {
                        hits.clear()
                        val cx = cos(rotX); val sx = sin(rotX)
                        val cy = cos(rotY); val sy = sin(rotY)
                        val scale = size.minDimension * 0.40f * zoom
                        val center = Offset(size.width / 2f, size.height * 0.52f)

                        fun project(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
                            val x1 = x * cy + z * sy; val z1 = -x * sy + z * cy
                            val y1 = y * cx - z1 * sx; val z2 = y * sx + z1 * cx
                            val persp = 1f / (1f + z2 * 0.18f)
                            return Triple(center.x + x1 * scale * persp, center.y - y1 * scale * persp, z2)
                        }

                        // Holographic grid floor on y=0.
                        val grid = Color(0xFF1DB4D4)
                        var g = -1f
                        while (g <= 1.0001f) {
                            val a0 = project(g, 0f, -1f); val a1 = project(g, 0f, 1f)
                            val b0 = project(-1f, 0f, g); val b1 = project(1f, 0f, g)
                            val ga = if (kotlin.math.abs(g) < 0.01f) 0.5f else 0.14f
                            drawLine(grid.copy(alpha = ga), Offset(a0.first, a0.second), Offset(a1.first, a1.second), 1f)
                            drawLine(grid.copy(alpha = ga), Offset(b0.first, b0.second), Offset(b1.first, b1.second), 1f)
                            g += 0.25f
                        }

                        // Each drive's path up to the scrub time, then its sightings as glowing points.
                        val window = (span * 0.14f).toLong().coerceAtLeast(1_200_000L) // ~fade tail
                        fun recency(todMs: Long): Float {
                            if (todMs > cutTod) return -1f
                            val age = cutTod - todMs
                            return if (age <= window) (1f - age.toFloat() / window) * 0.82f + 0.18f else 0.16f
                        }

                        val byDrive = shownPts.groupBy { it.drive }
                        byDrive.forEach { (drive, pts) ->
                            val hue = HOLO_HUES[drive % HOLO_HUES.size]
                            val ordered = pts.sortedBy { it.todMs }
                            var prev: Triple<Float, Float, Float>? = null
                            var prevVis = false
                            for (p in ordered) {
                                val rc = recency(p.todMs)
                                val pr = project(p.nx, 0.02f, p.nz)
                                if (prev != null && prevVis && rc >= 0f) {
                                    drawLine(hue.copy(alpha = (rc * 0.5f).coerceIn(0.05f, 0.5f)),
                                        Offset(prev.first, prev.second), Offset(pr.first, pr.second), 1.6f)
                                }
                                prev = pr; prevVis = rc >= 0f
                            }
                        }

                        // Draw sightings far→near so the glow layers correctly.
                        val visible = shownPts.mapNotNull { p ->
                            val rc = recency(p.todMs); if (rc < 0f) null else Triple(p, rc, project(p.nx, 0.05f, p.nz)) }
                            .sortedByDescending { it.third.third }
                        visible.forEach { (p, rc, pr) ->
                            val hue = HOLO_HUES[p.drive % HOLO_HUES.size]
                            val pt = Offset(pr.first, pr.second)
                            drawCircle(hue.copy(alpha = 0.10f * rc), 11f * rc + 3f, pt)       // glow
                            drawCircle(hue.copy(alpha = 0.85f * rc), 2.4f, pt)                // core
                            if (rc > 0.6f) hits.add(Triple(pt.x, pt.y, p))
                        }

                        // A scrub playhead marker: a faint pulse ring at centre keyed to the flicker.
                        val sel = selected
                        if (sel != null) {
                            val pr = project(sel.nx, 0.05f, sel.nz)
                            drawCircle(Color.White, 5f, Offset(pr.first, pr.second))
                            drawCircle(Color.White.copy(alpha = 0.4f), 10f, Offset(pr.first, pr.second))
                        }

                        // Hologram scanlines across the whole table.
                        var y = (flick % 6f)
                        while (y < size.height) {
                            drawLine(Color(0xFF22D3EE).copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y), 1f)
                            y += 6f
                        }
                    }

                    // Overlay HUD: counts + scope/drive pickers + scrub time + selection + scrubber.
                    Column(Modifier.fillMaxSize().padding(14.dp)) {
                        Text("${shownDrives} ${if (shownDrives == 1) "reality" else "realities"} · ${shownPts.size} sightings · ◷ ${hhmm(cutTod)}",
                            color = Color(0xFF22D3EE), fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 6.dp)) {
                            HoloScope.entries.forEach { s ->
                                androidx.compose.material3.FilterChip(selected = scope == s,
                                    onClick = { scope = s }, label = { Text(s.label) })
                            }
                            Box {
                                androidx.compose.material3.AssistChip(onClick = { driveMenu = true },
                                    label = { Text(driveSel?.let { "Drive #${it + 1}" } ?: "All drives") })
                                androidx.compose.material3.DropdownMenu(expanded = driveMenu, onDismissRequest = { driveMenu = false }) {
                                    androidx.compose.material3.DropdownMenuItem(text = { Text("All drives") },
                                        onClick = { driveSel = null; driveMenu = false })
                                    d.meta.forEach { m ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("${m.label}  (#${m.index + 1})") },
                                            onClick = { driveSel = m.index; driveMenu = false })
                                    }
                                }
                            }
                        }

                        Box(Modifier.weight(1f))

                        selected?.let { s ->
                            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(12.dp), tonalElevation = 4.dp,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(s.label.ifBlank { s.id.ifBlank { s.type } }, fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp, color = HOLO_HUES[s.drive % HOLO_HUES.size])
                                    Text("${s.type}${if (s.channel > 0) " · ch ${s.channel}" else ""} · drive #${s.drive + 1}",
                                        fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                    Text(stamp(s.timeMs), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                                }
                            }
                        }

                        Slider(value = cutoff, onValueChange = { cutoff = it; playing = false },
                            modifier = Modifier.fillMaxWidth())
                        Text("drag to rotate · pinch to zoom in on a point & tap it · scope + drive pickers up top · ▶/■ to play/stop",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterNote(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
            modifier = Modifier.padding(32.dp))
    }
}

/** Normalise every drive's points onto a shared ground plane in [-1,1], keyed by time of day. */
private fun buildHolo(byDrive: List<Pair<String, List<GeoPt>>>): HoloData {
    val drives = byDrive.map { it.second }.filter { it.isNotEmpty() }
    val all = drives.flatten().filter { it.timeMs > 0 && !(it.lat == 0.0 && it.lon == 0.0) }
    if (all.isEmpty()) return HoloData(emptyList(), 0, 0, 1)
    val lat0 = all.map { it.lat }.average()
    val lon0 = all.map { it.lon }.average()
    val cosLat = cos(Math.toRadians(lat0))
    fun east(p: GeoPt) = (p.lon - lon0) * 111_320.0 * cosLat
    fun north(p: GeoPt) = (p.lat - lat0) * 110_540.0
    val maxExtent = all.maxOf { maxOf(kotlin.math.abs(east(it)), kotlin.math.abs(north(it))) }.coerceAtLeast(1.0)
    val out = ArrayList<HoloPt>()
    drives.forEachIndexed { di, pts ->
        // Cap each drive to keep the hologram light on frame.
        val step = (pts.size / 220).coerceAtLeast(1)
        pts.filterIndexed { idx, _ -> idx % step == 0 }.forEach { p ->
            if (p.timeMs <= 0 || (p.lat == 0.0 && p.lon == 0.0)) return@forEach
            out.add(HoloPt(
                nx = (east(p) / maxExtent).toFloat(),
                nz = (north(p) / maxExtent).toFloat(),
                todMs = todOf(p.timeMs), timeMs = p.timeMs,
                type = p.type.ifBlank { "WIFI" }, label = p.label, id = p.id, channel = p.channel,
                drive = di,
            ))
        }
    }
    val tods = out.map { it.todMs }
    val fmt = java.text.SimpleDateFormat("EEE MMM d", java.util.Locale.US)
    val meta = drives.mapIndexed { di, pts ->
        val start = pts.minOfOrNull { it.timeMs } ?: 0L
        DriveMeta(di, start, if (start > 0) fmt.format(java.util.Date(start)) else "Drive ${di + 1}")
    }
    return HoloData(out, drives.size, tods.minOrNull() ?: 0, tods.maxOrNull() ?: 1, meta)
}
