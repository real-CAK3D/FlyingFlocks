package com.cak3d.biscuitmesh.ui

import android.content.Intent
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.wardrive.DriveSession

private val TJ_ACCENT = Color(0xFF8FD694)

private fun tjDistance(meters: Double, mph: Boolean): String =
    if (mph) "%.1f mi".format(meters / 1609.344) else "%.1f km".format(meters / 1000.0)

private fun tjDuration(ms: Long): String {
    val m = ms / 60000; val h = m / 60; val mm = m % 60
    return if (h > 0) "${h}h ${mm}m" else "${mm}m"
}

private fun tjDay(ms: Long): String =
    java.text.SimpleDateFormat("EEEE, MMM d", java.util.Locale.US).format(java.util.Date(ms))

private fun tjTime(ms: Long): String =
    java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(java.util.Date(ms))

/** A plain-language recap of one finished drive — the shareable trip-journal entry. */
fun driveNarrative(d: DriveSession, mph: Boolean): String {
    val dur = tjDuration(d.durationMs)
    val dist = tjDistance(d.distanceM, mph)
    val finds = buildList {
        if (d.wifiCount > 0) add("${d.wifiCount} Wi-Fi network${if (d.wifiCount == 1) "" else "s"}")
        if (d.btCount > 0) add("${d.btCount} Bluetooth device${if (d.btCount == 1) "" else "s"}")
        if (d.cameraCount > 0) add("${d.cameraCount} camera${if (d.cameraCount == 1) "" else "s"}")
    }
    val findsText = when (finds.size) {
        0 -> "no new contacts"
        1 -> finds[0]
        else -> finds.dropLast(1).joinToString(", ") + " and " + finds.last()
    }
    val mins = (d.durationMs / 60000).coerceAtLeast(1)
    val rate = d.wifiCount.toDouble() / mins
    val pace = if (d.wifiCount >= 10) " — about %.1f networks a minute.".format(rate) else "."
    return "${tjDay(d.startMs)} · started ${tjTime(d.startMs)}. A $dur drive covering $dist, " +
        "catching $findsText$pace"
}

/**
 * Trip Journal — every finished drive turned into a plain-language recap you can read back or
 * share. The latest drive is written up automatically the moment you stop; older ones are kept as
 * one-line entries. The story of where you've been and what you caught.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripJournalScreen(vm: MainViewModel, onBack: () -> Unit) {
    val drives by vm.drives.collectAsStateWithLifecycle(initialValue = emptyList())
    val mph by vm.useMph.collectAsStateWithLifecycle()
    val towns by vm.townsVisited.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Journal") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton("Trip Journal",
                        "Each drive you finish is written up automatically into a plain-language recap — how far, " +
                            "how long, and what you caught. Tap Share to send the latest one. Towns visited is the " +
                            "running count of distinct towns you've driven through.")
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
                Text("$towns town${if (towns == 1) "" else "s"} visited · ${drives.size} drive${if (drives.size == 1) "" else "s"} logged",
                    color = TJ_ACCENT, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
            }
            if (drives.isEmpty()) {
                item {
                    Text("No drives yet. Start a wardrive, then stop it — your recap shows up here, written for you.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp))
                }
            } else {
                val latest = drives.first()
                item {
                    Card(
                        Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("LATEST DRIVE", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                letterSpacing = 1.5.sp, color = TJ_ACCENT, fontWeight = FontWeight.Bold)
                            Text(driveNarrative(latest, mph), fontSize = 15.sp, lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                FilledTonalButton(onClick = {
                                    val text = "🐦 Flocked Up trip recap\n\n" + driveNarrative(latest, mph)
                                    runCatching {
                                        context.startActivity(Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).setType("text/plain")
                                                .putExtra(Intent.EXTRA_TEXT, text), "Share recap"))
                                    }
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                    Text("Share")
                                }
                            }
                        }
                    }
                }
                if (drives.size > 1) {
                    item {
                        Text("EARLIER", fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                    }
                    items(drives.drop(1)) { d ->
                        Card(
                            Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(tjDay(d.startMs), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TJ_ACCENT)
                                Text("${tjDuration(d.durationMs)} · ${tjDistance(d.distanceM, mph)} · " +
                                    "${d.wifiCount} Wi-Fi · ${d.btCount} BT" +
                                    if (d.cameraCount > 0) " · ${d.cameraCount} cams" else "",
                                    fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                            }
                        }
                    }
                }
            }
        }
    }
}
