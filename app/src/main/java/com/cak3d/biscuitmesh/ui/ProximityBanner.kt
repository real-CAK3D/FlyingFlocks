package com.cak3d.biscuitmesh.ui

import android.content.Context
import android.location.Location
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.HazardKind
import com.cak3d.biscuitmesh.wardrive.WardriveState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A global "you're near something" banner — like the mesh/camera proximity alert the
 * portal and wardrive apps pop at the top. It slides in whenever the vehicle comes
 * within range of a mapped Meshtastic node or camera, shows the name, distance and a
 * compass bearing (so it doubles as a locator), buzzes once on entry, and taps through
 * to the map. It clears itself when you move out of range.
 */
private data class Nearby(
    val key: String,
    val name: String,
    val distM: Int,
    val bearing: String,
    val icon: ImageVector,
    val accent: Color,
    val kind: String,
)

private const val MESH_RANGE_M = 600.0
private const val CAM_RANGE_M = 250.0
private val MESH_GREEN = Color(0xFF00E5A0)
private val CAM_RED = Color(0xFFFF5470)

@Composable
fun ProximityBanner(vm: MainViewModel, onOpenMap: () -> Unit) {
    val wsFlow = remember { vm.wardriveState ?: MutableStateFlow(WardriveState()) }
    val ws by wsFlow.collectAsStateWithLifecycle()
    val meshNodes by vm.meshNodes.collectAsStateWithLifecycle()
    val placements by vm.meshPlacements.collectAsStateWithLifecycle()
    val cameras by vm.hazardsState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val lat = ws.lastLat
    val lon = ws.lastLon
    val nearby = remember(lat, lon, meshNodes, placements, cameras) {
        if (lat == null || lon == null) return@remember null
        val mesh = meshNodes.mapNotNull { n ->
            val nlat = n.lat ?: placements[n.num]?.first
            val nlon = n.lon ?: placements[n.num]?.second
            if (nlat == null || nlon == null) null
            else {
                val (d, b) = distBearing(lat, lon, nlat, nlon)
                if (d <= MESH_RANGE_M) Nearby("mesh:${n.num}", n.name.ifBlank { "Mesh node" }, d.toInt(),
                    compass(b), Icons.Default.Hub, MESH_GREEN, "Meshtastic") else null
            }
        }.minByOrNull { it.distM }
        val cam = cameras.mapNotNull { h ->
            val (d, b) = distBearing(lat, lon, h.lat, h.lon)
            if (d <= CAM_RANGE_M) Nearby("cam:${h.id}", h.name.ifBlank { camLabel(h.kind) }, d.toInt(),
                compass(b), Icons.Default.Videocam, CAM_RED, camLabel(h.kind)) else null
        }.minByOrNull { it.distM }
        mesh ?: cam    // mesh nodes are rarer, so they win when both are in range
    }

    // Buzz once each time a new device enters range.
    LaunchedEffect(nearby?.key) { if (nearby != null) buzz(context) }

    AnimatedVisibility(
        visible = nearby != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        nearby?.let { n ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clickable { onOpenMap() },
                shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(n.icon, contentDescription = null, tint = n.accent)
                    Column(Modifier.weight(1f)) {
                        Text("NEARBY · ${n.kind}", style = MaterialTheme.typography.labelSmall,
                            color = n.accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(n.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    Text("${n.distM} m  ${n.bearing}", style = MaterialTheme.typography.titleMedium,
                        color = n.accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun distBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Pair<Double, Float> {
    val r = FloatArray(2)
    Location.distanceBetween(lat1, lon1, lat2, lon2, r)
    return r[0].toDouble() to r[1]
}

private fun compass(bearingDeg: Float): String {
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val idx = (((bearingDeg + 22.5f) % 360f + 360f) % 360f / 45f).toInt() % 8
    return dirs[idx]
}

private fun camLabel(kind: HazardKind): String = when (kind) {
    HazardKind.FLOCK -> "Flock camera"
    HazardKind.SPEED_CAMERA -> "Speed camera"
    HazardKind.RED_LIGHT -> "Red-light camera"
    HazardKind.CAMERA -> "Camera"
}

private fun buzz(context: Context) {
    runCatching {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vib.vibrate(120)
        }
    }
}
