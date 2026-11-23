package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.OrbitClass
import com.cak3d.biscuitmesh.geo.SatGeo
import com.cak3d.biscuitmesh.geo.Sgp4
import com.cak3d.biscuitmesh.geo.TleClient
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/** One coastline / country-border / lake outline as a flat [lon0, lat0, lon1, lat1, …] array. */
private class GeoLine(val water: Boolean, val lonlat: FloatArray)

/** Load the bundled Natural Earth vector outlines (countries + coastlines + lakes). */
private fun loadWorldVec(ctx: Context): List<GeoLine> = runCatching {
    ctx.assets.open("world_vec.txt").bufferedReader().useLines { lines ->
        lines.mapNotNull { line ->
            val bar = line.indexOf('|'); if (bar < 1) return@mapNotNull null
            val water = line[0] == 'W'
            val parts = line.substring(bar + 1).split(';')
            val arr = FloatArray(parts.size * 2); var i = 0
            for (p in parts) {
                val comma = p.indexOf(','); if (comma < 1) return@mapNotNull null
                arr[i++] = p.substring(0, comma).toFloat()
                arr[i++] = p.substring(comma + 1).toFloat()
            }
            GeoLine(water, arr)
        }.toList()
    }
}.getOrDefault(emptyList())

private fun classColor(c: OrbitClass): Color = when (c) {
    OrbitClass.LEO -> Color(0xFFFFD166) // yellow
    OrbitClass.MEO -> Color(0xFF22D3EE) // cyan
    OrbitClass.GEO -> Color(0xFFFF9F45) // orange
    OrbitClass.HEO -> Color(0xFFA78BFA) // purple
}

