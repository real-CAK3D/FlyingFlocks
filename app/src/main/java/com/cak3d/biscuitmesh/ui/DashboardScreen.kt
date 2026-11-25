package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.cak3d.biscuitmesh.model.BiscuitDevice
import com.cak3d.biscuitmesh.sensors.PhoneScanRow
import com.cak3d.biscuitmesh.wardrive.SampleKind
import com.cak3d.biscuitmesh.wardrive.WardriveSample
import kotlinx.coroutines.flow.MutableStateFlow
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

/**
 * Unfolded-Fold dashboard: the map, the Compass/Locator/Radar nav tools, and a live
 * device list all on one screen. On a narrow (folded / phone) screen the three panes
 * stack; on a wide (unfolded) screen the map sits beside the tools and the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: MainViewModel, onBack: () -> Unit) {
    // GPS + cell for the status strip (phone Wi-Fi/BT scan is started by the embedded radar).
    DisposableEffect(Unit) {
        vm.startGnss(); vm.startCell()
        onDispose { vm.stopGnss(); vm.stopCell() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Dashboard",
                        "Everything at a glance: a live status strip (Wi-Fi / BT / sub-GHz / GPS lock / cell), the " +
                            "map with your track and finds, the radar/compass/locator tools, fleet + mesh health, and " +
                            "the live device list. On an unfolded screen the panes sit side by side.",
                    )
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 640.dp
            if (wide) {
                Column(Modifier.fillMaxSize()) {
                    StatusStrip(vm, Modifier.fillMaxWidth())
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        MapPane(vm, Modifier.weight(1.35f).fillMaxHeight())
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Box(Modifier.weight(1.1f).fillMaxWidth()) {
                                NavigateScreen(vm = vm, onBack = onBack, startMode = NavMode.RADAR)
                            }
                            DashboardPager(vm, Modifier.weight(1.7f).fillMaxWidth())
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    StatusStrip(vm, Modifier.fillMaxWidth())
                    MapPane(vm, Modifier.fillMaxWidth().weight(1.2f))
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        NavigateScreen(vm = vm, onBack = onBack, startMode = NavMode.RADAR)
                    }
                    DashboardPager(vm, Modifier.fillMaxWidth().weight(1.8f))
                }
            }
        }
    }
}

/**
 * The dashboard's lower half: swipe between the fleet/device view and extra readings (a signal-
 * density spectrum and a sources bar chart) without disturbing the map or radar above.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardPager(vm: MainViewModel, modifier: Modifier) {
    val titles = listOf("FLEET", "SIGNAL", "BANDS")
    val pager = rememberPagerState(pageCount = { titles.size })
    Column(modifier) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            when (page) {
                0 -> Column(Modifier.fillMaxSize()) {
                    FleetHealthPane(vm, Modifier.fillMaxWidth().weight(0.8f))
                    DevicePane(vm, Modifier.fillMaxWidth().weight(1f))
                }
                1 -> DashSignalPage(vm)
                else -> DashBandsPage(vm)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            titles.forEachIndexed { i, label ->
                if (i == pager.currentPage) {
                    Text(label, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp))
                } else {
                    Box(Modifier.padding(horizontal = 4.dp).size(6.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)))
                }
            }
        }
    }
}

/** Dashboard page 2: a signal-strength density ridge of every phone contact. */
@Composable
private fun DashSignalPage(vm: MainViewModel) {
    val rows by vm.phoneScanResults.collectAsStateWithLifecycle(initialValue = emptyList())
    val contacts = rows.filter { it.rssi < 0 }
    val accent = Color(0xFF22D3EE)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SIGNAL DENSITY · ${contacts.size}", color = accent, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Wi-Fi + Bluetooth by signal · weak ← → strong",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        val buckets = 30
        val counts = IntArray(buckets)
        contacts.forEach {
            val f = ((it.rssi + 100f) / 70f).coerceIn(0f, 0.999f); counts[(f * buckets).toInt()]++
        }
        val maxC = (counts.maxOrNull() ?: 1).coerceAtLeast(1)
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val w = size.width; val h = size.height; val base = h - 8f
            val sm = FloatArray(buckets) { i ->
                val a = counts[(i - 1).coerceAtLeast(0)].toFloat() / maxC
                val b = counts[i].toFloat() / maxC
                val c = counts[(i + 1).coerceAtMost(buckets - 1)].toFloat() / maxC
                a * 0.25f + b * 0.5f + c * 0.25f
            }
            val line = Path(); val fill = Path()
            for (i in 0 until buckets) {
                val px = i / (buckets - 1f) * w; val py = base - sm[i] * (base - 6f)
                if (i == 0) { line.moveTo(px, py); fill.moveTo(px, base); fill.lineTo(px, py) }
                else { line.lineTo(px, py); fill.lineTo(px, py) }
            }
            fill.lineTo(w, base); fill.close()
            drawPath(fill, accent.copy(alpha = 0.18f))
            drawPath(line, accent, style = Stroke(width = 2f))
            drawLine(accent.copy(alpha = 0.3f), Offset(0f, base), Offset(w, base), 1.5f)
            contacts.forEach {
                val f = ((it.rssi + 100f) / 70f).coerceIn(0f, 1f)
                drawCircle(accent.copy(alpha = 0.4f), 2f, Offset(f * w, base - 2f))
            }
        }
    }
}

