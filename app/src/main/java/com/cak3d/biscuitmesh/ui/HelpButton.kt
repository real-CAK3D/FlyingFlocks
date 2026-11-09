package com.cak3d.biscuitmesh.ui

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

/**
 * A "?" icon for a screen's top bar (or anywhere). Tap it for a plain-language explanation of
 * what the page / feature is and does. Drop `HelpButton("…", "…")` into a TopAppBar's `actions`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpButton(title: String, body: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.HelpOutline, contentDescription = "What is this?")
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } },
            title = { Text(title) },
            text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        )
    }
}
