package com.cak3d.biscuitmesh.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.hunts.BirdKind
import com.cak3d.biscuitmesh.hunts.RareBird
import com.cak3d.biscuitmesh.hunts.RareBirdManager.Companion.bearingDeg
import com.cak3d.biscuitmesh.hunts.RareBirdManager.Companion.distanceM
import com.cak3d.biscuitmesh.wardrive.LatLon
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RareBirdsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val birds by vm.rareBirds.collectAsStateWithLifecycle(initialValue = emptyList())
    val here by vm.rareBirdHere.collectAsStateWithLifecycle()

    // Keep a live fix while the hunt is open so seeding, distances and captures update.
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

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("RARE BIRDS") },
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
            item("stats") {
                Text(
                    "> ${active.size} IN FLIGHT · $caught CAUGHT",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
            CompassArrow(here, lead, size = 56)
            Column(Modifier.weight(1f)) {
                Text("FOLLOW THE STRAY", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
                Text(lead.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(distanceLabel(here, lead), style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = { navigateTo(context, lead) }) { Text("Navigate") }
        }
    }
}

@Composable
private fun BirdRow(bird: RareBird, here: LatLon?) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(14.dp),
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
                CompassArrow(here, bird, size = 36)
            }
            Column(Modifier.weight(1f)) {
                Text(bird.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${kindLabel(bird.kind)} · ${distanceLabel(here, bird)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
            if (!bird.captured) {
                OutlinedButton(onClick = { navigateTo(context, bird) }) { Text("Go") }
            }
        }
    }
}

@Composable
private fun CompassArrow(here: LatLon?, target: RareBird, size: Int) {
    val bearing = if (here != null) bearingDeg(here.lat, here.lon, target.lat, target.lon) else 0f
    Icon(
        Icons.Default.Navigation,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(size.dp).graphicsLayer { rotationZ = bearing },
    )
}

private fun kindLabel(kind: BirdKind): String = when (kind) {
    BirdKind.RARE_FIND -> "Rare find"
    BirdKind.COLLECTIBLE -> "Collectible"
    BirdKind.STRAY -> "Stray"
}

private fun distanceLabel(here: LatLon?, bird: RareBird): String {
    if (here == null) return "locating…"
    val m = distanceM(here.lat, here.lon, bird.lat, bird.lon)
    val dir = compass(bearingDeg(here.lat, here.lon, bird.lat, bird.lon))
    return if (m >= 1000) "%.1f km %s".format(m / 1000, dir) else "${m.toInt()} m $dir"
}

private fun compass(deg: Float): String {
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[(((deg + 22.5f) / 45f).toInt()) % 8]
}

private fun navigateTo(context: Context, bird: RareBird) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${bird.lat},${bird.lon}"))
        .apply { setPackage("com.google.android.apps.maps") }
    val geo = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("geo:${bird.lat},${bird.lon}?q=${bird.lat},${bird.lon}(${Uri.encode(bird.name)})"),
    )
    runCatching { context.startActivity(nav) }
        .recoverCatching { context.startActivity(Intent.createChooser(geo, "Navigate")) }
}
