package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.cak3d.biscuitmesh.model.BoardHealth
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import com.cak3d.biscuitmesh.protocol.StatusCode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// A terminal palette for the debug console — independent of the app theme.
private val DBG_BG = Color(0xFF07080A)
private val DBG_PANEL = Color(0xFF101217)
private val DBG_INK = Color(0xFFE8E6DC)
private val DBG_DIM = Color(0xFF6B7078)
private val DBG_GREEN = Color(0xFF53D38A)
private val DBG_RED = Color(0xFFE5484D)
private val DBG_AMBER = Color(0xFFF2AC2A)
private val DBG_CYAN = Color(0xFF4FC3E8)
private val DBG_PLUM = Color(0xFFB24BFF)
private val DMONO = FontFamily.Monospace

/** Debug chips — handy commands to poke the board from here (free text field for the rest). */
private val DEBUG_CMDS = listOf(
    "status", "ui:sync", "ccdiag", "scanall", "stopscan", "scanap", "sniffbt:type=all",
)

private val TIME_FMT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

/**
 * A live, raw window into one board over BLE/USB — the console I use to debug the
 * T-Embed when serial isn't available. Shows parsed telemetry (heap, radios, link,
 * battery), the raw TYPE:SUBTYPE:PAYLOAD stream colour-coded by type, and a command
 * field so any CMD line can be fired at the board.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DebugScreen(vm: MainViewModel, address: String?, onBack: () -> Unit) {
    val devicesFlow = vm.devices
    val logFlow = vm.log
    if (address == null || devicesFlow == null || logFlow == null) {
        DebugMissing(onBack); return
    }
    val devices by devicesFlow.collectAsStateWithLifecycle()
    val log by logFlow.collectAsStateWithLifecycle()
    val device = devices[address]

    val lines = remember(log, address) { log.filter { it.address == address } }
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }
    var input by remember { mutableStateOf("") }

    Scaffold(
        containerColor = DBG_BG,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DBG_PANEL, titleContentColor = DBG_INK,
                    navigationIconContentColor = DBG_INK, actionIconContentColor = DBG_INK,
                ),
                title = {
                    Column {
                        Text("DEBUG · ${device?.typeLabel() ?: "board"}",
                            fontFamily = DMONO, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(address, fontFamily = DMONO, fontSize = 10.sp, color = DBG_DIM)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { vm.clearLog() }) {
                        Icon(Icons.Filled.DeleteSweep, "Clear", tint = DBG_DIM)
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 10.dp)) {
            PhoneStatusBar(
                boardConnected = device?.isLive == true,
                boardLink = device?.health?.link ?: device?.status?.link,
                modifier = Modifier.padding(top = 8.dp),
            )
            TelemetryGrid(device?.health, device?.status?.statusCode, device?.status?.activeOp)

            // raw protocol stream
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .background(DBG_PANEL, RoundedCornerShape(8.dp))
                    .padding(8.dp),
            ) {
                if (lines.isEmpty()) {
                    Text("waiting for traffic…", color = DBG_DIM, fontFamily = DMONO, fontSize = 12.sp)
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(lines) { e -> LogLine(e.timeMs, e.message) }
                    }
                }
            }

            // quick debug commands
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DEBUG_CMDS.forEach { cmd ->
                    Box(
                        Modifier.padding(bottom = 6.dp)
                            .background(DBG_BG, RoundedCornerShape(6.dp))
                            .border(1.dp, DBG_CYAN.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable { vm.send(address, cmd) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(cmd, color = DBG_CYAN, fontFamily = DMONO, fontSize = 11.sp)
                    }
                }
            }

            // free-text command
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("CMD line (e.g. ccdiag)", fontFamily = DMONO, color = DBG_DIM) },
                )
                IconButton(
                    onClick = { if (input.isNotBlank()) { vm.send(address, input.trim()); input = "" } },
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = DBG_GREEN)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TelemetryGrid(health: BoardHealth?, statusCode: Int?, op: String?) {
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val state = statusCode?.let { StatusCode.from(it).label } ?: "—"
        Stat("STATE", state, if (statusCode == 1) DBG_GREEN else DBG_AMBER)
        BitStat("CC1101", health?.cc1101)
        BitStat("SD", health?.sd)
        BitStat("NFC", health?.nfc)
        Stat("LINK", health?.link ?: "—",
            when (health?.link) { "ok" -> DBG_GREEN; "wait" -> DBG_AMBER; else -> DBG_DIM })
        Stat("BATT", health?.battery?.let { "$it%" } ?: "—", DBG_INK)
        Stat("HEAP", health?.heapFree?.let { "${it / 1024}k" } ?: "—", DBG_INK)
        Stat("UPTIME", health?.uptimeSec?.let { fmtUptime(it) } ?: "—", DBG_DIM)
        if (!op.isNullOrEmpty() && op != "idle") Stat("OP", op, DBG_PLUM)
    }
}

@Composable
private fun BitStat(label: String, v: Boolean?) =
    Stat(label, if (v == null) "—" else if (v) "OK" else "FAIL",
        when (v) { true -> DBG_GREEN; false -> DBG_RED; null -> DBG_DIM })

@Composable
private fun Stat(label: String, value: String, tint: Color) {
    Box(
        Modifier.padding(bottom = 6.dp)
            .background(DBG_PANEL, RoundedCornerShape(6.dp))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$label ", color = DBG_DIM, fontFamily = DMONO, fontSize = 10.sp)
            Text(value, color = tint, fontFamily = DMONO, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LogLine(timeMs: Long, m: BiscuitMessage) {
    val tint = when (m.type) {
        BiscuitMessage.Type.CMD -> DBG_CYAN
        BiscuitMessage.Type.RSP -> DBG_GREEN
        BiscuitMessage.Type.STATUS -> DBG_AMBER
        BiscuitMessage.Type.ERROR -> DBG_RED
        BiscuitMessage.Type.DATA -> DBG_INK
        BiscuitMessage.Type.TIME -> DBG_DIM
        BiscuitMessage.Type.UNKNOWN -> DBG_DIM
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(TIME_FMT.format(Date(timeMs)), color = DBG_DIM, fontFamily = DMONO, fontSize = 10.sp)
        Text("  " + m.raw.trimEnd('\r', '\n'), color = tint, fontFamily = DMONO, fontSize = 11.sp)
    }
}

private fun fmtUptime(s: Long): String {
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "${h}h${m}m" else if (m > 0) "${m}m${sec}s" else "${sec}s"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugMissing(onBack: () -> Unit) {
    Scaffold(
        containerColor = DBG_BG,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DBG_PANEL, titleContentColor = DBG_INK),
                title = { Text("Debug", fontFamily = DMONO) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = DBG_INK) } },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
            Text("No board selected.", color = DBG_DIM, fontFamily = DMONO)
        }
    }
}
