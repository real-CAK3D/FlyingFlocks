package com.cak3d.biscuitmesh.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The distinct loader animation a hub section shows while a selection opens. */
enum class LoaderTheme { NETWORK, RADAR, EQUALIZER, ORBIT, SONAR, BROADCAST, BARS, GEARS }

private const val TAU = (2 * PI).toFloat()

/**
 * A branded loading screen. Each hub category gets its own [theme] — a radar sweep, a spectrum, an
 * orbit, a sonar ping, broadcast waves, climbing bars or turning gears — all in the section's
 * [accent], with the section [icon] at the core, a "● …" status and a LOADING-NN% counter. [seed]
 * adds per-selection jitter; the spin is always clockwise so it reads consistently.
 */
@Composable
fun SectionLoader(
    title: String,
    accent: Color,
    icon: ImageVector,
    seed: Int,
    modifier: Modifier = Modifier,
    subtitle: String = "INITIALIZING",
    theme: LoaderTheme = LoaderTheme.NETWORK,
) {
    val rnd = remember(seed) { Lcg(seed) }
    val spinMs = remember(seed) { 5200 + (rnd.next() * 2600).toInt() }

    // --- NETWORK-only geometry (the original low-poly sphere) ---
    val nodes = remember(seed) {
        val n = 30
        val golden = (PI * (3.0 - sqrt(5.0))).toFloat()
        val base = rnd.next() * TAU
        Array(n) { i ->
            val y = 1f - 2f * (i + 0.5f) / n + (rnd.next() - 0.5f) * 0.04f
            val rr = sqrt((1f - y * y).coerceAtLeast(0f))
            val th = i * golden + base
            floatArrayOf(rr * cos(th), y, rr * sin(th))
        }
    }
    val edges = remember(seed) {
        val list = ArrayList<Pair<Int, Int>>()
        for (i in nodes.indices) for (j in i + 1 until nodes.size) {
            val d = nodes[i][0] * nodes[j][0] + nodes[i][1] * nodes[j][1] + nodes[i][2] * nodes[j][2]
            if (d > 0.62f) list.add(i to j)
            if (list.size >= 46) break
        }
        list.toList()
    }
    val hotspots = remember(seed) {
        val n = 2 + (rnd.next() * 2).toInt()
        (0 until n).map { (rnd.next() * nodes.size).toInt().coerceIn(0, nodes.size - 1) }.distinct()
    }

    val t = rememberInfiniteTransition(label = "loader")
    val spin by t.animateFloat(
        0f, TAU, infiniteRepeatable(tween(spinMs, easing = LinearEasing)), label = "spin",
    )
    val pulse by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "pulse",
    )
    val phase by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "phase",
    )
    val breathe by t.animateFloat(
        0.5f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe",
    )
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(100f, tween(1350, easing = LinearEasing)) }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(210.dp)) {
                    val r = size.minDimension / 2f * 0.82f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    when (theme) {
                        LoaderTheme.NETWORK -> drawNetwork(accent, nodes, edges, hotspots, spin, pulse, breathe, c, r)
                        LoaderTheme.RADAR -> drawRadar(accent, spin, c, r)
                        LoaderTheme.EQUALIZER -> drawEqualizer(accent, phase, c, r)
                        LoaderTheme.ORBIT -> drawOrbit(accent, spin, c, r)
                        LoaderTheme.SONAR -> drawSonar(accent, pulse, c, r)
                        LoaderTheme.BROADCAST -> drawBroadcast(accent, pulse, c, r)
                        LoaderTheme.BARS -> drawBars(accent, phase, c, r)
                        LoaderTheme.GEARS -> drawGears(accent, spin, c, r)
                    }
                }
                Icon(icon, contentDescription = null, tint = accent.copy(alpha = breathe), modifier = Modifier.size(28.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title.uppercase(), color = accent, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 2.5.sp)
                Text("● ${subtitle.uppercase()}", color = accent.copy(alpha = 0.55f + 0.4f * breathe),
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 3.sp)
                Text("LOADING ${progress.value.toInt()}%",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.sp)
            }
        }
    }
}

// ---------------- per-theme draw functions ----------------

private fun DrawScope.drawNetwork(
    accent: Color, nodes: Array<FloatArray>, edges: List<Pair<Int, Int>>, hotspots: List<Int>,
    spin: Float, pulse: Float, breathe: Float, c: Offset, r: Float,
) {
    val px = FloatArray(nodes.size); val py = FloatArray(nodes.size); val pz = FloatArray(nodes.size)
    val sa = sin(spin); val ca = cos(spin)
    for (k in nodes.indices) {
        val x = nodes[k][0]; val y = nodes[k][1]; val z = nodes[k][2]
        px[k] = c.x + (x * ca + z * sa) * r; py[k] = c.y - y * r; pz[k] = -x * sa + z * ca
    }
    drawCircle(accent.copy(alpha = 0.12f * breathe), radius = r, center = c, style = Stroke(width = 1.5f))
    edges.forEach { (i, j) ->
        val front = ((pz[i] + pz[j]) / 2f + 1f) / 2f
        if (front < 0.28f) return@forEach
        drawLine(accent.copy(alpha = 0.06f + 0.22f * front), Offset(px[i], py[i]), Offset(px[j], py[j]), strokeWidth = 1f)
    }
    for (k in nodes.indices) {
        val front = (pz[k] + 1f) / 2f
        drawCircle(accent.copy(alpha = 0.18f + 0.6f * front), radius = 1.3f + 1.8f * front, center = Offset(px[k], py[k]))
    }
    hotspots.forEach { k ->
        if (pz[k] < 0f) return@forEach
        val o = Offset(px[k], py[k])
        for (s in 0..1) {
            val p = (pulse + s * 0.5f) % 1f
            drawCircle(accent.copy(alpha = (1f - p) * 0.55f), radius = 3f + p * 20f, center = o, style = Stroke(width = 1.4f))
        }
        drawCircle(accent.copy(alpha = 0.95f), radius = 3.4f, center = o)
    }
}

