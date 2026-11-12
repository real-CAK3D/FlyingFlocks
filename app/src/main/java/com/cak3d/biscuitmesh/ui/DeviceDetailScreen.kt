package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.model.BiscuitDevice
import com.cak3d.biscuitmesh.model.ConnectionState
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import com.cak3d.biscuitmesh.protocol.StatusCode

/** Benign, read-only quick commands offered as chips. */
private val QUICK_COMMANDS = listOf(
    "Scan APs" to "scanap",
    "Scan BT" to "sniffbt:type=all",
    "Status" to null,     // handled as a status refresh, not a command
    "Stop" to "stopscan",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeviceDetailScreen(vm: MainViewModel, address: String?, onBack: () -> Unit, onMirror: () -> Unit = {}, onDebug: () -> Unit = {}) {
    val devicesFlow = vm.devices
    val logFlow = vm.log
    if (address == null || devicesFlow == null || logFlow == null) {
        MissingDevice(onBack)
        return
    }
    val devices by devicesFlow.collectAsStateWithLifecycle()
    val log by logFlow.collectAsStateWithLifecycle()
    val device = devices[address]
    if (device == null) {
        MissingDevice(onBack)
        return
    }

    val lines = remember(log, address) { log.filter { it.address == address }.map { it.message } }
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    var input by remember { mutableStateOf("") }
    val canSend = device.state == ConnectionState.READY

    // Link-code gate (T-Embed): the board is "locked" until the 6-digit code shown on
    // its screen is entered. Link state comes from the STATUS "link" field and DATA:LINK.
    val linkMsg = remember(lines) {
        lines.lastOrNull { it.type == BiscuitMessage.Type.DATA && it.subtype == "LINK" }?.payload
    }
    // Freshest link signal wins. DATA:HEALTH streams the live link bit every ~3s, so
    // a stale STATUS "required" (or an old DATA:LINK:need seen mid-scan) must not keep the
    // banner up once the board is actually linked. health.link == "ok" clears it.
    val linked = device.status?.link == "ok" || linkMsg == "ok" || device.health?.link == "ok"
    val needsLink = canSend && !linked && device.health?.link != "ok" &&
        (device.needsLink || linkMsg == "need" || linkMsg == "bad")
    var code by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device.displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    HelpButton(
                        device.displayName,
                        "The live console for one board — its connection status plus a log of what it's " +
                            "doing. Tap a quick command or type your own to send it to the board.",
                    )
                    IconButton(onClick = onMirror, enabled = canSend) {
                        Icon(Icons.Default.Cast, contentDescription = "Mirror / remote control")
                    }
                    IconButton(onClick = onDebug) {
                        Icon(Icons.Default.BugReport, contentDescription = "Debug console")
                    }
                    IconButton(onClick = { vm.refreshStatus(address) }, enabled = canSend) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh status")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            StatusCard(device)

            if (needsLink) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🔒 LINK REQUIRED", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error)
                        Text(
                            "This board is locked. Read the link code shown on its screen and enter it here to link it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = code,
                                onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Link code") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            OutlinedButton(
                                onClick = { vm.send(address, "link:$code"); code = "" },
                                enabled = code.length >= 4,
                            ) { Text("Link") }
                        }
                        if (linkMsg == "bad") {
                            Text("That code didn't match — check the screen and try again.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (device.state) {
                    ConnectionState.READY, ConnectionState.CONNECTING, ConnectionState.PROVISIONING ->
                        OutlinedButton(onClick = { vm.disconnect(address) }) { Text("Disconnect") }
                    else ->
                        OutlinedButton(onClick = { vm.connect(address) }) { Text("Connect") }
                }
            }

            Text(
                "CONSOLE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )

            // Quick command chips (benign: scans + stop + status).
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                QUICK_COMMANDS.forEach { (label, cmd) ->
                    AssistChip(
                        onClick = {
                            if (cmd == null) vm.refreshStatus(address) else vm.send(address, cmd)
                        },
                        label = { Text(label) },
                        enabled = canSend,
                    )
                }
            }

            // Live visualization — animates to whatever scan is running.
            LiveVizPanel(
                lines = lines,
                activeOp = device.status?.activeOp,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF07090C)),
            ) {
                if (lines.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (canSend) "No traffic yet. Send a command below."
                            else "Connect to see live traffic.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(lines.size) { i -> ConsoleLine(lines[i]) }
                    }
                }
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                enabled = canSend,
                singleLine = true,
                prefix = { Text("CMD:", fontFamily = FontFamily.Monospace) },
                placeholder = { Text("scanap  ·  attack:type=deauth,…") },
                trailingIcon = {
                    IconButton(
                        onClick = { vm.send(address, input); input = "" },
                        enabled = canSend && input.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                },
            )
        }
    }
}

@Composable
private fun StatusCard(device: BiscuitDevice) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(device.statusLabel(), style = MaterialTheme.typography.titleMedium, color = stateColor(device))
                Text(
                    "   " + device.address,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            val tiles = buildList {
                device.status?.statusCode?.let { add("State" to StatusCode.from(it).label) }
                device.status?.battery?.let { add("Battery" to "$it%") }
                device.status?.temperatureC?.let { add("Temp" to "%.0f°C".format(it)) }
                device.model?.let { add("Model" to it) }
                device.firmware?.let { add("Firmware" to it) }
                device.status?.sdMounted?.let { add("SD" to if (it) "mounted" else "none") }
                device.status?.activeOp?.let { add("Op" to it) }
                if (device.inRange || device.isLive) add("Signal" to "${device.rssi} dBm")
            }
            if (tiles.isEmpty()) {
                Text(
                    "No status yet — connect and refresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            } else {
                tiles.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth()) {
                        pair.forEach { (k, v) -> StatTile(k, v, Modifier.weight(1f)) }
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ConsoleLine(msg: BiscuitMessage) {
    val color = when (msg.type) {
        BiscuitMessage.Type.ERROR -> MaterialTheme.colorScheme.error
        BiscuitMessage.Type.STATUS -> MaterialTheme.colorScheme.secondary
        BiscuitMessage.Type.RSP -> MaterialTheme.colorScheme.primary
        BiscuitMessage.Type.DATA -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }
    Text(
        msg.raw.trimEnd('\n', '\r'),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = color,
    )
}

@Composable
private fun stateColor(device: BiscuitDevice): Color = when (device.state) {
    ConnectionState.READY -> MaterialTheme.colorScheme.primary
    ConnectionState.CONNECTING, ConnectionState.PROVISIONING -> MaterialTheme.colorScheme.secondary
    ConnectionState.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissingDevice(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Board") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    HelpButton(
                        "Board",
                        "This board is no longer in your fleet, so there's nothing to show. Go back " +
                            "and reconnect it to open its console again.",
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("Board no longer in the fleet.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
