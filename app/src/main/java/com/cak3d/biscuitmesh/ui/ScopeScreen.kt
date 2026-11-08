package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.sensors.PhoneScanRow
import com.cak3d.biscuitmesh.wardrive.SampleKind
import kotlinx.coroutines.delay

private val BG = Color(0xFF05080A)
private val PANEL = Color(0xFF0B1014)
private val GREEN = Color(0xFF5FD36A)
private val CYAN = Color(0xFF22D3EE)
private val AMBER = Color(0xFFF0B429)
private val EMBER = Color(0xFFFF6A3D)
private val RED = Color(0xFFFF5470)
private val INK = Color(0xFFE9F0EE)
private val MUTED = Color(0xFF9AABA9)
private val GRID = Color(0xFF243638)
private val HEADER = Color(0xFF11191D)
private val MONO = FontFamily.Monospace

private const val BINS = 38
private const val TERRAIN_LIMIT = 40
private const val TRACE_LIMIT = 120
private const val PROFILE_BINS = 16
private const val RANK_LIMIT = 6
private const val FEED_LIMIT = 8
private const val TICK_MS = 220L
private const val MIN_DBM = -100
private const val MAX_DBM = -20

private enum class ScopeBand(val tag: String, val tint: Color) {
    WiFi24("2.4", GREEN), WiFi5("5G", CYAN), WiFi6("6G", AMBER), Ble("BLE", RED), Other("—", MUTED),
}

private fun PhoneScanRow.scopeBand(): ScopeBand = when {
    kind == SampleKind.BT -> ScopeBand.Ble
    kind != SampleKind.AP && kind != SampleKind.STATION -> ScopeBand.Other
    channel in 1..14 -> ScopeBand.WiFi24
    channel in 32..177 -> ScopeBand.WiFi5
    channel >= 1000 -> ScopeBand.WiFi6
    else -> ScopeBand.Other
}

private fun channelBin(channel: Int, band: ScopeBand): Int = when (band) {
    ScopeBand.WiFi24 -> channel - 1
    ScopeBand.WiFi5 -> 15 + (channel - 32) * 12 / 145
    ScopeBand.WiFi6 -> 29 + (channel - 1000).coerceIn(0, 233) * 8 / 233
    else -> -1
}

private fun strength(rssi: Float): Float =
    ((rssi - MIN_DBM) / (MAX_DBM - MIN_DBM)).coerceIn(0f, 1f)

private fun heat(level: Float): Color =
    if (level < 0.55f) lerp(GREEN, AMBER, level / 0.55f)
    else lerp(AMBER, EMBER, (level - 0.55f) / 0.45f)

private const val PALETTE_STEPS = 32
private val TERRAIN_COLORS = Array(PALETTE_STEPS + 1) { heat(it / PALETTE_STEPS.toFloat()) }
private val WATERFALL_COLORS = Array(PALETTE_STEPS + 1) {
    val level = it / PALETTE_STEPS.toFloat()
    if (level < 0.66f) lerp(Color(0xFF0A2843), CYAN, level / 0.66f)
    else lerp(CYAN, Color(0xFFA3FAF1), (level - 0.66f) / 0.34f)
}

private data class ScopeTrace(val high: Int, val low: Int, val mean: Float)
private data class ScopePeak(val bin: Int, val rssi: Int)
private data class ScopeHit(val row: PhoneScanRow, val time: String)
private data class ScopeArrival(val tickMs: Long, val count: Int)
private data class ScopeSnapshot(
    val bins: FloatArray = FloatArray(BINS),
    val trace: ScopeTrace? = null,
    val channelPeak: ScopePeak? = null,
    val profile: IntArray = IntArray(PROFILE_BINS),
    val ranked: List<PhoneScanRow> = emptyList(),
    val contacts: Int = 0,
)

private fun <T> MutableList<T>.appendCapped(value: T, limit: Int) {
    if (size == limit) removeAt(0)
    add(value)
}

