package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.cak3d.biscuitmesh.data.Target
import com.cak3d.biscuitmesh.data.TargetKind
import com.cak3d.biscuitmesh.sensors.PhoneScanRow
import com.cak3d.biscuitmesh.ui.theme.appCardShape

/** A canScan target seen in the live phone scan within the last 30 s → its RSSI, else null. */
private fun inRangeRssi(t: Target, scan: List<PhoneScanRow>): Int? {
    if (t.kind == TargetKind.LOCATION) return null
    val now = System.currentTimeMillis()
    return scan.firstOrNull {
        (it.id.equals(t.value, true) || it.label.equals(t.value, true)) && now - it.lastSeenMs < 30_000L
    }?.rssi
}

/** Metres from the current GPS fix to a LOCATION target, or null. */
private fun distanceM(t: Target, fix: Pair<Double, Double>?): Float? {
    val ll = t.latLon ?: return null
    val f = fix ?: return null
    val out = FloatArray(1)
    android.location.Location.distanceBetween(f.first, f.second, ll.first, ll.second, out)
    return out[0]
}

private fun fmtDist(m: Float): String = if (m < 1000f) "${m.toInt()} m" else "%.1f km".format(m / 1000f)

private fun kindIcon(kind: TargetKind): ImageVector = when (kind) {
    TargetKind.NETWORK -> Icons.Default.Wifi
    TargetKind.DEVICE -> Icons.Default.Bluetooth
    TargetKind.LOCATION -> Icons.Default.Place
}

private fun kindLabel(kind: TargetKind): String = when (kind) {
    TargetKind.NETWORK -> "NETWORKS"
    TargetKind.DEVICE -> "DEVICES"
    TargetKind.LOCATION -> "LOCATIONS"
}

private fun kindTint(kind: TargetKind): Color = when (kind) {
    TargetKind.NETWORK -> Color(0xFF22D3EE)
    TargetKind.DEVICE -> Color(0xFFFF5FA2)
    TargetKind.LOCATION -> Color(0xFF34D399)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val targets by vm.targets.collectAsStateWithLifecycle(initialValue = emptyList())
    val scan by vm.phoneScanResults.collectAsStateWithLifecycle(initialValue = emptyList())
    val fix by vm.currentFix.collectAsStateWithLifecycle(initialValue = null)
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Targets") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Targets",
                        "A local list of networks, devices and locations you want to locate or scan for. " +
                            "Flip 'Scan' on a target to have it looked for during scans. Nothing here is ever " +
                            "uploaded — it stays on this phone only.",
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.help("Add target", "Save a network, device or location you want to locate later. Targets are local-only, never uploaded, and give you scan toggles to home in on them."), containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = "Add target")
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("privacy") {
                Card(Modifier.fillMaxWidth(), shape = appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF34D399))
                        Text("Stored only on this phone — never uploaded (no WiGLE, no cloud).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    }
                }
            }
            if (targets.isEmpty()) {
                item("empty") {
                    Text("No targets yet. Tap + to add a network, device, or location to locate or scan for.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
            val groups = targets.groupBy { it.kind }
            TargetKind.entries.forEach { kind ->
                val rows = groups[kind].orEmpty()
                if (rows.isNotEmpty()) {
                    item("h-$kind") {
                        Text("${kindLabel(kind)} · ${rows.size}", style = MaterialTheme.typography.labelMedium,
                            color = kindTint(kind), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    items(rows, key = { it.id }) { t ->
                        TargetRow(t,
                            rssi = if (t.canScan) inRangeRssi(t, scan) else null,
                            distanceM = if (t.canScan) distanceM(t, fix) else null,
                            onToggle = { vm.setTargetScan(t.id, it) },
                            onDelete = { vm.removeTarget(t.id) })
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddTargetDialog(onDismiss = { showAdd = false }, onAdd = { vm.addTarget(it); showAdd = false })
    }
}

@Composable
private fun TargetRow(t: Target, rssi: Int?, distanceM: Float?, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val inRange = rssi != null || distanceM != null
    val live = Color(0xFF34D399)
    Card(
        Modifier.fillMaxWidth()
            .then(if (inRange) Modifier.border(1.5.dp, live.copy(alpha = 0.7f), appCardShape()) else Modifier),
        shape = appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(kindIcon(t.kind), contentDescription = null, tint = kindTint(t.kind),
                modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.label.ifBlank { t.value }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(t.value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1)
            }
            when {
                rssi != null -> InRangeBadge("IN RANGE", "$rssi dBm", live)
                distanceM != null -> InRangeBadge("GPS", fmtDist(distanceM), Color(0xFF22D3EE))
                t.canScan -> Text("scanning…", style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.padding(end = 8.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Scan", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Switch(checked = t.canScan, onCheckedChange = onToggle)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun InRangeBadge(label: String, value: String, tint: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(end = 8.dp)
            .background(tint.copy(alpha = 0.14f), appCardShape())
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text("● $label", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, color = tint)
        Text(value, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
            color = tint.copy(alpha = 0.85f))
    }
}

@Composable
private fun AddTargetDialog(onDismiss: () -> Unit, onAdd: (Target) -> Unit) {
    var kind by remember { mutableStateOf(TargetKind.NETWORK) }
    var value by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var canScan by remember { mutableStateOf(true) }

    val valueHint = when (kind) {
        TargetKind.NETWORK -> "SSID or MAC"
        TargetKind.DEVICE -> "Device MAC"
        TargetKind.LOCATION -> "lat, lon"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
                onClick = { onAdd(Target(kind, value.trim(), label.trim(), canScan)) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add target") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val names = mapOf(TargetKind.NETWORK to "Network", TargetKind.DEVICE to "Device",
                        TargetKind.LOCATION to "Location")
                    TargetKind.entries.forEach { k ->
                        KindChip(names.getValue(k), kindTint(k), k == kind) { kind = k }
                    }
                }
                OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true,
                    label = { Text(valueHint) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = label, onValueChange = { label = it }, singleLine = true,
                    label = { Text("Name (optional)") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = canScan, onCheckedChange = { canScan = it })
                    Text("Scan for this target", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
    )
}

@Composable
private fun KindChip(label: String, tint: Color, on: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.clickable(onClick = onClick),
        shape = appCardShape(),
        colors = CardDefaults.cardColors(
            containerColor = if (on) tint.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = if (on) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
    }
}
