package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.data.TirePos
import com.cak3d.biscuitmesh.model.LogEntry
import com.cak3d.biscuitmesh.protocol.BiscuitMessage
import com.cak3d.biscuitmesh.ui.theme.appCardShape
import kotlinx.coroutines.flow.MutableStateFlow

private val GOOD = Color(0xFF34D399)
private val WARN = Color(0xFFF5A623)
private val BAD = Color(0xFFFF5470)

private data class Tpms(val id: String, val psi: Int, val tempC: Int, val timeMs: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TpmsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val logFlow = vm.log ?: remember { MutableStateFlow(emptyList<LogEntry>()) }
    val log by logFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val assigns by vm.tpmsAssignments.collectAsStateWithLifecycle(initialValue = emptyMap())
    val lowPsi by vm.tpmsLowPsi.collectAsStateWithLifecycle(initialValue = 30)

    // Latest reading per TPMS sensor id, from DATA:TPMS:<mhz>,<id>,<kpa>,<tempC>,...
    val sensors = remember(log) {
        log.asSequence()
            .filter { it.message.type == BiscuitMessage.Type.DATA && it.message.subtype == "TPMS" }
            .mapNotNull { e ->
                val f = e.message.payload.split(",")
                val id = f.getOrNull(1) ?: return@mapNotNull null
                val kpa = f.getOrNull(2)?.toIntOrNull() ?: 0
                Tpms(id, (kpa * 0.145038f).toInt(), f.getOrNull(3)?.toIntOrNull() ?: 0, e.timeMs)
            }
            .associateBy { it.id }
    }
    val assignedIds = assigns.values.toSet()
    val unassigned = sensors.keys.filter { it !in assignedIds }

    var pickFor by remember { mutableStateOf<TirePos?>(null) }

    val lows = TirePos.entries.filter { p -> assigns[p]?.let { sensors[it]?.psi }?.let { it < lowPsi } == true }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Tire Pressure") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (lows.isNotEmpty()) {
                Card(Modifier.fillMaxWidth(), shape = appCardShape(),
                    colors = CardDefaults.cardColors(containerColor = BAD.copy(alpha = 0.15f))) {
                    Text("⚠ LOW PRESSURE · ${lows.joinToString { it.short }}",
                        Modifier.padding(14.dp), color = BAD, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }

            Text("YOUR CAR · tap a tire to assign a sensor", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TireCard(TirePos.FL, assigns[TirePos.FL], sensors, lowPsi, Modifier.weight(1f)) { pickFor = TirePos.FL }
                TireCard(TirePos.FR, assigns[TirePos.FR], sensors, lowPsi, Modifier.weight(1f)) { pickFor = TirePos.FR }
            }
            Text("— front —", Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TireCard(TirePos.RL, assigns[TirePos.RL], sensors, lowPsi, Modifier.weight(1f)) { pickFor = TirePos.RL }
                TireCard(TirePos.RR, assigns[TirePos.RR], sensors, lowPsi, Modifier.weight(1f)) { pickFor = TirePos.RR }
            }

            Card(Modifier.fillMaxWidth(), shape = appCardShape(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DETECTED SENSORS · ${sensors.size}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    if (sensors.isEmpty()) {
                        Text("No TPMS yet. Connect the wtBLEep, Start sweep on the Sub-GHz tab, and drive near traffic — sensors wake when wheels spin.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    } else {
                        sensors.values.sortedByDescending { it.timeMs }.forEach { s ->
                            val where = assigns.entries.firstOrNull { it.value == s.id }?.key
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(s.id + (where?.let { " · ${it.short}" } ?: ""), fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium)
                                Text("${s.psi} psi · ${s.tempC}°C", fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium, color = psiColor(s.psi, lowPsi))
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Low-pressure alert at", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { vm.setTpmsLowPsi(lowPsi - 1) }) { Text("−") }
                Text("$lowPsi psi", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                TextButton(onClick = { vm.setTpmsLowPsi(lowPsi + 1) }) { Text("+") }
            }
        }
    }

    pickFor?.let { pos ->
        AlertDialog(
            onDismissRequest = { pickFor = null },
            title = { Text("Assign ${pos.label}") },
            text = {
                Column {
                    if (unassigned.isEmpty() && assigns[pos] == null) {
                        Text("No unassigned sensors detected yet. Drive near traffic to wake TPMS sensors.")
                    }
                    unassigned.forEach { id ->
                        Text("$id  ·  ${sensors[id]?.psi ?: 0} psi",
                            Modifier.fillMaxWidth().clickable { vm.assignTire(pos, id); pickFor = null }.padding(vertical = 10.dp),
                            fontFamily = FontFamily.Monospace)
                    }
                }
            },
            confirmButton = {
                if (assigns[pos] != null) TextButton(onClick = { vm.clearTire(pos); pickFor = null }) { Text("Clear") }
                else TextButton(onClick = { pickFor = null }) { Text("Close") }
            },
            dismissButton = { if (assigns[pos] != null) TextButton(onClick = { pickFor = null }) { Text("Close") } },
        )
    }
}

private fun psiColor(psi: Int, low: Int): Color = when {
    psi < low -> BAD
    psi < low + 4 -> WARN
    else -> GOOD
}

@Composable
private fun TireCard(pos: TirePos, sensorId: String?, sensors: Map<String, Tpms>, low: Int, modifier: Modifier, onTap: () -> Unit) {
    val reading = sensorId?.let { sensors[it] }
    val color = reading?.let { psiColor(it.psi, low) } ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    Card(modifier.height(112.dp).clickable { onTap() }, shape = appCardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(pos.short, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            if (reading != null) {
                Text("${reading.psi}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
                Text("psi · ${reading.tempC}°C", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
            } else if (sensorId != null) {
                Text("—", fontSize = 30.sp, color = color, fontFamily = FontFamily.Monospace)
                Text("waiting…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            } else {
                Text("Tap to\nassign", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}
