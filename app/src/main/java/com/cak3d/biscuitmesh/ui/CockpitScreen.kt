package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.AdsbClient
import com.cak3d.biscuitmesh.geo.Aircraft
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Cockpit / God's-Eye — ride a chosen aircraft. A MapLibre camera is pinned to the plane's live
 * ADS-B position over real satellite terrain, tilted to the horizon and turned to its heading, so
 * you fly along with it. A HUD shows callsign, altitude, speed and heading. (Osiris / God's-Eye style.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CockpitScreen(vm: MainViewModel, hex: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val adsb = remember { AdsbClient() }
    var fix by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var plane by remember { mutableStateOf<Aircraft?>(null) }
    var lost by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { runCatching { vm.locationUpdates().collect { fix = it.latitude to it.longitude } } }
    LaunchedEffect(fix) {
        val f = fix ?: return@LaunchedEffect
        while (true) {
            val near = runCatching { adsb.nearby(f.first, f.second, 250) }.getOrDefault(emptyList())
            val found = near.firstOrNull { it.hex.equals(hex, ignoreCase = true) }
            if (found != null) { plane = found; lost = false } else if (plane != null) lost = true
            delay(5000)
        }
    }

    remember { MapLibre.getInstance(context) }
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        mapView.onStart(); mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }
    LaunchedEffect(Unit) {
        mapView.getMapAsync { m ->
            m.uiSettings.setAllGesturesEnabled(false)   // a ride, not a free-pan map
            m.setStyle(Style.Builder().fromJson(TERRAIN_STYLE))
        }
    }
    // Ride the plane: re-point the camera each time its position updates.
    LaunchedEffect(plane) {
        val p = plane ?: return@LaunchedEffect
        val zoom = (15.0 - p.altFt / 40000.0 * 4.0).coerceIn(10.5, 15.0)
        val pos = CameraPosition.Builder()
            .target(LatLng(p.lat, p.lon))
            .zoom(zoom).tilt(80.0).bearing(p.trackDeg.toDouble()).build()
        mapView.getMapAsync { m -> m.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 2500) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cockpit · ${plane?.flight?.ifBlank { hex.uppercase() } ?: hex.uppercase()}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            // Cockpit canopy frame + a nose marker at centre, so it reads as "riding along".
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                val cx = w / 2f; val cy = h * 0.62f
                val wing = w * 0.16f
                val nose = Path().apply {
                    moveTo(cx, cy - h * 0.06f)
                    lineTo(cx + wing, cy + h * 0.05f)
                    lineTo(cx, cy + h * 0.015f)
                    lineTo(cx - wing, cy + h * 0.05f)
                    close()
                }
                drawPath(nose, Color(0xCC0E1116))
                drawPath(nose, Color(0xFF38BDF8), style = Stroke(3f))
                drawLine(Color(0x5538BDF8), Offset(0f, cy), Offset(w, cy), 1.5f)   // horizon reference
            }

            val p = plane
            if (p != null) {
                Column(
                    Modifier.align(Alignment.TopStart).padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.42f)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(p.flight.ifBlank { p.hex.uppercase() } + (if (p.type.isNotBlank()) "  ·  ${p.type}" else ""),
                        color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    HudRow("ALT", "${p.altFt} ft")
                    HudRow("SPD", "${p.gsKt} kt")
                    HudRow("HDG", "${p.trackDeg}°")
                    HudRow("DIST", "${"%.1f".format(p.distanceKm)} km")
                    if (lost) Text("signal lost — holding last position",
                        color = Color(0xFFFFD166), style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text(
                    if (lost) "That aircraft is no longer broadcasting nearby."
                    else "Acquiring the aircraft…",
                    Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.5f)).padding(14.dp),
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun HudRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(k, color = Color(0xFF38BDF8), fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelMedium)
        Text(v, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium)
    }
}
