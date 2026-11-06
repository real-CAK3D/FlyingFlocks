package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.data.DeadZone
import com.cak3d.biscuitmesh.data.DeadZoneDevice
import com.cak3d.biscuitmesh.sensors.PhoneScanRow
import com.cak3d.biscuitmesh.wardrive.SampleKind

private val LOCAL = Color(0xFFF59E0B)
private val BT_C = Color(0xFF7C9CFF)
private val WIFI_C = Color(0xFF22D3EE)

/**
 * Dead-zone device monitor. Shows each scanning dead zone and the devices seen inside it,
 * logged ONLY on this phone — this page's data never enters a capture and is never uploaded.
 * Pick the zone you're standing in, scan, and it quietly catalogs what's broadcasting there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadZonesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val zones by vm.deadZones.collectAsStateWithLifecycle(initialValue = emptyList())
    val log by vm.deadZoneLog.collectAsStateWithLifecycle(initialValue = emptyMap())
    val scanning by vm.phoneScanning.collectAsStateWithLifecycle(initialValue = false)
    val results by vm.phoneScanResults.collectAsStateWithLifecycle(initialValue = emptyList())

    var activeZone by remember { mutableStateOf<String?>(null) }
    // One zone? Log into it by default.
    LaunchedEffect(zones) { if (activeZone == null && zones.size == 1) activeZone = zones.first().id }
    // While scanning with a target zone selected, quietly record what's seen — locally.
    LaunchedEffect(results, activeZone, scanning) {
        val z = activeZone
        if (z != null && scanning && results.isNotEmpty()) {
            vm.recordDeadZoneDevices(z, results.map { it.toDeadZoneDevice() })
        }
    }
    DisposableEffect(Unit) { onDispose { vm.stopPhoneScan() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dead Zone Devices") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("badge") { LocalOnlyBadge() }
            item("scan") {
                Card(
                    Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (activeZone == null) "Pick the zone you're in below, then scan."
                            else "Logging into \"${zones.firstOrNull { it.id == activeZone }?.name ?: "zone"}\" while scanning.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(
                            onClick = {
                                if (scanning) vm.stopPhoneScan()
                                else { vm.clearPhoneScan(); vm.startPhoneScan(wifi = true, ble = true) }
                            },
                            enabled = activeZone != null || scanning,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(if (scanning) Icons.Default.Stop else Icons.Default.Wifi, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (scanning) "Stop scanning" else "Scan this zone")
                        }
                    }
                }
            }
            if (zones.isEmpty()) {
                item("none") {
                    Text("No dead zones yet. Add one in Settings (your home or work), then come back here to see what's broadcasting there — privately.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
            items(zones, key = { it.id }) { zone ->
                ZoneCard(
                    zone = zone,
                    devices = log[zone.id].orEmpty(),
                    active = activeZone == zone.id,
                    onToggle = { activeZone = if (activeZone == zone.id) null else zone.id },
                    onClear = { vm.clearDeadZoneLog(zone.id) },
                )
            }
        }
    }
}

@Composable
private fun LocalOnlyBadge() {
    Card(
        Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = LOCAL.copy(alpha = 0.12f)),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = LOCAL)
            Column {
                Text("LOCAL ONLY · NEVER UPLOADED", style = MaterialTheme.typography.labelMedium,
                    color = LOCAL, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text("Everything on this page stays on this phone. It's kept out of every capture, export and upload.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun ZoneCard(zone: DeadZone, devices: List<DeadZoneDevice>, active: Boolean, onToggle: () -> Unit, onClear: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(zone.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("r ${zone.radiusM.toInt()} m · ${devices.size} device${if (devices.size == 1) "" else "s"} logged",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                FilterChip(selected = active, onClick = onToggle, label = { Text(if (active) "Logging" else "Log here") })
            }
            if (devices.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide devices" else "Show devices") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onClear) { Text("Clear", color = MaterialTheme.colorScheme.error) }
                }
                if (expanded) {
                    devices.sortedByDescending { it.rssi }.forEach { d -> DeadZoneDeviceRow(d) }
                }
            }
        }
    }
}

@Composable
private fun DeadZoneDeviceRow(d: DeadZoneDevice) {
    val color = if (d.kind == "BLE") BT_C else WIFI_C
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(d.kind, style = MaterialTheme.typography.labelSmall, color = color, fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(56.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name.ifBlank { "(unnamed)" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(d.mac, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Text("${d.rssi}", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = color)
    }
}

private fun PhoneScanRow.toDeadZoneDevice(): DeadZoneDevice {
    val kind = when {
        this.kind == SampleKind.BT -> "BLE"
        this.channel >= 32 -> "WiFi 5G"
        else -> "WiFi"
    }
    return DeadZoneDevice(id, label, kind, rssi, lastSeenMs)
}
