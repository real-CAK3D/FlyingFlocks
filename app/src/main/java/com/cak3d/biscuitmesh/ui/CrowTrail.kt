package com.cak3d.biscuitmesh.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.lerp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

private val CROW_HEAD = Color(0xFFE6FFE9)
private val CROW_DIM = Color(0xFF2E6F43)

// Internal view-box the crow is authored in; dots are computed once in this space and scaled to fit.
private const val VB_W = 300f
private const val VB_H = 240f

/** A spread-wing crow emblem as one continuous closed outline, centred in the view-box. */
private fun crowEmblem(cx: Float, cy: Float, u: Float): Path = Path().apply {
    moveTo(cx, cy + 1.05f * u)                                                   // tail tip
    cubicTo(cx + 0.18f * u, cy + 0.5f * u, cx + 0.16f * u, cy + 0.0f * u, cx + 0.2f * u, cy - 0.4f * u)
    cubicTo(cx + 0.5f * u, cy - 0.6f * u, cx + 0.85f * u, cy - 0.66f * u, cx + 1.18f * u, cy - 0.62f * u)
    cubicTo(cx + 0.82f * u, cy - 0.26f * u, cx + 0.55f * u, cy - 0.04f * u, cx + 0.3f * u, cy + 0.1f * u)
    cubicTo(cx + 0.24f * u, cy - 0.1f * u, cx + 0.2f * u, cy - 0.34f * u, cx + 0.17f * u, cy - 0.5f * u)
    cubicTo(cx + 0.15f * u, cy - 0.64f * u, cx + 0.12f * u, cy - 0.74f * u, cx + 0.09f * u, cy - 0.82f * u)
    cubicTo(cx + 0.15f * u, cy - 0.9f * u, cx + 0.14f * u, cy - 1.04f * u, cx, cy - 1.06f * u)            // head
    cubicTo(cx - 0.14f * u, cy - 1.04f * u, cx - 0.15f * u, cy - 0.9f * u, cx - 0.09f * u, cy - 0.82f * u)
    cubicTo(cx - 0.12f * u, cy - 0.74f * u, cx - 0.15f * u, cy - 0.64f * u, cx - 0.17f * u, cy - 0.5f * u)
    cubicTo(cx - 0.2f * u, cy - 0.34f * u, cx - 0.24f * u, cy - 0.1f * u, cx - 0.3f * u, cy + 0.1f * u)
    cubicTo(cx - 0.55f * u, cy - 0.04f * u, cx - 0.82f * u, cy - 0.26f * u, cx - 1.18f * u, cy - 0.62f * u)
    cubicTo(cx - 0.85f * u, cy - 0.66f * u, cx - 0.5f * u, cy - 0.6f * u, cx - 0.2f * u, cy - 0.4f * u)
    cubicTo(cx - 0.16f * u, cy + 0.0f * u, cx - 0.18f * u, cy + 0.5f * u, cx, cy + 1.05f * u)
    close()
}

/**
 * The crow rendered as a dot matrix: every grid point inside the silhouette becomes a dot, so the
 * bird is "made of dots" (braille / halftone style). Computed once in view-box space.
 */
private val CROW_DOTS: List<Offset> by lazy {
    val path = crowEmblem(VB_W / 2f, VB_H * 0.49f, 95f).asAndroidPath()
    val b = android.graphics.RectF()
    path.computeBounds(b, true)
    val clip = android.graphics.Region(
        floor(b.left).toInt(), floor(b.top).toInt(), ceil(b.right).toInt(), ceil(b.bottom).toInt(),
    )
    val region = android.graphics.Region().apply { setPath(path, clip) }
    val dots = ArrayList<Offset>()
    val step = 9f
    var y = floor(b.top / step) * step
    while (y <= b.bottom) {
        var x = floor(b.left / step) * step
        while (x <= b.right) {
            if (region.contains(x.toInt(), y.toInt())) dots.add(Offset(x, y))
            x += step
        }
        y += step
    }
    dots
}

/**
 * The Terminal's signature: a crow built entirely out of dots, with a soft shimmer sweeping left to
 * right across the matrix so the bird looks alive — the braille-dot, Hermes-style emblem.
 */
@Composable
fun CrowTrailHeader(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "crow")
    val phase by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "phase",
    )
    Canvas(modifier) {
        val sc = minOf(size.width / VB_W, size.height / VB_H)
        if (sc <= 0f) return@Canvas
        val ox = (size.width - VB_W * sc) / 2f
        val oy = (size.height - VB_H * sc) / 2f
        val sweep = phase * (VB_W + 120f) - 60f   // a bright band travelling across the bird
        CROW_DOTS.forEach { d ->
            val px = ox + d.x * sc
            val py = oy + d.y * sc
            // Base twinkle from a travelling wave, plus a brighter crest where the sweep sits.
            val wave = (sin((d.x + d.y) * 0.08f + phase * 6.2832f) + 1f) / 2f
            val near = 1f - (kotlin.math.abs(d.x - sweep) / 70f).coerceIn(0f, 1f)
            val lum = (0.35f + 0.35f * wave + 0.75f * near).coerceIn(0f, 1f)
            val col = lerp(CROW_DIM, CROW_HEAD, lum)
            drawCircle(col.copy(alpha = 0.4f + 0.6f * lum), radius = (0.9f + 1.7f * lum) * sc,
                center = Offset(px, py))
        }
    }
}
