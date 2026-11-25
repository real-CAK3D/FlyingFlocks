package com.cak3d.biscuitmesh.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.AirQuality
import com.cak3d.biscuitmesh.geo.AirQualityClient
import com.cak3d.biscuitmesh.geo.SpaceEventsClient
import com.cak3d.biscuitmesh.geo.WeatherClient
import com.cak3d.biscuitmesh.geo.WeatherNow
import com.cak3d.biscuitmesh.geo.skyConditions
import com.cak3d.biscuitmesh.wardrive.SampleKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** One contributor to the Spot Score: a 0..1 sub-score, its reading, and a tint. */
private data class Factor(val name: String, val score: Float, val detail: String, val tint: Color)

private fun scoreColor(s: Float): Color = when {
    s >= 0.75f -> Color(0xFF5FD36A)
    s >= 0.5f -> Color(0xFFFFD166)
    s >= 0.3f -> Color(0xFFFF9F45)
    else -> Color(0xFFFF5470)
}

private fun scoreLabel(s: Int): String = when {
    s >= 80 -> "Excellent"
    s >= 65 -> "Good"
    s >= 45 -> "Fair"
    s >= 25 -> "Poor"
    else -> "Rough"
}

/** Weather clarity from a WMO code: clear skies score high, precip/fog low. */
private fun claritySub(code: Int): Float = when (code) {
    0 -> 1f; 1 -> 0.82f; 2 -> 0.6f; 3 -> 0.35f
    45, 48 -> 0.2f
    in 51..67 -> 0.15f
    in 71..77 -> 0.2f
    in 80..82 -> 0.12f
    in 95..99 -> 0.05f
    else -> 0.4f
}

