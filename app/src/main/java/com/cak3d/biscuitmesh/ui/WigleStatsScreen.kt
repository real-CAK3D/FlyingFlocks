package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.cak3d.biscuitmesh.geo.WigleClient
import com.cak3d.biscuitmesh.geo.WigleStats

private val WIGLE = Color(0xFF34D399)

/**
 * Your WiGLE account stats — WiGLE is where Wardrive Go uploads, so this is the "stats"
 * side of your wardriving. Paste your WiGLE "Encoded for use" credential once; it's stored
 * locally and used only to call the public WiGLE API.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WigleStatsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val auth by vm.wigleAuth.collectAsStateWithLifecycle(initialValue = "")
    val client = remember { WigleClient() }
    var stats by remember { mutableStateOf<WigleStats?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf("") }

    LaunchedEffect(auth) {
        if (auth.isNotBlank()) {
            loading = true; error = null
            client.userStats(auth)
                .onSuccess { stats = it; loading = false }
                .onFailure { error = it.message; loading = false }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiGLE Stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (auth.isBlank() || editing) {
                Card(
                    Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("CONNECT WiGLE", style = MaterialTheme.typography.labelMedium, color = WIGLE,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("On wigle.net → Account → Show my token, copy the \"Encoded for use\" line and paste it here. It's stored only on this phone.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        OutlinedTextField(
                            value = field, onValueChange = { field = it }, singleLine = true,
                            label = { Text("WiGLE \"Encoded for use\"") }, modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { if (field.isNotBlank()) { vm.setWigleAuth(field); editing = false; field = "" } },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Save & load stats") }
                    }
                }
            } else {
                Card(
                    Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("WiGLE".uppercase(), style = MaterialTheme.typography.labelMedium, color = WIGLE,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                loading -> "Loading your WiGLE stats…"
                                error != null -> error!!
                                stats != null -> "Signed in as ${stats!!.user.ifBlank { "you" }}"
                                else -> "No stats yet"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                stats?.let { s ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("RANK", if (s.rank > 0) "#${s.rank}" else "—", WIGLE, Modifier.weight(1f))
                        StatTile("THIS MONTH", if (s.monthRank > 0) "#${s.monthRank}" else "—", WIGLE, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("WiFi", fmt(s.wifiGps), Color(0xFF22D3EE), Modifier.weight(1f))
                        StatTile("BT", fmt(s.btGps), Color(0xFF7C9CFF), Modifier.weight(1f))
                        StatTile("CELL", fmt(s.cellGps), Color(0xFFF59E0B), Modifier.weight(1f))
                    }
                }
                OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) { Text("Change WiGLE key") }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, accent: Color, modifier: Modifier) {
    Card(
        modifier, shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.Start) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
        }
    }
}

private fun fmt(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}
