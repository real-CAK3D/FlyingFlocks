package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.ElevationClient

private val EL_ACCENT = Color(0xFF8FD694)

/**
 * Elevation Profile — the hills you actually drove. Samples your most recent drive's path, pulls
 * real terrain elevation for each point (keyless), and draws the climb-and-descent profile with
 * total gain, loss and high/low points.
 */
private fun havM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val dLat = (bLat - aLat) * 110_540.0
    val dLon = (bLon - aLon) * 111_320.0 * kotlin.math.cos(Math.toRadians((aLat + bLat) / 2))
    return Math.hypot(dLat, dLon)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElevationScreen(vm: MainViewModel, onBack: () -> Unit) {
    val mph by vm.useMph.collectAsStateWithLifecycle()
    var elev by remember { mutableStateOf<List<Double>?>(null) }
    var speedMps by remember { mutableStateOf<List<Double>>(emptyList()) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val byDrive = runCatching { vm.captureSamplesByDrive(limit = 1) }.getOrDefault(emptyList())
        val pts = byDrive.firstOrNull()?.second?.filter { it.timeMs > 0 && !(it.lat == 0.0 && it.lon == 0.0) }
            ?.sortedBy { it.timeMs } ?: emptyList()
        if (pts.size < 2) { done = true; return@LaunchedEffect }
        val step = (pts.size / 80).coerceAtLeast(1)
        val sampled = pts.filterIndexed { i, _ -> i % step == 0 }.take(100)
        // Speed between consecutive sampled points (m/s), lightly smoothed.
        val raw = DoubleArray(sampled.size)
        for (i in 1 until sampled.size) {
            val dt = (sampled[i].timeMs - sampled[i - 1].timeMs) / 1000.0
            val d = havM(sampled[i - 1].lat, sampled[i - 1].lon, sampled[i].lat, sampled[i].lon)
            raw[i] = if (dt in 0.5..3600.0) (d / dt).coerceIn(0.0, 60.0) else raw[i - 1]
        }
        if (sampled.size > 1) raw[0] = raw[1]
        speedMps = (raw.indices).map { i ->
            val lo = (i - 1).coerceAtLeast(0); val hi = (i + 1).coerceAtMost(raw.size - 1)
            (raw[lo] + raw[i] + raw[hi]) / (hi - lo + 1)
        }
        elev = ElevationClient.elevations(sampled.map { it.lat to it.lon })
        done = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Drive Stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton("Drive Stats",
                        "Your most recent drive, charted: the real terrain you climbed/descended (keyless elevation " +
                            "lookup) and your speed along the way, derived from how fast your GPS-tagged catches moved. " +
                            "G-force logging is coming to live drives next.")
                },
            )
        },
    ) { pad ->
        val e = elev
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                !done -> CenterMsg {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = EL_ACCENT)
                    Text("Reading your drive…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
                }
                e == null || e.size < 2 -> CenterMsg {
                    Text("No located drive to profile yet. Finish a wardrive with GPS-tagged captures and come back.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
                        modifier = Modifier.padding(32.dp))
                }
                else -> {
                    val lo = e.min(); val hi = e.max()
                    var gain = 0.0; var loss = 0.0
                    for (i in 1 until e.size) { val d = e[i] - e[i - 1]; if (d > 0) gain += d else loss += -d }
                    fun showDist(m: Double) = if (mph) "${(m * 3.28084).toInt()} ft" else "${m.toInt()} m"
                    fun showSpd(ms: Double) = if (mph) "${(ms * 2.23694).toInt()} mph" else "${(ms * 3.6).toInt()} km/h"
                    val maxSpd = speedMps.maxOrNull() ?: 0.0
                    val avgSpd = if (speedMps.isNotEmpty()) speedMps.average() else 0.0

                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("SPEED", style = MaterialTheme.typography.labelMedium, color = EL_ACCENT,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Stat(showSpd(maxSpd), "top speed", Color(0xFFFF5470))
                            Stat(showSpd(avgSpd), "average", Color(0xFF5CB4E8))
                            Stat("${e.size}", "points", EL_ACCENT)
                        }
                        ProfileChart(speedMps, Color(0xFFFF5470))

                        Text("ELEVATION", style = MaterialTheme.typography.labelMedium, color = EL_ACCENT,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Stat("▲ ${showDist(gain)}", "climb", Color(0xFFFF8A3D))
                            Stat("▼ ${showDist(loss)}", "descent", Color(0xFF5CB4E8))
                            Stat(showDist(hi - lo), "range", EL_ACCENT)
                        }
                        ProfileChart(e, EL_ACCENT)
                        Text("low ${showDist(lo)} · high ${showDist(hi)}",
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

/** A filled line chart of a numeric series. */
@Composable
private fun ProfileChart(series: List<Double>, tint: Color) {
    if (series.size < 2) return
    val lo = series.min(); val hi = series.max()
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val n = series.size
        val range = (hi - lo).coerceAtLeast(1e-6)
        fun px(i: Int) = size.width * i / (n - 1).toFloat()
        fun py(v: Double) = size.height * (1f - ((v - lo) / range).toFloat()) * 0.86f + size.height * 0.07f
        for (g in 0..4) {
            val y = size.height * g / 4f
            drawLine(Color(0xFF2A3540).copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1f)
        }
        val fill = Path().apply {
            moveTo(0f, size.height)
            for (i in 0 until n) lineTo(px(i), py(series[i]))
            lineTo(size.width, size.height); close()
        }
        drawPath(fill, tint.copy(alpha = 0.16f))
        val line = Path().apply {
            moveTo(px(0), py(series[0])); for (i in 1 until n) lineTo(px(i), py(series[i]))
        }
        drawPath(line, tint, style = Stroke(width = 2.5f))
    }
}

@Composable
private fun CenterMsg(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun Stat(value: String, label: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = tint, fontFamily = FontFamily.Monospace)
        Text(label.uppercase(), fontSize = 10.sp, letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}
