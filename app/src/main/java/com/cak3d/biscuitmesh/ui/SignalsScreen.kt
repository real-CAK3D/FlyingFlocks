package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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

/** The signal visualizers, folded into one window with a view-switcher instead of five hub entries. */
enum class SignalsView(val label: String) {
    SCOPE("Scope"), LIVE("Live"), GRAPH("Graph"), DEVICES("Devices"), DASHBOARD("Dashboard"), TRAFFIC("Traffic")
}

/**
 * One home for every live-signal visualizer — the RF scope, the mission-control live console, the
 * device graph, the signal dashboard and the traffic charts — switched with a strip at the bottom,
 * the same fold-into-one pattern Orbit (sky) and Landscape (ground) use.
 */
@Composable
fun SignalsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenSignal: (String) -> Unit = {},
    startView: SignalsView = SignalsView.SCOPE,
) {
    var view by remember { mutableStateOf(startView) }
    Box(Modifier.fillMaxSize()) {
        when (view) {
            SignalsView.SCOPE -> ScopeScreen(vm, onBack)
            SignalsView.LIVE -> LiveScreen(vm, onBack, onOpenSignal)
            SignalsView.GRAPH -> ForceGraphScreen(vm, onBack)
            SignalsView.DEVICES -> DevicesScreen(vm, onBack)
            SignalsView.DASHBOARD -> DashboardScreen(vm, onBack)
            SignalsView.TRAFFIC -> TrafficDashboardScreen(vm, onBack)
        }
        SignalsSwitcher(view, Modifier.align(Alignment.BottomCenter).fillMaxWidth()) { view = it }
    }
}

@Composable
private fun SignalsSwitcher(view: SignalsView, modifier: Modifier, onPick: (SignalsView) -> Unit) {
    Row(
        modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignalsView.entries.forEach { v ->
            val on = v == view
            androidx.compose.material3.Text(
                v.label.uppercase(),
                color = if (on) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp,
                modifier = Modifier.clickable { onPick(v) }.padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}
