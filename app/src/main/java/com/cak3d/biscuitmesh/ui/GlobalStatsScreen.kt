package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel

/**
 * Global Stats — the whole flock at a glance: how many wings there are, how much the flock
 * has caught all-time, and the leaderboard. (Your own numbers live in Profile.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalStatsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val online by vm.crew.online.collectAsStateWithLifecycle()
    val live by vm.crew.live.collectAsStateWithLifecycle()
    val totals by vm.crew.totals.collectAsStateWithLifecycle()
    val board by vm.crew.board.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.activateCrew() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Global Stats") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("flock") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("THE FLOCK", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
                        GRow("Wings in the flock", "${totals?.wings ?: 0}")
                        GRow("Live right now", "$live")
                        GRow("Online", if (online) "● yes" else "○ no")
                    }
                }
            }
            item("alltime") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ALL-TIME · EVERYONE", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
                        val t = totals
                        GRow("Wi-Fi networks", "${t?.wifi ?: 0}")
                        GRow("Bluetooth devices", "${t?.bt ?: 0}")
                        GRow("Cameras", "${t?.cameras ?: 0}")
                        GRow("Drives", "${t?.drives ?: 0}")
                        GRow("Distance", "%.0f km".format(t?.km ?: 0.0))
                    }
                }
            }
            board?.let { b ->
                item("lbhead") {
                    Text("LEADERBOARD · ${b.total} wings", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp))
                }
                items(b.top.size) { i ->
                    val r = b.top[i]
                    Card(colors = CardDefaults.cardColors(
                        containerColor = if (r.me) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("#${r.rank}", style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(r.handle.ifBlank { "wing" }, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (r.me) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            Text("${r.score}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                        }
                    }
                }
                if (b.me != null && b.top.none { it.me }) {
                    item("me") {
                        val r = b.me!!
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("#${r.rank}", style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("${r.handle.ifBlank { "you" }} (you)", style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("${r.score}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
