package com.cak3d.biscuitmesh.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

/** One explainable spot on the current screen — its live bounds plus what/why text. */
class HelpSpot(val id: String, val label: String, val text: String) {
    var rect by mutableStateOf(Rect.Zero)
}

/**
 * App-wide "tap to learn" help. Elements mark themselves with [Modifier.help]; when [active], a
 * [HelpOverlay] dims the screen, rings every marked element, and shows what each one is when tapped.
 * Spots register/unregister as screens come and go, so the overlay always reflects the current page.
 */
class HelpController {
    var active by mutableStateOf(false)
    val spots = mutableStateListOf<HelpSpot>()
    var selected by mutableStateOf<HelpSpot?>(null)

    fun enter() { selected = null; active = true }
    fun exit() { active = false; selected = null }
    fun hasSpots() = spots.isNotEmpty()
}

val LocalHelp = compositionLocalOf<HelpController?> { null }

/** Help text for the bottom-navigation tabs (used by the persistent nav bar). */
fun tabHelp(route: String): String = when (route) {
    "fleet" -> "Your boards (ESP32 nodes) and the phone. Connect a saved board, see what's live, check Health, or hit Scan to start a phone-only wardrive."
    "wardrive" -> "The map. Start it and drive — it logs every Wi-Fi/BT/camera along your route and draws your track. The ☰ menu has layers, offline download and traffic."
    "wifi" -> "A live list of the Wi-Fi networks the phone hears right now, with a spectrum graph and attack tools (for authorized testing)."
    "bluetooth" -> "A live list of Bluetooth/BLE devices the phone hears, classified into a 'BT house' — and it calls out other phones nearby."
    "subghz" -> "Sub-GHz (433/868/915 MHz) captures and tools — needs a connected board with a CC1101 radio."
    "misc" -> "Live meters (packets, probes, stations) and extra board tools."
    else -> "Switch to the ${route.replaceFirstChar { it.uppercase() }} screen."
}

/**
 * Mark this element as explainable in help mode. [label] is the short name, [text] is what it is /
 * what it does / when or how to use it. No-op when no [HelpController] is in scope.
 */
fun Modifier.help(label: String, text: String): Modifier = composed {
    val ctrl = LocalHelp.current ?: return@composed this
    val spot = remember(label, text) { HelpSpot(java.util.UUID.randomUUID().toString(), label, text) }
    DisposableEffect(ctrl, spot) {
        ctrl.spots.add(spot)
        onDispose { ctrl.spots.remove(spot); if (ctrl.selected === spot) ctrl.selected = null }
    }
    this.onGloballyPositioned { spot.rect = it.boundsInRoot() }
}

/** The overlay that renders when help mode is on. Place it once over the whole app content. */
@Composable
fun HelpOverlay(ctrl: HelpController) {
    if (!ctrl.active) return
    val accent = Color(0xFF22D3EE)
    val pulse = rememberInfiniteTransition(label = "help")
    val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), "t")

    Box(
        Modifier.fillMaxSize()
            .pointerInput(ctrl.spots.size) {
                detectTapGestures { tap ->
                    // Nearest spot whose box contains the tap, else the closest within reach.
                    val inside = ctrl.spots.filter { it.rect.contains(tap) }
                    ctrl.selected = inside.minByOrNull { it.rect.width * it.rect.height }
                        ?: ctrl.spots.minByOrNull { (it.rect.center - tap).getDistanceSquared() }
                            ?.takeIf { (it.rect.center - tap).getDistance() < 160f }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xE60A0E13))
            ctrl.spots.forEach { s ->
                if (s.rect.width <= 0f) return@forEach
                val selected = s === ctrl.selected
                val pad = 6f
                val r = Rect(s.rect.left - pad, s.rect.top - pad, s.rect.right + pad, s.rect.bottom + pad)
                // Punch a lighter window so the element reads through the scrim.
                drawRoundRect(Color(0x33FFFFFF), topLeft = r.topLeft,
                    size = r.size, cornerRadius = CornerRadius(12f, 12f))
                val a = if (selected) 1f else 0.4f + 0.4f * t
                drawRoundRect(accent.copy(alpha = a), topLeft = r.topLeft, size = r.size,
                    cornerRadius = CornerRadius(12f, 12f), style = Stroke(if (selected) 4f else 2.5f))
            }
        }

        // Top banner + exit.
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("HELP · tap a highlighted area", color = accent, fontFamily = FontFamily.Monospace,
                fontSize = 12.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { ctrl.exit() }) { Text("Done") }
        }

        // The explanation for the tapped spot.
        ctrl.selected?.let { s ->
            Surface(color = Color(0xFF16202C), shape = RoundedCornerShape(14.dp),
                shadowElevation = 10.dp,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = accent)
                    Text(s.text, fontSize = 13.sp, color = Color(0xFFD7DEE6))
                }
            }
        } ?: Surface(color = Color(0x991A2430), shape = RoundedCornerShape(10.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)) {
            Text(if (ctrl.hasSpots()) "Tap any glowing box to learn what it does"
                else "Nothing on this page is tagged yet — tap Done",
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color(0xFFBFC6CF), fontSize = 12.sp)
        }
    }
}
