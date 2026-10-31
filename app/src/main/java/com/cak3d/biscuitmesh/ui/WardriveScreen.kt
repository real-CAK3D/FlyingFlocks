package com.cak3d.biscuitmesh.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.wardrive.SampleKind
import com.cak3d.biscuitmesh.wardrive.WardriveMode
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

@Composable
fun WardriveScreen(vm: MainViewModel) {
    val stateFlow = vm.wardriveState
    val samplesFlow = vm.wardriveSamples
    val trackFlow = vm.wardriveTrack
    if (stateFlow == null || samplesFlow == null || trackFlow == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Wardrive unavailable (no Bluetooth adapter).")
        }
        return
    }

    val state by stateFlow.collectAsStateWithLifecycle()
    val samples by samplesFlow.collectAsStateWithLifecycle()
    val track by trackFlow.collectAsStateWithLifecycle()

    val context = LocalContext.current
    fun hasLocation() = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    var locGranted by remember { mutableStateOf(hasLocation()) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> locGranted = granted || hasLocation() }

    // Elapsed-time ticker while active.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.active) {
        while (state.active) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
            controller.setZoom(16.5)
        }
    }
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }
    val centered = remember { booleanArrayOf(false) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { map ->
                map.overlays.clear()

                if (track.size >= 2) {
                    val line = Polyline(map)
                    line.setPoints(track.map { GeoPoint(it.lat, it.lon) })
                    line.outlinePaint.color = AndroidColor.parseColor("#22D3EE")
                    line.outlinePaint.strokeWidth = 9f
                    map.overlays.add(line)
                }

                samples.values.sortedByDescending { it.timeMs }.take(700).forEach { s ->
                    val m = Marker(map)
                    m.position = GeoPoint(s.lat, s.lon)
                    m.icon = dot(colorFor(s.kind), 11)
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    m.title = "${s.label.ifBlank { s.id }}  ${s.rssi} dBm"
                    m.infoWindow = null
                    map.overlays.add(m)
                }

                val lat = state.lastLat
                val lon = state.lastLon
                if (lat != null && lon != null) {
                    val me = Marker(map)
                    me.position = GeoPoint(lat, lon)
                    me.icon = dot(AndroidColor.parseColor("#3B82F6"), 18)
                    me.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    me.infoWindow = null
                    map.overlays.add(me)
                    if (!centered[0]) {
                        map.controller.setCenter(GeoPoint(lat, lon))
                        centered[0] = true
                    }
                }
                map.invalidate()
            },
        )

        ControlPanel(
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            active = state.active,
            mode = state.mode,
            wifi = state.wifiCount,
            bt = state.btCount,
            accuracy = state.accuracy,
            inDeadZone = state.inDeadZone,
            deadZoneName = state.deadZoneName,
            elapsedMs = if (state.active) nowMs - state.startMs else 0L,
            onMode = { vm.setWardriveMode(it) },
            onStart = {
                if (!locGranted) permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                else vm.startWardrive(state.mode)
            },
            onStop = { vm.stopWardrive() },
            locationReady = locGranted,
        )
    }
}

@Composable
private fun ControlPanel(
    modifier: Modifier,
    active: Boolean,
    mode: WardriveMode,
    wifi: Int,
    bt: Int,
    accuracy: Float?,
    inDeadZone: Boolean,
    deadZoneName: String?,
    elapsedMs: Long,
    onMode: (WardriveMode) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    locationReady: Boolean,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (active && inDeadZone) {
                Text(
                    "⦸ Dead zone${deadZoneName?.let { " · $it" } ?: ""} — scanning paused, nothing logged",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WardriveMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { onMode(m) },
                        label = { Text(m.name) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("WiFi", wifi.toString())
                Stat("BT", bt.toString())
                Stat("Time", formatElapsed(elapsedMs))
                Stat("GPS", accuracy?.let { "±${it.toInt()}m" } ?: "—")
            }
            if (!locationReady) {
                Text(
                    "Location permission needed — tap Start to grant.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (active) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Stop") }
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start") }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

private fun formatElapsed(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

private fun colorFor(kind: SampleKind): Int = when (kind) {
    SampleKind.AP -> AndroidColor.parseColor("#22D3EE")
    SampleKind.STATION -> AndroidColor.parseColor("#34D399")
    SampleKind.BT -> AndroidColor.parseColor("#C084FC")
}

/** A small filled circle Drawable used as a map marker icon. */
private fun dot(color: Int, sizeDp: Int): GradientDrawable {
    val px = (sizeDp * 2.75).toInt()
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(2, AndroidColor.parseColor("#0E1116"))
        setSize(px, px)
    }
}
