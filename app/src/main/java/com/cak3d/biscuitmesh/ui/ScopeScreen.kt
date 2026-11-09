package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.sensors.CellSnapshot
import com.cak3d.biscuitmesh.sensors.GnssSnapshot
import com.cak3d.biscuitmesh.sensors.PhoneScanRow
import com.cak3d.biscuitmesh.wardrive.SampleKind
import com.cak3d.biscuitmesh.wardrive.WardriveSample
import kotlinx.coroutines.delay

private val BG = Color(0xFF05080A)
private val PANEL = Color(0xFF050708)
private val GREEN = Color(0xFF5FD36A)
private val CYAN = Color(0xFF22D3EE)
private val AMBER = Color(0xFFF0B429)
private val EMBER = Color(0xFFFF6A3D)
private val RED = Color(0xFFFF5470)
private val INK = Color(0xFFE9F0EE)
private val MUTED = Color(0xFF9AABA9)
private val GRID = Color(0xFF252624)
private val HEADER = Color(0xFF121415)
private val WALL_GRID = Color(0xFF451E22)
private val GOLD = Color(0xFFFFDC8D)
private val MONO = FontFamily.Monospace

private const val BINS = 60
private const val BLE_BIN = 35
private const val CANDLE_TICKS = 3
private const val TERRAIN_LIMIT = 40
private const val TRACE_LIMIT = 120
private const val PROFILE_BINS = 16
private const val RANK_LIMIT = 6
private const val FEED_LIMIT = 12
private const val TICK_MS = 220L
private const val MIN_DBM = -100
private const val MAX_DBM = -20

private enum class ScopeBand(val tag: String, val tint: Color) {
    WiFi24("2.4", GREEN), WiFi5("5G", CYAN), WiFi6("6G", AMBER), Ble("BLE", RED), Other("—", MUTED),
}

/** Source filter for the scope — mirrors the concept's Wi-Fi / BLE / SDR tabs. */
private enum class ScopeMode(val label: String) { ALL("ALL"), WIFI("WI-FI"), BT("BT"), SUBGHZ("SUB-GHZ") }

private fun PhoneScanRow.scopeBand(): ScopeBand = when {
    kind == SampleKind.BT -> ScopeBand.Ble
    kind != SampleKind.AP && kind != SampleKind.STATION -> ScopeBand.Other
    channel in 1..14 -> ScopeBand.WiFi24
    channel in 32..177 -> ScopeBand.WiFi5
    channel >= 1000 -> ScopeBand.WiFi6
    else -> ScopeBand.Other
}

// Full-spectrum lane layout (0 until BINS): sub-GHz 0–11, GPS 14–18, cell 19–21,
// 2.4 GHz 22–33, BLE 35, 5 GHz 38–49, 6 GHz 52–59.
private const val CELL_FIRST = 19
private const val CELL_LAST = 21

private fun channelBin(channel: Int, band: ScopeBand): Int = when (band) {
    ScopeBand.WiFi24 -> 22 + (channel - 1).coerceIn(0, 13) * 11 / 13
    ScopeBand.WiFi5 -> 38 + (channel - 32).coerceIn(0, 145) * 11 / 145
    ScopeBand.WiFi6 -> 52 + (channel - 1000).coerceIn(0, 233) * 7 / 233
    ScopeBand.Ble -> BLE_BIN // The phone reports RSSI but no BLE channel. This is a separate lane.
    ScopeBand.Other -> -1
}

/** Sub-GHz frequency (MHz) → bin in the 0–11 lane (300–960 MHz). */
private fun subGhzBin(mhz: Float): Int = (((mhz - 300f) / 660f) * 11f).toInt().coerceIn(0, 11)

/** GNSS constellation → bin in the 14–18 GPS lane. */
private fun gnssBin(constellation: String): Int = when (constellation.uppercase()) {
    "GLONASS" -> 17
    "BEIDOU" -> 15
    else -> 16 // GPS / Galileo / QZSS / SBAS
}

/** Cell dBm (~-120..-50) → terrain height. */
private fun cellStrength(dbm: Int): Float = ((dbm + 120f) / 75f).coerceIn(.12f, 1f)

/** Band for a board-sourced wardrive sample (same lane mapping as the phone's own rows). */
private fun wardriveBand(kind: SampleKind, channel: Int): ScopeBand = when {
    kind == SampleKind.BT -> ScopeBand.Ble
    channel in 1..14 -> ScopeBand.WiFi24
    channel in 32..177 -> ScopeBand.WiFi5
    channel >= 1000 -> ScopeBand.WiFi6
    else -> ScopeBand.Other
}

/** First/last bin of a band's lane, for clipping placeWide. */
private fun laneRange(band: ScopeBand): Pair<Int, Int> = when (band) {
    ScopeBand.WiFi24 -> 22 to 33
    ScopeBand.WiFi5 -> 38 to 49
    ScopeBand.WiFi6 -> 52 to 59
    else -> 34 to 36
}

// A wide, soft falloff so a single contact raises a rolling hill, not a spike. maxOf
// blends overlapping signals into connected ridges — mountains, not sharp triangles.
private val SPREAD = floatArrayOf(1f, .88f, .64f, .40f, .20f, .09f)

/** Paint a signal into [bins] at [center] with a soft ±5 falloff, clipped to [first]..[last]. */
private fun placeWide(bins: FloatArray, center: Int, first: Int, last: Int, level: Float) {
    for (offset in -(SPREAD.size - 1)..(SPREAD.size - 1)) {
        val column = center + offset
        if (column in first..last) {
            bins[column] = maxOf(bins[column], level * SPREAD[kotlin.math.abs(offset)])
        }
    }
}

// Deterministic, slowly drifting noise floor so the whole surface is solid terrain
// instead of bare grid between signals. Textured per bin; drifts with [seed].
private const val NOISE_BASE = 0.045f
private fun seedFloor(bins: FloatArray, seed: Int) {
    for (bin in bins.indices) {
        var h = (bin + 1) * 374761393 + seed * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        val n = ((h ushr 16) and 0xFF) / 255f
        bins[bin] = NOISE_BASE + 0.055f * n
    }
}

/** 3-tap blur that rounds sharp peaks into mountainous ridges. */
private fun smoothBins(bins: FloatArray, passes: Int) {
    repeat(passes) {
        var prev = bins[0]
        for (i in bins.indices) {
            val cur = bins[i]
            val next = if (i + 1 < bins.size) bins[i + 1] else cur
            bins[i] = prev * 0.26f + cur * 0.48f + next * 0.26f
            prev = cur
        }
    }
}