@Composable
fun ScopeScreen(vm: MainViewModel, onBack: () -> Unit) {
    val terrain = remember(vm) { mutableStateListOf<FloatArray>() }
    val trace = remember(vm) { mutableStateListOf<ScopeTrace?>() }
    val feed = remember(vm) { mutableStateListOf<ScopeHit>() }
    var current by remember(vm) { mutableStateOf(ScopeSnapshot()) }
    var newPerSecond by remember(vm) { mutableStateOf(0) }
    val enteredAt = remember(vm) { System.currentTimeMillis() }
    val text = rememberTextMeasurer(cacheSize = 96)

    DisposableEffect(vm) {
        vm.startPhoneScan(wifi = true, ble = true)
        onDispose { vm.stopPhoneScan() }
    }

    LaunchedEffect(vm) {
        val seen = HashSet<String>()
        val arrivals = ArrayDeque<ScopeArrival>()
        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        var previous: List<PhoneScanRow>? = null
        var rate = 0
        while (true) {
            val rows = vm.phoneScanResults.value
            val now = System.nanoTime() / 1_000_000L
            var next = current
            var newHits: List<ScopeHit>? = null
            if (rows !== previous) {
                previous = rows
                val newest = feed.mapTo(ArrayList(FEED_LIMIT + 1)) { it.row }
                var discovered = 0
                next = summarize(rows) { row ->
                    if (row.lastSeenMs >= enteredAt && seen.add(row.id)) {
                        discovered++
                        val index = newest.indexOfFirst { it.lastSeenMs < row.lastSeenMs }
                        if (index >= 0) newest.add(index, row)
                        else if (newest.size < FEED_LIMIT) newest.add(row)
                        if (newest.size > FEED_LIMIT) newest.removeAt(FEED_LIMIT)
                    }
                }
                if (discovered > 0) {
                    arrivals.addLast(ScopeArrival(now, discovered))
                    rate += discovered
                    newHits = newest.map { ScopeHit(it, timeFormat.format(java.util.Date(it.lastSeenMs))) }
                }
            }
            while (arrivals.firstOrNull()?.let { now - it.tickMs >= 1_000L } == true) {
                rate -= arrivals.removeFirst().count
            }
            Snapshot.withMutableSnapshot {
                current = next
                newPerSecond = rate
                terrain.appendCapped(next.bins, TERRAIN_LIMIT)
                trace.appendCapped(next.trace, TRACE_LIMIT)
                newHits?.let {
                    feed.clear()
                    feed.addAll(it)
                }
            }
            delay(TICK_MS)
        }
    }

    Scaffold(containerColor = BG, topBar = { ScopeTopBar(text, onBack) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 640.dp
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { ScopeStats(text, current.contacts, newPerSecond, current.trace?.high) }
                item {
                    ScopePanel(
                        text, "01  SPECTRAL TERRAIN", "CHANNEL × TIME × RSSI",
                        height = if (wide) 384 else 290,
                        description = "Wi-Fi channel and signal history. Newest at the front.",
                    ) { drawSurface(text, terrain, current.channelPeak, waterfall = false) }
                }
                if (wide) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ScopeTracePanel(text, trace, current.profile)
                                ScopeWaterfallPanel(text, terrain)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ScopeRankPanel(text, current.ranked)
                                ScopeFeedPanel(text, feed)
                            }
                        }
                    }
                } else {
                    item { ScopeTracePanel(text, trace, current.profile) }
                    item { ScopeWaterfallPanel(text, terrain) }
                    item { ScopeRankPanel(text, current.ranked) }
                    item { ScopeFeedPanel(text, feed) }
                }
            }
        }
    }
}

private inline fun summarize(rows: List<PhoneScanRow>, onValid: (PhoneScanRow) -> Unit): ScopeSnapshot {
    val bins = FloatArray(BINS)
    val profile = IntArray(PROFILE_BINS)
    val ranked = ArrayList<PhoneScanRow>(RANK_LIMIT)
    var channelPeak: ScopePeak? = null
    var high = Int.MIN_VALUE
    var low = Int.MAX_VALUE
    var total = 0L
    var count = 0
    for (row in rows) {
        val rssi = row.rssi
        if (rssi >= 0) continue // Includes the scanner's 127 sentinel.
        onValid(row)
        high = maxOf(high, rssi)
        low = minOf(low, rssi)
        total += rssi
        count++
        // PhoneScanManager publishes strongest first, including on its final flush.
        if (ranked.size < RANK_LIMIT) ranked.add(row)
        val bucket = (strength(rssi.toFloat()) * PROFILE_BINS).toInt().coerceAtMost(PROFILE_BINS - 1)
        profile[bucket]++
        val bin = channelBin(row.channel, row.scopeBand())
        if (bin >= 0) {
            val level = strength(rssi.toFloat()).coerceAtLeast(0.04f)
            bins[bin] = maxOf(bins[bin], level)
            if (channelPeak == null || rssi > channelPeak.rssi) {
                channelPeak = ScopePeak(bin, rssi)
            }
        }
    }
    val trace = if (count == 0) null else ScopeTrace(high, low, total.toFloat() / count)
    return ScopeSnapshot(bins, trace, channelPeak, profile, ranked, count)
}