/** Sense & Navigate — a radar sweep with blips that light as the beam crosses them. */
private fun DrawScope.drawRadar(accent: Color, spin: Float, c: Offset, r: Float) {
    drawCircle(accent.copy(alpha = 0.06f), radius = r, center = c)
    for (ring in 1..3) drawCircle(accent.copy(alpha = 0.16f), radius = r * ring / 3f, center = c, style = Stroke(1f))
    drawLine(accent.copy(alpha = 0.14f), Offset(c.x - r, c.y), Offset(c.x + r, c.y))
    drawLine(accent.copy(alpha = 0.14f), Offset(c.x, c.y - r), Offset(c.x, c.y + r))
    // trailing wedge behind the beam
    for (k in 0..20) {
        val a = spin - k * 0.05f
        drawLine(accent.copy(alpha = (1f - k / 20f) * 0.3f), c, Offset(c.x + cos(a) * r, c.y + sin(a) * r), strokeWidth = 2f)
    }
    drawLine(accent, c, Offset(c.x + cos(spin) * r, c.y + sin(spin) * r), strokeWidth = 2.5f)
    val blips = listOf(0.6f to 0.55f, 2.1f to 0.8f, 3.5f to 0.4f, 4.8f to 0.68f, 5.6f to 0.5f)
    blips.forEach { (ba, bf) ->
        var past = (spin - ba) % TAU; if (past < 0) past += TAU
        val lit = (1f - past / TAU).let { it * it * it }
        val o = Offset(c.x + cos(ba) * r * bf, c.y + sin(ba) * r * bf)
        drawCircle(accent.copy(alpha = 0.15f + 0.85f * lit), radius = 2f + 3f * lit, center = o)
    }
}

/** Live Visuals — a bouncing spectrum/equalizer. */
private fun DrawScope.drawEqualizer(accent: Color, phase: Float, c: Offset, r: Float) {
    val n = 11
    val span = r * 1.9f
    val barW = span / n * 0.58f
    val baseY = c.y + r * 0.72f
    for (i in 0 until n) {
        val h = r * 1.5f * (0.12f + 0.88f * abs(sin(phase * TAU * 1.2f + i * 0.6f)))
        val x = c.x - span / 2f + (i + 0.5f) * (span / n)
        drawRoundRect(
            accent.copy(alpha = 0.5f + 0.5f * (h / (r * 1.5f))),
            topLeft = Offset(x - barW / 2f, baseY - h), size = Size(barW, h),
            cornerRadius = CornerRadius(barW / 2f),
        )
    }
    drawLine(accent.copy(alpha = 0.25f), Offset(c.x - span / 2f, baseY), Offset(c.x + span / 2f, baseY))
}

/** Sky — satellites orbiting a core. */
private fun DrawScope.drawOrbit(accent: Color, spin: Float, c: Offset, r: Float) {
    drawCircle(accent.copy(alpha = 0.22f), radius = r * 0.2f, center = c, style = Stroke(1f))
    drawCircle(accent, radius = r * 0.12f, center = c)
    val orbits = listOf(Triple(0.9f, 0.34f, 18f), Triple(0.72f, 0.5f, -30f), Triple(0.5f, 0.78f, 62f))
    orbits.forEachIndexed { idx, (rx, ryf, tilt) ->
        val a = rx * r; val b = ryf * a
        rotate(tilt, c) {
            drawOval(accent.copy(alpha = 0.2f), topLeft = Offset(c.x - a, c.y - b), size = Size(2 * a, 2 * b), style = Stroke(1f))
            val ang = spin * (1f + idx * 0.5f)
            val o = Offset(c.x + a * cos(ang), c.y + b * sin(ang))
            drawCircle(accent.copy(alpha = 0.35f), radius = 7f, center = o)
            drawCircle(accent, radius = 3.2f, center = o)
        }
    }
}

