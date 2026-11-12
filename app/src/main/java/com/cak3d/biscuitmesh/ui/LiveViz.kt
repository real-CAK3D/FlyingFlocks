package com.cak3d.biscuitmesh.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import kotlin.math.abs
import kotlin.math.sin

// Terminal palette shared with the Debug/mirror views.
private val LV_PANEL = Color(0xFF101217)
private val LV_DIM = Color(0xFF6B7078)
private val LV_INK = Color(0xFFE8E6DC)
private val LV_GREEN = Color(0xFF53D38A)
private val LV_AMBER = Color(0xFFF2AC2A)
private val LV_BLOOD = Color(0xFFCA1C24)
private val LV_PLUM = Color(0xFFB24BFF)
private val LV_MONO = FontFamily.Monospace

/** rssi (dBm) -> bar colour. */
private fun sigColor(rssi: Int): Color = when {
    rssi > -55 -> LV_BLOOD
    rssi > -75 -> LV_AMBER
    else -> LV_PLUM
}

/** rssi (dBm, ~-110..-20) -> 0..1 height. */
private fun sigFrac(rssi: Int): Float = ((rssi + 110f) / 90f).coerceIn(0.03f, 1f)

private fun lastField(payload: String, idx: Int): Int? =
    payload.split(',').getOrNull(idx)?.trim()?.toIntOrNull()

private fun lastNumeric(payload: String): Int? =
    payload.split(',').lastOrNull { it.trim().toIntOrNull() != null }?.trim()?.toIntOrNull()

/**
 * A live, animated visualization panel that reflects whatever scan the board is
 * running — signal bars for AP/BLE scans, a running spectrum for sub-GHz, a
 * packet-rate graph for sniffers, and a gentle idle pulse otherwise. Driven
 * purely by the message stream, so it animates as data arrives.
 */
@Composable
fun LiveVizPanel(lines: List<BiscuitMessage>, activeOp: String?, modifier: Modifier = Modifier) {
    // Pull the freshest data out of the stream (scan the tail, newest first).
    val tail = remember(lines) { lines.takeLast(120) }

    val apRssis = remember(tail) {
        tail.filter { it.type == BiscuitMessage.Type.DATA && it.subtype == "AP" }
            .takeLast(24).mapNotNull { lastField(it.payload, 3) ?: lastNumeric(it.payload) }
    }
    val btRssis = remember(tail) {
        tail.filter { it.type == BiscuitMessage.Type.DATA && it.subtype == "BT" }
            .takeLast(24).mapNotNull { lastField(it.payload, 2) ?: lastNumeric(it.payload) }
    }
    val spectrum = remember(tail) {
        tail.lastOrNull { it.type == BiscuitMessage.Type.DATA && it.subtype == "SPECTRUM" }
            ?.let { parseSpectrumBins(it.payload) }
    }
    val rfHits = remember(tail) {
        tail.filter { it.type == BiscuitMessage.Type.DATA && it.subtype == "RF" }
            .takeLast(24).mapNotNull { lastNumeric(it.payload) }
    }

    val (label, content) = when {
        spectrum != null && (activeOp == "waterfall" || activeOp == "bandscan" || spectrum.isNotEmpty()) ->
            "SPECTRUM" to VizKind.Spectrum(spectrum)
        apRssis.isNotEmpty() -> "WIFI · ${apRssis.size} APs" to VizKind.Bars(apRssis)
        btRssis.isNotEmpty() -> "BLE · ${btRssis.size} devices" to VizKind.Bars(btRssis)
        rfHits.isNotEmpty() -> "SUB-GHZ" to VizKind.Bars(rfHits)
        else -> (activeOp?.uppercase()?.takeIf { it.isNotBlank() && it != "IDLE" } ?: "LISTENING") to VizKind.Idle
    }

    Column(
        modifier.fillMaxWidth()
            .background(LV_PANEL, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(label, color = LV_DIM, fontFamily = LV_MONO, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(96.dp).padding(top = 6.dp)) {
            when (content) {
                is VizKind.Spectrum -> SpectrumViz(content.bins)
                is VizKind.Bars -> SignalBars(content.rssis)
                VizKind.Idle -> ListeningPulse()
            }
        }
    }
}

private sealed interface VizKind {
    data class Spectrum(val bins: List<Int>) : VizKind
    data class Bars(val rssis: List<Int>) : VizKind
    data object Idle : VizKind
}

private fun parseSpectrumBins(payload: String): List<Int> {
    // DATA:SPECTRUM:<f0>,<step>,<n>,<r0;r1;...>
    val parts = payload.split(',')
    val csv = parts.getOrNull(3) ?: return emptyList()
    return csv.split(';').mapNotNull { it.trim().toIntOrNull() }
}

/** Animated signal-strength bars (one per AP/device), tallest = strongest. */
@Composable
private fun SignalBars(rssis: List<Int>) {
    val sorted = remember(rssis) { rssis.sortedDescending().take(28) }
    Canvas(Modifier.fillMaxSize()) {
        if (sorted.isEmpty()) return@Canvas
        val n = sorted.size
        val gap = 3f
        val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(2f)
        sorted.forEachIndexed { i, r ->
            val h = sigFrac(r) * size.height
            val x = i * (bw + gap)
            drawRect(
                color = sigColor(r),
                topLeft = androidx.compose.ui.geometry.Offset(x, size.height - h),
                size = androidx.compose.ui.geometry.Size(bw, h),
            )
        }
    }
}

/** A running spectrum: bars across frequency, coloured by strength. */
@Composable
private fun SpectrumViz(bins: List<Int>) {
    Canvas(Modifier.fillMaxSize()) {
        if (bins.isEmpty()) return@Canvas
        val n = bins.size
        val bw = (size.width / n).coerceAtLeast(1f)
        bins.forEachIndexed { i, r ->
            val h = sigFrac(r) * size.height
            drawRect(
                color = sigColor(r),
                topLeft = androidx.compose.ui.geometry.Offset(i * bw, size.height - h),
                size = androidx.compose.ui.geometry.Size(bw.coerceAtLeast(1f) - 0.5f, h),
            )
        }
    }
}

/** A gentle animated sine pulse while nothing is actively scanning. */
@Composable
private fun ListeningPulse() {
    var t by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) { withFrameMillis { t = (it % 100000L) / 1000f } }
    }
    Canvas(Modifier.fillMaxSize()) {
        val mid = size.height / 2f
        val steps = 80
        var prev: androidx.compose.ui.geometry.Offset? = null
        for (i in 0..steps) {
            val x = size.width * i / steps
            val phase = t * 2.4f + i * 0.22f
            val amp = (0.5f + 0.5f * sin(t * 1.3f)) * size.height * 0.32f
            val y = mid + sin(phase) * amp * (0.4f + 0.6f * (1f - abs(i - steps / 2f) / (steps / 2f)))
            val p = androidx.compose.ui.geometry.Offset(x, y)
            prev?.let { drawLine(LV_GREEN.copy(alpha = 0.7f), it, p, strokeWidth = 2f) }
            prev = p
        }
    }
}