@Composable
private fun ScopeTopBar(text: TextMeasurer, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(BG).statusBarsPadding()) {
        Canvas(
            Modifier.height(56.dp).width(56.dp)
                .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack)
                .semantics { contentDescription = "Back" },
        ) {
            val center = this.center
            val arm = 7.dp.toPx()
            drawLine(GREEN, center - Offset(arm, 0f), center + Offset(arm, 0f), 2.dp.toPx())
            drawLine(GREEN, center - Offset(arm, 0f), center - Offset(0f, arm), 2.dp.toPx())
            drawLine(GREEN, center - Offset(arm, 0f), center + Offset(0f, arm), 2.dp.toPx())
        }
        Canvas(
            Modifier.height(56.dp).weight(1f)
                .semantics { heading(); contentDescription = "RF Depth. Live phone scope." },
        ) {
            caption(text, "RF / DEPTH", 0f, 5.dp.toPx(), INK, 20, bold = true)
            caption(text, "LIVE PHONE SCOPE · WI-FI / BLE", 0f, 33.dp.toPx(), GREEN, 10)
        }
    }
}

@Composable
private fun ScopeStats(text: TextMeasurer, count: Int, rate: Int, peak: Int?) {
    val peakText = peak?.let { "$it dBm" } ?: "—"
    Canvas(
        Modifier.fillMaxWidth().height(61.dp).semantics {
            contentDescription = "$count contacts, $rate new per second, peak $peakText"
        },
    ) {
        drawRect(PANEL)
        drawRect(GRID, style = Stroke(1.dp.toPx()))
        val cell = size.width / 3f
        val labels = listOf("CONTACTS", "NEW / SEC", "PEAK")
        val values = listOf(count.toString(), rate.toString(), peakText)
        val colors = listOf(GREEN, CYAN, peak?.let { heat(strength(it.toFloat())) } ?: MUTED)
        for (index in labels.indices) {
            val x = cell * index
            if (index > 0) drawLine(GRID, Offset(x, 0f), Offset(x, size.height))
            caption(text, labels[index], x + 9.dp.toPx(), 8.dp.toPx(), MUTED, 10, cell - 14.dp.toPx())
            caption(text, values[index], x + 9.dp.toPx(), 23.dp.toPx(), colors[index], 17, cell - 14.dp.toPx(), bold = true)
        }
    }
}

@Composable
private fun ScopePanel(
    text: TextMeasurer,
    title: String,
    subtitle: String,
    height: Int,
    description: String,
    content: DrawScope.() -> Unit,
) {
    Canvas(
        Modifier.fillMaxWidth().height(height.dp)
            .semantics { contentDescription = description },
    ) {
        val headerHeight = 40.dp.toPx()
        drawRect(PANEL)
        drawRect(HEADER, size = Size(size.width, headerHeight))
        drawLine(GRID, Offset(0f, headerHeight), Offset(size.width, headerHeight))
        drawRect(GRID, style = Stroke(1.dp.toPx()))
        caption(text, title, 10.dp.toPx(), 4.dp.toPx(), CYAN, 12, bold = true)
        caption(text, subtitle, 10.dp.toPx(), 22.dp.toPx(), MUTED, 9)
        inset(left = 8.dp.toPx(), top = headerHeight + 4.dp.toPx(), right = 8.dp.toPx(), bottom = 5.dp.toPx()) {
            content()
        }
    }
}

