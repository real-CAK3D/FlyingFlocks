package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.sensors.OuiDb
import com.cak3d.biscuitmesh.ui.theme.appCardShape
import com.cak3d.biscuitmesh.wardrive.DeviceHistory
import com.cak3d.biscuitmesh.wardrive.SampleKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalDetailScreen(vm: MainViewModel, id: String, onBack: () -> Unit) {
    val samplesFlow = remember(vm) { vm.wardriveSamples ?: MutableStateFlow(emptyMap()) }
    val samples by samplesFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val sample = samples[id]
    var history by remember { mutableStateOf<DeviceHistory?>(vm.deviceHistory(id)) }
    // Poll the (non-flow) history so the RSSI trail grows live while this screen is open.
    LaunchedEffect(id) { while (true) { history = vm.deviceHistory(id); delay(1200) } }

    val accent = MaterialTheme.colorScheme.primary
    val band = when {
        sample?.kind == SampleKind.RF -> "Sub-GHz"
        sample?.kind == SampleKind.BT -> "Bluetooth"
        sample?.is5GHz == true -> "Wi-Fi 5 GHz"
        sample?.kind == SampleKind.AP || sample?.kind == SampleKind.STATION -> "Wi-Fi 2.4 GHz"
        else -> "—"
    }
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.US) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Signal") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DCard {
                Text(sample?.label?.ifBlank { null } ?: "(unnamed)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(id, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                OuiDb.vendor(id)?.let {
                    Text("Vendor · $it", style = MaterialTheme.typography.bodySmall, color = accent, fontFamily = FontFamily.Monospace)
                }
            }

            DCard {
                InfoRow("Band", band)
                sample?.let { if (it.channel > 0) InfoRow("Channel", "${it.channel}") }
                InfoRow("Signal (last)", history?.let { "${it.last} dBm" } ?: sample?.let { "${it.rssi} dBm" } ?: "—")
                InfoRow("Signal (best)", history?.let { "${it.best} dBm" } ?: "—")
                InfoRow("Sightings", history?.hits?.toString() ?: "—")
                history?.let {
                    InfoRow("First seen", fmt.format(Date(it.firstMs)))
                    InfoRow("Last seen", fmt.format(Date(it.lastMs)))
                }
                sample?.sources?.takeIf { it.isNotEmpty() }?.let {
                    InfoRow("Seen by", it.joinToString { s -> if (s == "phone") "phone" else s.takeLast(5) })
                }
            }

            history?.rssiTrail?.takeIf { it.size >= 2 }?.let { trail ->
                DCard {
                    Text("SIGNAL OVER TIME", style = MaterialTheme.typography.labelMedium,
                        color = accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("Newest on the right · -100 dBm (weak) to -30 dBm (strong)",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                        val lo = -100f; val hi = -30f; val span = hi - lo
                        val stepX = size.width / (trail.size - 1)
                        fun y(v: Int) = size.height - ((v.coerceIn(-100, -30) - lo) / span) * size.height
                        var prev = Offset(0f, y(trail[0]))
                        for (i in 1 until trail.size) {
                            val p = Offset(i * stepX, y(trail[i]))
                            drawLine(accent, prev, p, 3f); prev = p
                        }
                    }
                }
            }

            history?.path?.takeIf { it.isNotEmpty() }?.let { path ->
                DCard {
                    Text("WHERE IT WAS SEEN", style = MaterialTheme.typography.labelMedium,
                        color = accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("${path.size} point${if (path.size == 1) "" else "s"} tracked along your drive.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    val p = path.last()
                    Text("Last fix · %.5f, %.5f".format(p.lat, p.lon), style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }

            OutlinedButton(onClick = { vm.addWatch(id) }, modifier = Modifier.fillMaxWidth()) {
                Text("Add to watchlist")
            }
        }
    }
}

@Composable
private fun DCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}
