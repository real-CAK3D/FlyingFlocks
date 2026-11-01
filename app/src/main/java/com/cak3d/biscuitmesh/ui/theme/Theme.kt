package com.cak3d.biscuitmesh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Flying Flocks palette: a sunrise gold primary with a sky-blue secondary — its
// own identity, distinct from Biscuit Manager's plain gold and WDG's neon cyan.
private val FlockGold = Color(0xFFE8A838)
private val FlockGoldDim = Color(0xFFC98A1E)
private val SkyBlue = Color(0xFF5CB4E8)
private val SkyBlueDim = Color(0xFF3A93C9)
private val Ink = Color(0xFF0E1116)
private val InkRaised = Color(0xFF171B22)

private val DarkColors = darkColorScheme(
    primary = FlockGold,
    onPrimary = Ink,
    secondary = SkyBlue,
    onSecondary = Ink,
    tertiary = SkyBlueDim,
    background = Ink,
    surface = InkRaised,
    onBackground = Color(0xFFE6E8EB),
    onSurface = Color(0xFFE6E8EB),
)

private val LightColors = lightColorScheme(
    primary = FlockGoldDim,
    secondary = SkyBlueDim,
    tertiary = FlockGold,
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
