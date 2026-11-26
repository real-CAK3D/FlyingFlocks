package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A "?" icon for a screen's top bar (or anywhere). Tap it for a plain-language explanation of
 * what the page / feature is and does. Pass [items] (element → what/when/how) to show a scrollable
 * per-control guide under the summary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpButton(title: String, body: String, items: List<Pair<String, String>>? = null) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.HelpOutline, contentDescription = "What is this?")
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                    items?.forEach { (name, desc) ->
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            Text(desc, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                        }
                    }
                }
            },
        )
    }
}
