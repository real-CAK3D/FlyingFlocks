package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.SkyConditions
import com.cak3d.biscuitmesh.geo.Twilight
import com.cak3d.biscuitmesh.geo.nextTrueDark
import com.cak3d.biscuitmesh.geo.skyConditions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun darknessColor(d: Double): Color = when {
    d < 0.25 -> Color(0xFF5CB4E8) // bright / daylight-ish
    d < 0.5 -> Color(0xFFA78BFA)
    d < 0.75 -> Color(0xFF6366F1)
    else -> Color(0xFF22D3EE)
}

/**
 * Sky Conditions — real-time light pollution. Takes the site's Bortle class (yours to set) and
 * modulates it by the live Sun (twilight) and Moon (phase + altitude) from [skyConditions] to show
 * how dark the sky is *right now*, what you can see, and when it next gets properly dark.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyConditionsScreen(vm: MainViewModel, onBack: () -> Unit) {
    var fix by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var bortle by rememberSaveable { mutableStateOf(5) }
    var cond by remember { mutableStateOf<SkyConditions?>(null) }
    var nextDark by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) { runCatching { vm.locationUpdates().first().let { fix = it.latitude to it.longitude } } }
    LaunchedEffect(fix, bortle) {
        val f = fix ?: return@LaunchedEffect
        nextDark = nextTrueDark(f.first, f.second, System.currentTimeMillis())
        while (true) {
            cond = skyConditions(f.first, f.second, System.currentTimeMillis(), bortle)
            delay(30_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sky Conditions") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Sky Conditions — live light pollution",
                        "Set your site's Bortle class (how light-polluted your sky normally is), and " +
                            "this shows how dark it is right now by folding in the live Sun (twilight) " +
                            "and Moon (phase & altitude) — plus what you can see and when it next gets " +
                            "properly dark. All computed on-device from your location.",
                    )
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val c = cond
            if (fix == null) {
                Text("Waiting for a location fix…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace)
            } else if (c == null) {
                Text("Reading the sky…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace)
            } else {
                // Darkness gauge.
                val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                Box(Modifier.fillMaxWidth().help("Darkness gauge", "How dark your sky is right now, 0–100%. It takes your site's Bortle class and folds in the live Sun (twilight) and Moon (phase & altitude). Higher is darker and better for stargazing."), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(180.dp)) {
                        val r = size.minDimension / 2f * 0.82f
                        val ctr = Offset(size.width / 2f, size.height / 2f)
                        drawArc(
                            color = trackColor,
                            startAngle = 135f, sweepAngle = 270f, useCenter = false,
                            topLeft = Offset(ctr.x - r, ctr.y - r), size = Size(r * 2, r * 2),
                            style = Stroke(width = 16f),
                        )
                        drawArc(
                            color = darknessColor(c.darkness),
                            startAngle = 135f, sweepAngle = (270f * c.darkness).toFloat(), useCenter = false,
                            topLeft = Offset(ctr.x - r, ctr.y - r), size = Size(r * 2, r * 2),
                            style = Stroke(width = 16f),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(c.darkness * 100).toInt()}%", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                            color = darknessColor(c.darkness), fontFamily = FontFamily.Monospace)
                        Text("DARK", fontSize = 11.sp, letterSpacing = 3.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
                    }
                }

                InfoCard {
                    Text(c.twilight.label, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = darknessColor(c.darkness))
                    Text(c.canSee, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    InfoCard(Modifier.weight(1f)) {
                        Text("SUN", fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                        Text("${c.sunElDeg.toInt()}°", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(if (c.sunElDeg > 0) "above horizon" else "below horizon", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    InfoCard(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MoonGlyph(c.moonIllum, Modifier.size(26.dp))
                            Column {
                                Text("MOON", fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                                Text("${(c.moonIllum * 100).toInt()}%", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("${c.moonPhase} · ${if (c.moonUp) "up ${c.moonElDeg.toInt()}°" else "down"}", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }

                InfoCard {
                    Text("Limiting magnitude now: ${"%.1f".format(c.effectiveNelm)}  (site best ${"%.1f".format(c.baseNelm)})",
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    nextDark?.let {
                        val fmt = SimpleDateFormat("EEE h:mm a", Locale.getDefault())
                        Text("Next true dark: ${fmt.format(Date(it))}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    } ?: Text("No fully-dark window in the next 24 h (Moon or twilight)", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }

                InfoCard(Modifier.help("Bortle class", "Set how light-polluted your sky normally is on a 1–9 scale (1 = pristine dark-sky site, 9 = inner city). This is the baseline the live darkness gauge builds on — set it once for where you observe.")) {
                    Text("YOUR SITE'S BORTLE CLASS: $bortle", fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Slider(value = bortle.toFloat(), onValueChange = { bortle = it.toInt().coerceIn(1, 9) },
                        valueRange = 1f..9f, steps = 7)
                    Text(bortleDesc(bortle), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }
    }
}

private fun bortleDesc(b: Int): String = when (b) {
    1 -> "1 · Pristine dark-sky site"
    2 -> "2 · Truly dark rural sky"
    3 -> "3 · Rural sky"
    4 -> "4 · Rural/suburban transition"
    5 -> "5 · Suburban sky"
    6 -> "6 · Bright suburban sky"
    7 -> "7 · Suburban/urban transition"
    8 -> "8 · City sky"
    else -> "9 · Inner-city sky"
}

@Composable
private fun InfoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

/** A little moon disc showing the illuminated fraction. */
@Composable
private fun MoonGlyph(illum: Double, modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val ctr = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color(0xFF3A3A4A), radius = r, center = ctr)
        // Lit half as a simple right-hand disc; its width scaled by the illuminated fraction.
        drawArc(
            color = Color(0xFFE8E8D0),
            startAngle = -90f, sweepAngle = 180f, useCenter = true,
            topLeft = Offset(ctr.x - r, ctr.y - r), size = Size(r * 2, r * 2),
        )
        val shadow = ((1.0 - illum) * 2 * r).toFloat()
        if (shadow > 0.5f) drawCircle(Color(0xFF3A3A4A), radius = r, center = Offset(ctr.x + r - shadow / 2f, ctr.y))
    }
}
