package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.cak3d.biscuitmesh.MainViewModel

/** The three views the Orbit screen flips between — the sky equivalent of Compass/Radar/Locator. */
enum class OrbitMode(val label: String) { SATELLITES("Satellites"), AIRCRAFT("Aircraft"), SPACE_WEATHER("Space weather") }

/**
 * Orbit — one screen for everything overhead, with a bottom switcher between the live satellite
 * globe, aircraft (ADS-B), and space weather. Each view is the full existing screen; the switcher
 * floats at the bottom, mirroring how Sensors & Sky folds Compass/Radar/Locator into one.
 */
@Composable
fun OrbitScreen(vm: MainViewModel, onBack: () -> Unit, startMode: OrbitMode = OrbitMode.SATELLITES) {
    var mode by remember { mutableStateOf(startMode) }
    Box(Modifier.fillMaxSize()) {
        when (mode) {
            OrbitMode.SATELLITES -> GlobeScreen(vm, onBack)
            OrbitMode.AIRCRAFT -> AircraftScreen(vm, onBack)
            OrbitMode.SPACE_WEATHER -> AuroraScreen(vm, onBack)
        }
        OrbitSwitcher(
            mode,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) { mode = it }
    }
}

@Composable
private fun OrbitSwitcher(mode: OrbitMode, modifier: Modifier, onPick: (OrbitMode) -> Unit) {
    val modes = OrbitMode.entries
    val i = modes.indexOf(mode)
    Row(
        modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onPick(modes[(i - 1 + modes.size) % modes.size]) },
            modifier = Modifier.size(34.dp),
        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous view") }
        modes.forEach { m ->
            val on = m == mode
            androidx.compose.material3.Text(
                m.label.uppercase(),
                color = if (on) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp,
                modifier = Modifier.clickable { onPick(m) }.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
        IconButton(
            onClick = { onPick(modes[(i + 1) % modes.size]) },
            modifier = Modifier.size(34.dp),
        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next view") }
    }
}
