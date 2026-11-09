package com.cak3d.biscuitmesh.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.Hazard
import com.cak3d.biscuitmesh.geo.HazardKind
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    val drives by vm.drivesState.collectAsStateWithLifecycle()
    val cameras by vm.hazardsState.collectAsStateWithLifecycle()
    val meshNodes by vm.meshNodes.collectAsStateWithLifecycle()
    val mph by vm.useMph.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val totalMeters = drives.sumOf { it.distanceM }
    val totalMs = drives.sumOf { it.durationMs }
    // Wi-Fi is split across the 2.4 GHz and 5 GHz tracks; sum both (the old code looked for a
    // "Wi-Fi" short code that doesn't exist, so this always read 0).
    val wifi = progress.radios
        .filter { it.track == com.cak3d.biscuitmesh.progress.ProgressTrack.WIFI_24 || it.track == com.cak3d.biscuitmesh.progress.ProgressTrack.WIFI_5 }
        .sumOf { it.count }
    val bt = progress.radios.firstOrNull { it.track == com.cak3d.biscuitmesh.progress.ProgressTrack.BLUETOOTH }?.count ?: 0

    // Build the content AND write it off the UI thread — a full backup over many drives is a
    // lot of JSON and a disk write, which would jank/ANR the UI if done on the main thread.
    fun writeTo(uri: android.net.Uri?, text: () -> String, label: String) {
        if (uri == null) return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ok = runCatching {
                val content = text()
                context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            }.isSuccess
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, if (ok) "Exported $label" else "Export failed",
                    android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        writeTo(it, { vm.camerasCsv() }, "cameras CSV")
    }
    val kmlPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml")
    ) { writeTo(it, { vm.camerasKml() }, "cameras KML") }
    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        writeTo(it, { vm.backupJson() }, "full backup")
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Stats & Data") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Stats & Data",
                        "Your running totals, plus a place to export your finds — cameras as CSV or KML, " +
                            "or a full backup as JSON — to a file you choose.",
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("rank") {
                StatCard("RANK") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${progress.rank}", style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(progress.rankName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    StatRow("Total finds", "${progress.totalFinds}")
                    StatRow("Camera boost XP", "${progress.cameraBoostXp}")
                    if (progress.bonusLevels > 0) StatRow("Rare-bird levels", "${progress.bonusLevels}")
                }
            }
            item("driving") {
                StatCard("DRIVING") {
                    StatRow("Distance driven", fmtDistance(totalMeters, mph))
                    StatRow("Time driven", fmtDuration(totalMs))
                    StatRow("Drives logged", "${drives.size}")
                }
            }
            item("finds") {
                StatCard("FINDS") {
                    StatRow("Wi-Fi networks", "$wifi")
                    StatRow("Bluetooth devices", "$bt")
                    progress.cameras.forEach { c -> StatRow(c.track.displayName, "${c.count}") }
                }
            }
            item("export") {
                StatCard("EXPORT") {
                    Text("Save your data to a file.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { csvPicker.launch("flyingflocks-cameras.csv") }, modifier = Modifier.weight(1f)) { Text("Cameras CSV") }
                        OutlinedButton(onClick = { kmlPicker.launch("flyingflocks-cameras.kml") }, modifier = Modifier.weight(1f)) { Text("Cameras KML") }
                    }
                    OutlinedButton(onClick = { jsonPicker.launch("flyingflocks-backup.json") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Full backup (JSON)")
                    }
                }
            }
            if (meshNodes.isNotEmpty()) {
                item("meshhead") {
                    Text("MESH NODES (${meshNodes.size})", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp))
                }
                items(meshNodes, key = { it.num }) { n ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(n.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(n.battery?.let { "$it% battery" } ?: "mesh node",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Text(
                                if (n.lat != null && n.lon != null) "${"%.4f".format(n.lat)}, ${"%.4f".format(n.lon)}" else "no GPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
            item("camhead") {
                Text("CAMERAS (${cameras.size})", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp))
            }
            if (cameras.isEmpty()) {
                item("camempty") {
                    Text("No cameras logged yet. Drive, sweep, or plug in the dongle to fill this in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            } else {
                items(cameras, key = { it.id }) { h -> CameraRow(h) }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CameraRow(h: Hazard) {
    val label = when (h.kind) {
        HazardKind.FLOCK -> "Flock / ALPR"
        HazardKind.SPEED_CAMERA -> "Speed camera"
        HazardKind.RED_LIGHT -> "Red-light camera"
        HazardKind.CAMERA -> "Camera"
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(h.name.ifBlank { label }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Text("${"%.4f".format(h.lat)}, ${"%.4f".format(h.lon)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

private fun fmtDistance(meters: Double, mph: Boolean): String =
    if (mph) "%.1f mi".format(meters / 1609.34) else "%.1f km".format(meters / 1000.0)

private fun fmtDuration(ms: Long): String {
    val totalMin = ms / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
