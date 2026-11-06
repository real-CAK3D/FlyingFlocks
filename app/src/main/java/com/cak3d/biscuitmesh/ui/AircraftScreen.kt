package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.Aircraft
import com.cak3d.biscuitmesh.geo.AdsbClient
import kotlinx.coroutines.delay

private val SKY = Color(0xFF38BDF8)

/**
 * Live aircraft overhead — real-time ADS-B from airplanes.live for the planes broadcasting
 * their position near you right now: callsign, type, altitude, speed, distance and bearing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftScreen(vm: MainViewModel, onBack: () -> Unit) {
    val client = remember { AdsbClient() }
    var lat by remember { mutableStateOf<Double?>(null) }
    var lon by remember { mutableStateOf<Double?>(null) }
    var aircraft by remember { mutableStateOf<List<Aircraft>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Keep a current fix.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { vm.locationUpdates().collect { lat = it.latitude; lon = it.longitude } }
    }
    // Poll ADS-B around the *current* fix. Keyed on the rounded position so the loop
    // re-centres as you drive instead of querying the first fix forever.
    val fixKey = (lat?.let { Math.round(it * 100) } ?: Long.MIN_VALUE) to
        (lon?.let { Math.round(it * 100) } ?: Long.MIN_VALUE)
    androidx.compose.runtime.LaunchedEffect(fixKey) {
        val la = lat; val lo = lon
        if (la == null || lo == null) return@LaunchedEffect
        while (true) {
            loading = true
            val list = runCatching { client.nearby(la, lo, radiusNm = 150) }
            loading = false
            list.onSuccess { aircraft = it; error = null }
                .onFailure { error = "Couldn't reach the ADS-B feed. Retrying…" }
            delay(15_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aircraft Overhead") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("head") {
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Flight, contentDescription = null, tint = SKY)
                        Column(Modifier.weight(1f)) {
                            Text("LIVE ADS-B", style = MaterialTheme.typography.labelMedium, color = SKY,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    lat == null -> "Waiting for a GPS fix…"
                                    aircraft.isNotEmpty() -> "${aircraft.size} aircraft in the air within ~150 nm"
                                    loading -> "Scanning the sky…"
                                    error != null -> error!!
                                    else -> "No aircraft overhead right now."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            // hex can be blank/shared for TIS-B/MLAT targets, so index guarantees a unique key.
            items(aircraft.size, key = { "${aircraft[it].hex}-$it" }) { AircraftRow(aircraft[it]) }
        }
    }
}

@Composable
private fun AircraftRow(a: Aircraft) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // plane icon rotated to its heading
            Icon(Icons.Default.Flight, contentDescription = null, tint = SKY,
                modifier = Modifier.graphicsLayer { rotationZ = a.trackDeg.toFloat() })
            Column(Modifier.weight(1f)) {
                Text(
                    a.flight.ifBlank { a.hex.uppercase() } + (if (a.type.isNotBlank()) "  ·  ${a.type}" else ""),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${a.altFt} ft  ·  ${a.gsKt} kt  ·  hdg ${a.trackDeg}°",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), fontFamily = FontFamily.Monospace,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${"%.1f".format(a.distanceKm)} km", style = MaterialTheme.typography.titleMedium,
                    color = SKY, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(compass(a.bearingDeg), style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

private fun compass(bearingDeg: Float): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val idx = (((bearingDeg + 22.5f) % 360f + 360f) % 360f / 45f).toInt() % 8
    return dirs[idx]
}
