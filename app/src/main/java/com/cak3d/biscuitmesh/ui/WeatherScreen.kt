package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.RadarFrame
import com.cak3d.biscuitmesh.geo.WeatherClient
import com.cak3d.biscuitmesh.geo.WeatherDay
import com.cak3d.biscuitmesh.geo.WeatherForecast
import com.cak3d.biscuitmesh.geo.WeatherHour
import com.cak3d.biscuitmesh.geo.WeatherNow
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A RainViewer radar frame as an osmdroid online tile source ({z}/{x}/{y} templated). */
private class RadarTileSource(name: String, private val tpl: String) :
    OnlineTileSourceBase(name, 0, 10, 256, ".png", arrayOf("")) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        tpl.replace("{z}", MapTileIndex.getZoom(pMapTileIndex).toString())
            .replace("{x}", MapTileIndex.getX(pMapTileIndex).toString())
            .replace("{y}", MapTileIndex.getY(pMapTileIndex).toString())
}

/**
 * Weather: current conditions, an animated precipitation radar (RainViewer — past + nowcast),
 * an hourly strip and a multi-day forecast (Open-Meteo, into tomorrow and beyond). All free,
 * no API key.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val fix by vm.currentFix.collectAsStateWithLifecycle()
    val client = remember { WeatherClient() }
    val apiKeys by vm.apiKeys.collectAsStateWithLifecycle()

    var loc by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var forecast by remember { mutableStateOf<WeatherForecast?>(null) }
    var frames by remember { mutableStateOf<List<RadarFrame>>(emptyList()) }
    var frameIdx by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(fix) { fix?.let { loc = it } }
    LaunchedEffect(Unit) { if (loc == null) vm.oneShotLocation()?.let { loc = it.lat to it.lon } }
    LaunchedEffect(loc) {
        val ll = loc ?: return@LaunchedEffect
        loading = true
        val wx = com.cak3d.biscuitmesh.data.ApiKeys.resolve("weather", apiKeys)
        forecast = client.forecast(ll.first, ll.second, wx?.first, wx?.second)
        frames = client.radarFrames()
        frameIdx = frames.indexOfLast { !it.nowcast }.coerceAtLeast(0)
        loading = false
    }
    LaunchedEffect(frames, playing) {
        if (frames.isEmpty() || !playing) return@LaunchedEffect
        while (true) { delay(650); frameIdx = (frameIdx + 1) % frames.size }
    }

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
            setMultiTouchControls(true)
            controller.setZoom(7.5)
        }
    }
    DisposableEffect(Unit) { mapView.onResume(); onDispose { mapView.onPause() } }
    LaunchedEffect(loc) { loc?.let { mapView.controller.setCenter(GeoPoint(it.first, it.second)) } }

    // Swap the radar tile overlay whenever the frame advances.
    val radarHolder = remember { arrayOfNulls<TilesOverlay>(1) }
    LaunchedEffect(frames, frameIdx) {
        val f = frames.getOrNull(frameIdx) ?: return@LaunchedEffect
        radarHolder[0]?.let { mapView.overlays.remove(it) }
        val provider = MapTileProviderBasic(context, RadarTileSource("rv${f.timeSec}", f.urlTemplate))
        val ov = TilesOverlay(provider, context).apply {
            loadingBackgroundColor = android.graphics.Color.TRANSPARENT
            loadingLineColor = android.graphics.Color.TRANSPARENT
        }
        mapView.overlays.add(ov)
        radarHolder[0] = ov
        mapView.invalidate()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weather") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    HelpButton(
                        "Weather",
                        "Current conditions, an animated precipitation radar (past frames plus a short " +
                            "nowcast), an hourly strip and a multi-day forecast — all for your location, " +
                            "keyless from Open-Meteo and RainViewer. Add a weather API key in Settings → Keys " +
                            "for an alternate provider.",
                    )
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading && forecast == null) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            forecast?.now?.let { CurrentCard(it) }

            // Radar map with frame controls.
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column {
                    Text("  PRECIPITATION RADAR", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 10.dp).help("Precip radar", "An animated rain/snow radar centred on you. Play/pause loops it; the slider scrubs through past frames and the short forecast (nowcast) frames. Pinch the map to zoom."))
                    Box(Modifier.fillMaxWidth().height(300.dp)) {
                        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                    }
                    val f = frames.getOrNull(frameIdx)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledIconButton(onClick = { playing = !playing }, modifier = Modifier.size(40.dp)) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play")
                        }
                        if (frames.isNotEmpty()) {
                            Slider(
                                value = frameIdx.toFloat(),
                                onValueChange = { playing = false; frameIdx = it.toInt().coerceIn(0, frames.size - 1) },
                                valueRange = 0f..(frames.size - 1).coerceAtLeast(1).toFloat(),
                                modifier = Modifier.weight(1f),
                            )
                        } else Spacer(Modifier.weight(1f))
                        Text(
                            f?.let { radarLabel(it) } ?: "—",
                            style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace,
                            color = if (f?.nowcast == true) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Hourly strip.
            forecast?.hours?.takeIf { it.isNotEmpty() }?.let { HourlyStrip(it) }

            // Daily forecast.
            forecast?.days?.takeIf { it.isNotEmpty() }?.let { days ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) { days.forEach { DayRow(it) } }
                }
            }

            if (!loading && forecast == null) {
                Text("Couldn't load weather — check the connection.",
                    Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CurrentCard(now: WeatherNow) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(now.emoji, fontSize = 52.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("${now.tempF}°", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(now.desc, style = MaterialTheme.typography.titleMedium)
                Text("Feels ${now.feelsF}°  ·  ${now.humidity}% hum  ·  ${now.windMph} mph ${now.windCardinal}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun HourlyStrip(hours: List<WeatherHour>) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(vertical = 10.dp)) {
            Text("  NEXT 24 HOURS", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
                hours.forEach { h ->
                    Column(
                        Modifier.width(60.dp).padding(horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(h.hourLabel, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Text(h.emoji, fontSize = 20.sp)
                        Text("${h.tempF}°", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (h.precipPct > 0) "${h.precipPct}%" else " ",
                            style = MaterialTheme.typography.labelSmall, color = Color(0xFF5CB4E8))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayRow(d: WeatherDay) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(d.dayLabel, Modifier.width(96.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(d.emoji, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Text(if (d.precipPct > 0) "${d.precipPct}%" else "",
            Modifier.width(46.dp), style = MaterialTheme.typography.labelMedium, color = Color(0xFF5CB4E8))
        Text(d.desc, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 1)
        Text("${d.hiF}°", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text(" / ${d.loF}°", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

private fun radarLabel(f: RadarFrame): String {
    val t = SimpleDateFormat("h:mm a", Locale.US).format(Date(f.timeSec * 1000))
    return if (f.nowcast) "$t (forecast)" else t
}
