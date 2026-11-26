package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElevationScreen(vm: MainViewModel, onBack: () -> Unit) {
    val mph by vm.useMph.collectAsStateWithLifecycle()
    var elev by remember { mutableStateOf<List<Double>?>(null) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val byDrive = runCatching { vm.captureSamplesByDrive(limit = 1) }.getOrDefault(emptyList())
        val pts = byDrive.firstOrNull()?.second?.filter { it.timeMs > 0 && !(it.lat == 0.0 && it.lon == 0.0) }
            ?.sortedBy { it.timeMs } ?: emptyList()
        if (pts.isEmpty()) { done = true; return@LaunchedEffect }
        val step = (pts.size / 80).coerceAtLeast(1)
        val sampled = pts.filterIndexed { i, _ -> i % step == 0 }.take(100).map { it.lat to it.lon }
        elev = ElevationClient.elevations(sampled)
        done = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Elevation Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton("Elevation Profile",
                        "The terrain you climbed and descended on your most recent drive. We sample your path and " +
                            "look up real ground elevation for each point (keyless), then chart it with total gain, " +
                            "loss and the high and low points.")
                },
            )
        },
    ) { pad ->
        val e = elev
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                !done -> CenterMsg {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = EL_ACCENT)
                    Text("Reading the terrain…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp)
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
                    val toFt = mph
                    fun show(m: Double) = if (toFt) "${(m * 3.28084).toInt()} ft" else "${m.toInt()} m"

                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Stat("▲ ${show(gain)}", "climb", Color(0xFFFF8A3D))
                            Stat("▼ ${show(loss)}", "descent", Color(0xFF5CB4E8))
                            Stat(show(hi - lo), "range", EL_ACCENT)
                        }
                        Canvas(Modifier.fillMaxWidth().height(220.dp)) {
                            val n = e.size
                            val range = (hi - lo).coerceAtLeast(1.0)
                            fun px(i: Int) = size.width * i / (n - 1).toFloat()
                            fun py(v: Double) = size.height * (1f - ((v - lo) / range).toFloat()) * 0.88f + size.height * 0.06f
                            // Baseline grid.
                            for (g in 0..4) {
                                val y = size.height * g / 4f
                                drawLine(Color(0xFF2A3540).copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1f)
                            }
                            val fill = Path().apply {
                                moveTo(0f, size.height)
                                for (i in 0 until n) lineTo(px(i), py(e[i]))
                                lineTo(size.width, size.height); close()
                            }
                            drawPath(fill, EL_ACCENT.copy(alpha = 0.16f))
                            val line = Path().apply {
                                moveTo(px(0), py(e[0])); for (i in 1 until n) lineTo(px(i), py(e[i]))
                            }
                            drawPath(line, EL_ACCENT, style = Stroke(width = 2.5f))
                        }
                        Text("low ${show(lo)} · high ${show(hi)} · ${e.size} points along the drive",
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
        }
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
