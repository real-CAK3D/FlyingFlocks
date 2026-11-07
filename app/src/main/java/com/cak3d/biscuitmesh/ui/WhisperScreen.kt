package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.crew.DmMessage
import com.cak3d.biscuitmesh.crew.WingContact
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Whisper — the private side of the flock. Pick a wing and talk 1:1; only the two of you
 * can read it (enforced by row-level security). Use it to swap Band codes or Meshtastic
 * details so you can then track each other on the map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhisperListScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (String, String) -> Unit) {
    val handle by vm.flockHandle.collectAsStateWithLifecycle(initialValue = "")
    var wings by remember { mutableStateOf<List<WingContact>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            loading = true
            wings = runCatching { vm.crew.wings() }.getOrDefault(emptyList())
            loading = false
        }
    }
    LaunchedEffect(Unit) { vm.activateCrew(); refresh() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Whisper") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("hdr") {
                Text(
                    if (handle.isBlank())
                        "Set a handle in Flock first — it's how other wings find you here."
                    else "Private 1:1 whispers. Only you and the other wing can read them — good for swapping Band codes and Meshtastic details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (!loading && wings.isEmpty()) {
                item("empty") {
                    Text("No other wings with a handle yet. When someone sets a handle, they show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            items(wings, key = { it.userId }) { w ->
                Card(
                    onClick = { onOpen(w.userId, w.handle) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(w.handle.take(1).uppercase(), fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text(w.handle, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhisperThreadScreen(vm: MainViewModel, otherId: String, otherHandle: String, onBack: () -> Unit) {
    var messages by remember { mutableStateOf<List<DmMessage>>(emptyList()) }
    var field by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Poll this thread while it's open.
    LaunchedEffect(otherId) {
        vm.activateCrew()
        while (true) {
            messages = runCatching { vm.crew.dmThread(otherId) }.getOrDefault(messages)
            delay(5000)
        }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(otherHandle.ifBlank { "Whisper" }) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    Text("No whispers yet. Say hi — only $otherHandle can see this.",
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)) {
                        items(messages, key = { it.id }) { DmBubble(it) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = field, onValueChange = { field = it.take(1000) }, singleLine = true,
                    label = { Text("Message") }, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    if (field.isNotBlank()) {
                        val t = field; field = ""
                        vm.sendWhisper(otherId, t)
                        scope.launch { delay(400); messages = runCatching { vm.crew.dmThread(otherId) }.getOrDefault(messages) }
                    }
                }, enabled = field.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun DmBubble(m: DmMessage) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.US) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (m.mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(m.body, style = MaterialTheme.typography.bodyMedium)
                Text(fmt.format(Date(m.ts)), style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}
