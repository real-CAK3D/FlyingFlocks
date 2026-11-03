package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.tools.Tool
import com.cak3d.biscuitmesh.tools.ToolCatalog
import com.cak3d.biscuitmesh.wardrive.SampleKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScreen(vm: MainViewModel, onOpenTool: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PhoneScanCard(vm, wifi = true)
        Box(Modifier.fillMaxSize()) {
            TabbedToolScreen(
                title = "WiFi Tools",
                leadingIcon = Icons.Default.Wifi,
                tabs = listOf(
                    "Sniffing" to ToolCatalog.wifiSniffing,
                    "Attacks" to ToolCatalog.wifiAttacks,
                    "Scanners" to ToolCatalog.wifiScanners,
                ),
                onOpenTool = onOpenTool,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScreen(vm: MainViewModel, onOpenTool: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        PhoneScanCard(vm, wifi = false)
        Box(Modifier.fillMaxSize()) {
            TabbedToolScreen(
                title = "Bluetooth Tools",
                leadingIcon = Icons.Default.Bluetooth,
                tabs = listOf(
                    "Scanning" to ToolCatalog.btScanning,
                    "Attacks" to ToolCatalog.btAttacks,
                ),
                onOpenTool = onOpenTool,
            )
        }
    }
}

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

/**
 * Phone-only live scan. Uses the phone's own Wi-Fi or BLE radio (no board needed)
 * to list nearby signals in real time; everything it sees also levels the Field
 * Guide tracks. Scanning stops automatically when you leave the tab.
 */
@Composable
private fun PhoneScanCard(vm: MainViewModel, wifi: Boolean) {
    val scanning by vm.phoneScanning.collectAsStateWithLifecycle(initialValue = false)
    val results by vm.phoneScanResults.collectAsStateWithLifecycle(initialValue = emptyList())
    val rows = results.filter {
        if (wifi) it.kind == SampleKind.AP || it.kind == SampleKind.STATION else it.kind == SampleKind.BT
    }
    DisposableEffect(Unit) { onDispose { vm.stopPhoneScan() } }

    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (wifi) "Scan with this phone" else "BLE scan with this phone",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (scanning) "${rows.size} seen — live" else "No board needed — uses the phone's radio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
                Spacer(Modifier.width(10.dp))
                if (scanning) {
                    Button(onClick = { vm.stopPhoneScan() }) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp)); Text("Stop")
                    }
                } else {
                    Button(onClick = { vm.clearPhoneScan(); vm.startPhoneScan(wifi = wifi, ble = !wifi) }) {
                        Icon(Icons.Default.Wifi, contentDescription = null)
                        Spacer(Modifier.width(6.dp)); Text("Scan")
                    }
                }
            }
            rows.take(12).forEach { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        r.label.ifBlank { if (wifi) "(hidden)" else r.id },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${r.rssi} dBm", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
            if (rows.size > 12) {
                Text("+${rows.size - 12} more", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
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
