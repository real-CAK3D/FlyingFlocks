package com.cak3d.biscuitmesh.ui

import android.os.BatteryManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.mesh.MeshLinkState
import com.cak3d.biscuitmesh.usb.WtfLinkState
import com.cak3d.biscuitmesh.wardrive.WardriveState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val BG = Color(0xFF05080A)
private val PANEL = Color(0xFF0B1014)
private val GREEN = Color(0xFF00FF9C)
private val CYAN = Color(0xFF22D3EE)
private val AMBER = Color(0xFFF5A623)
private val RED = Color(0xFFFF5470)
private val GRID = Color(0xFF00FF9C).copy(alpha = 0.18f)
private const val RADAR_RANGE_M = 3000.0

/** Maxed-out single-screen mission control: radar, charts, readouts, and a live log — no page scroll. */
@Composable
fun LiveScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val progress by vm.progress.collectAsStateWithLifecycle()
    val mph by vm.useMph.collectAsStateWithLifecycle()
    val cameras by vm.hazardsState.collectAsStateWithLifecycle()
    val meshNodes by vm.meshNodes.collectAsStateWithLifecycle()
    val meshLink by vm.meshLink.collectAsStateWithLifecycle()
    val wtfLink by vm.wtfLink.collectAsStateWithLifecycle()
    val wtfGps by vm.wtfGps.collectAsStateWithLifecycle()
    val alert by vm.hazardAlert.collectAsStateWithLifecycle()
    val wsFlow = remember { vm.wardriveState ?: MutableStateFlow(WardriveState()) }
    val ws by wsFlow.collectAsStateWithLifecycle()
    val samplesFlow = vm.wardriveSamples

    val actTrace = remember { mutableStateListOf<Float>() }
    val sigTrace = remember { mutableStateListOf<Float>() }
    val feed = remember { mutableStateListOf<String>() }
    val batt = remember { mutableStateListOf(100) }  // single-cell holder for phone battery %

    fun push(tag: String, msg: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        feed.add("$t $tag $msg"); if (feed.size > 120) feed.removeAt(0)
    }

    LaunchedEffect(Unit) {
        push("SYS", "live console online")
        val bm = context.getSystemService(BatteryManager::class.java)
        while (true) {
            val s = wsFlow.value
            val n = samplesFlow?.value?.size ?: 0
            val bestSig = samplesFlow?.value?.values?.maxOfOrNull { it.rssi } ?: -110
            actTrace.add(n.toFloat()); if (actTrace.size > 60) actTrace.removeAt(0)
            sigTrace.add((bestSig + 110).toFloat().coerceAtLeast(0f)); if (sigTrace.size > 60) sigTrace.removeAt(0)
            batt[0] = runCatching { bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1 }.getOrDefault(-1)
            val loc = if (s.lastLat != null && s.lastLon != null) "%.5f,%.5f".format(s.lastLat, s.lastLon) else "no-fix"
            val spd = s.speedMps?.let { if (mph) "%.0fmph".format(it * 2.23694f) else "%.0fkmh".format(it * 3.6f) } ?: "0"
            push("TLM", "gps=$loc spd=$spd sig=${bestSig}dBm wifi=${s.wifiCount} bt=${s.btCount}" + if (s.inDeadZone) " [DEADZONE]" else "")
            kotlinx.coroutines.delay(1300)
        }
    }
    LaunchedEffect(alert?.hazard?.id) { alert?.let { push("CAM", "${it.hazard.kind} @ ${it.distanceM}m") } }
    LaunchedEffect(wtfLink) { push("WTF", "dongle ${wtfLink.name.lowercase()}") }
    LaunchedEffect(wtfGps) { wtfGps?.let { push("WTF", "gps fix=${it.fix} sats=${it.sats}") } }
    LaunchedEffect(meshLink) { push("MESH", "link ${meshLink.name.lowercase()}") }
    LaunchedEffect(meshNodes.size) { if (meshNodes.isNotEmpty()) push("MESH", "${meshNodes.size} nodes: ${meshNodes.joinToString { it.name.trim() }}") }
    LaunchedEffect(progress.rank) { push("RANK", "${progress.rank} ${progress.rankName} ${progress.levelXp}xp") }

    val logState = rememberLazyListState()
    LaunchedEffect(feed.size) { if (feed.isNotEmpty()) logState.animateScrollToItem(feed.size - 1) }

    val blips = remember(cameras, meshNodes, ws.lastLat, ws.lastLon) {
        val lat0 = ws.lastLat; val lon0 = ws.lastLon
        if (lat0 == null || lon0 == null) emptyList()
        else buildList {
            cameras.forEach { addBlip(lat0, lon0, it.lat, it.lon, RED)?.let(::add) }
            meshNodes.forEach { n -> val la = n.lat; val lo = n.lon; if (la != null && lo != null) addBlip(lat0, lon0, la, lo, CYAN)?.let(::add) }
        }
    }
    val sweep by rememberInfiniteTransition(label = "r").animateFloat(
        0f, 360f, infiniteRepeatable(tween(4500, easing = LinearEasing), RepeatMode.Restart), label = "s")

    val spd = ws.speedMps?.let { if (mph) it * 2.23694f else it * 3.6f } ?: 0f
    val meshBatt = meshNodes.mapNotNull { it.battery }.maxOrNull()
    val dongleOn = wtfLink == WtfLinkState.CONNECTED

    Column(Modifier.fillMaxSize().background(BG).padding(horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GREEN) }
            Text("flyingflocks", color = GREEN, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(" // LIVE", color = AMBER, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }

        // radar + readouts
        Row(Modifier.fillMaxWidth().weight(1.15f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxHeight().weight(1.05f), contentAlignment = Alignment.Center) {
                Radar(blips, sweep, Modifier.fillMaxWidth().aspectRatio(1f))
            }
            Column(Modifier.fillMaxHeight().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Readout("RANK", "${progress.rank}", progress.rankName.take(11), GREEN)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MiniStat("WIFI", "${ws.wifiCount}", CYAN, Modifier.weight(1f))
                    MiniStat("BT", "${ws.btCount}", CYAN, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MiniStat("CAMS", "${cameras.size}", RED, Modifier.weight(1f))
                    MiniStat("MESH", "${meshNodes.size}", if (meshLink == MeshLinkState.CONNECTED) GREEN else GRID, Modifier.weight(1f))
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    BarChart(listOf(
                        Triple("WIFI", ws.wifiCount.toFloat(), CYAN),
                        Triple("BT", ws.btCount.toFloat(), GREEN),
                        Triple("CAM", cameras.size.toFloat(), RED),
                        Triple("MESH", meshNodes.size.toFloat(), AMBER),
                    ), Modifier.fillMaxSize())
                }
            }
        }

        // power + location strip
        Row(Modifier.fillMaxWidth().height(30.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("PWR", "${batt[0].coerceAtLeast(0)}%", if (batt[0] in 1..20) RED else GREEN)
            Chip("MESH⚡", meshBatt?.let { "$it%" } ?: "--", if (meshBatt != null) CYAN else GRID)
            Chip("DONGLE", if (dongleOn) "ON" else "off", if (dongleOn) GREEN else GRID)
            Chip("SAT", "${wtfGps?.sats ?: 0}", AMBER)
            Chip("ACC", ws.accuracy?.let { "${it.toInt()}m" } ?: "--", GREEN)
        }

        // gauges + graphs
        Row(Modifier.fillMaxWidth().height(66.dp).padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f)) {
                Text("SPEED", color = AMBER.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                Text("%.0f".format(spd), color = AMBER, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                Text(if (mph) "mph" else "km/h", color = Color.White.copy(alpha = 0.4f), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
            }
            GraphCol("ACTIVITY", actTrace, CYAN, Modifier.weight(1.4f))
            GraphCol("SIGNAL", sigTrace, GREEN, Modifier.weight(1.4f))
        }

        // LIVE LOG (scrolls inside its own panel)
        Column(Modifier.fillMaxWidth().weight(0.95f).background(PANEL).padding(horizontal = 6.dp, vertical = 3.dp)) {
            Text("root@flyingflocks:~# tail -f live", color = GREEN.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            LazyColumn(state = logState, modifier = Modifier.fillMaxSize()) {
                items(feed) { line ->
                    val color = when {
                        " CAM " in line -> RED
                        " WTF " in line || " MESH " in line -> CYAN
                        " RANK " in line || " SYS " in line -> AMBER
                        " TLM " in line -> GREEN.copy(alpha = 0.55f)
                        else -> GREEN
                    }
                    Text(line, color = color, fontFamily = FontFamily.Monospace, fontSize = 9.sp, lineHeight = 12.sp)
                }
            }
        }
    }
}

// --- helpers ---------------------------------------------------------------

private fun addBlip(lat0: Double, lon0: Double, lat: Double, lon: Double, color: Color): Triple<Float, Float, Color>? {
    val dN = (lat - lat0) * 111320.0
    val dE = (lon - lon0) * 111320.0 * cos(Math.toRadians(lat0))
    val dist = hypot(dN, dE)
    if (dist > RADAR_RANGE_M) return null
    val r = (dist / RADAR_RANGE_M).toFloat()
    val ang = atan2(dE, dN)
    return Triple((sin(ang) * r).toFloat(), (-cos(ang) * r).toFloat(), color)
}

@Composable
private fun Radar(blips: List<Triple<Float, Float, Color>>, sweepDeg: Float, modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val rad = min(size.width, size.height) / 2f * 0.92f
        for (i in 1..3) drawCircle(GRID, rad * i / 3f, c, style = Stroke(1f))
        drawCircle(GREEN.copy(alpha = 0.5f), rad, c, style = Stroke(1.5f))
        drawLine(GRID, Offset(c.x - rad, c.y), Offset(c.x + rad, c.y), 1f)
        drawLine(GRID, Offset(c.x, c.y - rad), Offset(c.x, c.y + rad), 1f)
        val lead = Math.toRadians(sweepDeg.toDouble() - 90.0)
        for (t in 0 until 28) {
            val a = lead - Math.toRadians(t * 1.7)
            drawLine(GREEN.copy(alpha = (1f - t / 28f) * 0.33f), c,
                Offset(c.x + (cos(a) * rad).toFloat(), c.y + (sin(a) * rad).toFloat()), 2f)
        }
        blips.forEach { (x, y, color) ->
            val p = Offset(c.x + x * rad, c.y + y * rad)
            drawCircle(color.copy(alpha = 0.25f), 9f, p); drawCircle(color, 4.5f, p)
        }
        drawCircle(GREEN, 4f, c)
    }
}

@Composable
private fun GraphCol(label: String, data: List<Float>, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = color.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Canvas(Modifier.fillMaxSize()) {
            if (data.size < 2) return@Canvas
            val maxV = (data.maxOrNull() ?: 1f).coerceAtLeast(1f)
            val stepX = size.width / (data.size - 1)
            var prev = Offset(0f, size.height - data[0] / maxV * size.height)
            for (i in 1 until data.size) {
                val p = Offset(i * stepX, size.height - data[i] / maxV * size.height)
                drawLine(color, prev, p, 2f); prev = p
            }
            drawLine(GRID, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
        }
    }
}

@Composable
private fun BarChart(bars: List<Triple<String, Float, Color>>, modifier: Modifier) {
    val maxV = bars.maxOf { it.second }.coerceAtLeast(1f)
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        bars.forEach { (label, v, color) ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${v.toInt()}", color = color, fontFamily = FontFamily.Monospace, fontSize = 8.sp)
                Box(Modifier.fillMaxWidth().fillMaxHeight(((v / maxV) * 0.8f).coerceIn(0.02f, 0.8f))
                    .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.3f)))))
                Text(label, color = color.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace, fontSize = 7.sp)
            }
        }
    }
}

@Composable
private fun Readout(label: String, value: String, sub: String, accent: Color) {
    Column(Modifier.fillMaxWidth().background(PANEL).padding(horizontal = 6.dp, vertical = 3.dp)) {
        Text(label, color = accent.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("  $sub", color = Color.White.copy(alpha = 0.45f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(modifier.background(PANEL).padding(horizontal = 5.dp, vertical = 2.dp)) {
        Text(label, color = accent.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 7.sp)
        Text(value, color = accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun Chip(label: String, value: String, accent: Color) {
    Row(Modifier.background(PANEL).padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$label ", color = accent.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace, fontSize = 8.sp)
        Text(value, color = accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}
