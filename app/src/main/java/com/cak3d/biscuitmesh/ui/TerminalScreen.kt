package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.model.BiscuitDevice
import com.cak3d.biscuitmesh.model.LogEntry
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import kotlinx.coroutines.flow.MutableStateFlow

private val TERM_BG = Color(0xFF05080A)
private val T_GREEN = Color(0xFF5FD36A)
private val T_CYAN = Color(0xFF22D3EE)
private val T_AMBER = Color(0xFFF0B429)
private val T_RED = Color(0xFFFF5470)
private val T_MUTED = Color(0xFF7E8C8A)
private val T_INK = Color(0xFFE9F0EE)
private val MONO = FontFamily.Monospace

private enum class TermMode(val label: String) { BOARD("BOARD"), APP("APP") }

/** One line in the terminal scrollback. */
private data class TermLine(val text: String, val tint: Color, val ts: Long)

/** An app-palette destination: a keyword the user types, the nav route it opens, a human label. */
private data class TermDest(val key: String, val route: String, val label: String)

// Curated command palette — the destinations worth jumping to by name.
private val DESTS = listOf(
    TermDest("fleet", "fleet", "Fleet / boards"),
    TermDest("dashboard", "dashboard", "Owner dashboard"),
    TermDest("signals", "signals", "Signals (RF scope)"),
    TermDest("live", "live", "Live console"),
    TermDest("wardrive", "wardrive", "Wardrive"),
    TermDest("wifi", "wifi", "Wi-Fi"),
    TermDest("bluetooth", "bluetooth", "Bluetooth"),
    TermDest("subghz", "subghz", "Sub-GHz"),
    TermDest("misc", "misc", "Misc / tools"),
    TermDest("sensors", "sensors", "Sensors & Sky"),
    TermDest("datacube", "datacube", "Data Cube"),
    TermDest("rogueap", "rogueap", "Rogue AP"),
    TermDest("orbitglobe", "orbitglobe", "Orbit Globe"),
    TermDest("skyconditions", "skyconditions", "Sky Conditions"),
    TermDest("lightmap", "lightmap", "Light Pollution map"),
    TermDest("airquality", "airquality", "Air Quality"),
    TermDest("spaceevents", "spaceevents", "Space Events"),
    TermDest("skyar", "skyar", "Sky AR"),
    TermDest("satellites", "satellites", "Satellites"),
    TermDest("vision", "vision", "Vision portals"),
    TermDest("globe", "globe", "Globe"),
    TermDest("aircraft", "aircraft", "Aircraft"),
    TermDest("weather", "weather", "Weather"),
    TermDest("alerts", "alerts", "Alerts"),
    TermDest("aurora", "aurora", "Aurora"),
    TermDest("feeds", "feeds", "Feeds"),
    TermDest("places", "places", "Places"),
    TermDest("drives", "drives", "Drives"),
    TermDest("stats", "stats", "Stats"),
    TermDest("games", "games", "Games"),
    TermDest("remote", "remote", "Universal remote"),
    TermDest("meshchat", "meshchat", "Mesh chat"),
    TermDest("squawk", "squawk", "Flock / Squawk"),
    TermDest("profile", "profile", "Profile"),
    TermDest("settings", "settings", "Settings"),
    TermDest("rarebirds", "rarebirds", "Field guide"),
)

// Common board console verbs offered as quick chips (free-text is still allowed).
private val BOARD_CHIPS = listOf("status", "scanap", "scansta", "scanall", "wardrive", "stopscan", "dev:list", "caw", "reboot")
private val APP_CHIPS = listOf("help", "dashboard", "signals", "orbitglobe", "vision", "wardrive", "settings", "clear")

private fun logTint(type: BiscuitMessage.Type): Color = when (type) {
    BiscuitMessage.Type.DATA -> T_GREEN
    BiscuitMessage.Type.RSP -> T_CYAN
    BiscuitMessage.Type.STATUS -> T_AMBER
    BiscuitMessage.Type.TIME -> T_MUTED
    BiscuitMessage.Type.ERROR -> T_RED
    BiscuitMessage.Type.CMD -> T_INK
    BiscuitMessage.Type.UNKNOWN -> T_MUTED
}

