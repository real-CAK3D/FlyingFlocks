package com.cak3d.biscuitmesh.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.BuildConfig
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.data.DeadZone
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit, onOpenDeadZones: () -> Unit = {}, onOpenWigle: () -> Unit = {}) {
    val zones by vm.deadZones.collectAsStateWithLifecycle(initialValue = emptyList())
    val authorized by vm.authorizedUse.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    // Request location at the screen level, not inside the dialog: a permission
    // prompt that pops over the dialog dismisses it, so we settle permission
    // first and then open the dialog (manual entry is always possible either way).
    val context = LocalContext.current
    fun hasLocation() = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    var locGranted by remember { mutableStateOf(hasLocation()) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        locGranted = granted || hasLocation()
        showAdd = true
    }
    fun openAddZone() {
        if (locGranted) showAdd = true else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    HelpButton(
                        "Settings",
                        "Where you tune how the app works — units, appearance, collecting options, dead " +
                            "zones and more. Changes take effect right away.",
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val tabs = listOf("General", "Privacy", "Devices", "Data", "About")
            var tab by remember { mutableStateOf(0) }
            ScrollableTabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
                edgePadding = 12.dp,
            ) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i },
                        text = { Text(t, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) })
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (tab) {
                    0 -> {
                        AppearanceSection(vm)
                        HudSection(vm)
                        AlertsScreenSection(vm)
                    }
                    1 -> {
                        DeadZonesSection(
                            zones = zones,
                            onAdd = { openAddZone() },
                            onRemove = { vm.removeDeadZone(it) },
                            onSetRadius = { id, r -> vm.setDeadZoneRadius(id, r) },
                        )
                        androidx.compose.material3.OutlinedButton(
                            onClick = onOpenDeadZones, modifier = Modifier.fillMaxWidth(),
                        ) { Text("View dead-zone devices (local only)") }
                        AuthorizedUseSection(
                            accepted = authorized,
                            onAccept = { vm.acceptAuthorizedUse() },
                            onRevoke = { vm.revokeAuthorizedUse() },
                        )
                    }
                    2 -> {
                        LocationSourceSection(vm)
                        MeshSection(vm)
                        WtfSection(vm)
                        MarauderSection(vm)
                    }
                    3 -> {
                        ExportSection(vm)
                        WdgSection(vm)
                        androidx.compose.material3.OutlinedButton(
                            onClick = onOpenWigle, modifier = Modifier.fillMaxWidth(),
                        ) { Text("WiGLE account stats") }
                    }
                    4 -> AboutSection()
                }
            }
        }
    }

    if (showAdd) {
        AddDeadZoneDialog(
            vm = vm,
            locationGranted = locGranted,
            onDismiss = { showAdd = false },
            onSave = { name, lat, lon, radius ->
                vm.addDeadZone(name, lat, lon, radius)
                showAdd = false
            },
        )
    }
}

