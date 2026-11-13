package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.model.LogEntry
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import kotlinx.coroutines.flow.MutableStateFlow

private val AU_GREEN = Color(0xFF39D98A)
private val AU_AMBER = Color(0xFFFF8A3D)
private val AU_RED = Color(0xFFE5484D)

/**
 * On-board audio for the node: live mic VU + spectrum (DATA:MIC / DATA:ASPEC) and
 * speaker controls (beep / click / alert). Starting the screen tells the node to
 * stream audio; leaving it stops the stream.
 */
@Composable
fun AudioScreen(vm: MainViewModel, onBack: () -> Unit) {
    val logFlow = vm.log ?: remember { MutableStateFlow(emptyList<LogEntry>()) }
    val log by logFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Ask the node to stream while this screen is up; stop when it leaves.
    DisposableEffect(Unit) {
        vm.audioStart()
        onDispose { vm.audioStop() }
    }

    val vu = remember(log) {
        log.lastOrNull { it.message.type == BiscuitMessage.Type.DATA && it.message.subtype == "MIC" }
            ?.message?.payload?.trim()?.toIntOrNull() ?: 0
    }
    val bins = remember(log) {
        log.lastOrNull { it.message.type == BiscuitMessage.Type.DATA && it.message.subtype == "ASPEC" }
            ?.message?.payload?.split(";")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = AU_AMBER)
            Spacer(Modifier.width(8.dp))
            Text("Audio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text("Live from the node's microphone. The speaker controls below play on the device.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))

        // VU meter
        Text("Mic level  $vu", style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val w = size.width * (vu / 100f).coerceIn(0f, 1f)
            drawRoundRect(Color.White.copy(alpha = 0.10f), size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f))
            val c = if (vu > 80) AU_RED else if (vu > 50) AU_AMBER else AU_GREEN
            drawRoundRect(c, size = Size(w, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f))
        }
        Spacer(Modifier.height(16.dp))

        // Spectrum
        Text("Audio spectrum", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Card(Modifier.fillMaxWidth().height(160.dp)) {
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                if (bins.isEmpty()) return@Canvas
                val bw = size.width / bins.size
                bins.forEachIndexed { i, v ->
                    val h = size.height * (v / 100f).coerceIn(0f, 1f)
                    val c = if (v > 70) AU_RED else if (v > 40) AU_AMBER else AU_GREEN
                    drawRect(c, topLeft = Offset(i * bw, size.height - h), size = Size(bw - 2f, h))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (bins.isEmpty()) {
            Text("Waiting for audio… open the node and make some noise near the mic.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))

        Text("Speaker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.beep(1600, 120) }, modifier = Modifier.weight(1f)) { Text("Beep") }
            OutlinedButton(onClick = { vm.speakerClick() }, modifier = Modifier.weight(1f)) { Text("Click") }
            OutlinedButton(onClick = { vm.speakerAlert() }, modifier = Modifier.weight(1f)) { Text("Alert") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(440, 880, 1760, 3520).forEach { f ->
                OutlinedButton(onClick = { vm.beep(f, 150) }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)) { Text("${f}Hz", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
