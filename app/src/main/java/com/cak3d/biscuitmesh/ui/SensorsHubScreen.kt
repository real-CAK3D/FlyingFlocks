package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Science
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class HubEntry(val title: String, val sub: String, val icon: ImageVector, val route: String, val tint: Color)

/** A small hub for the live sensing / sky tools, so they don't crowd the top bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsHubScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val entries = listOf(
        HubEntry("Crew", "Share a track code, see your crew live on the map", Icons.Default.Groups, "crew", Color(0xFF34D399)),
        HubEntry("GPS Satellites", "Which satellites your GPS is locked onto", Icons.Default.SatelliteAlt, "satellites", Color(0xFF22D3EE)),
        HubEntry("Aircraft Overhead", "Live ADS-B — planes in the air near you", Icons.Default.Flight, "aircraft", Color(0xFF38BDF8)),
        HubEntry("Locator", "Home in on a device by signal — hot/cold + buzz", Icons.Default.MyLocation, "locator", Color(0xFF34D399)),
        HubEntry("Tracker Check", "Is a BLE tracker following you? Best-effort anti-stalking", Icons.Default.Radar, "trackers", Color(0xFFFF5470)),
        HubEntry("Lab Bench", "MAC decoder, signal→distance, crack-time, quiz", Icons.Default.Science, "labbench", Color(0xFFF59E0B)),
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sensors & Sky") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries, key = { it.route }) { e ->
                Card(
                    onClick = { onOpen(e.route) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(e.icon, contentDescription = null, tint = e.tint, modifier = Modifier.size(28.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(e.sub, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }
}
