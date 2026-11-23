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
import androidx.compose.runtime.LaunchedEffect
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.LaunchItem
import com.cak3d.biscuitmesh.geo.SpaceEventsClient
import kotlinx.coroutines.delay

private fun kpColor(kp: Double): Color = when {
    kp < 4 -> Color(0xFF5FD36A)
    kp < 5 -> Color(0xFFFFD166)
    kp < 6 -> Color(0xFFFF9F45)
    else -> Color(0xFFFF5470)
}

private fun kpLabel(kp: Double): String = when {
    kp < 4 -> "Quiet"
    kp < 5 -> "Unsettled"
    kp < 6 -> "G1 minor storm"
    kp < 7 -> "G2 moderate storm"
    kp < 8 -> "G3 strong storm"
    else -> "G4+ severe storm"
}

private fun countdown(netMs: Long, nowMs: Long): String {
    val d = netMs - nowMs
    if (d <= 0) return "now / in flight"
    val mins = d / 60000
    val days = mins / 1440; val hrs = (mins % 1440) / 60; val m = mins % 60
    return when {
        days > 0 -> "in ${days}d ${hrs}h"
        hrs > 0 -> "in ${hrs}h ${m}m"
        else -> "in ${m}m"
    }
}

/**
 * Space Events — native space intel in the Orbital Radar mould: the current Kp geomagnetic index
 * and a countdown list of upcoming rocket launches (Launch Library 2). All keyless, cached for the
 * session. Pairs with the Orbit Globe and Sky Conditions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceEventsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val client = remember { SpaceEventsClient() }
    var launches by remember { mutableStateOf<List<LaunchItem>?>(null) }
    var kp by remember { mutableStateOf<Double?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        kp = client.kp()
        launches = client.launches()
    }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(30_000) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Space Events") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Space Events",
                        "Live space intel: the current Kp geomagnetic index (aurora / storm level) " +
                            "and a countdown list of upcoming rocket launches worldwide, from Launch " +
                            "Library 2. Keyless and cached for the session.",
                    )
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val k = kp
                        Text(k?.let { "%.1f".format(it) } ?: "—", fontSize = 34.sp, fontWeight = FontWeight.Bold,
                            color = k?.let(::kpColor) ?: MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace)
                        Column {
                            Text("Kp INDEX NOW", fontSize = 11.sp, letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                            Text(k?.let(::kpLabel) ?: "reading…", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = k?.let(::kpColor) ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }
            item {
                Text("UPCOMING LAUNCHES", fontSize = 12.sp, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            }
            val list = launches
            if (list == null) {
                item {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            } else if (list.isEmpty()) {
                item {
                    Text("No launches available right now (or rate-limited — try later).",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp))
                }
            } else {
                items(list) { l -> LaunchCard(l, now) }
            }
        }
    }
}

@Composable
private fun LaunchCard(l: LaunchItem, now: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("🚀 LAUNCH" + if (l.status.isNotBlank()) " · ${l.status}" else "", fontSize = 10.sp,
                    letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFFFD166))
                Text(countdown(l.netMs, now), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text(l.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(listOfNotNull(l.provider.ifBlank { null }, l.pad.ifBlank { null }).joinToString(" · "),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}
