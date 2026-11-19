package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.model.LogEntry
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import com.cak3d.biscuitmesh.wardrive.WardriveSample
import com.cak3d.biscuitmesh.wardrive.WardriveState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

private const val WINDOW = 60   // seconds of history kept on screen

/** A fixed-length scrolling ring of floats; newest value on the right. */
private class Ring(val n: Int = WINDOW) {
    val v = FloatArray(n)
    fun push(x: Float) { System.arraycopy(v, 1, v, 0, n - 1); v[n - 1] = x }
    fun max() = v.maxOrNull() ?: 0f
}

private data class Series(val label: String, val color: Color, val ring: Ring, val filled: Boolean)

/**
 * A live traffic dashboard in the spirit of a server-traffic board, but fed by what the
 * kit actually collects: 802.11 frame throughput, device-discovery rate, sub-GHz pings
 * and per-board contribution — all scrolling in real time off the fleet's DATA streams.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrafficDashboardScreen(vm: MainViewModel, onBack: () -> Unit) {
    // Unconditional remember of fallbacks, so a missing fleet/wardrive doesn't make the
    // collect calls conditional (which would corrupt composition slots).
    val fallbackLog = remember { MutableStateFlow<List<LogEntry>>(emptyList()) }
    val fallbackSamples = remember { MutableStateFlow<Map<String, WardriveSample>>(emptyMap()) }
    val fallbackState = remember { MutableStateFlow(WardriveState()) }
    val log by (vm.log ?: fallbackLog).collectAsStateWithLifecycle()
    val samples by (vm.wardriveSamples ?: fallbackSamples).collectAsStateWithLifecycle()
    val wd by (vm.wardriveState ?: fallbackState).collectAsStateWithLifecycle()

    // Rings for each scrolling series.
    val beacons = remember { Ring() }
    val probes = remember { Ring() }
    val dataF = remember { Ring() }
    val deauths = remember { Ring() }
    val apRate = remember { Ring() }
    val staRate = remember { Ring() }
    val btRate = remember { Ring() }
    val rfRate = remember { Ring() }
    val devTotal = remember { Ring() }

    var lastTickMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var lastDevCount by remember { mutableStateOf(0) }
    var pktPerSec by remember { mutableStateOf(0) }
    var discPerSec by remember { mutableStateOf(0) }
    var rfPerSec by remember { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0) }

    // Once a second, bucket the last second of the fleet log into the rings.
    LaunchedEffectTicker {
        val now = System.currentTimeMillis()
        val since = lastTickMs
        lastTickMs = now
        val window = log.asSequence().filter { it.timeMs > since && it.timeMs <= now }.toList()

        fun countOf(sub: String) = window.count {
            it.message.type == BiscuitMessage.Type.DATA && it.message.subtype == sub
        }
        // DATA:PKT is a per-second frame-counter line (beacons,deauths,probes,data). Take the
        // newest one in the window as this second's throughput.
        val pkt = window.lastOrNull {
            it.message.type == BiscuitMessage.Type.DATA && it.message.subtype == "PKT"
        }?.message?.payload?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
        val b = pkt?.getOrNull(0) ?: 0
        val d = pkt?.getOrNull(1) ?: 0
        val p = pkt?.getOrNull(2) ?: 0
        val da = pkt?.getOrNull(3) ?: 0
        beacons.push(b.toFloat()); deauths.push(d.toFloat()); probes.push(p.toFloat()); dataF.push(da.toFloat())
        pktPerSec = b + d + p + da

        val ap = countOf("AP"); val sta = countOf("STA"); val bt = countOf("BT"); val rf = countOf("RF")
        apRate.push(ap.toFloat()); staRate.push(sta.toFloat()); btRate.push(bt.toFloat()); rfRate.push(rf.toFloat())
        rfPerSec = rf

        val dev = samples.size
        val grew = (dev - lastDevCount).coerceAtLeast(0)
        lastDevCount = dev
        discPerSec = grew
        devTotal.push(dev.toFloat())

        tick++   // nudge recomposition so the Canvas redraws
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Traffic Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton("Traffic Dashboard",
                        "A live board of what the kit is collecting right now — 802.11 frame throughput, " +
                            "device-discovery rate, sub-GHz pings and each board's share — scrolling in " +
                            "real time off the fleet's data streams. Start a wardrive to feed it.")
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // KPI strip.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kpi("FRAMES/s", pktPerSec.toString(), Color(0xFF22D3EE), Modifier.weight(1f))
                Kpi("NEW DEV/s", discPerSec.toString(), Color(0xFF34D399), Modifier.weight(1f))
                Kpi("RF/s", rfPerSec.toString(), Color(0xFFFF8A3D), Modifier.weight(1f))
                Kpi("DEVICES", samples.size.toString(), Color(0xFFA855F7), Modifier.weight(1f))
            }

            val ignored = tick   // read tick so these cards recompose each second

            ChartCard("802.11 FRAME THROUGHPUT", "frames per second", listOf(
                Series("beacons", Color(0xFF22D3EE), beacons, true),
                Series("probes", Color(0xFF34D399), probes, false),
                Series("data", Color(0xFF7C9CFF), dataF, false),
                Series("deauths", Color(0xFFFF5470), deauths, false),
            ))

            ChartCard("DEVICE DISCOVERY RATE", "new sightings per second", listOf(
                Series("APs", Color(0xFF22D3EE), apRate, true),
                Series("stations", Color(0xFFFFD166), staRate, false),
                Series("bluetooth", Color(0xFFA855F7), btRate, false),
            ))

            ChartCard("SUB-GHZ ACTIVITY", "RF pings per second", listOf(
                Series("433/915 MHz", Color(0xFFFF8A3D), rfRate, true),
            ))

            ChartCard("TOTAL DEVICES SEEN", "cumulative this session", listOf(
                Series("devices", Color(0xFF34D399), devTotal, true),
            ))

            // Per-board contribution bars (cumulative sightings tagged to each source).
            val counts = wd.sourceCounts
            if (counts.isNotEmpty()) BoardBars(counts, vm)

            if (!wd.active) {
                Text("Idle — start a wardrive (and connect your boards) to feed the board live.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun ChartCard(title: String, sub: String, series: List<Series>) {
    Card(Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary)
            Text(sub, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp)) { drawSeries(series) }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                series.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(s.color))
                        Text(s.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSeries(series: List<Series>) {
    val w = size.width; val h = size.height
    // grid baseline
    drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, h), Offset(w, h), 1f)
    drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, h / 2f), Offset(w, h / 2f), 1f)
    val maxY = (series.maxOf { it.ring.max() }).coerceAtLeast(1f)
    val n = WINDOW
    val dx = w / (n - 1)
    series.forEach { s ->
        val path = Path()
        val fill = Path()
        for (i in 0 until n) {
            val x = i * dx
            val y = h - (s.ring.v[i] / maxY) * h * 0.92f
            if (i == 0) { path.moveTo(x, y); fill.moveTo(x, h); fill.lineTo(x, y) }
            else { path.lineTo(x, y); fill.lineTo(x, y) }
        }
        if (s.filled) {
            fill.lineTo(w, h); fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.35f), s.color.copy(alpha = 0f))))
        }
        drawPath(path, s.color, style = Stroke(2f))
    }
}

@Composable
private fun BoardBars(counts: Map<String, Int>, vm: MainViewModel) {
    Card(Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("PER-BOARD CONTRIBUTION", style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary)
            val maxC = (counts.values.maxOrNull() ?: 1).coerceAtLeast(1)
            counts.entries.sortedByDescending { it.value }.forEach { (src, c) ->
                val name = if (src == "phone") "Phone" else vm.nameFor(src)
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("$c", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).padding(top = 2.dp)) {
                        Box(Modifier.fillMaxWidth(c.toFloat() / maxC).height(6.dp).clip(CircleShape)
                            .background(Color(0xFF22D3EE)))
                    }
                }
            }
        }
    }
}

/** Runs [onTick] roughly once a second for as long as the composable is on screen. Reads the
 *  latest lambda each tick (via rememberUpdatedState) so it sees fresh log/sample snapshots. */
@Composable
private fun LaunchedEffectTicker(onTick: suspend () -> Unit) {
    val current = androidx.compose.runtime.rememberUpdatedState(onTick)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) { current.value(); delay(1000) }
    }
}
