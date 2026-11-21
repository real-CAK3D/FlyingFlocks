package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Streetview
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.json.JSONArray
import org.json.JSONObject

/**
 * The "Eyesight" middle stage: a tilted 3D terrain view (MapLibre GL Native) with the
 * drive's sightings dropped on the real topography, free DEM + satellite imagery, no key.
 * Two-finger drag tilts/orbits; the Street-level button drops into the camera AR.
 *
 * Base imagery: Esri World Imagery. Elevation: AWS Terrarium DEM (Mapzen tiles).
 */

// Full MapLibre style: satellite raster + terrarium DEM, with 3D terrain + hillshade.
internal const val TERRAIN_STYLE = """
{
  "version": 8,
  "sources": {
    "sat": {
      "type": "raster",
      "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
      "tileSize": 256, "maxzoom": 19,
      "attribution": "Imagery © Esri"
    },
    "dem": {
      "type": "raster-dem",
      "tiles": ["https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"],
      "encoding": "terrarium", "tileSize": 256, "maxzoom": 15,
      "attribution": "Elevation © Mapzen / AWS"
    }
  },
  "layers": [
    { "id": "sat", "type": "raster", "source": "sat" },
    { "id": "hills", "type": "hillshade", "source": "dem",
      "paint": { "hillshade-exaggeration": 0.45, "hillshade-shadow-color": "#000000" } }
  ],
  "terrain": { "source": "dem", "exaggeration": 1.35 }
}
"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Terrain3DScreen(vm: MainViewModel, onBack: () -> Unit, onOpenEyesight: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val fix by vm.currentFix.collectAsStateWithLifecycle()
    val samplesFlow = vm.wardriveSamples
    val samples by (samplesFlow?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(emptyMap()) })

    DisposableEffect(Unit) { vm.startGnss(); onDispose { vm.stopGnss() } }

    // One MapLibre instance for the process; safe to call repeatedly.
    remember { MapLibre.getInstance(context) }

    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }

    // Forward the Compose lifecycle to the native MapView.
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        mapView.onStart(); mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    // (Re)load the style + camera + points when the fix or samples change.
    val fixKey = fix?.let { Math.round(it.first * 1000) to Math.round(it.second * 1000) }
    // Reload in batches (every ~40 new sightings) rather than on every single point, so the
    // heavy style/DEM rebuild doesn't thrash as the drive fills in thousands of blips.
    DisposableEffect(fixKey, samples.size / 40) {
        val f = fix
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromJson(TERRAIN_STYLE)) { style ->
                // Drop the sightings as a circle layer on the terrain.
                val fc = featureCollection(samples.values.toList(), f)
                val existing = style.getSource("pts") as? GeoJsonSource
                if (existing != null) {
                    existing.setGeoJson(fc)
                } else {
                    style.addSource(GeoJsonSource("pts", fc))
                    style.addLayer(
                        CircleLayer("ptsLayer", "pts").withProperties(
                            PropertyFactory.circleRadius(4.5f),
                            PropertyFactory.circleColor(Expression.get("c")),
                            PropertyFactory.circleOpacity(0.85f),
                            PropertyFactory.circleStrokeColor("#06202A"),
                            PropertyFactory.circleStrokeWidth(1f),
                        ),
                    )
                }
            }
            if (f != null) {
                val pos = CameraPosition.Builder()
                    .target(LatLng(f.first, f.second))
                    .zoom(15.0).tilt(62.0).bearing(0.0).build()
                // Google-Earth-style fly-in from the wide view instead of a hard snap.
                map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 1600)
            }
        }
        onDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("3D Terrain") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton("3D Terrain",
                        "Your drive on the real landscape, tilted into 3D over free elevation data. " +
                            "Two fingers tilt and orbit; pinch to zoom. Tap Street level to point your " +
                            "phone's camera at the plotted points around you.")
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenEyesight,
                icon = { Icon(Icons.Default.Streetview, contentDescription = null) },
                text = { Text("Street level") },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            if (fix == null) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Terrain, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Waiting for a GPS fix to place the terrain…",
                        style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text("${samples.size} sightings on the land · two fingers to tilt & orbit",
                    style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.padding(10.dp))
            }
        }
    }
}

/** Build a GeoJSON FeatureCollection string from the nearest ~1500 sightings. */
private fun featureCollection(
    samples: List<com.cak3d.biscuitmesh.wardrive.WardriveSample>,
    fix: Pair<Double, Double>?,
): String {
    val ordered = if (fix != null) {
        samples.sortedBy { s ->
            val dLat = s.lat - fix.first; val dLon = s.lon - fix.second
            dLat * dLat + dLon * dLon
        }
    } else samples
    val feats = JSONArray()
    ordered.take(12000).forEach { s ->
        if (s.lat == 0.0 && s.lon == 0.0) return@forEach
        val color = when (s.kind) {
            com.cak3d.biscuitmesh.wardrive.SampleKind.RF -> "#F59E0B"
            com.cak3d.biscuitmesh.wardrive.SampleKind.BT -> "#A855F7"
            com.cak3d.biscuitmesh.wardrive.SampleKind.STATION -> "#FF5470"
            com.cak3d.biscuitmesh.wardrive.SampleKind.AP -> if (s.is5GHz) "#60A5FA" else "#22D3EE"
        }
        feats.put(
            JSONObject().apply {
                put("type", "Feature")
                put("geometry", JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", JSONArray().apply { put(s.lon); put(s.lat) })
                })
                put("properties", JSONObject().apply { put("c", color) })
            },
        )
    }
    return JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", feats)
    }.toString()
}
