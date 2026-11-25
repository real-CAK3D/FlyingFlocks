package com.cak3d.biscuitmesh.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp

private val CROW_GREEN = Color(0xFF5FD36A)
private val CROW_HEAD = Color(0xFFE6FFE9)
private val CROW_DIM = Color(0xFF2E6F43)

/**
 * A spread-wing crow emblem as ONE continuous outline, centred at (cx, cy), scaled by [u]. Built so
 * it can be traced by [PathMeasure] — a moving light draws the whole bird in a single stroke.
 */
private fun crowEmblem(cx: Float, cy: Float, u: Float): Path = Path().apply {
    moveTo(cx, cy + 1.05f * u)                                                   // tail tip
    cubicTo(cx + 0.18f * u, cy + 0.5f * u, cx + 0.16f * u, cy + 0.0f * u, cx + 0.2f * u, cy - 0.4f * u)
    cubicTo(cx + 0.5f * u, cy - 0.6f * u, cx + 0.85f * u, cy - 0.66f * u, cx + 1.18f * u, cy - 0.62f * u) // right wingtip
    cubicTo(cx + 0.82f * u, cy - 0.26f * u, cx + 0.55f * u, cy - 0.04f * u, cx + 0.3f * u, cy + 0.1f * u)  // right wing trailing
    cubicTo(cx + 0.24f * u, cy - 0.1f * u, cx + 0.2f * u, cy - 0.34f * u, cx + 0.17f * u, cy - 0.5f * u)
    cubicTo(cx + 0.15f * u, cy - 0.64f * u, cx + 0.12f * u, cy - 0.74f * u, cx + 0.09f * u, cy - 0.82f * u)
    cubicTo(cx + 0.15f * u, cy - 0.9f * u, cx + 0.14f * u, cy - 1.04f * u, cx, cy - 1.06f * u)            // head / crown
    cubicTo(cx - 0.14f * u, cy - 1.04f * u, cx - 0.15f * u, cy - 0.9f * u, cx - 0.09f * u, cy - 0.82f * u)
    cubicTo(cx - 0.12f * u, cy - 0.74f * u, cx - 0.15f * u, cy - 0.64f * u, cx - 0.17f * u, cy - 0.5f * u)
    cubicTo(cx - 0.2f * u, cy - 0.34f * u, cx - 0.24f * u, cy - 0.1f * u, cx - 0.3f * u, cy + 0.1f * u)
    cubicTo(cx - 0.55f * u, cy - 0.04f * u, cx - 0.82f * u, cy - 0.26f * u, cx - 1.18f * u, cy - 0.62f * u) // left wingtip
    cubicTo(cx - 0.85f * u, cy - 0.66f * u, cx - 0.5f * u, cy - 0.6f * u, cx - 0.2f * u, cy - 0.4f * u)
    cubicTo(cx - 0.16f * u, cy + 0.0f * u, cx - 0.18f * u, cy + 0.5f * u, cx, cy + 1.05f * u)
    close()
}

/**
 * The Terminal's signature: a crow drawn out of a moving light-trail. The bird sits as a faint
 * resting outline while several comet heads race around its silhouette, each fading from a dim tail
 * to a bright white head — so the whole crow looks forged from motion, Hermes-style.
 */
@Composable
fun CrowTrailHeader(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "crow")
    val phase by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "phase",
    )
    val pm = remember { PathMeasure() }
    val scratch = remember { Path() }
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.54f
        val u = minOf(size.width / 2.8f, size.height / 2.3f)
        if (u <= 0f) return@Canvas
        val crow = crowEmblem(cx, cy, u)
        pm.setPath(crow, false)
        val len = pm.length
        if (len <= 0f) return@Canvas

        // Resting bird: soft outer glow + a faint full outline.
        drawPath(crow, CROW_GREEN.copy(alpha = 0.07f), style = Stroke(width = 12f))
        drawPath(crow, CROW_DIM.copy(alpha = 0.5f), style = Stroke(width = 1.4f))

        // A ground-line "perch" streak under the crow, drifting, to anchor the motion.
        val gy = cy + 1.2f * u
        drawLine(CROW_GREEN.copy(alpha = 0.10f), Offset(cx - 1.3f * u, gy), Offset(cx + 1.3f * u, gy), 1f)

        // Comet trails tracing the outline.
        val headLen = len * 0.18f
        val steps = 16
        for (k in 0 until 3) {
            val base = ((phase + k / 3f) % 1f) * len
            for (i in 0 until steps) {
                val a0 = (base + headLen * (i / steps.toFloat())) % len
                val a1 = (base + headLen * ((i + 1) / steps.toFloat())) % len
                if (a1 <= a0) continue   // skip the seam where the segment wraps past the end
                scratch.reset()
                if (pm.getSegment(a0, a1, scratch, true)) {
                    val f = i / (steps - 1f)                 // 0 = tail, 1 = head
                    val col = lerp(CROW_GREEN, CROW_HEAD, f * f)
                    drawPath(
                        scratch, col.copy(alpha = 0.15f + 0.85f * f),
                        style = Stroke(width = 1.1f + 3.4f * f, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}
