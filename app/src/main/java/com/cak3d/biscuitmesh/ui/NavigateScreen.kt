package com.cak3d.biscuitmesh.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.sensors.PhoneScanRow
import com.cak3d.biscuitmesh.wardrive.SampleKind
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

enum class NavMode(val label: String) { COMPASS("Compass"), RADAR("Radar"), LOCATOR("Locator") }

private val NORTH = Color(0xFFFF5FA2)

private fun cardinal16(deg: Float): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[(((deg + 22.5f) / 45f).toInt()) % 8]
}

/**
 * One screen, three tools that share the same tilted orientation: a 3D Compass, a Radar of
 * the devices around you, and a hot/cold Locator you can walk toward (it buzzes faster the
 * closer you get). Arrows at the bottom page between them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigateScreen(vm: MainViewModel, onBack: () -> Unit, startMode: NavMode, startTargetId: String? = null) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(startMode) }
    var raw by remember { mutableFloatStateOf(0f) }

    // Shared heading sensor.
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            val rot = FloatArray(9); val orient = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, orient)
                var deg = Math.toDegrees(orient[0].toDouble()).toFloat()
                if (deg < 0) deg += 360f
                var delta = deg - ((raw % 360f + 360f) % 360f)
                if (delta > 180) delta -= 360
                if (delta < -180) delta += 360
                raw += delta * 0.18f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(listener) }
    }
    val heading by animateFloatAsState(targetValue = raw, label = "heading")

    // Phone scan feeds radar + locator.
    DisposableEffect(Unit) {
        vm.clearPhoneScan(); vm.startPhoneScan(wifi = true, ble = true)
        onDispose { vm.stopPhoneScan() }
    }
    val results by vm.phoneScanResults.collectAsStateWithLifecycle(initialValue = emptyList())

    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
        0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "s",
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(mode.label) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (mode) {
                    NavMode.COMPASS -> CompassContent(heading)
                    NavMode.RADAR -> RadarContent(heading, sweep, results)
                    NavMode.LOCATOR -> LocatorContent(results, context, startTargetId)
                }
            }
            ModeSwitcher(mode) { mode = it }
        }
    }
}

@Composable
private fun ModeSwitcher(mode: NavMode, onPick: (NavMode) -> Unit) {
    val modes = NavMode.entries
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onPick(modes[(mode.ordinal - 1 + modes.size) % modes.size]) }) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", modifier = Modifier.padding(0.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            modes.forEach { m ->
                Text(
                    m.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (m == mode) FontWeight.Bold else FontWeight.Normal,
                    color = if (m == mode) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.clickable { onPick(m) },
                )
            }
        }
        IconButton(onClick = { onPick(modes[(mode.ordinal + 1) % modes.size]) }) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next")
        }
    }
}

// ---- Compass ----------------------------------------------------------------

@Composable
private fun CompassContent(heading: Float) {
    val headInt = ((heading.roundToInt() % 360) + 360) % 360
    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        TiltedRose(heading)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$headInt°", style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(cardinal16(headInt.toFloat()), style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TiltedRose(heading: Float) {
    val ink = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val cx = size.width / 2f; val cy = size.height / 2f
        val rx = min(size.width, size.height) / 2f * 0.80f
        val ry = rx * 0.46f
        drawOval(ink.copy(alpha = 0.05f), Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))
        drawOval(ink.copy(alpha = 0.18f), Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), style = Stroke(2f))
        fun pt(bearing: Float, scale: Float): Pair<Offset, Float> {
            val phi = Math.toRadians((bearing - heading).toDouble())
            val x = cx + (rx * scale * sin(phi)).toFloat()
            val y = cy - (ry * scale * cos(phi)).toFloat()
            return Offset(x, y) to ((1 - cos(phi)) / 2).toFloat()
        }
        var b = 0
        while (b < 360) {
            val (p, depth) = pt(b.toFloat(), 1f)
            val (pIn, _) = pt(b.toFloat(), 0.9f)
            val major = b % 90 == 0
            val col = (if (b == 0) NORTH else ink).copy(alpha = 0.3f + depth * 0.6f)
            drawLine(col, pIn, p, if (major) 3f else 1.5f)
            if (b % 45 == 0) {
                val label = when (b) { 0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"; 45 -> "NE"; 135 -> "SE"; 225 -> "SW"; else -> "NW" }
                val sz = (if (major) 46f else 30f) * (0.7f + depth * 0.6f)
                val tcol = if (b == 0) NORTH else if (major) primary else ink
                val (lp, _) = pt(b.toFloat(), 1.18f)
                drawContext.canvas.nativeCanvas.drawText(label, lp.x, lp.y + sz * 0.35f, android.graphics.Paint().apply {
                    color = tcol.copy(alpha = 0.45f + depth * 0.55f).toArgb()
                    textAlign = android.graphics.Paint.Align.CENTER; textSize = sz; isAntiAlias = true; isFakeBoldText = major
                })
            }
            b += 15
        }
        val tip = Offset(cx, cy - ry - 14f)
        drawPath(Path().apply { moveTo(tip.x, tip.y + 26f); lineTo(tip.x - 13f, tip.y); lineTo(tip.x + 13f, tip.y); close() }, primary)
    }
}

// ---- Radar ------------------------------------------------------------------

private fun blipColor(kind: SampleKind): Color = when (kind) {
    SampleKind.AP, SampleKind.STATION -> Color(0xFF22D3EE)
    SampleKind.BT -> NORTH
    else -> Color(0xFF8FA189)
}

@Composable
private fun RadarContent(heading: Float, sweep: Float, results: List<PhoneScanRow>) {
    val ink = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val cx = size.width / 2f; val cy = size.height / 2f
            val rx = min(size.width, size.height) / 2f * 0.82f
            val ry = rx * 0.46f
            for (k in 1..3) drawOval(ink.copy(alpha = 0.10f), Offset(cx - rx * k / 3, cy - ry * k / 3),
                Size(rx * 2 * k / 3, ry * 2 * k / 3), style = Stroke(1.5f))
            fun pos(angleDeg: Float, frac: Float): Offset {
                val phi = Math.toRadians((angleDeg - heading).toDouble())
                return Offset(cx + (rx * frac * sin(phi)).toFloat(), cy - (ry * frac * cos(phi)).toFloat())
            }
            // Sweep line.
            val sweepEnd = pos(sweep, 1f)
            drawLine(primary.copy(alpha = 0.5f), Offset(cx, cy), sweepEnd, 2.5f)
            // Device blips: distance from signal, a stable pseudo-angle per device.
            results.take(40).forEach { r ->
                val strength = ((r.rssi + 95f) / 65f).coerceIn(0f, 1f)
                val frac = (1f - strength) * 0.92f + 0.06f
                val ang = ((r.id.hashCode() % 360) + 360) % 360
                val p = pos(ang.toFloat(), frac)
                var diff = abs(((ang - sweep + 540) % 360) - 180)
                val glow = (1f - (diff / 60f)).coerceIn(0f, 1f)
                val c = blipColor(r.kind)
                drawCircle(c.copy(alpha = 0.3f + glow * 0.6f), 5f + glow * 5f, p)
            }
            // Heading marker (N).
            val np = pos(0f, 1.02f)
            drawCircle(NORTH, 4f, np)
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${results.size} in range", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("cyan = Wi-Fi · pink = Bluetooth · nearer the middle = stronger",
                style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.55f))
        }
    }
}

// ---- Locator ----------------------------------------------------------------

@Composable
private fun LocatorContent(results: List<PhoneScanRow>, context: Context, initialTargetId: String? = null) {
    var targetId by remember { mutableStateOf(initialTargetId) }
    val target = results.firstOrNull { it.id == targetId }
    val currentTarget by rememberUpdatedState(target)

    // Pulse faster the closer you get (stronger signal). The target routinely drops out of a
    // scan cycle for a moment, so wait it out instead of breaking — otherwise one blip ends the
    // haptics for the rest of the session even after the device comes back.
    LaunchedEffect(targetId) {
        while (targetId != null) {
            val t = currentTarget
            if (t == null) { delay(500); continue }
            val strength = ((t.rssi + 95f) / 65f).coerceIn(0f, 1f)
            buzz(context, (20 + strength * 80).toInt())
            delay((1200 - strength * 1050).toLong().coerceIn(140, 1200))
        }
    }

    if (target != null) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LocatorRing(target)
            TextButton(onClick = { targetId = null }) { Text("Pick a different device") }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pick a device to home in on — then walk toward it. It buzzes faster as you get closer.",
                style = MaterialTheme.typography.bodyMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.id }) { row ->
                    Card(
                        Modifier.fillMaxWidth().clickable { targetId = row.id },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(row.label.ifBlank { row.id }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text(row.id, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                            Text("${row.rssi} dBm", style = MaterialTheme.typography.labelLarge,
                                fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocatorRing(row: PhoneScanRow) {
    val rssi = row.rssi
    val strength = ((rssi + 95f) / 65f).coerceIn(0f, 1f)
    val color = when {
        strength > 0.75f -> Color(0xFF34D399)
        strength > 0.45f -> Color(0xFFF59E0B)
        else -> Color(0xFFFF5470)
    }
    val d = 10.0.pow(((-40.0) - rssi) / (10.0 * 2.7))
    val near = when {
        d < 2 -> "Right here"; d < 5 -> "Very close"; d < 15 -> "Close"; d < 40 -> "Nearby"; else -> "Far — keep moving"
    }
    Text(row.label.ifBlank { row.id }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    Box(
        Modifier.fillMaxWidth(0.72f).aspectRatio(1f).drawBehind {
            val r = min(size.width, size.height) / 2f
            drawCircle(color.copy(alpha = 0.12f), r)
            drawCircle(color.copy(alpha = 0.35f), r * (0.4f + strength * 0.6f), style = Stroke(10f))
            drawCircle(color, r * (0.4f + strength * 0.6f) * 0.5f, style = Stroke(6f))
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$rssi dB", style = MaterialTheme.typography.headlineMedium, color = color,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(near, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        }
    }
    Text("~${d.toInt()} m away · buzzes faster as you get warmer", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
}

private fun buzz(context: Context, amplitudePct: Int) {
    runCatching {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(55, (amplitudePct * 255 / 100).coerceIn(1, 255)))
        } else {
            @Suppress("DEPRECATION") vib.vibrate(55)
        }
    }
}
