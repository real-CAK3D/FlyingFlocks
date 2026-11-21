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
import androidx.compose.material.icons.filled.Streetview
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cak3d.biscuitmesh.MainViewModel

/** The two ground views that share one window — your drive on the land, or through the camera. */
enum class LandMode(val label: String, val icon: ImageVector) {
    TERRAIN("3D Terrain", Icons.Default.Terrain),
    EYESIGHT("Eyesight AR", Icons.Default.Streetview),
}

/**
 * One window for your drive on the ground: the 3D terrain map and the first-person Eyesight AR
 * camera, switched with a toggle at the bottom — the same fold-into-one pattern Orbit uses for
 * the sky. Each view is the full existing screen; the switcher floats above it.
 */
@Composable
fun LandscapeScreen(vm: MainViewModel, onBack: () -> Unit, startMode: LandMode = LandMode.TERRAIN) {
    var mode by remember { mutableStateOf(startMode) }
    Box(Modifier.fillMaxSize()) {
        when (mode) {
            LandMode.TERRAIN -> Terrain3DScreen(vm, onBack, onOpenEyesight = { mode = LandMode.EYESIGHT })
            LandMode.EYESIGHT -> EyesightArScreen(vm, onBack)
        }
        LandSwitcher(mode, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) { mode = it }
    }
}

@Composable
private fun LandSwitcher(mode: LandMode, modifier: Modifier, onPick: (LandMode) -> Unit) {
    Row(
        modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LandMode.entries.forEach { m ->
            val on = m == mode
            Row(
                Modifier.clickable { onPick(m) }.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    m.icon, contentDescription = null, modifier = Modifier.size(18.dp),
                    tint = if (on) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                androidx.compose.material3.Text(
                    m.label.uppercase(),
                    color = if (on) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
