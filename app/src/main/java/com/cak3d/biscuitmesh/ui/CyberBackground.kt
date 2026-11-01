package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The Flying Flocks backdrop: a living constellation. Star-nodes drift and link
 * up when they drift close, and when a finger touches or drags the screen the
 * nearby stars reach toward it with glowing lines — so the background reacts to
 * touch. Positions are kept normalized (0..1) and scaled to the canvas at draw.
 *
 * [touch] is the current pointer position in this canvas's pixel space (null when
 * nothing is pressed); it is observed one level up so the effect reacts to
 * touches even over scrolling content.
 */
@Composable
fun ConstellationBackground(
    modifier: Modifier = Modifier,
    touch: Offset? = null,
    nodeColor: Color = Color(0xFFE8A838),
    lineColor: Color = Color(0xFF5CB4E8),
    touchColor: Color = Color(0xFFFCE8C0),
) {
    val n = 56
    val px = remember { FloatArray(n) { Random.nextFloat() } }
    val py = remember { FloatArray(n) { Random.nextFloat() } }
    val vx = remember { FloatArray(n) { (Random.nextFloat() - 0.5f) * 0.04f } }
    val vy = remember { FloatArray(n) { (Random.nextFloat() - 0.5f) * 0.04f } }

    var lastNs by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffectFrames { now ->
        val dt = if (lastNs == 0L) 0f else ((now - lastNs) / 1_000_000_000f).coerceAtMost(0.05f)
        lastNs = now
        for (i in 0 until n) {
            px[i] += vx[i] * dt
            py[i] += vy[i] * dt
            if (px[i] < 0f) { px[i] = 0f; vx[i] = -vx[i] }
            if (px[i] > 1f) { px[i] = 1f; vx[i] = -vx[i] }
            if (py[i] < 0f) { py[i] = 0f; vy[i] = -vy[i] }
            if (py[i] > 1f) { py[i] = 1f; vy[i] = -vy[i] }
            // Gentle drag so touch-nudges don't accumulate forever.
            vx[i] *= 0.995f
            vy[i] *= 0.995f
            // Keep a minimum drift so the field never freezes.
            val sp = sqrt(vx[i] * vx[i] + vy[i] * vy[i])
            if (sp < 0.012f) { vx[i] += (Random.nextFloat() - 0.5f) * 0.01f; vy[i] += (Random.nextFloat() - 0.5f) * 0.01f }
        }
        tick = now
    }

    Canvas(modifier) {
        @Suppress("UNUSED_EXPRESSION") tick // read so the draw re-runs each frame
        val w = size.width
        val h = size.height
        val link = 150f
        val touchR = 240f

        // Pull nearby stars toward the finger (velocity nudge, then clamp).
        val tp = touch
        if (tp != null) {
            for (i in 0 until n) {
                val xi = px[i] * w
                val yi = py[i] * h
                val dx = tp.x - xi
                val dy = tp.y - yi
                val d2 = dx * dx + dy * dy
                if (d2 in 1f..(touchR * touchR)) {
                    val d = sqrt(d2)
                    val f = 0.06f * (1f - d / touchR)
                    vx[i] = (vx[i] + (dx / d) * f / w).coerceIn(-0.09f, 0.09f)
                    vy[i] = (vy[i] + (dy / d) * f / h).coerceIn(-0.09f, 0.09f)
                }
            }
        }

        // Links between nearby stars.
        for (i in 0 until n) {
            val xi = px[i] * w
            val yi = py[i] * h
            for (j in i + 1 until n) {
                val xj = px[j] * w
                val yj = py[j] * h
                val dx = xi - xj
                val dy = yi - yj
                val d2 = dx * dx + dy * dy
                if (d2 < link * link) {
                    val a = (1f - sqrt(d2) / link) * 0.45f
                    drawLine(lineColor.copy(alpha = a), Offset(xi, yi), Offset(xj, yj), strokeWidth = 1f)
                }
            }
        }

        // Finger links + glow.
        if (tp != null) {
            for (i in 0 until n) {
                val xi = px[i] * w
                val yi = py[i] * h
                val dx = tp.x - xi
                val dy = tp.y - yi
                val d2 = dx * dx + dy * dy
                if (d2 < touchR * touchR) {
                    val a = (1f - sqrt(d2) / touchR) * 0.8f
                    drawLine(touchColor.copy(alpha = a), tp, Offset(xi, yi), strokeWidth = 1.3f)
                }
            }
            drawCircle(touchColor.copy(alpha = 0.22f), radius = 14f, center = tp)
            drawCircle(touchColor.copy(alpha = 0.5f), radius = 4f, center = tp)
        }

        // Stars.
        for (i in 0 until n) {
            drawCircle(nodeColor.copy(alpha = 0.9f), radius = 2.3f, center = Offset(px[i] * w, py[i] * h))
        }
    }
}

/** Runs [onFrame] every frame for the life of the composition. */
@Composable
private fun LaunchedEffectFrames(onFrame: (Long) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { onFrame(it) }
        }
    }
}
