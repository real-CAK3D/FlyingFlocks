package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.cak3d.biscuitmesh.sensors.OuiDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.sensors.PhoneScanRow
import com.cak3d.biscuitmesh.tools.Tool
import com.cak3d.biscuitmesh.tools.ToolCatalog
import com.cak3d.biscuitmesh.wardrive.SampleKind

private val WIFI_CYAN = Color(0xFF22D3EE)
private val BT_BLUE = Color(0xFF7C9CFF)

/**
 * WiFi hub — a live monitor dashboard in the Sub-GHz page's style. Scans with the
 * phone's own radio and lists every network split by band (5 GHz, which only the
 * wtBLEep node otherwise catches, and 2.4 GHz) with full info: SSID, BSSID, channel
 * and signal. The WiFi tool launcher stays below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScreen(vm: MainViewModel, onOpenTool: (String) -> Unit) {
    val scanning by vm.phoneScanning.collectAsStateWithLifecycle(initialValue = false)
    val results by vm.phoneScanResults.collectAsStateWithLifecycle(initialValue = emptyList())
    val nets = results.filter { it.kind == SampleKind.AP || it.kind == SampleKind.STATION }
    val g5 = nets.filter { it.channel >= 32 }
    val g24 = nets.filter { it.channel in 1..14 }
    val other = nets.filter { it.channel == 0 }
    DisposableEffect(Unit) { onDispose { vm.stopPhoneScan() } }

    val context = LocalContext.current
    var ouiReady by remember { mutableStateOf(OuiDb.loaded) }
    LaunchedEffect(Unit) {
        if (!OuiDb.loaded) withContext(Dispatchers.IO) { OuiDb.ensureLoaded(context) }
        ouiReady = OuiDb.loaded
    }

    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Sniffing" to ToolCatalog.wifiSniffing,
        "Attacks" to ToolCatalog.wifiAttacks,
        "Scanners" to ToolCatalog.wifiScanners,
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("head") {
            ScanHeaderCard(
                accent = WIFI_CYAN, icon = Icons.Default.Wifi, title = "WiFi",
                subtitle = if (scanning) "${nets.size} network${plural(nets.size)} · ${g5.size} on 5 GHz — live"
                else "Scan with the phone's own radio — no board needed",
                scanning = scanning,
                onScan = { vm.clearPhoneScan(); vm.startPhoneScan(wifi = true, ble = false) },
                onStop = { vm.stopPhoneScan() },
            )
        }
        if (nets.isNotEmpty()) item("spectrum") { SpectrumCard(g24, g5, WIFI_CYAN) }
        if (nets.isNotEmpty()) item("enc") { EncryptionCard(nets, WIFI_CYAN) }
        if (nets.isNotEmpty()) item("mfg") { ManufacturersCard(nets, ouiReady, WIFI_CYAN) }
        if (g5.isNotEmpty()) {
            item("l5") { SectionLabel("5 GHZ · ${g5.size}", WIFI_CYAN) }
            items(g5, key = { "g5-${it.id}" }) { NetworkRow(it, WIFI_CYAN) }
        }
        if (g24.isNotEmpty()) {
            item("l24") { SectionLabel("2.4 GHZ · ${g24.size}", WIFI_CYAN) }
            items(g24, key = { "g24-${it.id}" }) { NetworkRow(it, WIFI_CYAN) }
        }
        if (other.isNotEmpty()) {
            item("lo") { SectionLabel("OTHER · ${other.size}", WIFI_CYAN) }
            items(other, key = { "go-${it.id}" }) { NetworkRow(it, WIFI_CYAN) }
        }
        if (nets.isEmpty()) item("empty") { EmptyScanHint(scanning, "networks", WIFI_CYAN) }

        item("tlabel") { SectionLabel("TOOLS", WIFI_CYAN) }
        item("tabs") { ToolTabRow(tabs.map { it.first }, tab) { tab = it } }
        items(tabs[tab.coerceIn(0, tabs.lastIndex)].second, key = { "t-${it.key}" }) {
            ToolRow(it, Icons.Default.Wifi) { onOpenTool(it.key) }
        }
    }
}

/**
 * Bluetooth hub — the same live monitor dashboard, for BLE. Lists every device the
 * phone's radio sees with name, MAC and signal (BLE is 2.4 GHz only). Tool launcher below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScreen(vm: MainViewModel, onOpenTool: (String) -> Unit) {
    val scanning by vm.phoneScanning.collectAsStateWithLifecycle(initialValue = false)
    val results by vm.phoneScanResults.collectAsStateWithLifecycle(initialValue = emptyList())
    val devs = results.filter { it.kind == SampleKind.BT }
    DisposableEffect(Unit) { onDispose { vm.stopPhoneScan() } }

    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Scanning" to ToolCatalog.btScanning,
        "Attacks" to ToolCatalog.btAttacks,
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("head") {
            ScanHeaderCard(
                accent = BT_BLUE, icon = Icons.Default.Bluetooth, title = "Bluetooth",
                subtitle = if (scanning) "${devs.size} device${plural(devs.size)} — live"
                else "Scan with the phone's own radio — no board needed",
                scanning = scanning,
                onScan = { vm.clearPhoneScan(); vm.startPhoneScan(wifi = false, ble = true) },
                onStop = { vm.stopPhoneScan() },
            )
        }
        if (devs.isNotEmpty()) item("priv") { PrivacyCard(devs, BT_BLUE) }
        if (devs.isNotEmpty()) {
            item("ld") { SectionLabel("DEVICES · ${devs.size}", BT_BLUE) }
            items(devs, key = { "d-${it.id}" }) { DeviceRow(it, BT_BLUE) }
        } else item("empty") { EmptyScanHint(scanning, "BLE devices", BT_BLUE) }

        item("tlabel") { SectionLabel("TOOLS", BT_BLUE) }
        item("tabs") { ToolTabRow(tabs.map { it.first }, tab) { tab = it } }
        items(tabs[tab.coerceIn(0, tabs.lastIndex)].second, key = { "t-${it.key}" }) {
            ToolRow(it, Icons.Default.Bluetooth) { onOpenTool(it.key) }
        }
    }
}

private fun plural(n: Int) = if (n == 1) "" else "s"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiscScreen(onOpenTool: (String) -> Unit) {
    TabbedToolScreen(
        title = "Misc Tools",
        leadingIcon = Icons.Default.Dashboard,
        tabs = listOf("Tools" to ToolCatalog.misc),
        onOpenTool = onOpenTool,
    )
}

/** Dashboard header: icon + title + live count, and a full-width Scan/Stop control. */
@Composable
private fun ScanHeaderCard(
    accent: Color,
    icon: ImageVector,
    title: String,
    subtitle: String,
    scanning: Boolean,
    onScan: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                }
            }
            Button(onClick = { if (scanning) onStop() else onScan() }, modifier = Modifier.fillMaxWidth()) {
                Icon(if (scanning) Icons.Default.Stop else icon, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (scanning) "Stop scan" else "Scan with this phone")
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, accent: Color) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = accent,
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
}

