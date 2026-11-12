package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.R
import com.cak3d.biscuitmesh.model.BiscuitDevice
import com.cak3d.biscuitmesh.model.LogEntry
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

// Exact T-Embed firmware palette (RGB565 values from the sketch) so the replica
// matches the real screen 1:1.
private val TE_BG = Color(0xFF08080A)
private val TE_PLUM = Color(0xFFB24BFF)
private val TE_BLOOD = Color(0xFFCA1C24)
private val TE_AMBER = Color(0xFFF2AC2A)
private val TE_INK = Color(0xFFE8E6DC)
private val TE_DIM = Color(0xFF70707C)
private val TE_PANEL = Color(0xFF121218)
private val MONO = FontFamily.Monospace

private data class MirrorUi(
    val screen: String, val title: String, val sel: Int, val mood: Int, val link: String,
    val total: Int, val off: Int, val rows: List<String>,
    val code: String?, val freq: Double?, val rssi: Int?, val step: Double?, val log: List<String>,
)

private fun parseMirror(payload: String): MirrorUi? = runCatching {
    val o = JSONObject(payload)
    fun arr(k: String): List<String> =
        o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
    MirrorUi(
        screen = o.optString("s", "?"), title = o.optString("t", ""), sel = o.optInt("i", 0),
        mood = o.optInt("m", 0), link = o.optString("link", "off"),
        total = o.optInt("n", 0), off = o.optInt("o", 0), rows = arr("r"),
        code = if (o.has("code")) o.getString("code") else null,
        freq = if (o.has("f")) o.getDouble("f") else null,
        rssi = if (o.has("rssi")) o.getInt("rssi") else null,
        step = if (o.has("step")) o.getDouble("step") else null,
        log = arr("log"),
    )
}.getOrNull()

