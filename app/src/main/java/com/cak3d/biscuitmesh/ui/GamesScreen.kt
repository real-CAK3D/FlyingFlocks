package com.cak3d.biscuitmesh.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.sensors.PhoneScanRow
import com.cak3d.biscuitmesh.wardrive.SampleKind
import kotlinx.coroutines.delay
import kotlin.math.min

private val GAME_GREEN = Color(0xFF34D399)

private data class GameEntry(val title: String, val sub: String, val icon: ImageVector, val route: String, val tint: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val entries = listOf(
        GameEntry("Signal Hunt", "The app hides a device nearby — walk to it, warmer buzzes faster", Icons.Default.Pets, "game/signalhunt", GAME_GREEN),
        GameEntry("Device Safari", "A card of radio species to spot around you — tick them all off", Icons.Default.Casino, "game/safari", Color(0xFF22D3EE)),
        GameEntry("Band Blitz", "60 seconds — catch as many new Wi-Fi & BT devices as you can", Icons.Default.Timer, "game/blitz", Color(0xFFFFD166)),
    )
    GameScaffold("Games", onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries, key = { it.route }) { e ->
                Card(onClick = { onOpen(e.route) }, modifier = Modifier.fillMaxWidth(),
                    shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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

// ---- Signal Hunt ------------------------------------------------------------

@Composable
fun SignalHuntScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) { vm.clearPhoneScan(); vm.startPhoneScan(wifi = true, ble = true); onDispose { vm.stopPhoneScan() } }
    val results by vm.phoneScanResults.collectAsStateWithLifecycle(initialValue = emptyList())

    var quarryId by remember { mutableStateOf<String?>(null) }
    var startMs by remember { mutableLongStateOf(0L) }
    var found by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }

    fun newHunt() {
        val pool = results.filter { it.kind == SampleKind.AP || it.kind == SampleKind.STATION }.ifEmpty { results }
        quarryId = pool.randomOrNull()?.id
        startMs = System.currentTimeMillis(); found = false; elapsed = 0L
    }
    LaunchedEffect(results.isNotEmpty()) { if (quarryId == null && results.isNotEmpty()) newHunt() }

    val quarry = results.firstOrNull { it.id == quarryId }
    val live by rememberUpdatedState(quarry)
    LaunchedEffect(quarryId, found) {
        while (quarryId != null && !found) {
            val q = live
            if (q != null) {
                elapsed = System.currentTimeMillis() - startMs
                if (q.rssi >= -45) { found = true; buzz(context, 100); delay(90); buzz(context, 100) }
                else {
                    val s = ((q.rssi + 95f) / 65f).coerceIn(0f, 1f)
                    buzz(context, (20 + s * 80).toInt())
                    delay((1100 - s * 950).toLong().coerceIn(150, 1100)); continue
                }
            }
            delay(400)
        }
    }

    GameScaffold("Signal Hunt", onBack) {
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (quarryId == null) {
                Text("Scanning for a quarry to hide…", style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 40.dp))
            } else {
                Text(if (found) "FOUND IT!" else "Hunting: ${huntHint(quarry)}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = if (found) GAME_GREEN else MaterialTheme.colorScheme.onSurface)
                val rssi = quarry?.rssi
                val strength = rssi?.let { ((it + 95f) / 65f).coerceIn(0f, 1f) } ?: 0f
                val ringCol = if (found) GAME_GREEN else when {
                    strength > 0.75f -> GAME_GREEN; strength > 0.45f -> Color(0xFFF59E0B); else -> Color(0xFFFF5470)
                }
                Box(Modifier.fillMaxWidth(0.75f).aspectRatio(1f).drawBehind {
                    val r = min(size.width, size.height) / 2f
                    drawCircle(ringCol.copy(alpha = 0.12f), r)
                    drawCircle(ringCol.copy(alpha = 0.4f), r * (0.35f + strength * 0.6f), style = Stroke(12f))
                    drawCircle(ringCol, r * (0.35f + strength * 0.6f) * 0.5f, style = Stroke(7f))
                }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (rssi != null) "$rssi dB" else "—", style = MaterialTheme.typography.headlineMedium,
                            color = ringCol, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                found -> "caught!"
                                rssi == null -> "lost — move around"
                                strength > 0.75f -> "boiling hot"
                                strength > 0.55f -> "warm"
                                strength > 0.35f -> "cool"
                                else -> "cold"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        )
                    }
                }
                Text("${elapsed / 1000}s", style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                if (found) {
                    Text("It was: ${quarry?.label?.ifBlank { quarry.id } ?: quarryId}  ·  ${elapsed / 1000}s",
                        style = MaterialTheme.typography.bodyMedium, color = GAME_GREEN)
                }
                Button(onClick = { newHunt() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (found) "Hunt another" else "New quarry")
                }
            }
        }
    }
}

private fun huntHint(q: PhoneScanRow?): String = when {
    q == null -> "a hidden signal"
    q.kind == SampleKind.BT -> "a Bluetooth device"
    q.channel >= 1000 -> "a 6 GHz Wi-Fi network"
    q.channel >= 32 -> "a 5 GHz Wi-Fi network"
    q.channel in 1..14 -> "a 2.4 GHz Wi-Fi network"
    else -> "a radio signal"
}

// ---- Device Safari (bingo) --------------------------------------------------

private data class SafariTarget(val key: String, val label: String, val find: (List<PhoneScanRow>) -> PhoneScanRow?)

