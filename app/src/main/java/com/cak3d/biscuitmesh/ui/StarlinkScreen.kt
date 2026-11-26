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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.StarlinkTrains
import com.cak3d.biscuitmesh.geo.TleClient
import com.cak3d.biscuitmesh.geo.VisiblePass
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private val SL_ACCENT = Color(0xFF7DD3FC)

/**
 * Starlink Train — when the freshest "string of pearls" next crosses your sky. The most-asked-about
 * thing in the night sky, predicted on-device from live TLEs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarlinkScreen(vm: MainViewModel, onBack: () -> Unit) {
    val tleClient = remember { TleClient() }
    var train by remember { mutableStateOf<StarlinkTrains.Train?>(null) }
    var done by remember { mutableStateOf(false) }
    var noFix by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        val fix = runCatching { vm.locationUpdates().first() }.getOrNull()
        if (fix == null) { noFix = true; done = true; return@LaunchedEffect }
        train = StarlinkTrains.freshestTrain(tleClient, fix.latitude, fix.longitude)
        done = true
    }
    LaunchedEffect(Unit) { while (true) { nowMs = System.currentTimeMillis(); delay(30_000) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Starlink Train") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Starlink Train",
                        "Right after a Starlink launch, 20+ satellites fly low and bunched in one orbital " +
                            "plane — a bright \"string of pearls\" moving together across the sky. We find the " +
                            "freshest low-orbit cluster from live TLEs and predict when it next passes over you, " +
                            "sunlit against a dark sky. When several lines show rising within a few minutes of " +
                            "each other, that's the train. They spread out and fade over a week or two.",
                    )
                },
            )
        },
    ) { pad ->
        val t = train
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                !done -> item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = SL_ACCENT)
                        Text("Finding the freshest train…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 13.sp)
                    }
                }
                noFix -> item {
                    Text("Need a location fix to work out when the train passes over you. Enable location and reopen.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp))
                }
                t == null -> item {
                    Text("Couldn't reach the satellite catalogue right now. Check your connection and reopen.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp))
                }
                else -> {
                    item {
                        Card(
                            Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (t.fresh) "Freshest cluster" else "Lowest-orbit cluster",
                                    color = SL_ACCENT, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${t.sats} satellites · ~${t.altKm} km" +
                                    if (t.fresh) " · still raising (recent launch)" else " · operational shell",
                                    fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                                if (!t.fresh) Text("No freshly-launched low train in the catalogue right now — " +
                                    "showing the lowest cluster instead. Trains appear in the days after a launch.",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                            }
                        }
                    }
                    if (t.passes.isEmpty()) {
                        item {
                            Text("No visible train passes in the next 48 hours — the cluster either stays too low " +
                                "on the horizon or your sky isn't dark when it's overhead. Check back tomorrow.",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
                                modifier = Modifier.padding(8.dp))
                        }
                    } else {
                        item {
                            Text("${t.passes.size} visible pass${if (t.passes.size == 1) "" else "es"} · next 48 h · " +
                                "look for several together",
                                color = SL_ACCENT, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                        }
                        items(t.passes) { p -> TrainPassCard(p, nowMs) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainPassCard(p: VisiblePass, nowMs: Long) {
    val until = p.aosMs - nowMs
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SL_ACCENT)
                Text(if (until <= 0) "● OVERHEAD" else "in ${starlinkSpan(until)}", fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (until <= 0) Color(0xFF5FD36A) else MaterialTheme.colorScheme.primary)
            }
            Text("rises ${starlinkDir(p.aosAzDeg)} · peak ${p.peakElDeg.toInt()}° · sets ${starlinkDir(p.losAzDeg)} · " +
                "visible ${starlinkSpan(p.losMs - p.aosMs)}",
                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            Text("${starlinkClock(p.aosMs)} → ${starlinkClock(p.losMs)}", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

private fun starlinkDir(azDeg: Double): String {
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[(((azDeg % 360 + 360) % 360) / 45.0).toInt() % 8]
}

private fun starlinkSpan(ms: Long): String {
    if (ms <= 0) return "now"
    val m = ms / 60000; val d = m / 1440; val h = (m % 1440) / 60; val mm = m % 60
    return when { d > 0 -> "${d}d ${h}h"; h > 0 -> "${h}h ${mm}m"; else -> "${mm}m" }
}

private fun starlinkClock(ms: Long): String =
    java.text.SimpleDateFormat("EEE h:mm a", java.util.Locale.US).format(java.util.Date(ms))
