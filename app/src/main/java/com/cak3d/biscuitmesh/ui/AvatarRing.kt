package com.cak3d.biscuitmesh.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cak3d.biscuitmesh.ui.theme.appAccent

/**
 * A profile avatar (an emoji glyph) inside a decorative ring — the Discord-style "overlay" that
 * circles your picture. Ring style comes from [overlay]: gold, neon, circuit, flame, aurora, or a
 * subtle default. Used on the Profile and as the store preview.
 */
@Composable
fun AvatarRing(avatar: String, overlay: String, size: Dp, modifier: Modifier = Modifier) {
    val accent = appAccent()
    val transition = rememberInfiniteTransition(label = "ring")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.07f
            val r = (this.size.minDimension - stroke) / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            when (overlay) {
                "gold" -> drawCircle(Color(0xFFFFC400), r, c, style = Stroke(stroke))
                "neon" -> {
                    for (i in 3 downTo 1) drawCircle(accent.copy(alpha = 0.12f * i), r, c, style = Stroke(stroke + i * 4f))
                    drawCircle(accent, r, c, style = Stroke(stroke))
                }
                "circuit" -> {
                    drawCircle(accent.copy(alpha = 0.25f), r, c, style = Stroke(stroke))
                    drawArc(accent, startAngle = phase * 360f, sweepAngle = 90f, useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
                "flame" -> drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(Color(0xFFFF3D00), Color(0xFFFFC400), Color(0xFFFF7A00), Color(0xFFFF3D00)), c),
                    radius = r, center = c, style = Stroke(stroke))
                "aurora" -> rotate(phase * 360f, c) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(Color(0xFF00E5A0), Color(0xFF22D3EE), Color(0xFFA78BFA), Color(0xFF00E5A0)), c),
                        radius = r, center = c, style = Stroke(stroke))
                }
                else -> drawCircle(accent.copy(alpha = 0.4f), r, c, style = Stroke(stroke * 0.6f))
            }
        }
        Text(avatar.ifBlank { "🐦‍⬛" }, fontSize = (size.value * 0.46f).sp)
    }
}