/** Alerts & feeds — a sonar/alert ping expanding from the centre. */
private fun DrawScope.drawSonar(accent: Color, pulse: Float, c: Offset, r: Float) {
    for (s in 0..2) {
        val p = (pulse + s / 3f) % 1f
        drawCircle(accent.copy(alpha = (1f - p) * 0.5f), radius = p * r, center = c, style = Stroke(width = 2f))
    }
    // warning triangle at the core
    val h = r * 0.26f
    val tri = Path().apply {
        moveTo(c.x, c.y - h); lineTo(c.x + h * 0.9f, c.y + h * 0.7f); lineTo(c.x - h * 0.9f, c.y + h * 0.7f); close()
    }
    drawPath(tri, accent.copy(alpha = 0.18f))
    drawPath(tri, accent, style = Stroke(2f))
    drawLine(accent, Offset(c.x, c.y - h * 0.3f), Offset(c.x, c.y + h * 0.25f), strokeWidth = 2.5f)
    drawCircle(accent, radius = 1.6f, center = Offset(c.x, c.y + h * 0.5f))
}

/** Comms — broadcast waves fanning out of a little mast. */
private fun DrawScope.drawBroadcast(accent: Color, pulse: Float, c: Offset, r: Float) {
    val base = Offset(c.x, c.y + r * 0.72f)
    val top = Offset(c.x, c.y - r * 0.2f)
    drawLine(accent.copy(alpha = 0.7f), base, top, strokeWidth = 2.5f)
    drawCircle(accent, radius = 3.4f, center = top)
    for (s in 0..2) {
        val p = (pulse + s / 3f) % 1f
        val rad = p * r * 0.95f
        drawArc(
            accent.copy(alpha = (1f - p) * 0.6f), startAngle = -150f, sweepAngle = 120f, useCenter = false,
            topLeft = Offset(top.x - rad, top.y - rad), size = Size(2 * rad, 2 * rad), style = Stroke(2f),
        )
    }
}

/** Play & Stats — climbing bars with a trend line. */
private fun DrawScope.drawBars(accent: Color, phase: Float, c: Offset, r: Float) {
    val n = 7
    val span = r * 1.8f
    val barW = span / n * 0.6f
    val baseY = c.y + r * 0.72f
    var prev: Offset? = null
    for (i in 0 until n) {
        val climb = (i + 1f) / n
        val h = r * 1.45f * climb * (0.7f + 0.3f * abs(sin(phase * TAU + i * 0.9f)))
        val x = c.x - span / 2f + (i + 0.5f) * (span / n)
        drawRoundRect(accent.copy(alpha = 0.35f + 0.5f * climb),
            topLeft = Offset(x - barW / 2f, baseY - h), size = Size(barW, h), cornerRadius = CornerRadius(2f))
        val top = Offset(x, baseY - h)
        prev?.let { drawLine(accent, it, top, strokeWidth = 2f) }
        prev = top
    }
    prev?.let { drawCircle(accent, radius = 4f, center = it) }
    drawLine(accent.copy(alpha = 0.25f), Offset(c.x - span / 2f, baseY), Offset(c.x + span / 2f, baseY))
}

/** Tools — two interlocking gears turning. */
private fun DrawScope.drawGears(accent: Color, spin: Float, c: Offset, r: Float) {
    gear(accent, Offset(c.x - r * 0.3f, c.y - r * 0.05f), r * 0.42f, 11, spin)
    gear(accent, Offset(c.x + r * 0.4f, c.y + r * 0.28f), r * 0.3f, 8, -spin * 1.38f + 0.3f)
}

private fun DrawScope.gear(accent: Color, center: Offset, radius: Float, teeth: Int, rot: Float) {
    drawCircle(accent.copy(alpha = 0.16f), radius = radius, center = center)
    drawCircle(accent.copy(alpha = 0.6f), radius = radius, center = center, style = Stroke(2f))
    drawCircle(accent.copy(alpha = 0.5f), radius = radius * 0.34f, center = center, style = Stroke(1.5f))
    val tooth = radius * 0.22f
    for (k in 0 until teeth) {
        val a = rot + k * TAU / teeth
        val o = Offset(center.x + cos(a) * radius, center.y + sin(a) * radius)
        rotate(Math.toDegrees(a.toDouble()).toFloat(), o) {
            drawRoundRect(accent, topLeft = Offset(o.x - tooth * 0.35f, o.y - tooth * 0.5f),
                size = Size(tooth, tooth), cornerRadius = CornerRadius(1.5f))
        }
    }
}

/**
 * The app's launch splash — the living network sphere, branded FLOCKEDUP. Shows for a beat on a
 * fresh start, then calls [onDone] to reveal the app.
 */
@Composable
fun FlockedUpSplash(onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(2200); onDone() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SectionLoader(
            title = "FLOCKEDUP", accent = MaterialTheme.colorScheme.primary,
            icon = Icons.Default.SatelliteAlt, seed = 73_261, subtitle = "INITIALIZING",
            theme = LoaderTheme.NETWORK,
        )
    }
}

/** Tiny deterministic PRNG so a given seed always yields the same loader. */
private class Lcg(seed: Int) {
    private var s = (seed.toLong() and 0xffffffffL).let { if (it == 0L) 0x9e3779b9L else it }
    fun next(): Float {
        s = (s * 1664525L + 1013904223L) and 0xffffffffL
        return (s ushr 8).toFloat() / (1 shl 24).toFloat()
    }
}
