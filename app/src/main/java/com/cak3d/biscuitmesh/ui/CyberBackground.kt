package com.cak3d.biscuitmesh.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.random.Random

/**
 * The Flying Flocks house backdrop: a subtle "data rain" of glyphs drifting down
 * a faint grid, drawn behind the text screens (never the map). Kept low-alpha so
 * content stays readable, and driven off frame time so it animates smoothly.
 */
@Composable
fun CyberBackground(
    modifier: Modifier = Modifier,
    glyphColor: Color = Color(0xFFE8A838),
    headColor: Color = Color(0xFFFCE8C0),
    gridColor: Color = Color(0xFF5CB4E8),
) {
    // A fixed pool of columns; mapped across the real width at draw time.
    val columns = remember {
        List(72) { Column(speed = Random.nextFloat() * 50f + 28f, phase = Random.nextFloat() * 1000f, len = Random.nextInt(8, 20)) }
    }
    val glyphSeed = remember { Random(1337) }

    var timeMs by remember { mutableLongStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var start = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (start == 0L) start = now
                timeMs = (now - start) / 1_000_000
            }
        }
    }

    val paint = remember {
        Paint().apply {
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }

    Canvas(modifier) {
        val t = timeMs / 1000f
        val w = size.width
        val h = size.height
        val cellW = 26f
        val cols = (w / cellW).toInt().coerceIn(1, columns.size)
        val cellH = 30f
        paint.textSize = 24f

        // Faint drifting grid.
        val gridStep = 48f
        val drift = (t * 10f) % gridStep
        var gx = -drift
        while (gx < w) {
            drawLine(gridColor.copy(alpha = 0.05f), Offset(gx, 0f), Offset(gx, h), strokeWidth = 1f)
            gx += gridStep
        }
        var gy = -drift
        while (gy < h) {
            drawLine(gridColor.copy(alpha = 0.05f), Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
            gy += gridStep
        }

        // Data rain.
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            for (c in 0 until cols) {
                val col = columns[c]
                val x = c * cellW + cellW / 2f
                val total = h + col.len * cellH
                val head = ((t * col.speed + col.phase) % total) - col.len * cellH
                for (i in 0 until col.len) {
                    val y = head + i * cellH
                    if (y < 0f || y > h) continue
                    val fromHead = col.len - 1 - i
                    val glyph = GLYPHS[(glyphSeed.nextInt(GLYPHS.length) + c * 7 + (t * col.speed / cellH).toInt() - i) .let { abs(it) % GLYPHS.length }]
                    if (fromHead == 0) {
                        paint.color = headColor.toArgb()
                        paint.alpha = 235
                    } else {
                        paint.color = glyphColor.toArgb()
                        paint.alpha = (150f * (1f - fromHead.toFloat() / col.len)).toInt().coerceIn(10, 150)
                    }
                    native.drawText(glyph.toString(), x, y, paint)
                }
            }
        }
    }
}

private class Column(val speed: Float, val phase: Float, val len: Int)

private const val GLYPHS = "01ｱｲｳｴｵｶｷｸ日ﾊﾋﾌ0123456789ABCDEF<>/\\#*+=アサソヲ"
