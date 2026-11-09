package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.model.ConnectionState
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import com.cak3d.biscuitmesh.tools.ToolCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunToolScreen(vm: MainViewModel, toolKey: String?, onBack: () -> Unit) {
    val tool = toolKey?.let { ToolCatalog.byKey(it) }
    val devicesFlow = vm.devices
    val logFlow = vm.log
    if (tool == null || devicesFlow == null || logFlow == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("Tool") }, navigationIcon = { BackButton(onBack) }, actions = { HelpButton("Tool", "This tool isn't available right now — usually because no board is connected. Go back and connect a board, then try again.") }) }) { p ->
            Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.Center) {
                Text("Tool unavailable.")
            }
        }
        return
    }

    val devices by devicesFlow.collectAsStateWithLifecycle()
    val log by logFlow.collectAsStateWithLifecycle()
    val authorized by vm.authorizedUse.collectAsStateWithLifecycle()
    val readyCount = devices.values.count { it.state == ConnectionState.READY }

    var command by remember { mutableStateOf(tool.command) }
    var startMs by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(false) }
    var showGate by remember { mutableStateOf(false) }

    fun doStart() {
        startMs = System.currentTimeMillis()
        running = true
        vm.broadcast(command)
    }
    fun onStartClicked() {
        if (tool.offensive && !authorized) showGate = true else doStart()
    }
    fun doStop() {
        vm.stopAllBoards()
        running = false
    }

    val results = remember(log, startMs) {
        if (startMs == 0L) emptyList()
        else log.filter {
            it.timeMs >= startMs && it.message.type in RESULT_TYPES
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(results.size) {
        if (results.isNotEmpty()) listState.scrollToItem(results.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tool.title) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    HelpButton(
                        tool.title,
                        "Sends a command to your connected boards and shows their results live. Check the " +
                            "command, make sure a board is connected, then press Start; Stop halts it.",
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            Text(
                tool.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            val targetColor = if (readyCount == 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary
            Text(
                if (readyCount == 0) "No connected boards — connect one to run."
                else "Targets: $readyCount connected board${if (readyCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelLarge,
                color = targetColor,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )

            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                prefix = { Text("CMD:", fontFamily = FontFamily.Monospace) },
                label = { Text(if (tool.documented) "Command" else "Command (verify / edit)") },
            )

            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = { onStartClicked() },
                    enabled = readyCount > 0 && command.isNotBlank(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("  Start")
                }
                OutlinedButton(onClick = { doStop() }, enabled = running) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Text("  Stop")
                }
            }

            Text(
                if (running) "LIVE RESULTS" else "RESULTS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 10.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF07090C)),
            ) {
                if (results.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (startMs == 0L) "Press Start to run on the fleet."
                            else "Waiting for results…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(results.size) { i ->
                            val e = results[i]
                            ResultLine(vm.nameFor(e.address), e.message)
                        }
                    }
                }
            }
        }
    }

    if (showGate) {
        AuthorizedUseDialog(
            onAccept = {
                showGate = false
                vm.acceptAuthorizedUse()
                doStart()
            },
            onCancel = { showGate = false },
        )
    }
}

private val RESULT_TYPES = setOf(
    BiscuitMessage.Type.DATA,
    BiscuitMessage.Type.RSP,
    BiscuitMessage.Type.STATUS,
    BiscuitMessage.Type.ERROR,
)

@Composable
private fun ResultLine(board: String, msg: BiscuitMessage) {
    val color = when (msg.type) {
        BiscuitMessage.Type.ERROR -> MaterialTheme.colorScheme.error
        BiscuitMessage.Type.STATUS -> MaterialTheme.colorScheme.secondary
        BiscuitMessage.Type.RSP -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        "[$board] ${msg.raw.trimEnd('\n', '\r')}",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = color,
    )
}

@Composable
private fun AuthorizedUseDialog(onAccept: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Authorized use only") },
        text = {
            Text(
                "This runs an active radio operation on the connected boards. Use it only " +
                    "against networks and devices you own or are explicitly authorized to test. " +
                    "You are responsible for complying with all applicable laws.",
            )
        },
        confirmButton = { Button(onClick = onAccept) { Text("I'm authorized") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}
