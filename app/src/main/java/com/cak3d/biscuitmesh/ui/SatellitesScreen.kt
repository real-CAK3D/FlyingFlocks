package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.sensors.Sat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val GRID = Color(0xFF00E5A0).copy(alpha = 0.25f)

private fun constColor(c: String): Color = when (c) {
    "GPS" -> Color(0xFF22D3EE)
    "GLONASS" -> Color(0xFFFF7AD9)
    "Galileo" -> Color(0xFF34D399)
    "BeiDou" -> Color(0xFFF59E0B)
    "QZSS" -> Color(0xFF7C9CFF)
    "SBAS" -> Color(0xFFA78BFA)
    "NavIC" -> Color(0xFFFF8A3D)
    else -> Color(0xFF9CA3AF)
}

/**
 * GNSS satellites — the live sky of what the phone's GPS receiver sees, and which
 * satellites it's actually using to compute the fix (filled = used in fix).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatellitesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val gnss by vm.gnssState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        vm.startGnss()
        onDispose { vm.stopGnss() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GPS Satellites") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("head") {
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("GPS SKY", style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF22D3EE), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(
                            if (!gnss.supported) "This device doesn't report satellite status."
                            else "${gnss.usedCount} used in fix · ${gnss.visibleCount} in view. Filled = locked into the GPS fix.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (gnss.sats.isNotEmpty()) {
                item("sky") {
                    Card(
                        Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF05080A)),
                    ) {
                        Box(Modifier.padding(16.dp)) { SkyPlot(gnss.sats, Modifier.fillMaxWidth().aspectRatio(1f)) }
                    }
                }
                // Group by constellation.
                val byConst = gnss.sats.groupBy { it.constellation }
                byConst.forEach { (name, sats) ->
                    item("c-$name") {
                        Text("${name.uppercase()} · ${sats.count { it.usedInFix }}/${sats.size} used",
                            style = MaterialTheme.typography.labelMedium, color = constColor(name),
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    // Dual-band receivers report the same svid on two carriers (L1+L5), so
                    // svid alone is not a unique key — index within the group guarantees it is.
                    items(sats.size, key = { "$name-$it" }) { SatRow(sats[it]) }
                }
            } else if (gnss.supported) {
                item("wait") {
                    Text("Acquiring satellites… step outside for a clear view of the sky.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun SkyPlot(sats: List<Sat>, modifier: Modifier) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = min(size.width, size.height) / 2f * 0.92f
        // elevation rings: 90° (center) .. 0° (edge), rings at 60/30/0
        for (k in 1..3) drawCircle(GRID, r * k / 3f, Offset(cx, cy), style = Stroke(1f))
        drawLine(GRID, Offset(cx - r, cy), Offset(cx + r, cy), 1f)
        drawLine(GRID, Offset(cx, cy - r), Offset(cx, cy + r), 1f)
        sats.forEach { s ->
            if (s.elevationDeg < 0f) return@forEach
            val rr = (90f - s.elevationDeg) / 90f * r
            val az = Math.toRadians(s.azimuthDeg.toDouble())
            val x = cx + (rr * sin(az)).toFloat()
            val y = cy - (rr * cos(az)).toFloat()
            val col = constColor(s.constellation)
            val dot = 6f + (s.cn0 / 50f).coerceIn(0f, 1f) * 7f
            if (s.usedInFix) {
                drawCircle(col.copy(alpha = 0.3f), dot + 4f, Offset(x, y))
                drawCircle(col, dot, Offset(x, y))
            } else {
                drawCircle(col, dot, Offset(x, y), style = Stroke(2f))
            }
        }
        drawCircle(Color(0xFF00E5A0), 3f, Offset(cx, cy))  // zenith
    }
}

@Composable
private fun SatRow(s: Sat) {
    val col = constColor(s.constellation)
    val strength = (s.cn0 / 50f).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("#${s.svid}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            color = if (s.usedInFix) col else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.width(44.dp))
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))) {
            if (strength > 0f) Box(Modifier.fillMaxWidth(strength).height(10.dp)
                .clip(RoundedCornerShape(5.dp)).background(col.copy(alpha = if (s.usedInFix) 1f else 0.4f)))
        }
        Text("${s.cn0.toInt()}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.width(32.dp))
    }
}
