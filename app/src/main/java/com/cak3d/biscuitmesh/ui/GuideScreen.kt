package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class GuideEntry(val name: String, val text: String)
private data class GuideSection(val title: String, val entries: List<GuideEntry>)

private val GUIDE = listOf(
    GuideSection("Getting started", listOf(
        GuideEntry("Fleet (home)", "Your boards (ESP32 nodes) and the phone itself. Tap Connect to pair a saved board over BLE/USB; the ⋮ menu opens a board's tools (including the live mirror of a T-Embed). The ▶ Scan button starts a phone-only wardrive."),
        GuideEntry("Top bar icons", "Dashboard, Field Guide, Drives history, Sensors & Sky hub, Terminal, and Settings. The Sensors & Sky icon (satellite) opens the hub where almost every feature lives."),
        GuideEntry("Do I need a board?", "No. The phone's own Wi-Fi/BT radios drive most features. Boards add sub-GHz, extra range, and standalone scanning."),
    )),
    GuideSection("Scanning & capture", listOf(
        GuideEntry("Wardrive", "The map screen. Start it and drive — it logs every Wi-Fi/BT/camera along your route and draws your track. Use it any time you want to map an area. The map menu (☰) has layers, map styles, offline download and TomTom traffic (needs a key)."),
        GuideEntry("Wi-Fi / Bluetooth / Sub-GHz tabs", "Live lists of what the phone (or a board) hears right now, with a spectrum graph and classification. The BT tab calls out other phones nearby. Use when you want to inspect the air where you're standing."),
        GuideEntry("Background Collect", "Phone-only wardrive that keeps catching with the screen off. Use for passive, all-day collection."),
        GuideEntry("Place Fingerprint", "Snapshot every device at a spot, then Compare on return to see what's new/gone/unchanged. Use to detect changes at a location (e.g. a new device that wasn't here before)."),
    )),
    GuideSection("Navigate & security", listOf(
        GuideEntry("Navigate (Compass/Radar/Locator)", "Point yourself toward a flagged target, or watch nearby devices sweep on radar. Use to physically find a specific network/device."),
        GuideEntry("Watchlist & Targets", "Flag MACs/names you care about and get spotted when they're in range. Local only."),
        GuideEntry("Tracker Check", "Best-effort anti-stalking — flags a BLE tracker that keeps following you across locations."),
        GuideEntry("Rogue AP", "Evil-twin sweep — flags a Wi-Fi name coming from more than one radio (worst when a secured network also appears open). Use to spot a fake hotspot."),
    )),
    GuideSection("Live visuals", listOf(
        GuideEntry("Signals", "Every live RF visualizer in one window — 3D scope, console, device graph, telemetry and traffic charts."),
        GuideEntry("Data Cube", "A rotatable 3D diagram of everything you're hearing now, stacked by layer. Tap a node; the neighbour mode links it to others by Vendor, Layer, or Nearby (similar signal ≈ similar distance from you)."),
        GuideEntry("Holo-Timeline", "A hologram of your drives on the real ground. Pick a scope (Day/Week/Month/All) or a single drive up top; drag to rotate, pinch to zoom in and tap a signal for its details; ▶/■ to play or stop the day's scrub."),
    )),
    GuideSection("Sky", listOf(
        GuideEntry("Sky AR", "Point your phone up — satellites, planets, the Moon, constellations and aircraft overlaid on the camera."),
        GuideEntry("Visible Passes & Starlink Train", "The satellites you'll actually SEE soon, with a countdown and where to look. Starlink Train finds the freshest low-orbit 'string of pearls'. Tap 'Stamp this catch' when one's overhead to add a passport stamp."),
        GuideEntry("Sky Conditions / Light Map / Site Survey", "How dark your sky is right now, city-glow tiles to find a dark spot, and one 'Spot Score' rolling together light, air, weather, geomag and RF for stargazing."),
        GuideEntry("Background sky alerts", "Turn on in Settings → Alerts to get a notification when a bright pass is coming, even with the app closed."),
    )),
    GuideSection("Drives, maps & telemetry", listOf(
        GuideEntry("Coverage Heatmap", "Everywhere you've driven, heat-mapped by density (blue→red). Use the Today/Month/Year/All chips to pick a window."),
        GuideEntry("Drive Stats", "Your latest drive charted — speed profile (top & average) and the real terrain you climbed/descended."),
        GuideEntry("Trip Journal", "Each finished drive written up in plain language with a Share button, plus your running towns-visited count."),
        GuideEntry("Offline map", "On the Wardrive map menu, download the area so the map works with no signal."),
    )),
    GuideSection("Alerts, feeds & comms", listOf(
        GuideEntry("Alerts / Weather / Air Quality / Space Events", "Live NWS warnings, quakes & disasters; current conditions + radar; US AQI + UV; rocket launches & the Kp storm index. All keyless."),
        GuideEntry("Live Feeds", "Public surveillance/traffic cams and scanner audio near you (consume-only)."),
        GuideEntry("Voice & crossing alerts", "Settings → Talk-back voice speaks alerts; pick a voice, speed and pitch. Town/county/state crossings announce and award Feathers."),
        GuideEntry("Flock / Caw / Squawk / Chirps", "The online layer — chat, mesh messaging, bands to tail flockmates, and leaderboards. Needs Flock enabled."),
    )),
    GuideSection("Points & cosmetics", listOf(
        GuideEntry("Feathers", "The spendable currency, separate from rank/XP. Earn by driving & catching (plus streak and first-of-day bonuses); a gentle idle decay kicks in after a week off. Premium doubles earning."),
        GuideEntry("Store", "Spend Feathers on profile pictures, avatar overlay rings, titles, premium accent colours, and Flock Premium (20000 Feathers; a $2/mo option would need Google Play Billing)."),
        GuideEntry("Passport", "Your collectible stamps — places, rare species, sky catches and events. Each new stamp animates in. Browse them by family on the Passport page."),
        GuideEntry("Renames", "On your Profile: name your flock (first time free) and change your username (spends Feathers)."),
    )),
    GuideSection("Settings", listOf(
        GuideEntry("Keys", "One place for every optional API key (aircraft, weather, traffic, webcams, maps). Everything has a keyless default; a key only raises limits or unlocks a source."),
        GuideEntry("Appearance & Night vision", "Pick a feel and accent; Night vision turns everything red-on-black for dark-sky field use."),
        GuideEntry("Danger zone", "Reset progress, wipe captures/history, sign out, or delete your account — each needs you to type a word and press-and-hold to confirm."),
    )),
)

/**
 * Guide — the whole app explained: what each feature is, when to use it, and how. Searchable. The
 * in-app manual that complements each screen's own "?" button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val sections = if (q.isBlank()) GUIDE else GUIDE.mapNotNull { s ->
        val matches = s.entries.filter { it.name.lowercase().contains(q) || it.text.lowercase().contains(q) }
        if (matches.isEmpty()) null else s.copy(entries = matches)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
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
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Search the guide") })
            }
            sections.forEach { section ->
                item(section.title) {
                    Text(section.title.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                }
                items(section.entries.size) { i ->
                    val e = section.entries[i]
                    Card(Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(e.name, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(e.text, fontSize = 13.sp, lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f))
                        }
                    }
                }
            }
            if (sections.isEmpty()) item {
                Text("Nothing matches \"$query\".", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = 13.sp, modifier = Modifier.padding(8.dp))
            }
        }
    }
}
