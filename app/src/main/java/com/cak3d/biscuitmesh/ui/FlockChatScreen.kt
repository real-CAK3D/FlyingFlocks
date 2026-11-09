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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.crew.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Squawk — the flock's open chat room, carried over the online (Supabase) layer, no board or
 * Meshtastic node needed. Anyone running Flying Flocks sees and can post to the same room.
 * (For off-grid chat over a Meshtastic mesh, see Caw.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlockChatScreen(vm: MainViewModel, onBack: () -> Unit) {
    val online by vm.crew.online.collectAsStateWithLifecycle()
    val live by vm.crew.live.collectAsStateWithLifecycle()
    val messages by vm.crew.messages.collectAsStateWithLifecycle()
    val handle by vm.flockHandle.collectAsStateWithLifecycle(initialValue = "")
    var field by remember { mutableStateOf("") }
    var nameField by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Start the online layer when the room opens, so messages poll even if flock sharing is off.
    LaunchedEffect(Unit) { vm.activateCrew() }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    val named = handle.isNotBlank()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Squawk") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Squawk",
                        "The flock's open online chat room — anyone running the app sees and can post " +
                            "to the same room, no board or mesh node needed. For off-grid mesh chat, use Caw.",
                    )
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
                        Text(
                            if (online) "● Online · $live wing${if (live == 1) "" else "s"} here" else "○ Connecting…",
                            color = if (online) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (named) "Posting as $handle · call out to the whole flock" else "Call out to the whole flock — online, no hardware needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    Text(
                        "No calls yet. Say hello — every wing running Flying Flocks sees this room.",
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp)) {
                        items(messages, key = { it.id }) { ChatBubble(it) }
                    }
                }
            }

            if (!named) {
                // Pick a handle first, so posts are attributed.
                Card(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pick a name so the flock knows who's calling.",
                            style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = nameField, onValueChange = { nameField = it.take(24) },
                                singleLine = true, label = { Text("Your handle") }, modifier = Modifier.weight(1f))
                            Button(onClick = { if (nameField.isNotBlank()) vm.setFlockHandle(nameField.trim()) },
                                enabled = nameField.isNotBlank()) { Text("Save") }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = field, onValueChange = { field = it.take(500) }, singleLine = true,
                        label = { Text("Message") }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (field.isNotBlank()) { vm.sendFlockChat(field); field = "" } },
                        enabled = field.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(m: ChatMessage) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.US) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (m.mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(m.body, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${if (m.mine) "you" else (m.handle ?: "wing")} · ${fmt.format(Date(m.ts))}",
                    style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}
