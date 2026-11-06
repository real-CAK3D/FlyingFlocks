package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.sensors.OuiDb
import com.cak3d.biscuitmesh.ui.theme.appCardShape

private class LedgerEntry(val id: String, var label: String, val type: String, val channel: Int) {
    val driveIds = HashSet<String>()
    var lastMs = 0L
    var best = -200
}

private class LedgerStats(
    val total: Int, val regulars: List<LedgerEntry>, val transients: Int,
    val vendors: Int, val fiveGhz: Int, val bt: Int, val rf: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(vm: MainViewModel, onBack: () -> Unit) {
    var stats by remember { mutableStateOf<LedgerStats?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val byDrive = vm.captureSamplesByDrive()
        val ledger = HashMap<String, LedgerEntry>()
        byDrive.forEach { (driveId, pts) ->
            pts.forEach { p ->
                if (p.id.isBlank()) return@forEach
                val e = ledger.getOrPut(p.id) { LedgerEntry(p.id, p.label, p.type, p.channel) }
                e.driveIds.add(driveId)
                if (p.timeMs > e.lastMs) e.lastMs = p.timeMs
                if (p.rssi > e.best) e.best = p.rssi
                if (e.label.isBlank() && p.label.isNotBlank()) e.label = p.label
            }
        }
        val all = ledger.values
        val regulars = all.filter { it.driveIds.size >= 2 }.sortedByDescending { it.driveIds.size }
        stats = LedgerStats(
            total = all.size,
            regulars = regulars,
            transients = all.count { it.driveIds.size == 1 },
            vendors = all.mapNotNull { OuiDb.vendor(it.id) }.distinct().size,
            fiveGhz = all.count { it.channel >= 32 },
            bt = all.count { it.type.equals("BT", true) },
            rf = all.count { it.type.equals("RF", true) },
        )
        loading = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Device Ledger") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
            val s = stats
            if (loading) {
                item("load") { Text("Reading your saved drives…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
            } else if (s == null || s.total == 0) {
                item("empty") {
                    Text("No saved captures yet. Finish a wardrive (Stop on the map) and your devices build up here across drives.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            } else {
                item("stats") {
                    LCard {
                        Text("ACROSS ${(s.total)} DEVICES", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Stat("${s.regulars.size}", "REGULARS", Color(0xFF34D399), Modifier.weight(1f))
                            Stat("${s.transients}", "TRANSIENTS", Color(0xFF8A97AD), Modifier.weight(1f))
                            Stat("${s.vendors}", "VENDORS", Color(0xFF22D3EE), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Stat("${s.fiveGhz}", "5 GHz", Color(0xFFA78BFA), Modifier.weight(1f))
                            Stat("${s.bt}", "BT", Color(0xFF7C9CFF), Modifier.weight(1f))
                            Stat("${s.rf}", "SUB-GHZ", Color(0xFFF5A623), Modifier.weight(1f))
                        }
                        Text("Regulars show up on more than one drive — your neighbours and routine. Transients you saw once, passing through.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                if (s.regulars.isNotEmpty()) {
                    item("rlabel") {
                        Text("REGULARS · ${s.regulars.size}", style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF34D399), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    items(s.regulars.take(100), key = { it.id }) { RegularRow(it) }
                }
            }
        }
    }
}

@Composable
private fun LCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun Stat(value: String, label: String, accent: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun RegularRow(e: LedgerEntry) {
    Card(Modifier.fillMaxWidth(), shape = appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.label.ifBlank { e.id }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(OuiDb.vendor(e.id)?.let { "$it · ${e.id}" } ?: e.id, style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Text("${e.driveIds.size} drives", style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF34D399), fontFamily = FontFamily.Monospace)
        }
    }
}
