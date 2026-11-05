package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cak3d.biscuitmesh.sensors.OuiDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow

private val ACCENT = Color(0xFF22D3EE)
private val GOOD = Color(0xFF34D399)
private val WARN = Color(0xFFF59E0B)
private val BAD = Color(0xFFFF5470)

/**
 * The Lab Bench — hands-on instruments to understand what the radio hears, mirroring the
 * WDGWars workshop: decode a MAC, turn signal strength into distance, and see how long an
 * encryption type takes to crack. All computed locally from the numbers themselves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabBenchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { if (!OuiDb.loaded) withContext(Dispatchers.IO) { OuiDb.ensureLoaded(context) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lab Bench") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MacDecoderCard()
            SignalDistanceCard()
            CrackTimeCard()
        }
    }
}

@Composable
private fun MacDecoderCard() {
    var input by remember { mutableStateOf("A4:83:E7:1C:0D:9F") }
    LabCard("MAC / OUI DECODER", "Every device carries a MAC. Its first half names the maker; one hidden bit tells a real address from a privacy decoy.") {
        OutlinedTextField(
            value = input, onValueChange = { input = it }, singleLine = true,
            label = { Text("MAC address") }, modifier = Modifier.fillMaxWidth(),
        )
        val hex = input.replace(":", "").replace("-", "").uppercase()
        if (hex.length >= 2 && hex.all { it in "0123456789ABCDEF" }) {
            val first = hex.substring(0, 2).toInt(16)
            val multicast = first and 0x01 != 0
            val local = first and 0x02 != 0
            val vendor = if (hex.length >= 6) OuiDb.vendor(input) else null
            Row(Modifier.fillMaxWidth()) {
                Text("VENDOR", Modifier.weight(0.4f), color = ACCENT, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
                Text(vendor ?: if (local) "— (randomized)" else "— (unknown prefix)", Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium)
            }
            if (hex.length >= 6) Row(Modifier.fillMaxWidth()) {
                Text("OUI", Modifier.weight(0.4f), color = ACCENT, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
                Text("${hex.substring(0,2)}:${hex.substring(2,4)}:${hex.substring(4,6)}", Modifier.weight(0.6f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            }
            Tag(if (local) "RANDOM / PRIVATE" else "REAL / FACTORY", if (local) WARN else GOOD)
            Text(
                if (local) "Locally-administered bit set — a randomized/private address, rotated for privacy. It names no real hardware."
                else "Universally-administered — a genuine factory address that truly identifies the hardware." +
                    (if (multicast) " (multicast)" else ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        } else {
            Text("enter a hex MAC like A4:83:E7:1C:0D:9F", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun SignalDistanceCard() {
    var rssi by remember { mutableFloatStateOf(-70f) }
    LabCard("SIGNAL → DISTANCE", "Signal strength (dBm) fades with distance. Drag to estimate the gap.") {
        // Log-distance path loss: d = 10^((P@1m − RSSI) / (10·n)), P@1m ≈ −40, n ≈ 2.7 (indoor-ish).
        val d = 10.0.pow(((-40.0) - rssi) / (10.0 * 2.7))
        val label = when {
            d < 3 -> "same room"
            d < 10 -> "next room"
            d < 30 -> "across the street"
            d < 100 -> "down the block"
            else -> "far / open-air only"
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${rssi.toInt()} dBm", color = ACCENT, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("~${d.toInt()} m · $label", color = WARN, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = rssi, onValueChange = { rssi = it }, valueRange = -95f..-30f)
        Text("closer to 0 = strong; −90 is the edge of range.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun CrackTimeCard() {
    val options = listOf("Open", "WEP", "WPA", "WPA2", "WPA3")
    var sel by remember { mutableStateOf("WPA2") }
    LabCard("HOW LONG TO CRACK", "Not all locks are equal. Pick an encryption type:") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o ->
                FilterChip(selected = sel == o, onClick = { sel = o }, label = { Text(o) })
            }
        }
        val (verdict, detail, color, strength) = crackVerdict(sel)
        Text(verdict, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Box(
            Modifier.fillMaxWidth().padding(top = 2.dp).height(8.dp)
                .clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
        ) {
            Box(Modifier.fillMaxWidth(strength).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

private data class Verdict(val verdict: String, val detail: String, val color: Color, val strength: Float)

private operator fun Verdict.component1() = verdict
private operator fun Verdict.component2() = detail
private operator fun Verdict.component3() = color
private operator fun Verdict.component4() = strength

private fun crackVerdict(enc: String): Verdict = when (enc) {
    "Open" -> Verdict("Instant — no lock", "Anyone nearby reads the traffic. No password at all.", BAD, 0.04f)
    "WEP" -> Verdict("Minutes", "\"Locked\" but broken since 2001; cracked with a laptop in minutes.", BAD, 0.15f)
    "WPA" -> Verdict("Hours–days (weak pw)", "Deprecated; falls to wordlists if the passphrase is common.", WARN, 0.4f)
    "WPA2" -> Verdict("Strong (if the password is)", "Today's normal. Safe with a long passphrase; weak ones fall to wordlists.", ACCENT, 0.72f)
    else -> Verdict("Today's gold standard", "WPA3/SAE resists offline cracking even with a short password.", GOOD, 0.95f)
}

@Composable
private fun Tag(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun LabCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = ACCENT, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            content()
        }
    }
}
