package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.model.LogEntry
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import com.cak3d.biscuitmesh.sensors.OuiDb
import com.cak3d.biscuitmesh.wardrive.SampleKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.sqrt
import kotlin.random.Random

// A live force-directed graph of the devices around you: access points, the client
// stations talking to them (edges, from the board's DATA:STA sniff), and loose BLE
// devices. Repulsion spreads everything out, springs pull associated pairs together
// — the SciChart force-directed-graph demo, fed by the radios.

private const val AP = 0
private const val STA = 1
private const val BT = 2

private class GNode(var x: Float, var y: Float, var vx: Float = 0f, var vy: Float = 0f,
                    val id: String, val type: Int, val label: String)
private class Graph(val nodes: List<GNode>, val edges: List<Pair<Int, Int>>)

private val NODE_AP = Color(0xFF22D3EE)
private val NODE_STA = Color(0xFFFF5470)
private val NODE_BT = Color(0xFF7C9CFF)
private val EDGE = Color(0x33FFFFFF)

private fun nodeColor(t: Int) = when (t) { AP -> NODE_AP; STA -> NODE_STA; else -> NODE_BT }

private fun buildGraph(scan: List<com.cak3d.biscuitmesh.sensors.PhoneScanRow>, log: List<LogEntry>): Graph {
    val byId = LinkedHashMap<String, GNode>()
    val rnd = Random(1234)
    fun pos() = 0.35f + rnd.nextFloat() * 0.30f
    fun ensure(id: String, type: Int, label: String): GNode =
        byId.getOrPut(id) { GNode(pos(), pos(), id = id, type = type, label = label) }

    // Phone-seen APs and BLE as nodes (strongest first, capped).
    scan.filter { it.kind == SampleKind.AP }.sortedByDescending { it.rssi }.take(40)
        .forEach { ensure(it.id.lowercase(), AP, it.label.ifBlank { "(hidden)" }) }
    scan.filter { it.kind == SampleKind.BT }.sortedByDescending { it.rssi }.take(30)
        .forEach { ensure(it.id.lowercase(), BT, it.label.ifBlank { "·" }) }

    // Board station sniff: station -> AP edges (creates any missing AP).
    val edges = ArrayList<Pair<Int, Int>>()
    log.asSequence()
        .filter { it.message.type == BiscuitMessage.Type.DATA && it.message.subtype == "STA" }
        .mapNotNull {
            val f = it.message.payload.split(",")
            val mac = f.getOrNull(0)?.takeIf { s -> s.isNotBlank() } ?: return@mapNotNull null
            val bssid = f.getOrNull(1)?.takeIf { s -> s.isNotBlank() } ?: return@mapNotNull null
            mac.lowercase() to bssid.lowercase()
        }
        .distinct().take(80)
        .forEach { (mac, bssid) ->
            val sta = ensure(mac, STA, OuiDb.vendor(mac) ?: "client")
            val ap = ensure(bssid, AP, "AP")
            edges.add(byId.keys.indexOf(sta.id) to byId.keys.indexOf(ap.id))
        }

    val nodes = byId.values.toList()
    val idx = nodes.withIndex().associate { (i, n) -> n.id to i }
    val fixedEdges = edges.mapNotNull { (a, b) ->
        val ai = idx[nodes.getOrNull(a)?.id]; val bi = idx[nodes.getOrNull(b)?.id]
        if (ai != null && bi != null) ai to bi else null
    }
    return Graph(nodes, fixedEdges)
}

private fun step(g: Graph) {
    val n = g.nodes
    val kRep = 0.00035f
    for (i in n.indices) {
        var fx = 0f; var fy = 0f
        val a = n[i]
        for (j in n.indices) {
            if (i == j) continue
            val b = n[j]
            var dx = a.x - b.x; var dy = a.y - b.y
            var d2 = dx * dx + dy * dy
            if (d2 < 1e-4f) { dx = (Random.nextFloat() - 0.5f) * 0.01f; dy = (Random.nextFloat() - 0.5f) * 0.01f; d2 = 1e-4f }
            val f = kRep / d2
            val inv = 1f / sqrt(d2)
            fx += dx * inv * f; fy += dy * inv * f
        }
        // centering pull
        fx += (0.5f - a.x) * 0.012f
        fy += (0.5f - a.y) * 0.012f
        a.vx = (a.vx + fx) * 0.82f
        a.vy = (a.vy + fy) * 0.82f
    }
    // springs
    for ((ai, bi) in g.edges) {
        val a = n[ai]; val b = n[bi]
        val dx = b.x - a.x; val dy = b.y - a.y
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
        val f = (d - 0.12f) * 0.06f
        val ux = dx / d; val uy = dy / d
        a.vx += ux * f; a.vy += uy * f
        b.vx -= ux * f; b.vy -= uy * f
    }
    for (node in n) {
        node.x = (node.x + node.vx).coerceIn(0.04f, 0.96f)
        node.y = (node.y + node.vy).coerceIn(0.04f, 0.96f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForceGraphScreen(vm: MainViewModel, onBack: () -> Unit) {
    val scan by vm.phoneScanResults.collectAsStateWithLifecycle(initialValue = emptyList())
    val logFlow = vm.log ?: remember { MutableStateFlow(emptyList<LogEntry>()) }
    val log by logFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Rebuild only when the set of devices/edges changes, so positions persist and settle.
    val signature = remember(scan, log) {
        val ids = scan.filter { it.kind == SampleKind.AP || it.kind == SampleKind.BT }.map { it.id }
        val staIds = log.count { it.message.type == BiscuitMessage.Type.DATA && it.message.subtype == "STA" }
        (ids.sorted().joinToString() + "|" + staIds)
    }
    val graph = remember(signature) { buildGraph(scan, log) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(graph) {
        while (true) { withFrameNanos { }; step(graph); tick++ }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Graph") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            Canvas(Modifier.fillMaxSize()) {
                if (tick < 0) return@Canvas   // reads the frame tick so each step redraws
                val w = size.width; val h = size.height
                fun px(nx: Float) = nx * w
                fun py(ny: Float) = ny * h
                for ((ai, bi) in graph.edges) {
                    val a = graph.nodes[ai]; val b = graph.nodes[bi]
                    drawLine(EDGE, Offset(px(a.x), py(a.y)), Offset(px(b.x), py(b.y)), strokeWidth = 1.5f)
                }
                for (node in graph.nodes) {
                    val c = nodeColor(node.type)
                    val r = when (node.type) { AP -> 9f; STA -> 5f; else -> 6f }
                    drawCircle(c.copy(alpha = 0.22f), r + 4f, Offset(px(node.x), py(node.y)))
                    drawCircle(c, r, Offset(px(node.x), py(node.y)))
                }
                // AP labels, drawn with the native canvas so names are legible.
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(200, 180, 220, 255)
                        textSize = 22f; isAntiAlias = true
                    }
                    graph.nodes.filter { it.type == AP }.forEach { node ->
                        drawText(node.label.take(16), px(node.x) + 11f, py(node.y) + 7f, paint)
                    }
                }
            }
            if (graph.nodes.isEmpty()) {
                Text(
                    "No devices yet. Scan on the Wi-Fi or Bluetooth tab, or run a station sniff on a board to draw the links.",
                    Modifier.fillMaxWidth().padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            } else {
                Text(
                    "${graph.nodes.count { it.type == AP }} APs · ${graph.nodes.count { it.type == STA }} clients · ${graph.nodes.count { it.type == BT }} BLE · ${graph.edges.size} links",
                    Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
