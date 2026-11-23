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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A branded loading screen in the Conflictly / Orbital Radar mould: a wireframe globe of dots
 * spinning behind a radar sweep, tinted to the section's accent colour, with the section name
 * ticking below. Each hub section passes its own [accent], [icon] and [title], so every section
 * gets its own unique loader while sharing one animation engine.
 *
 * Purely decorative and self-contained — drop it over a [Box] while a section spins up.
 */
@Composable
fun SectionLoader(
    title: String,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String = "LOADING",
) {
    val t = rememberInfiniteTransition(label = "loader")
    // Globe longitude spin.
    val spin by t.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "spin",
    )
    // Radar sweep angle (faster than the globe).
    val sweep by t.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "sweep",
    )
    // Gentle breathing pulse for the rim + title.
    val pulse by t.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    // A fixed lattice of points on a unit sphere (stable across recompositions): each is
    // ((latitude, longitude), isRed). A sprinkle of red dots nods to the Conflictly loader.
    val dots = remember {
        val list = ArrayList<Pair<Pair<Float, Float>, Boolean>>()
        val bands = 9
        for (b in 0 until bands) {
            val lat = (-PI / 2 + PI * (b + 0.5) / bands).toFloat()
            val ring = (cos(lat.toDouble()) * 14).toInt().coerceAtLeast(3)
            for (j in 0 until ring) {
                val lon = (2 * PI * j / ring).toFloat()
                // Deterministic pseudo-random for the red flag.
                val red = ((b * 31 + j * 17) % 7) == 0
                list.add((lat to lon) to red)
            }
        }
        list.toList()
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(200.dp)) {
                    val r = size.minDimension / 2f * 0.84f
                    val c = Offset(size.width / 2f, size.height / 2f)

                    // Faint rim.
                    drawCircle(accent.copy(alpha = 0.18f * pulse), radius = r, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

                    // Latitude rings (as flattened ellipses).
                    for (k in -2..2) {
                        val lat = k * (PI / 6)
                        val ry = (r * sin(lat)).toFloat()
                        val rx = (r * cos(lat)).toFloat()
                        drawOval(
                            color = accent.copy(alpha = 0.08f),
                            topLeft = Offset(c.x - rx, c.y + ry - 1f),
                            size = androidx.compose.ui.geometry.Size(rx * 2f, 2f),
                        )
                    }

                    // The spinning dot cloud.
                    dots.forEach { (latlon, red) ->
                        val (lat, lon0) = latlon
                        val lon = lon0 + spin
                        val x = cos(lat) * sin(lon)
                        val z = cos(lat) * cos(lon)
                        val y = sin(lat)
                        // Orthographic projection; z<0 is the far side (dimmed).
                        val px = c.x + (x * r).toFloat()
                        val py = c.y - (y * r).toFloat()
                        val front = (z + 1f) / 2f // 0 far .. 1 near
                        val a = (0.15f + 0.85f * front).toFloat()
                        val col = if (red) Color(0xFFFF3B4E) else accent
                        drawCircle(
                            color = col.copy(alpha = a * (if (red) 1f else 0.9f)),
                            radius = (1.2f + 2.2f * front).toFloat(),
                            center = Offset(px, py),
                        )
                    }

                    // Radar sweep wedge.
                    val sweepRad = (sweep * PI / 180).toFloat()
                    val end = Offset(c.x + (sin(sweepRad) * r), c.y - (cos(sweepRad) * r))
                    drawLine(
                        color = accent.copy(alpha = 0.85f),
                        start = c, end = end, strokeWidth = 2.5f,
                    )
                    // Trailing glow dots along the sweep.
                    for (s in 1..5) {
                        val tr = (sweep - s * 10f) * PI / 180
                        val gr = r * (0.3f + 0.14f * s)
                        drawCircle(
                            accent.copy(alpha = 0.10f),
                            radius = 3f,
                            center = Offset(c.x + (sin(tr) * gr).toFloat(), c.y - (cos(tr) * gr).toFloat()),
                        )
                    }
                }
                Icon(icon, contentDescription = null, tint = accent.copy(alpha = pulse), modifier = Modifier.size(30.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title.uppercase(),
                    color = accent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 3.sp,
                )
                Text(
                    "• $subtitle •",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f + 0.4f * pulse),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}
