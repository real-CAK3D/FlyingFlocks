package com.cak3d.biscuitmesh.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.Hazard
import com.cak3d.biscuitmesh.geo.HazardKind
import com.cak3d.biscuitmesh.geo.OpenMhzClient
import com.cak3d.biscuitmesh.geo.OverpassClient
import com.cak3d.biscuitmesh.geo.WindyWebcamsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Local surveillance & comms feeds — all consume-only, all public data:
 *  • Cameras — OSM-tagged surveillance / ALPR cams near you (free, Overpass).
 *  • Scanner — public-safety radio recordings from OpenMHz (free), streamed here.
 *  • Webcams — live traffic / public webcams via Windy (needs a free key in Settings).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val fix by vm.currentFix.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.startGnss() }
    DisposableEffect(Unit) { onDispose { vm.stopGnss() } }

    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Cameras", "Scanner", "Webcams")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Feeds") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton("Live Feeds",
                        "Public, consume-only feeds for where you are: OSM-tagged surveillance cameras, " +
                            "OpenMHz public-safety scanner recordings, and live webcams via Windy. " +
                            "Nothing here transmits — it only shows what's already public.")
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab, modifier = Modifier.help("Feed tabs", "Three public, consume-only feeds for your area: Cameras (OSM-tagged surveillance/ALPR cams near you), Scanner (OpenMHz recorded calls plus a Broadcastify live link), and Webcams (live public webcams via a free Windy key). Nothing here transmits.")) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            when (tab) {
                0 -> CamerasTab(fix)
                1 -> ScannerTab(vm, fix)
                else -> WebcamsTab(vm, fix)
            }
        }
    }
}

// ---------------- Cameras (OSM) ----------------

