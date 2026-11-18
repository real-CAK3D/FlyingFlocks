package com.cak3d.biscuitmesh.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cak3d.biscuitmesh.model.LogEntry
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import com.cak3d.biscuitmesh.sensors.OuiDb

// Live meters for the Misc / MaurCAK3D / T-Embed deck. Each panel reads a firmware
// DATA:* stream that the rest of the app only dumped to the raw log, and renders it
// the way the WiFi / BT / Sub-GHz pages render theirs. All are driven off vm.log, so
// they light up as soon as the matching board scan runs and stay dark otherwise.

private val PKT_BEACON = Color(0xFF22D3EE)   // beacons — cyan
private val PKT_DATA = Color(0xFF7C9CFF)     // data — blue
private val PKT_PROBE = Color(0xFFF59E0B)    // probes — amber
private val PKT_DEAUTH = Color(0xFFFF5470)   // deauths — red (the attack signature)
private val VIZ_ACCENT = Color(0xFFA855F7)   // the Misc deck's violet
private const val VIZ_WINDOW = 60            // samples kept on screen
private const val DEAUTH_ALARM = 5           // deauth frames/s that trips the attack alert

private fun List<LogEntry>.dataOf(subtype: String) =
    asSequence().filter { it.message.type == BiscuitMessage.Type.DATA && it.message.subtype == subtype }

// ---------------------------------------------------------------------------
// DATA:PKT — 802.11 frame-type counters (beacons,deauths,probes,data), 1/sec.
// ---------------------------------------------------------------------------

private data class PktSample(val beacons: Int, val deauths: Int, val probes: Int, val data: Int)

/**
 * Live packet monitor: a four-series graph of the air's frame mix, plus a loud
 * deauth-flood alarm. A burst of deauth/disassoc frames is the signature of a
 * device being forced off Wi-Fi, so that series gets its own red alert banner —
 * this is passive attack *detection*, never transmission.
 */
