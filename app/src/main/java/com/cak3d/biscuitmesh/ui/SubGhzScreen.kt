package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.model.LogEntry
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs

// The warm-amber accent shared with the Sub-GHz map view.
private val RF_AMBER = Color(0xFFFF8A3D)
private const val RF_WINDOW_MS = 45_000L   // a band counts as "active" if it fired this recently

private data class RfBand(val mhz: String, val sub: String, val centerMhz: Double)

private val RF_BANDS = listOf(
    RfBand("315 MHz", "TPMS · keyfobs (US)", 315.0),
    RfBand("433 MHz", "remotes · sensors · TPMS (EU)", 433.92),
    RfBand("868 MHz", "ISM sensors (EU)", 868.0),
    RfBand("915 MHz", "ISM · LoRa (US)", 915.0),
)

private data class RfHit(val freq: Double, val rssi: Int, val timeMs: Long)

private data class Capability(val title: String, val desc: String, val icon: ImageVector, val live: Boolean)

private val CAPABILITIES = listOf(
    Capability("Band activity monitor", "Which sub-GHz bands are busy and how strong — live from the node's CC1101.", Icons.Default.GraphicEq, true),
    Capability("TPMS reader", "Decode tire-pressure sensors as cars pass: sensor ID, pressure, temperature.", Icons.Default.DirectionsCar, false),
    Capability("Weather & 433 sensors", "433/868 MHz temperature, humidity and rain stations in range.", Icons.Default.Thermostat, false),
    Capability("Keyfobs · remotes · garage", "OOK 433 remotes, doorbells and garage openers — activity and device IDs.", Icons.Default.SettingsRemote, false),
)

/**
 * The sub-GHz hub for the wtBLEep node's CC1101 antenna. Shows live 315/433/868/915 MHz
 * band activity parsed from the node's DATA:RF stream, a sweep control, the capability
 * roadmap (what the antenna can do as we add decoders to the firmware), and a raw RF log.
 */
@Composable
fun SubGhzScreen(vm: MainViewModel, onOpenMap: () -> Unit) {
    val logFlow = vm.log ?: remember { MutableStateFlow(emptyList<LogEntry>()) }
    val log by logFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var sweeping by remember { mutableStateOf(false) }

    val hits = remember(log) {
        log.asSequence()
            .filter { it.message.type == BiscuitMessage.Type.DATA && it.message.subtype == "RF" }
            .mapNotNull { e ->
                val f = e.message.payload.split(",")
                val freq = f.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                val rssi = f.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                RfHit(freq, rssi, e.timeMs)
            }
            .toList()
    }
    val now = System.currentTimeMillis()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header + sweep control.
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.SettingsInputAntenna, contentDescription = null, tint = RF_AMBER, modifier = Modifier.size(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Sub-GHz antenna", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (hits.isEmpty()) "No RF seen yet — connect the wtBLEep node and wire the CC1101"
                            else "${hits.size} RF hit${if (hits.size == 1) "" else "s"} logged",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (sweeping) { vm.stopSubGhzSweep(); sweeping = false }
                            else { vm.startSubGhzSweep(); sweeping = true }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(if (sweeping) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (sweeping) "Stop sweep" else "Start sweep")
                    }
                    OutlinedButton(onClick = onOpenMap, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Map, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Map")
                    }
                }
            }
        }

        // Live band meters.
        Text("BANDS", style = MaterialTheme.typography.labelMedium, color = RF_AMBER,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        RF_BANDS.forEach { band ->
            val recent = hits.filter { now - it.timeMs <= RF_WINDOW_MS && bandFor(it.freq) == band }
            val best = recent.maxByOrNull { it.rssi }?.rssi
            BandMeter(band, best, recent.size)
        }

        // Capability roadmap.
        Text("CAPABILITIES", style = MaterialTheme.typography.labelMedium, color = RF_AMBER,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        CAPABILITIES.forEach { CapabilityRow(it) }

        // Raw RF log.
        Text("RF LOG", style = MaterialTheme.typography.labelMedium, color = RF_AMBER,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A08)),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (hits.isEmpty()) {
                    Text("waiting for DATA:RF…", style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace, color = RF_AMBER.copy(alpha = 0.6f))
                } else {
                    hits.takeLast(18).reversed().forEach { h ->
                        Text("${"%.2f".format(h.freq)} MHz   ${h.rssi} dBm", style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace, color = RF_AMBER)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BandMeter(band: RfBand, bestRssi: Int?, count: Int) {
    // Normalise CC1101 RSSI (~ -110 dBm noise .. -30 dBm strong) to a 0..1 bar.
    val strength = bestRssi?.let { ((it + 110f) / 80f).coerceIn(0f, 1f) } ?: 0f
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(band.mhz, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(band.sub, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Text(
                    if (bestRssi != null) "$bestRssi dBm · ${count}×" else "quiet",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = if (bestRssi != null) RF_AMBER else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
            ) {
                if (strength > 0f) {
                    Box(Modifier.fillMaxWidth(strength).height(8.dp).clip(RoundedCornerShape(4.dp)).background(RF_AMBER))
                }
            }
        }
    }
}

@Composable
private fun CapabilityRow(cap: Capability) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(cap.icon, contentDescription = null, tint = RF_AMBER, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(cap.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(cap.desc, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            }
            StatusChip(cap.live)
        }
    }
}

@Composable
private fun StatusChip(live: Boolean) {
    val color = if (live) RF_AMBER else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(if (live) "LIVE" else "SOON", style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = color)
    }
}

private fun bandFor(freq: Double): RfBand = RF_BANDS.minByOrNull { abs(it.centerMhz - freq) }!!
