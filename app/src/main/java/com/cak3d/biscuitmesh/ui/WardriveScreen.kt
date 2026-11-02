package com.cak3d.biscuitmesh.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.HazardKind
import com.cak3d.biscuitmesh.model.BiscuitDevice
import com.cak3d.biscuitmesh.nav.ActiveRoute
import com.cak3d.biscuitmesh.wardrive.PHONE_SOURCE
import com.cak3d.biscuitmesh.wardrive.SampleKind
import com.cak3d.biscuitmesh.wardrive.WardriveMode
import com.cak3d.biscuitmesh.wardrive.WardriveSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import kotlin.math.roundToInt

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
    val wdgKey by vm.wdgApiKey.collectAsStateWithLifecycle()
    val wdgStatus by vm.wdgStatus.collectAsStateWithLifecycle()
    val cells by vm.wdgCells.collectAsStateWithLifecycle()
    val devFlow = remember { vm.devices ?: MutableStateFlow(emptyMap<String, BiscuitDevice>()) }
    val deviceMap by devFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val hazards by vm.hazards.collectAsStateWithLifecycle(initialValue = emptyList())
    val speedLimit by vm.speedLimit.collectAsStateWithLifecycle()
    val alert by vm.hazardAlert.collectAsStateWithLifecycle()
    val hudOn by vm.hudEnabled.collectAsStateWithLifecycle()
    val useMph by vm.useMph.collectAsStateWithLifecycle()
    val route by vm.activeRoute.collectAsStateWithLifecycle()

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

    // Center on the operator's location as soon as the screen opens, so the map
    // doesn't sit on Null Island (0,0) until the first wardrive starts.
    LaunchedEffect(locGranted) {
        if (locGranted && !centered[0]) {
            val fix = vm.oneShotLocation()
            if (fix != null && !centered[0]) {
                mapView.controller.setCenter(GeoPoint(fix.lat, fix.lon))
                centered[0] = true
            }
        }
    }

    val phoneOn by vm.phoneSensors.collectAsStateWithLifecycle()

    // Free two-finger rotation (re-added on each map update, which clears overlays).
    val rotationOverlay = remember { RotationGestureOverlay(mapView).apply { isEnabled = true } }

    // Heading-up: rotate the map to the device's compass bearing, WDG-style.
    var headingUp by remember { mutableStateOf(false) }
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    DisposableEffect(headingUp) {
        if (!headingUp) {
            mapView.mapOrientation = 0f
            mapView.invalidate()
            onDispose { }
        } else {
            val rot = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val listener = object : SensorEventListener {
                private val rotM = FloatArray(9)
                private val orient = FloatArray(3)
                private var last = Float.NaN
                override fun onSensorChanged(e: SensorEvent) {
                    if (e.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                    SensorManager.getRotationMatrixFromVector(rotM, e.values)
                    SensorManager.getOrientation(rotM, orient)
                    val az = Math.toDegrees(orient[0].toDouble()).toFloat()
                    if (!last.isNaN() && kotlin.math.abs(az - last) < 1.5f) return
                    last = az
                    mapView.mapOrientation = -az
                    mapView.invalidate()
                }
                override fun onAccuracyChanged(s: Sensor?, a: Int) {}
            }
            if (rot != null) sensorManager.registerListener(listener, rot, SensorManager.SENSOR_DELAY_UI)
            onDispose { sensorManager?.unregisterListener(listener) }
        }
    }

    // What-the-Flock-style controls: follow the vehicle, layer toggles, telemetry.
    var followMode by remember { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var showCameras by remember { mutableStateOf(true) }
    var showPlaces by remember { mutableStateOf(true) }
    var showBoards by remember { mutableStateOf(true) }
    var controlsExpanded by remember { mutableStateOf(true) }
    var mapStyle by remember { mutableStateOf(MapStyle.DARK) }
    val places by vm.places.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(mapStyle) {
        when (mapStyle) {
            MapStyle.DARK -> { mapView.setTileSource(TileSourceFactory.MAPNIK); mapView.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS) }
            MapStyle.LIGHT -> { mapView.setTileSource(TileSourceFactory.MAPNIK); mapView.overlayManager.tilesOverlay.setColorFilter(null) }
            MapStyle.TOPO -> { mapView.setTileSource(TOPO_SOURCE); mapView.overlayManager.tilesOverlay.setColorFilter(null) }
        }
        mapView.invalidate()
    }

    // G-force from the accelerometer (running max), and a max-speed tracker.
    var gForce by remember { mutableFloatStateOf(0f) }
    var maxG by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                val mag = kotlin.math.sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                val g = kotlin.math.abs(mag / 9.80665f - 1f)
                gForce = gForce * 0.8f + g * 0.2f
                if (gForce > maxG) maxG = gForce
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (accel != null) sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager?.unregisterListener(listener) }
    }
    var maxSpeedMps by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.speedMps) {
        val s = state.speedMps ?: 0f
        if (s > maxSpeedMps) maxSpeedMps = s
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { map ->
                map.overlays.clear()
                map.overlays.add(rotationOverlay)

                // WDG owned-territory cells first, so samples/track draw on top.
                cells.forEach { c ->
                    val poly = Polygon(map)
                    poly.points = Polygon.pointsAsCircle(GeoPoint(c.lat, c.lon), 900.0)
                    poly.fillPaint.color = AndroidColor.parseColor("#44F59E0B")
                    poly.outlinePaint.color = AndroidColor.parseColor("#F59E0B")
                    poly.outlinePaint.strokeWidth = 3f
                    poly.setOnClickListener { _, _, _ -> false }
                    map.overlays.add(poly)
                }

                if (track.size >= 2) {
                    val line = Polyline(map)
                    line.setPoints(track.map { GeoPoint(it.lat, it.lon) })
                    line.outlinePaint.color = AndroidColor.parseColor("#22D3EE")
                    line.outlinePaint.strokeWidth = 9f
                    map.overlays.add(line)
                }

                samples.values.sortedByDescending { it.timeMs }.take(900).forEach { s ->
                    val shared = s.sources.size > 1
                    val m = Marker(map)
                    m.position = GeoPoint(s.lat, s.lon)
                    m.icon = dot(colorForSample(s), if (shared) 13 else sizeForKind(s.kind))
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    val seenBy = if (shared) "  ·  seen by ${s.sources.size}" else ""
                    m.title = "${s.label.ifBlank { s.id }}  ${s.rssi} dBm$seenBy"
                    map.overlays.add(m)
                }

                // Active navigation route + destination.
                route?.let { r ->
                    if (r.points.size >= 2) {
                        val line = Polyline(map)
                        line.setPoints(r.points.map { GeoPoint(it.lat, it.lon) })
                        line.outlinePaint.color = AndroidColor.parseColor("#38BDF8")
                        line.outlinePaint.strokeWidth = 14f
                        map.overlays.add(line)
                    }
                    val dest = Marker(map)
                    dest.position = GeoPoint(r.destLat, r.destLon)
                    dest.icon = ring(AndroidColor.parseColor("#38BDF8"), 18)
                    dest.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    dest.title = r.name
                    map.overlays.add(dest)
                }

                // Saved places (tappable labels).
                if (showPlaces) {
                    places.forEach { p ->
                        val m = Marker(map)
                        m.position = GeoPoint(p.lat, p.lon)
                        m.icon = ring(AndroidColor.parseColor("#A3E635"), 14)
                        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        m.title = p.name
                        map.overlays.add(m)
                    }
                }

                // Cameras from OpenStreetMap (speed / Flock / red-light).
                if (showCameras) {
                    hazards.forEach { hz ->
                        val m = Marker(map)
                        m.position = GeoPoint(hz.lat, hz.lon)
                        m.icon = ring(hazardColor(hz.kind), 15)
                        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        m.title = hazardLabel(hz.kind) + if (hz.name.isNotBlank()) " · ${hz.name}" else ""
                        map.overlays.add(m)
                    }
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
                    // Follow the vehicle: keep the map centred on the puck while on.
                    if (followMode) {
                        map.controller.setCenter(GeoPoint(lat, lon))
                        centered[0] = true
                    }
                }
                map.invalidate()
            },
        )

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        route?.let { r -> NavCard(r, useMph, onClear = { vm.clearRoute() }) }
        if (showBoards) BoardsLegend(deviceMap, state.sourceCounts, phoneOn)
        ControlPanel(
            modifier = Modifier,
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
            wdgConfigured = wdgKey.isNotBlank(),
            canUpload = samples.values.any { it.kind == SampleKind.AP || it.kind == SampleKind.BT },
            wdgStatus = wdgStatusText(wdgStatus),
            wdgWorking = wdgStatus is MainViewModel.WdgStatus.Working,
            territoryCount = cells.size,
            phoneOn = phoneOn,
            onTogglePhone = { vm.setPhoneSensors(it) },
            onUpload = { vm.uploadToWdg() },
            onLoadTerritory = { vm.fetchOwnedCells() },
            expanded = controlsExpanded,
            onToggleExpand = { controlsExpanded = !controlsExpanded },
        )
        }

        // Top-right stack: heading-up compass + follow-the-vehicle toggle.
        Column(
            Modifier.align(Alignment.TopEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            val mapBtnColors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
            )
            FilledIconToggleButton(
                checked = headingUp, onCheckedChange = { headingUp = it },
                modifier = Modifier.size(44.dp), colors = mapBtnColors,
            ) { Icon(Icons.Default.Explore, contentDescription = if (headingUp) "Heading up" else "North up") }
            FilledIconToggleButton(
                checked = followMode, onCheckedChange = { followMode = it },
                modifier = Modifier.size(44.dp), colors = mapBtnColors,
            ) { Icon(Icons.Default.MyLocation, contentDescription = if (followMode) "Following" else "Follow vehicle") }
            FilledIconToggleButton(
                checked = hudOn, onCheckedChange = { vm.setHudEnabled(it) },
                modifier = Modifier.size(44.dp), colors = mapBtnColors,
            ) { Icon(Icons.Default.Speed, contentDescription = if (hudOn) "Hide HUD" else "Show HUD") }
        }

        // Top-left: hamburger menu + telemetry HUD.
        Column(
            Modifier.align(Alignment.TopStart).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledIconButton(
                onClick = { menuOpen = !menuOpen },
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
            ) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
            if (menuOpen) {
                MapMenu(
                    hud = hudOn, onHud = { vm.setHudEnabled(it) },
                    showBoards = showBoards, onBoards = { showBoards = it },
                    showCameras = showCameras, onCameras = { showCameras = it },
                    showPlaces = showPlaces, onPlaces = { showPlaces = it },
                    style = mapStyle, onStyle = { mapStyle = it },
                    camCount = hazards.size,
                )
            }
            if (hudOn) {
                TelemetryHud(
                    speedMps = state.speedMps, altitudeM = state.altitudeM, gForce = gForce,
                    maxG = maxG, maxSpeedMps = maxSpeedMps, recording = state.active,
                    limit = speedLimit, useMph = useMph,
                )
            }
        }

        // Over-speed camera alert banner + a buzz when it first trips.
        LaunchedEffect(alert?.hazard?.id) { if (alert != null) vibrate(context) }
        alert?.let { a ->
            CameraAlert(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                alert = a,
                useMph = useMph,
            )
        }
    }
}