/**
 * Terminal — the app's command surface, replacing the old Live-Scope top-bar shortcut. Two modes:
 *
 *  • BOARD — a serial-style console to the mesh. Type a raw command (e.g. `scanap`, `wardrive`,
 *    `attack:type=deauth`); it is framed as `CMD:` and sent to the whole fleet, or to one board when
 *    a board chip is selected. The live board log streams back in-line, colour-coded by message type.
 *  • APP — a command palette for the app itself. `help` lists it; a destination keyword (or `go
 *    <name>` / `open <name>`) jumps there; `clear` wipes the scrollback; `version` prints the build.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(vm: MainViewModel, onNavigate: (String) -> Unit, onBack: () -> Unit) {
    var mode by remember { mutableStateOf(TermMode.BOARD) }
    var input by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<String?>(null) } // null == ALL (broadcast)
    val history = remember { mutableStateListOf<TermLine>() }

    val logFlow = remember(vm) { vm.log ?: MutableStateFlow(emptyList<LogEntry>()) }
    val devFlow = remember(vm) { vm.devices ?: MutableStateFlow(emptyMap<String, BiscuitDevice>()) }
    val entries by logFlow.collectAsState()
    val devMap by devFlow.collectAsState()
    val liveBoards = devMap.values.filter { it.isLive }

    // One-time banner.
    LaunchedEffect(Unit) {
        if (history.isEmpty()) {
            history += TermLine("FlockedUp terminal · v${com.cak3d.biscuitmesh.BuildConfig.VERSION_NAME}", T_CYAN, 0)
            history += TermLine("BOARD = console to the mesh · APP = jump anywhere · type 'help'", T_MUTED, 1)
        }
    }

    fun push(text: String, tint: Color) { history += TermLine(text, tint, System.currentTimeMillis()) }

    fun runApp(raw: String) {
        val c = raw.trim()
        if (c.isEmpty()) return
        push("> $c", T_CYAN)
        val lower = c.lowercase()
        when {
            lower == "help" || lower == "?" -> {
                push("app commands:", T_AMBER)
                push("  help · clear · version · board · go <name> · open <name>", T_MUTED)
                push("destinations (type the name):", T_AMBER)
                DESTS.chunked(3).forEach { row ->
                    push("  " + row.joinToString("  ·  ") { it.key }, T_MUTED)
                }
            }
            lower == "clear" || lower == "cls" -> history.clear()
            lower == "version" || lower == "ver" || lower == "about" ->
                push("FlockedUp v${com.cak3d.biscuitmesh.BuildConfig.VERSION_NAME}", T_GREEN)
            lower == "board" -> { mode = TermMode.BOARD; push("switched to BOARD console", T_GREEN) }
            else -> {
                val q = lower.removePrefix("go ").removePrefix("open ").trim()
                val dest = DESTS.firstOrNull { it.key == q }
                    ?: DESTS.firstOrNull { it.key.startsWith(q) && q.isNotEmpty() }
                    ?: DESTS.firstOrNull { it.label.lowercase().contains(q) && q.isNotEmpty() }
                if (dest != null) {
                    push("→ opening ${dest.label}", T_GREEN)
                    onNavigate(dest.route)
                } else {
                    push("unknown command '$c' — type 'help'", T_RED)
                }
            }
        }
    }

    fun runBoard(raw: String) {
        val c = raw.trim()
        if (c.isEmpty()) return
        val dest = if (target == null) "ALL" else vm.nameFor(target!!)
        push("[$dest] › $c", T_GREEN)
        val t = target
        if (t == null) vm.broadcast(c) else vm.send(t, c)
    }

    fun submit() {
        val line = input
        input = ""
        if (mode == TermMode.APP) runApp(line) else runBoard(line)
    }

    // Combined scrollback: local history, plus (in BOARD mode) the live board log filtered by target.
    val combined = remember(history.toList(), entries, mode, target) {
        if (mode == TermMode.APP) {
            history.toList()
        } else {
            val boardLines = entries
                .filter { target == null || it.address == target }
                .map { e ->
                    val m = e.message
                    val body = if (m.payload.isBlank()) "${m.type}:${m.subtype}" else "${m.type}:${m.subtype} ${m.payload}"
                    TermLine("${vm.nameFor(e.address)}  $body", logTint(m.type), e.timeMs)
                }
            (history.toList() + boardLines).sortedBy { it.ts }.takeLast(400)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(combined.size) {
        if (combined.isNotEmpty()) listState.animateScrollToItem(combined.lastIndex)
    }

    Scaffold(
        containerColor = TERM_BG,
        topBar = {
            TopAppBar(
                title = { Text("Terminal", fontFamily = MONO) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Terminal",
                        "Two modes. BOARD is a serial-style console to the mesh — type a raw command " +
                            "like 'scanap', 'wardrive' or 'attack:type=deauth' and it is framed as CMD: and " +
                            "sent to the whole fleet (or one board when you pick a board chip); the live board " +
                            "log streams back, colour-coded. APP is a command palette for the app itself: " +
                            "type 'help', a destination name, or 'go <name>' to jump anywhere; 'clear' and " +
                            "'version' do what they say.",
                    )
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // Signature: a crow forged from a moving light-trail.
            CrowTrailHeader(Modifier.fillMaxWidth().height(118.dp))
            Text(
                "FLOCKEDUP ⟐ TERMINAL",
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                color = T_GREEN.copy(alpha = 0.8f), fontFamily = MONO, fontSize = 11.sp,
                letterSpacing = 3.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            // Mode toggle.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TermMode.entries.forEach { m ->
                    val on = m == mode
                    Surface(
                        color = if (on) T_GREEN.copy(alpha = 0.18f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                            .border(1.dp, (if (on) T_GREEN else T_MUTED).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { mode = m },
                    ) {
                        Text(
                            m.label,
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            color = if (on) T_GREEN else T_MUTED,
                            fontFamily = MONO, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }

            // BOARD target selector.
            if (mode == TermMode.BOARD) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TargetChip("ALL", target == null, T_AMBER) { target = null }
                    liveBoards.forEach { d ->
                        TargetChip(vm.nameFor(d.address), target == d.address, T_CYAN) { target = d.address }
                    }
                    if (liveBoards.isEmpty()) {
                        Text("no board connected — commands queue to the fleet", color = T_MUTED,
                            fontFamily = MONO, fontSize = 10.sp)
                    }
                }
            }

            // Scrollback.
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                items(combined) { ln ->
                    Text(ln.text, color = ln.tint, fontFamily = MONO, fontSize = 12.sp)
                }
            }

            // Quick chips.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val chips = if (mode == TermMode.BOARD) BOARD_CHIPS else APP_CHIPS
                chips.forEach { chip ->
                    Surface(
                        color = T_INK.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable {
                            if (mode == TermMode.BOARD) runBoard(chip) else runApp(chip)
                        },
                    ) {
                        Text(chip, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = T_INK.copy(alpha = 0.85f), fontFamily = MONO, fontSize = 11.sp)
                    }
                }
            }

            // Input line.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MONO),
                    placeholder = {
                        Text(
                            if (mode == TermMode.BOARD) "command to the mesh…" else "app command or destination…",
                            fontFamily = MONO, fontSize = 13.sp, color = T_MUTED,
                        )
                    },
                    leadingIcon = {
                        Text(if (mode == TermMode.BOARD) "›" else ">", color = T_GREEN, fontFamily = MONO,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }, onDone = { submit() }),
                )
                Surface(
                    color = T_GREEN.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(52.dp).clickable { submit() },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = T_GREEN,
                            modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetChip(name: String, on: Boolean, tint: Color, onClick: () -> Unit) {
    Surface(
        color = if (on) tint.copy(alpha = 0.18f) else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .border(1.dp, (if (on) tint else T_MUTED).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        Text(name.uppercase(), Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = if (on) tint else T_MUTED, fontFamily = MONO, fontSize = 10.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}