private fun isPrivateBle(mac: String): Boolean {
    val first = mac.substringBefore(":").toIntOrNull(16) ?: return false
    return ((first ushr 6) and 0x3) == 0b01
}

private val SAFARI = listOf(
    SafariTarget("g5", "A 5 GHz network") { l -> l.firstOrNull { it.channel in 32..999 } },
    SafariTarget("wifi6", "A Wi-Fi 6 (or newer) AP") { l -> l.firstOrNull { it.standard == "Wi-Fi 6" || it.standard == "Wi-Fi 6E" || it.standard == "Wi-Fi 7" } },
    SafariTarget("hidden", "A hidden network") { l -> l.firstOrNull { (it.kind == SampleKind.AP || it.kind == SampleKind.STATION) && it.label.isBlank() } },
    SafariTarget("wpa3", "A WPA3 network") { l -> l.firstOrNull { it.security == "WPA3" } },
    SafariTarget("open", "An open network") { l -> l.firstOrNull { it.security == "Open" } },
    SafariTarget("namedbt", "A named Bluetooth device") { l -> l.firstOrNull { it.kind == SampleKind.BT && it.label.isNotBlank() } },
    SafariTarget("randbt", "A randomized BLE address") { l -> l.firstOrNull { it.kind == SampleKind.BT && isPrivateBle(it.id) } },
    SafariTarget("strong", "Something closer than −40 dBm") { l -> l.firstOrNull { it.rssi >= -40 } },
    SafariTarget("crowd", "10+ devices at once") { l -> if (l.size >= 10) l.first() else null },
)

@Composable
fun DeviceSafariScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) { vm.clearPhoneScan(); vm.startPhoneScan(wifi = true, ble = true); onDispose { vm.stopPhoneScan() } }
    val results by vm.phoneScanResults.collectAsStateWithLifecycle(initialValue = emptyList())

    var caught by remember { mutableStateOf(mapOf<String, String>()) }
    LaunchedEffect(results) {
        var changed = false
        val next = caught.toMutableMap()
        SAFARI.forEach { t ->
            if (!next.containsKey(t.key)) t.find(results)?.let { m ->
                next[t.key] = m.label.ifBlank { m.id }; changed = true
            }
        }
        if (changed) { caught = next; buzz(context, 60) }
    }
    val done = caught.size

    GameScaffold("Device Safari", onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item("head") {
                Card(Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("$done / ${SAFARI.size} spotted", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            color = if (done == SAFARI.size) GAME_GREEN else MaterialTheme.colorScheme.onSurface)
                        Text(if (done == SAFARI.size) "Full card — you caught them all!"
                        else "Walk around and keep scanning — species get ticked off as they appear.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    }
                }
            }
            items(SAFARI, key = { it.key }) { t ->
                val hit = caught[t.key]
                Card(Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(containerColor =
                        if (hit != null) GAME_GREEN.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = CircleShape, color = if (hit != null) GAME_GREEN else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            modifier = Modifier.size(30.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (hit != null) Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF06281E), modifier = Modifier.size(20.dp))
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(t.label, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (hit != null) FontWeight.SemiBold else FontWeight.Normal)
                            if (hit != null) Text(hit, style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ---- Band Blitz -------------------------------------------------------------

@Composable
fun BandBlitzScreen(vm: MainViewModel, onBack: () -> Unit) {
    DisposableEffect(Unit) { vm.clearPhoneScan(); vm.startPhoneScan(wifi = true, ble = true); onDispose { vm.stopPhoneScan() } }
    val results by vm.phoneScanResults.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current

    var running by remember { mutableStateOf(false) }
    var remaining by remember { mutableIntStateOf(60) }
    var seen by remember { mutableStateOf(setOf<String>()) }
    var lastScore by remember { mutableIntStateOf(-1) }

    LaunchedEffect(running, results) { if (running) seen = seen + results.map { it.id } }
    LaunchedEffect(running) {
        if (running) {
            remaining = 60; seen = emptySet()
            while (remaining > 0 && running) { delay(1000); remaining-- }
            if (running) { lastScore = seen.size; running = false; buzz(context, 100) }
        }
    }

    GameScaffold("Band Blitz", onBack) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(if (running) "${remaining}s" else "60s", style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold, color = if (running && remaining <= 10) Color(0xFFFF5470) else MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 24.dp))
            Text("${seen.size}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = GAME_GREEN)
            Text("devices caught this round", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

            if (!running && lastScore >= 0) {
                Text("Last round: $lastScore caught · ${blitzRank(lastScore)}",
                    style = MaterialTheme.typography.titleMedium, color = GAME_GREEN, fontWeight = FontWeight.Bold)
            }
            Button(onClick = { running = !running }, modifier = Modifier.fillMaxWidth()) {
                Text(if (running) "Stop" else "Start 60-second blitz")
            }
            if (!running) Text("Hold the phone up and move — every new Wi-Fi or Bluetooth device counts once.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

private fun blitzRank(n: Int): String = when {
    n >= 120 -> "Apex · unreal"
    n >= 80 -> "Hawk"
    n >= 50 -> "Falcon"
    n >= 25 -> "Scout"
    else -> "Fledgling"
}

// ---- shared -----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
}

private fun buzz(context: Context, amplitudePct: Int) {
    runCatching {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(55, (amplitudePct * 255 / 100).coerceIn(1, 255)))
        } else {
            @Suppress("DEPRECATION") vib.vibrate(55)
        }
    }
}