@Composable
fun PacketMonitorCard(log: List<LogEntry>) {
    val samples = log.dataOf("PKT").mapNotNull { e ->
        val f = e.message.payload.split(",")
        if (f.size < 4) return@mapNotNull null
        PktSample(
            f[0].toIntOrNull() ?: 0, f[1].toIntOrNull() ?: 0,
            f[2].toIntOrNull() ?: 0, f[3].toIntOrNull() ?: 0,
        )
    }.toList().takeLast(VIZ_WINDOW)
    if (samples.isEmpty()) return

    val latest = samples.last()
    val recentDeauth = samples.takeLast(5).maxOf { it.deauths }
    val underAttack = recentDeauth >= DEAUTH_ALARM
    val total = latest.beacons + latest.deauths + latest.probes + latest.data

    VizCard("PACKET MONITOR", "live 802.11 frame mix · $total frames/s", Icons.Default.Wifi, VIZ_ACCENT) {
        if (underAttack) DeauthAlarm(recentDeauth)

        // Four-series line graph over the time window.
        val maxV = samples.maxOf { maxOf(it.beacons, it.data, it.probes, it.deauths) }.coerceAtLeast(1)
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val w = size.width; val h = size.height
            drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, h - 1f), Offset(w, h - 1f), 1.5f)
            fun series(pick: (PktSample) -> Int, color: Color, fill: Boolean) {
                if (samples.size < 2) return
                val path = Path()
                samples.forEachIndexed { i, s ->
                    val x = i.toFloat() / (samples.size - 1) * w
                    val y = h - (pick(s).toFloat() / maxV) * (h * 0.92f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (fill) {
                    val area = Path().apply {
                        addPath(path); lineTo(w, h); lineTo(0f, h); close()
                    }
                    drawPath(area, color.copy(alpha = 0.18f))
                }
                drawPath(path, color, style = Stroke(width = if (fill) 3f else 2f))
            }
            series({ it.beacons }, PKT_BEACON, false)
            series({ it.data }, PKT_DATA, false)
            series({ it.probes }, PKT_PROBE, false)
            series({ it.deauths }, PKT_DEAUTH, true)   // the one that matters most, filled
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FrameTile("beacons", latest.beacons, PKT_BEACON, Modifier.weight(1f))
            FrameTile("data", latest.data, PKT_DATA, Modifier.weight(1f))
            FrameTile("probes", latest.probes, PKT_PROBE, Modifier.weight(1f))
            FrameTile("deauths", latest.deauths, PKT_DEAUTH, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DeauthAlarm(rate: Int) {
    val pulse = rememberInfiniteTransition(label = "alarm")
    val a by pulse.animateFloat(
        0.35f, 0.9f,
        infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse",
    )
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(PKT_DEAUTH.copy(alpha = a * 0.25f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.GppMaybe, contentDescription = null, tint = PKT_DEAUTH)
        Column {
            Text("DEAUTH FLOOD DETECTED", style = MaterialTheme.typography.labelLarge,
                color = PKT_DEAUTH, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("$rate deauth/disassoc frames a second nearby — a device is being forced off Wi-Fi. Detection only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun FrameTile(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$value", style = MaterialTheme.typography.titleMedium, color = color,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
    }
}

// ---------------------------------------------------------------------------
// DATA:PROBE — SSIDs nearby clients are probing for (ssid,rssi).
// ---------------------------------------------------------------------------

/** Preferred-network list: which Wi-Fi names the phones around you are quietly calling out for. */
@Composable
fun ProbeRequestCard(log: List<LogEntry>) {
    data class Probe(val ssid: String, val count: Int, val rssi: Int)
    val byName = HashMap<String, Int>()
    val lastRssi = HashMap<String, Int>()
    log.dataOf("PROBE").forEach { e ->
        val f = e.message.payload.split(",")
        val ssid = f.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@forEach
        byName[ssid] = (byName[ssid] ?: 0) + 1
        f.getOrNull(1)?.toIntOrNull()?.let { lastRssi[ssid] = it }
    }
    if (byName.isEmpty()) return
    val top = byName.entries.sortedByDescending { it.value }.take(8)
        .map { Probe(it.key, it.value, lastRssi[it.key] ?: -100) }
    val maxC = top.maxOf { it.count }.coerceAtLeast(1)

    VizCard("PROBE REQUESTS", "${byName.size} network name${if (byName.size == 1) "" else "s"} being hunted for", Icons.Default.Sensors, PKT_PROBE) {
        Text("Phones broadcast the networks they remember. Each name below is a place a nearby device has connected before.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        top.forEach { p -> VizBar(p.ssid, p.count, maxC, "${p.rssi} dBm", PKT_PROBE) }
    }
}

// ---------------------------------------------------------------------------
// DATA:STA — client stations near APs (srcMac,bssid,rssi).
// ---------------------------------------------------------------------------

/** Station list: the client devices seen talking to access points, grouped by the AP they're on. */
@Composable
fun StationListCard(log: List<LogEntry>) {
    data class Sta(val mac: String, val bssid: String, val rssi: Int)
    val stations = log.dataOf("STA").mapNotNull { e ->
        val f = e.message.payload.split(",")
        val mac = f.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        Sta(mac, f.getOrNull(1) ?: "?", f.getOrNull(2)?.toIntOrNull() ?: -100)
    }.associateBy { it.mac }.values            // latest per client MAC
    if (stations.isEmpty()) return
    val byAp = stations.groupBy { it.bssid }.entries.sortedByDescending { it.value.size }.take(6)

    VizCard("STATIONS", "${stations.size} client${if (stations.size == 1) "" else "s"} on ${byAp.size} AP${if (byAp.size == 1) "" else "s"}", Icons.Default.Router, PKT_DATA) {
        byAp.forEach { (bssid, clients) ->
            val apVendor = OuiDb.vendor(bssid)
            Text("AP ${bssid.uppercase()}${apVendor?.let { "  ·  $it" } ?: ""}  ·  ${clients.size}",
                style = MaterialTheme.typography.labelSmall, color = PKT_DATA, fontFamily = FontFamily.Monospace)
            clients.sortedByDescending { it.rssi }.take(6).forEach { c ->
                Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.mac.uppercase(), style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    OuiDb.vendor(c.mac)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.padding(end = 8.dp))
                    }
                    Text("${c.rssi} dBm", style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace, color = PKT_DATA)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// DATA:WARDRIVE — per-board capture counters (count,apN,btN,rfN,live|offline).
// ---------------------------------------------------------------------------

/** Live capture counter: a board's running AP / BT / RF tally and whether it's logging to SD. */
@Composable
fun CaptureCounterCard(log: List<LogEntry>) {
    data class Cap(val ap: Int, val bt: Int, val rf: Int, val logging: Boolean, val timeMs: Long)
    val latest = log.dataOf("WARDRIVE").mapNotNull { e ->
        val f = e.message.payload.split(",")
        when (f.getOrNull(0)) {
            "count" -> Cap(f.getOrNull(1)?.toIntOrNull() ?: 0, f.getOrNull(2)?.toIntOrNull() ?: 0,
                f.getOrNull(3)?.toIntOrNull() ?: 0, f.getOrNull(4) == "live", e.timeMs)
            "done" -> Cap(f.getOrNull(2)?.toIntOrNull() ?: 0, f.getOrNull(3)?.toIntOrNull() ?: 0,
                f.getOrNull(4)?.toIntOrNull() ?: 0, false, e.timeMs)
            else -> null
        }
    }.maxByOrNull { it.timeMs } ?: return

    VizCard("CAPTURE SESSION", if (latest.logging) "live · logging to SD" else "idle", Icons.Default.Storage,
        if (latest.logging) Color(0xFF34D399) else VIZ_ACCENT) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FrameTile("APs", latest.ap, PKT_BEACON, Modifier.weight(1f))
            FrameTile("BT", latest.bt, PKT_DATA, Modifier.weight(1f))
            FrameTile("sub-GHz", latest.rf, PKT_PROBE, Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------------------
// DATA:MODE — current engine mode chips (<mode>,start|stop|idle).
// ---------------------------------------------------------------------------

/** A row of chips showing which engines each board currently has running. */
@Composable
fun ModeChips(log: List<LogEntry>) {
    val active = LinkedHashMap<String, Boolean>()
    log.dataOf("MODE").forEach { e ->
        val f = e.message.payload.split(",")
        val mode = f.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@forEach
        active[mode] = f.getOrNull(1) == "start"
    }
    val running = active.filterValues { it }.keys.toList()
    if (running.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        running.forEach { mode ->
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .background(Color(0xFF34D399).copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(Color(0xFF34D399)))
                Text(mode, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace,
                    color = Color(0xFF34D399))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------

/** Standard framed meter card — icon + title + subtitle header, themed to match the deck. */
@Composable
fun VizCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    content: @Composable () -> Unit,
) {
    com.cak3d.biscuitmesh.ui.theme.ThemedCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = accent,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                }
            }
            content()
        }
    }
}

/** A labelled horizontal bar with a trailing value — the deck's standard tally row. */
@Composable
private fun VizBar(label: String, count: Int, max: Int, trailing: String, color: Color) {
    val frac = (count.toFloat() / max).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
            Text("×$count  ·  $trailing", style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace, color = color)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))) {
            if (frac > 0f) Box(Modifier.fillMaxWidth(frac).height(8.dp)
                .clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}