/** A notable RF event the "factor signals" engine picks out of the live stream. */
private data class ScopeSignal(val kind: String, val tint: Color, val detail: String, val tick: Int)

private fun strength(rssi: Float): Float =
    ((rssi - MIN_DBM) / (MAX_DBM - MIN_DBM)).coerceIn(0f, 1f)

private fun heat(level: Float): Color =
    if (level < 0.55f) lerp(GREEN, AMBER, level / 0.55f)
    else lerp(AMBER, EMBER, (level - 0.55f) / 0.45f)

private val FIRE_STOPS = listOf(
    Color(0xFF1B090D), Color(0xFF731724), Color(0xFFBE2919), Color(0xFFF75A22),
    Color(0xFFFF9C43), GOLD, Color(0xFFFFF2C7),
)
private val SURFACE_STOPS = listOf(
    Color(0xFF171850), Color(0xFF2437B8), Color(0xFF258DDD), CYAN,
    Color(0xFF35CF85), Color(0xFFCCE843), AMBER, EMBER, Color(0xFFE84B9E),
)
private val RIBBON = listOf(RED, EMBER, AMBER, GREEN, CYAN, Color(0xFF8566E8))
private val RIBBON_SMOOTHING = floatArrayOf(.72f, .50f, .35f, .23f, .14f, .08f)
private const val PALETTE_STEPS = 48

private fun palette(stops: List<Color>) = Array(PALETTE_STEPS + 1) { step ->
    val position = step.toFloat() / PALETTE_STEPS * (stops.size - 1)
    val lower = position.toInt().coerceAtMost(stops.lastIndex - 1)
    lerp(stops[lower], stops[lower + 1], position - lower)
}

private val TERRAIN_COLORS = palette(FIRE_STOPS)
private val WATERFALL_COLORS = palette(SURFACE_STOPS)

private data class ScopeTrace(val high: Int, val low: Int, val mean: Float, val contacts: Int, val discoveries: Int = 0)
private data class ScopeCandle(val open: Int, val high: Int, val low: Int, val close: Int, val mean: Float, val contacts: Int, val discoveries: Int)
private data class ScopePeak(val bin: Int, val rssi: Int, val band: ScopeBand, val channel: Int)
private data class ScopeSlice(val bins: FloatArray, val peak: ScopePeak?)
private data class ScopeHit(val row: PhoneScanRow, val time: String)
private data class ScopeArrival(val tickMs: Long, val count: Int)
private data class ScopeSnapshot(
    val bins: FloatArray = FloatArray(BINS),
    val trace: ScopeTrace? = null,
    val channelPeak: ScopePeak? = null,
    val profile: IntArray = IntArray(PROFILE_BINS),
    val ranked: List<PhoneScanRow> = emptyList(),
    val contacts: Int = 0,
    val bands: IntArray = IntArray(ScopeBand.entries.size),
    // Per-source load: 0 Wi-Fi, 1 BLE, 2 sub-GHz, 3 GPS, 4 cell.
    val sources: IntArray = IntArray(5),
    val ceiling: Int = 0,
    val floor: Int = 0,
)

private fun <T> MutableList<T>.appendCapped(value: T, limit: Int) {
    if (size == limit) removeAt(0)
    add(value)
}

