package com.cak3d.biscuitmesh.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The three "feels" the user can pick. */
enum class AppFeel(val label: String, val blurb: String) {
    ROUNDED("Rounded", "Soft corners — calm and friendly"),
    CIRCUIT("Circuit", "Boxy hacker panels with a light running the border"),
    NEON("Neon", "Rounded glass with a glowing accent edge"),
}

/** Accent colour options. Drives Material primary + the themed card edges. */
enum class AppAccent(val label: String, val color: Color) {
    GOLD("Sunrise", Color(0xFFE8A838)),
    CYAN("Cyber", Color(0xFF22D3EE)),
    MAGENTA("Hot Pink", Color(0xFFFF3D9A)),
    GREEN("Matrix", Color(0xFF34D399)),
    VIOLET("Ultra", Color(0xFFA78BFA)),
    EMBER("Ember", Color(0xFFFF5470)),
}

val LocalAppFeel = staticCompositionLocalOf { AppFeel.ROUNDED }
val LocalAppAccent = staticCompositionLocalOf { AppAccent.GOLD }

/** The current accent colour, read anywhere under the theme. */
@Composable
fun appAccent(): Color = LocalAppAccent.current.color

/** Card corner shape for the active feel. */
@Composable
fun appCardShape(): Shape = when (LocalAppFeel.current) {
    AppFeel.ROUNDED -> RoundedCornerShape(16.dp)
    AppFeel.CIRCUIT -> RoundedCornerShape(3.dp)
    AppFeel.NEON -> RoundedCornerShape(14.dp)
}

/**
 * The signature surface of the theme system — a panel drawn in the active feel:
 *  - ROUNDED: a plain soft card.
 *  - CIRCUIT: a near-square panel with a dim accent frame and a bright light that
 *    runs continuously around the border (the WDG-style running light).
 *  - NEON: a rounded panel with a soft glowing accent edge.
 */
@Composable
fun ThemedCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val feel = LocalAppFeel.current
    val accent = LocalAppAccent.current.color
    val surface = MaterialTheme.colorScheme.surface
    val shape = appCardShape()
    val base = modifier.clip(shape).background(surface, shape)
    val framed = when (feel) {
        AppFeel.ROUNDED -> base
        AppFeel.CIRCUIT -> base.staticFrame(accent.copy(alpha = 0.22f), 3f).runningBorderLight(accent)
        AppFeel.NEON -> base.neonEdge(accent)
    }
    Box(framed.padding(contentPadding)) { content() }
}

/** A dim static rounded-rect outline. */
private fun Modifier.staticFrame(color: Color, cornerPx: Float) = drawBehind {
    val s = 1.5.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(s / 2, s / 2),
        size = Size(size.width - s, size.height - s),
        cornerRadius = CornerRadius(cornerPx, cornerPx),
        style = Stroke(width = s),
    )
}

/** A bright accent comet that runs clockwise around the rectangular border, trailing a fade. */
private fun Modifier.runningBorderLight(accent: Color): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "border")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    drawWithContent {
        drawContent()
        val w = 2.dp.toPx()
        val off = w / 2
        val rectW = size.width - w
        val rectH = size.height - w
        val perim = 2f * (rectW + rectH)
        if (perim <= 0f) return@drawWithContent
        val head = t * perim
        val trail = 14
        val seg = perim * 0.07f / trail
        for (i in 0 until trail) {
            val d = ((head - i * seg) % perim + perim) % perim
            val p = perimeterPoint(d, rectW, rectH, off)
            val a = 1f - i / trail.toFloat()
            drawCircle(accent.copy(alpha = 0.10f * a), radius = w * 3.2f, center = p)
            drawCircle(accent.copy(alpha = a), radius = w * 1.15f, center = p)
        }
    }
}

/** Point at distance [d] clockwise along a w×h rectangle's perimeter, offset into the box. */
private fun perimeterPoint(d: Float, w: Float, h: Float, off: Float): Offset {
    var rem = d
    if (rem <= w) return Offset(off + rem, off)
    rem -= w
    if (rem <= h) return Offset(off + w, off + rem)
    rem -= h
    if (rem <= w) return Offset(off + w - rem, off + h)
    rem -= w
    return Offset(off, off + h - rem)
}

/** A soft glowing accent edge: expanding strokes at falling alpha, then a crisp line. */
private fun Modifier.neonEdge(accent: Color): Modifier = drawBehind {
    val r = 14.dp.toPx()
    for (i in 3 downTo 1) {
        val gw = i * 3f
        drawRoundRect(
            color = accent.copy(alpha = 0.06f * i),
            topLeft = Offset(gw / 2, gw / 2),
            size = Size(size.width - gw, size.height - gw),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = gw),
        )
    }
    val s = 1.5.dp.toPx()
    drawRoundRect(
        color = accent.copy(alpha = 0.85f),
        topLeft = Offset(s / 2, s / 2),
        size = Size(size.width - s, size.height - s),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = s),
    )
}

/** Builds the Material colour scheme from the chosen accent and exposes feel + accent to the tree. */
@Composable
fun BiscuitMeshTheme(
    feel: AppFeel = AppFeel.ROUNDED,
    accent: AppAccent = AppAccent.GOLD,
    content: @Composable () -> Unit,
) {
    val ink = if (feel == AppFeel.CIRCUIT) Color(0xFF080B0E) else Color(0xFF0E1116)
    val raised = if (feel == AppFeel.CIRCUIT) Color(0xFF10151B) else Color(0xFF171B22)
    val scheme = darkColorScheme(
        primary = accent.color,
        onPrimary = Color(0xFF0E1116),
        secondary = Color(0xFF5CB4E8),
        onSecondary = Color(0xFF0E1116),
        tertiary = accent.color,
        background = ink,
        surface = raised,
        onBackground = Color(0xFFE6E8EB),
        onSurface = Color(0xFFE6E8EB),
    )
    CompositionLocalProvider(LocalAppFeel provides feel, LocalAppAccent provides accent) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
