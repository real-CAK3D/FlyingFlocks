package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.data.WatchlistStore
import com.cak3d.biscuitmesh.ui.theme.appCardShape
import com.cak3d.biscuitmesh.wardrive.SampleKind
import com.cak3d.biscuitmesh.wardrive.WardriveSample
import kotlinx.coroutines.flow.MutableStateFlow

private val HIT = Color(0xFF34D399)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(vm: MainViewModel, onBack: () -> Unit) {
    val entries by vm.watchlist.collectAsStateWithLifecycle(initialValue = emptyList())
    val samplesFlow = remember(vm) { vm.wardriveSamples ?: MutableStateFlow(emptyMap()) }
    val samples by samplesFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    var field by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<WardriveSample?>(null) }

    val hits = remember(entries, samples) {
        if (entries.isEmpty()) emptyList()
        else samples.values.filter { WatchlistStore.matches(entries, it.id, it.label) != null }
            .sortedByDescending { it.rssi }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Watchlist") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Watchlist",
                        "Add device names or addresses you want to keep an eye out for. When one is picked " +
                            "up on a drive it's flagged here, strongest signal first.",
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
            item("add") {
                Card(Modifier.fillMaxWidth(), shape = appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("WATCH FOR", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("Add a MAC or a name fragment. You'll see it light up below whenever it's in range during a scan.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = field, onValueChange = { field = it }, singleLine = true,
                                label = { Text("MAC or name") }, modifier = Modifier.weight(1f))
                            IconButton(onClick = { if (field.isNotBlank()) { vm.addWatch(field); field = "" } }) {
                                Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            if (hits.isNotEmpty()) {
                item("hitlabel") {
                    Text("● IN RANGE NOW · ${hits.size}", style = MaterialTheme.typography.labelMedium,
                        color = HIT, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                items(hits, key = { "h-${it.id}" }) { HitRow(it, onClick = { selected = it }) }
            }
            if (entries.isNotEmpty()) {
                item("watchlabel") {
                    Text("WATCHING · ${entries.size}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                items(entries, key = { "e-$it" }) { entry ->
                    Card(Modifier.fillMaxWidth(), shape = appCardShape(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(entry, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { vm.removeWatch(entry) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            } else {
                item("empty") {
                    Text("Nothing on the watchlist yet.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        }

        selected?.let { s ->
            val band = when {
                s.kind == SampleKind.RF -> "sub-GHz"
                s.kind == SampleKind.BT -> "Bluetooth"
                s.is5GHz -> "Wi-Fi 5 GHz"
                else -> "Wi-Fi 2.4 GHz"
            }
            AlertDialog(
                onDismissRequest = { selected = null },
                confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
                title = { Text(s.label.ifBlank { s.id }) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Address: ${s.id}")
                        Text("Band: $band")
                        if (s.channel > 0) Text("Channel: ${s.channel}")
                        Text("Signal: ${s.rssi} dBm")
                        if (s.sources.isNotEmpty()) Text("Seen by: ${s.sources.joinToString()}")
                    }
                },
            )
        }
    }
}

@Composable
private fun HitRow(s: WardriveSample, onClick: () -> Unit) {
    val band = when {
        s.kind == SampleKind.RF -> "sub-GHz"
        s.kind == SampleKind.BT -> "BT"
        s.is5GHz -> "5 GHz"
        else -> "2.4 GHz"
    }
    Card(Modifier.fillMaxWidth().clickable { onClick() }, shape = appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(s.label.ifBlank { s.id }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${s.id} · $band", style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Text("${s.rssi} dBm", style = MaterialTheme.typography.titleMedium, color = HIT, fontFamily = FontFamily.Monospace)
        }
    }
}
