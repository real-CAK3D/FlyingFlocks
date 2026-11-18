package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.mesh.MeshLinkState
import com.cak3d.biscuitmesh.mesh.MeshNode

/**
 * Per-node telemetry + chat for a Meshtastic radio — the mesh equivalent of the
 * Biscuit [DeviceDetailScreen]. Shows what the node reports (battery, SNR, hops,
 * last-heard, GPS, uptime, utilization) and the mesh channel's recent messages,
 * with a quick-send box right here so you can reply on the spot.
 *
 * Meshtastic text is channel-wide, not a per-node DM, so the thread and send are
 * the primary channel's — shown on whichever node you opened.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MeshNodeDetailScreen(vm: MainViewModel, num: Long?, onBack: () -> Unit) {
    val nodes by vm.meshNodes.collectAsStateWithLifecycle()
    val messages by vm.meshMessages.collectAsStateWithLifecycle()
    val link by vm.meshLink.collectAsStateWithLifecycle()
    val node = nodes.firstOrNull { it.num == num }

    var draft by remember { mutableStateOf("") }
    val connected = link == MeshLinkState.CONNECTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(node?.name?.ifBlank { null } ?: "Node ${num ?: "?"}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (connected) "Message the mesh…" else "Connect a mesh radio to send") },
                        enabled = connected,
                        singleLine = true,
                    )
                    IconButton(
                        onClick = {
                            val t = draft.trim()
                            if (t.isNotEmpty()) { vm.sendMeshText(t); draft = "" }
                        },
                        enabled = connected && draft.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("header") { NodeHeader(node, num) }
            item("telemetry") { if (node != null) TelemetryTiles(node) }
            item("msg-head") {
                Text(
                    "MESH MESSAGES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (messages.isEmpty()) {
                item("msg-empty") {
                    Text(
                        if (connected) "No messages yet." else "Not connected to a mesh radio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            } else {
                items(messages.takeLast(40).reversed()) { m -> MessageRow(m.text, m.mine) }
            }
        }
    }
}

@Composable
private fun NodeHeader(node: MeshNode?, num: Long?) {
    Card(
        Modifier.fillMaxWidth(),
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = MaterialTheme.colorScheme.secondary
            Surface(shape = CircleShape, color = tint.copy(alpha = 0.15f), modifier = Modifier.padding(0.dp)) {
                Box(Modifier.padding(10.dp)) { Icon(Icons.Default.Router, contentDescription = null, tint = tint) }
            }
            Column {
                Text(node?.name?.ifBlank { null } ?: "Node ${num ?: "?"}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "#${node?.num ?: num ?: "?"}" + (node?.hopsAway?.let {
                        "  ·  " + if (it == 0) "direct neighbour" else "$it hop${if (it == 1) "" else "s"} away"
                    } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TelemetryTiles(node: MeshNode) {
    val la = node.lat; val lo = node.lon
    val gps = if (la != null && lo != null) "%.5f, %.5f".format(la, lo) else "no fix"
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile("Battery", node.battery?.let { "$it%" } ?: "—")
        Tile("Voltage", node.voltage?.let { "%.2f V".format(it) } ?: "—")
        Tile("SNR", node.snr?.let { "%.1f dB".format(it) } ?: "—")
        Tile("Hops", node.hopsAway?.let { if (it == 0) "direct" else "$it" } ?: "—")
        Tile("Last heard", relTime(node.lastHeardSec))
        Tile("Uptime", fmtUptime(node.uptimeSec))
        Tile("Channel util", node.channelUtil?.let { "%.1f%%".format(it) } ?: "—")
        Tile("Air util TX", node.airUtilTx?.let { "%.1f%%".format(it) } ?: "—")
        Tile("GPS", gps)
    }
}

@Composable
private fun Tile(label: String, value: String) {
    Card(
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MessageRow(text: String, mine: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
            color = if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.surface,
        ) {
            Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun relTime(sec: Long?): String {
    if (sec == null || sec <= 0) return "—"
    val s = (System.currentTimeMillis() / 1000) - sec
    return when {
        s < 0 -> "now"
        s < 60 -> "${s}s ago"
        s < 3600 -> "${s / 60}m ago"
        s < 86400 -> "${s / 3600}h ago"
        else -> "${s / 86400}d ago"
    }
}

private fun fmtUptime(sec: Long?): String {
    if (sec == null || sec <= 0) return "—"
    val d = sec / 86400; val h = (sec % 86400) / 3600; val m = (sec % 3600) / 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}
