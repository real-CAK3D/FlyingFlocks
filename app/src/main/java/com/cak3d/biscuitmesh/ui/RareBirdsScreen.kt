package com.cak3d.biscuitmesh.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.Hazard
import com.cak3d.biscuitmesh.geo.HazardKind
import com.cak3d.biscuitmesh.hunts.BirdKind
import com.cak3d.biscuitmesh.hunts.RareBird
import com.cak3d.biscuitmesh.hunts.RareBirdManager.Companion.bearingDeg
import com.cak3d.biscuitmesh.hunts.RareBirdManager.Companion.distanceM
import com.cak3d.biscuitmesh.progress.Progress
import com.cak3d.biscuitmesh.progress.ProgressTrack
import com.cak3d.biscuitmesh.progress.TrackState
import com.cak3d.biscuitmesh.wardrive.LatLon
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RareBirdsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenFinds: (String) -> Unit = {},
    onOpenStats: () -> Unit = {},
) {
    val birds by vm.rareBirds.collectAsStateWithLifecycle(initialValue = emptyList())
    val here by vm.rareBirdHere.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val hazards by vm.hazards.collectAsStateWithLifecycle(initialValue = emptyList())
    val sweep by vm.cameraSweep.collectAsStateWithLifecycle()

    // Keep a live fix while the guide is open so distances, captures and sweeps update.
    LaunchedEffect(Unit) {
        while (true) {
            val fix = vm.oneShotLocation()
            if (fix != null) vm.rareBirdOnLocation(fix.lat, fix.lon)
            delay(4000)
        }
    }

    val active = birds.filter { !it.captured }
    val sorted = here?.let { h -> active.sortedBy { distanceM(h.lat, h.lon, it.lat, it.lon) } } ?: active
    val lead = sorted.firstOrNull()
    val caught = birds.count { it.captured }

    val cameraTargets = here?.let { h ->
        hazards.sortedBy { distanceM(h.lat, h.lon, it.lat, it.lon) }.take(8)
    } ?: hazards.take(8)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("FIELD GUIDE") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("rank") { RankCard(progress, onClick = onOpenStats) }
            items(progress.radios, key = { it.track.name }) { t -> TrackRow(t, onClick = { onOpenFinds(t.track.name) }) }
            item("boosts") { CameraBoostCard(progress) }

            item("camhead") { SectionLabel("> CAMERA HUNT") }
            item("camsweep") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { vm.sweepCamerasHere() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Find cameras near me")
                    }
                    sweep?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary)
                    }
                    if (cameraTargets.isEmpty()) {
                        Text(
                            "No cameras logged yet. Sweep here, or start a wardrive — every Flock, " +
                                "speed and red-light camera you pass is logged and boosts your rank.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            items(cameraTargets, key = { it.id }) { cam -> CameraRow(cam, here) }

            item("birdhead") { SectionLabel("> RARE BIRDS · ${active.size} IN FLIGHT · $caught CAUGHT") }
            if (lead != null) item("lead") { LeadCard(lead, here) }
            items(sorted, key = { it.id }) { bird -> BirdRow(bird, here) }
            if (birds.isEmpty()) {
                item("empty") {
                    Text(
                        "No birds yet. Start a wardrive — rare finds take wing here, and a few " +
                            "collectibles are seeded nearby to chase down.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RankCard(progress: Progress, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("FLOCK RANK", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
                Text("Stats ›", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${progress.rank}", style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(progress.rankName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { progress.rankProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
            )
            Text(
                "${progress.xpToNext} XP to rank ${progress.rank + 1}. Wi-Fi and Bluetooth level you up; " +
                    "each camera is a one-time boost, and every rare bird is a full level.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
            val birdLine = if (progress.bonusLevels > 0) " · ${progress.bonusLevels} rare-bird levels" else ""
            Text("${progress.totalFinds} total finds · ${progress.cameraBoostXp} camera boost XP$birdLine",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun CameraBoostCard(progress: Progress) {
    Card(
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CAMERA BOOSTS", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
            if (progress.cameraFinds == 0) {
                Text("No cameras yet. Each Flock, speed or red-light camera you find gives a big " +
                    "one-time boost toward your next rank.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            } else {
                progress.cameras.filter { it.count > 0 }.forEach { t ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(trackIcon(t.track), contentDescription = null, tint = trackColor(t.track),
                                modifier = Modifier.size(22.dp))
                            Text("${t.track.displayName} · ${t.count}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("+${t.xp} XP", style = MaterialTheme.typography.titleSmall,
                            color = trackColor(t.track), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TrackRow(t: TrackState, onClick: () -> Unit) {
    val accent = trackColor(t.track)
    Card(
        onClick = onClick,
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(trackIcon(t.track), contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t.track.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Lv ${t.level}", style = MaterialTheme.typography.titleSmall,
                        color = accent, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { t.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                )
                Text("${t.count} found · ${t.xp - t.levelStartXp}/${t.nextLevelXp - t.levelStartXp} XP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun CameraRow(cam: Hazard, here: LatLon?) {
    val context = LocalContext.current
    Card(
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(hazardIcon(cam.kind), contentDescription = null,
                tint = hazardColor(cam.kind), modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text(hazardLabel(cam.kind), style = MaterialTheme.typography.titleMedium)
                Text(distanceLabelLL(here, cam.lat, cam.lon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
            }
            OutlinedButton(onClick = { navigateTo(context, cam.lat, cam.lon, hazardLabel(cam.kind)) }) { Text("Go") }
        }
    }
}

@Composable
private fun LeadCard(lead: RareBird, here: LatLon?) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CompassArrow(here, lead.lat, lead.lon, size = 56)
            Column(Modifier.weight(1f)) {
                Text("FOLLOW THE STRAY", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
                Text(lead.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(distanceLabelLL(here, lead.lat, lead.lon), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = { navigateTo(context, lead.lat, lead.lon, lead.name) }) { Text("Navigate") }
        }
    }
}

@Composable
private fun BirdRow(bird: RareBird, here: LatLon?) {
    val context = LocalContext.current
    Card(
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (bird.captured) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Caught",
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            } else {
                CompassArrow(here, bird.lat, bird.lon, size = 36)
            }
            Column(Modifier.weight(1f)) {
                Text(bird.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${kindLabel(bird.kind)} · ${distanceLabelLL(here, bird.lat, bird.lon)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
            if (!bird.captured) {
                OutlinedButton(onClick = { navigateTo(context, bird.lat, bird.lon, bird.name) }) { Text("Go") }
            }
        }
    }
}

@Composable
private fun CompassArrow(here: LatLon?, lat: Double, lon: Double, size: Int) {
    val bearing = if (here != null) bearingDeg(here.lat, here.lon, lat, lon) else 0f
    Icon(
        Icons.Default.Navigation,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(size.dp).graphicsLayer { rotationZ = bearing },
    )
}

private fun trackIcon(track: ProgressTrack): ImageVector = when (track) {
    ProgressTrack.WIFI_24, ProgressTrack.WIFI_5 -> Icons.Default.Wifi
    ProgressTrack.BLUETOOTH -> Icons.Default.Bluetooth
    ProgressTrack.FLOCK -> Icons.Default.PhotoCamera
    ProgressTrack.SPEED_CAM -> Icons.Default.Speed
    ProgressTrack.REDLIGHT -> Icons.Default.Traffic
}

private fun trackColor(track: ProgressTrack): Color = when (track) {
    ProgressTrack.WIFI_24 -> Color(0xFF34D399)   // emerald — the common 2.4 GHz grind
    ProgressTrack.WIFI_5 -> Color(0xFF7DF9FF)    // electric teal — matches the wtBLEep map view
    ProgressTrack.BLUETOOTH -> Color(0xFF22D3EE)
    ProgressTrack.FLOCK -> Color(0xFFF87171)
    ProgressTrack.SPEED_CAM -> Color(0xFFFBBF24)
    ProgressTrack.REDLIGHT -> Color(0xFFA855F7)
}

private fun hazardIcon(kind: HazardKind): ImageVector = when (kind) {
    HazardKind.FLOCK -> Icons.Default.PhotoCamera
    HazardKind.SPEED_CAMERA -> Icons.Default.Speed
    HazardKind.RED_LIGHT -> Icons.Default.Traffic
    HazardKind.CAMERA -> Icons.Default.Videocam
}

private fun hazardColor(kind: HazardKind): Color = when (kind) {
    HazardKind.FLOCK -> Color(0xFFF87171)
    HazardKind.SPEED_CAMERA -> Color(0xFFFBBF24)
    HazardKind.RED_LIGHT -> Color(0xFFA855F7)
    HazardKind.CAMERA -> Color(0xFF93C5FD)
}

private fun hazardLabel(kind: HazardKind): String = when (kind) {
    HazardKind.FLOCK -> "Flock / ALPR camera"
    HazardKind.SPEED_CAMERA -> "Speed camera"
    HazardKind.RED_LIGHT -> "Red-light camera"
    HazardKind.CAMERA -> "Surveillance camera"
}

private fun kindLabel(kind: BirdKind): String = when (kind) {
    BirdKind.RARE_FIND -> "Rare find"
    BirdKind.COLLECTIBLE -> "Collectible"
    BirdKind.STRAY -> "Stray"
}

private fun distanceLabelLL(here: LatLon?, lat: Double, lon: Double): String {
    if (here == null) return "locating…"
    val m = distanceM(here.lat, here.lon, lat, lon)
    val dir = compass(bearingDeg(here.lat, here.lon, lat, lon))
    return if (m >= 1000) "%.1f km %s".format(m / 1000, dir) else "${m.toInt()} m $dir"
}

private fun compass(deg: Float): String {
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[(((deg + 22.5f) / 45f).toInt()) % 8]
}

private fun navigateTo(context: Context, lat: Double, lon: Double, name: String) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lon"))
        .apply { setPackage("com.google.android.apps.maps") }
    val geo = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(name)})"),
    )
    runCatching { context.startActivity(nav) }
        .recoverCatching { context.startActivity(Intent.createChooser(geo, "Navigate")) }
}
