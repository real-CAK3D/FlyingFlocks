package com.cak3d.biscuitmesh.ui

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.data.Fingerprint
import kotlin.math.cos

private val FP_ACCENT = Color(0xFF34D399)

private fun distM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val dLat = (bLat - aLat) * 110_540.0
    val dLon = (bLon - aLon) * 111_320.0 * cos(Math.toRadians((aLat + bLat) / 2))
    return Math.hypot(dLat, dLon)
}

/**
 * Place Fingerprint — snapshot every Wi-Fi/BT device heard where you're standing, then compare the
 * live air to that snapshot on return to see what's new, what's gone and what's still here. A
 * change-detector for a location: handy for spotting a device that wasn't here before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FingerprintScreen(vm: MainViewModel, onBack: () -> Unit) {
    val fingerprints by vm.fingerprints.collectAsStateWithLifecycle(initialValue = emptyList())
    val scan by vm.phoneScanResults.collectAsStateWithLifecycle()
    val fix by vm.currentFix.collectAsStateWithLifecycle()

    // Keep the phone's radios scanning while this screen is open.
    DisposableEffect(Unit) {
        vm.startPhoneScan(wifi = true, ble = true)
        onDispose { vm.stopPhoneScan() }
    }

    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var compareTo by remember { mutableStateOf<Fingerprint?>(null) }

    val liveDevices = remember(scan) { scan.associate { it.id to it.label } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Place Fingerprint") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton("Place Fingerprint",
                        "Snapshot the radios heard where you're standing. Later, come back and tap Compare to see " +
                            "what's new, what's gone and what's unchanged versus the snapshot — a change-detector for " +
                            "a place. Everything stays on your phone.")
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
                Card(Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Live air", color = FP_ACCENT, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${liveDevices.size} device${if (liveDevices.size == 1) "" else "s"} heard right now",
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                        Button(onClick = { name = ""; naming = true },
                            enabled = liveDevices.isNotEmpty() && fix != null) {
                            Text("Save fingerprint here")
                        }
                        if (fix == null) Text("Waiting for a location fix…", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }

            if (fingerprints.isEmpty()) {
                item {
                    Text("No fingerprints yet. Stand somewhere, let the scan settle, and save one.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp))
                }
            } else {
                item {
                    Text("SAVED", fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                }
                items(fingerprints, key = { it.id }) { fp ->
                    val d = fix?.let { distM(it.first, it.second, fp.lat, fp.lon) }
                    FingerprintCard(fp, d, onCompare = { compareTo = fp }, onDelete = { vm.removeFingerprint(fp.id) })
                }
            }
        }
    }

    if (naming) {
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Name this place") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    singleLine = true, label = { Text("e.g. Driveway, Office lot") })
            },
            confirmButton = {
                Button(enabled = name.isNotBlank(), onClick = {
                    fix?.let { vm.saveFingerprint(name.trim(), it.first, it.second, liveDevices) }
                    naming = false
                }) { Text("Save ${liveDevices.size}") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }

    compareTo?.let { fp ->
        val gone = fp.devices.keys - liveDevices.keys
        val added = liveDevices.keys - fp.devices.keys
        val stay = fp.devices.keys.intersect(liveDevices.keys)
        AlertDialog(
            onDismissRequest = { compareTo = null },
            title = { Text(fp.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Live air vs this fingerprint:", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    DiffLine("New here", added.size, Color(0xFFFF5470),
                        added.take(4).map { liveDevices[it].orEmpty().ifBlank { it } })
                    DiffLine("Gone", gone.size, Color(0xFFF59E0B),
                        gone.take(4).map { fp.devices[it].orEmpty().ifBlank { it } })
                    DiffLine("Still here", stay.size, FP_ACCENT, emptyList())
                }
            },
            confirmButton = { TextButton(onClick = { compareTo = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun DiffLine(label: String, count: Int, tint: Color, examples: List<String>) {
    Column {
        Text("$label · $count", color = tint, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            fontFamily = FontFamily.Monospace)
        if (examples.isNotEmpty()) Text(examples.joinToString(", "), fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun FingerprintCard(fp: Fingerprint, distMeters: Double?, onCompare: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fp.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = FP_ACCENT)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
            Text("${fp.devices.size} devices · saved ${java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US).format(java.util.Date(fp.savedMs))}" +
                (distMeters?.let { " · ${if (it < 1000) "${it.toInt()} m" else "%.1f km".format(it / 1000)} away" } ?: ""),
                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onCompare) { Text("Compare to live") }
                if (distMeters != null && distMeters > 300)
                    OutlinedButton(onClick = {}, enabled = false) { Text("far from here") }
            }
        }
    }
}