@Composable
fun ScopeScreen(vm: MainViewModel, onBack: () -> Unit) {
    val terrain = remember(vm) { mutableStateListOf<ScopeSlice>() }
    val trace = remember(vm) { mutableStateListOf<ScopeTrace?>() }
    val feed = remember(vm) { mutableStateListOf<ScopeHit>() }
    val signals = remember(vm) { mutableStateListOf<ScopeSignal>() }
    var current by remember(vm) { mutableStateOf(ScopeSnapshot()) }
    var newPerSecond by remember(vm) { mutableStateOf(0) }
    var mode by remember(vm) { mutableStateOf(ScopeMode.ALL) }
    val enteredAt = remember(vm) { System.currentTimeMillis() }
    val text = rememberTextMeasurer(cacheSize = 96)

    DisposableEffect(vm) {
        vm.startPhoneScan(wifi = true, ble = true)
        vm.startGnss()
        vm.startCell()
        onDispose { vm.stopPhoneScan(); vm.stopGnss(); vm.stopCell() }
    }

    LaunchedEffect(vm) {
        val seen = HashSet<String>()
        val arrivals = ArrayDeque<ScopeArrival>()
        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        var previous: List<PhoneScanRow>? = null
        var rate = 0
        var tick = 0
        var lastMode = ScopeMode.ALL
        while (true) {
            tick++
            if (mode != lastMode) {
                // Source switched — wipe the accumulated terrain/trace so the new source
                // fills in fresh instead of blending with the old one.
                lastMode = mode
                Snapshot.withMutableSnapshot {
                    terrain.clear(); trace.clear(); feed.clear(); signals.clear()
                }
                seen.clear(); previous = null; rate = 0; arrivals.clear()
            }
            val rows = vm.phoneScanResults.value
            val now = System.nanoTime() / 1_000_000L
            var next = current
            var newHits: List<ScopeHit>? = null
            var discovered = 0
            var strongNew = Int.MIN_VALUE
            var strongNewBand = ScopeBand.Other
            if (rows !== previous) {
                previous = rows
                val newest = feed.mapTo(ArrayList(FEED_LIMIT + 1)) { it.row }
                next = summarize(rows, vm.gnssState.value, vm.wardriveSamples?.value?.values,
                    vm.cellState.value, tick, mode) { row ->
                    if (row.lastSeenMs >= enteredAt && seen.add(row.id)) {
                        discovered++
                        if (row.rssi > strongNew) { strongNew = row.rssi; strongNewBand = row.scopeBand() }
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
            // Factor-signals engine: pick notable RF events out of the live stream.
            val event = factorSignal(discovered, strongNew, strongNewBand, tick)
            // Temporal smoothing: ease each slice toward the previous so the range flows
            // front-to-back as rolling terrain rather than hard-edged steps.
            val prevBins = terrain.lastOrNull()?.bins
            val eased = if (prevBins == null) next.bins else FloatArray(BINS) { i ->
                next.bins[i] * 0.7f + prevBins[i] * 0.3f
            }
            Snapshot.withMutableSnapshot {
                current = next
                newPerSecond = rate
                terrain.appendCapped(ScopeSlice(eased, next.channelPeak), TERRAIN_LIMIT)
                trace.appendCapped(next.trace?.copy(discoveries = discovered), TRACE_LIMIT)
                newHits?.let {
                    feed.clear()
                    feed.addAll(it)
                }
                if (event != null) signals.appendCapped(event, 7)
            }
            delay(TICK_MS)
        }
    }

    Scaffold(containerColor = BG, topBar = { ScopeTopBar(text, onBack) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 640.dp
            val heroHeight = (maxWidth * .53f).coerceIn(214.dp, 490.dp)
            val traceHeight = (maxWidth * .34f).coerceIn(154.dp, 260.dp)
            val signalsHeight = (maxWidth * .22f).coerceIn(96.dp, 150.dp)
            val lowerHeight = (maxWidth * .46f).coerceIn(230.dp, 400.dp)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = if (wide) 8.dp else 2.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item { ScopeStats(text, current, newPerSecond) }
                item { ScopeModeTabs(text, mode) { mode = it } }
                item {
                    ScopePanel(text, "◆  3D DEPTH VISUALIZER", "CH / RSSI / HISTORY", heroHeight.value.toInt(),
                        "RF depth, Wi-Fi channels and a separate Bluetooth lane. Newest at the front.") {
                        drawDepth(text, terrain)
                    }
                }
                item { ScopeTracePanel(text, trace, current.profile, traceHeight.value.toInt()) }
                item { ScopeSignalsPanel(text, signals, current, trace, signalsHeight.value.toInt()) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Column(Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            ScopeRankPanel(text, current.ranked, (lowerHeight.value * .40f).toInt())
                            ScopeFeedPanel(text, feed, (lowerHeight.value * .60f).toInt() - 2)
                        }
                        Column(Modifier.weight(1f)) {
                            ScopeWaterfallPanel(text, terrain, lowerHeight.value.toInt())
                        }
                    }
                }
            }
        }
    }
}

private inline fun summarize(
    rows: List<PhoneScanRow>,
    gnss: GnssSnapshot,
    rf: Collection<WardriveSample>?,
    cell: CellSnapshot,
    seed: Int,
    mode: ScopeMode,
    onValid: (PhoneScanRow) -> Unit,
): ScopeSnapshot {
    val bins = FloatArray(BINS)
    seedFloor(bins, seed)
    val profile = IntArray(PROFILE_BINS)
    val ranked = ArrayList<PhoneScanRow>(RANK_LIMIT)
    val bands = IntArray(ScopeBand.entries.size)
    val sources = IntArray(5)
    var channelPeak: ScopePeak? = null
    var high = Int.MIN_VALUE
    var low = Int.MAX_VALUE
    var total = 0L
    var count = 0
    for (row in rows) {
        val rssi = row.rssi
        if (rssi >= 0) continue // Includes the scanner's 127 sentinel.
        val band = row.scopeBand()
        val inMode = when (mode) {
            ScopeMode.ALL -> true
            ScopeMode.WIFI -> band == ScopeBand.WiFi24 || band == ScopeBand.WiFi5 || band == ScopeBand.WiFi6
            ScopeMode.BT -> band == ScopeBand.Ble
            ScopeMode.SUBGHZ -> false
        }
        if (!inMode) continue
        onValid(row)
        high = maxOf(high, rssi)
        low = minOf(low, rssi)
        total += rssi
        count++
        // PhoneScanManager publishes strongest first, including on its final flush.
        if (ranked.size < RANK_LIMIT) ranked.add(row)
        val bucket = (strength(rssi.toFloat()) * PROFILE_BINS).toInt().coerceAtMost(PROFILE_BINS - 1)
        profile[bucket]++
        bands[band.ordinal]++
        val bin = channelBin(row.channel, band)
        if (bin >= 0) {
            val level = strength(rssi.toFloat()).coerceAtLeast(0.06f)
            val first = when (band) {
                ScopeBand.WiFi24 -> 22
                ScopeBand.WiFi5 -> 38
                ScopeBand.WiFi6 -> 52
                else -> 34
            }
            val last = when (band) {
                ScopeBand.WiFi24 -> 33
                ScopeBand.WiFi5 -> 49
                ScopeBand.WiFi6 -> 59
                else -> 36
            }
            placeWide(bins, bin, first, last, level)
            if (channelPeak == null || rssi > channelPeak.rssi) {
                channelPeak = ScopePeak(bin, rssi, band, row.channel)
            }
        }
    }
    // Board-sourced samples (Wi-Fi / BLE / sub-GHz the connected boards pick up). Wi-Fi and
    // BLE paint the same lanes as the phone's own radios, so the terrain shows everything the
    // whole fleet hears, not just the phone; sub-GHz goes in the 0–11 lane by its MHz label.
    rf?.forEach { s ->
        when (s.kind) {
            SampleKind.RF -> {
                if (mode != ScopeMode.ALL && mode != ScopeMode.SUBGHZ) return@forEach
                val mhz = s.label.removeSuffix(" MHz").trim().toFloatOrNull() ?: return@forEach
                placeWide(bins, subGhzBin(mhz), 0, 11, strength(s.rssi.toFloat()).coerceAtLeast(.06f))
                sources[2]++
            }
            SampleKind.AP, SampleKind.STATION, SampleKind.BT -> {
                val band = wardriveBand(s.kind, s.channel)
                val inMode = when (mode) {
                    ScopeMode.ALL -> true
                    ScopeMode.WIFI -> band != ScopeBand.Ble
                    ScopeMode.BT -> band == ScopeBand.Ble
                    ScopeMode.SUBGHZ -> false
                }
                if (!inMode) return@forEach
                val bin = channelBin(s.channel, band)
                if (bin >= 0) {
                    val (first, last) = laneRange(band)
                    placeWide(bins, bin, first, last, strength(s.rssi.toFloat()).coerceAtLeast(.06f))
                    if (band == ScopeBand.Ble) sources[1]++ else sources[0]++
                }
            }
        }
    }
    // GPS / GNSS satellites (14–18 lane) + cell sectors (19–21 lane) — only in the ALL view.
    if (mode == ScopeMode.ALL) {
        for (sat in gnss.sats) {
            if (sat.cn0 <= 0f) continue
            placeWide(bins, gnssBin(sat.constellation), 14, 18, (sat.cn0 / 48f).coerceIn(.06f, 1f))
            sources[3]++
        }
        for (tower in cell.cells) {
            placeWide(bins, (CELL_FIRST + tower.tier).coerceIn(CELL_FIRST, CELL_LAST),
                CELL_FIRST, CELL_LAST, cellStrength(tower.dbm))
            sources[4]++
        }
    }
    // += so the board Wi-Fi/BLE counts tallied above aren't overwritten by the phone's.
    sources[0] += bands[0] + bands[1] + bands[2]
    sources[1] += bands[3]
    // Round the whole surface into rolling mountains rather than sharp per-channel spikes.
    smoothBins(bins, 2)
    val trace = if (count == 0) null else ScopeTrace(high, low, total.toFloat() / count, count)
    val ceiling = if (count == 0) 0 else high
    val floor = if (count == 0) 0 else low
    return ScopeSnapshot(bins, trace, channelPeak, profile, ranked, count, bands, sources, ceiling, floor)
}

/** Pick the single most notable event this tick — the quant "factor signal" repurposed. */
private fun factorSignal(
    discovered: Int, strongNew: Int, strongNewBand: ScopeBand, tick: Int,
): ScopeSignal? = when {
    strongNew >= -52 -> ScopeSignal("LOCK", GOLD, "${strongNewBand.tag}  $strongNew dBm", tick)
    discovered >= 3 -> ScopeSignal("SURGE", EMBER, "+$discovered contacts", tick)
    discovered > 0 -> ScopeSignal("NEW", GREEN, "+$discovered  ${strongNewBand.tag}", tick)
    else -> null
}

@Composable
private fun ScopeTopBar(text: TextMeasurer, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(BG).statusBarsPadding()) {
        Canvas(
            Modifier.height(44.dp).width(44.dp)
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
            Modifier.height(44.dp).weight(1f)
                .semantics { heading(); contentDescription = "RF Depth. Live phone scope." },
        ) {
            caption(text, "RF / DEPTH", 0f, 4.dp.toPx(), INK, 16, bold = true)
            caption(text, "LIVE PHONE SCOPE · WI-FI / BLE", 0f, 25.dp.toPx(), GREEN, 9)
        }
    }
}