@Composable
private fun EmptyScanHint(scanning: Boolean, what: String, accent: Color) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0C)),
    ) {
        Text(
            if (scanning) "scanning… nothing yet" else "tap Scan to sense $what with this phone",
            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            color = accent.copy(alpha = 0.6f), modifier = Modifier.padding(14.dp),
        )
    }
}

/** One Wi-Fi network: SSID, BSSID, channel + band, signal and a strength bar. */
@Composable
private fun NetworkRow(row: PhoneScanRow, accent: Color) {
    val band = when {
        row.channel >= 32 -> "5 GHz"
        row.channel in 1..14 -> "2.4 GHz"
        else -> "band ?"
    }
    val meta = buildString {
        if (row.channel > 0) { append("ch ${row.channel}"); append("  ·  ") }
        append(band)
    }
    SignalCard(accent, row.label.ifBlank { "(hidden network)" }, row.id, meta, row.rssi, wifiStrength(row.rssi))
}

/** One BLE device: name, MAC, signal and a strength bar. */
@Composable
private fun DeviceRow(row: PhoneScanRow, accent: Color) {
    SignalCard(accent, row.label.ifBlank { "(unnamed)" }, row.id,
        "BLE  ·  2.4 GHz  ·  ${bleAddrType(row.id)}", row.rssi, btStrength(row.rssi))
}

