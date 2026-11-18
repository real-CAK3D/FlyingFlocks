package com.cak3d.biscuitmesh.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.wardrive.SampleKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

// "Eyesight" — ground AR. Point the phone around and the things you've plotted on the
// map (saved places, cameras, wardrive sightings) within a chosen radius appear through
// the camera at their real-world bearing, tagged with distance. Same az/el projection as
// Sky AR, but targets sit on the horizon and carry a distance pill.

private const val EYE_V_FOV_DEG = 58f
private const val EYE_HEIGHT_M = 1.6

private data class EyeTarget(val bearing: Float, val distM: Float, val label: String, val color: Color)

private fun kindColor(k: SampleKind): Color = when (k) {
    SampleKind.AP -> Color(0xFF22D3EE)
    SampleKind.STATION -> Color(0xFFFF5470)
    SampleKind.BT -> Color(0xFF7C9CFF)
    else -> Color(0xFFF59E0B)   // RF / sub-GHz and any future kind
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EyesightArScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCam by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCam = it }
    LaunchedEffect(Unit) { if (!hasCam) permLauncher.launch(Manifest.permission.CAMERA) }

    var fix by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    LaunchedEffect(Unit) { runCatching { vm.locationUpdates().collect { fix = it.latitude to it.longitude } } }

    val places by vm.places.collectAsStateWithLifecycle(initialValue = emptyList())
    val hazards by vm.hazards.collectAsStateWithLifecycle(initialValue = emptyList())
    val samplesFlow = vm.wardriveSamples ?: remember { MutableStateFlow(emptyMap()) }
    val samples by samplesFlow.collectAsStateWithLifecycle(initialValue = emptyMap())

    var radiusM by remember { mutableFloatStateOf(900f) }

    // Device attitude.
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

    // Build the in-range targets from everything plotted.
    val targets = run {
        val f = fix
        if (f == null) emptyList()
        else {
            val res = FloatArray(2)
            fun near(lat: Double, lon: Double): Pair<Float, Float>? {
                Location.distanceBetween(f.first, f.second, lat, lon, res)
                return if (res[0] <= radiusM) res[0] to res[1] else null
            }
            buildList {
                places.forEach { p -> near(p.lat, p.lon)?.let { add(EyeTarget(it.second, it.first, p.name, Color(0xFF34D399))) } }
                hazards.forEach { h -> near(h.lat, h.lon)?.let { add(EyeTarget(it.second, it.first, h.name.ifBlank { "camera" }, Color(0xFFFF5470))) } }
                // Wardrive sightings can be dense; keep the nearest 50.
                samples.values.mapNotNull { s -> near(s.lat, s.lon)?.let { Triple(it.second, it.first, s) } }
                    .sortedBy { it.second }.take(50)
                    .forEach { (bear, dist, s) -> add(EyeTarget(bear, dist, s.label.ifBlank { s.id.takeLast(5) }, kindColor(s.kind))) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Eyesight") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (!hasCam) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Eyesight needs the camera to show your plots in the real world.",
                        style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.padding(top = 12.dp)) { Text("Enable camera") }
                }
                return@Box
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val view = PreviewView(ctx)
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                        provider.unbindAll()
                        runCatching { provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview) }
                    }, ContextCompat.getMainExecutor(ctx))
                    view
                },
            )

            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                val tanHalfV = tan(Math.toRadians((EYE_V_FOV_DEG / 2f).toDouble())).toFloat()
                val tanHalfH = tanHalfV * (w / h)
                val fwd = floatArrayOf(-rm[2], -rm[5], -rm[8])
                val right = floatArrayOf(rm[0], rm[3], rm[6])
                val up = floatArrayOf(rm[1], rm[4], rm[7])

                drawCircle(Color.White.copy(alpha = 0.5f), 10f, Offset(w / 2f, h / 2f), style = Stroke(1.5f))

                val paint = android.graphics.Paint().apply { textSize = 26f; isAntiAlias = true }
                targets.sortedByDescending { it.distM }.forEach { t ->
                    // Ground targets sit just below the horizon by eye-height/distance.
                    val elR = -atan2(EYE_HEIGHT_M, t.distM.toDouble().coerceAtLeast(1.0))
                    val azR = Math.toRadians(t.bearing.toDouble())
                    val e = (cos(elR) * sin(azR)).toFloat(); val n = (cos(elR) * cos(azR)).toFloat(); val u = sin(elR).toFloat()
                    val df = e * fwd[0] + n * fwd[1] + u * fwd[2]
                    if (df <= 0.08f) return@forEach
                    val ndcX = (e * right[0] + n * right[1] + u * right[2]) / df / tanHalfH
                    val ndcY = (e * up[0] + n * up[1] + u * up[2]) / df / tanHalfV
                    if (abs(ndcX) > 1.25f || abs(ndcY) > 1.25f) return@forEach
                    val sx = (0.5f + 0.5f * ndcX) * w
                    val sy = (0.5f - 0.5f * ndcY) * h
                    val prox = (1f - (t.distM / radiusM)).coerceIn(0.2f, 1f)
                    val r = 6f + prox * 8f
                    drawCircle(t.color.copy(alpha = 0.25f), r + 5f, Offset(sx, sy))
                    drawCircle(t.color, r, Offset(sx, sy))
                    paint.color = android.graphics.Color.argb(230, 255, 255, 255)
                    val dist = if (t.distM >= 1000) "%.1fkm".format(t.distM / 1000f) else "${t.distM.toInt()}m"
                    drawContext.canvas.nativeCanvas.drawText("${t.label}  $dist", sx + r + 6f, sy + 8f, paint)
                }
            }

            // Heads-up + radius control.
            val heading = ((Math.toDegrees(atan2((-rm[2]).toDouble(), (-rm[5]).toDouble())).toFloat() + 360f) % 360f)
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("${cardinalOfEye(heading)}  ${heading.toInt()}°  ·  ${targets.size} plots in ${radiusM.toInt()} m",
                    style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f)))
                if (fix == null) Text("waiting for GPS…", color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium)
            }
            Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp)) {
                Text("Radius: ${radiusM.toInt()} m", color = Color.White, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f)))
                Slider(value = radiusM, onValueChange = { radiusM = it }, valueRange = 200f..1500f)
            }
        }
    }
}

private fun cardinalOfEye(deg: Float): String {
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[(((deg + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)]
}
