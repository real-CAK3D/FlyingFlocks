package com.cak3d.biscuitmesh.ui

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
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon

/**
 * NASA GIBS "Black Marble" night-lights (VIIRS). A worldwide composite of Earth at night: bright
 * where cities glow, black where the sky is dark — i.e. a light-pollution map. Keyless, served as
 * EPSG:3857 tiles (z/y/x order, max native zoom 8). .png, 256 px.
 */
private class BlackMarbleTileSource : OnlineTileSourceBase(
    "VIIRS_Black_Marble", 1, 8, 256, ".png", arrayOf(""),
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/VIIRS_Black_Marble/default/" +
            "2016-01-01/GoogleMapsCompatible_Level8/$z/$y/$x.png"
    }
}

/**
 * Light Pollution map — your sky's darkness as a picture. Centres NASA's Earth-at-Night composite on
 * your location: the brighter the glow around you, the worse the light pollution; black means dark
 * skies. Pairs with the numeric read in Sky Conditions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightMapScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val fix by vm.currentFix.collectAsStateWithLifecycle()
    var loc by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    LaunchedEffect(fix) { fix?.let { loc = it } }
    LaunchedEffect(Unit) { if (loc == null) vm.oneShotLocation()?.let { loc = it.lat to it.lon } }

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(BlackMarbleTileSource())
            setMultiTouchControls(true)
            minZoomLevel = 2.0
            maxZoomLevel = 8.0
            controller.setZoom(7.0)
            setBackgroundColor(android.graphics.Color.BLACK)
        }
    }
    DisposableEffect(Unit) { mapView.onResume(); onDispose { mapView.onPause() } }

    // "You are here" ring over the glow.
    LaunchedEffect(loc) {
        val ll = loc ?: return@LaunchedEffect
        val here = GeoPoint(ll.first, ll.second)
        mapView.controller.setCenter(here)
        mapView.overlays.removeAll { it is Polygon }
        val ring = Polygon().apply {
            points = Polygon.pointsAsCircle(here, 1200.0)
            fillPaint.color = 0x33FFD166
            outlinePaint.color = 0xFFFFD166.toInt()
            outlinePaint.strokeWidth = 4f
        }
        mapView.overlays.add(ring)
        mapView.invalidate()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Light Pollution") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Light Pollution map",
                        "Earth at night, centred on you (NASA VIIRS Black Marble). Bright glow = heavy light " +
                            "pollution; black = dark skies. Pan and pinch to explore, tap the crosshair to " +
                            "recentre. For the live numeric read — how dark it is right now with twilight and " +
                            "the Moon folded in — see Sky Conditions.",
                    )
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            // Recentre button.
            FilledIconButton(
                onClick = { loc?.let { mapView.controller.animateTo(GeoPoint(it.first, it.second)) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(48.dp)
                    .help("Recentre", "Snaps the night-lights map back to your location after you've panned away."),
            ) { Icon(Icons.Default.MyLocation, contentDescription = "Recentre on me") }

            // Legend.
            Surface(
                color = Color(0xCC05080A),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("EARTH AT NIGHT · VIIRS", fontSize = 10.sp, letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFFFFD166))
                    LegendRow(Color(0xFFFFF2C7), "bright — city glow, washed-out sky")
                    LegendRow(Color(0xFFBE7A24), "suburban haze")
                    LegendRow(Color(0xFF2A2340), "dark — good observing skies")
                    Text("NASA GIBS · Black Marble 2016", fontSize = 9.sp,
                        color = Color(0xFF8A9896), fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun LegendRow(swatch: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(color = swatch, shape = RoundedCornerShape(2.dp), modifier = Modifier.size(11.dp)) {}
        Text(label, fontSize = 10.sp, color = Color(0xFFE9F0EE))
    }
}
