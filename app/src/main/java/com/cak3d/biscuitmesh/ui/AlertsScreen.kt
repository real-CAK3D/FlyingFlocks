package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.geo.AlertSeverity
import com.cak3d.biscuitmesh.geo.AlertsBundle
import com.cak3d.biscuitmesh.geo.AlertsClient
import com.cak3d.biscuitmesh.geo.ThreatAlert
import kotlinx.coroutines.delay

private fun sevColor(s: AlertSeverity): Color = when (s) {
    AlertSeverity.EXTREME -> Color(0xFFFF3B30)
    AlertSeverity.SEVERE -> Color(0xFFFF9500)
    AlertSeverity.MODERATE -> Color(0xFFFFCC00)
    AlertSeverity.MINOR -> Color(0xFF5AC8FA)
    AlertSeverity.INFO -> Color(0xFF8E8E93)
}

/**
 * Alerts & Threats — a live security feed of what's affecting your area and the wider world:
 * NWS warnings for your exact point, USGS earthquakes, and GDACS global disasters. Free, no key.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val fix by vm.currentFix.collectAsStateWithLifecycle()
    val client = remember { AlertsClient() }

    var loc by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var bundle by remember { mutableStateOf<AlertsBundle?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<ThreatAlert?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(fix) { fix?.let { loc = it } }
    LaunchedEffect(Unit) { if (loc == null) vm.oneShotLocation()?.let { loc = it.lat to it.lon } }
    LaunchedEffect(loc, reload) {
        loading = true
        bundle = client.all(loc?.first, loc?.second)
        loading = false
    }
    // Refresh in the background every 3 minutes.
    LaunchedEffect(Unit) {
        while (true) { delay(180_000); reload++ }
    }

    val worst = bundle?.local?.firstOrNull()?.severity ?: bundle?.global?.firstOrNull()?.severity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alerts & Threats") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, "Refresh") }
                    HelpButton(
                        "Alerts & Threats",
                        "A live security feed of what's happening around you and worldwide. 'Affecting your area' " +
                            "pulls official National Weather Service warnings for your exact location plus any quake or " +
                            "disaster within ~400 km. 'Global events' lists significant earthquakes (USGS) and major " +
                            "disasters — cyclones, floods, volcanoes, droughts, wildfires (GDACS). Tap any card for the " +
                            "full detail. All sources are free and public; nothing about you leaves the phone.",
                    )
                },
            )
        },
    ) { pad ->
        val b = bundle
        if (loading && b == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { AreaBanner(b?.local.orEmpty().size, worst, loc != null) }
                item { SectionHeader("AFFECTING YOUR AREA") }
                if (b?.local.isNullOrEmpty()) {
                    item { AllClearCard(loc != null) }
                } else {
                    items(b!!.local) { a -> AlertCard(a) { selected = a } }
                }
                if (!b?.global.isNullOrEmpty()) {
                    item { Column { Spacer(Modifier.height(4.dp)); SectionHeader("GLOBAL EVENTS") } }
                    items(b!!.global) { a -> AlertCard(a) { selected = a } }
                }
            }
        }
    }

    selected?.let { a -> AlertDetailDialog(a) { selected = null } }
}

@Composable
private fun AreaBanner(localCount: Int, worst: AlertSeverity?, located: Boolean) {
    val color = worst?.let { sevColor(it) } ?: Color(0xFF34C759)
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.16f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(color, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        !located -> "Location unknown — showing global events"
                        localCount == 0 -> "No active warnings for your area"
                        else -> "$localCount active ${if (localCount == 1) "alert" else "alerts"} affecting your area"
                    },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
                Text("NWS warnings · USGS quakes · GDACS global disasters",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun AllClearCard(located: Boolean) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF34C759))
            Spacer(Modifier.width(12.dp))
            Text(
                if (located) "All clear near you right now. The feed refreshes every few minutes."
                else "Waiting for a location fix to check your area.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

@Composable
private fun AlertCard(a: ThreatAlert, onClick: () -> Unit) {
    val color = sevColor(a.severity)
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxSize().background(color))
            Column(Modifier.weight(1f).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(a.kind, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = color, modifier = Modifier.weight(1f))
                    Text(a.source, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Text(a.area, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                val meta = buildList {
                    a.distanceKm?.let { if (it > 1) add("${it.toInt()} km away") }
                    if (a.timeMs > 0) add(ago(a.timeMs))
                }.joinToString("  ·  ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                }
            }
        }
    }
}

@Composable
private fun AlertDetailDialog(a: ThreatAlert, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(a.kind) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(a.area, fontWeight = FontWeight.SemiBold)
                val meta = buildList {
                    add(a.severity.name.lowercase().replaceFirstChar { it.uppercase() })
                    a.distanceKm?.let { if (it > 1) add("${it.toInt()} km away") }
                    if (a.timeMs > 0) add(ago(a.timeMs))
                    add("via ${a.source}")
                }.joinToString("  ·  ")
                Text(meta, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                if (a.detail.isNotBlank()) Text(a.detail, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

private fun ago(ms: Long): String {
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000 -> "just now"
        d < 3_600_000 -> "${d / 60_000} min ago"
        d < 86_400_000 -> "${d / 3_600_000} h ago"
        else -> "${d / 86_400_000} d ago"
    }
}
