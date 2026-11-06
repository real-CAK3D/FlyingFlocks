package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.crew.CrewMember
import com.cak3d.biscuitmesh.crew.LeaderRow
import kotlinx.coroutines.launch

private val FLOCK = Color(0xFF34D399)
private val ACCENT = Color(0xFF22D3EE)

/** The Flock — online layer: your band code, wings you tail, live operators, all-time totals. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewScreen(vm: MainViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val share by vm.crewShare.collectAsStateWithLifecycle(initialValue = false)
    val live by vm.crew.live.collectAsStateWithLifecycle()
    val online by vm.crew.online.collectAsStateWithLifecycle()
    val tail by vm.crew.flock.collectAsStateWithLifecycle()
    val totals by vm.crew.totals.collectAsStateWithLifecycle()
    val board by vm.crew.board.collectAsStateWithLifecycle()
    val feed by vm.crew.feed.collectAsStateWithLifecycle()
    val season by vm.crew.season.collectAsStateWithLifecycle()
    val savedHandle by vm.flockHandle.collectAsStateWithLifecycle(initialValue = "")
    val drives by vm.drives.collectAsStateWithLifecycle(initialValue = emptyList())

    var band by remember { mutableStateOf<String?>(null) }
    var codeField by remember { mutableStateOf("") }
    var claimMsg by remember { mutableStateOf<String?>(null) }
    // Don't re-key on savedHandle (it starts "" then arrives async and would wipe a mid-edit).
    // Sync from the stored value only until the user starts typing.
    var handleField by remember { mutableStateOf(savedHandle) }
    var handleTouched by remember { mutableStateOf(false) }
    LaunchedEffect(savedHandle) { if (!handleTouched && savedHandle.isNotBlank()) handleField = savedHandle }

    // Turn the online layer on, and report my lifetime totals to the flock.
    LaunchedEffect(Unit) { vm.enableFlock() }
    LaunchedEffect(drives.size) {
        if (drives.isNotEmpty()) runCatching {
            vm.crew.pushStats(
                wifi = drives.sumOf { it.wifiCount.toLong() },
                bt = drives.sumOf { it.btCount.toLong() },
                cameras = drives.sumOf { it.cameraCount.toLong() },
                drives = drives.size.toLong(),
                km = drives.sumOf { it.distanceM } / 1000.0,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Flock") },
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
            item("live") {
                FCard {
                    Text(if (online) "● ALOFT" else "○ TAKING OFF…", style = MaterialTheme.typography.labelMedium,
                        color = if (online) FLOCK else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("$live wing${if (live == 1) "" else "s"} in the air now", style = MaterialTheme.typography.titleMedium)
                }
            }
            item("handle") {
                FCard {
                    Text("MY HANDLE", style = MaterialTheme.typography.labelMedium, color = ACCENT,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("The name other wings see on the map and the leaderboard.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = handleField, onValueChange = { handleField = it.take(24); handleTouched = true },
                            singleLine = true, label = { Text("Handle") }, modifier = Modifier.weight(1f))
                        Button(onClick = { vm.setFlockHandle(handleField) },
                            enabled = handleField.isNotBlank() && handleField != savedHandle) { Text("Save") }
                    }
                }
            }
            item("mycode") {
                FCard {
                    Text("MY BAND", style = MaterialTheme.typography.labelMedium, color = ACCENT,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("Hand this to another wing so you can see them on your map. Works once — then make a new band.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    band?.let {
                        Text(it, style = MaterialTheme.typography.displaySmall, color = ACCENT,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = { scope.launch { runCatching { band = vm.crew.newCode() } } }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (band == null) "Ring a band" else "New band")
                    }
                }
            }
            item("enter") {
                FCard {
                    Text("TAIL A WING", style = MaterialTheme.typography.labelMedium, color = ACCENT,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("Enter a band another wing gave you — they'll appear on your map.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    OutlinedTextField(value = codeField, onValueChange = { codeField = it.uppercase() },
                        singleLine = true, label = { Text("Band") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        scope.launch {
                            // claim() hits the network and can throw (bad code / offline) — never let it crash the app.
                            claimMsg = runCatching { vm.crew.claim(codeField) }
                                .fold({ it ?: "On their tail ✓" }, { "Couldn't reach the flock — try again." })
                            if (claimMsg?.contains("✓") == true) codeField = ""
                        }
                    }, enabled = codeField.length >= 4, modifier = Modifier.fillMaxWidth()) { Text("Tail them") }
                    claimMsg?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium,
                            color = if (it.contains("✓")) FLOCK else MaterialTheme.colorScheme.error)
                    }
                }
            }
            item("share") {
                FCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Show my position", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Only wings holding your band can see it. Off = you fly dark.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        }
                        Switch(checked = share, onCheckedChange = { vm.setCrewShare(it) })
                    }
                }
            }
            totals?.let { t ->
                item("totals") {
                    FCard {
                        Text("FLOCK · ALL-TIME", style = MaterialTheme.typography.labelMedium, color = FLOCK,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("Everything every wing has ever caught.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Stat(fmt(t.wifi), "WiFi", ACCENT, Modifier.weight(1f))
                            Stat(fmt(t.bt), "BT", Color(0xFF7C9CFF), Modifier.weight(1f))
                            Stat(fmt(t.cameras), "CAMS", Color(0xFFFF5470), Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Stat("${t.wings}", "WINGS", FLOCK, Modifier.weight(1f))
                            Stat(fmt(t.drives), "FLIGHTS", Color(0xFFF59E0B), Modifier.weight(1f))
                            Stat("${t.km.toInt()}", "KM", ACCENT, Modifier.weight(1f))
                        }
                    }
                }
            }
            season?.let { s ->
                item("season") {
                    FCard {
                        Text("SEASON · ${s.season}", style = MaterialTheme.typography.labelMedium, color = Color(0xFFF59E0B),
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("Territory resets each month — grab cells to climb this season's board.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Stat("${s.mineSeason}", "THIS SEASON", Color(0xFF34D399), Modifier.weight(1f))
                            Stat("${s.mineTotal}", "ALL-TIME", ACCENT, Modifier.weight(1f))
                            Stat("${s.total}", "FLOCK CELLS", Color(0xFFF59E0B), Modifier.weight(1f))
                        }
                    }
                }
            }
            if (feed.isNotEmpty()) {
                item("feed") {
                    FCard {
                        Text("FLOCK FEED · LIVE", style = MaterialTheme.typography.labelMedium, color = FLOCK,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("The latest land grabs across the whole flock.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        feed.take(12).forEach { f ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("▸", color = if (f.mine) FLOCK else ACCENT, fontFamily = FontFamily.Monospace)
                                Text(if (f.mine) "You" else f.handle, style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                                    color = if (f.mine) FLOCK else MaterialTheme.colorScheme.onSurface)
                                Text("held a cell ×${f.strength}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            board?.let { b ->
                if (b.top.isNotEmpty()) item("board") {
                    FCard {
                        Text("FLOCK LEADERBOARD · ${b.total} WING${if (b.total == 1) "" else "S"}",
                            style = MaterialTheme.typography.labelMedium, color = Color(0xFFF59E0B),
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("Score = WiFi + BT + cams×5 + flights×25 + km×8.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        b.top.forEach { LeaderRowView(it) }
                        // If you're ranked below the shown top, pin your own standing at the bottom.
                        b.me?.let { me -> if (b.top.none { it.me }) { HDivider(); LeaderRowView(me) } }
                    }
                }
            }
            if (tail.isNotEmpty()) {
                item("tlabel") {
                    Text("ON YOUR TAIL · ${tail.size}", style = MaterialTheme.typography.labelMedium, color = FLOCK,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                items(tail, key = { it.userId }) { WingRow(it) }
            }
        }
    }
}

@Composable
private fun FCard(content: @Composable ColumnScope.() -> Unit) {
    com.cak3d.biscuitmesh.ui.theme.ThemedCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun Stat(value: String, label: String, accent: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun HDivider() {
    androidx.compose.material3.HorizontalDivider(
        Modifier.padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
}

@Composable
private fun LeaderRowView(r: LeaderRow) {
    val rankColor = when (r.rank) { 1 -> Color(0xFFFFD166); 2 -> Color(0xFFC0C7D0); 3 -> Color(0xFFCD7F32); else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("#${r.rank}", style = MaterialTheme.typography.titleMedium, color = rankColor,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
        Text(if (r.me) "${r.handle}  (you)" else r.handle, style = MaterialTheme.typography.bodyLarge,
            color = if (r.me) FLOCK else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (r.me) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(fmt(r.score), style = MaterialTheme.typography.titleMedium, color = ACCENT,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WingRow(m: CrewMember) {
    Card(Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(m.handle?.takeIf { it.isNotBlank() } ?: "Wing ${m.userId.take(8)}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("%.5f, %.5f".format(m.lat, m.lon), style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Text("updated ${m.updatedAt.take(19).replace('T', ' ')}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
        }
    }
}

private fun fmt(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}
