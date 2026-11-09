package com.cak3d.biscuitmesh.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.sensors.Sat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private fun constColorG(c: String): Color = when (c) {
    "GPS" -> Color(0xFF22D3EE)
    "GLONASS" -> Color(0xFFFF7AD9)
    "Galileo" -> Color(0xFF34D399)
    "BeiDou" -> Color(0xFFF59E0B)
    "QZSS" -> Color(0xFF7C9CFF)
    "SBAS" -> Color(0xFFA78BFA)
    "NavIC" -> Color(0xFFFF8A3D)
    else -> Color(0xFF9CA3AF)
}

// A 3D point on the unit sphere, with the spin + tilt applied, projected to screen.
private class P3(val x: Float, val y: Float, val z: Float)

private fun project(x: Float, y: Float, z: Float, cosS: Float, sinS: Float, cosT: Float, sinT: Float): P3 {
    // spin about the vertical (Y) axis
    val x1 = x * cosS + z * sinS
    val z1 = -x * sinS + z * cosS
    // tilt about X so the poles aren't edge-on
    val y2 = y * cosT - z1 * sinT
    val z2 = y * sinT + z1 * cosT
    return P3(x1, y2, z2) // z2 is depth (toward viewer = +)
}

/**
 * Orbit — a live 3D globe of the sky overhead. A slowly turning wire-frame Earth with the
 * GNSS satellites the phone can see floating in the dome above you, colored by constellation
 * and filled when they're locked into the fix. This is the globe the flat sky-plot hints at.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GlobeScreen(vm: MainViewModel, onBack: () -> Unit) {
    val gnss by vm.gnssState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        vm.startGnss()
        onDispose { vm.stopGnss() }
    }
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "a",
    )
    var selected by remember { mutableStateOf<Sat?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Orbit") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("head") {
                Card(Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ORBIT", style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF22D3EE), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(
                            if (!gnss.supported) "This device doesn't report satellite status."
                            else "${gnss.usedCount} locked · ${gnss.visibleCount} overhead. Filled = used in the fix.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item("globe") {
                Card(Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF04070A))) {
                    Box(Modifier.padding(10.dp)) {
                        GlobeCanvas(gnss.sats, spin, Modifier.fillMaxWidth().aspectRatio(1f)) { selected = it }
                    }
                }
            }
            if (gnss.sats.isNotEmpty()) {
                val byConst = gnss.sats.groupBy { it.constellation }.toList().sortedByDescending { it.second.size }
                item("legend") {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        byConst.forEach { (name, sats) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(shape = CircleShape, color = constColorG(name), modifier = Modifier.size(10.dp)) {}
                                Text("$name ${sats.count { it.usedInFix }}/${sats.size}",
                                    style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            } else if (gnss.supported) {
                item("wait") {
                    Text("Acquiring satellites… step outside for a clear view of the sky.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }
        // Tap a satellite on the globe for its live stats (re-looked-up each frame so it updates).
        val sel = selected
        if (sel != null) {
            val live = gnss.sats.firstOrNull { it.svid == sel.svid && it.constellation == sel.constellation } ?: sel
            SatDetailDialog(live) { selected = null }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SatDetailDialog(sat: Sat, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = CircleShape, color = constColorG(sat.constellation), modifier = Modifier.size(12.dp)) {}
                Text("${sat.constellation} · SV ${sat.svid}", fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatLine("Signal", "${"%.0f".format(sat.cn0)} dB-Hz")
                StatLine("In fix", if (sat.usedInFix) "yes — locked" else "no")
                StatLine("Elevation", "${"%.0f".format(sat.elevationDeg)}°")
                StatLine("Azimuth", "${"%.0f".format(sat.azimuthDeg)}°")
                Text(
                    if (sat.usedInFix) "Contributing to your position fix right now."
                    else "Visible but not used in the current fix.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
    )
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GlobeCanvas(sats: List<Sat>, spin: Float, modifier: Modifier, onSelect: (Sat) -> Unit) {
    val cosS = cos(spin); val sinS = sin(spin)
    val tilt = 0.42f // ~24° so the poles show
    val cosT = cos(tilt); val sinT = sin(tilt)
    // Screen position of each drawn satellite, refreshed every frame by the draw pass so a tap
    // can hit-test against where the (slowly spinning) dots actually are right now.
    val hitPoints = remember { ArrayList<Pair<Sat, Offset>>() }
    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { tap ->
                val nearest = hitPoints.minByOrNull { (it.second - tap).getDistanceSquared() }
                if (nearest != null && (nearest.second - tap).getDistance() <= 48.dp.toPx()) onSelect(nearest.first)
            }
        },
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = min(size.width, size.height) / 2f * 0.52f
        fun scr(p: P3) = Offset(cx + p.x * r, cy - p.y * r)
        fun depth(p: P3) = (p.z + 1f) / 2f // 0 (back) .. 1 (front)

        val gridFront = Color(0xFF2FA36A)
        val gridBack = Color(0xFF123B28)

        // Meridians (lines of longitude)
        var lon = 0
        while (lon < 360) {
            val a = Math.toRadians(lon.toDouble())
            var prev: P3? = null
            var lat = -90
            while (lat <= 90) {
                val la = Math.toRadians(lat.toDouble())
                val x = (cos(la) * sin(a)).toFloat(); val y = sin(la).toFloat(); val z = (cos(la) * cos(a)).toFloat()
                val p = project(x, y, z, cosS, sinS, cosT, sinT)
                if (prev != null) {
                    val d = (depth(p) + depth(prev!!)) / 2f
                    drawLine(lerpC(gridBack, gridFront, d).copy(alpha = 0.25f + d * 0.5f), scr(prev!!), scr(p), 1f)
                }
                prev = p; lat += 12
            }
            lon += 30
        }
        // Parallels (lines of latitude)
        var plat = -60
        while (plat <= 60) {
            val la = Math.toRadians(plat.toDouble())
            var prev: P3? = null
            var l2 = 0
            while (l2 <= 360) {
                val a = Math.toRadians(l2.toDouble())
                val x = (cos(la) * sin(a)).toFloat(); val y = sin(la).toFloat(); val z = (cos(la) * cos(a)).toFloat()
                val p = project(x, y, z, cosS, sinS, cosT, sinT)
                if (prev != null) {
                    val d = (depth(p) + depth(prev!!)) / 2f
                    drawLine(lerpC(gridBack, gridFront, d).copy(alpha = 0.22f + d * 0.45f), scr(prev!!), scr(p), 1f)
                }
                prev = p; l2 += 12
            }
            plat += 30
        }

        // Observer at the north pole (local zenith = +Y), and the satellite dome above it.
        val obs = project(0f, 1f, 0f, cosS, sinS, cosT, sinT)
        // Draw satellites, far ones first so near ones sit on top.
        val drawn = sats.filter { it.elevationDeg >= 0f }.map { s ->
            val el = Math.toRadians(s.elevationDeg.toDouble())
            val az = Math.toRadians(s.azimuthDeg.toDouble())
            // local sky direction around the pole: zenith = +Y, horizon spreads into the XZ plane
            val x = (cos(el) * sin(az)).toFloat()
            val y = sin(el).toFloat()
            val z = (cos(el) * cos(az)).toFloat()
            val p = project(x * 1.5f, y * 1.5f, z * 1.5f, cosS, sinS, cosT, sinT)
            Triple(s, p, project(x, y, z, cosS, sinS, cosT, sinT)) // sat point + its surface anchor
        }.sortedBy { it.second.z }

        hitPoints.clear()
        drawn.forEach { (s, p, anchor) ->
            val col = constColorG(s.constellation)
            val d = depth(p)
            val a = scr(anchor); val b = scr(p)
            hitPoints.add(s to b)
            drawLine(col.copy(alpha = 0.12f + d * 0.2f), a, b, 1f) // tether to the sky point
            val dot = (5f + (s.cn0 / 50f).coerceIn(0f, 1f) * 7f) * (0.65f + d * 0.5f)
            if (s.usedInFix) {
                drawCircle(col.copy(alpha = 0.25f * d + 0.1f), dot + 4f, b)
                drawCircle(col.copy(alpha = 0.5f + d * 0.5f), dot, b)
            } else {
                drawCircle(col.copy(alpha = 0.4f + d * 0.4f), dot, b, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            }
        }
        // Observer marker
        drawCircle(Color(0xFF00E5A0), 4f, scr(obs))
    }
}

private fun lerpC(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * tt,
        green = a.green + (b.green - a.green) * tt,
        blue = a.blue + (b.blue - a.blue) * tt,
        alpha = 1f,
    )
}
