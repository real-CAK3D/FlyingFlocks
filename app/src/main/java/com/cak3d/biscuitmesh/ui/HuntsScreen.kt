package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.hunts.HUNTS
import com.cak3d.biscuitmesh.hunts.Hunt
import com.cak3d.biscuitmesh.hunts.HuntInputs
import com.cak3d.biscuitmesh.progress.ProgressTrack
import com.cak3d.biscuitmesh.ui.theme.appCardShape
import kotlinx.coroutines.flow.MutableStateFlow

private fun tierColor(t: Hunt.Tier) = when (t) {
    Hunt.Tier.BRONZE -> Color(0xFFCD7F32)
    Hunt.Tier.SILVER -> Color(0xFFC0C7D0)
    Hunt.Tier.GOLD -> Color(0xFFFFD166)
    Hunt.Tier.LEGEND -> Color(0xFF9B6BFF)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuntsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    val drives by vm.drivesState.collectAsStateWithLifecycle()
    val meshNodes by vm.meshNodes.collectAsStateWithLifecycle()
    val samplesFlow = remember0(vm)
    val samples by samplesFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    var selected by remember { mutableStateOf<Pair<Hunt, Int>?>(null) }

    fun count(t: ProgressTrack) = progress.tracks.firstOrNull { it.track == t }?.count ?: 0
    val inputs = HuntInputs(
        wifi24 = count(ProgressTrack.WIFI_24),
        wifi5 = count(ProgressTrack.WIFI_5),
        bt = count(ProgressTrack.BLUETOOTH),
        flock = count(ProgressTrack.FLOCK),
        speedCam = count(ProgressTrack.SPEED_CAM),
        redlight = count(ProgressTrack.REDLIGHT),
        totalKm = drives.sumOf { it.distanceM } / 1000.0,
        mesh = meshNodes.size,
        rf = samples.values.count { it.kind == com.cak3d.biscuitmesh.wardrive.SampleKind.RF },
        rank = progress.rank,
    )

    val scored = HUNTS.map { it to it.metric(inputs) }
    val maxed = scored.count { (h, c) -> h.maxed(c) }
    val tiersCleared = scored.sumOf { (h, c) -> h.levelAt(c) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Hunts") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton(
                        "Hunts",
                        "Ongoing challenges that track your catches over time — each hunt fills up as you " +
                            "find more of a given kind, clearing tiers as you go.",
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("head") {
                Card(Modifier.fillMaxWidth(), shape = appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("BOUNTY BOARD", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("$tiersCleared tiers cleared · $maxed / ${HUNTS.size} maxed",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Hunts tick up on their own as you drive and grind. Clear a target and it rolls up to the next — 5 → 10 → 15 — so there's always a next rung.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    }
                }
            }
            items(scored, key = { it.first.id }) { (h, c) -> HuntRow(h, c, onClick = { selected = h to c }) }
        }

        selected?.let { (h, c) ->
            AlertDialog(
                onDismissRequest = { selected = null },
                confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
                title = { Text("${h.emoji}  ${h.title}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Tier: ${h.tier.label}")
                        Text("Counting: ${h.unit}")
                        Text("Progress: $c ${h.unit}")
                        Text("Rungs cleared: ${h.levelAt(c)} / ${h.tiers.size}")
                        Text("Targets: ${h.tiers.joinToString(" → ")}")
                    }
                },
            )
        }
    }
}

@Composable
private fun remember0(vm: MainViewModel) = androidx.compose.runtime.remember(vm) {
    vm.wardriveSamples ?: MutableStateFlow(emptyMap())
}

@Composable
private fun HuntRow(h: Hunt, current: Int, onClick: () -> Unit) {
    val maxed = h.maxed(current)
    val level = h.levelAt(current)
    val target = h.targetAt(current)
    val frac = h.progressAt(current)
    val tc = tierColor(h.tier)
    Card(Modifier.fillMaxWidth().clickable { onClick() }, shape = appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(h.emoji, fontSize = 26.sp)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(h.title, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = if (maxed) tc else MaterialTheme.colorScheme.onSurface)
                        if (level > 0) Text("Lv $level", style = MaterialTheme.typography.labelSmall,
                            color = tc, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(h.tier.label.uppercase(), style = MaterialTheme.typography.labelSmall,
                            color = tc.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
                    }
                    Text(
                        if (maxed) "Maxed — all ${h.tiers.size} rungs cleared"
                        else "Next: ${target} ${h.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
                Text(if (maxed) "★" else "${(frac * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, color = if (maxed) tc else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))) {
                Box(Modifier.fillMaxWidth(frac).height(7.dp).clip(RoundedCornerShape(4.dp)).background(tc))
            }
            Text(
                if (maxed) "$current ${h.unit}" else "$current / $target ${h.unit}",
                style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}