private fun DrawScope.caption(
    text: TextMeasurer,
    value: String,
    x: Float,
    y: Float,
    color: Color,
    fontSize: Int,
    width: Float = size.width - x,
    bold: Boolean = false,
) {
    val available = width.toInt()
    if (available <= 0) return
    val layout = text.measure(
        text = AnnotatedString(value),
        style = TextStyle(
            fontFamily = MONO, fontSize = fontSize.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = available),
    )
    drawText(layout, color = color, topLeft = Offset(x, y))
}

@Composable
private fun ScopeWaterfallPanel(text: TextMeasurer, terrain: List<FloatArray>) {
    ScopePanel(text, "03  3D WATERFALL", "CHANNEL INTENSITY · NEWEST AT FRONT", 195,
        "Wi-Fi channel intensity over time.") {
        drawSurface(text, terrain, peak = null, waterfall = true)
    }
}

private fun DrawScope.drawSurface(
    text: TextMeasurer,
    terrain: List<FloatArray>,
    peak: ScopePeak?,
    waterfall: Boolean,
) {
    val width = size.width
    val height = size.height
    val front = height - 22.dp.toPx()
    drawRect(Brush.verticalGradient(listOf(BG, if (waterfall) Color(0xFF0D242B) else Color(0xFF201510), PANEL)))

    fun project(bin: Int, depth: Float, level: Float): Offset {
        val convergence = 1f - depth * 0.54f
        val x = width * (0.49f + 0.13f * depth + (bin / (BINS - 1f) - 0.5f) * 0.94f * convergence)
        val floor = front * (1f - 0.57f * depth) + width * 0.008f * bin / (BINS - 1f)
        val lift = front * (if (waterfall) 0.34f else 0.48f) * (1f - 0.48f * depth)
        return Offset(x, floor - level * lift)
    }

    for (bin in 0 until BINS step 3) {
        drawLine(GRID, project(bin, 0f, 0f), project(bin, 1f, 0f))
    }
    for (line in 0..10) {
        val depth = line / 10f
        drawLine(GRID, project(0, depth, 0f), project(BINS - 1, depth, 0f))
    }
    for (bin in listOf(14, 28)) {
        drawLine(CYAN.copy(alpha = 0.26f), project(bin, 0f, 0f), project(bin, 1f, 0f))
    }

    val palette = if (waterfall) WATERFALL_COLORS else TERRAIN_COLORS
    val cell = Path()
    val ridge = Path()
    val floorRow = FloatArray(BINS)
    for (index in terrain.indices) {
        val row = terrain[index]
        val older = terrain.getOrNull(index - 1) ?: floorRow
        val near = (terrain.lastIndex - index) / TERRAIN_LIMIT.toFloat()
        val far = near + 1f / TERRAIN_LIMIT
        val opacity = 0.94f - near * 0.53f
        for (bin in 0 until BINS - 1) {
            val a = older[bin]
            val b = older[bin + 1]
            val c = row[bin + 1]
            val d = row[bin]
            val level = maxOf(maxOf(a, b), maxOf(c, d))
            if (level == 0f) continue
            val p1 = project(bin, far, a)
            val p2 = project(bin + 1, far, b)
            val p3 = project(bin + 1, near, c)
            val p4 = project(bin, near, d)
            val tint = palette[(level * PALETTE_STEPS).toInt()]
            cell.reset()
            cell.moveTo(p1.x, p1.y)
            cell.lineTo(p2.x, p2.y)
            cell.lineTo(p3.x, p3.y)
            cell.lineTo(p4.x, p4.y)
            cell.close()
            drawPath(cell, tint.copy(alpha = opacity * (0.24f + level * 0.68f)))
            drawLine(tint.copy(alpha = opacity * 0.76f), p4, p3)
        }
        if (index == terrain.lastIndex) {
            for (bin in row.indices) {
                val point = project(bin, 0f, row[bin])
                if (bin == 0) ridge.moveTo(point.x, point.y) else ridge.lineTo(point.x, point.y)
            }
            drawPath(ridge, if (waterfall) CYAN else AMBER, style = Stroke(1.dp.toPx()))
        }
    }
    val latest = terrain.lastOrNull()
    if (peak != null && latest != null) {
        val point = project(peak.bin, 0f, latest[peak.bin])
        val labelWidth = minOf(132.dp.toPx(), width)
        val x = (point.x + 9.dp.toPx()).coerceIn(0f, width - labelWidth)
        val y = (point.y - 30.dp.toPx()).coerceAtLeast(1.dp.toPx())
        drawLine(CYAN, point, Offset(x, y + 9.dp.toPx()), 1.dp.toPx())
        drawCircle(CYAN, 3.dp.toPx(), point)
        drawRect(CYAN, Offset(x, y), Size(labelWidth, 18.dp.toPx()))
        caption(text, "PEAK  ${peak.rssi} dBm", x + 4.dp.toPx(), y + 1.dp.toPx(), BG, 10, labelWidth - 6.dp.toPx(), true)
    } else if (latest == null || latest.none { it > 0f }) {
        caption(text, "NO CURRENT WI-FI CHANNELS", 9.dp.toPx(), 8.dp.toPx(), MUTED, 10)
    }
    val axisY = height - 17.dp.toPx()
    caption(text, "2.4 GHz", width * 0.04f, axisY, GREEN, 10)
    caption(text, "5 GHz", width * 0.47f, axisY, CYAN, 10)
    caption(text, "6 GHz", width * 0.79f, axisY, AMBER, 10)
}