/**
 * Orbit Globe (beta) — a native, SGP4-tracked globe. Fetches notable satellites (stations +
 * bright "visual" birds) from Celestrak, propagates each to the current second with the validated
 * [Sgp4], and plots its real sub-point on a spinning Earth, coloured by orbit class. Drag to spin,
 * tap a satellite to read it. A real-data preview of the native-rebuild path (not the 32k globe).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitGlobeScreen(vm: MainViewModel, onBack: () -> Unit) {
    val tleClient = remember { TleClient() }
    val ctx = LocalContext.current
    var world by remember { mutableStateOf<List<GeoLine>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) { world = withContext(Dispatchers.Default) { loadWorldVec(ctx) } }
    var sats by remember { mutableStateOf<List<SatGeo>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var observer by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var selected by remember { mutableStateOf<SatGeo?>(null) }

    var rotY by remember { mutableFloatStateOf(0f) }
    var rotX by remember { mutableFloatStateOf(0.4f) }
    var autoSpin by remember { mutableStateOf(true) }

    // Observer fix (optional — just for the "You" marker).
    LaunchedEffectSafe { runCatching { observer = vm.locationUpdates().first().let { it.latitude to it.longitude } } }

    // Fetch TLEs once, then re-propagate every second.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val tles = (tleClient.group(TleClient.STATIONS) + tleClient.group(TleClient.VISUAL))
            .distinctBy { it.noradId }
        loaded = true
        while (true) {
            val now = System.currentTimeMillis()
            sats = tles.mapNotNull { Sgp4.geoNow(it, now) }
            delay(1000)
        }
    }
    // Idle auto-spin (30 fps — lighter now the globe draws coastlines each frame).
    androidx.compose.runtime.LaunchedEffect(autoSpin) {
        while (autoSpin) { rotY += 0.005f; delay(33) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Orbit Globe", fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Orbit Globe (beta)",
                        "A native globe that tracks notable satellites with real SGP4 propagation " +
                            "from live Celestrak TLEs — the ISS, Tiangong and the bright 'visual' " +
                            "satellites — coloured by orbit class. Drag to spin, tap a dot to read it. " +
                            "This is a preview of the native path; the full 32k globe is the embedded " +
                            "Orbital Radar portal under Vision.",
                    )
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            // Precomputed screen points for hit-testing (filled during draw).
            val hits = remember { ArrayList<Triple<Float, Float, SatGeo>>() }

            Canvas(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { autoSpin = false },
                    ) { change, drag ->
                        change.consume()
                        rotY += drag.x * 0.005f
                        rotX = (rotX + drag.y * 0.005f).coerceIn(-1.4f, 1.4f)
                    }
                }.pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val near = hits.minByOrNull { (x, y, _) ->
                            val dx = x - tap.x; val dy = y - tap.y; dx * dx + dy * dy
                        }
                        val hit = near?.takeIf { (x, y, _) ->
                            val dx = x - tap.x; val dy = y - tap.y; dx * dx + dy * dy < 90f * 90f
                        }?.third
                        selected = hit
                        if (hit != null) autoSpin = false   // freeze so the selected sat stays put
                    }
                },
            ) {
                hits.clear()
                val r = size.minDimension / 2f * 0.82f
                val c = Offset(size.width / 2f, size.height / 2f)
                val cx = cos(rotX); val sx = sin(rotX)
                val cy = cos(rotY); val sy = sin(rotY)

                fun project(latDeg: Double, lonDeg: Double, radius: Float): Triple<Float, Float, Float> {
                    val la = Math.toRadians(latDeg); val lo = Math.toRadians(lonDeg)
                    var x = (cos(la) * cos(lo)).toFloat()
                    val y0 = sin(la).toFloat()
                    var z = (cos(la) * sin(lo)).toFloat()
                    // spin around Y
                    val x1 = x * cy + z * sy; val z1 = -x * sy + z * cy
                    // tilt around X
                    val y = y0 * cx - z1 * sx; val z2 = y0 * sx + z1 * cx
                    x = x1
                    return Triple(c.x + x * radius, c.y - y * radius, z2)
                }

                // Earth disc + graticule.
                drawCircle(Color(0xFF0B2A4A), radius = r, center = c)
                drawCircle(Color(0xFF2E6FB0).copy(alpha = 0.5f), radius = r, center = c, style = Stroke(width = 2f))
                for (lat in -60..60 step 30) {
                    var prev: Triple<Float, Float, Float>? = null
                    var lon = -180
                    while (lon <= 180) {
                        val p = project(lat.toDouble(), lon.toDouble(), r)
                        val pv = prev
                        if (pv != null && (p.third > 0 || pv.third > 0)) {
                            drawLine(Color(0xFF2E6FB0).copy(alpha = 0.25f), Offset(pv.first, pv.second), Offset(p.first, p.second), strokeWidth = 1f)
                        }
                        prev = p; lon += 15
                    }
                }
                for (lon in -180..150 step 30) {
                    var prev: Triple<Float, Float, Float>? = null
                    var lat = -90
                    while (lat <= 90) {
                        val p = project(lat.toDouble(), lon.toDouble(), r)
                        val pv = prev
                        if (pv != null && (p.third > 0 || pv.third > 0)) {
                            drawLine(Color(0xFF2E6FB0).copy(alpha = 0.2f), Offset(pv.first, pv.second), Offset(p.first, p.second), strokeWidth = 1f)
                        }
                        prev = p; lat += 15
                    }
                }

                // Coastlines, country borders (green) & lakes (blue) — front hemisphere only.
                if (world.isNotEmpty()) {
                    val landPath = Path(); val waterPath = Path()
                    world.forEach { gl ->
                        val path = if (gl.water) waterPath else landPath
                        var pen = false
                        val a = gl.lonlat; var k = 0
                        while (k < a.size) {
                            val p = project(a[k + 1].toDouble(), a[k].toDouble(), r); k += 2
                            if (p.third > 0f) {
                                if (!pen) { path.moveTo(p.first, p.second); pen = true } else path.lineTo(p.first, p.second)
                            } else pen = false
                        }
                    }
                    drawPath(landPath, color = Color(0xFF4F9E74), style = Stroke(width = 1.1f))
                    drawPath(waterPath, color = Color(0xFF3B8ED0), style = Stroke(width = 1f))
                }

                // Observer.
                observer?.let { (la, lo) ->
                    val p = project(la, lo, r)
                    if (p.third > 0) {
                        drawCircle(Color(0xFFFFD166), radius = 5f, center = Offset(p.first, p.second))
                        drawCircle(Color(0xFFFFD166).copy(alpha = 0.3f), radius = 11f, center = Offset(p.first, p.second), style = Stroke(width = 2f))
                    }
                }

                // Satellites at their real sub-points, lifted by (compressed) altitude.
                sats.forEach { s ->
                    val lift = 1f + (s.altKm / 6371.0).coerceAtMost(0.5).toFloat()
                    val p = project(s.latDeg, s.lonDeg, r * lift)
                    if (p.third > 0) {
                        val col = classColor(s.orbitClass)
                        val sel = selected?.tle?.noradId == s.tle.noradId
                        drawCircle(col, radius = if (sel) 5.5f else 3.2f, center = Offset(p.first, p.second))
                        if (sel) drawCircle(col.copy(alpha = 0.35f), radius = 12f, center = Offset(p.first, p.second), style = Stroke(width = 2f))
                        hits.add(Triple(p.first, p.second, s))
                    }
                }
            }

            if (!loaded) {
                SectionLoader("ORBIT GLOBE", MaterialTheme.colorScheme.primary, Icons.Filled.Public, seed = 4719, subtitle = "ACQUIRING TLES")
            }

            // Count + legend.
            Text(
                "${sats.size} tracked · SGP4 live",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
            Row(
                Modifier.align(Alignment.TopEnd).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("LEO" to OrbitClass.LEO, "MEO" to OrbitClass.MEO, "GEO" to OrbitClass.GEO, "HEO" to OrbitClass.HEO).forEach { (lbl, cl) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Canvas(Modifier.size(8.dp)) { drawCircle(classColor(cl)) }
                        Text(lbl, color = classColor(cl), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                    }
                }
            }

            selected?.let { s ->
                SatCard(s, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun SatCard(s: SatGeo, modifier: Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(12.dp)
            .border(1.5.dp, classColor(s.orbitClass).copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(s.tle.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = classColor(s.orbitClass))
                Text("NORAD ${s.tle.noradId}", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Text(
                "${s.orbitClass} · ${"%.2f".format(s.latDeg)}°, ${"%.2f".format(s.lonDeg)}° · alt ${s.altKm.toInt()} km",
                fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
            Text(
                "incl ${"%.1f".format(s.tle.inclinationDeg)}° · ${"%.2f".format(s.tle.meanMotion)} rev/day · period ${(1440.0 / s.tle.meanMotion).toInt()} min",
                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Text("tap empty space to close", fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Small helper so the one-shot observer fetch reads clearly. */
@Composable
private fun LaunchedEffectSafe(block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}
