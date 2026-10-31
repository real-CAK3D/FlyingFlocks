package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.model.BiscuitDevice
import com.cak3d.biscuitmesh.model.ConnectionState

@Composable
fun FleetScreen(vm: MainViewModel) {
    val devicesFlow = vm.devices
    if (devicesFlow == null) {
        BluetoothUnavailable()
        return
    }
    val devices by devicesFlow.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()

    val list = devices.values.sortedWith(
        compareByDescending<BiscuitDevice> { it.state == ConnectionState.READY }
            .thenByDescending { it.rssi },
    )
    val connected = devices.values.count { it.state == ConnectionState.READY }

    Scaffold(
        topBar = {
            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
            TopAppBar(title = {
                Column {
                    Text("Biscuit Mesh")
                    Text(
                        "$connected connected · ${devices.size} seen",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.toggleScan() },
                icon = {
                    Icon(
                        if (scanning) Icons.Default.Stop else Icons.Default.BluetoothSearching,
                        contentDescription = null,
                    )
                },
                text = { Text(if (scanning) "Stop" else "Scan") },
            )
        },
    ) { padding ->
        if (list.isEmpty()) {
            EmptyState(Modifier.padding(padding), scanning)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.address }) { device ->
                    DeviceCard(
                        device = device,
                        onConnect = { vm.connect(device.address) },
                        onDisconnect = { vm.disconnect(device.address) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: BiscuitDevice,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(device.displayName, style = MaterialTheme.typography.titleMedium)
                StatePill(device.state)
            }
            Text(
                device.address,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            val meta = buildList {
                device.model?.let { add(it) }
                device.firmware?.let { add("fw $it") }
                add("${device.rssi} dBm")
                device.status?.battery?.let { add("$it%") }
            }.joinToString("  ·  ")
            Text(meta, style = MaterialTheme.typography.bodyMedium)
            device.lastError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (device.state) {
                    ConnectionState.READY, ConnectionState.CONNECTING, ConnectionState.PROVISIONING ->
                        OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                    else ->
                        OutlinedButton(onClick = onConnect) { Text("Connect") }
                }
            }
        }
    }
}

@Composable
private fun StatePill(state: ConnectionState) {
    val (label, color) = when (state) {
        ConnectionState.READY -> "READY" to MaterialTheme.colorScheme.primary
        ConnectionState.CONNECTING -> "CONNECTING" to MaterialTheme.colorScheme.secondary
        ConnectionState.PROVISIONING -> "SETUP" to MaterialTheme.colorScheme.secondary
        ConnectionState.DISCOVERED -> "SEEN" to MaterialTheme.colorScheme.onSurface
        ConnectionState.DISCONNECTED -> "OFFLINE" to MaterialTheme.colorScheme.onSurface
        ConnectionState.FAILED -> "FAILED" to MaterialTheme.colorScheme.error
    }
    Text(label, style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
private fun EmptyState(modifier: Modifier, scanning: Boolean) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (scanning) "Scanning for Biscuit boards…" else "No boards yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (scanning) "Make sure a board is powered and advertising."
            else "Tap Scan to look for nearby boards.",
            style = MaterialTheme.typography.bodyMedium,
        )
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
