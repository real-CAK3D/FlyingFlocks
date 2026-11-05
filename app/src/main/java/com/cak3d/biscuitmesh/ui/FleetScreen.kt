package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.R
import com.cak3d.biscuitmesh.model.BiscuitDevice
import com.cak3d.biscuitmesh.model.ConnectionState
import com.cak3d.biscuitmesh.usb.UsbDeviceInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetScreen(vm: MainViewModel, onOpen: (String) -> Unit = {}) {
    val devicesFlow = vm.devices
    if (devicesFlow == null) {
        BluetoothUnavailable()
        return
    }
    val devices by devicesFlow.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val usb by vm.usbDevices.collectAsStateWithLifecycle(initialValue = emptyList())

    // Scan the whole time the Connect page is open, so a powered board nearby
    // moves to "In range" on its own instead of sitting at "out of range" until
    // you remember to tap Scan. Stops when you leave, to spare the battery.
    DisposableEffect(Unit) {
        vm.startScan()
        onDispose { vm.stopScan() }
    }

    val all = devices.values
    val connected = all.filter { it.isLive }.sortedByDescending { it.rssi }
    val inRange = all.filter { !it.isLive && it.inRange }.sortedByDescending { it.rssi }
    val saved = all.filter { !it.isLive && !it.inRange && it.known }.sortedBy { it.displayName }

    val liveCount = connected.count { it.state == ConnectionState.READY }
    val connectableInRange = inRange.count()

    var renaming by remember { mutableStateOf<BiscuitDevice?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (all.isEmpty()) {
            EmptyState(Modifier.fillMaxSize(), scanning, usb)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item("header") {
                    Text(
                        "$liveCount LIVE · ${all.size} KNOWN".let { "> $it" },
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (usb.isNotEmpty()) item("usb") { UsbBanner(usb) }
                if (connectableInRange > 0 || connected.isNotEmpty()) {
                    item {
                        BroadcastBar(
                            connectableInRange = connectableInRange,
                            liveCount = connected.size,
                            onConnectAll = { vm.connectAll() },
                            onDisconnectAll = { vm.disconnectAll() },
                        )
                    }
                }
                section("Connected", connected, vm, onOpen) { renaming = it }
                section("In range", inRange, vm, onOpen) { renaming = it }
                section("Saved · out of range", saved, vm, onOpen) { renaming = it }
            }
        }
    }

    renaming?.let { RenameDialog(it, vm) { renaming = null } }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    devices: List<BiscuitDevice>,
    vm: MainViewModel,
    onOpen: (String) -> Unit,
    onRename: (BiscuitDevice) -> Unit,
) {
    if (devices.isEmpty()) return
    item(key = "header-$title") {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        )
    }
    items(devices, key = { it.address }) { device ->
        DeviceCard(
            device = device,
            onOpen = { onOpen(device.address) },
            onConnect = { vm.connect(device.address) },
            onDisconnect = { vm.disconnect(device.address) },
            onForget = { vm.forget(device.address) },
            onRename = { onRename(device) },
        )
    }
}

@Composable
private fun BroadcastBar(
    connectableInRange: Int,
    liveCount: Int,
    onConnectAll: () -> Unit,
    onDisconnectAll: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (connectableInRange > 0) {
            FilledTonalButton(onClick = onConnectAll, modifier = Modifier.weight(1f)) {
                Text("Connect all ($connectableInRange)")
            }
        }
        if (liveCount > 0) {
            OutlinedButton(onClick = onDisconnectAll, modifier = Modifier.weight(1f)) {
                Text("Disconnect all")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceCard(
    device: BiscuitDevice,
    onOpen: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
    onRename: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BoardBadge(device.state)
                Column(Modifier.weight(1f)) {
                    Text(
                        device.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        device.address,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                OverflowMenu(device = device, onForget = onForget, onRename = onRename)
            }

            val meta = buildList {
                device.model?.let { add(it) }
                device.firmware?.let { add("fw $it") }
                if (device.inRange || device.isLive) add("${device.rssi} dBm")
                device.status?.battery?.let { add("$it%") }
            }.joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodyMedium)
            }

            device.lastError?.takeIf { device.state == ConnectionState.FAILED }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(device)
                if (device.isLive) {
                    OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                } else {
                    FilledTonalButton(onClick = onConnect) { Text("Connect") }
                }
            }
        }
    }
}

@Composable
private fun OverflowMenu(device: BiscuitDevice, onForget: () -> Unit, onRename: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { open = false; onRename() })
            if (device.known) {
                DropdownMenuItem(text = { Text("Forget") }, onClick = { open = false; onForget() })
            }
        }
    }
}

@Composable
private fun BoardBadge(state: ConnectionState) {
    val tint = pillColor(state)
    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = 0.15f),
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.DeveloperBoard,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun StatusPill(device: BiscuitDevice) {
    val color = pillColor(device.state, device.inRange, device.known)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(8.dp)) {}
        Text(device.statusLabel(), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun pillColor(
    state: ConnectionState,
    inRange: Boolean = false,
    known: Boolean = false,
): Color = when (state) {
    ConnectionState.READY -> MaterialTheme.colorScheme.primary
    ConnectionState.CONNECTING, ConnectionState.PROVISIONING -> MaterialTheme.colorScheme.secondary
    ConnectionState.FAILED -> MaterialTheme.colorScheme.error
    else -> if (inRange) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
}

@Composable
private fun RenameDialog(device: BiscuitDevice, vm: MainViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(device.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename board") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) vm.rename(device.address, text.trim())
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EmptyState(modifier: Modifier, scanning: Boolean, usb: List<UsbDeviceInfo>) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(140.dp),
        )
        Text(
            if (scanning) "Scanning for Biscuit boards…" else "No boards yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (scanning) "Make sure a board is powered and advertising."
            else "Tap Scan to add boards — or just head to Wardrive and scan with the phone's own Wi-Fi + BLE.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (usb.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            UsbBanner(usb)
        }
    }
}

@Composable
private fun UsbBanner(usb: List<UsbDeviceInfo>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Usb, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            val first = usb.first()
            Column(Modifier.weight(1f)) {
                Text(
                    if (usb.size == 1) first.name else "${usb.size} USB devices",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    if (first.serialCapable) "${first.kind} — board-over-USB transport coming soon"
                    else "${first.kind} detected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun BluetoothUnavailable() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Bluetooth unavailable", style = MaterialTheme.typography.titleMedium)
        Text(
            "This device has no Bluetooth LE adapter, so the fleet can't connect.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
