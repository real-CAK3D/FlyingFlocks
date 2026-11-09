package com.cak3d.biscuitmesh.ui

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
import androidx.compose.material3.AlertDialog
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
import com.cak3d.biscuitmesh.wardrive.SampleKind
import com.cak3d.biscuitmesh.wardrive.WardriveSample
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Drill-down from the Field Guide: the actual Wi-Fi networks or Bluetooth devices
 * currently geo-tagged this run, with name, address and signal. (The lifetime count
 * on the Field Guide is persisted; this live list is what's loaded in memory now.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindsScreen(vm: MainViewModel, kind: String, onBack: () -> Unit) {
    val wifi24 = kind.equals("WIFI_24", ignoreCase = true)
    val wifi5 = kind.equals("WIFI_5", ignoreCase = true)
    val wifi = wifi24 || wifi5
    val title = when {
        wifi24 -> "Wi-Fi 2.4 GHz"
        wifi5 -> "Wi-Fi 5 GHz"
        else -> "Bluetooth devices"
    }
    val samplesFlow = vm.wardriveSamples ?: MutableStateFlow(emptyMap())
    val samples by samplesFlow.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<WardriveSample?>(null) }

    val items = samples.values
        .filter {
            val isWifiKind = it.kind == SampleKind.AP || it.kind == SampleKind.STATION
            when {
                wifi24 -> isWifiKind && !it.is5GHz
                wifi5 -> isWifiKind && it.is5GHz
                else -> it.kind == SampleKind.BT
            }
        }
        .sortedByDescending { it.rssi }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        title,
                        "A live list of the devices of this type that have been picked up on this " +
                            "wardrive run, strongest signal first, with their name and address.",
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item("head") {
                Text("${items.size} loaded this session", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
            }
            if (items.isEmpty()) {
                item("empty") {
                    Text("Nothing loaded yet. Start a wardrive (or a phone scan) and they'll appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            } else {
                items(items, key = { it.id }) { s -> FindRow(s, wifi, onClick = { selected = s }) }
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
                title = { Text(s.label.ifBlank { "(unnamed)" }) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Address: ${s.id}")
                        Text("Band: $band")
                        if (s.channel > 0) Text("Channel: ${s.channel}")
                        Text("Signal: ${s.rssi} dBm")
                        if (s.accuracyM > 0f) Text("Fix accuracy: ${s.accuracyM.toInt()} m")
                        if (s.sources.isNotEmpty()) Text("Seen by: ${s.sources.joinToString()}")
                    }
                },
            )
        }
    }
}

@Composable
private fun FindRow(s: WardriveSample, wifi: Boolean, onClick: () -> Unit) {
    val name = s.label.ifBlank { if (wifi) "(hidden network)" else "(unnamed device)" }
    Card(modifier = Modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.padding(end = 8.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(s.id, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Text("${s.rssi} dBm", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}
