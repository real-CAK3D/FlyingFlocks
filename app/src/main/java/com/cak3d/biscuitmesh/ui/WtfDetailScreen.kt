package com.cak3d.biscuitmesh.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.usb.WtfEvent
import com.cak3d.biscuitmesh.usb.WtfLinkState
import kotlinx.coroutines.delay

/**
 * The What the Flock dongle's board page — reached by tapping its card on the
 * Fleet screen, the mesh/Biscuit boards' equivalent. Connect over BLE (or plug
 * in over USB), see its GPS, pull saved sightings, and run the camera QR/barcode
 * scanner from here. A scanned URL is shown in full with an Open button so you
 * check the address first (no auto-open); other text can be copied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WtfDetailScreen(vm: MainViewModel, onBack: () -> Unit) {
    val usbLink by vm.wtfLink.collectAsStateWithLifecycle()
    val bleLink by vm.wtfBleLink.collectAsStateWithLifecycle()
    val usbName by vm.wtfDeviceName.collectAsStateWithLifecycle()
    val bleName by vm.wtfBleDeviceName.collectAsStateWithLifecycle()
    val gps by vm.wtfGps.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val usbConnected = usbLink == WtfLinkState.CONNECTED
    val bleConnected = bleLink == WtfLinkState.CONNECTED
    val bleConnecting = bleLink == WtfLinkState.CONNECTING
    val connected = usbConnected || bleConnected

    var scanResult by remember { mutableStateOf<WtfEvent.Scan?>(null) }
    var scanning by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.wtfScans.collect { scanResult = it; scanning = false } }
    LaunchedEffect(scanning) { if (scanning) { delay(9000); scanning = false } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What the Flock") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Connection status
            Card(
                Modifier.fillMaxWidth(),
                shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (connected) Icons.Default.CheckCircle else Icons.Default.Usb,
                        contentDescription = null,
                        tint = if (connected) Color(0xFF3DDC84) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                    Text(
                        when {
                            usbConnected -> "${usbName ?: "Connected"}  ·  USB"
                            bleConnected -> "${bleName ?: "Connected"}  ·  BLE"
                            bleConnecting -> "Searching over BLE…"
                            else -> "Not connected"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            if (!connected) {
                Button(
                    onClick = { vm.connectWtfBle() },
                    enabled = !bleConnecting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (bleConnecting) "Searching…" else "Connect over BLE")
                }
                Text(
                    "Or plug the dongle into the phone over USB (USB-OTG). Needs the BLE-bridge firmware for wireless.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            } else {
                // GPS
                val gpsText = gps?.let { g ->
                    when {
                        !g.present -> "GPS: no module detected"
                        g.fix -> "GPS: fix · ${g.sats} satellites"
                        else -> "GPS: searching… ${g.sats} satellites"
                    }
                } ?: "GPS: tap Check"
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.MyLocation, contentDescription = null,
                        tint = if (gps?.fix == true) Color(0xFF3DDC84) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(gpsText, style = MaterialTheme.typography.bodyMedium)
                }

                // The camera scan — what this board can do that the others can't.
                Button(
                    onClick = { vm.scanWtf(); scanning = true },
                    enabled = !scanning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (scanning) "Scanning… aim the camera at a code" else "Scan QR / barcode")
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.refreshWtfGps() }, modifier = Modifier.weight(1f)) { Text("Check GPS") }
                    OutlinedButton(onClick = { vm.pullWtfSaved() }, modifier = Modifier.weight(1f)) { Text("Pull saved") }
                }
                if (bleConnected) {
                    OutlinedButton(onClick = { vm.disconnectWtfBle() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Disconnect")
                    }
                }
            }
        }
    }

    scanResult?.let { sc ->
        AlertDialog(
            onDismissRequest = { scanResult = null },
            title = { Text(if (sc.isUrl) "Scanned link" else "Scanned code") },
            text = {
                Column {
                    Text(sc.data, style = MaterialTheme.typography.bodyMedium)
                    if (sc.isUrl) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Opens in your browser — check the address first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            },
            confirmButton = {
                if (sc.isUrl) {
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sc.data))) }
                        scanResult = null
                    }) { Text("Open") }
                } else {
                    TextButton(onClick = {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("scan", sc.data))
                        scanResult = null
                    }) { Text("Copy") }
                }
            },
            dismissButton = { TextButton(onClick = { scanResult = null }) { Text("Close") } },
        )
    }
}