@Composable
private fun ScopeStats(text: TextMeasurer, snapshot: ScopeSnapshot, rate: Int) {
    val peak = snapshot.trace?.high?.let { "$it dBm" } ?: "—"
    Canvas(Modifier.fillMaxWidth().height(23.dp).semantics {
        contentDescription = "${snapshot.contacts} contacts, $rate new per second, peak $peak"
    }) {
        drawRect(HEADER)
        val cell = size.width / 7f
        val fields = listOf("● LIVE ${snapshot.contacts}", "2.4 ${snapshot.bands[0]}",
            "5G ${snapshot.bands[1]}", "6G ${snapshot.bands[2]}", "BLE ${snapshot.bands[3]}",
            "+$rate/s", peak)
        val colors = listOf(GREEN, GREEN, CYAN, AMBER, RED, CYAN, GOLD)
        for (index in fields.indices) {
            val x = cell * index
            if (index > 0) drawLine(GRID, Offset(x, 0f), Offset(x, size.height))
            caption(text, fields[index], x + 3.dp.toPx(), 5.dp.toPx(), colors[index], 8, cell - 5.dp.toPx(), true)
        }
        drawLine(AMBER.copy(alpha = .45f), Offset(0f, size.height - 1), Offset(size.width, size.height - 1))
    }
}