@Composable
private fun CamerasTab(fix: Pair<Double, Double>?) {
    var cams by remember { mutableStateOf<List<Hazard>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(fix?.let { Math.round(it.first * 100) to Math.round(it.second * 100) }) {
        val f = fix ?: return@LaunchedEffect
        loading = true
        cams = OverpassClient().cameras(f.first, f.second, 6000)
        loading = false
    }
    if (fix == null) { Centered("Waiting for a GPS fix…"); return }
    if (loading && cams.isEmpty()) { Centered("Scanning OpenStreetMap for cameras…", spin = true); return }
    if (cams.isEmpty()) { Centered("No tagged cameras within 6 km."); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValuesAll(12), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(cams, key = { it.id }) { c ->
            val distM = fix.let { f -> distMeters(f.first, f.second, c.lat, c.lon) }
            FeedRow(
                icon = if (c.kind == HazardKind.FLOCK) Icons.Default.CameraAlt else Icons.Default.Videocam,
                title = kindLabel(c.kind),
                sub = buildString {
                    if (c.name.isNotBlank()) append(c.name).append(" · ")
                    append("${distM.roundToInt()} m away")
                },
            )
        }
    }
}

private fun kindLabel(k: HazardKind): String = when (k) {
    HazardKind.FLOCK -> "ALPR / Flock camera"
    HazardKind.CAMERA -> "Surveillance camera"
    HazardKind.SPEED_CAMERA -> "Speed camera"
    HazardKind.RED_LIGHT -> "Red-light camera"
    else -> "Camera"
}

// ---------------- Scanner (OpenMHz) ----------------

@Composable
private fun ScannerTab(vm: MainViewModel, fix: Pair<Double, Double>?) {
    val ctx = LocalContext.current
    val client = remember { OpenMhzClient() }
    var systems by remember { mutableStateOf<List<OpenMhzClient.System>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<OpenMhzClient.System?>(null) }
    var calls by remember { mutableStateOf<List<OpenMhzClient.Call>>(emptyList()) }
    var loadingSys by remember { mutableStateOf(true) }
    var loadingCalls by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var region by remember { mutableStateOf<ScanRegion?>(null) }

    // One MediaPlayer for the whole tab.
    val player = remember { MediaPlayer() }
    var playingId by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { runCatching { player.release() } } }

    LaunchedEffect(Unit) { systems = client.systems(); loadingSys = false }
    LaunchedEffect(fix) {
        val f = fix ?: return@LaunchedEffect
        region = withContext(Dispatchers.IO) { reverseRegion(ctx, f.first, f.second) }
    }
    LaunchedEffect(selected, reload) {
        val s = selected ?: return@LaunchedEffect
        loadingCalls = true; calls = client.calls(s.shortName); loadingCalls = false
    }

    fun play(c: OpenMhzClient.Call) {
        runCatching {
            if (playingId == c.id) { player.stop(); player.reset(); playingId = null; return }
            player.reset()
            player.setDataSource(c.url)
            player.setOnCompletionListener { playingId = null }
            player.prepareAsync()
            player.setOnPreparedListener { it.start() }
            playingId = c.id
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (selected == null) {
            BroadcastifyCard(vm)
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Search systems (city / state)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
            if (loadingSys) { Centered("Loading scanner systems…", spin = true); return }
            val q = query.trim()
            val reg = region
            // When the box is empty, surface systems in the operator's state/city first.
            val near = if (q.isBlank() && reg != null) systems.filter { matchesRegion(it, reg) } else emptyList()
            val nearKeys = near.mapTo(HashSet()) { it.shortName }
            val rest = (if (q.isBlank()) systems else systems.filter {
                "${it.name} ${it.city} ${it.state}".contains(q, ignoreCase = true)
            }).filter { it.shortName !in nearKeys }.take(140)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValuesAll(12), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (near.isNotEmpty()) {
                    item { ScanLabel("NEAR YOU · ${reg!!.label}") }
                    items(near, key = { it.shortName }) { s ->
                        FeedRow(Icons.Default.Nightlight, s.name,
                            listOf(s.city, s.state).filter { it.isNotBlank() }.joinToString(", "),
                            onClick = { selected = s })
                    }
                    item { ScanLabel("ALL SYSTEMS") }
                } else if (q.isBlank() && reg != null) {
                    item {
                        Text(
                            "No OpenMHz system is tagged ${reg!!.label}. Live ${reg!!.label} police/fire/EMS " +
                                "usually lives on Broadcastify, not OpenMHz. Browse every recorded system below, " +
                                "or search a nearby city.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                items(rest, key = { it.shortName }) { s ->
                    FeedRow(
                        icon = Icons.Default.Nightlight,
                        title = s.name,
                        sub = listOf(s.city, s.state).filter { it.isNotBlank() }.joinToString(", "),
                        onClick = { selected = s },
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { runCatching { player.stop(); player.reset() }; playingId = null; selected = null; calls = emptyList() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to systems")
                }
                Text(selected!!.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
            }
            if (loadingCalls && calls.isEmpty()) { Centered("Fetching recent calls…", spin = true); return }
            if (calls.isEmpty()) { Centered("No recent calls on this system."); return }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValuesAll(12), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(calls, key = { it.id }) { c ->
                    val ago = ((System.currentTimeMillis() - c.timeMs) / 1000).coerceAtLeast(0)
                    FeedRow(
                        icon = if (playingId == c.id) Icons.Default.Stop else Icons.Default.PlayArrow,
                        title = c.talkgroupName.ifBlank { "TG ${c.talkgroupNum}" },
                        sub = "${c.lenSec}s · ${agoText(ago)}",
                        onClick = { play(c) },
                    )
                }
            }
        }
    }
}

/**
 * Live police/fire/EMS via Broadcastify. Their stream now needs a session and streams through an
 * MSE blob, so it can't be played with a plain in-app MediaPlayer — instead we hand the feed to the
 * Broadcastify app (or the browser), which runs their player. The operator saves their county feed
 * ID once. OpenMHz (below) still plays recorded calls directly in the app.
 */
@Composable
private fun BroadcastifyCard(vm: MainViewModel) {
    val ctx = LocalContext.current
    val savedFeed by vm.broadcastifyFeed.collectAsStateWithLifecycle(initialValue = "")
    var feed by remember(savedFeed) { mutableStateOf(savedFeed) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📻  BROADCASTIFY · LIVE", style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text("Live police/fire/EMS scanners. Enter your county feed ID (the number in a " +
                "broadcastify.com/listen/feed/… link), then Listen — it opens in the Broadcastify app or " +
                "your browser, which runs the live stream. (OpenMHz below plays recorded calls in-app.)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            OutlinedTextField(
                value = feed, onValueChange = { feed = it.filter { c -> c.isDigit() } },
                label = { Text("Feed ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        vm.setBroadcastifyFeed(feed)
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://www.broadcastify.com/listen/feed/$feed")))
                        }
                    },
                    enabled = feed.isNotBlank(), modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(6.dp)); Text("Listen")
                }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://www.broadcastify.com/listen/near/")))
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Find my feed") }
            }
        }
    }
}

// ---------------- Scanner location helpers ----------------

/** The operator's coarse location, for surfacing nearby scanner systems. */
private data class ScanRegion(val city: String, val stateName: String, val stateAbbr: String) {
    val label: String get() = listOf(city, stateAbbr.ifBlank { stateName })
        .filter { it.isNotBlank() }.joinToString(", ").ifBlank { "your area" }
}

/** Reverse-geocode a fix to city + state (blocking; call off the main thread). Null if unavailable. */
private fun reverseRegion(ctx: android.content.Context, lat: Double, lon: Double): ScanRegion? = runCatching {
    @Suppress("DEPRECATION")
    val addr = android.location.Geocoder(ctx, java.util.Locale.US).getFromLocation(lat, lon, 1)?.firstOrNull()
        ?: return null
    val state = addr.adminArea ?: ""
    ScanRegion(addr.locality ?: addr.subAdminArea ?: "", state, US_STATE_ABBR[state] ?: "")
}.getOrNull()

/** True if an OpenMHz system sits in the operator's city or state (matching full name or abbrev). */
private fun matchesRegion(s: OpenMhzClient.System, r: ScanRegion): Boolean {
    val st = s.state.trim()
    val stateHit = st.isNotBlank() &&
        (st.equals(r.stateName, true) || (r.stateAbbr.isNotBlank() && st.equals(r.stateAbbr, true)))
    val cityHit = r.city.isNotBlank() && s.city.contains(r.city, ignoreCase = true)
    return stateHit || cityHit
}

@Composable
private fun ScanLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

private val US_STATE_ABBR = mapOf(
    "Alabama" to "AL", "Alaska" to "AK", "Arizona" to "AZ", "Arkansas" to "AR", "California" to "CA",
    "Colorado" to "CO", "Connecticut" to "CT", "Delaware" to "DE", "Florida" to "FL", "Georgia" to "GA",
    "Hawaii" to "HI", "Idaho" to "ID", "Illinois" to "IL", "Indiana" to "IN", "Iowa" to "IA",
    "Kansas" to "KS", "Kentucky" to "KY", "Louisiana" to "LA", "Maine" to "ME", "Maryland" to "MD",
    "Massachusetts" to "MA", "Michigan" to "MI", "Minnesota" to "MN", "Mississippi" to "MS", "Missouri" to "MO",
    "Montana" to "MT", "Nebraska" to "NE", "Nevada" to "NV", "New Hampshire" to "NH", "New Jersey" to "NJ",
    "New Mexico" to "NM", "New York" to "NY", "North Carolina" to "NC", "North Dakota" to "ND", "Ohio" to "OH",
    "Oklahoma" to "OK", "Oregon" to "OR", "Pennsylvania" to "PA", "Rhode Island" to "RI", "South Carolina" to "SC",
    "South Dakota" to "SD", "Tennessee" to "TN", "Texas" to "TX", "Utah" to "UT", "Vermont" to "VT",
    "Virginia" to "VA", "Washington" to "WA", "West Virginia" to "WV", "Wisconsin" to "WI", "Wyoming" to "WY",
    "District of Columbia" to "DC",
)

// ---------------- Webcams (Windy, keyed) ----------------

@Composable
private fun WebcamsTab(vm: MainViewModel, fix: Pair<Double, Double>?) {
    val keys by vm.apiKeys.collectAsStateWithLifecycle()
    val key = keys["windy_webcams"].orEmpty()
    val ctx = LocalContext.current
    var cams by remember { mutableStateOf<List<WindyWebcamsClient.Webcam>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(key, fix?.let { Math.round(it.first * 100) to Math.round(it.second * 100) }) {
        val f = fix ?: return@LaunchedEffect
        if (key.isBlank()) return@LaunchedEffect
        loading = true
        cams = WindyWebcamsClient().nearby(f.first, f.second, 50, key)
        loading = false
    }
    if (key.isBlank()) {
        Centered("Add a free Windy Webcams key in Settings → API keys to see live traffic & public webcams near you.")
        return
    }
    if (fix == null) { Centered("Waiting for a GPS fix…"); return }
    if (loading && cams.isEmpty()) { Centered("Finding webcams near you…", spin = true); return }
    if (cams.isEmpty()) { Centered("No webcams within 50 km."); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValuesAll(12), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(cams, key = { it.id }) { cam ->
            Column(
                Modifier.fillMaxWidth().clickable {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cam.previewUrl))) }
                },
            ) {
                RemoteImage(cam.previewUrl, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Text(cam.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// ---------------- shared bits ----------------

@Composable
private fun FeedRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    sub: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun Centered(text: String, spin: Boolean = false) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (spin) CircularProgressIndicator()
            Text(text, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
    }
}

/** Minimal remote-image loader — the app doesn't bundle an image library. */
@Composable
private fun RemoteImage(url: String, modifier: Modifier = Modifier) {
    var bmp by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) {
        bmp = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000; readTimeout = 15_000
                    setRequestProperty("User-Agent", "FlyingFlocks/0.9")
                }
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

private fun agoText(secs: Long): String = when {
    secs < 60 -> "${secs}s ago"
    secs < 3600 -> "${secs / 60}m ago"
    else -> "${secs / 3600}h ago"
}

private fun distMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

@Suppress("FunctionName")
private fun PaddingValuesAll(dp: Int) = androidx.compose.foundation.layout.PaddingValues(dp.dp)
