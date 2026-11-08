package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.sensors.PhoneScanRow
import com.cak3d.biscuitmesh.wardrive.SampleKind
import kotlinx.coroutines.delay
import kotlin.math.max

private val BG = Color(0xFF05080A)
private val PANEL = Color(0xFF0B1014)
private val GREEN = Color(0xFF5FD36A)
private val LEAF = Color(0xFF86EFAC)
private val CYAN = Color(0xFF22D3EE)
private val AMBER = Color(0xFFF0B429)
private val EMBER = Color(0xFFFF6A3D)
private val RED = Color(0xFFFF5470)
private val GRID = GREEN.copy(alpha = 0.14f)
private val MONO = FontFamily.Monospace

private const val COLS = 28      // spectrum columns (channel bins)
private const val ROWS = 40      // terrain depth (time rows kept)
private const val HIST = 120     // waveform history length

/** Map a Wi-Fi channel to a spectrum column: 2.4 GHz left, 5 GHz middle, 6 GHz right. */
private fun colOf(ch: Int): Int {
    val pos = when {
        ch >= 1000 -> 0.82f + ((ch - 1000).coerceIn(1, 233) / 233f) * 0.18f   // 6 GHz (offset-marked)
        ch in 32..177 -> 0.30f + ((ch - 32) / 145f) * 0.50f                   // 5 GHz
        ch in 1..14 -> ((ch - 1) / 13f) * 0.28f                               // 2.4 GHz
        else -> 0.29f
    }
    return (pos * (COLS - 1)).toInt().coerceIn(0, COLS - 1)
}

/** 0..1 signal strength from an RSSI in dBm (−95 floor … −30 ceiling). */
private fun strengthOf(rssi: Int): Float = ((rssi + 95f) / 65f).coerceIn(0f, 1f)

/** Height colour ramp: cool green valleys → firelight → ember peaks. */
private fun ramp(h: Float): Color = when {
    h > 0.80f -> EMBER
    h > 0.58f -> AMBER
    h > 0.32f -> GREEN
    else -> LEAF.copy(alpha = 0.7f)
}

