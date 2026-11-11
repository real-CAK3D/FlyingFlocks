package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.model.BiscuitDevice
import com.cak3d.biscuitmesh.model.BoardHealth
import com.cak3d.biscuitmesh.model.LogEntry
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import kotlinx.coroutines.flow.MutableStateFlow

private val OK = Color(0xFF5FD36A)
private val WARN = Color(0xFFF0B429)
private val BAD = Color(0xFFFF5470)
private val DIM = Color(0xFF9AABA9)
private val MONO = FontFamily.Monospace

// Capture DATA subtypes, so each board shows how much it has actually relayed.
private val HEALTH_CAPTURES = setOf(
    "AP", "STA", "BT", "RF", "SUBGHZ", "TPMS", "WEATHER", "KEYFOB", "PROBE", "NFC", "IR",
)

/**
 * Fleet Health + Triangulation. The health tab answers "is each board actually
 * working?" from the live DATA:HEALTH heartbeat (heap, uptime, radios) and the
 * relay tally. The triangulate tab uses the SAME signal heard by two or more
 * boards to point at where it is (strongest board = closest).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetHealthScreen(vm: MainViewModel, onBack: () -> Unit) {
    val devFlow = remember(vm) { vm.devices ?: MutableStateFlow(emptyMap<String, BiscuitDevice>()) }
    val logFlow = remember(vm) { vm.log ?: MutableStateFlow(emptyList<LogEntry>()) }
    val devMap by devFlow.collectAsStateWithLifecycle()
    val log by logFlow.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("FLEET HEALTH", fontFamily = MONO) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("HEALTH", fontFamily = MONO, fontSize = 12.sp) })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("TRIANGULATE", fontFamily = MONO, fontSize = 12.sp) })
            }
            if (tab == 0) HealthTab(vm, devMap, log) else TriangulateTab(vm, log)
        }
    }
}

// ---- Health tab -----------------------------------------------------------

@Composable
private fun HealthTab(vm: MainViewModel, devMap: Map<String, BiscuitDevice>, log: List<LogEntry>) {
    val boards = devMap.values.sortedWith(
        compareByDescending<BiscuitDevice> { it.isLive }.thenBy { it.displayName },
    )
    // Per-board relayed-capture tally from the fleet log.
    val relayCounts = remember(log) {
        log.asSequence()
            .filter { it.message.type == BiscuitMessage.Type.DATA && it.message.subtype in HEALTH_CAPTURES }
            .groupingBy { it.address }.eachCount()
    }
    val liveCount = boards.count { it.isLive }
    val totalRelayed = relayCounts.values.sum()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "> $liveCount LIVE · ${boards.size} KNOWN · $totalRelayed CAPTURES RELAYED",
                style = MaterialTheme.typography.labelMedium, fontFamily = MONO,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (boards.isEmpty()) {
            item {
                Text(
                    "No boards yet. Connect one in Fleet, or attach a board over USB. " +
                        "Each board streams a DATA:HEALTH heartbeat here every few seconds.",
                    color = DIM, fontSize = 13.sp,
                )
            }
        }
        items(boards.size) { i ->
            BoardHealthCard(boards[i], relayCounts[boards[i].address] ?: 0, vm)
        }
        item {
            Text(
                "Health streams over BLE and USB-CDC alike — the same board plugged into a " +
                    "PC or phone shows these numbers on any serial terminal at 115200 baud.",
                color = DIM, fontSize = 11.sp, fontFamily = MONO,
            )
        }
    }
}

@Composable
private fun BoardHealthCard(d: BiscuitDevice, relayed: Int, vm: MainViewModel) {
    val h = d.health
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(if (d.isLive) OK else DIM)
                Spacer(Modifier.size(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                    Text(
                        (d.typeLabel() ?: "board") + " · " + d.statusLabel(),
                        color = DIM, fontSize = 11.sp, fontFamily = MONO, maxLines = 1,
                    )
                }
                val linkState = h?.link ?: d.status?.link
                if (linkState != null) {
                    Pill(
                        if (linkState == "ok") "LINKED" else linkState.uppercase(),
                        if (linkState == "ok") OK else WARN,
                    )
                }
            }

            if (h == null) {
                Text(
                    if (d.isLive) "waiting for first heartbeat…" else "no live telemetry (offline)",
                    color = DIM, fontSize = 12.sp, fontFamily = MONO,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Metric("HEAP", h.heapFree?.let { "${it / 1024} KB" } ?: "—")
                    Metric("UPTIME", h.uptimeSec?.let { fmtUptime(it) } ?: "—")
                    Metric("HEARD", fmtAgo(System.currentTimeMillis() - h.atMs))
                    h.battery?.let { Metric("BATT", "$it%") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioChip("SD", h.sd)
                    RadioChip("CC1101", h.cc1101)
                    RadioChip("NFC", h.nfc)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RELAYED $relayed", color = DIM, fontSize = 11.sp, fontFamily = MONO)
                Spacer(Modifier.weight(1f))
                if (d.isLive) {
                    Text(
                        "refresh",
                        color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontFamily = MONO,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                            .clickable { vm.refreshStatus(d.address) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

// ---- Triangulate tab ------------------------------------------------------

/** A signal heard by two or more boards, with each board's latest RSSI. */
private data class TriTarget(
    val kind: String,          // "BT" | "RF"
    val id: String,            // mac or freq
    val label: String,
    val perBoard: List<Pair<String, Int>>,  // board address -> rssi, strongest first
)

