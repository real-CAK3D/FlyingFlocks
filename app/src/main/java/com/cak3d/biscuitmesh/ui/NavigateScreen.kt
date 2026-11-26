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
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.material3.Switch
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
fun NavigateScreen(
    vm: MainViewModel, onBack: () -> Unit, startMode: NavMode,
    startTargetId: String? = null, onOpenTargets: () -> Unit = {},
) {
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
                actions = {
                    HelpButton(
                        mode.label,
                        when (mode) {
                            NavMode.COMPASS -> "A live compass from the phone's magnetometer — which way you're " +
                                "facing. Hold the phone flat and away from metal for the steadiest heading."
                            NavMode.RADAR -> "A sweeping radar of the Wi-Fi/BLE devices around you, placed by signal " +
                                "strength (closer = stronger). It's a rough proximity view, not a true bearing."
                            NavMode.LOCATOR -> "Pick one device and home in on it — the ring tightens and the phone " +
                                "buzzes faster as the signal strengthens. Great for finding a specific tag or board."
                        },
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (mode) {
                    NavMode.COMPASS -> CompassContent(heading)
                    NavMode.RADAR -> RadarContent(heading, sweep, results)
                    NavMode.LOCATOR -> LocatorContent(vm, results, context, heading, onOpenTargets, startTargetId)
                }
            }
            androidx.compose.foundation.layout.Box(Modifier.help("View switch", "Flip between Compass (point toward a target), Radar (devices sweeping around you) and Locator (hone in on one). Pick a target in Targets first.")) {
                ModeSwitcher(mode) { mode = it }
            }
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
                // Signed lead angle: positive while the beam is still approaching the blip,
                // negative once it has swept past. Light the blip BEFORE the line reaches it
                // (long approach ramp) and let it fade fast behind the beam (short tail).
                val lead = ((ang - sweep + 540) % 360) - 180
                val glow = if (lead >= 0f) (1f - lead / 72f).coerceIn(0f, 1f)
                           else (1f + lead / 16f).coerceIn(0f, 1f)
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

private fun heatColor(strength: Float): Color = when {
    strength > 0.75f -> Color(0xFF34D399)
    strength > 0.45f -> Color(0xFFF59E0B)
    else -> Color(0xFFFF5470)
}

@Composable
private fun LocatorContent(
    vm: MainViewModel, results: List<PhoneScanRow>, context: Context, heading: Float,
    onOpenTargets: () -> Unit, initialTargetId: String? = null,
) {
    val savedTargets by vm.targets.collectAsStateWithLifecycle(initialValue = emptyList())
    val fix by vm.currentFix.collectAsStateWithLifecycle(initialValue = null)
    var wanted by remember { mutableStateOf(initialTargetId) }
    var locTarget by remember { mutableStateOf<com.cak3d.biscuitmesh.data.Target?>(null) }
    var locatorOn by remember { mutableStateOf(true) }
    var kindFilter by remember { mutableStateOf<SampleKind?>(null) }   // null = all

    // A saved LOCATION target uses true GPS bearing + distance instead of RSSI.
    locTarget?.let { lt ->
        GpsLocatorView(lt, fix, heading, context, locatorOn,
            onToggle = { locatorOn = it }, onClear = { locTarget = null })
        return
    }

    // A network/device target is matched against the live scan by id (MAC) or label (SSID).
    val target = results.firstOrNull { it.id.equals(wanted, true) || it.label.equals(wanted, true) }
    val currentTarget by rememberUpdatedState(target)
    val currentHeading by rememberUpdatedState(heading)

    // "Warmer this way" direction: handheld RSSI direction-finding. We bucket the target's
    // RSSI by the compass heading it was heard at; the strongest bucket is the hot bearing.
    // Turning/moving sweeps the signal across headings so the arrow converges on the target.
    val hotByHeading = remember { FloatArray(36) { -200f } }
    var hotHeading by remember { mutableStateOf<Float?>(null) }
    var liveStrength by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(wanted, locatorOn) {
        for (i in hotByHeading.indices) hotByHeading[i] = -200f
        hotHeading = null
        while (wanted != null && locatorOn) {
            val t = currentTarget
            if (t == null) { delay(500); continue }
            val strength = ((t.rssi + 95f) / 65f).coerceIn(0f, 1f)
            liveStrength = strength
            val b = ((((currentHeading % 360f) + 360f) % 360f) / 10f).toInt().coerceIn(0, 35)
            for (i in hotByHeading.indices) hotByHeading[i] -= 1.2f          // slow decay
            if (t.rssi.toFloat() > hotByHeading[b]) hotByHeading[b] = t.rssi.toFloat()
            var bestB = 0; var bestV = -1000f
            for (i in hotByHeading.indices) if (hotByHeading[i] > bestV) { bestV = hotByHeading[i]; bestB = i }
            hotHeading = if (bestV > -199f) bestB * 10f + 5f else null
            buzz(context, (20 + strength * 80).toInt())
            delay((1200 - strength * 1050).toLong().coerceIn(140, 1200))
        }
    }

    if (wanted != null) {
        val relBearing = hotHeading?.let { ((it - currentHeading + 540f) % 360f) - 180f }
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (locatorOn) "Locating" else "Paused", style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    color = if (locatorOn) heatColor(liveStrength) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Switch(checked = locatorOn, onCheckedChange = { locatorOn = it })
            }
            val tgt = target
            if (tgt != null) {
                LocatorRing(tgt, if (locatorOn) liveStrength else ((tgt.rssi + 95f) / 65f).coerceIn(0f, 1f),
                    relBearing, locatorOn)
            } else {
                Text("Searching for “$wanted” — not in range yet.\nKeep the scan running and move around.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            TextButton(onClick = { wanted = null }) { Text("Pick a different target") }
        }
    } else {
        val shown = results.filter {
            when (kindFilter) {
                SampleKind.AP -> it.kind == SampleKind.AP || it.kind == SampleKind.STATION
                SampleKind.BT -> it.kind == SampleKind.BT
                else -> true
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Pick a network, device or saved target to home in on — the bar fills and flashes, " +
                    "the arrow points the way, and the phone buzzes faster as you get closer.",
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (savedTargets.isNotEmpty()) {
                item {
                    Text("SAVED TARGETS", style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                }
                items(savedTargets, key = { "t-${it.id}" }) { t ->
                    val tag = when (t.kind) {
                        com.cak3d.biscuitmesh.data.TargetKind.NETWORK -> "NET"
                        com.cak3d.biscuitmesh.data.TargetKind.DEVICE -> "DEV"
                        else -> "LOC"
                    }
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            if (t.kind == com.cak3d.biscuitmesh.data.TargetKind.LOCATION) locTarget = t
                            else wanted = t.value
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(tag, modifier = Modifier.padding(end = 10.dp),
                                style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(t.label.ifBlank { t.value }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text(t.value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1)
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill("All", kindFilter == null) { kindFilter = null }
                    FilterPill("Wi-Fi", kindFilter == SampleKind.AP) { kindFilter = SampleKind.AP }
                    FilterPill("BLE", kindFilter == SampleKind.BT) { kindFilter = SampleKind.BT }
                }
            }
            item {
                Text("IN RANGE · ${shown.size}", style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold)
            }
            items(shown, key = { "s-${it.id}" }) { row ->
                Card(
                    Modifier.fillMaxWidth().clickable { wanted = row.id },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.padding(end = 10.dp)) {
                            Text(if (row.kind == SampleKind.BT) "BLE" else "WiFi",
                                style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                                color = blipColor(row.kind))
                        }
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
            item {
                TextButton(onClick = onOpenTargets) { Text("＋  Manage targets") }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, on: Boolean, onClick: () -> Unit) {
    val c = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    Box(
        Modifier.clickable(onClick = onClick)
            .drawBehind {
                drawRoundRect(c.copy(alpha = if (on) 0.18f else 0.06f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f))
            }
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = c)
    }
}

@Composable
private fun LocatorRing(row: PhoneScanRow, strength: Float, relBearing: Float?, active: Boolean) {
    val rssi = row.rssi
    val color = heatColor(strength)
    val d = 10.0.pow(((-40.0) - rssi) / (10.0 * 2.7))
    val near = when {
        d < 2 -> "Right here"; d < 5 -> "Very close"; d < 15 -> "Close"; d < 40 -> "Nearby"; else -> "Far — keep moving"
    }
    // flash when very close (and active)
    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(360, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "b",
    )
    val hot = active && strength > 0.8f
    val barAlpha = if (hot) blink else 1f

    Text(row.label.ifBlank { row.id }, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold, maxLines = 1)

    // Direction dial — the "warmer this way" arrow.
    Box(Modifier.fillMaxWidth(0.42f).aspectRatio(1f), contentAlignment = Alignment.Center) {
        val ink = MaterialTheme.colorScheme.onSurface
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = min(size.width, size.height) / 2f * 0.92f
            drawCircle(ink.copy(alpha = 0.06f), r)
            drawCircle(ink.copy(alpha = 0.18f), r, style = Stroke(2f))
            if (relBearing != null && active) {
                val phi = Math.toRadians(relBearing.toDouble())
                val tip = Offset(c.x + (r * 0.82f * sin(phi)).toFloat(), c.y - (r * 0.82f * cos(phi)).toFloat())
                drawLine(color, c, tip, 9f)
                drawCircle(color, 9f, tip)
            } else {
                drawCircle(ink.copy(alpha = 0.3f), 6f, c)
            }
        }
        Text(if (relBearing != null && active) "WARMER" else "turn\nslowly",
            style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.BottomCenter))
    }

    Text("$rssi dB · $near", style = MaterialTheme.typography.headlineSmall, color = color,
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

    // Proximity BAR — fills with strength, lights the colour, and flashes when very close.
    Box(
        Modifier.fillMaxWidth(0.82f).padding(top = 2.dp).drawBehind {
            val h = size.height
            val rr = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f)
            drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = rr)
            val w = size.width * strength.coerceIn(0.02f, 1f)
            drawRoundRect(color.copy(alpha = barAlpha),
                size = Size(w, h), cornerRadius = rr)
        }.aspectRatio(10f),
    ) {}

    Text("~${d.toInt()} m away · ${if (active) "buzzes + flashes as you get warmer" else "paused"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
}

private fun gpsNear(distM: Float): String = when {
    distM < 0f -> "—"
    distM < 3f -> "You're here · ${distM.roundToInt()} m"
    distM < 1000f -> "${distM.roundToInt()} m away"
    else -> "%.1f km away".format(distM / 1000f)
}

/** Locate a saved LOCATION target by true GPS bearing + distance (real direction, not RSSI). */
@Composable
private fun GpsLocatorView(
    t: com.cak3d.biscuitmesh.data.Target, fix: Pair<Double, Double>?, heading: Float,
    context: Context, active: Boolean, onToggle: (Boolean) -> Unit, onClear: () -> Unit,
) {
    val ll = t.latLon
    val dist: Float
    val bearing: Float
    if (fix != null && ll != null) {
        val out = FloatArray(2)
        android.location.Location.distanceBetween(fix.first, fix.second, ll.first, ll.second, out)
        dist = out[0]; bearing = out[1]
    } else {
        dist = -1f; bearing = 0f
    }
    val strength = if (dist < 0f) 0f else (1f - dist / 200f).coerceIn(0f, 1f)   // closer = stronger
    val color = heatColor(strength)
    val relBearing = ((bearing - heading + 540f) % 360f) - 180f

    val curStrength by rememberUpdatedState(strength)
    LaunchedEffect(active) {
        while (active) {
            val s = curStrength
            buzz(context, (20 + s * 80).toInt())
            delay((1200 - s * 1050).toLong().coerceIn(140, 1200))
        }
    }
    val blink by rememberInfiniteTransition(label = "gblink").animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(360, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "gb",
    )
    val barAlpha = if (active && strength > 0.8f) blink else 1f
    val ink = MaterialTheme.colorScheme.onSurface

    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (active) "Locating" else "Paused", style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace, color = if (active) color else ink.copy(alpha = 0.5f))
            Switch(checked = active, onCheckedChange = onToggle)
        }
        Text(t.label.ifBlank { t.value }, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
        Box(Modifier.fillMaxWidth(0.52f).aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = min(size.width, size.height) / 2f * 0.92f
                drawCircle(ink.copy(alpha = 0.06f), r)
                drawCircle(ink.copy(alpha = 0.18f), r, style = Stroke(2f))
                if (fix != null) {
                    val phi = Math.toRadians(relBearing.toDouble())
                    val tip = Offset(c.x + (r * 0.82f * sin(phi)).toFloat(), c.y - (r * 0.82f * cos(phi)).toFloat())
                    drawLine(color, c, tip, 10f)
                    drawCircle(color, 10f, tip)
                }
            }
            Text(if (fix != null) cardinal16(((bearing % 360f) + 360f) % 360f) else "GPS…",
                style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                color = ink.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.BottomCenter))
        }
        if (fix == null) {
            Text("Waiting for a GPS fix…", style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.6f))
        } else {
            Text(gpsNear(dist), style = MaterialTheme.typography.headlineSmall, color = color,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Box(
                Modifier.fillMaxWidth(0.82f).aspectRatio(10f).drawBehind {
                    val h = size.height
                    val rr = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f)
                    drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = rr)
                    drawRoundRect(color.copy(alpha = barAlpha),
                        size = Size(size.width * strength.coerceIn(0.02f, 1f), h), cornerRadius = rr)
                },
            ) {}
        }
        TextButton(onClick = onClear) { Text("Pick a different target") }
    }
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
