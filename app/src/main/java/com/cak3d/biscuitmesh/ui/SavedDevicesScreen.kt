package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.model.LogEntry
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import kotlinx.coroutines.flow.MutableStateFlow

private val RF_AMBER = androidx.compose.ui.graphics.Color(0xFFFF8A3D)
private val BTN_PRESETS = listOf("On", "Off", "Up", "Down", "Open", "Close", "Stop", "Unlock", "Lock")

private data class SavedDevice(val name: String, val buttons: List<String>)

/**
 * Saved sub-GHz devices: a named device (Garage, Lamp…) holds labeled buttons (On, Off…),
 * each a captured .sub on the node. Add a button → the node auto-detects the band, rejects
 * noise and saves it. Tap a saved button → it re-transmits (own-device use only).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SavedDevicesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val logFlow = vm.log ?: remember { MutableStateFlow(emptyList<LogEntry>()) }
    val log by logFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Latest DATA:DEV:<device>|<b1>,<b2> per device name, from the most recent snapshot
    // (a DATA:DEVCLR marks the start of a fresh inventory, so deleted devices drop off).
    val devices = remember(log) {
        val clrIdx = log.indexOfLast { it.message.type == BiscuitMessage.Type.DATA && it.message.subtype == "DEVCLR" }
        val scope = if (clrIdx >= 0) log.subList(clrIdx, log.size) else log
        scope.asSequence()
            .filter { it.message.type == BiscuitMessage.Type.DATA && it.message.subtype == "DEV" }
            .mapNotNull { e ->
                val bar = e.message.payload.indexOf('|')
                if (bar < 0) return@mapNotNull null
                val name = e.message.payload.substring(0, bar).trim()
                if (name.isEmpty()) return@mapNotNull null
                val btns = e.message.payload.substring(bar + 1).split(",").map { it.trim() }.filter { it.isNotEmpty() }
                Triple(name, btns, e.timeMs)
            }
            .groupBy { it.first }
            .map { (_, v) -> v.maxByOrNull { it.third }!! }
            .map { SavedDevice(it.first, it.second) }
            .sortedBy { it.name.lowercase() }
    }

    // Latest DATA:SUBCAP line → a friendly status while capturing.
    val capStatus = remember(log) {
        log.asSequence()
            .filter { it.message.type == BiscuitMessage.Type.DATA && it.message.subtype == "SUBCAP" }
            .lastOrNull()?.message?.payload
    }
    val savedCount = remember(log) {
        log.count { it.message.type == BiscuitMessage.Type.DATA && it.message.subtype == "SUBCAP" && it.message.payload.startsWith("saved") }
    }

    // Refresh the inventory on open and whenever a new capture is saved.
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.requestSavedDevices() }
    androidx.compose.runtime.LaunchedEffect(savedCount) { if (savedCount > 0) vm.requestSavedDevices() }

    var showAdd by remember { mutableStateOf(false) }
    var addDeviceName by remember { mutableStateOf("") }
    var playConfirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleteConfirm by remember { mutableStateOf<Pair<String, String?>?>(null) }  // device to (button | null=whole device)

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Icon(Icons.Default.SettingsRemote, contentDescription = null, tint = RF_AMBER)
            Spacer(Modifier.width(8.dp))
            Text("Saved Devices", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.requestSavedDevices() }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
        }
        Text(
            "A device holds labeled buttons. Adding one captures it on the node — it auto-detects the band (315/433) and only saves a real signal.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        capStatus?.let { st -> CapStatusBanner(st) }

        Button(onClick = { addDeviceName = ""; showAdd = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Add device / button")
        }
        Spacer(Modifier.height(10.dp))

        if (devices.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "No saved devices yet. Tap “Add device / button”, name the device (e.g. Garage), pick a button " +
                        "(On / Off…), then hold your remote’s button when the node says it’s listening.",
                    Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            devices.forEach { dev ->
                DeviceCard(
                    device = dev,
                    onFire = { btn -> playConfirm = dev.name to btn },
                    onAddButton = { addDeviceName = dev.name; showAdd = true },
                    onDeleteButton = { btn -> deleteConfirm = dev.name to btn },
                    onDeleteDevice = { deleteConfirm = dev.name to null },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showAdd) {
        AddButtonDialog(
            initialDevice = addDeviceName,
            existingDevices = devices.map { it.name },
            onDismiss = { showAdd = false },
            onCapture = { dev, btn -> vm.addDeviceButton(dev, btn); showAdd = false },
        )
    }

    deleteConfirm?.let { (dev, btn) ->
        AlertDialog(
            onDismissRequest = { deleteConfirm = null },
            title = { Text(if (btn == null) "Delete “$dev”?" else "Delete “$btn”?") },
            text = {
                Text(if (btn == null) "This removes the device “$dev” and all its saved buttons from the node."
                     else "This removes the “$btn” button from “$dev”.")
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteDeviceButton(dev, btn ?: ""); deleteConfirm = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = null }) { Text("Cancel") } },
        )
    }

    playConfirm?.let { (dev, btn) ->
        AlertDialog(
            onDismissRequest = { playConfirm = null },
            title = { Text("Transmit on your own device?") },
            text = {
                Text("This re-transmits the captured “$btn” signal for “$dev”. Only fire this at a device you own " +
                    "and are authorised to operate — never anyone else’s gate, vehicle or property.")
            },
            confirmButton = {
                TextButton(onClick = { vm.playDeviceButton(dev, btn); playConfirm = null }) { Text("I own this — fire") }
            },
            dismissButton = { TextButton(onClick = { playConfirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CapStatusBanner(status: String) {
    val (text, tint) = when {
        status.startsWith("saved") -> {
            val p = status.split(",")
            val freq = p.getOrNull(3) ?: "?"
            val edges = p.getOrNull(2) ?: "?"
            "Saved ✓  ($freq MHz, $edges edges)" to androidx.compose.ui.graphics.Color(0xFF4CAF50)
        }
        status.startsWith("nothing") -> "No clean signal — hold the button and retry" to androidx.compose.ui.graphics.Color(0xFFE53935)
        status.startsWith("listening") -> "Listening… point the remote and HOLD the button" to RF_AMBER
        status.startsWith("probe") -> { val p = status.split(","); "Probing ${p.getOrNull(1) ?: ""} MHz…" to RF_AMBER }
        status.startsWith("savefail") -> "Save failed — is the SD card in?" to androidx.compose.ui.graphics.Color(0xFFE53935)
        else -> status to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.14f)),
    ) {
        Text(text, Modifier.padding(12.dp), color = tint, fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun DeviceCard(
    device: SavedDevice,
    onFire: (String) -> Unit,
    onAddButton: () -> Unit,
    onDeleteButton: (String) -> Unit,
    onDeleteDevice: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDeleteDevice) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete device",
                        tint = MaterialTheme.colorScheme.error)
                }
            }
            if (device.buttons.isEmpty()) {
                Text("No buttons yet", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                device.buttons.forEach { btn ->
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.combinedClickable(
                            onClick = { onFire(btn) },
                            onLongClick = { onDeleteButton(btn) },
                        ),
                    ) {
                        Text(btn, Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
                    }
                }
                OutlinedButton(onClick = onAddButton) {
                    Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Button")
                }
            }
            if (device.buttons.isNotEmpty()) {
                Text("tap = fire · long-press = delete", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun AddButtonDialog(
    initialDevice: String,
    existingDevices: List<String>,
    onDismiss: () -> Unit,
    onCapture: (String, String) -> Unit,
) {
    var device by remember { mutableStateOf(initialDevice) }
    var button by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a button") },
        text = {
            Column {
                OutlinedTextField(
                    value = device, onValueChange = { device = it },
                    label = { Text("Device name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (existingDevices.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        existingDevices.forEach { d ->
                            AssistChip(onClick = { device = d }, label = { Text(d) })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = button, onValueChange = { button = it },
                    label = { Text("Button label") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BTN_PRESETS.forEach { p ->
                        FilterChip(selected = button == p, onClick = { button = p }, label = { Text(p) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("When you tap Capture, point the remote at the node and HOLD its button until it says saved.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                enabled = device.isNotBlank() && button.isNotBlank(),
                onClick = { onCapture(device.trim(), button.trim()) },
            ) { Text("Capture") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