enum class MapStyle(val label: String) { DARK("Dark"), LIGHT("Light"), TOPO("Topo") }

private val TOPO_SOURCE = org.osmdroid.tileprovider.tilesource.XYTileSource(
    "OpenTopoMap", 0, 17, 256, ".png",
    arrayOf("https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/", "https://c.tile.opentopomap.org/"),
    "© OpenTopoMap (CC-BY-SA), © OpenStreetMap contributors",
)

@Composable
private fun MapMenu(
    hud: Boolean, onHud: (Boolean) -> Unit,
    showBoards: Boolean, onBoards: (Boolean) -> Unit,
    showCameras: Boolean, onCameras: (Boolean) -> Unit,
    showPlaces: Boolean, onPlaces: (Boolean) -> Unit,
    style: MapStyle, onStyle: (MapStyle) -> Unit,
    camCount: Int,
) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp).widthIn(min = 220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DISPLAYS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            MenuToggle("Telemetry HUD", hud, onHud)
            MenuToggle("Boards panel", showBoards, onBoards)
            Text("LAYERS", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
            MenuToggle("Cameras ($camCount)", showCameras, onCameras)
            MenuToggle("Saved places", showPlaces, onPlaces)
            Text("MAP STYLE", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MapStyle.entries.forEach { st ->
                    FilterChip(selected = style == st, onClick = { onStyle(st) }, label = { Text(st.label) })
                }
            }
        }
    }
}

