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
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Streetview
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private data class HubEntry(val title: String, val sub: String, val icon: ImageVector, val route: String, val tint: Color)
private data class HubSection(val title: String, val accent: Color, val loaderIcon: ImageVector, val entries: List<HubEntry>)

/** A card tap that is showing its own loading screen before the route opens. */
private data class PendingOpen(val route: String, val label: String, val accent: Color, val icon: ImageVector, val seed: Int)

/** A hub for the live sensing / sky tools, grouped so the long list doesn't crowd the screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsHubScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val sections = listOf(
        HubSection("Sense & Navigate", Color(0xFF22D3EE), Icons.Default.Explore, listOf(
            HubEntry("Navigate", "Compass, Radar & Locator — flip between them in one view", Icons.Default.Explore, "radar", Color(0xFF22D3EE)),
            HubEntry("Background Collect", "Phone-only — keep catching Wi-Fi & BT with the screen off", Icons.Default.Sensors, "collect", Color(0xFF5FD36A)),
            HubEntry("Tracker Check", "Is a BLE tracker following you? Best-effort anti-stalking", Icons.Default.PersonSearch, "trackers", Color(0xFFFF5470)),
            HubEntry("Watchlist", "Flag MACs or names and spot them in range", Icons.Default.Visibility, "watchlist", Color(0xFF34D399)),
            HubEntry("Rogue AP", "Evil-twin sweep — flags a Wi-Fi name coming from more than one radio, worst when a secured network also shows up open", Icons.Default.Security, "rogueap", Color(0xFFFF5470)),
            HubEntry("Targets", "Networks, devices & locations to locate — scan toggles, local only", Icons.Default.GpsFixed, "targets", Color(0xFFF59E0B)),
            HubEntry("3D Terrain & AR", "Your drive on the real landscape in 3D, or through the camera at street level — toggle between the two in one window", Icons.Default.Terrain, "terrain3d", Color(0xFF8FD694)),
        )),
        HubSection("Live Visuals", Color(0xFFEC4899), Icons.Default.Equalizer, listOf(
            HubEntry("Signals", "Every live RF visualizer in one window — the 3D scope, live console, device graph, per-device telemetry, dashboard & traffic charts, switchable at the bottom", Icons.Default.Equalizer, "signals", Color(0xFF22D3EE)),
            HubEntry("Data Cube", "The whole live picture in one rotatable 3D diagram — Wi-Fi, Bluetooth & satellites as stacked layers; drag to spin, tap a node for its details", Icons.Default.ViewInAr, "datacube", Color(0xFFFFD166)),
        )),
        HubSection("Sky", Color(0xFF8B5CF6), Icons.Default.SatelliteAlt, listOf(
            HubEntry("Sky AR", "Point your phone at the sky — satellites, planets, the Moon, constellations & aircraft live on the camera", Icons.Default.CameraAlt, "skyar", Color(0xFFA78BFA)),
            HubEntry("Orbit", "Everything overhead in one window — GPS satellites, ADS-B aircraft & aurora/space weather, switchable", Icons.Default.Public, "orbit", Color(0xFF38BDF8)),
            HubEntry("Orbit Globe", "Native SGP4 globe — the ISS, Tiangong & bright satellites tracked live, color-coded by orbit class. A preview of the native rebuild.", Icons.Default.SatelliteAlt, "orbitglobe", Color(0xFFFFD166)),
            HubEntry("Sky Conditions", "Real-time light pollution — how dark your sky is right now, folding in live twilight & the Moon, with what you can see and when it next gets dark", Icons.Default.Nightlight, "skyconditions", Color(0xFF6366F1)),
            HubEntry("Light Pollution Map", "Earth at night around you — NASA VIIRS city-glow tiles show how bright your skies are; pan to find a dark spot", Icons.Default.Map, "lightmap", Color(0xFFFFD166)),
            HubEntry("Vision", "God's Eye View forks, live in-app — CAK3Dvision over Tailscale, plus any fork of your own you add", Icons.Default.RemoveRedEye, "vision", Color(0xFF8B5CF6)),
        )),
        HubSection("Alerts & feeds", Color(0xFFFF9500), Icons.Default.Warning, listOf(
            HubEntry("Alerts & Threats", "Live security feed — NWS warnings for your area, earthquakes & global disasters, tap for detail", Icons.Default.Warning, "alerts", Color(0xFFFF9500)),
            HubEntry("Space Events", "Upcoming rocket launches worldwide with live countdowns, plus the current Kp geomagnetic storm index — native, keyless", Icons.Default.RocketLaunch, "spaceevents", Color(0xFFFFD166)),
            HubEntry("Weather", "Current conditions, animated precipitation radar & the forecast into tomorrow", Icons.Default.Cloud, "weather", Color(0xFF5CB4E8)),
            HubEntry("Air Quality", "US AQI, the main pollutants near you & the UV index — keyless, your location, with a plain-language health note", Icons.Default.Air, "airquality", Color(0xFF6EE7B7)),
            HubEntry("Live Feeds", "Surveillance cams, scanner audio & traffic webcams near you — all public, consume-only", Icons.Default.Videocam, "feeds", Color(0xFFFF5470)),
        )),
        HubSection("Comms", Color(0xFF00E5A0), Icons.Default.Forum, listOf(
            HubEntry("Caw", "Chat over your Meshtastic mesh — off-grid, no internet", Icons.Default.Forum, "meshchat", Color(0xFF00E5A0)),
            HubEntry("Squawk", "Open chat with anyone running the app — online", Icons.Default.Campaign, "squawk", Color(0xFF5FD36A)),
            HubEntry("Chirps", "Private 1:1 chirps — leave a feather, swap Band & Meshtastic info", Icons.Default.Lock, "whisper", Color(0xFF86EFAC)),
            HubEntry("Flock", "Ring a band, see the wings you tail live on the map", Icons.Default.Groups, "crew", Color(0xFF34D399)),
        )),
        HubSection("Play & Stats", Color(0xFFFFD166), Icons.Default.EmojiEvents, listOf(
            HubEntry("Global Stats", "The whole flock's totals and the leaderboard", Icons.Default.Leaderboard, "globalstats", Color(0xFF22D3EE)),
            HubEntry("Hunts", "Bounty board — challenges that tick up as you drive", Icons.Default.EmojiEvents, "hunts", Color(0xFFFFD166)),
            HubEntry("Games", "Mini-games — hunt a signal, spot species, beat the clock", Icons.Default.SportsEsports, "games", Color(0xFF34D399)),
            HubEntry("Device Ledger", "Regulars vs transients across all your drives", Icons.Default.ReceiptLong, "ledger", Color(0xFF22D3EE)),
        )),
        HubSection("Tools", Color(0xFFF59E0B), Icons.Default.Science, listOf(
            HubEntry("Tire Pressure", "Learn your car's TPMS sensors, watch live pressure", Icons.Default.DirectionsCar, "tiredash", Color(0xFF38BDF8)),
            HubEntry("Universal Remote", "TV power/volume/channel over the node's IR LED — Flipper-style", Icons.Default.SettingsRemote, "remote", Color(0xFFFF5470)),
            HubEntry("Lab Bench", "MAC decoder, signal→distance, crack-time, quiz", Icons.Default.Science, "labbench", Color(0xFFF59E0B)),
        )),
    )
    var pending by remember { mutableStateOf<PendingOpen?>(null) }
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
      Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            Modifier.fillMaxSize(),
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
                        onClick = { if (pending == null) pending = PendingOpen(e.route, e.title, e.tint, e.icon, e.route.hashCode()) },
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

        // Branded, per-section loading screen shown briefly when a card is tapped.
        pending?.let { p ->
            LaunchedEffect(p.route) {
                delay(1500)
                onOpen(p.route)
                // Keep the loader up across the nav transition so the hub never flashes back
                // into view for a frame before the chosen screen draws.
                delay(450)
                pending = null
            }
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f)),
            ) {
                SectionLoader(title = p.label, accent = p.accent, icon = p.icon, seed = p.seed)
            }
        }
      }
    }
}
