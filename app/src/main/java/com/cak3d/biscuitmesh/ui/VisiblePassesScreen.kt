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
import com.cak3d.biscuitmesh.geo.TleClient
import com.cak3d.biscuitmesh.geo.VisiblePass
import com.cak3d.biscuitmesh.geo.visiblePasses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private fun compassDir(azDeg: Double): String {
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[(((azDeg % 360 + 360) % 360) / 45.0).toInt() % 8]
}

private fun span(ms: Long): String {
    if (ms <= 0) return "now"
    val m = ms / 60000; val d = m / 1440; val h = (m % 1440) / 60; val mm = m % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${mm}m"
        else -> "${mm}m"
    }
}

private fun clock(ms: Long): String =
    java.text.SimpleDateFormat("EEE h:mm a", java.util.Locale.US).format(java.util.Date(ms))

/**
 * Visible Passes — the satellites you'll actually *see* overhead soon: bright birds caught in
 * sunlight against a dark sky. Built from live TLEs and the validated SGP4 + Sun-shadow math, with
 * a live countdown to each rise and the direction to look. The real "Orbital Radar" move.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisiblePassesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val tleClient = remember { TleClient() }
    var passes by remember { mutableStateOf<List<VisiblePass>?>(null) }
    var noFix by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        val fix = runCatching { vm.locationUpdates().first() }.getOrNull()
        if (fix == null) { noFix = true; passes = emptyList(); return@LaunchedEffect }
        val tles = (tleClient.group(TleClient.STATIONS) + tleClient.group(TleClient.VISUAL))
            .distinctBy { it.noradId }.take(40)
        passes = withContext(Dispatchers.Default) {
            visiblePasses(tles, fix.latitude, fix.longitude, System.currentTimeMillis())
        }
    }
    LaunchedEffect(Unit) { while (true) { nowMs = System.currentTimeMillis(); delay(30_000) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Visible Passes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Visible Passes",
                        "The satellites you'll actually see with your eyes in the next day and a half — bright " +
                            "ones high enough overhead, while your sky is dark and the satellite is still lit by " +
                            "the Sun. Each shows a countdown to when it rises and which way to look. Computed on " +
                            "the phone from live TLEs.",
                    )
                },
            )
        },
    ) { pad ->
        val list = passes
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                list == null -> item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text("Computing visible passes…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 13.sp)
                    }
                }
                noFix -> item {
                    Text("Need a location fix to work out what's overhead. Enable location and reopen.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp))
                }
                list.isEmpty() -> item {
                    Text("No visible passes in the next 36 hours — either the sky stays too bright, or nothing " +
                        "bright passes high enough while sunlit. Check back tomorrow.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp))
                }
                else -> {
                    item {
                        Text("${list.size} visible pass${if (list.size == 1) "" else "es"} · next 36 h",
                            color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                    }
                    items(list) { p -> PassCard(p, nowMs) }
                }
            }
        }
    }
}

@Composable
private fun PassCard(p: VisiblePass, nowMs: Long) {
    val accent = Color(0xFFFFD166)
    val until = p.aosMs - nowMs
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = accent)
                Text(if (until <= 0) "● OVERHEAD" else "in ${span(until)}", fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (until <= 0) Color(0xFF5FD36A) else MaterialTheme.colorScheme.primary)
            }
            Text("rises ${compassDir(p.aosAzDeg)} · peak ${p.peakElDeg.toInt()}° · sets ${compassDir(p.losAzDeg)} · visible ${span(p.losMs - p.aosMs)}",
                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            Text("${clock(p.aosMs)} → ${clock(p.losMs)}", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}