@Composable
private fun SignalCard(accent: Color, title: String, mono: String, meta: String, rssi: Int, strength: Float) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(mono, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
                Text("$rssi dBm", style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace, color = accent)
            }
            Text(meta, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
            ) {
                if (strength > 0f) Box(Modifier.fillMaxWidth(strength).height(6.dp)
                    .clip(RoundedCornerShape(3.dp)).background(accent))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolTabRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    TabRow(selectedTabIndex = selected.coerceIn(0, labels.lastIndex), containerColor = Color.Transparent) {
        labels.forEachIndexed { i, l ->
            Tab(selected = selected == i, onClick = { onSelect(i) }, text = { Text(l) })
        }
    }
}

// RSSI → 0..1 bar. Wi-Fi: ~ -90 (weak) .. -30 (strong). BLE: ~ -100 .. -40.
private fun wifiStrength(rssi: Int) = ((rssi + 90f) / 60f).coerceIn(0f, 1f)
private fun btStrength(rssi: Int) = ((rssi + 100f) / 60f).coerceIn(0f, 1f)

private val BAND_24 = Color(0xFF60A5FA)
private val BAND_5 = Color(0xFFF59E0B)

/** Spectrum summary: 2.4-vs-5 GHz split and the 2.4 GHz channel-congestion story (1/6/11). */
@Composable
private fun SpectrumCard(g24: List<PhoneScanRow>, g5: List<PhoneScanRow>, accent: Color) {
    val ch1 = g24.count { it.channel == 1 }
    val ch6 = g24.count { it.channel == 6 }
    val ch11 = g24.count { it.channel == 11 }
    val chOther = (g24.size - ch1 - ch6 - ch11).coerceAtLeast(0)
    val maxCh = maxOf(ch1, ch6, ch11, chOther, 1)
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("SPECTRUM", style = MaterialTheme.typography.labelMedium, color = accent,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

            // Band split bar (2.4 vs 5 GHz).
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))) {
                Row(Modifier.fillMaxWidth().height(14.dp)) {
                    if (g24.isNotEmpty()) Box(Modifier.fillMaxHeight().weight(g24.size.toFloat()).background(BAND_24))
                    if (g5.isNotEmpty()) Box(Modifier.fillMaxHeight().weight(g5.size.toFloat()).background(BAND_5))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text("2.4 GHz · ${g24.size}", style = MaterialTheme.typography.bodySmall, color = BAND_24,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                Text("5 GHz · ${g5.size}", style = MaterialTheme.typography.bodySmall, color = BAND_5,
                    fontFamily = FontFamily.Monospace)
            }

            // 2.4 GHz channel congestion — only 1/6/11 don't overlap, so they dominate.
            Text("2.4 GHZ CHANNELS", style = MaterialTheme.typography.labelSmall, color = accent,
                fontFamily = FontFamily.Monospace)
            ChannelBar("ch 1", ch1, maxCh, accent)
            ChannelBar("ch 6", ch6, maxCh, accent)
            ChannelBar("ch 11", ch11, maxCh, accent)
            ChannelBar("other", chOther, maxCh, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun ChannelBar(label: String, count: Int, max: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(48.dp))
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))) {
            if (count > 0) Box(Modifier.fillMaxWidth(count.toFloat() / max).height(10.dp)
                .clip(RoundedCornerShape(5.dp)).background(color))
        }
        Text("$count", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

/** Encryption Shield: how the nearby networks are locked, weakest flagged. */
@Composable
private fun EncryptionCard(nets: List<PhoneScanRow>, accent: Color) {
    val counts = nets.groupingBy { it.security.ifBlank { "Open" } }.eachCount()
    val total = nets.size.coerceAtLeast(1)
    val order = listOf("WPA3", "WPA2", "WPA", "WEP", "Open", "Ent")
    val openWep = (counts["Open"] ?: 0) + (counts["WEP"] ?: 0)
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ENCRYPTION", style = MaterialTheme.typography.labelMedium, color = accent,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            order.forEach { sec ->
                val n = counts[sec] ?: 0
                if (n > 0) PctBar(sec, n, total, secColor(sec))
            }
            if (openWep > 0) {
                Text("$openWep open or WEP — no real lock", style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF5470), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** BLE address-privacy breakdown — most devices rotate/randomize, derived from the address bits. */
@Composable
private fun PrivacyCard(devs: List<PhoneScanRow>, accent: Color) {
    val counts = devs.groupingBy { bleAddrType(it.id) }.eachCount()
    val total = devs.size.coerceAtLeast(1)
    val order = listOf("private rotating", "random static", "non-resolvable", "public / maker")
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ADDRESS PRIVACY", style = MaterialTheme.typography.labelMedium, color = accent,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text("Most BLE devices rotate or randomize their address every few minutes, so one device looks like many.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            order.forEach { t ->
                val n = counts[t] ?: 0
                if (n > 0) PctBar(t, n, total, typeColor(t))
            }
        }
    }
}

