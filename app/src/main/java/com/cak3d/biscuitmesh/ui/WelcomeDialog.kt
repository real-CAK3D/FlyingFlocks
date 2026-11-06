package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Tour(val emoji: String, val title: String, val blurb: String)

private val TOUR = listOf(
    Tour("🛰️", "Wardrive", "Hit the map, tap Start, and drive — Wi-Fi, Bluetooth, sub-GHz and cameras get mapped as you go."),
    Tour("📡", "WiFi / BT / Sub-GHz", "Scan with just the phone, or connect a Biscuit board on Fleet for more range and the CC1101."),
    Tour("🖥️", "Live", "The console (⌨ up top) — a reactive mission-control of everything you're catching right now."),
    Tour("🏆", "Sensors & Sky", "The hub (🛰 up top): Hunts, Watchlist, Tire Pressure, Device Ledger, satellites, aircraft and more."),
    Tour("🎨", "Make it yours", "Settings → Appearance: three feels (Rounded / Circuit / Neon) and six accent colours."),
)

@Composable
fun WelcomeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Welcome to Flying Flocks", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("A wardriving cyber-deck for your pocket. A quick lay of the land:",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                TOUR.forEach { t ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(t.emoji, fontSize = 20.sp)
                        Column {
                            Text(t.title, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                            Text(t.blurb, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Let's fly") } },
    )
}
