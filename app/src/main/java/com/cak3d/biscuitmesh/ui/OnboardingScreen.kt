package com.cak3d.biscuitmesh.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cak3d.biscuitmesh.MainViewModel
import com.cak3d.biscuitmesh.data.ApiKeys
import com.cak3d.biscuitmesh.data.ApiService

/**
 * First-run (and re-runnable) setup wizard: welcome → profile handle → optional API keys with
 * one-tap sign-up links → a quick tour of the app → done. Full-screen overlay; [onDone] marks the
 * tour seen. Re-armed from Settings → Profile → "Run setup again".
 */
@Composable
fun OnboardingScreen(vm: MainViewModel, onDone: () -> Unit) {
    val steps = 5
    var step by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("SETUP · ${step + 1}/$steps", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                TextButton(onClick = onDone) { Text("Skip") }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
                repeat(steps) { i ->
                    Box(Modifier.padding(horizontal = 3.dp).size(if (i == step) 9.dp else 6.dp).clip(CircleShape)
                        .background(if (i <= step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)))
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                when (step) {
                    0 -> WelcomeStep()
                    1 -> ProfileStep(vm)
                    2 -> KeysStep(vm)
                    3 -> WalkthroughStep()
                    else -> DoneStep()
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) { Text("Back") }
                Button(onClick = { if (step < steps - 1) step++ else onDone() }, modifier = Modifier.weight(1f)) {
                    Text(if (step < steps - 1) "Next" else "Start flying")
                }
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, sub: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text(sub, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
    }
}

@Composable
private fun WelcomeStep() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        CrowTrailHeader(Modifier.fillMaxWidth().height(150.dp))
        Text("FLOCKED UP", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            fontSize = 24.sp, letterSpacing = 4.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Text("A fleet-first wardriver, sky-watcher and RF toolkit. Catch Wi-Fi, Bluetooth and sub-GHz, " +
            "track satellites and read the night sky — with your phone, or a flock of ESP32 boards.",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun ProfileStep(vm: MainViewModel) {
    val saved by vm.flockHandle.collectAsStateWithLifecycle(initialValue = "")
    var handle by remember(saved) { mutableStateOf(saved) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StepHeader("Your call sign", "Pick a handle for the Flock — the online layer where you can see " +
            "other wings on the map and swap signals. Optional; you can change it later in Settings.")
        OutlinedTextField(
            value = handle, onValueChange = { handle = it },
            label = { Text("Flock handle") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { vm.setFlockHandle(handle.trim()) }, enabled = handle.trim().isNotBlank() && handle.trim() != saved) {
            Text(if (saved.isBlank()) "Save handle" else "Update handle")
        }
    }
}

@Composable
private fun KeysStep(vm: MainViewModel) {
    val keys by vm.apiKeys.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepHeader("Optional keys", "Everything works with no keys at all. Add one only to unlock or " +
            "raise limits on a live feed — tap a link to sign up, grab the key, come back and paste it. " +
            "You can always do this later in Settings → Data.")
        ApiKeys.services.forEach { svc -> OnboardKeyField(svc, keys[svc.id] ?: "") { vm.setApiKey(svc.id, it) } }
    }
}

@Composable
private fun OnboardKeyField(svc: ApiService, saved: String, onSave: (String) -> Unit) {
    val ctx = LocalContext.current
    var field by remember(saved) { mutableStateOf(saved) }
    Card(shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(svc.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(svc.hint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            OutlinedTextField(value = field, onValueChange = { field = it }, singleLine = true,
                label = { Text("Key") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (svc.url.isNotBlank()) {
                    TextButton(onClick = {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(svc.url))) }
                    }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp)); Text("Sign up / get key")
                    }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { onSave(field.trim()) }, enabled = field.trim() != saved) {
                    Text(if (saved.isBlank()) "Save" else "Update")
                }
            }
        }
    }
}

@Composable
private fun WalkthroughStep() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StepHeader("The lay of the land", "Five tabs at the bottom, and more up top.")
        TourCard("Fleet", "Connect your ESP32 boards — or scan with just the phone.")
        TourCard("Wardrive · Wi-Fi · Bluetooth · Sub-GHz", "The bottom tabs: drive and map what's in the air, each band with its own live spectrum.")
        TourCard("Sensors & Sky", "The satellite icon up top — Orbit Globe, Sky AR with constellations, light pollution, the Data Cube and more.")
        TourCard("Terminal", "The console icon — talk to your boards, or jump anywhere in the app by name.")
        TourCard("Settings → Profile", "Your handle, API keys, and this setup — re-run it anytime.")
    }
}

@Composable
private fun TourCard(title: String, body: String) {
    Card(shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text(body, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun DoneStep() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), shape = CircleShape,
            modifier = Modifier.size(84.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("✓", fontSize = 44.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("You're all set", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Tap Start flying. You can re-run this setup anytime from Settings → Profile.",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.padding(horizontal = 24.dp))
    }
}
