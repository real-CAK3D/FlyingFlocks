package com.cak3d.biscuitmesh.ui

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.data.VisionPortal
import com.cak3d.biscuitmesh.data.VisionStore

/**
 * Vision — external God's Eye View style web apps, opened full-screen in an in-app browser.
 * Ships with the owner's CAK3Dvision fork; anyone running the app can point their own fork
 * (over Tailscale, LAN, or a public URL) at it by adding a portal. All local, consume-only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionScreen(vm: MainViewModel, onBack: () -> Unit) {
    val portals by vm.visionPortals.collectAsStateWithLifecycle()
    // Which portal is open full-screen (-1 = the list), and the add/edit dialog target.
    var openIndex by rememberSaveable { mutableIntStateOf(-1) }
    var editing by remember { mutableStateOf<EditTarget?>(null) }

    val open = portals.getOrNull(openIndex)
    if (open != null) {
        PortalWebView(portal = open, onBack = { openIndex = -1 })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vision") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { vm.restoreVisionDefaults() }) {
                        Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Restore default portals")
                    }
                    HelpButton(
                        "Vision portals",
                        "Open a God's Eye View style web app inside Flying Flocks. The app ships with " +
                            "CAK3Dvision (reachable over Tailscale). Add your own fork — anything the phone " +
                            "can reach by URL, including your own Tailscale node — with the + button. The URLs " +
                            "stay on your phone and nothing is uploaded.",
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = EditTarget(index = -1, name = "", url = "") },
                modifier = Modifier.help("Add portal", "Point Vision at your own God's Eye View fork — any URL the phone can reach, including a Tailscale node. It opens full-screen in an in-app browser. Tap a portal to open it; the URLs stay on your phone."),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add portal")
            }
        },
    ) { pad ->
        if (portals.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.RemoveRedEye, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp))
                    Text(
                        "No Vision portals yet. Tap + to add your God's Eye View fork — a URL or your Tailscale node.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    TextButton(onClick = { vm.restoreVisionDefaults() }) { Text("Restore default portals") }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(portals, key = { i, p -> "$i-${p.url}" }) { i, p ->
                    PortalCard(
                        portal = p,
                        onOpen = { openIndex = i },
                        onEdit = { editing = EditTarget(index = i, name = p.name, url = p.url) },
                        onRemove = { vm.removeVisionPortal(i) },
                    )
                }
            }
        }
    }

    editing?.let { target ->
        PortalDialog(
            target = target,
            onDismiss = { editing = null },
            onSave = { name, url ->
                if (target.index < 0) vm.addVisionPortal(name, url) else vm.updateVisionPortal(target.index, name, url)
                editing = null
            },
        )
    }
}

private data class EditTarget(val index: Int, val name: String, val url: String)

@Composable
private fun PortalCard(portal: VisionPortal, onOpen: () -> Unit, onEdit: () -> Unit, onRemove: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = com.cak3d.biscuitmesh.ui.theme.appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(portal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    portal.url,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Remove") }
        }
    }
}

@Composable
private fun PortalDialog(target: EditTarget, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(target.name) }
    var url by remember { mutableStateOf(target.url) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (target.index < 0) "Add Vision portal" else "Edit portal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("URL or Tailscale address") },
                    placeholder = { Text("nukebox.tailac984b.ts.net:4173") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Your fork of God's Eye View, reachable from this phone. https:// is assumed if you leave off the scheme.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, url) },
                enabled = VisionStore.normalizeUrl(url).isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A portal opened full-screen in an in-app WebView, with reload / open-externally controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortalWebView(portal: VisionPortal, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Hardware back navigates within the portal first, then exits to the list.
    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(portal.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to portals") }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, contentDescription = "Reload") }
                    IconButton(onClick = {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(portal.url))) }
                    }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in browser") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    WebView(c).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        @Suppress("SetJavaScriptEnabled")
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                loading = true
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                            }
                        }
                        webChromeClient = WebChromeClient()
                        loadUrl(portal.url)
                        webView = this
                    }
                },
                update = { webView = it },
            )
        }
    }
}
