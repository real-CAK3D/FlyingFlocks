package com.cak3d.biscuitmesh.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.Aircraft
import com.cak3d.biscuitmesh.geo.AdsbClient
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private val SKY = Color(0xFF38BDF8)

/**
 * Live aircraft overhead — real-time ADS-B from airplanes.live for the planes broadcasting
 * their position near you right now: callsign, type, altitude, speed, distance and bearing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftScreen(vm: MainViewModel, onBack: () -> Unit, onOpenCockpit: (String) -> Unit = {}) {
    val client = remember { AdsbClient() }
    var lat by remember { mutableStateOf<Double?>(null) }
    var lon by remember { mutableStateOf<Double?>(null) }
    var aircraft by remember { mutableStateOf<List<Aircraft>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Aircraft?>(null) }
    var mode by remember { mutableStateOf(AircraftView.MAP) }
    // Per-aircraft position history (the flight trail), accumulated across polls.
    val trails = remember { mutableStateMapOf<String, List<GeoPoint>>() }

    // Keep a current fix.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { vm.locationUpdates().collect { lat = it.latitude; lon = it.longitude } }
    }
    // Poll ADS-B around the *current* fix. Keyed on the rounded position so the loop
    // re-centres as you drive instead of querying the first fix forever.
    val fixKey = (lat?.let { Math.round(it * 100) } ?: Long.MIN_VALUE) to
        (lon?.let { Math.round(it * 100) } ?: Long.MIN_VALUE)
    androidx.compose.runtime.LaunchedEffect(fixKey) {
        val la = lat; val lo = lon
        if (la == null || lo == null) return@LaunchedEffect
        while (true) {
            loading = true
            val list = runCatching { client.nearby(la, lo, radiusNm = 150) }
            loading = false
            list.onSuccess { planes ->
                aircraft = planes; error = null
                // Extend each plane's trail, de-duping a repeated position.
                planes.forEach { a ->
                    if (a.hex.isBlank()) return@forEach
                    val prev = trails[a.hex] ?: emptyList()
                    val last = prev.lastOrNull()
                    if (last == null || last.latitude != a.lat || last.longitude != a.lon) {
                        trails[a.hex] = (prev + GeoPoint(a.lat, a.lon)).takeLast(40)
                    }
                }
            }.onFailure { error = "Couldn't reach the ADS-B feed. Retrying…" }
            delay(15_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aircraft Overhead") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { mode = if (mode == AircraftView.MAP) AircraftView.LIST else AircraftView.MAP }) {
                        Icon(
                            if (mode == AircraftView.MAP) Icons.AutoMirrored.Filled.List else Icons.Default.Map,
                            contentDescription = "Toggle list / map",
                        )
                    }
                    HelpButton(
                        "Aircraft Overhead",
                        "Live ADS-B — aircraft broadcasting their position within ~150 nm, via airplanes.live. The map " +
                            "shows each plane rotated to its heading, its flight trail behind it and its projected path " +
                            "ahead; the list shows callsign, type, altitude, speed, heading and distance. Tap a plane for " +
                            "full details. To see them through the camera, use Sky AR.",
                    )
                },
            )
        },
    ) { padding ->
        if (mode == AircraftView.MAP) {
            AircraftMap(Modifier.fillMaxSize().padding(padding), lat, lon, aircraft, trails) { selected = it }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("head") {
                Card(
                    Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Flight, contentDescription = null, tint = SKY)
                        Column(Modifier.weight(1f)) {
                            Text("LIVE ADS-B", style = MaterialTheme.typography.labelMedium, color = SKY,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    lat == null -> "Waiting for a GPS fix…"
                                    aircraft.isNotEmpty() -> "${aircraft.size} aircraft in the air within ~150 nm"
                                    loading -> "Scanning the sky…"
                                    error != null -> error!!
                                    else -> "No aircraft overhead right now."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            // hex can be blank/shared for TIS-B/MLAT targets, so index guarantees a unique key.
            items(aircraft.size, key = { "${aircraft[it].hex}-$it" }) {
                val a = aircraft[it]
                AircraftRow(a) { selected = a }
            }
        }
    }
    selected?.let { a ->
        AircraftDetailDialog(
            a,
            onRide = { if (a.hex.isNotBlank()) { onOpenCockpit(a.hex); selected = null } },
        ) { selected = null }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AircraftDetailDialog(a: Aircraft, onRide: () -> Unit = {}, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onRide) { Text("Ride cockpit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Flight, contentDescription = null, tint = SKY,
                    modifier = Modifier.graphicsLayer { rotationZ = a.trackDeg.toFloat() })
                Text(a.flight.ifBlank { a.hex.uppercase() }, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AircraftStat("Type", a.type.ifBlank { "—" })
                AircraftStat("ICAO hex", a.hex.uppercase().ifBlank { "—" })
                AircraftStat("Altitude", "${a.altFt} ft")
                AircraftStat("Ground speed", "${a.gsKt} kt")
                AircraftStat("Heading", "${a.trackDeg}°")
                AircraftStat("Distance", "${"%.1f".format(a.distanceKm)} km")
                AircraftStat("Bearing", "${a.bearingDeg.toInt()}° ${compass(a.bearingDeg)}")
            }
        },
    )
}

@Composable
private fun AircraftStat(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AircraftRow(a: Aircraft, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // plane icon rotated to its heading
            Icon(Icons.Default.Flight, contentDescription = null, tint = SKY,
                modifier = Modifier.graphicsLayer { rotationZ = a.trackDeg.toFloat() })
            Column(Modifier.weight(1f)) {
                Text(
                    a.flight.ifBlank { a.hex.uppercase() } + (if (a.type.isNotBlank()) "  ·  ${a.type}" else ""),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${a.altFt} ft  ·  ${a.gsKt} kt  ·  hdg ${a.trackDeg}°",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), fontFamily = FontFamily.Monospace,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${"%.1f".format(a.distanceKm)} km", style = MaterialTheme.typography.titleMedium,
                    color = SKY, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(compass(a.bearingDeg), style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

private fun compass(bearingDeg: Float): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val idx = (((bearingDeg + 22.5f) % 360f + 360f) % 360f / 45f).toInt() % 8
    return dirs[idx]
}

private enum class AircraftView { LIST, MAP }

/** Live map of the aircraft overhead: each plane rotated to its heading, its trail behind it
 *  and a dashed projected path ahead. Tap a plane for its details. */
