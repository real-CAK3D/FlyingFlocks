package com.cak3d.biscuitmesh.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A branded loading screen in the Conflictly mould: a dark wireframe globe of dim dots, slowly
 * spinning, with a few bright "hotspots" throwing out pulsing rings — plus one extra motion layer
 * (radar sweep / orbiting satellite / pulse rings) chosen by [seed]. Tinted to [accent], centred on
 * [icon], captioned [title].
 *
 * [seed] makes every loader unique: it sets the spin speed and direction, the dot jitter, the
 * hotspot placements, and which extra motion plays — so each selection in the app gets its own.
 */
@Composable
fun SectionLoader(
    title: String,
    accent: Color,
    icon: ImageVector,
    seed: Int,
    modifier: Modifier = Modifier,
    subtitle: String = "LOADING",
) {
    val rnd = remember(seed) { Lcg(seed) }
    val archetype = remember(seed) { (seed.hashCode() and 0x7fffffff) % 3 } // 0 sweep, 1 orbiter, 2 rings
    val dir = remember(seed) { if (rnd.next() < 0.5f) 1f else -1f }
    val spinMs = remember(seed) { 5500 + (rnd.next() * 4000).toInt() }

    // The dim dot sphere (seeded jitter so no two look identical).
    val dots = remember(seed) {
        val list = ArrayList<Triple<Float, Float, Boolean>>() // lat, lon, isCyanSignal
        val bands = 9
        for (b in 0 until bands) {
            val lat = (-PI / 2 + PI * (b + 0.5) / bands).toFloat()
            val ring = (cos(lat.toDouble()) * 14).toInt().coerceAtLeast(3)
            for (j in 0 until ring) {
                val jitter = (rnd.next() - 0.5f) * 0.12f
                val lon = (2 * PI * j / ring).toFloat() + jitter
                val cyan = rnd.next() < 0.06f
                list.add(Triple(lat + jitter, lon, cyan))
            }
        }
        list.toList()
    }
    // Hotspots: 2–4 bright accent points that pulse rings.
    val hotspots = remember(seed) {
        val n = 2 + (rnd.next() * 3).toInt()
        (0 until n).map {
            val lat = (rnd.next() - 0.5f) * (PI.toFloat() * 0.8f)
            val lon = rnd.next() * (2 * PI).toFloat()
            lat to lon
        }
    }

    val t = rememberInfiniteTransition(label = "loader")
    val spin by t.animateFloat(
        0f, dir * (2 * PI).toFloat(),
        infiniteRepeatable(tween(spinMs, easing = LinearEasing)), label = "spin",
    )
    val motion by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (archetype == 0) 2600 else 2200, easing = LinearEasing)), label = "motion",
    )
    val pulse by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "pulse",
    )
    val breathe by t.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe",
    )

    val cyan = Color(0xFF38E1D6)

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(210.dp)) {
                    val r = size.minDimension / 2f * 0.84f
                    val c = Offset(size.width / 2f, size.height / 2f)

                    // Faint rim + latitude ellipses.
                    drawCircle(accent.copy(alpha = 0.14f * breathe), radius = r, center = c, style = Stroke(width = 2f))
                    for (k in -2..2) {
                        val lat = k * (PI / 6)
                        val rx = (r * cos(lat)).toFloat()
                        val ry = (r * sin(lat)).toFloat()
                        drawOval(accent.copy(alpha = 0.06f), topLeft = Offset(c.x - rx, c.y + ry - 1f), size = Size(rx * 2f, 2f))
                    }

                    // Dim spinning dot sphere.
                    dots.forEach { (lat, lon0, isCyan) ->
                        val lon = lon0 + spin
                        val x = cos(lat) * sin(lon)
                        val z = cos(lat) * cos(lon)
                        val y = sin(lat)
                        if (z < -0.15f) return@forEach // cull far back
                        val front = (z + 1f) / 2f
                        val px = c.x + x * r
                        val py = c.y - y * r
                        val col = if (isCyan) cyan else accent
                        val a = if (isCyan) 0.25f + 0.6f * front else 0.10f + 0.28f * front
                        drawCircle(col.copy(alpha = a), radius = 1.1f + 1.8f * front, center = Offset(px, py))
                    }

                    // Hotspots with expanding rings (the Conflictly signature).
                    hotspots.forEach { (lat, lon0) ->
                        val lon = lon0 + spin
                        val x = cos(lat) * sin(lon)
                        val z = cos(lat) * cos(lon)
                        val y = sin(lat)
                        if (z < 0f) return@forEach // only front-facing hotspots
                        val px = c.x + x * r
                        val py = c.y - y * r
                        // Two staggered rings.
                        for (s in 0..1) {
                            val p = (pulse + s * 0.5f) % 1f
                            drawCircle(
                                accent.copy(alpha = (1f - p) * 0.5f),
                                radius = 3f + p * 22f,
                                center = Offset(px, py),
                                style = Stroke(width = 1.5f),
                            )
                        }
                        drawCircle(accent.copy(alpha = 0.95f), radius = 3.2f, center = Offset(px, py))
                        drawCircle(accent.copy(alpha = 0.25f), radius = 6f, center = Offset(px, py))
                    }

                    // Extra motion layer, chosen by seed.
                    when (archetype) {
                        0 -> { // radar sweep
                            val ang = motion * 2f * PI.toFloat()
                            drawLine(accent.copy(alpha = 0.8f), c, Offset(c.x + sin(ang) * r, c.y - cos(ang) * r), strokeWidth = 2.5f)
                        }
                        1 -> { // orbiting satellite
                            val ang = motion * 2f * PI.toFloat()
                            val orx = r * 1.12f
                            val ory = r * 0.42f
                            val ox = c.x + sin(ang) * orx
                            val oy = c.y - cos(ang) * ory * (if (sin(ang) > 0) 1f else 1f)
                            drawOval(accent.copy(alpha = 0.12f), topLeft = Offset(c.x - orx, c.y - ory), size = Size(orx * 2f, ory * 2f), style = Stroke(width = 1f))
                            drawCircle(cyan.copy(alpha = 0.9f), radius = 3.5f, center = Offset(ox, oy))
                        }
                        else -> { // concentric pulse rings from the core
                            for (s in 0..2) {
                                val p = (motion + s / 3f) % 1f
                                drawCircle(accent.copy(alpha = (1f - p) * 0.4f), radius = p * r, center = c, style = Stroke(width = 1.5f))
                            }
                        }
                    }
                }
                Icon(icon, contentDescription = null, tint = accent.copy(alpha = breathe), modifier = Modifier.size(30.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    title.uppercase(),
                    color = accent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    letterSpacing = 2.5.sp,
                )
                Text(
                    "• $subtitle •",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f + 0.4f * breathe),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                )
            }
        }
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
