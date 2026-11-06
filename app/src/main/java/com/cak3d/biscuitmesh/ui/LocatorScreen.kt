package com.cak3d.biscuitmesh.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.sensors.PhoneScanRow
import kotlin.math.min
import kotlin.math.pow

/**
 * Hot/cold device finder. Pick a WiFi or BLE device and walk toward it: a ring shows its
 * live signal and an estimated distance, and the phone buzzes as you get warmer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocatorScreen(vm: MainViewModel, onBack: () -> Unit) {
    val results by vm.phoneScanResults.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    var targetId by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        vm.clearPhoneScan(); vm.startPhoneScan(wifi = true, ble = true)
        onDispose { vm.stopPhoneScan() }
    }

    val target = results.firstOrNull { it.id == targetId }

    // Buzz when the signal strengthens (warmer).
    var lastRssi by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(target?.rssi, targetId) {
        val r = target?.rssi
        if (r != null) {
            val prev = lastRssi
            if (prev != null && r >= prev + 2) buzz(context, ((r + 100).coerceIn(0, 100)))
            lastRssi = r
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Locator") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (target != null && !picking) {
                LocatorRing(target)
                TextButton(onClick = { picking = true }) { Text("Choose a different device") }
            } else {
                Text("Pick a device to home in on — then walk toward it.", style = MaterialTheme.typography.bodyMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.id }) { row ->
                        Card(
                            Modifier.fillMaxWidth().clickable { targetId = row.id; picking = false; lastRssi = null },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.label.ifBlank { row.id }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                    Text(row.id, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                                Text("${row.rssi} dBm", style = MaterialTheme.typography.labelLarge,
                                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocatorRing(row: PhoneScanRow) {
    val rssi = row.rssi
    val strength = ((rssi + 95f) / 65f).coerceIn(0f, 1f)
    val color = when {
        strength > 0.75f -> Color(0xFF34D399)
        strength > 0.45f -> Color(0xFFF59E0B)
        else -> Color(0xFFFF5470)
    }
    val d = 10.0.pow(((-40.0) - rssi) / (10.0 * 2.7))
    val near = when {
        d < 2 -> "Right here"
        d < 5 -> "Very close"
        d < 15 -> "Close"
        d < 40 -> "Nearby"
        else -> "Far — keep moving"
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(row.label.ifBlank { row.id }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Box(
            Modifier.fillMaxWidth(0.72f).aspectRatio(1f).drawBehind {
                val r = min(size.width, size.height) / 2f
                drawCircle(color.copy(alpha = 0.12f), r)
                drawCircle(color.copy(alpha = 0.35f), r * (0.4f + strength * 0.6f), style = Stroke(width = 10f))
                drawCircle(color, r * (0.4f + strength * 0.6f) * 0.5f, style = Stroke(width = 6f))
            },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$rssi dB", style = MaterialTheme.typography.headlineMedium, color = color,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(near, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            }
        }
        Text("~${d.toInt()} m away · buzzes as you get warmer", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

private fun buzz(context: Context, amplitudePct: Int) {
    runCatching {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amp = (amplitudePct * 255 / 100).coerceIn(1, 255)
            vib.vibrate(VibrationEffect.createOneShot(60, amp))
        } else {
            @Suppress("DEPRECATION") vib.vibrate(60)
        }
    }
}