@Composable
private fun AircraftMap(
    modifier: Modifier,
    lat: Double?,
    lon: Double?,
    aircraft: List<Aircraft>,
    trails: Map<String, List<GeoPoint>>,
    onSelect: (Aircraft) -> Unit,
) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
            controller.setZoom(9.0)
        }
    }
    DisposableEffect(Unit) { mapView.onResume(); onDispose { mapView.onPause(); mapView.onDetach() } }
    val centered = remember { booleanArrayOf(false) }
    LaunchedEffect(lat, lon) {
        if (!centered[0] && lat != null && lon != null) {
            mapView.controller.setCenter(GeoPoint(lat, lon)); centered[0] = true
        }
    }
    val planeIcon = remember { planeBitmap(context, 0xFF38BDF8.toInt()) }
    val meIcon = remember { dotBitmap(context, 0xFF3B82F6.toInt(), 30) }
    Box(modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.overlays.clear()
                if (lat != null && lon != null) {
                    val me = Marker(map)
                    me.position = GeoPoint(lat, lon)
                    me.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    me.icon = meIcon
                    me.infoWindow = null
                    map.overlays.add(me)
                }
                aircraft.forEach { a ->
                    val pos = GeoPoint(a.lat, a.lon)
                    // Flight trail behind the plane.
                    trails[a.hex]?.takeIf { it.size >= 2 }?.let { pts ->
                        val line = Polyline(map)
                        line.setPoints(pts)
                        line.outlinePaint.color = 0x8838BDF8.toInt()
                        line.outlinePaint.strokeWidth = 4f
                        map.overlays.add(line)
                    }
                    // Dashed projected path ahead, length scaled by ground speed.
                    val aheadKm = (a.gsKt * 0.12).coerceIn(5.0, 45.0)
                    val proj = Polyline(map)
                    proj.setPoints(listOf(pos, destinationPoint(a.lat, a.lon, a.trackDeg.toDouble(), aheadKm)))
                    proj.outlinePaint.color = 0x66FFD166.toInt()
                    proj.outlinePaint.strokeWidth = 3f
                    proj.outlinePaint.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
                    map.overlays.add(proj)
                    // The plane, rotated to its heading.
                    val m = Marker(map)
                    m.position = pos
                    m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    m.icon = planeIcon
                    m.rotation = -a.trackDeg.toFloat()
                    m.title = a.flight.ifBlank { a.hex.uppercase() } + "  ·  ${a.altFt} ft  ·  ${a.gsKt} kt"
                    m.setOnMarkerClickListener { _, _ -> onSelect(a); true }
                    map.overlays.add(m)
                }
                map.invalidate()
            },
        )
        Text(
            if (lat == null) "Waiting for a GPS fix…"
            else "${aircraft.size} aircraft · trail behind · dashed path ahead · tap a plane",
            style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace,
            color = Color.White,
            modifier = Modifier.padding(10.dp)
                .background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** A plane glyph pointing "up" (north) so osmdroid can rotate it to the heading. */
private fun planeBitmap(context: android.content.Context, color: Int): BitmapDrawable {
    val s = (24 * context.resources.displayMetrics.density).toInt().coerceAtLeast(24)
    val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFF0E1116.toInt(); style = Paint.Style.STROKE; strokeWidth = s * 0.05f
    }
    val path = Path().apply {
        moveTo(s / 2f, s * 0.08f)
        lineTo(s * 0.80f, s * 0.84f)
        lineTo(s / 2f, s * 0.66f)
        lineTo(s * 0.20f, s * 0.84f)
        close()
    }
    c.drawPath(path, p); c.drawPath(path, outline)
    return BitmapDrawable(context.resources, bmp)
}

private fun dotBitmap(context: android.content.Context, color: Int, px: Int): BitmapDrawable {
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    c.drawCircle(px / 2f, px / 2f, px / 2f - 2f, p)
    return BitmapDrawable(context.resources, bmp)
}

/** Point `distKm` from (lat,lon) along `bearingDeg` — the end of the projected path. */
private fun destinationPoint(lat: Double, lon: Double, bearingDeg: Double, distKm: Double): GeoPoint {
    val r = 6371.0
    val d = distKm / r
    val br = Math.toRadians(bearingDeg)
    val lat1 = Math.toRadians(lat); val lon1 = Math.toRadians(lon)
    val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(br))
    val lon2 = lon1 + atan2(sin(br) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
    return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
}
