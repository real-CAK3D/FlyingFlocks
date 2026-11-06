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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.wardrive.DriveSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrivesScreen(vm: MainViewModel, onBack: () -> Unit, onOpenReplay: (String) -> Unit = {}) {
    val drives by vm.drives.collectAsStateWithLifecycle(initialValue = emptyList())
    val useMph by vm.useMph.collectAsStateWithLifecycle()
    val anyCapture = drives.any { it.hasCapture }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("DRIVES") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (drives.isEmpty()) {
                item("empty") {
                    Text(
                        "No saved drives yet. Start a wardrive from the map, then tap Stop — each " +
                            "run is saved here with its stats, and you can re-upload its capture to WDG.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
            if (drives.isNotEmpty()) item("reach") { RangeReachCard(drives, useMph) }
            if (anyCapture) item("coverage") {
                OutlinedButton(onClick = { onOpenReplay("all") }, modifier = Modifier.fillMaxWidth()) {
                    Text("View all-drive coverage heatmap")
                }
            }
            items(drives, key = { it.id }) { d ->
                DriveCard(
                    d, useMph,
                    onOpen = { if (d.hasCapture) onOpenReplay(d.id) },
                    onReupload = { vm.reuploadDrive(d.id) },
                    onDelete = { vm.deleteDrive(d.id) },
                )
            }
        }
    }
}

@Composable
private fun DriveCard(drive: DriveSession, useMph: Boolean, onOpen: () -> Unit, onReupload: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = if (drive.hasCapture) Modifier.clickable { onOpen() } else Modifier,
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stamp(drive.startMs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(duration(drive.durationMs), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary)
            }
            Text(
                "${drive.wifiCount} Wi-Fi · ${drive.btCount} BT · ${drive.cameraCount} cams · ${distance(drive.distanceM, useMph)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (drive.hasCapture) {
                    OutlinedButton(onClick = onReupload) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Text(if (drive.uploaded) "  Re-upload" else "  Upload to WDG")
                    }
                }
                if (drive.uploaded) {
                    Text("✓ uploaded", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f)) {}
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Range & Reach summary over all drives — ground surveyed, active days, best single day. */
@Composable
private fun RangeReachCard(drives: List<DriveSession>, useMph: Boolean) {
    val accent = Color(0xFF22D3EE)
    val totalM = drives.sumOf { it.distanceM }
    val byDay = drives.groupBy { dayKey(it.startMs) }
    val bestM = byDay.values.maxOfOrNull { day -> day.sumOf { it.distanceM } } ?: 0.0
    Card(
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("RANGE & REACH", style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReachStat(distance(totalM, useMph), "SURVEYED", accent, Modifier.weight(1f))
                ReachStat("${byDay.size}", "ACTIVE DAYS", accent, Modifier.weight(1f))
                ReachStat(distance(bestM, useMph), "BEST DAY", accent, Modifier.weight(1f))
            }
            Text("${drives.size} drive${if (drives.size == 1) "" else "s"} logged.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun ReachStat(value: String, label: String, accent: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

private fun dayKey(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

private fun stamp(ms: Long): String =
    SimpleDateFormat("EEE MMM d, h:mm a", Locale.US).format(Date(ms))

private fun duration(ms: Long): String {
    val mins = ms / 60000
    return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
}

private fun distance(m: Double, useMph: Boolean): String =
    if (useMph) "%.1f mi".format(m / 1609.34) else "%.1f km".format(m / 1000)