@Composable
private fun ScopeTracePanel(text: TextMeasurer, trace: List<ScopeTrace?>, profile: IntArray) {
    ScopePanel(text, "02  SIGNAL TRACE", "PEAK / MEAN · RSSI PROFILE", 197,
        "Strongest and mean signal over time, with a high-low envelope and RSSI distribution.") {
        drawTrace(text, trace, profile)
    }
}

private fun DrawScope.drawTrace(text: TextMeasurer, trace: List<ScopeTrace?>, profile: IntArray) {
    val left = 34.dp.toPx()
    val right = size.width * 0.77f
    val top = 9.dp.toPx()
    val bottom = size.height - 21.dp.toPx()
    val height = bottom - top
    if (right <= left || height <= 0f) return
    fun x(index: Int) = right - (trace.lastIndex - index) * (right - left) / (TRACE_LIMIT - 1f)
    fun y(rssi: Float) = bottom - strength(rssi) * height
    for (dbm in MIN_DBM..MAX_DBM step 20) {
        val line = y(dbm.toFloat())
        drawLine(GRID, Offset(left, line), Offset(size.width, line))
        caption(text, dbm.toString(), 0f, line - 6.dp.toPx(), MUTED, 9)
    }
    for (division in 0..6) {
        val gridX = left + (right - left) * division / 6
        drawLine(GRID, Offset(gridX, top), Offset(gridX, bottom))
    }
    val maxCount = profile.maxOrNull()?.coerceAtLeast(1) ?: 1
    val barHeight = height / PROFILE_BINS
    val barRight = size.width - 2.dp.toPx()
    for (bucket in profile.indices) {
        val length = (barRight - right - 7.dp.toPx()) * profile[bucket] / maxCount
        if (length > 0f) {
            val level = (bucket + 0.5f) / PROFILE_BINS
            drawRect(heat(level), Offset(barRight - length, bottom - (bucket + 1) * barHeight),
                Size(length, (barHeight - 1.dp.toPx()).coerceAtLeast(1f)))
        }
    }
    val envelope = Path()
    val peak = Path()
    val mean = Path()
    var index = 0
    while (index < trace.size) {
        if (trace[index] == null) {
            index++
            continue
        }
        val start = index
        while (index < trace.size && trace[index] != null) index++
        envelope.reset()
        peak.reset()
        mean.reset()
        for (point in start until index) {
            val sample = trace[point] ?: continue
            val px = x(point)
            val hi = y(sample.high.toFloat())
            val avg = y(sample.mean)
            if (point == start) {
                envelope.moveTo(px, hi)
                peak.moveTo(px, hi)
                mean.moveTo(px, avg)
            } else {
                envelope.lineTo(px, hi)
                peak.lineTo(px, hi)
                mean.lineTo(px, avg)
            }
        }
        for (point in index - 1 downTo start) {
            val sample = trace[point] ?: continue
            envelope.lineTo(x(point), y(sample.low.toFloat()))
        }
        envelope.close()
        drawPath(envelope, AMBER.copy(alpha = 0.18f))
        drawPath(mean, CYAN, style = Stroke(1.25.dp.toPx()))
        drawPath(peak, EMBER, style = Stroke(1.5.dp.toPx()))
        if (index - start == 1) {
            trace[start]?.let { drawCircle(EMBER, 2.dp.toPx(), Offset(x(start), y(it.high.toFloat()))) }
        }
    }
    caption(text, "OLDER", left, bottom + 3.dp.toPx(), MUTED, 9)
    caption(text, "NOW", right - 20.dp.toPx(), bottom + 3.dp.toPx(), MUTED, 9)
    caption(text, "dBm", 0f, bottom + 3.dp.toPx(), MUTED, 9)
}