/** Dashboard page 3: a bar chart of how many contacts each source is hearing. */
@Composable
private fun DashBandsPage(vm: MainViewModel) {
    val rows by vm.phoneScanResults.collectAsStateWithLifecycle(initialValue = emptyList())
    val gnss by vm.gnssState.collectAsStateWithLifecycle()
    val cell by vm.cellState.collectAsStateWithLifecycle()
    val bands = listOf(
        Triple("Wi-Fi 2.4", rows.count { it.kind == SampleKind.AP && it.channel in 1..14 }, Color(0xFF5FD36A)),
        Triple("Wi-Fi 5/6", rows.count { it.kind == SampleKind.AP && it.channel >= 32 }, Color(0xFF22D3EE)),
        Triple("Bluetooth", rows.count { it.kind == SampleKind.BT }, Color(0xFFFF6FA5)),
        Triple("Satellites", gnss.sats.count { it.cn0 > 0f }, Color(0xFFFFD166)),
        Triple("Cellular", cell.cells.size, Color(0xFFFF9F45)),
    )
    val max = bands.maxOf { it.second }.coerceAtLeast(1)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SIGNAL SOURCES", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = 12.sp)
        bands.forEach { (label, n, col) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(72.dp))
                Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))) {
                    if (n > 0) Box(Modifier.fillMaxWidth(n.toFloat() / max).fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp)).background(col))
                }
                Text("$n", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, color = col, modifier = Modifier.width(30.dp))
            }
        }
    }
}

@Composable
private fun MapPane(vm: MainViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
            controller.setZoom(16.5)
        }
    }
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }
    val centered = remember { booleanArrayOf(false) }
    LaunchedEffect(Unit) {
        if (!centered[0]) {
            val fix = vm.oneShotLocation()
            if (fix != null) { mapView.controller.setCenter(GeoPoint(fix.lat, fix.lon)); centered[0] = true }
        }
    }

    val samplesFlow = vm.wardriveSamples
    val samplesState = remember(samplesFlow) { samplesFlow ?: MutableStateFlow(emptyMap<String, WardriveSample>()) }
    val samples by samplesState.collectAsStateWithLifecycle(initialValue = emptyMap())
    val trackFlow = vm.wardriveTrack
    val trackState = remember(trackFlow) { trackFlow ?: MutableStateFlow(emptyList()) }
    val track by trackState.collectAsStateWithLifecycle(initialValue = emptyList())

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { map ->
            map.overlays.clear()
            if (track.size >= 2) {
                val line = Polyline(map)
                line.setPoints(track.map { GeoPoint(it.lat, it.lon) })
                line.outlinePaint.color = android.graphics.Color.parseColor("#22D3EE")
                line.outlinePaint.strokeWidth = 9f
                map.overlays.add(line)
            }
            val dots = samples.values.map { s ->
                val color = if (s.kind == SampleKind.RF) android.graphics.Color.parseColor("#F0B429") else colorForSample(s)
                val ch = if (s.channel > 0) "  ch${s.channel}" else ""
                DotsOverlay.Dot(GeoPoint(s.lat, s.lon), color, sizeForKind(s.kind) * 2.75f / 2f,
                    "${s.label.ifBlank { s.id }}  ${s.rssi} dBm$ch")
            }
            if (dots.isNotEmpty()) map.overlays.add(DotsOverlay(dots))
            map.invalidate()
        },
    )
}

