package com.cak3d.biscuitmesh.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.AdsbClient
import com.cak3d.biscuitmesh.geo.Aircraft
import com.cak3d.biscuitmesh.geo.BodyKind
import com.cak3d.biscuitmesh.geo.CelestialBody
import com.cak3d.biscuitmesh.geo.SkyStar
import com.cak3d.biscuitmesh.geo.celestialBodies
import com.cak3d.biscuitmesh.geo.constellationLines
import com.cak3d.biscuitmesh.geo.starField
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

// Point-at-the-sky AR. A live back-camera preview with the things overhead drawn on
// top at their real azimuth/elevation, using the phone's rotation-vector sensor. This
// first cut overlays the GPS satellites the receiver sees (live) plus a compass and
// cardinal ring; aircraft, planets/Moon and constellations layer onto the same engine.

private const val V_FOV_DEG = 58f   // nominal vertical camera FOV; tune on-device

private data class SkyTarget(val az: Float, val el: Float, val label: String, val color: Color, val size: Float)

private fun satColor(constellation: String): Color = when (constellation) {
    "GPS" -> Color(0xFF22D3EE); "GLONASS" -> Color(0xFFFF7AD9); "Galileo" -> Color(0xFF34D399)
    "BeiDou" -> Color(0xFFF59E0B); "QZSS" -> Color(0xFF7C9CFF); else -> Color(0xFF9CA3AF)
}