@Composable
private fun TriangulateTab(vm: MainViewModel, log: List<LogEntry>) {
    val targets = remember(log) { buildTargets(log) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Signals two or more boards both hear. The board with the strongest RSSI " +
                    "is physically closest — spread the fleet out and the bars point the way.",
                color = DIM, fontSize = 12.sp,
            )
        }
        if (targets.isEmpty()) {
            item {
                Text(
                    "Nothing heard by two boards yet. Connect two or more boards and run a " +
                        "Bluetooth or sub-GHz scan on each so the same signal lands on both.",
                    color = DIM, fontSize = 13.sp, fontFamily = MONO,
                )
            }
        }
        items(targets.size) { i -> TriCard(targets[i], vm) }
        item {
            Text(
                "Note: a passive scan only hears devices that are transmitting (advertising). " +
                    "Telling transmit from receive needs a sniffer radio the fleet doesn't carry yet.",
                color = DIM, fontSize = 11.sp, fontFamily = MONO,
            )
        }
    }
}

@Composable
private fun TriCard(t: TriTarget, vm: MainViewModel) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(t.kind, if (t.kind == "BT") BAD else WARN)
                Spacer(Modifier.size(8.dp))
                Text(t.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
            }
            val closest = t.perBoard.firstOrNull()?.first
            t.perBoard.forEach { (addr, rssi) ->
                RssiBar(vm.nameFor(addr), rssi, addr == closest)
            }
        }
    }
}

@Composable
private fun RssiBar(name: String, rssi: Int, closest: Boolean) {
    val frac = ((rssi + 100f) / 70f).coerceIn(0.04f, 1f)
    val tint = if (closest) OK else DIM
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row {
            Text(
                (if (closest) "◆ " else "") + name, color = tint, fontSize = 12.sp,
                fontFamily = MONO, maxLines = 1, modifier = Modifier.weight(1f),
            )
            Text("$rssi dBm", color = tint, fontSize = 12.sp, fontFamily = MONO)
        }
        Box(
            Modifier.fillMaxWidth().height(6.dp)
                .background(DIM.copy(alpha = 0.18f), RoundedCornerShape(3.dp)),
        ) {
            Box(
                Modifier.fillMaxWidth(frac).height(6.dp)
                    .background(tint, RoundedCornerShape(3.dp)),
            )
        }
    }
}

/** Collapse the fleet log into cross-board targets (latest RSSI per board). */
private fun buildTargets(log: List<LogEntry>): List<TriTarget> {
    // id -> (kind,label, board->rssi) ; later entries overwrite earlier RSSI.
    data class Acc(val kind: String, var label: String, val boards: LinkedHashMap<String, Int> = LinkedHashMap())
    val byId = LinkedHashMap<String, Acc>()
    for (e in log) {
        val m = e.message
        if (m.type != BiscuitMessage.Type.DATA) continue
        val p = m.payload.split(',')
        when (m.subtype) {
            "BT" -> {
                if (p.size < 3) continue
                val mac = p[1].trim(); val rssi = p.last().trim().toIntOrNull() ?: continue
                val name = p[0].trim().ifEmpty { mac }
                val a = byId.getOrPut("BT:$mac") { Acc("BT", name) }
                if (name.isNotBlank()) a.label = name
                a.boards[e.address] = rssi
            }
            "RF" -> {
                if (p.size < 2) continue
                val freq = p[0].trim(); val rssi = p.last().trim().toIntOrNull() ?: continue
                val a = byId.getOrPut("RF:$freq") { Acc("RF", "$freq MHz") }
                a.boards[e.address] = rssi
            }
        }
    }
    return byId.values
        .filter { it.boards.size >= 2 }
        .map { acc ->
            val id = acc.label
            TriTarget(acc.kind, id, acc.label, acc.boards.entries.map { it.key to it.value }.sortedByDescending { it.second })
        }
        .sortedByDescending { it.perBoard.size }
}

// ---- small shared pieces --------------------------------------------------

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, color = DIM, fontSize = 9.sp, fontFamily = MONO)
        Text(value, fontSize = 14.sp, fontFamily = MONO, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RadioChip(label: String, state: Boolean?) {
    val tint = when (state) { true -> OK; false -> BAD; null -> DIM }
    Row(
        Modifier
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Dot(tint, 7.dp)
        Text(label, color = tint, fontSize = 10.sp, fontFamily = MONO)
    }
}

@Composable
private fun Pill(label: String, tint: Color) {
    Text(
        label, color = tint, fontSize = 10.sp, fontFamily = MONO, fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun Dot(color: Color, size: androidx.compose.ui.unit.Dp = 9.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

private fun fmtUptime(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

private fun fmtAgo(ms: Long): String {
    if (ms < 0) return "now"
    val s = ms / 1000
    return when {
        s < 2 -> "now"
        s < 60 -> "${s}s ago"
        else -> "${s / 60}m ago"
    }
}
