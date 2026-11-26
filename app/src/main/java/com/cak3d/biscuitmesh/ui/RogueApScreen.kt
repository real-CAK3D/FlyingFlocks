package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.sensors.OuiDb
import com.cak3d.biscuitmesh.sensors.PhoneScanRow
import com.cak3d.biscuitmesh.wardrive.SampleKind

private val R_CRIT = Color(0xFFFF5470)
private val R_WARN = Color(0xFFFFB020)
private val R_OK = Color(0xFF5FD36A)
private val R_MUTED = Color(0xFF8A9896)
private val MONO = FontFamily.Monospace

private enum class RogueLevel(val tint: Color, val tag: String) {
    CRITICAL(R_CRIT, "EVIL TWIN"), WARN(R_WARN, "LOOK-ALIKE")
}

/** One SSID broadcast by more than one radio, with why it's worth a look. */
private data class RogueFinding(
    val ssid: String,
    val radios: List<PhoneScanRow>,
    val level: RogueLevel,
    val reasons: List<String>,
)

private fun isStrongSecurity(s: String) = s == "WPA2" || s == "WPA3" || s == "Ent"
private fun isWeakOpen(s: String) = s == "Open" || s == "WEP"

/**
 * Pure analysis: given the live AP rows, find SSIDs broadcast by 2+ distinct BSSIDs and rank how
 * suspicious each is. The classic evil-twin tell is a trusted (secured) SSID that is *also* showing
 * up unencrypted/weak — a copy standing by to catch an auto-connect. Several radios on one SSID can
 * be perfectly legit (mesh nodes, range extenders, enterprise APs), so those are flagged lower.
 */
private fun analyze(rows: List<PhoneScanRow>): Pair<Int, List<RogueFinding>> {
    val aps = rows.filter { it.kind == SampleKind.AP && it.label.isNotBlank() }
    // Dedupe by BSSID, keep the strongest sighting of each.
    val byBssid = aps.groupBy { it.id }.values.map { g -> g.maxBy { it.rssi } }
    val groups = byBssid.groupBy { it.label }.filter { it.value.size >= 2 }

    val findings = groups.map { (ssid, radios) ->
        val sorted = radios.sortedByDescending { it.rssi }
        val secs = sorted.map { it.security }.toSet()
        val vendors = sorted.mapNotNull { OuiDb.vendor(it.id) }.toSet()
        val reasons = ArrayList<String>()
        var level = RogueLevel.WARN

        val hasStrong = secs.any { isStrongSecurity(it) }
        val hasWeak = secs.any { isWeakOpen(it) }
        if (hasStrong && hasWeak) {
            level = RogueLevel.CRITICAL
            reasons += "A secured network and an open/weak copy share this name — a classic evil-twin lure."
        } else {
            reasons += "Same name broadcast by ${sorted.size} radios."
        }
        if (vendors.size >= 2) {
            reasons += "Radios from different vendors (${vendors.joinToString(", ")})."
            if (level != RogueLevel.CRITICAL && hasWeak) level = RogueLevel.CRITICAL
        }
        if (secs.size >= 2) reasons += "Mixed security: ${secs.joinToString(", ")}."
        RogueFinding(ssid, sorted, level, reasons)
    }.sortedWith(compareByDescending<RogueFinding> { it.level == RogueLevel.CRITICAL }
        .thenByDescending { it.radios.maxOf { r -> r.rssi } })

    return byBssid.size to findings
}

/**
 * Rogue AP — a defensive sweep for evil-twin / look-alike Wi-Fi. Watches the phone's own live scan
 * and flags any SSID coming from more than one radio, ranking a secured-network-with-an-open-copy as
 * a likely lure. Local only; it reads the air, it never transmits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RogueApScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { OuiDb.ensureLoaded(ctx) }
    DisposableEffect(vm) {
        vm.startPhoneScan(wifi = true, ble = false)
        onDispose { vm.stopPhoneScan() }
    }
    val rows by vm.phoneScanResults.collectAsState()
    val (apCount, findings) = remember(rows) { analyze(rows) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rogue AP") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Rogue AP",
                        "A defensive sweep for evil-twin Wi-Fi. It watches the phone's live scan and flags " +
                            "any network name coming from more than one radio. The dangerous case is a " +
                            "secured network that also shows up open or weak — a copy waiting for your phone " +
                            "to auto-connect so it can snoop. Several radios on one name can be legit (a mesh " +
                            "or range extender), so those are flagged lower. Reads the air only; never transmits.",
                    )
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val crit = findings.count { it.level == RogueLevel.CRITICAL }
                Row(Modifier.fillMaxWidth().help("Evil-twin scan", "Counts the Wi-Fi radios in range, how many SSIDs look duplicated (look-alikes), and how many are likely evil twins — a secured network also appearing open. Zero is healthy."),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("$apCount", "AP RADIOS", R_MUTED, Modifier.weight(1f))
                    StatTile("${findings.size}", "LOOK-ALIKES",
                        if (findings.isEmpty()) R_OK else R_WARN, Modifier.weight(1f))
                    StatTile("$crit", "EVIL TWINS", if (crit == 0) R_OK else R_CRIT, Modifier.weight(1f))
                }
            }

            if (apCount == 0) {
                item { Hint("Scanning the air for Wi-Fi… (needs location + Wi-Fi on).") }
            } else if (findings.isEmpty()) {
                item {
                    Hint("No look-alike networks in range — nothing is broadcasting a duplicate SSID right now. " +
                        "That's the healthy state.")
                }
            } else {
                items(findings) { f -> FindingCard(f) }
                item {
                    Text(
                        "Note: mesh systems, range extenders and enterprise networks legitimately put one " +
                            "name on several radios. Treat an open/weak copy of a secured name as the real " +
                            "red flag, and don't auto-join open networks you don't recognise.",
                        fontSize = 11.sp, color = R_MUTED,
                        modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, tint: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = tint, fontFamily = MONO)
            Text(label, fontSize = 9.sp, letterSpacing = 1.2.sp, color = R_MUTED, fontFamily = MONO)
        }
    }
}

@Composable
private fun FindingCard(f: RogueFinding) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .border(1.5.dp, f.level.tint.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(f.ssid, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Surface(color = f.level.tint.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp)) {
                    Text(f.level.tag, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        color = f.level.tint, fontFamily = MONO, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            f.reasons.forEach { r ->
                Text("• $r", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            }
            f.radios.forEach { ap ->
                val vendor = OuiDb.vendor(ap.id) ?: if (OuiDb.isRandom(ap.id)) "randomized" else "unknown vendor"
                val weak = isWeakOpen(ap.security)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ap.id, fontFamily = MONO, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                        Text("$vendor · ch ${ap.channel}", fontSize = 10.sp, color = R_MUTED)
                    }
                    Surface(
                        color = (if (weak) R_CRIT else R_OK).copy(alpha = 0.16f),
                        shape = RoundedCornerShape(5.dp),
                    ) {
                        Text(ap.security.ifBlank { "Open" }, Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            color = if (weak) R_CRIT else R_OK, fontFamily = MONO, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold)
                    }
                    Text("${ap.rssi}", Modifier.padding(start = 10.dp), fontFamily = MONO, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 13.sp, color = R_MUTED, modifier = Modifier.padding(8.dp))
}
