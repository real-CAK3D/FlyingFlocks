package com.cak3d.biscuitmesh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BiscuitGold = Color(0xFFE8A838)
private val BiscuitGoldDim = Color(0xFFC98A1E)
private val Ink = Color(0xFF0E1116)
private val InkRaised = Color(0xFF171B22)

private val DarkColors = darkColorScheme(
    primary = BiscuitGold,
    onPrimary = Ink,
    secondary = BiscuitGoldDim,
    background = Ink,
    surface = InkRaised,
    onBackground = Color(0xFFE6E8EB),
    onSurface = Color(0xFFE6E8EB),
)

private val LightColors = lightColorScheme(
    primary = BiscuitGoldDim,
    secondary = BiscuitGold,
)

@Composable
fun BiscuitMeshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
