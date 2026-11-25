package com.cak3d.biscuitmesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cak3d.biscuitmesh.BoundaryCrossing
import kotlinx.coroutines.delay

/**
 * A brief "NOW ENTERING …" bar that drops in when you drive into a new town, county or state. Pinned
 * to the top, non-blocking (taps fall through to the app), and it dismisses itself after a few
 * seconds. Shows a "★ first time here" flag the first time you reach a town this session.
 */
@Composable
fun CrossingBanner(crossing: BoundaryCrossing, onDismiss: () -> Unit) {
    LaunchedEffect(crossing) { delay(5000); onDismiss() }
    val sub = when (crossing.kind) {
        "state" -> "State line"
        "county" -> listOfNotNull(crossing.place.state.ifBlank { null }).joinToString().ifBlank { "County line" }
            .let { "County · $it" }
        else -> listOfNotNull(
            crossing.place.county.ifBlank { null }?.let { "$it County" },
            crossing.place.state.ifBlank { null },
        ).joinToString(" · ").ifBlank { "Town line" }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 6.dp, shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Place, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (crossing.kind == "state") "NOW ENTERING · STATE"
                        else "NOW ENTERING · ${crossing.kind.uppercase()}",
                        fontSize = 10.sp, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text(crossing.label, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(sub, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                }
                if (crossing.firstVisit) {
                    Surface(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)) {
                        Text("★ NEW", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}