/**
 * Live Scope — a quant-terminal-style look at the RF around you: a signal waveform
 * with its highs and lows, a spectrum terrain (channel across, time receding, signal
 * as the peaks), and a live hit-feed with per-band load. Runs the phone's own radios
 * so it works with no board attached.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScopeScreen(vm: MainViewModel, onBack: () -> Unit) {
    DisposableEffect(Unit) {
        vm.startPhoneScan(wifi = true, ble = true)
        onDispose { vm.stopPhoneScan() }
    }

    // Rolling history, all derived on a fixed tick so the panels animate smoothly.
    val waveHi = remember { mutableStateListOf<Float>() }   // strongest signal per tick
    val waveLo = remember { mutableStateListOf<Float>() }   // weakest signal per tick
    val waveAvg = remember { mutableStateListOf<Float>() }  // mean signal per tick
    val terrain = remember { mutableStateListOf<FloatArray>() } // newest first; each = COLS heights
    val feed = remember { mutableStateListOf<FeedItem>() }
    var total by remember { mutableStateOf(0) }
    var rate by remember { mutableStateOf(0f) }
    var band24 by remember { mutableStateOf(0) }
    var band5 by remember { mutableStateOf(0) }
    var band6 by remember { mutableStateOf(0) }
    var bandBt by remember { mutableStateOf(0) }
    var peakDbm by remember { mutableStateOf(-120) }

    LaunchedEffect(Unit) {
        var lastTotal = 0
        while (true) {
            val snap: List<PhoneScanRow> = vm.phoneScanResults.value
            // Drop unknown-RSSI rows (Android reports 127 / 0x7F when it has no reading).
            val valid = snap.filter { it.rssi in -119..-1 }
            val nets = valid.filter { it.kind == SampleKind.AP || it.kind == SampleKind.STATION }
            val bt = valid.count { it.kind == SampleKind.BT }

            // Waveform: envelope + mean of this tick's signal strengths.
            val strengths = valid.map { strengthOf(it.rssi) }
            val hi = strengths.maxOrNull() ?: 0f
            val lo = strengths.minOrNull() ?: 0f
            val avg = if (strengths.isEmpty()) 0f else strengths.average().toFloat()
            waveHi.add(hi); waveLo.add(lo); waveAvg.add(avg)
            if (waveHi.size > HIST) { waveHi.removeAt(0); waveLo.removeAt(0); waveAvg.removeAt(0) }

            // Terrain: per-column max strength of the Wi-Fi networks this tick.
            val row = FloatArray(COLS)
            for (n in nets) {
                if (n.channel <= 0) continue  // nets already excludes unknown-RSSI rows
                val c = colOf(n.channel)
                val s = strengthOf(n.rssi)
                if (s > row[c]) row[c] = s
            }
            terrain.add(0, row); if (terrain.size > ROWS) terrain.removeAt(terrain.size - 1)

            // Headline stats.
            total = snap.size
            band24 = nets.count { it.channel in 1..14 }
            band5 = nets.count { it.channel in 32..177 }
            band6 = nets.count { it.channel >= 1000 }
            bandBt = bt
            val curMax = valid.maxOfOrNull { it.rssi } ?: -120
            peakDbm = curMax
            rate = max(0, snap.size - lastTotal).toFloat()

            // Feed: note fresh arrivals and the strongest contact.
            if (snap.size > lastTotal) {
                val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                val strongest = valid.maxByOrNull { it.rssi }
                val isBt = strongest?.kind == SampleKind.BT
                val tag = when {
                    isBt -> "BT"
                    (strongest?.channel ?: 0) >= 1000 -> "6E"
                    (strongest?.channel ?: 0) in 32..177 -> "5G"
                    else -> "2G4"
                }
                val tagColor = when (tag) { "BT" -> RED; "6E" -> AMBER; "5G" -> CYAN; else -> GREEN }
                val lbl = strongest?.let { it.label.ifBlank { it.id } } ?: "—"
                feed.add(FeedItem(t, tag, tagColor, strengthOf(curMax), "+${snap.size - lastTotal}  ${curMax}dBm  ${lbl.take(16)}"))
                if (feed.size > 80) feed.removeAt(0)
            }
            lastTotal = snap.size

            delay(220)
        }
    }

    Scaffold(
        containerColor = BG,
        topBar = {
            TopAppBar(
                title = { Text("Live Scope", fontFamily = MONO, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BG, titleContentColor = GREEN),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = GREEN) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatStrip(total, rate.toInt(), peakDbm) }
            item {
                ScopePanel("SIGNAL · HIGHS & LOWS", "live RF envelope — top line strongest, shade = spread") {
                    WaveformCanvas(waveHi, waveLo, waveAvg)
                }
            }
            item {
                ScopePanel("SPECTRUM TERRAIN · ${COLS} BINS", "2.4 GHz ◂ channels ▸ 6 GHz · height = signal · back = older") {
                    TerrainCanvas(terrain)
                }
            }
            item { ScopePanel("BANDS", "live network count per band") { BandBars(band24, band5, band6, bandBt) } }
            item { ScopePanel("FEED · tail", "newest detections") { FeedPanel(feed) } }
        }
    }
}

@Composable
private fun StatStrip(total: Int, rate: Int, peak: Int) {
    Row(
        Modifier.fillMaxWidth().background(PANEL, RoundedCornerShape(12.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Stat("CONTACTS", "$total", GREEN)
        Stat("NEW/s", "$rate", CYAN)
        Stat("PEAK", "${peak}dBm", if (peak > -55) EMBER else AMBER)
    }
}

@Composable
private fun Stat(label: String, value: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = tint, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(label, color = Color.White.copy(alpha = 0.5f), fontFamily = MONO, fontSize = 10.sp)
    }
}

@Composable
private fun ScopePanel(title: String, sub: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(PANEL, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = CYAN, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(sub, color = Color.White.copy(alpha = 0.45f), fontFamily = MONO, fontSize = 10.sp)
        content()
    }
}

// Rainbow ribbon palette (fast → slow), red through violet like the reference EMA ribbon.
private val RIBBON = listOf(
    Color(0xFFFF5470), Color(0xFFFF8A3D), Color(0xFFF0B429),
    Color(0xFF5FD36A), Color(0xFF22D3EE), Color(0xFF7C5CFF),
)

/** Exponential moving average of a series with smoothing factor [a] (0..1, smaller = smoother). */
private fun ema(src: List<Float>, a: Float): FloatArray {
    val out = FloatArray(src.size)
    var acc = src.firstOrNull() ?: 0f
    for (i in src.indices) { acc += a * (src[i] - acc); out[i] = acc }
    return out
}

@Composable
private fun WaveformCanvas(hi: List<Float>, lo: List<Float>, avg: List<Float>) {
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val w = size.width; val h = size.height
        for (i in 0..4) {
            val y = h * i / 4f
            drawLine(GRID, Offset(0f, y), Offset(w, y), 1f)
        }
        val n = hi.size
        if (n < 2) return@Canvas
        fun x(i: Int) = w * i / (n - 1f)
        fun y(v: Float) = h - v.coerceIn(0f, 1f) * h
        // High/low envelope fill.
        val env = Path().apply {
            moveTo(x(0), y(hi[0]))
            for (i in 1 until n) lineTo(x(i), y(hi[i]))
            for (i in n - 1 downTo 0) lineTo(x(i), y(lo[i]))
            close()
        }
        drawPath(env, GREEN.copy(alpha = 0.12f))
        fun drawSeries(series: FloatArray, color: Color, stroke: Float) {
            val p = Path().apply {
                moveTo(x(0), y(series[0]))
                for (i in 1 until series.size) lineTo(x(i), y(series[i]))
            }
            drawPath(p, color, style = Stroke(stroke))
        }
        // Rainbow EMA ribbon over the mean — fast (bright) to slow (smooth).
        val alphas = listOf(0.60f, 0.42f, 0.28f, 0.18f, 0.11f, 0.06f)
        for (k in alphas.indices.reversed()) {
            drawSeries(ema(avg, alphas[k]), RIBBON[k].copy(alpha = 0.9f), 2f)
        }
        // Strongest-signal line on top, hot.
        drawSeries(hi.toFloatArray(), EMBER, 2.4f)
    }
}