@Composable
private fun MenuToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TelemetryHud(
    speedMps: Float?, altitudeM: Double?, gForce: Float, maxG: Float, maxSpeedMps: Float,
    recording: Boolean, limit: com.cak3d.biscuitmesh.geo.SpeedLimit?, useMph: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        ) {
            Column(Modifier.padding(14.dp).widthIn(min = 160.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                HudRow("Speed", speedText(speedMps, useMph))
                HudRow("Altitude", altText(altitudeM, useMph))
                HudRow("G-force", "%.2f g".format(gForce))
                Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth((gForce / 0.6f).coerceIn(0f, 1f)).height(4.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                }
                HudRow("Max", "${speedText(maxSpeedMps, useMph)} · ${"%.2f".format(maxG)} g")
                Text(
                    if (recording) "● Recording" else "Not recording",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        val postedLimit = limit?.let { if (useMph) it.mph else it.kmh }
        if (postedLimit != null) {
            Column(
                Modifier.background(Color.White, RoundedCornerShape(10.dp))
                    .border(3.dp, Color(0xFFD32F2F), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("LIMIT", color = Color(0xFF111111), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("$postedLimit", color = Color(0xFF111111), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HudRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

private fun speedText(speedMps: Float?, useMph: Boolean): String {
    val v = (speedMps ?: 0f) * if (useMph) 2.236936f else 3.6f
    return "${v.toInt()} ${if (useMph) "mph" else "km/h"}"
}

private fun altText(altM: Double?, useMph: Boolean): String {
    if (altM == null) return "—"
    return if (useMph) "${(altM * 3.28084).toInt()} ft" else "${altM.toInt()} m"
}

@Composable
private fun CameraAlert(modifier: Modifier, alert: com.cak3d.biscuitmesh.geo.HazardAlert, useMph: Boolean) {
    Card(
        modifier = modifier.fillMaxWidth(0.92f),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "⚠  ${hazardLabel(alert.hazard.kind)} AHEAD · ${alert.distanceM} m",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onError,
            )
            Text(
                "${alert.speedMph} mph" + (alert.limitMph?.let { " in a $it" } ?: "") + " — slow down",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onError,
            )
        }
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
    wdgConfigured: Boolean,
    canUpload: Boolean,
    wdgStatus: String?,
    wdgWorking: Boolean,
    territoryCount: Int,
    phoneOn: Boolean,
    onTogglePhone: (Boolean) -> Unit,
    onUpload: () -> Unit,
    onLoadTerritory: () -> Unit,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
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
            // Always-visible stat strip + collapse chevron.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("WiFi", wifi.toString())
                    Stat("BT", bt.toString())
                    Stat("Time", formatElapsed(elapsedMs))
                    Stat("GPS", accuracy?.let { "±${it.toInt()}m" } ?: "—")
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = if (expanded) "Minimize controls" else "Expand controls",
                    )
                }
            }
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WardriveMode.entries.forEach { m ->
                        FilterChip(selected = mode == m, onClick = { onMode(m) }, label = { Text(m.name) })
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Phone sensors", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (phoneOn) "Scanning with this phone's Wi-Fi + BLE" else "Boards only",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    Switch(checked = phoneOn, onCheckedChange = onTogglePhone)
                }
                if (!locationReady) {
                    Text(
                        "Location permission needed — tap Start to grant.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
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
            if (expanded && wdgConfigured) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onUpload,
                        enabled = !wdgWorking && canUpload,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (wdgWorking) "Working…" else "Upload") }
                    OutlinedButton(
                        onClick = onLoadTerritory,
                        enabled = !wdgWorking,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (territoryCount > 0) "Territory ($territoryCount)" else "Load territory") }
                }
            }
            if (expanded) {
                wdgStatus?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

/** Collapses the WDG upload status into a short line for the control panel. */
private fun wdgStatusText(status: MainViewModel.WdgStatus): String? = when (status) {
    is MainViewModel.WdgStatus.Idle -> null
    is MainViewModel.WdgStatus.Working -> status.message
    is MainViewModel.WdgStatus.Done -> status.message
    is MainViewModel.WdgStatus.Error -> status.message
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun BoardsLegend(devices: Map<String, BiscuitDevice>, counts: Map<String, Int>, phoneOn: Boolean) {
    val live = devices.values.filter { it.isLive }
    if (live.isEmpty() && !phoneOn) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("BOARDS", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary)
            live.forEach { d ->
                val meta = buildList {
                    add("${d.rssi} dBm")
                    d.status?.battery?.let { add("$it%") }
                    add("${counts[d.address] ?: 0} pings")
                }.joinToString("  ·  ")
                LegendRow(Color(sourceColor(d.address)), d.displayName, meta)
            }
            if (phoneOn) {
                LegendRow(Color(sourceColor(PHONE_SOURCE)), "This phone", "${counts[PHONE_SOURCE] ?: 0} pings")
            }
        }
    }
}

@Composable
private fun NavCard(route: ActiveRoute, useMph: Boolean, onClear: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ROUTE · ${route.name}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary)
            Text(routeSummary(route, useMph), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { startInMaps(context, route.destLat, route.destLon, route.name) },
                    modifier = Modifier.weight(1f),
                ) { Text("Start in Maps") }
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) { Text("Clear") }
            }
        }
    }
}