@Composable
private fun AppearanceSection(vm: MainViewModel) {
    val feelName by vm.appFeel.collectAsStateWithLifecycle(initialValue = "ROUNDED")
    val accentName by vm.appAccent.collectAsStateWithLifecycle(initialValue = "GOLD")
    val feel = runCatching { com.cak3d.biscuitmesh.ui.theme.AppFeel.valueOf(feelName) }
        .getOrDefault(com.cak3d.biscuitmesh.ui.theme.AppFeel.ROUNDED)
    val accent = runCatching { com.cak3d.biscuitmesh.ui.theme.AppAccent.valueOf(accentName) }
        .getOrDefault(com.cak3d.biscuitmesh.ui.theme.AppAccent.GOLD)

    SectionCard(title = "Appearance", subtitle = "Pick a feel and an accent colour. Changes apply instantly.") {
        Text("FEEL", style = MaterialTheme.typography.labelMedium, color = accent.color,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.cak3d.biscuitmesh.ui.theme.AppFeel.entries.forEach { f ->
                FeelRow(f, accent, selected = f == feel) { vm.setAppFeel(f.name) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("ACCENT", style = MaterialTheme.typography.labelMedium, color = accent.color,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.cak3d.biscuitmesh.ui.theme.AppAccent.entries.forEach { a ->
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(a.color)
                        .border(
                            width = if (a == accent) 3.dp else 0.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape,
                        )
                        .clickable { vm.setAppAccent(a.name) },
                )
            }
        }
    }
}

@Composable
private fun FeelRow(
    feel: com.cak3d.biscuitmesh.ui.theme.AppFeel,
    accent: com.cak3d.biscuitmesh.ui.theme.AppAccent,
    selected: Boolean,
    onPick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent.color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onPick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Live mini-preview rendered in that feel, so the running light / glow is visible.
        CompositionLocalProvider(
            com.cak3d.biscuitmesh.ui.theme.LocalAppFeel provides feel,
            com.cak3d.biscuitmesh.ui.theme.LocalAppAccent provides accent,
        ) {
            com.cak3d.biscuitmesh.ui.theme.ThemedCard(
                modifier = Modifier.size(54.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {}
        }
        Column(Modifier.weight(1f)) {
            Text(feel.label, style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Text(feel.blurb, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = accent.color)
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            content()
        }
    }
}

@Composable
private fun DeadZonesSection(
    zones: List<DeadZone>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onSetRadius: (String, Double) -> Unit,
) {
    var editing by remember { mutableStateOf<DeadZone?>(null) }
    SectionCard(
        title = "Scanning dead zones",
        subtitle = "Inside these areas the fleet stops scanning and nothing is " +
            "logged — use them to keep home, work, or a friend's place out of your captures. " +
            "Tap the ruler to change how far a zone reaches.",
    ) {
        if (zones.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocationOff, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text("No dead zones yet.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        } else {
            zones.forEach { z ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(z.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "±${z.radiusM.toInt()} m · ${"%.5f".format(z.lat)}, ${"%.5f".format(z.lon)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    IconButton(onClick = { editing = z }) {
                        Icon(Icons.Default.Straighten, contentDescription = "Change radius of ${z.name}",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onRemove(z.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${z.name}",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add dead zone")
        }
    }

    editing?.let { z ->
        RadiusDialog(
            title = z.name,
            initialM = z.radiusM,
            onDismiss = { editing = null },
            onSave = { onSetRadius(z.id, it); editing = null },
        )
    }
}

/** Slider + quick presets for setting a dead-zone radius in metres. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RadiusDialog(title: String, initialM: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var meters by remember { mutableStateOf(initialM.toFloat().coerceIn(20f, 2000f)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Radius · $title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${meters.toInt()} m", style = MaterialTheme.typography.headlineSmall)
                Slider(value = meters, onValueChange = { meters = it }, valueRange = 20f..2000f)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(100, 250, 500, 1000, 2000).forEach { preset ->
                        FilterChip(selected = meters.toInt() == preset, onClick = { meters = preset.toFloat() },
                            label = { Text("$preset m") })
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(meters.toDouble()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AlertsScreenSection(vm: MainViewModel) {
    val awake by vm.keepAwake.collectAsStateWithLifecycle()
    val vibrate by vm.vibrateAlerts.collectAsStateWithLifecycle()
    SectionCard(
        title = "Alerts & screen",
        subtitle = "How the app behaves while you're driving.",
    ) {
        ToggleRow("Keep screen on", "Stay awake everywhere, not just the map", awake) { vm.setKeepAwake(it) }
        ToggleRow("Vibrate on alert", "Buzz when a camera is close", vibrate) { vm.setVibrateAlerts(it) }
    }
}

@Composable
private fun ExportSection(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun writeTo(uri: android.net.Uri?, text: String, label: String) {
        if (uri == null) return
        scope.launch {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }.isSuccess
            android.widget.Toast.makeText(
                context,
                if (ok) "Exported $label" else "Export failed",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        writeTo(it, vm.camerasCsv(), "cameras CSV")
    }
    val kmlPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml")
    ) { writeTo(it, vm.camerasKml(), "cameras KML") }

    SectionCard(
        title = "Export",
        subtitle = "Save the cameras on your map to a file — CSV for spreadsheets, " +
            "KML to open in Google Earth or Maps.",
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { csvPicker.launch("flyingflocks-cameras.csv") }, modifier = Modifier.weight(1f)) {
                Text("Cameras CSV")
            }
            OutlinedButton(onClick = { kmlPicker.launch("flyingflocks-cameras.kml") }, modifier = Modifier.weight(1f)) {
                Text("Cameras KML")
            }
        }
    }
}

@Composable
private fun LocationSourceSection(vm: MainViewModel) {
    val boardGps by vm.boardGps.collectAsStateWithLifecycle(initialValue = false)
    SectionCard(
        title = "Location source",
        subtitle = "The map uses your phone's GPS by default. Turn this on to wardrive off a " +
            "connected board's GPS instead — your Meshtastic node's position (or the dongle's, once " +
            "its firmware reports coordinates). The phone fix is always kept as a fallback.",
    ) {
        ToggleRow(
            "Use board GPS when available",
            if (boardGps) "Meshtastic node / dongle GPS feeds the map" else "Phone GPS",
            boardGps,
        ) { vm.setBoardGps(it) }
    }
}

@Composable
private fun MeshSection(vm: MainViewModel) {
    val link by vm.meshLink.collectAsStateWithLifecycle()
    val nodes by vm.meshNodes.collectAsStateWithLifecycle()
    val connected = link == com.cak3d.biscuitmesh.mesh.MeshLinkState.CONNECTED
    val busy = link == com.cak3d.biscuitmesh.mesh.MeshLinkState.SCANNING ||
        link == com.cak3d.biscuitmesh.mesh.MeshLinkState.CONNECTING
    SectionCard(
        title = "Meshtastic",
        subtitle = "Connect to one of your Meshtastic nodes over Bluetooth to show the mesh " +
            "on the map. Disconnect the node in the Meshtastic app first (only one app can " +
            "hold it), and allow pairing if it asks for a PIN.",
    ) {
        val status = when (link) {
            com.cak3d.biscuitmesh.mesh.MeshLinkState.CONNECTED -> "Connected · ${nodes.size} nodes"
            com.cak3d.biscuitmesh.mesh.MeshLinkState.SCANNING -> "Scanning for a node…"
            com.cak3d.biscuitmesh.mesh.MeshLinkState.CONNECTING -> "Connecting…"
            else -> "Not connected"
        }
        Text(status, style = MaterialTheme.typography.bodyMedium)
        if (connected) {
            OutlinedButton(onClick = { vm.disconnectMesh() }, modifier = Modifier.fillMaxWidth()) { Text("Disconnect") }
        } else {
            Button(onClick = { vm.connectMesh() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Connect to mesh")
            }
        }
    }
}

@Composable
private fun WtfSection(vm: MainViewModel) {
    val link by vm.wtfLink.collectAsStateWithLifecycle()
    val name by vm.wtfDeviceName.collectAsStateWithLifecycle()
    val sourceOn by vm.wtfSourceEnabled.collectAsStateWithLifecycle()
    val importResult by vm.importResult.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Fire a toast when an import finishes, then clear it so it shows only once.
    androidx.compose.runtime.LaunchedEffect(importResult) {
        importResult?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            vm.clearImportResult()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text != null) vm.importWtfBackup(text)
            else android.widget.Toast.makeText(context, "Couldn't read that file", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val connected = link == com.cak3d.biscuitmesh.usb.WtfLinkState.CONNECTED
    val connecting = link == com.cak3d.biscuitmesh.usb.WtfLinkState.CONNECTING

    SectionCard(
        title = "What the Flock dongle",
        subtitle = "Plug a What the Flock XIAO into the phone (USB-OTG) and its camera " +
            "sightings drop onto the map as Flock/ALPR hazards. Or import a backup JSON " +
            "the What the Flock app exported to put its saved locations on the map.",
    ) {
        val status = when {
            connected -> name ?: "Connected"
            connecting -> "Connecting…"
            else -> "Not connected"
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                if (connected) Icons.Default.CheckCircle else Icons.Default.Usb,
                contentDescription = null,
                tint = if (connected) Color(0xFF3DDC84) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }

        if (connected) {
            val gps by vm.wtfGps.collectAsStateWithLifecycle()
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.refreshWtfGps() }, modifier = Modifier.weight(1f)) {
                    Text("Check GPS")
                }
                OutlinedButton(onClick = { vm.pullWtfSaved() }, modifier = Modifier.weight(1f)) {
                    Text("Pull saved")
                }
                OutlinedButton(onClick = { vm.disconnectWtf() }, modifier = Modifier.weight(1f)) {
                    Text("Disconnect")
                }
            }
            OutlinedButton(onClick = { vm.loadWtfSdLog() }, modifier = Modifier.fillMaxWidth()) {
                Text("Load board log (SD) to map")
            }
        } else {
            Button(onClick = { vm.connectWtf() }, enabled = !connecting, modifier = Modifier.fillMaxWidth()) {
                Text("Connect dongle")
            }
        }

        ToggleRow("Plot dongle sightings", "Add live detections to the map", sourceOn) { vm.setWtfSourceEnabled(it) }

        OutlinedButton(
            onClick = { picker.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Import What the Flock backup")
        }
    }
}

@Composable
private fun MarauderSection(vm: MainViewModel) {
    val link by vm.marauderLink.collectAsStateWithLifecycle()
    val name by vm.marauderDeviceName.collectAsStateWithLifecycle()
    val sourceOn by vm.marauderSourceEnabled.collectAsStateWithLifecycle()

    val connected = link == com.cak3d.biscuitmesh.usb.WtfLinkState.CONNECTED
    val connecting = link == com.cak3d.biscuitmesh.usb.WtfLinkState.CONNECTING

    SectionCard(
        title = "ESP Marauder",
        subtitle = "Plug an ESP Marauder into the phone (USB-OTG). While a scan is running, " +
            "the Wi-Fi and Bluetooth devices it sees drop onto your map, tagged with the " +
            "phone's GPS. Works with CP210x and CH340 dev-kits and native-USB boards.",
    ) {
        val status = when {
            connected -> name ?: "Connected"
            connecting -> "Connecting…"
            else -> "Not connected"
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                if (connected) Icons.Default.CheckCircle else Icons.Default.Usb,
                contentDescription = null,
                tint = if (connected) Color(0xFF3DDC84) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }

        if (connected) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.rescanMarauder() }, modifier = Modifier.weight(1f)) {
                    Text("Scan")
                }
                OutlinedButton(onClick = { vm.stopMarauderScan() }, modifier = Modifier.weight(1f)) {
                    Text("Stop scan")
                }
                OutlinedButton(onClick = { vm.disconnectMarauder() }, modifier = Modifier.weight(1f)) {
                    Text("Disconnect")
                }
            }
            Text(
                "Start a wardrive to record what the Marauder finds — hits only land on the map " +
                    "while a scan session is active.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        } else {
            Button(onClick = { vm.connectMarauder() }, enabled = !connecting, modifier = Modifier.fillMaxWidth()) {
                Text("Connect Marauder")
            }
        }

        ToggleRow("Plot Marauder sightings", "Add its live detections to the map", sourceOn) {
            vm.setMarauderSourceEnabled(it)
        }
    }
}

@Composable
private fun HudSection(vm: MainViewModel) {
    val hud by vm.hudEnabled.collectAsStateWithLifecycle()
    val mph by vm.useMph.collectAsStateWithLifecycle()
    val voice by vm.voiceAlerts.collectAsStateWithLifecycle()
    SectionCard(
        title = "Driving HUD & cameras",
        subtitle = "Shows your speed and the posted limit while driving, and warns you when a " +
            "speed or Flock/ALPR camera is close and you're over the limit. Camera + limit data " +
            "is OpenStreetMap (DeFlock), free under ODbL.",
    ) {
        ToggleRow("Speed HUD", "Overlay speed + limit on the map", hud) { vm.setHudEnabled(it) }
        ToggleRow("Use mph", if (mph) "Miles per hour" else "Kilometres per hour", mph) { vm.setUseMph(it) }
        ToggleRow("Voice alerts", "Speak camera warnings and turn-by-turn directions", voice) { vm.setVoiceAlerts(it) }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun WdgSection(vm: MainViewModel) {
    val savedKey by vm.wdgApiKey.collectAsStateWithLifecycle()
    val autoUpload by vm.wdgAutoUpload.collectAsStateWithLifecycle()
    val status by vm.wdgStatus.collectAsStateWithLifecycle()
    val lastUpload by vm.wdgLastUpload.collectAsStateWithLifecycle()

    var keyField by remember(savedKey) { mutableStateOf(savedKey) }
    val working = status is MainViewModel.WdgStatus.Working

    SectionCard(
        title = "WDG Wars feeder",
        subtitle = "Upload your Wi-Fi/BLE captures to the WDGWars leaderboard. Create an " +
            "API key in your profile at wdgwars.pl and paste it here. Access points and " +
            "Bluetooth devices are sent; dead zones and client stations are never uploaded.",
    ) {
        OutlinedTextField(
            value = keyField,
            onValueChange = { keyField = it.trim() },
            label = { Text("API key (64-char hex)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.setWdgApiKey(keyField) },
                enabled = keyField != savedKey && keyField.isNotBlank(),
            ) { Text("Save key") }
            OutlinedButton(
                onClick = { vm.validateWdgKey(keyField) },
                enabled = keyField.isNotBlank() && !working,
            ) { Text("Test key") }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Auto-upload on stop", style = MaterialTheme.typography.bodyLarge)
                Text("Send the capture to WDGWars each time a wardrive ends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Switch(
                checked = autoUpload,
                onCheckedChange = { vm.setWdgAutoUpload(it) },
                enabled = savedKey.isNotBlank(),
            )
        }
        Text(
            if (lastUpload.isBlank()) "Last upload: never" else "Last upload: $lastUpload",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        when (val s = status) {
            is MainViewModel.WdgStatus.Idle -> {}
            is MainViewModel.WdgStatus.Working ->
                Text(s.message, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary)
            is MainViewModel.WdgStatus.Done ->
                Text(s.message, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            is MainViewModel.WdgStatus.Error ->
                Text(s.message, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AuthorizedUseSection(accepted: Boolean, onAccept: () -> Unit, onRevoke: () -> Unit) {
    SectionCard(
        title = "Authorized use",
        subtitle = "Offensive tools (deauth, beacon spam, BLE spam, portals) stay locked " +
            "behind an acknowledgment that you only run them on networks and devices you own " +
            "or are authorized to test.",
    ) {
        val green = Color(0xFF2E7D32)
        if (accepted) {
            // Once acknowledged, show it green with a check so the state is obvious.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = green)
                Spacer(Modifier.width(8.dp))
                Text("Acknowledged", style = MaterialTheme.typography.bodyMedium, color = green)
            }
            Button(
                onClick = onAccept,
                enabled = false,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = green,
                    disabledContentColor = Color.White,
                ),
            ) { Text("Acknowledged") }
            TextButton(onClick = onRevoke) { Text("Revoke acknowledgment") }
        } else {
            Text(
                "Status: not acknowledged",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onAccept) { Text("Acknowledge now") }
        }
    }
}

@Composable
private fun AboutSection() {
    SectionCard(title = "About") {
        Text("Flying Flocks ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodyMedium)
        Text(
            "A fleet-first wardriver for ESP32 Biscuit boards — or the phone alone. " +
                "Scans Wi-Fi + Bluetooth, maps and feeds WDG Wars.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            "Map, camera & speed-limit data © OpenStreetMap contributors (ODbL).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddDeadZoneDialog(
    vm: MainViewModel,
    locationGranted: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, lat: Double, lon: Double, radius: Double) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("150") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun fetchLocation() {
        locating = true
        status = "Getting a GPS fix…"
        scope.launch {
            val fix = vm.oneShotLocation()
            locating = false
            if (fix != null) {
                lat = "%.6f".format(fix.lat)
                lon = "%.6f".format(fix.lon)
                status = "Captured current location."
            } else {
                status = "Couldn't get a fix — enter coordinates manually."
            }
        }
    }

    val latD = lat.toDoubleOrNull()
    val lonD = lon.toDoubleOrNull()
    val radiusD = radius.toDoubleOrNull()
    val valid = latD != null && lonD != null && radiusD != null &&
        latD in -90.0..90.0 && lonD in -180.0..180.0 && radiusD > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add dead zone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name (e.g. Home)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = radius, onValueChange = { radius = it.filter { c -> c.isDigit() } },
                    label = { Text("Radius (metres)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(100, 250, 500, 1000).forEach { preset ->
                        FilterChip(selected = radius == preset.toString(), onClick = { radius = preset.toString() },
                            label = { Text("$preset m") })
                    }
                }
                OutlinedButton(
                    onClick = { fetchLocation() },
                    enabled = !locating && locationGranted,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (locating) "Locating…" else "Use my current location")
                }
                if (!locationGranted) {
                    Text(
                        "Location permission denied — enter coordinates manually.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lat, onValueChange = { lat = it }, label = { Text("Latitude") },
                        singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = lon, onValueChange = { lon = it }, label = { Text("Longitude") },
                        singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                status?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, latD!!, lonD!!, radiusD!!) },
                enabled = valid,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
