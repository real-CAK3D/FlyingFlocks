package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.data.Stamp
import com.cak3d.biscuitmesh.data.StampKind

/**
 * The passport — every collectible you've stamped, filtered by family. Tap the chips to switch
 * between Places, Species, Sky catches and Events. New stamps are added automatically as you drive
 * and catch, with the little animation when each lands.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassportScreen(vm: MainViewModel, onBack: () -> Unit) {
    val stamps by vm.stamps.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<StampKind?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Passport") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    HelpButton("Passport",
                        "Your collection of stamps — places you've driven into, rare device species you've caught, " +
                            "sky catches, and special events. They're added automatically; a stamp animation plays " +
                            "the first time each one lands.")
                },
            )
        },
    ) { pad ->
        val shown = stamps.filter { filter == null || it.kind == filter }.sortedByDescending { it.ms }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("${stamps.size} stamp${if (stamps.size == 1) "" else "s"} collected",
                    color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = filter == null, onClick = { filter = null },
                        label = { Text("All ${stamps.size}") })
                    StampKind.entries.forEach { k ->
                        val n = stamps.count { it.kind == k }
                        FilterChip(selected = filter == k, onClick = { filter = k },
                            label = { Text("${k.glyph} $n") })
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text("No stamps here yet. Drive into a new town, catch a rare device, or log a sky catch to fill your passport.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp))
                }
            } else {
                items2(shown) { s -> StampCard(s) }
            }
        }
    }
}

@Composable
private fun StampCard(s: Stamp) {
    Card(Modifier.fillMaxWidth(), shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(s.kind.glyph, fontSize = 28.sp)
            Column(Modifier.fillMaxWidth()) {
                Text(s.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                if (s.subtitle.isNotBlank()) Text(s.subtitle, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                if (s.data.isNotBlank()) Text(s.data, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                Text(java.text.SimpleDateFormat("MMM d, yyyy · h:mm a", java.util.Locale.US)
                    .format(java.util.Date(s.ms)), fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
            }
        }
    }
}

private fun <T> androidx.compose.foundation.lazy.LazyListScope.items2(list: List<T>, row: @Composable (T) -> Unit) {
    items(list.size) { i -> row(list[i]) }
}
