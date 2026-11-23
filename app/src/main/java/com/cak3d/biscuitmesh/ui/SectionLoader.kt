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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A branded loading screen modelled on Conflictly's: a low-poly network sphere — nodes joined by
 * thin lines — slowly spinning in [accent], with a couple of brighter "hotspot" nodes pulsing
 * rings, an "● INITIALIZING" status and a LOADING-NN% counter ticking up. [icon] sits at the core
 * and [title] captions it.
 *
 * [seed] makes every loader unique: it drives the spin speed and direction, the node layout jitter
 * and the hotspot placement, so each selection in the app loads with its own one-off sphere.
 */
@Composable
fun SectionLoader(
    title: String,
    accent: Color,
    icon: ImageVector,
    seed: Int,
    modifier: Modifier = Modifier,
    subtitle: String = "INITIALIZING",
) {
    val rnd = remember(seed) { Lcg(seed) }
    val dir = remember(seed) { if (rnd.next() < 0.5f) 1f else -1f }
    val spinMs = remember(seed) { 6000 + (rnd.next() * 4000).toInt() }

    // Evenly spread nodes on a unit sphere (Fibonacci lattice) with a little seeded jitter.
    val nodes = remember(seed) {
        val n = 30
        val golden = (PI * (3.0 - sqrt(5.0))).toFloat()
        val base = rnd.next() * (2 * PI).toFloat()
        Array(n) { i ->
            val y = 1f - 2f * (i + 0.5f) / n + (rnd.next() - 0.5f) * 0.04f
            val rr = sqrt((1f - y * y).coerceAtLeast(0f))
            val th = i * golden + base
            floatArrayOf(rr * cos(th), y, rr * sin(th))
        }
    }
    // Edges between nearby nodes → the low-poly "network" look (capped so it isn't a hairball).
    val edges = remember(seed) {
        val list = ArrayList<Pair<Int, Int>>()
        for (i in nodes.indices) for (j in i + 1 until nodes.size) {
            val d = nodes[i][0] * nodes[j][0] + nodes[i][1] * nodes[j][1] + nodes[i][2] * nodes[j][2]
            if (d > 0.62f) list.add(i to j)
            if (list.size >= 46) break
        }
        list.toList()
    }
    // A few hotspot node indices that pulse rings.
    val hotspots = remember(seed) {
        val n = 2 + (rnd.next() * 2).toInt()
        (0 until n).map { (rnd.next() * nodes.size).toInt().coerceIn(0, nodes.size - 1) }.distinct()
    }

    val t = rememberInfiniteTransition(label = "loader")
    val spin by t.animateFloat(
        0f, dir * (2 * PI).toFloat(),
        infiniteRepeatable(tween(spinMs, easing = LinearEasing)), label = "spin",
    )
    val pulse by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "pulse",
    )
    val breathe by t.animateFloat(
        0.5f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe",
    )
    // Monotonic 0→100 counter for the ~1.5s the loader is on screen.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(100f, tween(1350, easing = LinearEasing)) }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(210.dp)) {
                    val r = size.minDimension / 2f * 0.82f
                    val c = Offset(size.width / 2f, size.height / 2f)

                    // Rotate every node around the Y axis by `spin`, project orthographically.
                    val px = FloatArray(nodes.size)
                    val py = FloatArray(nodes.size)
                    val pz = FloatArray(nodes.size)
                    val sa = sin(spin); val ca = cos(spin)
                    for (k in nodes.indices) {
                        val x = nodes[k][0]; val y = nodes[k][1]; val z = nodes[k][2]
                        val xr = x * ca + z * sa
                        val zr = -x * sa + z * ca
                        px[k] = c.x + xr * r
                        py[k] = c.y - y * r
                        pz[k] = zr
                    }

                    // Faint rim.
                    drawCircle(accent.copy(alpha = 0.12f * breathe), radius = r, center = c, style = Stroke(width = 1.5f))

                    // Network edges (dimmer on the far side).
                    edges.forEach { (i, j) ->
                        val front = ((pz[i] + pz[j]) / 2f + 1f) / 2f
                        if (front < 0.28f) return@forEach
                        drawLine(
                            accent.copy(alpha = 0.06f + 0.22f * front),
                            Offset(px[i], py[i]), Offset(px[j], py[j]),
                            strokeWidth = 1f,
                        )
                    }

                    // Nodes.
                    for (k in nodes.indices) {
                        val front = (pz[k] + 1f) / 2f
                        drawCircle(
                            accent.copy(alpha = 0.18f + 0.6f * front),
                            radius = 1.3f + 1.8f * front,
                            center = Offset(px[k], py[k]),
                        )
                    }

                    // Hotspots: pulsing rings on a couple of front-facing nodes.
                    hotspots.forEach { k ->
                        if (pz[k] < 0f) return@forEach
                        val o = Offset(px[k], py[k])
                        for (s in 0..1) {
                            val p = (pulse + s * 0.5f) % 1f
                            drawCircle(accent.copy(alpha = (1f - p) * 0.55f), radius = 3f + p * 20f, center = o, style = Stroke(width = 1.4f))
                        }
                        drawCircle(accent.copy(alpha = 0.95f), radius = 3.4f, center = o)
                        drawCircle(accent.copy(alpha = 0.3f), radius = 6.5f, center = o)
                    }
                }
                Icon(icon, contentDescription = null, tint = accent.copy(alpha = breathe), modifier = Modifier.size(28.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    title.uppercase(),
                    color = accent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    letterSpacing = 2.5.sp,
                )
                Text(
                    "● ${subtitle.uppercase()}",
                    color = accent.copy(alpha = 0.55f + 0.4f * breathe),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 3.sp,
                )
                Text(
                    "LOADING ${progress.value.toInt()}%",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}

/**
 * The app's launch splash — the same living network sphere, branded FLOCKEDUP, over a solid
 * background. Shows for a beat on a fresh start, then calls [onDone] to reveal the app.
 */
@Composable
fun FlockedUpSplash(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2200)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SectionLoader(
            title = "FLOCKEDUP",
            accent = MaterialTheme.colorScheme.primary,
            icon = Icons.Default.SatelliteAlt,
            seed = 73_261,
            subtitle = "INITIALIZING",
        )
    }
}

/** Tiny deterministic PRNG so a given seed always yields the same loader. */
private class Lcg(seed: Int) {
    private var s = (seed.toLong() and 0xffffffffL).let { if (it == 0L) 0x9e3779b9L else it }
    /** Next float in [0, 1). */
    fun next(): Float {
        s = (s * 1664525L + 1013904223L) and 0xffffffffL
        return (s ushr 8).toFloat() / (1 shl 24).toFloat()
    }
}