@Composable
private fun TerrainCanvas(terrain: List<FloatArray>) {
    Canvas(Modifier.fillMaxWidth().height(230.dp)) {
        val w = size.width; val h = size.height
        val rows = terrain.size
        if (rows == 0) return@Canvas
        val amp = h * 0.34f          // peak height
        val cx = w / 2f
        // Perspective projection: back rows sit higher, converge toward centre, and fade.
        // depth: 0 = newest/front (wide, low), 1 = oldest/back (narrow, high).
        fun project(col: Int, depth: Float, height: Float): Offset {
            val persp = 1f - depth * 0.52f                       // back rows narrower
            val nx = (col / (COLS - 1f)) - 0.5f
            val x = cx + nx * w * 0.96f * persp
            val yBase = h * 0.86f - depth * (h * 0.52f)          // back rows higher on screen
            val y = yBase - height * amp * (1f - depth * 0.35f)  // nearer peaks taller
            return Offset(x, y)
        }
        // Faint floor grid (depth lines) first, so ridges draw over it.
        for (c in 0 until COLS step 3) {
            val p = Path()
            for (r in rows - 1 downTo 0) {
                val o = project(c, r / (ROWS - 1f), 0f)
                if (r == rows - 1) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
            }
            drawPath(p, GRID, style = Stroke(1f))
        }
        // Ridge lines, back-to-front (painter's algorithm) so near ridges overlap far ones.
        for (r in rows - 1 downTo 0) {
            val row = terrain[r]
            val depth = r / (ROWS - 1f)
            val fade = 0.18f + (1f - depth) * 0.82f
            val ridge = Path()
            for (c in 0 until COLS) {
                val o = project(c, depth, row[c])
                if (c == 0) ridge.moveTo(o.x, o.y) else ridge.lineTo(o.x, o.y)
            }
            val peak = row.maxOrNull() ?: 0f
            // soft under-glow then the hot ridge line
            drawPath(ridge, ramp(peak).copy(alpha = fade * 0.22f), style = Stroke(5f))
            drawPath(ridge, ramp(peak).copy(alpha = fade), style = Stroke(1.6f))
        }
        // Depth "walls" at the band edges (2.4 | 5 | 6 GHz), front-to-back.
        for (edgeCol in listOf((COLS * 0.28f).toInt(), (COLS * 0.80f).toInt())) {
            val p = Path()
            for (r in rows - 1 downTo 0) {
                val o = project(edgeCol, r / (ROWS - 1f), 0f)
                if (r == rows - 1) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
            }
            drawPath(p, CYAN.copy(alpha = 0.18f), style = Stroke(1.2f))
        }
    }
}

@Composable
private fun BandBars(b24: Int, b5: Int, b6: Int, bt: Int) {
    val maxv = max(1, maxOf(b24, b5, b6, bt))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Bar("2.4 GHz", b24, maxv, GREEN)
        Bar("5 GHz", b5, maxv, CYAN)
        Bar("6 GHz", b6, maxv, AMBER)
        Bar("Bluetooth", bt, maxv, RED)
    }
}

@Composable
private fun Bar(label: String, value: Int, maxv: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontFamily = MONO, fontSize = 11.sp,
            modifier = Modifier.width(72.dp))
        Box(Modifier.weight(1f).height(14.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))) {
            Box(
                Modifier.fillMaxWidth(value / maxv.toFloat()).height(14.dp)
                    .background(color.copy(alpha = 0.75f), RoundedCornerShape(4.dp)),
            )
        }
        Text("$value", color = color, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            modifier = Modifier.width(28.dp))
    }
}

private data class FeedItem(val t: String, val tag: String, val color: Color, val strength: Float, val text: String)

@Composable
private fun FeedPanel(feed: List<FeedItem>) {
    val listState = rememberLazyListState()
    LaunchedEffect(feed.size) { if (feed.isNotEmpty()) listState.animateScrollToItem(feed.size - 1) }
    LazyColumn(
        Modifier.fillMaxWidth().height(172.dp).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(feed) { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.t, color = Color.White.copy(alpha = 0.4f), fontFamily = MONO, fontSize = 10.sp)
                Box(
                    Modifier.background(item.color.copy(alpha = 0.22f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp),
                ) {
                    Text(item.tag, color = item.color, fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
                // per-line load bar from signal strength
                Box(Modifier.width(54.dp).height(8.dp).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(item.strength).height(8.dp)
                        .background(item.color.copy(alpha = 0.8f), RoundedCornerShape(2.dp)))
                }
                Text(item.text, color = GREEN.copy(alpha = 0.85f), fontFamily = MONO, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}
