package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.sensors.Follower
import com.cak3d.biscuitmesh.wardrive.SampleKind

private val SAFE = Color(0xFF34D399)
private val WARN = Color(0xFFF59E0B)
private val ALERT = Color(0xFFFF5470)

/**
 * "Is something following me?" — scans BLE while open and flags any stable-address device
 * that keeps reappearing across several places over time as you move. Best-effort anti-stalking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(vm: MainViewModel, onBack: () -> Unit) {
    val results by vm.phoneScanResults.collectAsStateWithLifecycle(initialValue = emptyList())
    val followers by vm.trackerFollowers.collectAsStateWithLifecycle()
    var lat by remember { mutableStateOf<Double?>(null) }
    var lon by remember { mutableStateOf<Double?>(null) }

    DisposableEffect(Unit) {
        vm.startPhoneScan(wifi = false, ble = true)
        onDispose { vm.stopPhoneScan() }
    }
    LaunchedEffect(Unit) {
        runCatching { vm.locationUpdates().collect { lat = it.latitude; lon = it.longitude } }
    }
    LaunchedEffect(results, lat, lon) {
        val la = lat; val lo = lon
        if (la != null && lo != null && results.isNotEmpty()) {
            val now = System.currentTimeMillis()
            // Only record devices actually seen in the last ~20s. The scan list never ages
            // rows out, so without this every BLE device you ever passed gets re-stamped at
            // each new location and is falsely flagged as "following you".
            results.asSequence()
                .filter { it.kind == SampleKind.BT && now - it.lastSeenMs < 20_000 }
                .forEach { vm.recordTrackerSighting(it.id, it.label, it.rssi, la, lo, now) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracker Check") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Tracker Check",
                        "A best-effort \"is something following me?\" check — it watches Bluetooth as you " +
                            "move and flags any device that keeps reappearing across several places over time.",
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("head") {
                Card(
                    Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Radar, contentDescription = null,
                            tint = if (followers.any { it.isKnownTracker }) ALERT else SAFE)
                        Column(Modifier.weight(1f)) {
                            Text("TRACKER CHECK", style = MaterialTheme.typography.labelMedium,
                                color = if (followers.isEmpty()) SAFE else WARN,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(
                                if (followers.isEmpty()) "Nothing is riding along yet. Keep this open and move around for a few minutes."
                                else "${followers.size} device${if (followers.size == 1) "" else "s"} have stayed with you across places + time.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (followers.isNotEmpty()) TextButton(onClick = { vm.clearTrackers() }) { Text("Reset") }
                    }
                }
            }
            items(followers, key = { it.mac }) { FollowerRow(it) }
            item("note") {
                Text(
                    "Best-effort: AirTags, Find My and phones rotate their address every ~15 min, so they look like many short-lived devices and won't show here. This flags stable-address devices (many Tiles/SmartTags, beacons) that trail you. A hit isn't proof — it's a lead to check.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun FollowerRow(f: Follower) {
    val accent = if (f.isKnownTracker) ALERT else WARN
    val mins = ((f.lastMs - f.firstMs) / 60000).coerceAtLeast(1)
    Card(
        Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.name.ifBlank { "Unknown device" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(f.mac, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Text(if (f.isKnownTracker) "TRACKER" else "FOLLOWING",
                    style = MaterialTheme.typography.labelSmall, color = accent,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Text("seen at ${f.places} places · over ${mins} min · up to ${f.spanM} m apart · ${f.rssi} dBm",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace)
        }
    }
}
