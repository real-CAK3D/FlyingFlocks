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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.progress.Progress
import com.cak3d.biscuitmesh.wardrive.DriveSession
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrivesScreen(vm: MainViewModel, onBack: () -> Unit, onOpenReplay: (String) -> Unit = {}) {
    val drives by vm.drives.collectAsStateWithLifecycle(initialValue = emptyList())
    val useMph by vm.useMph.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val anyCapture = drives.any { it.hasCapture }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("HISTORY") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    HelpButton(
                        "Drives",
                        "A history of your wardrive sessions, each with its distance and how many Wi-Fi, " +
                            "Bluetooth and camera finds it logged. Tap one to replay it on the map.",
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val tabs = listOf("Drives", "Days", "Totals")
            var tab by remember { mutableIntStateOf(0) }
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i },
                        text = { Text(t, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) })
                }
            }
            when (tab) {
                0 -> LazyColumn(
                    Modifier.fillMaxSize(),
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
                            onShare = {
                                scope.launch {
                                    val pts = vm.captureSamples(d.id)
                                    ShareCard.share(context, d, useMph, pts)
                                }
                            },
                            onReupload = { vm.reuploadDrive(d.id) },
                            onDelete = { vm.deleteDrive(d.id) },
                        )
                    }
                }
                1 -> DaysTab(drives, useMph)
                else -> TotalsTab(drives, progress, useMph)
            }
        }
    }
}

/** History by day — each day you drove, with its totals. */
@Composable
private fun DaysTab(drives: List<DriveSession>, useMph: Boolean) {
    val byDay = remember(drives) {
        drives.groupBy { dayKey(it.startMs) }.toList().sortedByDescending { it.first }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (byDay.isEmpty()) item("e") {
            Text("No days logged yet.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
        items(byDay, key = { it.first }) { (day, list) ->
            Card(shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(prettyDay(day), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${list.size} drive${if (list.size == 1) "" else "s"} · ${distance(list.sumOf { it.distanceM }, useMph)} · ${duration(list.sumOf { it.durationMs })}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Text("${list.sumOf { it.wifiCount }} Wi-Fi · ${list.sumOf { it.btCount }} Bluetooth · ${list.sumOf { it.cameraCount }} cameras",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** All-time totals — your whole journey in numbers, plus the progression tracks. */
@Composable
private fun TotalsTab(drives: List<DriveSession>, progress: Progress, useMph: Boolean) {
    val days = drives.map { dayKey(it.startMs) }.distinct().size
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("tot") {
            Card(shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ALL-TIME TOTALS", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReachStat("${drives.size}", "drives", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        ReachStat(distance(drives.sumOf { it.distanceM }, useMph), "surveyed",
                            MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                        ReachStat("$days", "active days", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    }
                    Text("${drives.sumOf { it.wifiCount }} Wi-Fi · ${drives.sumOf { it.btCount }} Bluetooth · ${drives.sumOf { it.cameraCount }} cameras caught",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                }
            }
        }
        item("prog") {
            Card(shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PROGRESSION", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    progress.tracks.forEach { ts ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ts.track.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text("${ts.count}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

private fun prettyDay(dayKey: String): String = runCatching {
    SimpleDateFormat("EEEE, MMM d", Locale.US).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dayKey)!!)
}.getOrDefault(dayKey)

@Composable
private fun DriveCard(drive: DriveSession, useMph: Boolean, onOpen: () -> Unit, onShare: () -> Unit, onReupload: () -> Unit, onDelete: () -> Unit) {
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
                if (drive.hasCapture) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.primary)
                    }
                }
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
