package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Path
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
            BeaconAnatomyCard()
            ChannelPlannerCard()
            FieldExamCard()
        }
    }
}

private data class BeaconField(val name: String, val value: String, val desc: String)

@Composable
private fun BeaconAnatomyCard() {
    val fields = listOf(
        BeaconField("Timestamp", "0x00A3F2", "The AP clock in microseconds — keeps every client on the network in sync."),
        BeaconField("Interval", "100 TU", "How often the beacon is sent: ~10 times a second (1 TU = 1024 µs)."),
        BeaconField("Capability", "ESS · Privacy", "Flags: ESS = it's an access point; Privacy = encryption is on."),
        BeaconField("SSID", "\"Smith_Home\"", "The network name the AP shouts — the human-readable label."),
        BeaconField("Rates", "1..54 Mb/s", "The data rates this AP supports, oldest to newest."),
        BeaconField("DS Param", "channel 6", "The channel the AP is actually on — what our scanner reads."),
        BeaconField("RSN IE", "CCMP · PSK", "The security suite: CCMP cipher, pre-shared-key auth (that's WPA2)."),
        BeaconField("BSSID", "A4:83:E7", "The AP's MAC address — its unique hardware fingerprint."),
    )
    var sel by remember { mutableStateOf(0) }
    LabCard("BEACON FRAME ANATOMY", "A router shouts \"I am here!\" ~10 times a second. Tap a field to see what it carries:") {
        // simple two-column wrap
        fields.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { f ->
                    val idx = fields.indexOf(f)
                    val on = idx == sel
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (on) GOOD.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .clickable { sel = idx }.padding(8.dp),
                    ) {
                        Text(f.name.uppercase(), style = MaterialTheme.typography.labelSmall,
                            color = if (on) GOOD else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
                        Text(f.value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    }
                }
                if (pair.size == 1) Column(Modifier.weight(1f)) {}
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(fields[sel].name, color = ACCENT, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(fields[sel].desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun ChannelPlannerCard() {
    var a by remember { mutableFloatStateOf(1f) }
    var b by remember { mutableFloatStateOf(6f) }
    var c by remember { mutableFloatStateOf(11f) }
    LabCard("2.4 GHZ CHANNEL PLANNER", "Each channel is ~22 MHz wide and bleeds into its neighbours. Set three and watch why only 1 / 6 / 11 stay clear:") {
        val cols = listOf(Triple(a, Color(0xFF22D3EE), "A"), Triple(b, Color(0xFF34D399), "B"), Triple(c, Color(0xFFFF7AD9), "C"))
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val w = size.width; val h = size.height
            fun x(ch: Float) = (ch - 1f) / 12f * w
            // baseline
            cols.forEach { (ch, col, _) ->
                val p = Path().apply {
                    moveTo(x(ch - 2.5f), h); lineTo(x(ch), h * 0.08f); lineTo(x(ch + 2.5f), h); close()
                }
                drawPath(p, col.copy(alpha = 0.33f))
                drawPath(p, col, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("1", "6", "11").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.weight(1f))
            }
        }
        ChannelSlider("AP A · ${a.toInt()}", a, Color(0xFF22D3EE)) { a = it }
        ChannelSlider("AP B · ${b.toInt()}", b, Color(0xFF34D399)) { b = it }
        ChannelSlider("AP C · ${c.toInt()}", c, Color(0xFFFF7AD9)) { c = it }
        val clean = setOf(a.toInt(), b.toInt(), c.toInt()) == setOf(1, 6, 11)
        Text(
            if (clean) "✓ 1 / 6 / 11 — the only trio with no overlap." else "Overlap! Non-1/6/11 channels bleed into each other and slow everyone down.",
            style = MaterialTheme.typography.bodySmall, color = if (clean) GOOD else WARN, fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ChannelSlider(label: String, value: Float, color: Color, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontFamily = FontFamily.Monospace)
        Slider(value = value, onValueChange = { onChange(it) }, valueRange = 1f..13f, steps = 11)
    }
}

private data class Quiz(val q: String, val options: List<String>, val answer: Int)

@Composable
private fun FieldExamCard() {
    val quiz = remember {
        listOf(
            Quiz("RSSI of −90 dBm means the signal is…", listOf("Very strong", "At the edge of range", "Medium"), 1),
            Quiz("Which 2.4 GHz channels don't overlap?", listOf("1, 6, 11", "All 14", "Any three"), 0),
            Quiz("A randomized/private MAC has which bit set?", listOf("Multicast bit", "Locally-administered bit", "Broadcast bit"), 1),
        )
    }
    var i by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf(-1) }
    var score by remember { mutableStateOf(0) }
    LabCard("FIELD EXAM", "Three questions from what you've learned.") {
        if (i >= quiz.size) {
            Text("Score: $score / ${quiz.size}", color = ACCENT, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(if (score == quiz.size) "Perfect — you read the air." else "Scroll back up and try the instruments again.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        } else {
            val q = quiz[i]
            Text("${i + 1}/${quiz.size} · ${q.q}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            q.options.forEachIndexed { oi, opt ->
                val color = when {
                    picked < 0 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                    oi == q.answer -> GOOD.copy(alpha = 0.22f)
                    oi == picked -> BAD.copy(alpha = 0.22f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(color)
                        .clickable(enabled = picked < 0) {
                            picked = oi; if (oi == q.answer) score++
                        }.padding(12.dp),
                ) { Text(opt, style = MaterialTheme.typography.bodyMedium) }
            }
            if (picked >= 0) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
                    .background(ACCENT.copy(alpha = 0.14f)).clickable { i++; picked = -1 }.padding(12.dp)) {
                    Text(if (i == quiz.size - 1) "See score" else "Next question →", color = ACCENT, fontWeight = FontWeight.Bold)
                }
            }
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