/**
 * Site Survey — the one view that needs all three pillars. It reads where you're standing right now
 * across RF, sky and weather and rolls them into a single Spot Score: how clean and clear this place
 * is for stargazing, camping or a quiet RF sweep. Nowhere else in the app (or any wardriving app)
 * fuses light pollution, air quality, weather, geomagnetic activity and RF density into one number.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSurveyScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var loc by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var bortle by rememberSaveable { mutableIntStateOf(5) }
    var weather by remember { mutableStateOf<WeatherNow?>(null) }
    var air by remember { mutableStateOf<AirQuality?>(null) }
    var kp by remember { mutableStateOf<Double?>(null) }
    var loading by remember { mutableStateOf(true) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(vm) {
        vm.startPhoneScan(wifi = true, ble = true)
        onDispose { vm.stopPhoneScan() }
    }
    val rows by vm.phoneScanResults.collectAsState()
    val rfContacts = rows.count { it.rssi < 0 }

    LaunchedEffect(Unit) { runCatching { loc = vm.locationUpdates().first().let { it.latitude to it.longitude } } }
    LaunchedEffect(loc) {
        val f = loc ?: return@LaunchedEffect
        loading = true
        weather = WeatherClient().forecast(f.first, f.second)?.now
        air = AirQualityClient().air(f.first, f.second)
        kp = SpaceEventsClient().kp()
        loading = false
    }
    LaunchedEffect(Unit) { while (true) { nowMs = System.currentTimeMillis(); delay(30_000) } }

    val sky = loc?.let { skyConditions(it.first, it.second, nowMs, bortle) }

    // --- Spot Score: weighted blend of the five pillars (weights sum to 1) ---
    val factors = buildList {
        sky?.let {
            add(Factor("Dark sky", it.darkness.toFloat(),
                "Bortle $bortle · limiting mag ${"%.1f".format(it.effectiveNelm)} · ${it.twilight.label}",
                Color(0xFF8B5CF6)))
        }
        air?.usAqi?.let { aqi ->
            add(Factor("Clean air", (1f - (aqi / 180f)).coerceIn(0f, 1f), "AQI $aqi", Color(0xFF6EE7B7)))
        }
        weather?.let { w ->
            add(Factor("Clear weather", claritySub(w.code), "${w.desc} · ${w.tempF}°F", Color(0xFF5CB4E8)))
        }
        add(Factor("Quiet RF", (1f - (rfContacts / 70f)).coerceIn(0f, 1f),
            "$rfContacts Wi-Fi/BT contacts", Color(0xFF22D3EE)))
        kp?.let { k ->
            add(Factor("Calm geomag", (1.0 - k / 9.0).coerceIn(0.0, 1.0).toFloat(), "Kp ${"%.1f".format(k)}",
                Color(0xFFFFD166)))
        }
    }
    val weights = mapOf("Dark sky" to 0.34f, "Clean air" to 0.2f, "Clear weather" to 0.2f,
        "Quiet RF" to 0.16f, "Calm geomag" to 0.1f)
    val totalW = factors.sumOf { (weights[it.name] ?: 0f).toDouble() }.toFloat().coerceAtLeast(0.01f)
    val score = (factors.sumOf { ((weights[it.name] ?: 0f) * it.score).toDouble() }.toFloat() / totalW * 100f).toInt()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Site Survey") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Site Survey",
                        "A read of where you're standing right now, across all three pillars — light " +
                            "pollution, air quality, weather, geomagnetic activity and RF density — rolled " +
                            "into one Spot Score for how clean and clear this place is for stargazing, camping " +
                            "or a quiet sweep. Set the Bortle to match your sky; share the card when you find a " +
                            "good one.",
                    )
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading && weather == null && air == null) {
                item { Row(Modifier.fillMaxWidth().padding(28.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } }
            }
            // Hero score.
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("SPOT SCORE", fontSize = 11.sp, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("$score", fontSize = 52.sp, fontWeight = FontWeight.Bold,
                                color = scoreColor(score / 100f), fontFamily = FontFamily.Monospace)
                            Text(scoreLabel(score), fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                                color = scoreColor(score / 100f))
                        }
                        Text(verdict(score), fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                    }
                }
            }
            // Factor tiles.
            items(factors) { f -> FactorRow(f) }
            // Bortle slider.
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("YOUR SKY · BORTLE $bortle", fontSize = 11.sp, letterSpacing = 1.5.sp,
                            fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                        Text(bortleBlurb(bortle), fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        Slider(value = bortle.toFloat(), onValueChange = { bortle = it.toInt().coerceIn(1, 9) },
                            valueRange = 1f..9f, steps = 7)
                    }
                }
            }
            // Share.
            item {
                OutlinedButton(
                    onClick = {
                        val txt = buildString {
                            append("Spot Score $score/100 (${scoreLabel(score)})\n")
                            factors.forEach { append("• ${it.name}: ${(it.score * 100).toInt()} — ${it.detail}\n") }
                            append("— via FlockedUp Site Survey")
                        }
                        runCatching {
                            ctx.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, txt)
                                }, "Share spot"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Share this spot")
                }
            }
        }
    }
}

@Composable
private fun FactorRow(f: Factor) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${(f.score * 100).toInt()}", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = scoreColor(f.score), fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 2.dp))
            Column(Modifier.weight(1f)) {
                Text(f.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = f.tint)
                Text(f.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            // mini bar
            androidx.compose.foundation.Canvas(Modifier.padding(start = 4.dp).width(60.dp).height(6.dp)) {
                drawRect(f.tint.copy(alpha = 0.18f), size = size)
                drawRect(f.tint, size = androidx.compose.ui.geometry.Size(size.width * f.score.coerceIn(0f, 1f), size.height))
            }
        }
    }
}

private fun verdict(score: Int): String = when {
    score >= 80 -> "A genuinely clean, clear spot — great for stargazing or a quiet RF sweep."
    score >= 65 -> "Solid conditions. A good place to park and observe."
    score >= 45 -> "Mixed — one or two factors are holding it back; check the breakdown."
    score >= 25 -> "Not ideal right now. Try after dark, better weather, or a darker site."
    else -> "Rough conditions here — bright, busy or cloudy. Head somewhere quieter."
}

private fun bortleBlurb(b: Int): String = when (b) {
    1, 2 -> "Pristine dark site — the Milky Way casts shadows."
    3 -> "Rural sky — the Milky Way is structured and bright."
    4 -> "Rural/suburban transition — Milky Way visible but washed near the horizon."
    5 -> "Suburban — Milky Way faint or lost overhead."
    6 -> "Bright suburban — only the brightest constellations."
    7 -> "Suburban/urban — the sky glows grey."
    else -> "Inner-city — only the Moon, planets and a few stars."
}
