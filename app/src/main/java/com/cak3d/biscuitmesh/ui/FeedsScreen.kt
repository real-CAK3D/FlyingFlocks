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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            when (tab) {
                0 -> CamerasTab(fix)
                1 -> ScannerTab()
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
private fun ScannerTab() {
    val client = remember { OpenMhzClient() }
    var systems by remember { mutableStateOf<List<OpenMhzClient.System>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<OpenMhzClient.System?>(null) }
    var calls by remember { mutableStateOf<List<OpenMhzClient.Call>>(emptyList()) }
    var loadingSys by remember { mutableStateOf(true) }
    var loadingCalls by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    // One MediaPlayer for the whole tab.
    val player = remember { MediaPlayer() }
    var playingId by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { runCatching { player.release() } } }

    LaunchedEffect(Unit) { systems = client.systems(); loadingSys = false }
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
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Search systems (city / state)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
            if (loadingSys) { Centered("Loading scanner systems…", spin = true); return }
            val filtered = systems.filter {
                query.isBlank() || "${it.name} ${it.city} ${it.state}".contains(query, ignoreCase = true)
            }.take(120)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValuesAll(12), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.shortName }) { s ->
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
