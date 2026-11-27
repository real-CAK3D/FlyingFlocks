package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.nav.NavCatalog
import com.cak3d.biscuitmesh.nav.NavDest
import com.cak3d.biscuitmesh.ui.theme.appCardShape

/** A target for the "pick a feature" dialog: which bar, and the slot index (-1 = add a new one). */
private data class PickTarget(val bottom: Boolean, val index: Int)

/**
 * Navigation editor — let anyone choose which feature sits on each bottom tab and top-bar icon, and
 * in what order. The bottom tabs can hold any feature (a main scanning screen looks like a tab; any
 * other opens with its own header); the top icons hold pages. Settings and Help can be moved but are
 * always kept somewhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavLayoutScreen(vm: MainViewModel, onBack: () -> Unit) {
    val layout by vm.navLayout.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<PickTarget?>(null) }

    fun bottomOps(f: (MutableList<String>) -> Unit) =
        vm.setNavBottom(layout.bottom.toMutableList().apply(f))
    fun topOps(f: (MutableList<String>) -> Unit) =
        vm.setNavTop(layout.top.toMutableList().apply(f))

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Navigation") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (!layout.isDefault) TextButton(onClick = { vm.resetNavLayout() }) { Text("Reset") }
                    HelpButton(
                        "Navigation",
                        "Choose what sits on each bottom tab and top-bar icon. Tap a row to swap it for " +
                            "another feature, use ▲▼ to reorder, or ✕ to remove it. Add a slot with + . The " +
                            "bottom tabs can be any feature; a main scanning screen looks like a tab, anything " +
                            "else opens with its own header. Settings and Help are always kept somewhere.",
                    )
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("bh") { SlotHeader("BOTTOM TABS", "The bar along the bottom. Up to 6.") }
            itemsIndexed(layout.bottom, key = { _, id -> "b-$id" }) { i, id ->
                SlotRow(
                    dest = NavCatalog.byId(id), index = i, count = layout.bottom.size,
                    onTap = { picking = PickTarget(bottom = true, index = i) },
                    onUp = { bottomOps { it[i] = it[i - 1].also { v -> it[i - 1] = v } } },
                    onDown = { bottomOps { it[i] = it[i + 1].also { v -> it[i + 1] = v } } },
                    onRemove = { bottomOps { it.removeAt(i) } },
                )
            }
            if (layout.bottom.size < 6) item("ba") {
                AddRow("Add a bottom tab") { picking = PickTarget(bottom = true, index = -1) }
            }

            item("th") { SlotHeader("TOP ICONS", "The quick icons along the top. Up to 8.") }
            itemsIndexed(layout.top, key = { _, id -> "t-$id" }) { i, id ->
                SlotRow(
                    dest = if (id == NavCatalog.HELP) null else NavCatalog.byId(id),
                    label = if (id == NavCatalog.HELP) "Help" else null,
                    index = i, count = layout.top.size,
                    onTap = { picking = PickTarget(bottom = false, index = i) },
                    onUp = { topOps { it[i] = it[i - 1].also { v -> it[i - 1] = v } } },
                    onDown = { topOps { it[i] = it[i + 1].also { v -> it[i + 1] = v } } },
                    onRemove = { topOps { it.removeAt(i) } },
                )
            }
            if (layout.top.size < 8) item("ta") {
                AddRow("Add a top icon") { picking = PickTarget(bottom = false, index = -1) }
            }
        }
    }

    picking?.let { target ->
        // Help is an action, not a destination — only the top bar can host it.
        val options = if (target.bottom) NavCatalog.ALL.filter { it.id != NavCatalog.HELP } else NavCatalog.PAGES
        PickerDialog(
            options = options,
            onDismiss = { picking = null },
            onPick = { id ->
                if (target.bottom) bottomOps { if (target.index >= 0) it[target.index] = id else it.add(id) }
                else topOps { if (target.index >= 0) it[target.index] = id else it.add(id) }
                picking = null
            },
        )
    }
}

@Composable
private fun SlotHeader(title: String, sub: String) {
    Column(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.primary)
        Text(sub, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun SlotRow(
    dest: NavDest?,
    index: Int,
    count: Int,
    onTap: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
    label: String? = null,
) {
    Card(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth(),
        shape = appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (dest != null) Icon(dest.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp))
            Text(label ?: dest?.label ?: "Unknown", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onUp, enabled = index > 0) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(onClick = onDown, enabled = index < count - 1) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
            }
            IconButton(onClick = onRemove, enabled = count > 1) {
                Icon(Icons.Default.Close, contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun AddRow(text: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PickerDialog(options: List<NavDest>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Choose a feature") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(options, key = { it.id }) { d ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(d.id) }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(d.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp))
                        Text(d.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
    )
}