private fun routeSummary(route: ActiveRoute, useMph: Boolean): String {
    val dist = if (useMph) "%.1f mi".format(route.distanceM / 1609.34) else "%.1f km".format(route.distanceM / 1000)
    val mins = (route.durationS / 60).toInt()
    val eta = if (mins >= 60) "${mins / 60} h ${mins % 60} min" else "$mins min"
    return "$dist  ·  $eta"
}

private fun startInMaps(context: Context, lat: Double, lon: Double, name: String) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lon"))
        .apply { setPackage("com.google.android.apps.maps") }
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(name)})"))
    runCatching { context.startActivity(nav) }
        .recoverCatching { context.startActivity(Intent.createChooser(geo, "Navigate")) }
}

@Composable
private fun LegendRow(color: Color, name: String, meta: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(12.dp).background(color, androidx.compose.foundation.shape.CircleShape))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(meta, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        }
    }
}

private fun formatElapsed(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

// Each board (and the phone) gets its own colour so the map shows what saw what;
// a point seen by more than one source is drawn white.
private val SOURCE_PALETTE = intArrayOf(
    0xFF34D399.toInt(), // green
    0xFFC084FC.toInt(), // purple
    0xFFFB923C.toInt(), // orange
    0xFFF472B6.toInt(), // pink
    0xFFFACC15.toInt(), // yellow
    0xFF60A5FA.toInt(), // blue
    0xFFF87171.toInt(), // red
    0xFF2DD4BF.toInt(), // teal
)

fun sourceColor(src: String): Int =
    if (src == PHONE_SOURCE) 0xFF22D3EE.toInt()
    else SOURCE_PALETTE[kotlin.math.abs(src.hashCode()) % SOURCE_PALETTE.size]

private fun colorForSample(s: WardriveSample): Int =
    if (s.sources.size > 1) 0xFFFFFFFF.toInt()
    else sourceColor(s.sources.firstOrNull() ?: PHONE_SOURCE)

private fun sizeForKind(kind: SampleKind): Int = when (kind) {
    SampleKind.AP -> 11
    SampleKind.STATION -> 8
    SampleKind.BT -> 10
}

private fun hazardColor(kind: HazardKind): Int = when (kind) {
    HazardKind.FLOCK -> AndroidColor.parseColor("#EF4444")
    HazardKind.SPEED_CAMERA -> AndroidColor.parseColor("#F59E0B")
    HazardKind.RED_LIGHT -> AndroidColor.parseColor("#EC4899")
    HazardKind.CAMERA -> AndroidColor.parseColor("#94A3B8")
}

private fun hazardLabel(kind: HazardKind): String = when (kind) {
    HazardKind.FLOCK -> "Flock/ALPR camera"
    HazardKind.SPEED_CAMERA -> "Speed camera"
    HazardKind.RED_LIGHT -> "Red-light camera"
    HazardKind.CAMERA -> "Camera"
}

/** A camera marker: a filled circle with a white ring, to stand apart from pings. */
private fun ring(color: Int, sizeDp: Int): GradientDrawable {
    val px = (sizeDp * 2.75).toInt()
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(4, AndroidColor.WHITE)
        setSize(px, px)
    }
}

private fun vibrate(context: Context) {
    runCatching {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        v.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
    }
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
