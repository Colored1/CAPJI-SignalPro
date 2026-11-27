package com.capji.signalpro.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

object Capji {
    val Navy = Color(0xFF070B14)
    val Surface = Color(0xFF0E1526)
    val SurfaceHigh = Color(0xFF162038)
    val Cyan = Color(0xFF00E5FF)
    val Emerald = Color(0xFF00E676)
    val Violet = Color(0xFF7C4DFF)
    val Amber = Color(0xFFFFC400)
    val Red = Color(0xFFFF5370)
    val Text = Color(0xFFEAF2FF)
    val Muted = Color(0xFF8FA3C0)

    /** Green when good, amber in between, red when poor. */
    fun quality(percent: Int): Color = when {
        percent >= 60 -> Emerald
        percent >= 35 -> Amber
        else -> Red
    }

    fun latency(ms: Double): Color = when {
        ms < 60 -> Emerald
        ms < 150 -> Amber
        else -> Red
    }
}

private val scheme = darkColorScheme(
    primary = Capji.Cyan,
    onPrimary = Capji.Navy,
    primaryContainer = Color(0xFF0B3A4A),
    onPrimaryContainer = Capji.Cyan,
    secondary = Capji.Emerald,
    onSecondary = Capji.Navy,
    secondaryContainer = Color(0xFF123A3A),
    onSecondaryContainer = Capji.Text,
    tertiary = Capji.Violet,
    onTertiary = Color.White,
    background = Capji.Navy,
    onBackground = Capji.Text,
    surface = Capji.Surface,
    onSurface = Capji.Text,
    surfaceVariant = Capji.SurfaceHigh,
    onSurfaceVariant = Capji.Muted,
    surfaceContainerLowest = Capji.Navy,
    surfaceContainerLow = Capji.Surface,
    surfaceContainer = Capji.Surface,
    surfaceContainerHigh = Capji.SurfaceHigh,
    surfaceContainerHighest = Capji.SurfaceHigh,
    outline = Color(0xFF33425F),
    outlineVariant = Color(0xFF223050),
    error = Capji.Red,
)

@Composable
fun CapjiTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = scheme,
        typography = base.copy(
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
        ),
    ) {
        // Text and icons outside a Material Surface would otherwise be black.
        CompositionLocalProvider(LocalContentColor provides Capji.Text, content = content)
    }
}