@Composable
private fun ScopeRankPanel(text: TextMeasurer, ranked: List<PhoneScanRow>) {
    val description = ranked.joinToString { "${it.label.ifBlank { it.id }}, ${it.rssi} dBm" }
    ScopePanel(text, "04  STRONGEST SIGNALS", "WI-FI AND BLE · TOP $RANK_LIMIT", 197,
        "Strongest signals. ${description.ifEmpty { "No contacts yet." }}") {
        if (ranked.isEmpty()) caption(text, "NO CONTACTS YET", 2.dp.toPx(), 4.dp.toPx(), MUTED, 10)
        val rowHeight = 23.dp.toPx()
        val meterX = size.width * 0.47f
        val valueWidth = 60.dp.toPx()
        val meterWidth = (size.width - meterX - valueWidth - 8.dp.toPx()).coerceAtLeast(0f)
        for ((index, signal) in ranked.withIndex()) {
            val y = rowHeight * index
            val level = strength(signal.rssi.toFloat())
            val tint = heat(level)
            drawLine(GRID, Offset(0f, y + rowHeight), Offset(size.width, y + rowHeight))
            caption(text, signal.label.ifBlank { signal.id }, 2.dp.toPx(), y + 3.dp.toPx(), INK, 11,
                meterX - 7.dp.toPx())
            drawRect(GRID, Offset(meterX, y + 9.dp.toPx()), Size(meterWidth, 5.dp.toPx()))
            drawRect(tint, Offset(meterX, y + 9.dp.toPx()), Size(meterWidth * level.coerceAtLeast(0.04f), 5.dp.toPx()))
            caption(text, "${signal.rssi} dBm", size.width - valueWidth, y + 3.dp.toPx(), tint, 10)
        }
    }
}

@Composable
private fun ScopeFeedPanel(text: TextMeasurer, feed: List<ScopeHit>) {
    val description = feed.joinToString {
        "${it.time}, ${it.row.scopeBand().tag}, ${it.row.label.ifBlank { it.row.id }}, ${it.row.rssi} dBm"
    }
    ScopePanel(text, "05  DETECTION FEED", "NEW CONTACTS · NEWEST FIRST", 233,
        "Detection feed. ${description.ifEmpty { "Waiting for new contacts." }}") {
        if (feed.isEmpty()) caption(text, "WAITING FOR NEW CONTACTS", 2.dp.toPx(), 4.dp.toPx(), MUTED, 10)
        val rowHeight = 22.dp.toPx()
        val bandX = 56.dp.toPx()
        val labelX = 91.dp.toPx()
        val valueWidth = 60.dp.toPx()
        for ((index, hit) in feed.withIndex()) {
            val y = rowHeight * index
            val row = hit.row
            val band = row.scopeBand()
            if (index % 2 == 0) drawRect(HEADER, Offset(0f, y), Size(size.width, rowHeight))
            caption(text, hit.time, 2.dp.toPx(), y + 4.dp.toPx(), MUTED, 9, bandX - 4.dp.toPx())
            drawRect(band.tint.copy(alpha = 0.15f), Offset(bandX, y + 3.dp.toPx()), Size(29.dp.toPx(), 15.dp.toPx()))
            caption(text, band.tag, bandX + 3.dp.toPx(), y + 4.dp.toPx(), band.tint, 9, 26.dp.toPx(), true)
            caption(text, row.label.ifBlank { row.id }, labelX, y + 3.dp.toPx(), INK, 10,
                size.width - labelX - valueWidth - 4.dp.toPx())
            caption(text, "${row.rssi} dBm", size.width - valueWidth, y + 3.dp.toPx(),
                heat(strength(row.rssi.toFloat())), 10)
        }
    }
}
