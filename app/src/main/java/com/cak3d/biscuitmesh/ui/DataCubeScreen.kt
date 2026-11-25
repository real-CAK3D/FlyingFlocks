package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.sensors.OuiDb
import com.cak3d.biscuitmesh.sensors.PhoneScanRow
import com.cak3d.biscuitmesh.wardrive.SampleKind
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val MONO = FontFamily.Monospace

/**
 * A layer drawn on one FACE of the cube (top, a side wall, or the bottom) rather than a flat
 * horizontal slice — so the four layers wrap the cube like the reference multilayer diagram. Each
 * plane has a centre and two in-plane unit basis vectors (u, v); a node's (x, z) maps onto them.
 */
private class LayerPlane(
    val name: String, val tint: Color,
    val cx: Float, val cy: Float, val cz: Float,   // plane centre
    val ux: Float, val uy: Float, val uz: Float,   // in-plane basis u
    val vx: Float, val vy: Float, val vz: Float,   // in-plane basis v
)

private val LAYERS = listOf(
    // Wi-Fi 2.4 — TOP face (y = +1)
    LayerPlane("Wi-Fi 2.4", Color(0xFF5FD36A), 0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
    // Wi-Fi 5/6 — LEFT wall (x = -1)
    LayerPlane("Wi-Fi 5/6", Color(0xFF22D3EE), -1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
    // Bluetooth — RIGHT wall (x = +1)
    LayerPlane("Bluetooth", Color(0xFFFF6FA5), 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
    // Satellites — BOTTOM face (y = -1)
    LayerPlane("Satellites", Color(0xFFFFD166), 0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
)

/** 3D point of a node at plane-local (nx, nz) on its layer's face. */
private fun nodePoint(layer: Int, nx: Float, nz: Float): FloatArray {
    val L = LAYERS[layer]
    return floatArrayOf(
        L.cx + L.ux * nx + L.vx * nz,
        L.cy + L.uy * nx + L.vy * nz,
        L.cz + L.uz * nx + L.vz * nz,
    )
}

/** Which related nodes to wire up when one is selected. */
private enum class NeighborMode(val label: String) { VENDOR("Vendor"), LAYER("Layer"), OFF("Off") }

/** One plotted contact: layer index, position on its plane, signal 0..1, and its detail. */
private data class CubeNode(
    val id: String,
    val label: String,
    val layer: Int,
    val sig: Float,
    val x: Float,
    val z: Float,
    val sub: String,
    /** Grouping key for cross-layer neighbours: OUI vendor (Wi-Fi/BT) or constellation (sats). */
    val group: String,
    val detail: List<Pair<String, String>>,
)

/** Deterministic position on a plane from an id, so a device keeps its spot frame to frame. */
private fun hashPos(id: String): Pair<Float, Float> {
    var h = 2166136261.toInt()
    for (c in id) h = (h xor c.code) * 16777619
    val a = ((h ushr 1) and 0xFFFF) / 65535f
    val b = ((h ushr 17) and 0xFFFF) / 65535f
    return (a * 2f - 1f) * 0.78f to (b * 2f - 1f) * 0.78f
}

private fun rssiSig(rssi: Int): Float = ((rssi + 100f) / 70f).coerceIn(0.05f, 1f)

private fun ago(ms: Long): String {
    val s = (System.currentTimeMillis() - ms) / 1000
    return when {
        s < 60 -> "${s}s ago"
        s < 3600 -> "${s / 60}m ago"
        else -> "${s / 3600}h ago"
    }
}

/**
 * Data Cube — the whole live picture in one rotatable 3D diagram. Four stacked layers (Wi-Fi 2.4,
 * Wi-Fi 5/6, Bluetooth, satellites), each a plane of contacts the app is hearing right now, sized by
 * signal and wired to their nearest neighbour. Drag to spin the cube; tap any node to read it —
 * name, vendor, signal, channel and when it was last seen. A living map of everything collected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataCubeScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { OuiDb.ensureLoaded(ctx) }
    DisposableEffect(vm) {
        vm.startPhoneScan(wifi = true, ble = true)
        vm.startGnss()
        onDispose { vm.stopPhoneScan(); vm.stopGnss() }
    }
    val rows by vm.phoneScanResults.collectAsState()
    val gnss by vm.gnssState.collectAsState()

    // Session sightings history: first/last seen + how many scan cycles each contact appeared in.
    val history = remember { HashMap<String, Sighting>() }
    val nodes = remember(rows, gnss) {
        updateHistory(history, rows, gnss)
        buildNodes(rows, gnss, history)
    }
    // Nearest-neighbour edges within each layer (index pairs into [nodes]).
    val edges = remember(nodes) { buildEdges(nodes) }

    var rotY by remember { mutableFloatStateOf(0.6f) }
    var rotX by remember { mutableFloatStateOf(0.5f) }
    var autoSpin by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<CubeNode?>(null) }
    var neighborMode by remember { mutableStateOf(NeighborMode.VENDOR) }

    // Which nodes to wire to the selected one (shared vendor, same layer, or none).
    val neighborIds = remember(selected, neighborMode, nodes) {
        val s = selected
        when {
            s == null || neighborMode == NeighborMode.OFF -> emptySet()
            neighborMode == NeighborMode.VENDOR -> if (s.group.isBlank()) emptySet()
                else nodes.asSequence().filter { it.id != s.id && it.group == s.group }.map { it.id }.toSet()
            else -> nodes.asSequence().filter { it.id != s.id && it.layer == s.layer }.map { it.id }.toSet()
        }
    }

    LaunchedEffect(autoSpin) { while (autoSpin) { rotY += 0.004f; delay(33) } }

    Scaffold(
        containerColor = Color(0xFF04070A),
        topBar = {
            TopAppBar(
                title = { Text("Data Cube", fontFamily = MONO) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Data Cube",
                        "Everything the app is hearing, inside one rotatable, see-through 3D cube. Four " +
                            "layers wrap the cube's faces — Wi-Fi 2.4 on top, Wi-Fi 5/6 and Bluetooth on the " +
                            "side walls, satellites on the floor — each a plane of live contacts sized by " +
                            "signal and linked to their nearest neighbour. The cube " +
                            "is drawn as a wireframe so the front and back never hide the sides. Drag to spin; " +
                            "tap a node to read its name, vendor, signal, channel, when it was last seen, when " +
                            "you first saw it and how many times this session. The LINK control wires a tapped " +
                            "node to its neighbours — everything from the same vendor across all layers, or " +
                            "everything on its own layer.",
                    )
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            val hits = remember { ArrayList<Triple<Float, Float, CubeNode>>() }

            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(onDragStart = { autoSpin = false }) { change, drag ->
                            change.consume()
                            rotY += drag.x * 0.006f
                            rotX = (rotX + drag.y * 0.006f).coerceIn(-1.3f, 1.3f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val hit = hits.minByOrNull { (x, y, _) ->
                                val dx = x - tap.x; val dy = y - tap.y; dx * dx + dy * dy
                            }?.takeIf { (x, y, _) ->
                                val dx = x - tap.x; val dy = y - tap.y; dx * dx + dy * dy < 80f * 80f
                            }?.third
                            selected = hit
                            if (hit != null) autoSpin = false
                        }
                    },
            ) {
                hits.clear()
                val cx = cos(rotX); val sx = sin(rotX)
                val cy = cos(rotY); val sy = sin(rotY)
                val scale = size.minDimension * 0.34f
                val center = Offset(size.width / 2f, size.height * 0.46f)

                fun project(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
                    val x1 = x * cy + z * sy; val z1 = -x * sy + z * cy
                    val y1 = y * cx - z1 * sx; val z2 = y * sx + z1 * cx
                    val persp = 1f / (1f + z2 * 0.22f)
                    return Triple(center.x + x1 * scale * persp, center.y - y1 * scale * persp, z2)
                }

                // The containing cube: 12 edges only (no face fills), so the front and rear are
                // see-through and you read the layers inside accurately. Near edges draw brighter.
                val corners = listOf(
                    Triple(-1f, -1.1f, -1f), Triple(1f, -1.1f, -1f), Triple(1f, -1.1f, 1f), Triple(-1f, -1.1f, 1f),
                    Triple(-1f, 1.1f, -1f), Triple(1f, 1.1f, -1f), Triple(1f, 1.1f, 1f), Triple(-1f, 1.1f, 1f),
                ).map { (x, y, z) -> project(x, y, z) }
                val cubeEdges = listOf(
                    0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4,
                    0 to 4, 1 to 5, 2 to 6, 3 to 7,
                )
                cubeEdges.forEach { (i, j) ->
                    val a = corners[i]; val b = corners[j]
                    val depth = (a.third + b.third) / 2f
                    val alpha = (0.5f - depth * 0.24f).coerceIn(0.1f, 0.6f)
                    drawLine(Color(0xFF9FB4C9).copy(alpha = alpha),
                        Offset(a.first, a.second), Offset(b.first, b.second), strokeWidth = 1.5f)
                }

                fun proj(p: FloatArray) = project(p[0], p[1], p[2])

                // Layer planes — each laid on a cube face (top / left wall / right wall / bottom).
                for (li in LAYERS.indices) {
                    val tint = LAYERS[li].tint
                    val p0 = proj(nodePoint(li, -0.95f, -0.95f)); val p1 = proj(nodePoint(li, 0.95f, -0.95f))
                    val p2 = proj(nodePoint(li, 0.95f, 0.95f)); val p3 = proj(nodePoint(li, -0.95f, 0.95f))
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(p0.first, p0.second); lineTo(p1.first, p1.second)
                        lineTo(p2.first, p2.second); lineTo(p3.first, p3.second); close()
                    }
                    drawPath(path, tint.copy(alpha = 0.06f))
                    drawPath(path, tint.copy(alpha = 0.35f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                }

                // Intra-layer edges.
                edges.forEach { (i, j) ->
                    val a = nodes[i]; val b = nodes[j]
                    val pa = proj(nodePoint(a.layer, a.x, a.z))
                    val pb = proj(nodePoint(b.layer, b.x, b.z))
                    drawLine(LAYERS[a.layer].tint.copy(alpha = 0.22f),
                        Offset(pa.first, pa.second), Offset(pb.first, pb.second), strokeWidth = 1f)
                }

                // Nodes, painter-sorted back to front.
                val projected = nodes.map { n -> n to proj(nodePoint(n.layer, n.x, n.z)) }
                    .sortedBy { it.second.third }
                val sel = selected
                val linkCol = sel?.let { LAYERS[it.layer].tint }

                // Cross-layer neighbour links fan out from the selected node in its layer colour.
                if (sel != null && linkCol != null && neighborIds.isNotEmpty()) {
                    val sp = projected.firstOrNull { it.first.id == sel.id }?.second
                    if (sp != null) {
                        projected.forEach { (n, p) ->
                            if (n.id in neighborIds) {
                                drawLine(linkCol.copy(alpha = 0.55f), Offset(sp.first, sp.second),
                                    Offset(p.first, p.second), strokeWidth = 1.5f)
                            }
                        }
                    }
                }

                projected.forEach { (n, p) ->
                    val tint = LAYERS[n.layer].tint
                    val isSel = sel?.id == n.id
                    val isNbr = n.id in neighborIds
                    val r = (2.5f + n.sig * 6f) * (if (isSel) 1.7f else if (isNbr) 1.3f else 1f)
                    drawCircle(tint, radius = r, center = Offset(p.first, p.second))
                    if (isSel) drawCircle(tint.copy(alpha = 0.4f), radius = r + 7f,
                        center = Offset(p.first, p.second),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                    else if (isNbr && linkCol != null) drawCircle(linkCol.copy(alpha = 0.6f), radius = r + 4f,
                        center = Offset(p.first, p.second),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                    hits.add(Triple(p.first, p.second, n))
                }
            }

            // Layer legend / counts.
            Column(Modifier.align(Alignment.TopStart).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                LAYERS.indices.forEach { li ->
                    val name = LAYERS[li].name; val tint = LAYERS[li].tint
                    val n = nodes.count { it.layer == li }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Canvas(Modifier.size(9.dp)) { drawCircle(tint) }
                        Text("$name  $n", color = tint, fontFamily = MONO, fontSize = 10.sp)
                    }
                }
            }
            Text("${nodes.size} contacts · drag to spin · tap a node",
                color = Color(0xFF8A9896), fontFamily = MONO, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))

            // Neighbour-link mode — what a tapped node wires to (like the reference's Select Neighbors).
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("LINK", color = Color(0xFF8A9896), fontFamily = MONO, fontSize = 9.sp)
                NeighborMode.entries.forEach { m ->
                    val on = neighborMode == m
                    Surface(
                        color = if (on) Color(0x33FFD166) else Color(0x1AFFFFFF),
                        shape = RoundedCornerShape(5.dp),
                        modifier = Modifier.clickable { neighborMode = m },
                    ) {
                        Text(m.label, Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            color = if (on) Color(0xFFFFD166) else Color(0xFFBFC9C7),
                            fontFamily = MONO, fontSize = 10.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            selected?.let { n ->
                NodeCard(n, if (neighborIds.isNotEmpty()) neighborIds.size else null,
                    neighborMode, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun NodeCard(n: CubeNode, neighborCount: Int?, mode: NeighborMode, modifier: Modifier) {
    val tint = LAYERS[n.layer].tint
    Card(
        modifier = modifier.fillMaxWidth().padding(12.dp)
            .border(1.5.dp, tint.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(n.label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = tint)
                Text(LAYERS[n.layer].name, fontFamily = MONO, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            if (n.sub.isNotBlank()) Text(n.sub, fontFamily = MONO, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            if (mode != NeighborMode.OFF && neighborCount != null && neighborCount > 0) {
                Text("↔ $neighborCount linked by ${mode.label.lowercase()}", fontFamily = MONO, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, color = tint)
            }
            n.detail.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(k, fontFamily = MONO, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text(v, fontFamily = MONO, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                }
            }
            Text("tap empty space to close", fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** One contact's session history: when first/last seen and how many scan cycles it appeared in. */
private class Sighting(val firstMs: Long, var lastMs: Long, var count: Int)

/** Fold the current scan into the running session history (one tick per new scan emission). */
private fun updateHistory(
    store: MutableMap<String, Sighting>, rows: List<PhoneScanRow>,
    gnss: com.cak3d.biscuitmesh.sensors.GnssSnapshot,
) {
    val now = System.currentTimeMillis()
    val ids = ArrayList<String>()
    rows.forEach { if (it.kind == SampleKind.AP || it.kind == SampleKind.BT) ids.add(it.id) }
    gnss.sats.forEach { if (it.cn0 > 0f) ids.add("${it.constellation}-${it.svid}") }
    ids.forEach { id ->
        val s = store[id]
        if (s == null) store[id] = Sighting(now, now, 1) else { s.lastMs = now; s.count++ }
    }
}

private val DATE_FMT = java.text.SimpleDateFormat("MMM d · h:mm a", java.util.Locale.US)
private fun fmtDate(ms: Long): String = DATE_FMT.format(java.util.Date(ms))

/** Session history rows for a contact's detail card. */
private fun histDetail(h: Sighting?): List<Pair<String, String>> =
    if (h == null) emptyList() else listOf(
        "first seen" to fmtDate(h.firstMs),
        "times seen" to "${h.count} (session)",
    )

private fun buildNodes(
    rows: List<PhoneScanRow>, gnss: com.cak3d.biscuitmesh.sensors.GnssSnapshot,
    history: Map<String, Sighting>,
): List<CubeNode> {
    val out = ArrayList<CubeNode>()
    fun wifiNode(r: PhoneScanRow, layer: Int) {
        val (x, z) = hashPos(r.id)
        val vendor = OuiDb.vendor(r.id) ?: if (OuiDb.isRandom(r.id)) "randomized" else "unknown"
        out.add(CubeNode(
            id = r.id, label = r.label.ifBlank { r.id }, layer = layer, sig = rssiSig(r.rssi), x = x, z = z,
            sub = "${r.rssi} dBm · ch ${r.channel}",
            group = OuiDb.vendor(r.id) ?: "",
            detail = listOf(
                "BSSID" to r.id, "vendor" to vendor,
                "security" to r.security.ifBlank { "—" },
                "band" to if (r.is5GHz) "5/6 GHz" else "2.4 GHz",
                "last seen" to ago(r.lastSeenMs),
            ) + histDetail(history[r.id]),
        ))
    }
    rows.asSequence().filter { it.kind == SampleKind.AP && it.channel in 1..14 }
        .sortedByDescending { it.rssi }.take(45).forEach { wifiNode(it, 0) }
    rows.asSequence().filter { it.kind == SampleKind.AP && it.channel >= 32 }
        .sortedByDescending { it.rssi }.take(45).forEach { wifiNode(it, 1) }
    rows.asSequence().filter { it.kind == SampleKind.BT }
        .sortedByDescending { it.rssi }.take(45).forEach { r ->
            val (x, z) = hashPos(r.id)
            val vendor = OuiDb.vendor(r.id) ?: if (OuiDb.isRandom(r.id)) "randomized" else "unknown"
            out.add(CubeNode(
                id = r.id, label = r.label.ifBlank { r.id }, layer = 2, sig = rssiSig(r.rssi), x = x, z = z,
                sub = "${r.rssi} dBm", group = OuiDb.vendor(r.id) ?: "",
                detail = listOf("MAC" to r.id, "vendor" to vendor, "last seen" to ago(r.lastSeenMs)) +
                    histDetail(history[r.id]),
            ))
        }
    gnss.sats.asSequence().filter { it.cn0 > 0f }.sortedByDescending { it.cn0 }.take(45).forEach { s ->
        val id = "${s.constellation}-${s.svid}"
        val (x, z) = hashPos(id)
        out.add(CubeNode(
            id = id, label = "${s.constellation} ${s.svid}", layer = 3,
            sig = (s.cn0 / 48f).coerceIn(0.05f, 1f), x = x, z = z,
            sub = "${s.cn0.toInt()} dB-Hz${if (s.usedInFix) " · in fix" else ""}",
            group = "sat:${s.constellation}",
            detail = listOf(
                "constellation" to s.constellation, "svid" to s.svid.toString(),
                "C/N0" to "${s.cn0.toInt()} dB-Hz", "elevation" to "${s.elevationDeg.toInt()}°",
                "azimuth" to "${s.azimuthDeg.toInt()}°", "in fix" to if (s.usedInFix) "yes" else "no",
            ) + histDetail(history[id]),
        ))
    }
    return out
}

/** For each node, link it to the nearest other node on the same plane. Returns index pairs. */
private fun buildEdges(nodes: List<CubeNode>): List<Pair<Int, Int>> {
    val edges = HashSet<Long>()
    val out = ArrayList<Pair<Int, Int>>()
    for (i in nodes.indices) {
        val a = nodes[i]
        var best = -1; var bestD = Float.MAX_VALUE
        for (j in nodes.indices) {
            if (j == i) continue
            val b = nodes[j]
            if (b.layer != a.layer) continue
            val dx = a.x - b.x; val dz = a.z - b.z; val d = dx * dx + dz * dz
            if (d < bestD) { bestD = d; best = j }
        }
        if (best >= 0) {
            val key = (minOf(i, best).toLong() shl 32) or maxOf(i, best).toLong()
            if (edges.add(key)) out.add(i to best)
        }
    }
    return out
}
