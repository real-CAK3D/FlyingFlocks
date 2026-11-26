package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.ui.theme.appCardShape

private val Amber = Color(0xFFF59E0B)
private val Green = Color(0xFF34D399)
private val Cyan = Color(0xFF22D3EE)
private val Red = Color(0xFFFF5470)

/** A Flipper-style universal TV remote. Every button blasts common-brand IR codes
 *  (Samsung / LG / Sony) out of the connected node's IR LED via CMD:irtv / CMD:tvbgone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(vm: MainViewModel, onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var last by remember { mutableStateOf("") }

    fun fire(label: String, action: () -> Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        action()
        last = label
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Universal Remote") },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Universal Remote",
                        "Each button fires the matching power/volume/channel code for the most common TV " +
                            "brands (Samsung, LG, Sony) out of the node's IR LED — point the T-Embed at the TV. " +
                            "If nothing responds, hit the TV-B-Gone sweep, which tries every power code in turn.",
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "POINT THE NODE'S IR LED AT THE TV",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.help("Universal remote", "A Flipper-style TV remote. Each button blasts common-brand IR codes (Samsung/LG/Sony) out of the connected node's IR LED — so aim the node at the TV. Power also fires a TV-B-Gone sweep. Needs a node with an IR LED connected."),
            )

            // Power — the big one
            RoundKey(Icons.Default.PowerSettingsNew, "Power", Red, 92.dp) { fire("Power") { vm.irTv("power") } }

            // Volume column | Mute | Channel column
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillColumn("VOL", Cyan,
                    up = { fire("Vol +") { vm.irTv("volup") } },
                    down = { fire("Vol −") { vm.irTv("voldn") } })
                RoundKey(Icons.AutoMirrored.Filled.VolumeOff, "Mute", Amber, 68.dp) { fire("Mute") { vm.irTv("mute") } }
                PillColumn("CH", Green,
                    up = { fire("Ch +") { vm.irTv("chup") } },
                    down = { fire("Ch −") { vm.irTv("chdn") } })
            }

            // Source
            WideKey(Icons.Default.Input, "Source / Input", Cyan) { fire("Source") { vm.irTv("source") } }

            // Channel number pad — each digit fires the matching universal IR code
            Column(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("CHANNEL", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                for (r in 0 until 3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 1..3) {
                            val d = r * 3 + c
                            DigitKey(d.toString(), Modifier.weight(1f)) { fire("Ch $d") { vm.irTv(d.toString()) } }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    DigitKey("0", Modifier.weight(1f)) { fire("Ch 0") { vm.irTv("0") } }
                    Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(2.dp))

            // TV-B-Gone sweep
            Card(
                Modifier.fillMaxWidth().clickable { fire("TV-B-Gone sweep") { vm.tvbGone() } },
                shape = appCardShape(),
                colors = CardDefaults.cardColors(containerColor = Red.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, Red.copy(alpha = 0.5f)),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = Red, modifier = Modifier.size(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text("TV-B-Gone sweep", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, color = Red)
                        Text("Fires every common power code in turn — turns off most TVs in range",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    }
                }
            }

            Text(
                if (last.isBlank()) "Ready." else "Sent: $last",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (last.isBlank()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else Green,
                modifier = Modifier.alpha(0.9f),
            )
        }
    }
}

@Composable
private fun RoundKey(icon: ImageVector, label: String, tint: Color, diam: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(diam).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                Modifier.size(diam),
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.14f)),
                border = BorderStroke(1.5.dp, tint.copy(alpha = 0.6f)),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(diam * 0.42f))
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

@Composable
private fun PillColumn(label: String, tint: Color, up: () -> Unit, down: () -> Unit) {
    Card(
        shape = appCardShape(),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = up, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Add, contentDescription = "$label up", tint = tint, modifier = Modifier.size(28.dp))
            }
            Text(label, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, color = tint)
            IconButton(onClick = down, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Remove, contentDescription = "$label down", tint = tint, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun DigitKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier.height(52.dp).clickable(onClick = onClick),
        shape = appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Green.copy(alpha = 0.3f)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun WideKey(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = appCardShape(),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.4f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = tint)
        }
    }
}
