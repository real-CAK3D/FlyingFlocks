package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.crew.CrewManager

/**
 * The global Feathers leaderboard — the whole flock ranked by Feathers earned. Reads from the
 * online layer (theGarden); your own row is highlighted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeathersBoardScreen(vm: MainViewModel, onBack: () -> Unit) {
    val rows by vm.feathersBoard.collectAsStateWithLifecycle()
    val mine by vm.feathers.collectAsStateWithLifecycle()
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadFeathersBoard(); kotlinx.coroutines.delay(1200); loaded = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feathers Leaderboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    Text("🪶 $mine", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 14.dp)
                            .help("Your Feathers", "Your current Feathers balance — the app's points, earned by driving and catching. Spend them in the Store. With Flock enabled your balance syncs online and ranks you on this board."))
                    HelpButton("Feathers Leaderboard",
                        "The whole flock ranked by Feathers earned. Your balance syncs to the server while you're " +
                            "online (Flock enabled); your own row is highlighted.")
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                rows.isEmpty() && !loaded -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
                rows.isEmpty() -> item {
                    Text("No leaderboard yet. Enable Flock in Settings and earn some Feathers — scores sync when " +
                        "you're online. (If you just set this up, the server function may still need to be added.)",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp))
                }
                else -> items(rows) { r -> BoardRow(r) }
            }
        }
    }
}

@Composable
private fun BoardRow(r: CrewManager.FeatherRank) {
    val highlight = r.me
    Card(Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val medal = when (r.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#${r.rank}" }
            Box(Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center) {
                Text(medal, fontSize = if (r.rank <= 3) 18.sp else 12.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold)
            }
            Text(r.handle + if (r.me) " (you)" else "", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            Text("🪶 ${r.feathers}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}
