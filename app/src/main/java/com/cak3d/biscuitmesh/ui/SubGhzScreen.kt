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

private data class Capability(val title: String, val desc: String, val icon: ImageVector)

/** A decoded TPMS sensor — keyed by its 4-byte ID, latest pressure/temp. */
private data class TpmsSensor(val id: String, val freq: Double, val kpa: Int, val tempC: Int, val timeMs: Long) {
    val psi: Float get() = kpa * 0.145038f
}

/** A keyfob/remote OOK burst the node caught. */
private data class KeyfobEvent(val freq: Double, val pulses: Int, val bits: String, val timeMs: Long)

/** A 433/868 MHz weather / environment sensor reading. */
private data class WeatherStation(val id: String, val tempC: Double?, val humidity: Int?, val timeMs: Long)

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
    var txConfirm by remember { mutableStateOf<KeyfobEvent?>(null) }

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
    // Decoded TPMS: DATA:TPMS:<mhz>,<id>,<kpa>,<tempC>,<rawhex> — keep the latest per sensor id.
    val tpms = remember(log) {
        log.asSequence()
            .filter { it.message.type == BiscuitMessage.Type.DATA && it.message.subtype == "TPMS" }
            .mapNotNull { e ->
                val f = e.message.payload.split(",")
                val freq = f.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                val id = f.getOrNull(1) ?: return@mapNotNull null
                TpmsSensor(id, freq, f.getOrNull(2)?.toIntOrNull() ?: 0, f.getOrNull(3)?.toIntOrNull() ?: 0, e.timeMs)
            }
            .associateBy { it.id }.values.sortedByDescending { it.timeMs }
    }
    // Keyfob/remote bursts: DATA:KEYFOB:<mhz>,<pulses>,<bits>.
    val keyfobs = remember(log) {
        log.asSequence()
            .filter { it.message.type == BiscuitMessage.Type.DATA && it.message.subtype == "KEYFOB" }
            .mapNotNull { e ->
                val f = e.message.payload.split(",")
                val freq = f.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                KeyfobEvent(freq, f.getOrNull(1)?.toIntOrNull() ?: 0, f.getOrNull(2) ?: "", e.timeMs)
            }
            .toList()
    }
    // Weather / 433 sensors: DATA:WEATHER:<mhz>,<id>,<tempC>,<humidity%>[,<rain>] — latest per station.
    val weather = remember(log) {
        log.asSequence()
            .filter { it.message.type == BiscuitMessage.Type.DATA && it.message.subtype == "WEATHER" }
            .mapNotNull { e ->
                val f = e.message.payload.split(",")
                val id = f.getOrNull(1) ?: return@mapNotNull null
                WeatherStation(id, f.getOrNull(2)?.toDoubleOrNull(), f.getOrNull(3)?.toIntOrNull(), e.timeMs)
            }
            .associateBy { it.id }.values.sortedByDescending { it.timeMs }
    }
    val now = System.currentTimeMillis()
    // Capability cards go LIVE as real decodes arrive from the node (TPMS/weather/keyfob
    // decoders all live in the firmware now); a card stays "SOON" only until its first hit.
    val capabilities = listOf(
        Capability("Band activity monitor", "Which sub-GHz bands are busy and how strong — live from the node's CC1101.", Icons.Default.GraphicEq) to true,
        Capability("TPMS reader", "FSK tire-pressure sensors, positively identified as cars pass: sensor ID, pressure, temperature.", Icons.Default.DirectionsCar) to tpms.isNotEmpty(),
        Capability("Weather & 433 sensors", "433/868 MHz temperature, humidity and rain stations in range.", Icons.Default.Thermostat) to weather.isNotEmpty(),
        Capability("OOK bursts · keyfobs · remotes", "433 OOK bursts — keyfobs, doorbells, garage openers (and OOK TPMS, which read the same).", Icons.Default.SettingsRemote) to keyfobs.isNotEmpty(),
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header + sweep control.
        Card(
            Modifier.fillMaxWidth(),
            shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
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

        // Waterfall spectrogram — the last 60s of RF across 300–950 MHz.
        if (hits.isNotEmpty()) {
            Text("WATERFALL", style = MaterialTheme.typography.labelMedium, color = RF_AMBER,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            WaterfallCard(hits, now)
        }

        // Live band meters.
        Text("BANDS", style = MaterialTheme.typography.labelMedium, color = RF_AMBER,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        RF_BANDS.forEach { band ->
            val recent = hits.filter { now - it.timeMs <= RF_WINDOW_MS && bandFor(it.freq) == band }
            val best = recent.maxByOrNull { it.rssi }?.rssi
            BandMeter(band, best, recent.size)
        }

        // Decoded TPMS sensors + keyfob bursts (populated once the CC1101 is wired).
        if (tpms.isNotEmpty() || keyfobs.isNotEmpty()) {
            Text("DECODED", style = MaterialTheme.typography.labelMedium, color = RF_AMBER,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(
                "TPMS rows are positively identified (FSK, decoded sensor ID + pressure). " +
                    "Raw OOK bursts below could be a keyfob, a remote or an OOK-based TPMS — the node " +
                    "can't tell them apart without a per-protocol decode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            tpms.take(8).forEach { TpmsRow(it) }
            keyfobs.takeLast(6).reversed().forEach { KeyfobRow(it) }
        }

        // Weather / 433 sensors.
        if (weather.isNotEmpty()) {
            Text("WEATHER & SENSORS", style = MaterialTheme.typography.labelMedium, color = RF_AMBER,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    weather.take(8).forEach { ws ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("sensor ${ws.id}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                            Text(buildString {
                                ws.tempC?.let { append("%.1f°C".format(it)) }
                                ws.humidity?.let { if (isNotEmpty()) append("  ·  "); append("$it%") }
                            }, fontFamily = FontFamily.Monospace, color = RF_AMBER, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Gated transmit: replay a captured OOK burst onto YOUR OWN device only.
        if (keyfobs.isNotEmpty()) {
            Text("TRANSMIT", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFF5470),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Replay a captured burst back out the CC1101. Only ever transmit on devices you own and are authorised to operate.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    keyfobs.takeLast(4).reversed().forEach { k ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${"%.2f".format(k.freq)} MHz · ${k.pulses}p", fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { txConfirm = k }) { Text("Replay") }
                        }
                    }
                }
            }
        }

        // Capability roadmap.
        Text("CAPABILITIES", style = MaterialTheme.typography.labelMedium, color = RF_AMBER,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        capabilities.forEach { (cap, live) -> CapabilityRow(cap, live) }

        // Raw RF log.
        Text("RF LOG", style = MaterialTheme.typography.labelMedium, color = RF_AMBER,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Card(
            Modifier.fillMaxWidth(),
            shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
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

    txConfirm?.let { k ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { txConfirm = null },
            title = { Text("Transmit on your own device?") },
            text = {
                Text("This re-transmits the captured ${"%.2f".format(k.freq)} MHz burst. Only do this on a device you " +
                    "own and are authorised to operate — never on anyone else's vehicle, gate or property.")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.transmitSubGhz("%.2f".format(k.freq), k.bits); txConfirm = null
                }) { Text("I own this — transmit") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { txConfirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun WaterfallCard(hits: List<RfHit>, now: Long) {
    val windowMs = 60_000L
    Card(
        Modifier.fillMaxWidth(),
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("300–950 MHz · last 60s · newest on top · brighter = stronger",
                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val w = size.width; val h = size.height
                fun fx(f: Double) = (((f - 300.0) / 650.0).coerceIn(0.0, 1.0) * w).toFloat()
                // band gridlines
                listOf(315.0, 433.92, 868.0, 915.0).forEach { b ->
                    val x = fx(b)
                    drawLine(RF_AMBER.copy(alpha = 0.16f), androidx.compose.ui.geometry.Offset(x, 0f),
                        androidx.compose.ui.geometry.Offset(x, h), 1f)
                }
                hits.forEach { hit ->
                    val age = now - hit.timeMs
                    if (age < 0 || age > windowMs) return@forEach
                    val y = (age.toFloat() / windowMs) * h
                    val x = fx(hit.freq)
                    val strength = ((hit.rssi + 100).coerceIn(0, 70)) / 70f
                    val col = androidx.compose.ui.graphics.lerp(Color(0xFF5A1E00), Color(0xFFFFE08A), strength)
                        .copy(alpha = 0.3f + strength * 0.6f)
                    drawCircle(col, 2.5f + strength * 3.5f, androidx.compose.ui.geometry.Offset(x, y))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("300", "433", "600", "750", "950").forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
private fun BandMeter(band: RfBand, bestRssi: Int?, count: Int) {
    // Normalise CC1101 RSSI (~ -110 dBm noise .. -30 dBm strong) to a 0..1 bar.
    val strength = bestRssi?.let { ((it + 110f) / 80f).coerceIn(0f, 1f) } ?: 0f
    Card(
        Modifier.fillMaxWidth(),
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
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
private fun TpmsRow(s: TpmsSensor) {
    Card(
        Modifier.fillMaxWidth(),
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = RF_AMBER, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("TPMS ${s.id}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace)
                Text("${"%.2f".format(s.freq)} MHz · ${s.tempC}°C", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Text("${"%.1f".format(s.psi)} psi", style = MaterialTheme.typography.titleMedium,
                color = RF_AMBER, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun KeyfobRow(k: KeyfobEvent) {
    Card(
        Modifier.fillMaxWidth(),
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SettingsRemote, contentDescription = null, tint = RF_AMBER, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("OOK burst · ${"%.2f".format(k.freq)} MHz", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Text("${k.pulses} pulses · keyfob / remote / maybe TPMS", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun CapabilityRow(cap: Capability, live: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
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
            StatusChip(live)
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
