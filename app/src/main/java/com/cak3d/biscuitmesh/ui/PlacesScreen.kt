package com.cak3d.biscuitmesh.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.nav.Place
import com.cak3d.biscuitmesh.nav.PlaceKind
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(vm: MainViewModel, onBack: () -> Unit, onRouteStarted: () -> Unit) {
    val places by vm.places.collectAsStateWithLifecycle(initialValue = emptyList())
    val routeStatus by vm.routeStatus.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    val context = LocalContext.current
    fun hasLocation() = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    var locGranted by remember { mutableStateOf(hasLocation()) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> locGranted = granted || hasLocation(); showAdd = true }
    fun openAdd() { if (locGranted) showAdd = true else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("PLACES") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Places",
                        "Save spots you care about, then start a route to one to be guided there. Add a " +
                            "place from your current location with the button below.",
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            routeStatus?.let { item("status") { Text(it, color = MaterialTheme.colorScheme.secondary) } }
            items(places, key = { it.id }) { place ->
                PlaceRow(
                    place = place,
                    onNavigate = { vm.navigateTo(place); onRouteStarted() },
                    onDirections = { openDirections(context, place) },
                    onRemove = { vm.removePlace(place.id) },
                )
            }
            item("add") {
                OutlinedButton(onClick = { openAdd() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add place")
                }
            }
            if (places.isEmpty()) {
                item("empty") {
                    Text(
                        "Save places like Home and Work, then tap Navigate to draw a route on the " +
                            "map (and hand off to turn-by-turn).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddPlaceDialog(
            vm = vm,
            locationGranted = locGranted,
            onDismiss = { showAdd = false },
            onSave = { name, kind, lat, lon -> vm.addPlace(name, kind, lat, lon); showAdd = false },
        )
    }
}

@Composable
private fun PlaceRow(place: Place, onNavigate: () -> Unit, onDirections: () -> Unit, onRemove: () -> Unit) {
    Card(
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(kindIcon(place.kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium)
                Text("${"%.5f".format(place.lat)}, ${"%.5f".format(place.lon)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            IconButton(onClick = onDirections) { Icon(Icons.Default.Navigation, contentDescription = "Directions") }
            OutlinedButton(onClick = onNavigate) { Text("Route") }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun AddPlaceDialog(
    vm: MainViewModel,
    locationGranted: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, kind: PlaceKind, lat: Double, lon: Double) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(PlaceKind.CUSTOM) }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }

    fun fetch() {
        locating = true; status = "Getting a GPS fix…"
        scope.launch {
            val fix = vm.oneShotLocation()
            locating = false
            if (fix != null) { lat = "%.6f".format(fix.lat); lon = "%.6f".format(fix.lon); status = "Captured current location." }
            else status = "Couldn't get a fix — enter coordinates."
        }
    }

    val latD = lat.toDoubleOrNull(); val lonD = lon.toDoubleOrNull()
    val valid = latD != null && lonD != null && latD in -90.0..90.0 && lonD in -180.0..180.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add place") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaceKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
                OutlinedButton(onClick = { fetch() }, enabled = !locating && locationGranted, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (locating) "Locating…" else "Use my current location")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Latitude") },
                        singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = lon, onValueChange = { lon = it }, label = { Text("Longitude") },
                        singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, kind, latD!!, lonD!!) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun kindIcon(kind: PlaceKind): ImageVector = when (kind) {
    PlaceKind.HOME -> Icons.Default.Home
    PlaceKind.WORK -> Icons.Default.Work
    PlaceKind.CUSTOM -> Icons.Default.Place
}

private fun openDirections(context: Context, place: Place) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${place.lat},${place.lon}"))
        .apply { setPackage("com.google.android.apps.maps") }
    val geo = Intent(Intent.ACTION_VIEW,
        Uri.parse("geo:${place.lat},${place.lon}?q=${place.lat},${place.lon}(${Uri.encode(place.name)})"))
    runCatching { context.startActivity(nav) }
        .recoverCatching { context.startActivity(Intent.createChooser(geo, "Navigate")) }
}
