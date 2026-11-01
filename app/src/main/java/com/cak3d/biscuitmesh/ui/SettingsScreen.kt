package com.cak3d.biscuitmesh.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
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
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DeadZonesSection(
                zones = zones,
                onAdd = { openAddZone() },
                onRemove = { vm.removeDeadZone(it) },
            )
            WdgSection(vm)
            AuthorizedUseSection(
                accepted = authorized,
                onAccept = { vm.acceptAuthorizedUse() },
                onRevoke = { vm.revokeAuthorizedUse() },
            )
            AboutSection()
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
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
private fun DeadZonesSection(zones: List<DeadZone>, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    SectionCard(
        title = "Scanning dead zones",
        subtitle = "Inside these areas the fleet stops scanning and nothing is " +
            "logged — use them to keep home, work, or a friend's place out of your captures.",
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
}

@Composable
private fun WdgSection(vm: MainViewModel) {
    val savedKey by vm.wdgApiKey.collectAsStateWithLifecycle()
    val autoUpload by vm.wdgAutoUpload.collectAsStateWithLifecycle()
    val status by vm.wdgStatus.collectAsStateWithLifecycle()

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
        Text(
            if (accepted) "Status: acknowledged" else "Status: not acknowledged",
            style = MaterialTheme.typography.bodyMedium,
            color = if (accepted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        if (accepted) {
            OutlinedButton(onClick = onRevoke) { Text("Revoke acknowledgment") }
        } else {
            OutlinedButton(onClick = onAccept) { Text("Acknowledge now") }
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
    }
}

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
