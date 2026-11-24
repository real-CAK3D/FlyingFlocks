package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.AirQuality
import com.cak3d.biscuitmesh.geo.AirQualityClient
import kotlinx.coroutines.flow.first

private fun aqiColor(aqi: Int): Color = when {
    aqi <= 50 -> Color(0xFF5FD36A)
    aqi <= 100 -> Color(0xFFFFD166)
    aqi <= 150 -> Color(0xFFFF9F45)
    aqi <= 200 -> Color(0xFFFF5470)
    aqi <= 300 -> Color(0xFFA78BFA)
    else -> Color(0xFF9B2242)
}

private fun aqiLabel(aqi: Int): String = when {
    aqi <= 50 -> "Good"
    aqi <= 100 -> "Moderate"
    aqi <= 150 -> "Unhealthy for sensitive groups"
    aqi <= 200 -> "Unhealthy"
    aqi <= 300 -> "Very unhealthy"
    else -> "Hazardous"
}

private fun aqiAdvice(aqi: Int): String = when {
    aqi <= 50 -> "Air is clean — a great time to be outside."
    aqi <= 100 -> "Fine for most; unusually sensitive people may notice it."
    aqi <= 150 -> "Sensitive groups (asthma, heart/lung) should ease up outdoors."
    aqi <= 200 -> "Everyone may feel effects — limit prolonged exertion outside."
    aqi <= 300 -> "Health alert — avoid outdoor exertion; mask up if you must go out."
    else -> "Emergency conditions — stay indoors, filter the air."
}

private fun uvColor(uv: Double): Color = when {
    uv < 3 -> Color(0xFF5FD36A)
    uv < 6 -> Color(0xFFFFD166)
    uv < 8 -> Color(0xFFFF9F45)
    uv < 11 -> Color(0xFFFF5470)
    else -> Color(0xFFA78BFA)
}

private fun uvLabel(uv: Double): String = when {
    uv < 3 -> "Low"
    uv < 6 -> "Moderate"
    uv < 8 -> "High"
    uv < 11 -> "Very high"
    else -> "Extreme"
}

/**
 * Air Quality — the environmental companion to Weather and Sky Conditions. Pulls the current US AQI,
 * the main pollutants (PM2.5/PM10, ozone, NO₂, SO₂, CO) and the UV index from the keyless Open-Meteo
 * air-quality API, colour-coded by severity with a plain-language health note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirQualityScreen(vm: MainViewModel, onBack: () -> Unit) {
    val client = remember { AirQualityClient() }
    var data by remember { mutableStateOf<AirQuality?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val fix = runCatching { vm.locationUpdates().first() }.getOrNull()
        if (fix == null) { loading = false; failed = true; return@LaunchedEffect }
        data = client.air(fix.latitude, fix.longitude)
        loading = false
        failed = data == null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Air Quality") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Air Quality",
                        "The current US Air Quality Index, the main pollutants near you (PM2.5, PM10, " +
                            "ozone, NO₂, SO₂, CO) and the UV index — from the keyless Open-Meteo air-quality " +
                            "service, at your current location.",
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
            if (loading) {
                item {
                    Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            } else if (failed || data == null) {
                item {
                    Text(
                        "Couldn't read air quality right now — need a location fix and a connection. Try again shortly.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 13.sp, modifier = Modifier.padding(8.dp),
                    )
                }
            } else {
                val a = data!!
                // AQI hero.
                item {
                    val aqi = a.usAqi
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("US AIR QUALITY INDEX", fontSize = 11.sp, letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(aqi?.toString() ?: "—", fontSize = 48.sp, fontWeight = FontWeight.Bold,
                                    color = aqi?.let(::aqiColor) ?: MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace)
                                Text(aqi?.let(::aqiLabel) ?: "no reading", fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = aqi?.let(::aqiColor) ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            if (aqi != null) {
                                Text(aqiAdvice(aqi), fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                            }
                        }
                    }
                }
                // UV index.
                a.uvIndex?.let { uv ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("%.0f".format(uv), fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                    color = uvColor(uv), fontFamily = FontFamily.Monospace)
                                Column {
                                    Text("UV INDEX", fontSize = 11.sp, letterSpacing = 2.sp,
                                        color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                                    Text(uvLabel(uv), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = uvColor(uv))
                                }
                            }
                        }
                    }
                }
                // Pollutant breakdown.
                item {
                    Text("POLLUTANTS", fontSize = 12.sp, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pollutant("PM2.5", a.pm25, "µg/m³", "Fine particles — the big one for lungs")
                            Pollutant("PM10", a.pm10, "µg/m³", "Coarse dust & pollen")
                            Pollutant("Ozone", a.ozone, "µg/m³", "Smog, worse on hot sunny days")
                            Pollutant("NO₂", a.no2, "µg/m³", "Traffic & combustion")
                            Pollutant("SO₂", a.so2, "µg/m³", "Industrial / fuel burning")
                            Pollutant("CO", a.co, "µg/m³", "Carbon monoxide")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pollutant(name: String, value: Double?, unit: String, note: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(note, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        }
        Text(
            value?.let { "%.1f %s".format(it, unit) } ?: "—",
            fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
    }
}
