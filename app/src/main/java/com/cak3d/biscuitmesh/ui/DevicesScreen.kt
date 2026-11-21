package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.cak3d.biscuitmesh.model.LogEntry
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import kotlinx.coroutines.flow.MutableStateFlow

private val CYAN = Color(0xFF22D3EE)
private val GREEN = Color(0xFF34D399)
private val DIM = Color(0xFF64748B)

/** The capture subtypes a board relays — what it's "picking up". */
private val CAPTURE_SUBS = setOf(
    "AP", "STA", "BT", "RF", "SUBGHZ", "TPMS", "WEATHER", "KEYFOB", "PROBE", "NFC", "IR",
)

/**
 * Per-device view — one card per live board showing its own telemetry (link, battery, heap,
 * uptime, radios, mode) and, beneath it, the live signals that board is picking up: the most
 * recent captures it has relayed. The "all" cut of the visualizers, broken out per board.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DevicesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val devFlow = remember(vm) { vm.devices ?: MutableStateFlow(emptyMap<String, BiscuitDevice>()) }
    val logFlow = remember(vm) { vm.log ?: MutableStateFlow(emptyList<LogEntry>()) }
    val devMap by devFlow.collectAsStateWithLifecycle()
    val log by logFlow.collectAsStateWithLifecycle()

    val boards = devMap.values.filter { it.isLive }.sortedBy { it.displayName }
    // Per-board recent captures (newest first) + total count.
    val byBoard = remember(log) {
        log.asSequence()
            .filter { it.message.type == BiscuitMessage.Type.DATA && it.message.subtype in CAPTURE_SUBS }
            .groupBy { it.address }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Per-Device") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    HelpButton(
                        "Per-Device",
                        "Each connected board on its own: its live telemetry (link strength, battery, free memory, " +
                            "uptime, which radios came up, current mode) and — underneath — the signals that board is " +
                            "picking up right now, the most recent captures it has relayed. Connect boards in the Fleet tab.",
                    )
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "${boards.size} ${if (boards.size == 1) "board" else "boards"} live · each with its own telemetry + the signals it sees",
                    style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (boards.isEmpty()) {
                item {
                    Text(
                        "No boards connected. Connect one in the Fleet tab and its telemetry and live captures show up here.",
                        Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            } else {
                items(boards, key = { it.address }) { d ->
                    DeviceCard(d, byBoard[d.address].orEmpty())
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DeviceCard(d: BiscuitDevice, captures: List<LogEntry>) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = 8.dp)) { Text("●", color = GREEN) }
                Column(Modifier.weight(1f)) {
                    Text(d.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    d.typeLabel()?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }

            // Telemetry tiles.
            val h = d.health
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tele("LINK", "${d.rssi} dBm")
                Tele("BATT", (h?.battery ?: d.status?.battery)?.let { "$it%" } ?: "—")
                Tele("HEAP", h?.heapFree?.let { "${it / 1024} KB" } ?: "—")
                Tele("UPTIME", h?.uptimeSec?.let { fmtUp(it) } ?: "—")
                d.status?.activeOp?.takeIf { it.isNotBlank() }?.let { Tele("MODE", it) }
            }

            // Radios the board brought up.
            val radios = buildList {
                h?.sd?.let { add("SD" to it) }
                h?.cc1101?.let { add("CC1101" to it) }
                h?.nfc?.let { add("NFC" to it) }
            }
            if (radios.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    radios.forEach { (name, ok) -> RadioChip(name, ok) }
                }
            }

            // What it's picking up.
            Text(
                "PICKING UP · ${captures.size} captures",
                style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = CYAN,
            )
            if (captures.isEmpty()) {
                Text("Idle — run a scan from the WiFi / Bluetooth / Sub-GHz tab.",
                    style = MaterialTheme.typography.bodySmall, color = DIM)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    captures.takeLast(8).reversed().forEach { e -> CaptureLine(e) }
                }
            }
        }
    }
}

@Composable
private fun CaptureLine(e: LogEntry) {
    val sub = e.message.subtype
    val payload = e.message.payload
    // AP/STA/BT lines are comma-separated "label,addr,ch,rssi,..."; show the label + rssi where we can.
    val f = payload.split(',')
    val label = f.getOrNull(0)?.trim().orEmpty().ifBlank { payload.take(28) }
    val rssi = f.getOrNull(3)?.trim()?.toIntOrNull()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(sub, color = CYAN, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.background(CYAN.copy(alpha = 0.12f)).padding(horizontal = 4.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace, maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
        if (rssi != null) Text("$rssi", color = DIM, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}

@Composable
private fun Tele(k: String, v: String) {
    Column(
        Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(k, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = DIM, fontSize = 9.sp)
        Text(v, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RadioChip(name: String, ok: Boolean) {
    val c = if (ok) GREEN else Color(0xFFFF5470)
    Text(
        "${if (ok) "✓" else "✕"} $name",
        color = c, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(c.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

private fun fmtUp(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${sec % 60}s"
        else -> "${sec}s"
    }
}
