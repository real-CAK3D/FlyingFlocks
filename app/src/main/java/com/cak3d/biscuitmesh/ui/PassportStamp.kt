package com.cak3d.biscuitmesh.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cak3d.biscuitmesh.data.Stamp
import kotlinx.coroutines.delay

private val PAPER = Color(0xFF16202C)
private val GOLD = Color(0xFFE8C06A)
private val STAMP_RED = Color(0xFFD64541)

/**
 * The passport animation: a dim scrim, the book opens, the page writes itself in, then an ink stamp
 * slams down and the book closes. Tap anywhere to skip. Always calls [onDone] when finished.
 */
@Composable
fun PassportStampOverlay(stamp: Stamp, onDone: () -> Unit) {
    val open = remember { Animatable(0f) }
    val content = remember { Animatable(0f) }
    val stampScale = remember { Animatable(2.6f) }
    val stampAlpha = remember { Animatable(0f) }

    LaunchedEffect(stamp.id) {
        open.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        content.animateTo(1f, tween(360))
        delay(160)
        stampAlpha.snapTo(1f)
        stampScale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium))
        delay(1500)
        content.animateTo(0f, tween(220))
        open.animateTo(0f, tween(440, easing = FastOutSlowInEasing))
        onDone()
    }

    val date = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.US)
        .format(java.util.Date(stamp.ms))

    Box(
        Modifier.fillMaxSize().background(Color(0xCC05070A))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        // The passport page — "opens" by scaling up vertically.
        Column(
            Modifier.width(310.dp)
                .graphicsLayer {
                    scaleY = 0.12f + 0.88f * open.value
                    alpha = (open.value * 1.6f).coerceIn(0f, 1f)
                }
                .clip(RoundedCornerShape(14.dp))
                .background(PAPER)
                .border(BorderStroke(1.5.dp, GOLD.copy(alpha = 0.5f)), RoundedCornerShape(14.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.graphicsLayer { alpha = content.value },
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("FLOCK PASSPORT", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    letterSpacing = 2.sp, color = GOLD, fontWeight = FontWeight.Bold)
                Text("${stamp.kind.glyph}  ${stamp.kind.label.uppercase()}", fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp, color = GOLD.copy(alpha = 0.8f))
                Text(stamp.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF2EAD8))
                if (stamp.subtitle.isNotBlank())
                    Text(stamp.subtitle, fontSize = 13.sp, color = Color(0xFFBFC6CF))
                if (stamp.data.isNotBlank())
                    Text(stamp.data, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        color = Color(0xFF9AA3AD))
                Text(date, fontSize = 12.sp, color = Color(0xFF7F8893))
            }
        }

        // The ink stamp that slams down over the page.
        Box(
            Modifier.size(132.dp)
                .graphicsLayer {
                    scaleX = stampScale.value; scaleY = stampScale.value
                    alpha = stampAlpha.value; rotationZ = -16f
                }
                .clip(CircleShape)
                .border(BorderStroke(3.dp, STAMP_RED), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stamp.kind.glyph, fontSize = 30.sp)
                Text("COLLECTED", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, color = STAMP_RED, letterSpacing = 1.sp)
            }
        }
    }
}