private fun bodyColor(name: String): Color = when (name) {
    "Sun" -> Color(0xFFFFC53D); "Moon" -> Color(0xFFE8ECF4); "Mercury" -> Color(0xFFB8B8B8)
    "Venus" -> Color(0xFFFFE9A8); "Mars" -> Color(0xFFFF6B4A); "Jupiter" -> Color(0xFFE6C27A)
    "Saturn" -> Color(0xFFEAD6A0); else -> Color(0xFFFFFFFF)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyArScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val gnss by vm.gnssState.collectAsStateWithLifecycle()

    var hasCam by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCam = it }
    LaunchedEffect(Unit) { if (!hasCam) permLauncher.launch(Manifest.permission.CAMERA) }

    // Keep the GNSS feed running while the AR view is open.
    DisposableEffect(Unit) { vm.startGnss(); onDispose { vm.stopGnss() } }

    // Live aircraft overhead (reuses the no-key airplanes.live ADS-B client), polled
    // around the current fix and placed by bearing + computed elevation.
    val adsb = remember { AdsbClient() }
    var fix by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var planes by remember { mutableStateOf<List<Aircraft>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching { vm.locationUpdates().collect { fix = it.latitude to it.longitude } }
    }
    val fixKey = fix?.let { Math.round(it.first * 50) to Math.round(it.second * 50) }
    LaunchedEffect(fixKey) {
        val f = fix ?: return@LaunchedEffect
        while (true) {
            planes = adsb.nearby(f.first, f.second, 60)
            delay(12_000)
        }
    }

    // Sun, Moon, planets and the star field for this place & moment. The sky drifts slowly,
    // so a recompute every 20s keeps the overlay honest without burning cycles.
    var bodies by remember { mutableStateOf<List<CelestialBody>>(emptyList()) }
    var stars by remember { mutableStateOf<List<SkyStar>>(emptyList()) }
    var showStars by remember { mutableStateOf(true) }
    LaunchedEffect(fixKey) {
        val f = fix ?: return@LaunchedEffect
        while (true) {
            val now = System.currentTimeMillis()
            bodies = celestialBodies(f.first, f.second, now)
            stars = starField(f.first, f.second, now)
            delay(20_000)
        }
    }

    // Device attitude as a rotation matrix (device -> world ENU), updated from the sensor.
    var rm by remember { mutableStateOf(FloatArray(9) { if (it % 4 == 0) 1f else 0f }) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rot = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val out = FloatArray(9)
                val v = if (e.values.size > 4) e.values.copyOf(4) else e.values
                SensorManager.getRotationMatrixFromVector(out, v)
                rm = out
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (rot != null) sm.registerListener(listener, rot, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sky AR") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showStars = !showStars }) {
                        Icon(
                            if (showStars) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Toggle stars & constellations",
                        )
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (!hasCam) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Sky AR needs the camera to show the sky behind the overlay.",
                        style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.padding(top = 12.dp)) { Text("Enable camera") }
                }
                return@Box
            }

            // Live camera preview (behind the overlay).
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val view = PreviewView(ctx)
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        provider.unbindAll()
                        runCatching {
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    view
                },
            )

            // Build the overlay targets: cardinal ring + live satellites.
            val targets = buildList {
                val cardinals = listOf("N" to 0f, "NE" to 45f, "E" to 90f, "SE" to 135f,
                    "S" to 180f, "SW" to 225f, "W" to 270f, "NW" to 315f)
                cardinals.forEach { (lbl, az) -> add(SkyTarget(az, 6f, lbl, Color(0x99FFFFFF), 0f)) }
                gnss.sats.filter { it.elevationDeg >= 0f }.forEach { s ->
                    add(SkyTarget(
                        s.azimuthDeg, s.elevationDeg, "${s.constellation.take(3)}${s.svid}",
                        satColor(s.constellation).copy(alpha = if (s.usedInFix) 1f else 0.55f),
                        6f + (s.cn0 / 50f).coerceIn(0f, 1f) * 8f,
                    ))
                }
                // Aircraft: azimuth = bearing to the plane, elevation from its altitude vs ground distance.
                planes.forEach { a ->
                    val distM = (a.distanceKm * 1000.0).coerceAtLeast(1.0)
                    val el = Math.toDegrees(atan2(a.altFt * 0.3048, distM)).toFloat()
                    add(SkyTarget(a.bearingDeg, el, a.flight.ifBlank { a.hex }, Color(0xFFFFD166), 8f))
                }
            }

            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                val tanHalfV = tan(Math.toRadians((V_FOV_DEG / 2f).toDouble())).toFloat()
                val tanHalfH = tanHalfV * (w / h)
                val fwd = floatArrayOf(-rm[2], -rm[5], -rm[8])
                val right = floatArrayOf(rm[0], rm[3], rm[6])
                val up = floatArrayOf(rm[1], rm[4], rm[7])

                // Project an azimuth/elevation onto the screen; null when it's behind the
                // camera or well outside the frame.
                fun project(az: Float, el: Float, margin: Float = 1.25f): Offset? {
                    val azR = Math.toRadians(az.toDouble()); val elR = Math.toRadians(el.toDouble())
                    val e = (cos(elR) * sin(azR)).toFloat()
                    val n = (cos(elR) * cos(azR)).toFloat()
                    val u = sin(elR).toFloat()
                    val df = e * fwd[0] + n * fwd[1] + u * fwd[2]
                    if (df <= 0.08f) return null
                    val ndcX = (e * right[0] + n * right[1] + u * right[2]) / df / tanHalfH
                    val ndcY = (e * up[0] + n * up[1] + u * up[2]) / df / tanHalfV
                    if (abs(ndcX) > margin || abs(ndcY) > margin) return null
                    return Offset((0.5f + 0.5f * ndcX) * w, (0.5f - 0.5f * ndcY) * h)
                }

                // center reticle
                drawCircle(Color.White.copy(alpha = 0.5f), 10f, Offset(w / 2f, h / 2f), style = Stroke(1.5f))

                val paint = android.graphics.Paint().apply {
                    textSize = 26f; isAntiAlias = true
                    color = android.graphics.Color.WHITE
                }

                // --- Star field (drawn first, behind everything) ---
                if (showStars && stars.isNotEmpty()) {
                    // Constellation stick-figures.
                    constellationLines.forEach { (a, b) ->
                        val sa = stars.getOrNull(a) ?: return@forEach
                        val sb = stars.getOrNull(b) ?: return@forEach
                        if (sa.el < -2f || sb.el < -2f) return@forEach
                        val pa = project(sa.az, sa.el) ?: return@forEach
                        val pb = project(sb.az, sb.el) ?: return@forEach
                        drawLine(Color(0x553FA9FF), pa, pb, strokeWidth = 1.5f)
                    }
                    // Stars themselves — size & brightness from magnitude.
                    stars.forEach { s ->
                        if (s.el < -2f) return@forEach
                        val p = project(s.az, s.el) ?: return@forEach
                        val r = (2.2f - s.mag * 0.7f).coerceIn(1.2f, 6f)
                        val a = (0.9f - s.mag * 0.12f).coerceIn(0.35f, 1f)
                        drawCircle(Color.White.copy(alpha = a * 0.3f), r + 2f, p)
                        drawCircle(Color.White.copy(alpha = a), r, p)
                        if (s.mag < 1.0f) {
                            paint.color = android.graphics.Color.argb(170, 180, 210, 255)
                            drawContext.canvas.nativeCanvas.drawText(s.name, p.x + r + 4f, p.y + 6f, paint)
                        }
                    }
                }

                // --- Overlay targets: cardinals, satellites, aircraft ---
                targets.forEach { t ->
                    val p = project(t.az, t.el) ?: return@forEach
                    if (t.size == 0f) {
                        paint.color = android.graphics.Color.argb(200, 255, 255, 255)
                        drawContext.canvas.nativeCanvas.drawText(t.label, p.x - 10f, p.y, paint)
                    } else {
                        drawCircle(t.color.copy(alpha = 0.25f), t.size + 4f, p)
                        drawCircle(t.color, t.size, p)
                        paint.color = android.graphics.Color.argb(220, 200, 230, 255)
                        drawContext.canvas.nativeCanvas.drawText(t.label, p.x + t.size + 6f, p.y + 8f, paint)
                    }
                }

                // --- Sun, Moon & planets (drawn last, biggest, with a glow) ---
                bodies.forEach { b ->
                    if (b.el < -4f) return@forEach
                    val p = project(b.az, b.el) ?: return@forEach
                    val c = bodyColor(b.name)
                    val r = when (b.kind) {
                        BodyKind.SUN -> 22f; BodyKind.MOON -> 20f; BodyKind.PLANET -> 9f
                    }
                    drawCircle(c.copy(alpha = 0.18f), r + 12f, p)
                    drawCircle(c.copy(alpha = 0.35f), r + 5f, p)
                    drawCircle(c, r, p)
                    paint.color = android.graphics.Color.argb(240, 255, 255, 255)
                    paint.textSize = if (b.kind == BodyKind.PLANET) 26f else 30f
                    drawContext.canvas.nativeCanvas.drawText(b.name, p.x + r + 8f, p.y + 10f, paint)
                    paint.textSize = 26f
                }
            }

            // Heads-up: compass heading + sat count.
            val heading = ((Math.toDegrees(atan2((-rm[2]).toDouble(), (-rm[5]).toDouble())).toFloat() + 360f) % 360f)
            val card = cardinalOf(heading)
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("$card  ${heading.toInt()}°", style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White)
                val up = bodies.count { it.el >= 0f }
                Text("${gnss.sats.count { it.elevationDeg >= 0f }} sats · ${planes.size} aircraft · $up bodies up · point at the sky",
                    style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.25f)))
            }
        }
    }
}

private fun cardinalOf(deg: Float): String {
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[(((deg + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)]
}
