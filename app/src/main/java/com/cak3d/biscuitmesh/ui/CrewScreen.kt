package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.cak3d.biscuitmesh.crew.CrewMember
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CREW = Color(0xFF34D399)
private val ACCENT = Color(0xFF22D3EE)

/**
 * Crew — the online layer. An anonymous identity, a one-time share code to let someone
 * track you, a live operator count, and the live positions of the people you track.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewScreen(vm: MainViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val share by vm.crewShare.collectAsStateWithLifecycle(initialValue = false)

    var lat by remember { mutableStateOf<Double?>(null) }
    var lon by remember { mutableStateOf<Double?>(null) }
    var heading by remember { mutableStateOf<Float?>(null) }
    var speed by remember { mutableStateOf<Float?>(null) }

    var live by remember { mutableStateOf(0) }
    var tracked by remember { mutableStateOf<List<CrewMember>>(emptyList()) }
    var myCode by remember { mutableStateOf<String?>(null) }
    var codeField by remember { mutableStateOf("") }
    var claimMsg by remember { mutableStateOf<String?>(null) }
    var online by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { vm.locationUpdates().collect { lat = it.latitude; lon = it.longitude
            heading = if (it.hasBearing()) it.bearing else null; speed = if (it.hasSpeed()) it.speed else null } }
    }
    // Heartbeat + publish + poll loop.
    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                vm.crew.ensure(); online = true; error = null
                vm.crew.touch()
                val la = lat; val lo = lon
                if (share && la != null && lo != null) vm.crew.publishLocation(la, lo, heading, speed)
                live = vm.crew.liveCount()
                tracked = vm.crew.tracked()
            }.onFailure { online = false; error = it.message }
            delay(5000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crew") },
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
            item("live") {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (online) "● ONLINE" else "○ CONNECTING…", style = MaterialTheme.typography.labelMedium,
                            color = if (online) CREW else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("$live operator${if (live == 1) "" else "s"} online now", style = MaterialTheme.typography.titleMedium)
                        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item("mycode") {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("MY TRACK CODE", style = MaterialTheme.typography.labelMedium, color = ACCENT,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("Give this to someone so you can see them on your map. It works once, then make a new one.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        myCode?.let {
                            Text(it, style = MaterialTheme.typography.displaySmall, color = ACCENT,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Button(onClick = { scope.launch { runCatching { myCode = vm.crew.newCode() } } },
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (myCode == null) "Generate my code" else "New code")
                        }
                    }
                }
            }
            item("enter") {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("TRACK SOMEONE", style = MaterialTheme.typography.labelMedium, color = ACCENT,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("Enter a code someone gave you — they'll become visible on your map.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        OutlinedTextField(value = codeField, onValueChange = { codeField = it.uppercase() },
                            singleLine = true, label = { Text("Track code") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            scope.launch { claimMsg = vm.crew.claim(codeField)?.also { } ?: "Linked ✓"; if (claimMsg == "Linked ✓") codeField = "" }
                        }, enabled = codeField.length >= 4, modifier = Modifier.fillMaxWidth()) { Text("Track them") }
                        claimMsg?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium,
                                color = if (it.contains("✓")) CREW else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item("share") {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Share my live location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Only people who claimed your code can see it. Off = you're invisible.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        }
                        Switch(checked = share, onCheckedChange = { vm.setCrewShare(it) })
                    }
                }
            }
            if (tracked.isNotEmpty()) {
                item("tlabel") {
                    Text("TRACKING · ${tracked.size}", style = MaterialTheme.typography.labelMedium, color = CREW,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                items(tracked, key = { it.userId }) { m ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Operator ${m.userId.take(8)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("%.5f, %.5f".format(m.lat, m.lon), style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Text("updated ${m.updatedAt.take(19).replace('T', ' ')}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}
