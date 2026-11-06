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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.mesh.MeshLinkState
import com.cak3d.biscuitmesh.mesh.MeshMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshChatScreen(vm: MainViewModel, onBack: () -> Unit) {
    val link by vm.meshLink.collectAsStateWithLifecycle()
    val diag by vm.meshDiag.collectAsStateWithLifecycle()
    val messages by vm.meshMessages.collectAsStateWithLifecycle()
    val nodes by vm.meshNodes.collectAsStateWithLifecycle()
    var field by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Caw") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            Card(Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(when (link) {
                            MeshLinkState.CONNECTED -> "● Connected · ${nodes.size} node${if (nodes.size == 1) "" else "s"}"
                            MeshLinkState.CONNECTING -> "○ Connecting…"
                            MeshLinkState.SCANNING -> "○ Scanning for a node…"
                            else -> "○ Not connected"
                        }, color = if (link == MeshLinkState.CONNECTED) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(
                            diag ?: "Broadcast text over your Meshtastic mesh — no internet needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = if (diag != null && link != MeshLinkState.CONNECTED) 0.85f else 0.6f,
                            ),
                        )
                    }
                    if (link == MeshLinkState.CONNECTED) TextButton(onClick = { vm.disconnectMesh() }) { Text("Disconnect") }
                    else TextButton(onClick = { vm.connectMesh() }) { Text("Connect") }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    Text("No messages yet. Connect to your node and say hello — it broadcasts to the whole mesh.",
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp)) {
                        items(messages, key = { it.timeMs.toString() + it.text.hashCode() }) { MsgBubble(it) }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = field, onValueChange = { field = it.take(200) }, singleLine = true,
                    label = { Text("Message") }, modifier = Modifier.weight(1f),
                    enabled = link == MeshLinkState.CONNECTED)
                IconButton(onClick = { if (field.isNotBlank()) { vm.sendMeshText(field); field = "" } },
                    enabled = link == MeshLinkState.CONNECTED && field.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun MsgBubble(m: MeshMessage) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.US) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (m.mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(m.text, style = MaterialTheme.typography.bodyMedium)
                Text("${if (m.mine) "you" else "mesh"} · ${fmt.format(Date(m.timeMs))}",
                    style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}