@Composable
private fun DevicePane(vm: MainViewModel, modifier: Modifier) {
    val results by vm.phoneScanResults.collectAsStateWithLifecycle(initialValue = emptyList())
    val sorted = remember(results) { results.sortedByDescending { it.rssi } }
    var selected by remember { mutableStateOf<PhoneScanRow?>(null) }
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Text(
            "DEVICES · ${sorted.size}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(12.dp),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
            items(sorted, key = { it.id }) { r ->
                val band = when {
                    r.kind == SampleKind.BT -> "BT" to Color(0xFFFF5470)
                    r.channel >= 1000 -> "6E" to Color(0xFFF0B429)
                    r.channel in 32..179 -> "5G" to Color(0xFF22D3EE)
                    r.channel in 1..14 -> "2G4" to Color(0xFF5FD36A)
                    else -> "·" to Color.Gray
                }
                Row(
                    Modifier.fillMaxWidth().clickable { selected = r }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.background(band.second.copy(alpha = 0.22f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)) {
                        Text(band.first, color = band.second, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                    Text(
                        r.label.ifBlank { r.id },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${r.rssi}", color = band.second, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        selected?.let { r ->
            val type = when (r.kind) {
                SampleKind.BT -> "Bluetooth"
                SampleKind.RF -> "Sub-GHz"
                else -> if (r.channel >= 32) "Wi-Fi 5 GHz" else "Wi-Fi 2.4 GHz"
            }
            AlertDialog(
                onDismissRequest = { selected = null },
                confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
                title = { Text(r.label.ifBlank { r.id }) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Address: ${r.id}")
                        Text("Type: $type")
                        if (r.channel > 0) Text("Channel: ${r.channel}")
                        Text("Signal: ${r.rssi} dBm")
                        if (r.security.isNotBlank()) Text("Security: ${r.security}")
                        if (r.standard.isNotBlank()) Text("Standard: ${r.standard}")
                    }
                },
            )
        }
    }
}

@Composable
private fun StatusStrip(vm: MainViewModel, modifier: Modifier) {
    val results by vm.phoneScanResults.collectAsStateWithLifecycle(initialValue = emptyList())
    val gnss by vm.gnssState.collectAsStateWithLifecycle()
    val cell by vm.cellState.collectAsStateWithLifecycle()
    val samplesFlow = vm.wardriveSamples
    val samplesState = remember(samplesFlow) { samplesFlow ?: MutableStateFlow(emptyMap<String, WardriveSample>()) }
    val samples by samplesState.collectAsStateWithLifecycle(initialValue = emptyMap())

    val wifi = results.count { it.kind == SampleKind.AP || it.kind == SampleKind.STATION }
    val bt = results.count { it.kind == SampleKind.BT }
    val rf = samples.values.count { it.kind == SampleKind.RF }
    Row(
        modifier.background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatTile("WIFI", "$wifi", Color(0xFF5FD36A), Modifier.weight(1f))
        StatTile("BT", "$bt", Color(0xFFFF5470), Modifier.weight(1f))
        StatTile("SUB-G", "$rf", Color(0xFFF0B429), Modifier.weight(1f))
        StatTile("GPS", "${gnss.usedCount}/${gnss.visibleCount}", Color(0xFF22D3EE), Modifier.weight(1f))
        StatTile("CELL", "${cell.cells.size}", Color(0xFF8566E8), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, tint: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = tint, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            fontFamily = FontFamily.Monospace, fontSize = 9.sp)
    }
}

@Composable
private fun FleetHealthPane(vm: MainViewModel, modifier: Modifier) {
    val devicesFlow = vm.devices
    val devicesState = remember(devicesFlow) { devicesFlow ?: MutableStateFlow(emptyMap<String, BiscuitDevice>()) }
    val devices by devicesState.collectAsStateWithLifecycle(initialValue = emptyMap())
    val meshNodes by vm.meshNodes.collectAsStateWithLifecycle()
    val homeBase by vm.homeBase.collectAsStateWithLifecycle()
    val live = remember(devices) { devices.values.filter { it.isLive } }
    Column(
        modifier.background(MaterialTheme.colorScheme.surface).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "FLEET · ${live.size} live · ${meshNodes.size} mesh",
            style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
        )
        live.take(5).forEach { d ->
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(d.displayName, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    modifier = Modifier.weight(1f))
                Text(d.status?.battery?.let { "$it%" } ?: "—", style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
        homeBase?.let {
            Text("Home base · ${it.label}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        if (live.isEmpty() && meshNodes.isEmpty()) {
            Text("No boards or mesh nodes connected", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}