/** Manufacturers from the MAC OUI prefix; randomized/private addresses are grouped out. */
@Composable
private fun ManufacturersCard(nets: List<PhoneScanRow>, ouiReady: Boolean, accent: Color) {
    val counts = remember(nets, ouiReady) {
        nets.groupingBy { n ->
            OuiDb.vendor(n.id) ?: if (OuiDb.isRandom(n.id)) "Unknown / random" else "Unknown"
        }.eachCount()
    }
    val total = nets.size.coerceAtLeast(1)
    val top = counts.entries.sortedByDescending { it.value }.take(8)
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MANUFACTURERS", style = MaterialTheme.typography.labelMedium, color = accent,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            if (!ouiReady) {
                Text("loading vendor database…", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
            }
            top.forEach { (name, n) -> PctBar(name, n, total, accent) }
        }
    }
}

@Composable
private fun PctBar(label: String, count: Int, total: Int, color: Color) {
    val frac = (count.toFloat() / total).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
            Text("$count · ${"%.1f".format(frac * 100)}%", style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace, color = color)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))) {
            if (frac > 0f) Box(Modifier.fillMaxWidth(frac).height(8.dp)
                .clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

private fun secColor(sec: String): Color = when (sec) {
    "WPA3" -> Color(0xFF34D399)
    "WPA2" -> Color(0xFF22D3EE)
    "Ent" -> Color(0xFF7C9CFF)
    "WPA", "WEP" -> Color(0xFFF59E0B)
    else -> Color(0xFFFF5470) // Open
}

// BLE random-address class from the top two bits of the first address octet.
private fun bleAddrType(mac: String): String {
    val first = mac.substringBefore(":").toIntOrNull(16) ?: return "public / maker"
    return when ((first ushr 6) and 0x3) {
        0b11 -> "random static"
        0b01 -> "private rotating"
        0b00 -> "non-resolvable"
        else -> "public / maker"
    }
}

private fun typeColor(t: String): Color = when (t) {
    "private rotating" -> Color(0xFFFF5470)
    "random static" -> Color(0xFFF59E0B)
    "non-resolvable" -> Color(0xFF7C9CFF)
    else -> Color(0xFF34D399) // public / maker
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabbedToolScreen(
    title: String,
    leadingIcon: ImageVector,
    tabs: List<Pair<String, List<Tool>>>,
    onOpenTool: (String) -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        if (tabs.size > 1) {
            TabRow(selectedTabIndex = selected, containerColor = Color.Transparent) {
                tabs.forEachIndexed { i, (label, _) ->
                    Tab(
                        selected = selected == i,
                        onClick = { selected = i },
                        text = { Text(label) },
                    )
                }
            }
        }
        val tools = tabs[selected.coerceIn(0, tabs.lastIndex)].second
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(tools, key = { it.key }) { tool ->
                ToolRow(tool, leadingIcon) { onOpenTool(tool.key) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolRow(tool: Tool, leadingIcon: ImageVector, onClick: () -> Unit) {
    val accent = if (tool.offensive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (tool.offensive) Icons.Default.Warning else leadingIcon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(26.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(tool.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    tool.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                if (!tool.documented) {
                    Text(
                        "command editable — verify before running",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