@Composable
private fun ScopeModeTabs(text: TextMeasurer, mode: ScopeMode, onPick: (ScopeMode) -> Unit) {
    val modes = ScopeMode.entries
    Canvas(
        Modifier.fillMaxWidth().height(24.dp).pointerInput(Unit) {
            detectTapGestures { tap ->
                val idx = (tap.x / (size.width.toFloat() / modes.size)).toInt().coerceIn(0, modes.size - 1)
                onPick(modes[idx])
            }
        },
    ) {
        drawRect(HEADER)
        val w = size.width / modes.size
        modes.forEachIndexed { i, m ->
            val on = m == mode
            val x = w * i
            if (on) drawRect(EMBER.copy(alpha = .20f), Offset(x, 0f), Size(w, size.height))
            if (i > 0) drawLine(GRID, Offset(x, 0f), Offset(x, size.height))
            caption(text, m.label, x + 6.dp.toPx(), 5.dp.toPx(), if (on) GOLD else MUTED, 9, w - 9.dp.toPx(), on)
        }
        drawLine(AMBER.copy(alpha = .45f), Offset(0f, size.height - 1), Offset(size.width, size.height - 1))
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
        val headerHeight = 17.dp.toPx()
        drawRect(PANEL)
        drawRect(HEADER, size = Size(size.width, headerHeight))
        drawLine(GRID, Offset(0f, headerHeight), Offset(size.width, headerHeight))
        drawRect(GRID, style = Stroke(1.dp.toPx()))
        caption(text, title, 4.dp.toPx(), 2.dp.toPx(), GOLD, 8, size.width * .57f, true)
        caption(text, subtitle, size.width * .59f, 2.dp.toPx(), MUTED, 7)
        clipRect {
            inset(left = 3.dp.toPx(), top = headerHeight + 1.dp.toPx(), right = 3.dp.toPx(), bottom = 2.dp.toPx()) {
                content()
            }
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
    titleFace: Boolean = false,
) {
    val available = width.toInt()
    if (available <= 0) return
    val layout = text.measure(
        text = AnnotatedString(value),
        style = TextStyle(
            fontFamily = if (titleFace) FontFamily.Serif else MONO, fontSize = fontSize.sp,
            fontStyle = if (titleFace) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = available),
    )
    drawText(layout, color = color, topLeft = Offset(x, y))
}

private fun DrawScope.drawDepth(text: TextMeasurer, terrain: List<ScopeSlice>) {
    val width = size.width
    val height = size.height
    drawRect(Brush.verticalGradient(listOf(Color.Black, Color(0xFF270D0D), Color(0xFF090506))))
    val axis = height - 12.dp.toPx()
    val focalBin = terrain.lastOrNull()?.peak?.bin ?: BINS / 2
    fun project(bin: Int, depth: Float, level: Float): Offset {
        val perspective = 1f - .88f * depth
        val relative = (bin - focalBin) / (BINS - 1f)
        val x = width * (.59f - .15f * depth + relative * 1.12f * perspective)
        val floor = axis * (.99f - .61f * depth)
        return Offset(x, floor - level * axis * (.36f - .19f * depth))
    }
    for (bin in 0 until BINS step 2) {
        drawLine(WALL_GRID, project(bin, 0f, 0f), project(bin, 1f, 0f))
    }
    for (line in 0..16) {
        val depth = line / 16f
        drawLine(WALL_GRID, project(0, depth, 0f), project(BINS - 1, depth, 0f))
    }
    drawMesh(terrain, TERRAIN_COLORS, ::project, rainbow = false)

    // Current-peak highlight column (cyan) at the strongest frequency — the concept's CURRENT PEAK.
    val frameNow = terrain.lastOrNull()
    val peakNow = frameNow?.peak
    if (peakNow != null) {
        val lvl = frameNow.bins[peakNow.bin]
        val baseFront = project(peakNow.bin, 0f, 0f)
        val topFront = project(peakNow.bin, 0f, lvl)
        drawLine(CYAN.copy(alpha = .20f), project(peakNow.bin, 1f, 0f), baseFront, 10.dp.toPx())
        drawLine(CYAN.copy(alpha = .85f), baseFront, topFront, 3.dp.toPx())
        drawCircle(CYAN, 3.dp.toPx(), topFront)
    }

    val ridge = Path()
    var connected = false
    for (index in terrain.indices) {
        val frame = terrain[index]
        val peak = frame.peak
        if (peak == null) {
            connected = false
            continue
        }
        val depth = (terrain.lastIndex - index) / TERRAIN_LIMIT.toFloat()
        val point = project(peak.bin, depth, frame.bins[peak.bin])
        if (connected) ridge.lineTo(point.x, point.y) else ridge.moveTo(point.x, point.y)
        connected = true
        if (index % 4 == 0 && depth in .25f..0.85f) {
            val right = Offset(point.x + width * (.18f + depth * .13f), point.y - height * .01f)
            val lift = height * (.045f + frame.bins[peak.bin] * .25f)
            val wall = Path().apply {
                moveTo(point.x, point.y)
                lineTo(point.x, point.y - lift)
                lineTo(right.x, right.y - lift * .92f)
                lineTo(right.x, right.y)
                close()
            }
            drawPath(wall, EMBER.copy(alpha = .12f))
            drawPath(wall, EMBER.copy(alpha = .36f), style = Stroke(1.dp.toPx()))
            drawLine(GOLD.copy(alpha = .30f), point - Offset(0f, lift * .46f),
                right - Offset(0f, lift * .41f))
            drawLine(INK.copy(alpha = .7f), right - Offset(0f, lift * .12f), right, 1.dp.toPx())
        }
    }
    drawPath(ridge, Color(0xFF090F13), style = Stroke(4.dp.toPx()))
    drawPath(ridge, Color(0xFF99E8F3), style = Stroke(1.dp.toPx()))

    caption(text, "R F   D E P T H", width * .43f, 1.dp.toPx(), GOLD, 13, titleFace = true)
    caption(text, "PHONE RF", 3.dp.toPx(), 2.dp.toPx(), INK, 8, bold = true)
    caption(text, "WI-FI / BLE", 3.dp.toPx(), 13.dp.toPx(), MUTED, 7)
    for (index in 0..6) {
        val tint = TERRAIN_COLORS[(6 - index) * 7]
        caption(text, "${-30 - index * 10} dBm", 3.dp.toPx(), (26 + index * 8).dp.toPx(), tint, 7)
    }
    caption(text, "SNAPSHOTS ${terrain.size}/$TERRAIN_LIMIT", width * .72f, 4.dp.toPx(), GOLD, 7)
    caption(text, "FRONT = NEWEST", width * .72f, 15.dp.toPx(), MUTED, 7)
    val frame = terrain.lastOrNull()
    val peak = frame?.peak
    if (peak != null) {
        val point = project(peak.bin, 0f, frame.bins[peak.bin])
        val channel = when (peak.band) {
            ScopeBand.Ble -> "BLE"
            ScopeBand.WiFi6 -> "6G CH ${peak.channel - 1000}"
            else -> "${peak.band.tag} CH ${peak.channel}"
        }
        plotTag(text, "LAST  ${peak.rssi}", point, width * .62f, height * .77f, CYAN, 66)
        plotTag(text, channel, project(peak.bin, .68f, frame.bins[peak.bin]), width * .36f, height * .21f, GOLD, 74)
        plotTag(text, "BASE GRID", project((peak.bin - 5).coerceAtLeast(0), .27f, 0f),
            width * .20f, height * .70f, INK, 62)
        val tapeX = width * .77f
        val tapeY = height * .83f
        drawRect(Color.Black.copy(alpha = .82f), Offset(tapeX, tapeY), Size(width - tapeX, 23.dp.toPx()))
        caption(text, "TAPE  ${peak.band.tag}", tapeX + 3.dp.toPx(), tapeY, GOLD, 7)
        caption(text, "${peak.rssi} dBm", tapeX + 3.dp.toPx(), tapeY + 10.dp.toPx(), INK, 8)
    } else {
        caption(text, "WAITING FOR PHONE SCAN", width * .33f, height * .55f, MUTED, 9)
    }
    caption(text, "SUB · GPS · CELL · 2.4 · 5 · 6 GHz", 3.dp.toPx(), axis, MUTED, 7)
}

private fun DrawScope.plotTag(
    text: TextMeasurer, label: String, point: Offset, targetX: Float, targetY: Float, color: Color, widthDp: Int,
) {
    val width = minOf(widthDp.dp.toPx(), size.width)
    val x = targetX.coerceIn(0f, size.width - width)
    val y = targetY.coerceIn(0f, (size.height - 12.dp.toPx()).coerceAtLeast(0f))
    drawLine(color.copy(alpha = .65f), point, Offset(x + width * .5f, y + 5.dp.toPx()))
    drawRect(color, Offset(x, y), Size(width, 11.dp.toPx()))
    caption(text, label, x + 2.dp.toPx(), y, BG, 7, width - 4.dp.toPx(), true)
}

private fun DrawScope.drawMesh(
    terrain: List<ScopeSlice>, palette: Array<Color>,
    project: (Int, Float, Float) -> Offset, rainbow: Boolean,
) {
    val cell = Path()
    for (index in 1 until terrain.size) {
        val near = (terrain.lastIndex - index) / (TERRAIN_LIMIT - 1f)
        val far = near + 1f / (TERRAIN_LIMIT - 1f)
        val back = terrain[index - 1].bins
        val front = terrain[index].bins
        for (bin in 0 until BINS - 1) {
            val a = back[bin]
            val b = back[bin + 1]
            val c = front[bin + 1]
            val d = front[bin]
            val level = (a + b + c + d) / 4f
            val p1 = project(bin, far, a)
            val p2 = project(bin + 1, far, b)
            val p3 = project(bin + 1, near, c)
            val p4 = project(bin, near, d)
            if (level <= .001f) {
                if (!rainbow) {
                    drawLine(WALL_GRID.copy(alpha = .55f), p4, p3)
                    drawLine(WALL_GRID.copy(alpha = .44f), p1, p4)
                }
                continue
            }
            val peak = maxOf(maxOf(a, b), maxOf(c, d))
            val color = palette[((.40f * level + .60f * peak) * PALETTE_STEPS).toInt()]
            cell.reset()
            cell.moveTo(p1.x, p1.y)
            cell.lineTo(p2.x, p2.y)
            cell.lineTo(p3.x, p3.y)
            cell.lineTo(p4.x, p4.y)
            cell.close()
            drawPath(cell, color.copy(alpha = .95f - near * .22f))
            val grid = if (rainbow) INK.copy(alpha = .15f) else BG.copy(alpha = .32f)
            drawLine(grid, p4, p3)
            drawLine(grid, p1, p4)
        }
    }
}

@Composable
private fun ScopeWaterfallPanel(text: TextMeasurer, terrain: List<ScopeSlice>, height: Int) {
    ScopePanel(text, "◆  RF INTENSITY SURFACE", "3D", height, "Color surface of Wi-Fi and Bluetooth signal strength over time.") {
        drawRect(Color.Black)
        val w = size.width
        val h = size.height
        fun project(bin: Int, depth: Float, level: Float): Offset {
            val x = bin / (BINS - 1f)
            return Offset(w * (.12f + .68f * x + .12f * depth), h * (.77f + .13f * x - .46f * depth - .29f * level))
        }
        for (line in 0..4) {
            val depth = line / 4f
            drawLine(GRID, project(0, depth, 0f), project(BINS - 1, depth, 0f))
        }
        for (bin in 0 until BINS step 7) {
            drawLine(GRID, project(bin, 0f, 0f), project(bin, 1f, 0f))
        }
        drawMesh(terrain, WATERFALL_COLORS, ::project, rainbow = true)
        drawRect(Brush.verticalGradient(SURFACE_STOPS.asReversed()), Offset(w - 6.dp.toPx(), h * .15f),
            Size(4.dp.toPx(), h * .68f))
        caption(text, "RSSI / CHANNEL / TIME", 3.dp.toPx(), 3.dp.toPx(), MUTED, 8)
        caption(text, "OLDER", w * .19f, h * .25f, MUTED, 7)
        caption(text, "CHANNEL / BLE", w * .28f, h * .92f, MUTED, 7)
        terrain.lastOrNull()?.peak?.let {
            val point = project(it.bin, 0f, strength(it.rssi.toFloat()))
            plotTag(text, "${it.rssi} dBm", point, w * .48f, h * .57f, INK, 58)
        }
    }
}

@Composable
private fun ScopeSignalsPanel(
    text: TextMeasurer, signals: List<ScopeSignal>, snap: ScopeSnapshot,
    trace: List<ScopeTrace?>, height: Int,
) {
    val recent = signals.asReversed().joinToString { "${it.kind} ${it.detail}" }
    ScopePanel(text, "◆  RF EVENTS", "LEVEL · HI/LO/AVG", height,
        "Notable RF events, and a live meter of signal level with its trend and running high, low and average. ${recent.ifEmpty { "Listening." }}") {
        val w = size.width
        val h = size.height
        val splitX = w * .44f
        // Left: factor-signal events, newest at top.
        if (signals.isEmpty()) caption(text, "LISTENING…", 3.dp.toPx(), 4.dp.toPx(), MUTED, 9)
        val rowH = 12.dp.toPx()
        val maxRows = ((h - 2.dp.toPx()) / rowH).toInt().coerceAtLeast(1)
        val shown = signals.asReversed().take(maxRows)
        for ((i, s) in shown.withIndex()) {
            val y = i * rowH
            val fade = (1f - i * 0.12f).coerceAtLeast(0.4f)
            drawRect(s.tint.copy(alpha = .13f * fade), Offset(0f, y + 1.dp.toPx()),
                Size(splitX - 5.dp.toPx(), rowH - 2.dp.toPx()))
            drawRect(s.tint.copy(alpha = fade), Offset(0f, y + 1.dp.toPx()), Size(2.dp.toPx(), rowH - 2.dp.toPx()))
            caption(text, s.kind, 6.dp.toPx(), y + .5.dp.toPx(), s.tint.copy(alpha = fade), 8, 34.dp.toPx(), true)
            caption(text, s.detail, 40.dp.toPx(), y + .5.dp.toPx(), INK.copy(alpha = fade), 7, splitX - 44.dp.toPx())
        }
        drawLine(GRID, Offset(splitX, 2.dp.toPx()), Offset(splitX, h - 2.dp.toPx()))

        // Right: a live level meter. Each recent tick is a bar; a gold trend line runs
        // through the bar tops; the current reading is a WHITE bar with a caret on the
        // right edge that rides up and down with the live level; HI/LO/AVG along the base.
        val mLeft = splitX + 5.dp.toPx()
        val mRight = w - 30.dp.toPx()
        val mTop = 3.dp.toPx()
        val mBot = h - 10.dp.toPx()
        val chartH = (mBot - mTop).coerceAtLeast(1f)
        val valid = trace.filterNotNull()
        fun yOf(rssi: Float) = mBot - strength(rssi) * chartH
        if (valid.isEmpty() || mRight <= mLeft) {
            caption(text, "NO SIGNAL", splitX + 6.dp.toPx(), mTop + 2.dp.toPx(), MUTED, 8)
        } else {
            val maxCols = ((mRight - mLeft) / 2.4.dp.toPx()).toInt().coerceIn(1, 72)
            val window = valid.takeLast(maxCols)
            val colW = (mRight - mLeft) / window.size
            val barW = (colW - 1.2.dp.toPx()).coerceAtLeast(1f)
            drawLine(GRID, Offset(mLeft, mBot), Offset(mRight, mBot))
            // bars (current one white, the rest heat-tinted)
            window.forEachIndexed { i, t ->
                val top = yOf(t.mean)
                val x = mLeft + i * colW
                val cur = i == window.lastIndex
                drawRect(if (cur) Color.White else heat(strength(t.mean)).copy(alpha = .5f),
                    Offset(x, top), Size(barW, (mBot - top).coerceAtLeast(0f)))
            }
            // trend line through the bar tops (the curve)
            val path = Path()
            window.forEachIndexed { i, t ->
                val x = mLeft + i * colW + barW / 2f
                val y = yOf(t.mean)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, GOLD, style = Stroke(1.3.dp.toPx()))
            // current reading: white guide line across + caret riding the right edge
            val cur = window.last()
            val curY = yOf(cur.mean)
            drawLine(Color.White.copy(alpha = .45f), Offset(mLeft, curY), Offset(w - 5.dp.toPx(), curY))
            val tri = Path().apply {
                moveTo(w - 5.dp.toPx(), curY)
                lineTo(mRight + 3.dp.toPx(), curY - 3.dp.toPx())
                lineTo(mRight + 3.dp.toPx(), curY + 3.dp.toPx())
                close()
            }
            drawPath(tri, Color.White)
            caption(text, "${cur.mean.toInt()}", mRight + 3.dp.toPx(), curY - 5.dp.toPx(), Color.White, 7, 28.dp.toPx(), true)
            // HI / LO / AVG over the window
            val hi = window.maxOf { it.high }
            val lo = window.minOf { it.low }
            val avg = window.map { it.mean }.average().toInt()
            caption(text, "HI $hi  LO $lo  AVG $avg dBm", splitX + 4.dp.toPx(), h - 9.dp.toPx(),
                GOLD, 7, w - splitX - 6.dp.toPx())
        }
    }
}

@Composable
private fun ScopeTracePanel(text: TextMeasurer, trace: List<ScopeTrace?>, profile: IntArray, height: Int) {
    ScopePanel(text, "◆  FACTOR SIGNALS", "EMA RIBBON · VPVR · VOL", height,
        "RSSI candles, a six-line moving-average ribbon, a volume-at-level profile with POC, a white live-level line, and contact activity.") {
        drawTrace(text, trace, profile)
    }
}

private fun candles(trace: List<ScopeTrace?>): List<ScopeCandle?> =
    trace.chunked(CANDLE_TICKS).map { samples ->
        val valid = samples.filterNotNull()
        if (valid.isEmpty()) null else ScopeCandle(
            open = valid.first().high, high = valid.maxOf { it.high }, low = valid.minOf { it.low },
            close = valid.last().high, mean = valid.map { it.mean }.average().toFloat(),
            contacts = valid.maxOf { it.contacts }, discoveries = valid.sumOf { it.discoveries },
        )
    }

private fun DrawScope.drawTrace(text: TextMeasurer, trace: List<ScopeTrace?>, profile: IntArray) {
    drawRect(Color.Black)
    val left = 2.dp.toPx()
    val right = size.width * .67f
    val top = 23.dp.toPx()
    val bottom = size.height - 28.dp.toPx()
    val chartHeight = bottom - top
    val profileRight = size.width - 25.dp.toPx()
    if (right <= left || chartHeight <= 0f || profileRight <= right) return
    val bars = candles(trace)
    val step = (right - left) / (TRACE_LIMIT / CANDLE_TICKS)
    fun x(index: Int) = right - (bars.size - index - .5f) * step
    fun y(rssi: Float) = bottom - strength(rssi) * chartHeight
    caption(text, "RF  ▪  RSSI  ▪  660 ms", left, 1.dp.toPx(), INK, 8, right - left)
    for (index in RIBBON.indices) {
        val keyX = left + index * 19.dp.toPx()
        caption(text, "MA${index + 1}", keyX, 12.dp.toPx(), RIBBON[index], 7, 18.dp.toPx())
    }

    for (dbm in MIN_DBM..MAX_DBM step 10) {
        val line = y(dbm.toFloat())
        drawLine(GRID.copy(alpha = .65f), Offset(left, line), Offset(profileRight, line))
        caption(text, dbm.toString(), profileRight + 2.dp.toPx(), line - 5.dp.toPx(), MUTED, 7)
    }
    for (division in 0..8) {
        val gridX = left + (right - left) * division / 8
        drawLine(GRID.copy(alpha = .55f), Offset(gridX, top), Offset(gridX, bottom))
    }

    val maxCount = profile.maxOrNull() ?: 0
    val barHeight = chartHeight / PROFILE_BINS
    var mode = -1
    for (bucket in profile.indices) {
        val count = profile[bucket]
        if (count == 0) continue
        if (mode < 0 || count > profile[mode]) mode = bucket
        val length = (profileRight - right - 3.dp.toPx()) * count / maxCount
        val barY = bottom - (bucket + 1) * barHeight
        val tint = lerp(GOLD, EMBER, bucket / PROFILE_BINS.toFloat())
        val size = Size(length, (barHeight - .5.dp.toPx()).coerceAtLeast(1f))
        drawRect(Brush.horizontalGradient(listOf(tint.copy(alpha = .35f), tint),
            startX = profileRight - length, endX = profileRight), Offset(profileRight - length, barY), size)
    }
    if (mode >= 0) {
        val level = bottom - (mode + .5f) * barHeight
        for (segment in 0..24) {
            val px = left + (right - left) * segment / 25
            drawLine(GOLD.copy(alpha = .45f), Offset(px, level), Offset(px + 2.dp.toPx(), level))
        }
        plotTag(text, "POC", Offset(right, level), right - 22.dp.toPx(), level - 5.dp.toPx(), GOLD, 20)
    }

    val maxContacts = bars.maxOfOrNull { it?.contacts ?: 0 }?.coerceAtLeast(1) ?: 1
    val ribbonPaths = Array(RIBBON.size) { Path() }
    val means = FloatArray(RIBBON.size)
    var connected = false
    for (index in bars.indices) {
        val candle = bars[index]
        if (candle == null) {
            connected = false
            continue
        }
        val px = x(index)
        for (line in RIBBON.indices) {
            means[line] = if (connected) means[line] + (candle.mean - means[line]) * RIBBON_SMOOTHING[line]
                else candle.mean
            val py = y(means[line])
            if (connected) ribbonPaths[line].lineTo(px, py) else ribbonPaths[line].moveTo(px, py)
        }
        connected = true
        val barWidth = (step * .66f).coerceAtLeast(1f)
        val height = 11.dp.toPx() * candle.contacts / maxContacts
        val activityBottom = size.height - 11.dp.toPx()
        drawRect(AMBER.copy(alpha = .48f), Offset(px - barWidth / 2, activityBottom - height), Size(barWidth, height))
        if (candle.discoveries > 0) {
            val newHeight = 11.dp.toPx() * candle.discoveries.coerceAtMost(maxContacts) / maxContacts
            drawRect(EMBER, Offset(px - barWidth / 2, activityBottom - newHeight), Size(barWidth, newHeight))
        }
    }
    for (line in RIBBON.indices.reversed()) {
        drawPath(ribbonPaths[line], RIBBON[line].copy(alpha = .11f), style = Stroke(5.dp.toPx()))
        drawPath(ribbonPaths[line], RIBBON[line], style = Stroke(1.dp.toPx()))
    }
    val bodyWidth = (step * .59f).coerceAtLeast(1f)
    for (index in bars.indices) {
        val candle = bars[index] ?: continue
        val px = x(index)
        val open = y(candle.open.toFloat())
        val close = y(candle.close.toFloat())
        val tint = if (candle.close >= candle.open) INK else MUTED
        drawLine(tint.copy(alpha = .68f), Offset(px, y(candle.high.toFloat())), Offset(px, y(candle.low.toFloat())))
        val position = Offset(px - bodyWidth / 2, minOf(open, close))
        val body = Size(bodyWidth, kotlin.math.abs(open - close).coerceAtLeast(1.4.dp.toPx()))
        if (candle.close >= candle.open) drawRect(tint, position, body)
        else drawRect(tint, position, body, style = Stroke(.75.dp.toPx()))
    }
    if (bars.lastOrNull() != null) {
        val current = bars.lastOrNull() ?: return
        val py = y(current.close.toFloat())
        // White live-level line riding up/down with the current RSSI, dashed across to the
        // profile — the "white bar on the right" from the reference, tagged with the value.
        var sx = left
        val dash = 5.dp.toPx(); val gap = 3.dp.toPx()
        while (sx < profileRight) {
            drawLine(Color.White.copy(alpha = .85f), Offset(sx, py),
                Offset((sx + dash).coerceAtMost(profileRight), py), 1.2.dp.toPx())
            sx += dash + gap
        }
        plotTag(text, "${current.close}", Offset(right, py), profileRight, py - 5.dp.toPx(), Color.White, 24)
    }
    drawLine(GRID, Offset(left, size.height - 11.dp.toPx()), Offset(right, size.height - 11.dp.toPx()))
    caption(text, "−26s", left, size.height - 11.dp.toPx(), MUTED, 7)
    caption(text, "−13s", right * .48f, size.height - 11.dp.toPx(), MUTED, 7)
    caption(text, "NOW", right - 17.dp.toPx(), size.height - 11.dp.toPx(), MUTED, 7)
    caption(text, "CONTACTS", right + 6.dp.toPx(), size.height - 12.dp.toPx(), GOLD, 7)
}

@Composable
private fun ScopeRankPanel(text: TextMeasurer, ranked: List<PhoneScanRow>, height: Int) {
    val description = ranked.joinToString { "${it.label.ifBlank { it.id }}, ${it.rssi} dBm" }
    ScopePanel(text, "◆  CONTACTS", "RSSI", height,
        "Strongest signals. ${description.ifEmpty { "No contacts yet." }}") {
        if (ranked.isEmpty()) caption(text, "NO CONTACTS YET", 2.dp.toPx(), 4.dp.toPx(), MUTED, 10)
        val rowHeight = (size.height / RANK_LIMIT).coerceAtMost(18.dp.toPx())
        val meterX = size.width * .44f
        val valueWidth = 26.dp.toPx()
        val meterWidth = (size.width - meterX - valueWidth - 8.dp.toPx()).coerceAtLeast(0f)
        for ((index, signal) in ranked.withIndex()) {
            val y = rowHeight * index
            val level = strength(signal.rssi.toFloat())
            val tint = RIBBON.reversed()[index]
            if (index % 2 == 0) drawRect(HEADER, Offset(0f, y), Size(size.width, rowHeight))
            caption(text, signal.label.ifBlank { signal.id }, 2.dp.toPx(), y + 1.dp.toPx(), tint, 8,
                meterX - 7.dp.toPx())
            drawRect(GRID, Offset(meterX, y + rowHeight * .42f), Size(meterWidth, 3.dp.toPx()))
            drawRect(tint, Offset(meterX, y + rowHeight * .42f), Size(meterWidth * level.coerceAtLeast(0.04f), 3.dp.toPx()))
            caption(text, "${signal.rssi}", size.width - valueWidth, y + 1.dp.toPx(), INK, 8)
        }
    }
}

@Composable
private fun ScopeFeedPanel(text: TextMeasurer, feed: List<ScopeHit>, height: Int) {
    val description = feed.joinToString {
        "${it.time}, ${it.row.scopeBand().tag}, ${it.row.label.ifBlank { it.row.id }}, ${it.row.rssi} dBm"
    }
    ScopePanel(text, "◆  LIVE FEED", "TAIL", height,
        "Detection feed. ${description.ifEmpty { "Waiting for new contacts." }}") {
        if (feed.isEmpty()) caption(text, "WAITING FOR NEW CONTACTS", 2.dp.toPx(), 4.dp.toPx(), MUTED, 10)
        val rowHeight = 11.dp.toPx()
        val bandX = 38.dp.toPx()
        val labelX = 61.dp.toPx()
        val valueWidth = 26.dp.toPx()
        val visible = ((size.height - 10.dp.toPx()) / rowHeight).toInt().coerceIn(0, FEED_LIMIT)
        for ((index, hit) in feed.take(visible).withIndex()) {
            val y = rowHeight * index
            val row = hit.row
            val band = row.scopeBand()
            if (index % 2 == 0) drawRect(HEADER, Offset(0f, y), Size(size.width, rowHeight))
            caption(text, hit.time, 1.dp.toPx(), y, MUTED, 7, bandX - 2.dp.toPx())
            drawRect(band.tint, Offset(bandX, y + 1.dp.toPx()), Size(20.dp.toPx(), 9.dp.toPx()))
            caption(text, band.tag, bandX + 2.dp.toPx(), y, BG, 7, 18.dp.toPx(), true)
            caption(text, row.label.ifBlank { row.id }, labelX, y, INK, 7,
                size.width - labelX - valueWidth - 1.dp.toPx())
            caption(text, "${row.rssi}", size.width - valueWidth, y, heat(strength(row.rssi.toFloat())), 7)
        }
        caption(text, "›  phone.scan | tail", 2.dp.toPx(), size.height - 9.dp.toPx(), GREEN, 7)
    }
}
