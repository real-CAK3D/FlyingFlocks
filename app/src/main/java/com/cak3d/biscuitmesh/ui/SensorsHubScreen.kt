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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SportsEsports
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class HubEntry(val title: String, val sub: String, val icon: ImageVector, val route: String, val tint: Color)
private data class HubSection(val title: String, val entries: List<HubEntry>)

/** A hub for the live sensing / sky tools, grouped so the long list doesn't crowd the screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsHubScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val sections = listOf(
        HubSection("Sense & Navigate", listOf(
            HubEntry("Live Scope", "A live waveform of the RF around you — highs, lows & bands", Icons.Default.Insights, "scope", Color(0xFF5FD36A)),
            HubEntry("Navigate", "Compass, Radar & Locator — flip between them in one view", Icons.Default.Explore, "radar", Color(0xFF22D3EE)),
            HubEntry("Background Collect", "Phone-only — keep catching Wi-Fi & BT with the screen off", Icons.Default.Sensors, "collect", Color(0xFF5FD36A)),
            HubEntry("Tracker Check", "Is a BLE tracker following you? Best-effort anti-stalking", Icons.Default.PersonSearch, "trackers", Color(0xFFFF5470)),
            HubEntry("Watchlist", "Flag MACs or names and spot them in range", Icons.Default.Visibility, "watchlist", Color(0xFF34D399)),
        )),
        HubSection("Sky", listOf(
            HubEntry("Orbit", "Satellites, aircraft & space weather — flip between the three in one view", Icons.Default.Public, "orbit", Color(0xFF38BDF8)),
        )),
        HubSection("Comms", listOf(
            HubEntry("Caw", "Chat over your Meshtastic mesh — off-grid, no internet", Icons.Default.Forum, "meshchat", Color(0xFF00E5A0)),
            HubEntry("Squawk", "Open chat with anyone running the app — online", Icons.Default.Campaign, "squawk", Color(0xFF5FD36A)),
            HubEntry("Chirps", "Private 1:1 chirps — leave a feather, swap Band & Meshtastic info", Icons.Default.Lock, "whisper", Color(0xFF86EFAC)),
            HubEntry("Flock", "Ring a band, see the wings you tail live on the map", Icons.Default.Groups, "crew", Color(0xFF34D399)),
        )),
        HubSection("Play & Stats", listOf(
            HubEntry("Global Stats", "The whole flock's totals and the leaderboard", Icons.Default.Leaderboard, "globalstats", Color(0xFF22D3EE)),
            HubEntry("Hunts", "Bounty board — challenges that tick up as you drive", Icons.Default.EmojiEvents, "hunts", Color(0xFFFFD166)),
            HubEntry("Games", "Mini-games — hunt a signal, spot species, beat the clock", Icons.Default.SportsEsports, "games", Color(0xFF34D399)),
            HubEntry("Device Ledger", "Regulars vs transients across all your drives", Icons.Default.ReceiptLong, "ledger", Color(0xFF22D3EE)),
        )),
        HubSection("Tools", listOf(
            HubEntry("Tire Pressure", "Learn your car's TPMS sensors, watch live pressure", Icons.Default.DirectionsCar, "tiredash", Color(0xFF38BDF8)),
            HubEntry("Lab Bench", "MAC decoder, signal→distance, crack-time, quiz", Icons.Default.Science, "labbench", Color(0xFFF59E0B)),
        )),
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sensors & Sky") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Sensors & Sky",
                        "A hub that gathers the app's extra screens — the sky view, chat, stats, games " +
                            "and tools — into one place. Tap any card to open that feature.",
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            sections.forEach { section ->
                item(key = "sec-${section.title}") {
                    Text(
                        section.title.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                    )
                }
                items(section.entries, key = { it.route }) { e ->
                    Card(
                        onClick = { onOpen(e.route) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
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
}
