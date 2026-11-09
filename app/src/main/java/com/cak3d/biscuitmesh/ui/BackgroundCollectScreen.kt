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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Background Collect — phone-only passive collection. Start it, lock the phone and pocket it;
 * Flying Flocks keeps catching Wi-Fi and Bluetooth with the screen off and pings you as the
 * count climbs. No board needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundCollectScreen(vm: MainViewModel, onBack: () -> Unit) {
    val stateFlow = remember(vm) {
        vm.wardriveState ?: MutableStateFlow(com.cak3d.biscuitmesh.wardrive.WardriveState())
    }
    val state by stateFlow.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val active = state.active

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Background Collect") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Background Collect",
                        "Phone-only passive collecting — start it, lock the phone and pocket it, and the " +
                            "app keeps catching Wi-Fi and Bluetooth with the screen off. No board needed.",
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("status") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (active) "● Collecting" else "○ Idle",
                            color = if (active) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Wi-Fi this session", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                            Text("${state.wifiCount}", fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Bluetooth this session", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                            Text("${state.btCount}", fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Rank", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                            Text("${progress.rank} · ${progress.rankName}", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item("btn") {
                if (active) {
                    Button(
                        onClick = { vm.stopBackgroundCollect() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null); Text("  Stop collecting")
                    }
                } else {
                    Button(onClick = { vm.startBackgroundCollect() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null); Text("  Start background collect")
                    }
                }
            }
            item("how") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("How it works", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
                        Text(
                            "Uses only your phone's Wi-Fi and Bluetooth — no board needed. Start it, then lock " +
                                "your phone and pocket it. It keeps a notification so Android won't kill it, and " +
                                "pings you an update every 50 signals and whenever you rank up. Everything it " +
                                "catches scores toward your rank and fills the map.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}
