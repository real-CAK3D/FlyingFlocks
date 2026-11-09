package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.AuroraClient
import com.cak3d.biscuitmesh.geo.AuroraNow
import kotlinx.coroutines.delay

private val AURORA = Color(0xFF34D399)
private val AURORA_HI = Color(0xFFA855F7)

/**
 * Aurora / space weather — live geomagnetic activity (planetary Kp 0–9) from NOAA SWPC,
 * with a rough "could I see it tonight?" verdict based on the phone's latitude.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuroraScreen(vm: MainViewModel, onBack: () -> Unit) {
    val client = remember { AuroraClient() }
    var now by remember { mutableStateOf<AuroraNow?>(null) }
    var lat by remember { mutableStateOf<Double?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { vm.locationUpdates().collect { lat = it.latitude } }
    }
    LaunchedEffect(Unit) {
        while (true) {
            loading = true
            val r = runCatching { client.current() }
            loading = false
            r.onSuccess { if (it != null) { now = it; error = null } else error = "No space-weather data right now." }
                .onFailure { error = "Couldn't reach NOAA's space-weather feed. Retrying…" }
            delay(300_000) // Kp updates roughly every 3 hours; poll gently.
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Aurora") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Aurora / space weather",
                        "Live space weather from NOAA. The planetary Kp index (0–9) measures geomagnetic activity; " +
                            "higher means auroras are visible further from the poles. The verdict estimates your chance " +
                            "of seeing the lights tonight from your latitude — look toward the nearest pole.",
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("head") {
                Card(
                    Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Nightlight, contentDescription = null, tint = AURORA, modifier = Modifier.size(30.dp))
                            Column(Modifier.weight(1f)) {
                                Text("SPACE WEATHER", style = MaterialTheme.typography.labelMedium, color = AURORA,
                                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text(
                                    when {
                                        now != null -> "Geomagnetic activity · ${now!!.stormLabel}"
                                        loading -> "Reading the magnetosphere…"
                                        error != null -> error!!
                                        else -> "No data yet."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        now?.let { KpGauge(it.kp) }
                    }
                }
            }

            now?.let { n ->
                item("trend") {
                    Card(
                        Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("RECENT KP", style = MaterialTheme.typography.labelMedium, color = AURORA,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            KpTrend(n.recent)
                            Text("updated ${n.timeTag} UTC", style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
                item("verdict") {
                    Card(
                        Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("AURORA TONIGHT?", style = MaterialTheme.typography.labelMedium, color = AURORA_HI,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(AuroraClient.chanceAt(lat, n.kp), style = MaterialTheme.typography.bodyMedium)
                            Text("Rough guide: Kp is a global index, so local visibility also depends on your " +
                                "magnetic latitude, darkness and cloud. Look toward the nearest pole.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            item("src") {
                Text("Source: NOAA Space Weather Prediction Center — planetary K-index.",
                    style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun KpGauge(kp: Double) {
    val frac = (kp / 9.0).coerceIn(0.0, 1.0).toFloat()
    val color = kpColor(kp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Kp", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text("%.1f".format(kp), style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold, color = color)
            Text("/ 9", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
        Box(
            Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
        ) {
            Box(Modifier.fillMaxWidth(frac).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        }
    }
}

@Composable
private fun KpTrend(recent: List<Double>) {
    Row(
        Modifier.fillMaxWidth().height(72.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        recent.forEach { kp ->
            val frac = (kp / 9.0).coerceIn(0.08, 1.0).toFloat()
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom) {
                Box(
                    Modifier.fillMaxWidth().height((frac * 56).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(kpColor(kp)),
                )
                Text("%.0f".format(kp), style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

private fun kpColor(kp: Double): Color = when {
    kp >= 7.0 -> Color(0xFFF87171) // storm
    kp >= 5.0 -> AURORA_HI          // G1+
    kp >= 4.0 -> Color(0xFFFBBF24)  // active
    else -> AURORA                  // quiet/unsettled
}
