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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.progress.ProgressTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenFlock: () -> Unit,
) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    val drives by vm.drivesState.collectAsStateWithLifecycle()
    val mph by vm.useMph.collectAsStateWithLifecycle()
    val handle by vm.flockHandle.collectAsStateWithLifecycle(initialValue = "")
    val online by vm.crew.online.collectAsStateWithLifecycle()
    val board by vm.crew.board.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.activateCrew() }

    val wifi = progress.radios.filter { it.track == ProgressTrack.WIFI_24 || it.track == ProgressTrack.WIFI_5 }.sumOf { it.count }
    val bt = progress.radios.firstOrNull { it.track == ProgressTrack.BLUETOOTH }?.count ?: 0
    val totalMeters = drives.sumOf { it.distanceM }
    val totalMs = drives.sumOf { it.durationMs }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Profile",
                        "Your own totals in one place — devices caught, distance covered and rank — plus " +
                            "your flock handle. Jump from here to your full Stats or your Flock.",
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Identity + rank
            item("id") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            val avatarGlyph by vm.equippedAvatar.collectAsStateWithLifecycle()
                            val overlayId by vm.equippedOverlay.collectAsStateWithLifecycle()
                            AvatarRing(avatarGlyph.ifBlank { "🐦‍⬛" }, overlayId, 64.dp)
                            Column(Modifier.weight(1f)) {
                                Text(handle.ifBlank { "Unnamed wing" },
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                val title by vm.equippedTitle.collectAsStateWithLifecycle()
                                val flock by vm.flockName.collectAsStateWithLifecycle()
                                val premium by vm.premium.collectAsStateWithLifecycle()
                                if (premium) Text("✦ PREMIUM", style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFFFFD166), fontWeight = FontWeight.Bold)
                                if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text("Rank ${progress.rank} · ${progress.rankName}" + if (flock.isNotBlank()) " · $flock" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { progress.rankProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                if (progress.xpToNext > 0) "${progress.xpToNext} XP to rank ${progress.rank + 1}"
                                else "Max rank for now — keep grinding",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        if (handle.isBlank()) {
                            Text("Set a handle in Flock so the leaderboard and chat show your name.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                        }
                    }
                }
            }

            // Identity — rename flock / username (spends Feathers)
            item("identity") { IdentityCard(vm) }

            // Finds
            item("finds") {
                SectionCard("FINDS") {
                    InfoRow("Wi-Fi networks", "$wifi")
                    InfoRow("Bluetooth devices", "$bt")
                    InfoRow("Cameras", "${progress.cameraFinds}")
                    InfoRow("Rare birds captured", "${progress.bonusLevels}")
                    InfoRow("Total finds", "${progress.totalFinds}")
                }
            }

            // Driving
            item("drive") {
                SectionCard("DRIVING") {
                    InfoRow("Distance driven", fmtDist(totalMeters, mph))
                    InfoRow("Time driven", fmtDur(totalMs))
                    InfoRow("Drives logged", "${drives.size}")
                }
            }

            // Online standing
            item("online") {
                SectionCard("FLOCK (ONLINE)") {
                    InfoRow("Status", if (online) "● Online" else "○ Offline")
                    board?.me?.let { me ->
                        InfoRow("Leaderboard", "#${me.rank} of ${board!!.total}")
                    } ?: InfoRow("Leaderboard", "—")
                    OutlinedButton(onClick = onOpenFlock, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("Flock, bands & leaderboard")
                    }
                }
            }

            item("more") {
                OutlinedButton(onClick = onOpenStats, modifier = Modifier.fillMaxWidth()) {
                    Text("Full stats & data export")
                }
            }
        }
    }
}

@Composable
private fun IdentityCard(vm: MainViewModel) {
    val flock by vm.flockName.collectAsStateWithLifecycle()
    val handle by vm.flockHandle.collectAsStateWithLifecycle(initialValue = "")
    val feathers by vm.feathers.collectAsStateWithLifecycle()
    val cost = com.cak3d.biscuitmesh.data.StoreCatalog.RENAME_COST
    var dialog by remember { mutableStateOf<String?>(null) } // "flock" | "flock_free" | "user"
    SectionCard("IDENTITY") {
        InfoRow("Flock name", flock.ifBlank { "— not set —" })
        if (flock.isBlank())
            FilledTonalButton(onClick = { dialog = "flock_free" }, modifier = Modifier.fillMaxWidth()) {
                Text("Name your flock (free)")
            }
        else
            OutlinedButton(onClick = { dialog = "flock" }, enabled = feathers >= cost, modifier = Modifier.fillMaxWidth()) {
                Text("Rename flock · 🪶 $cost")
            }
        InfoRow("Username", handle.ifBlank { "— not set —" })
        OutlinedButton(onClick = { dialog = "user" }, enabled = feathers >= cost, modifier = Modifier.fillMaxWidth()) {
            Text("Change username · 🪶 $cost")
        }
    }
    dialog?.let { target ->
        var text by remember { mutableStateOf(if (target == "user") handle else flock) }
        val free = target == "flock_free"
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (target == "user") "Change username" else if (free) "Name your flock" else "Rename flock") },
            text = {
                Column {
                    OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true,
                        label = { Text("New name") })
                    if (!free) Text("Costs 🪶 $cost Feathers.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                Button(enabled = text.isNotBlank(), onClick = {
                    when (target) {
                        "user" -> vm.renameUsername(text.trim())
                        "flock" -> vm.renameFlock(text.trim())
                        else -> vm.setFlockNameFree(text.trim())
                    }
                    dialog = null
                }) { Text(if (free) "Set" else "Confirm · 🪶 $cost") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary, fontFamily = FontFamily.Monospace)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun fmtDist(meters: Double, mph: Boolean): String =
    if (mph) "%.1f mi".format(meters / 1609.34) else "%.1f km".format(meters / 1000.0)

private fun fmtDur(ms: Long): String {
    val totalMin = ms / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