/**
 * Live mirror + remote control of a T-Embed ("Flocked Up"). The board streams a
 * compact screen model (DATA:UI) which we render as a replica of its screen; the
 * on-screen buttons (and tapping a row) send CMD:ui:* back, so the phone drives
 * the device as if it were the T-Embed itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirrorScreen(vm: MainViewModel, address: String?, onBack: () -> Unit) {
    val devFlow = remember(vm) { vm.devices ?: MutableStateFlow(emptyMap<String, BiscuitDevice>()) }
    val logFlow = remember(vm) { vm.log ?: MutableStateFlow(emptyList<LogEntry>()) }
    val devices by devFlow.collectAsStateWithLifecycle()
    val log by logFlow.collectAsStateWithLifecycle()
    val device = address?.let { devices[it] }

    val ui = remember(log, address) {
        log.lastOrNull {
            it.address == address && it.message.type == BiscuitMessage.Type.DATA && it.message.subtype == "UI"
        }?.let { parseMirror(it.message.payload) }
    }
    // Ask the board for a fresh frame when we open the mirror.
    LaunchedEffect(address) { if (address != null) vm.send(address, "ui:sync") }

    fun tap(cmd: String) { if (address != null) vm.send(address, cmd) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("MIRROR · ${device?.displayName ?: "board"}", fontFamily = MONO, fontSize = 15.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (ui == null) {
                Text(
                    "Waiting for the board's screen…\n\nConnect and link the T-Embed (enter the 6-digit code from " +
                        "its screen in the board's detail page), then its live screen shows here.",
                    color = TE_DIM, fontSize = 14.sp,
                )
            } else {
                DeviceScreen(ui) { globalIdx -> tap("ui:pick:$globalIdx") }
            }

            // Controls — the encoder + the two buttons, as taps.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CtrlButton("◀ BACK", TE_DIM, Modifier.weight(1f)) { tap("ui:back") }
                CtrlButton("▲", TE_INK, Modifier.weight(0.7f)) { tap("ui:up") }
                CtrlButton("▼", TE_INK, Modifier.weight(0.7f)) { tap("ui:down") }
                CtrlButton("● SELECT", TE_PLUM, Modifier.weight(1.3f)) { tap("ui:sel") }
            }
            Text(
                "Tap a row to jump to it and select. ▲/▼ moves the highlight, ● selects, ◀ goes back — " +
                    "exactly like the encoder on the device.",
                color = TE_DIM, fontSize = 11.sp, fontFamily = MONO,
            )
        }
    }
}

@Composable
private fun DeviceScreen(ui: MirrorUi, onPick: (Int) -> Unit) {
    Box(
        Modifier.fillMaxWidth().aspectRatio(320f / 170f)
            .background(TE_BG, RoundedCornerShape(10.dp))
            .border(2.dp, TE_PLUM, RoundedCornerShape(10.dp))
            .padding(8.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            // header bar
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("FLOCKED UP", color = TE_BLOOD, fontFamily = MONO, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("  ·  ${ui.title}", color = TE_DIM, fontFamily = MONO, fontSize = 11.sp,
                    modifier = Modifier.weight(1f), maxLines = 1)
                Box(
                    Modifier.size(9.dp).background(
                        if (ui.link == "ok") TE_AMBER else if (ui.link == "wait") TE_BLOOD else TE_DIM,
                        CircleShape,
                    ),
                )
            }
            Row(Modifier.fillMaxWidth().weight(1f).padding(top = 2.dp)) {
                // real crow sprite on the left
                CrowSprite(ui.mood, Modifier.weight(0.34f).fillMaxSize())
                // content on the right
                Box(Modifier.weight(0.66f).fillMaxSize()) {
                    when (ui.screen) {
                        "TUNE" -> TuneView(ui)
                        "LINK" -> CenteredBig(ui.code ?: "------", "link code")
                        "ACTION" -> LogView(ui)
                        "CONFIRM" -> ConfirmView()
                        "ABOUT" -> AboutView(ui)
                        else -> RowsView(ui, onPick)
                    }
                }
            }
        }
        // Flock accent — sits on the bottom border, breaking the frame, bottom-left.
        CrowAccent(Modifier.align(Alignment.BottomStart).offset(x = 8.dp, y = 9.dp))
    }
}

@Composable
private fun RowsView(ui: MirrorUi, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (ui.off > 0) Text("▲", color = TE_AMBER, fontFamily = MONO, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.End))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(ui.rows) { i, label ->
                val globalIdx = ui.off + i
                val selected = globalIdx == ui.sel
                Box(
                    Modifier.fillMaxWidth()
                        .background(if (selected) TE_PANEL else Color.Transparent, RoundedCornerShape(4.dp))
                        .then(if (selected) Modifier.border(1.dp, TE_BLOOD, RoundedCornerShape(4.dp)) else Modifier)
                        .clickable { onPick(globalIdx) }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(label, color = if (selected) TE_INK else TE_DIM, fontFamily = MONO,
                        fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        if (ui.off + ui.rows.size < ui.total) Text("▼", color = TE_AMBER, fontFamily = MONO, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.End))
    }
}

@Composable
private fun TuneView(ui: MirrorUi) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("%.2f MHz".format(ui.freq ?: 0.0), color = TE_INK, fontFamily = MONO,
            fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("step ${"%.2f".format(ui.step ?: 0.0)} MHz", color = TE_DIM, fontFamily = MONO, fontSize = 11.sp)
        val r = ui.rssi ?: -128
        val frac = ((r + 110f) / 90f).coerceIn(0.04f, 1f)
        Box(Modifier.fillMaxWidth().height(10.dp).padding(top = 8.dp)
            .background(TE_DIM.copy(alpha = 0.2f), RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(frac).height(10.dp)
                .background(if (r > -55) TE_BLOOD else if (r > -80) TE_AMBER else TE_PLUM, RoundedCornerShape(3.dp)))
        }
        Text("$r dBm", color = TE_INK, fontFamily = MONO, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun LogView(ui: MirrorUi) {
    Column(Modifier.fillMaxSize()) {
        if (ui.log.isEmpty()) Text("listening…", color = TE_DIM, fontFamily = MONO, fontSize = 12.sp)
        ui.log.forEachIndexed { i, line ->
            Text(line, color = if (i == 0) TE_INK else TE_DIM, fontFamily = MONO, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ConfirmView() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("Your OWN gear only.", color = TE_AMBER, fontFamily = MONO, fontSize = 12.sp)
        Text("This TRANSMITS.", color = TE_AMBER, fontFamily = MONO, fontSize = 12.sp)
        Text("● SELECT = confirm", color = TE_INK, fontFamily = MONO, fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp))
        Text("◀ BACK = cancel", color = TE_DIM, fontFamily = MONO, fontSize = 12.sp)
    }
}

@Composable
private fun AboutView(ui: MirrorUi) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("Flocked Up", color = TE_PLUM, fontFamily = MONO, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("FTM", color = TE_AMBER, fontFamily = MONO, fontSize = 12.sp)
        ui.code?.let { Text("link $it", color = TE_DIM, fontFamily = MONO, fontSize = 11.sp) }
    }
}

@Composable
private fun CenteredBig(big: String, label: String) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(big, color = TE_AMBER, fontFamily = MONO, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TE_DIM, fontFamily = MONO, fontSize = 11.sp)
    }
}

/** The real T-Embed crow sprite for this mood, drawn pixel-for-pixel (the same
 *  1-bit masks the firmware uses, exported to PNG), so the mirror matches the screen. */
@Composable
private fun CrowSprite(mood: Int, modifier: Modifier) {
    val res = when (mood) {
        1 -> R.drawable.crow_scan   // scanning
        2 -> R.drawable.crow_hit    // got one
        3 -> R.drawable.crow_flip   // FTM
        else -> R.drawable.crow_idle
    }
    Image(
        bitmap = ImageBitmap.imageResource(res),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,   // crisp pixels, no smoothing
    )
}

/** Three gulls in the distance (wings up, body dipping) that sit ON the border
 *  and break it — the bg backing notches the purple frame, like a flock crossing it. */
@Composable
private fun CrowAccent(modifier: Modifier) {
    Row(
        modifier.background(TE_BG).padding(horizontal = 5.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // near → far, so they read as a flock receding into the distance
        listOf(17f to 9f, 13f to 7f, 10f to 6f).forEach { (gw, gh) ->
            Canvas(Modifier.size(gw.dp, gh.dp)) {
                val w = size.width; val h = size.height
                val tip = h * 0.22f      // wingtips near the top
                val body = h * 0.88f     // body dips to the bottom centre
                val gull = Path().apply {
                    moveTo(0f, tip)
                    quadraticBezierTo(w * 0.26f, h * 0.08f, w * 0.5f, body)  // left wing: up, then to body
                    quadraticBezierTo(w * 0.74f, h * 0.08f, w, tip)         // right wing: body, up to tip
                }
                drawPath(gull, TE_BLOOD, style = Stroke(width = (h * 0.2f).coerceAtLeast(2f), cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
private fun CtrlButton(label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(54.dp)
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = tint, fontFamily = MONO, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
