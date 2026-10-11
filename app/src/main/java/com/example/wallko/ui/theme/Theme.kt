package com.example.wallko.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.compositeOver
import com.example.wallko.ui.theme.WallkoColors as C

private val LightColors = lightColorScheme(
    primary = C.Grass,
    onPrimary = C.Ink,
    primaryContainer = C.Grass.copy(alpha = 0.35f).compositeOver(C.Cream),
    onPrimaryContainer = C.Ink,
    secondary = C.Sky,
    onSecondary = C.Ink,
    secondaryContainer = C.Sky.copy(alpha = 0.35f).compositeOver(C.Cream),
    onSecondaryContainer = C.Ink,
    tertiary = C.Lavender,
    onTertiary = C.Ink,
    tertiaryContainer = C.Lavender.copy(alpha = 0.4f).compositeOver(C.Cream),
    onTertiaryContainer = C.Ink,
    background = C.Cream,
    onBackground = C.Ink,
    surface = C.Cream,
    onSurface = C.Ink,
    surfaceVariant = C.PeachSoft,
    onSurfaceVariant = C.Soil,
    surfaceContainerLowest = C.Cream,
    surfaceContainerLow = C.Cream,
    surfaceContainer = C.CreamDeep,
    surfaceContainerHigh = C.CreamDeep,
    surfaceContainerHighest = C.PeachSoft,
    inverseSurface = C.Ink,
    inverseOnSurface = C.Cream,
    outline = C.Soil,
    outlineVariant = C.Wilt,
    error = C.Berry,
    onError = C.Ink,
)

private val DarkColors = darkColorScheme(
    primary = C.Grass,
    onPrimary = C.Ink,
    primaryContainer = C.Grass.copy(alpha = 0.3f).compositeOver(C.NightSurface),
    onPrimaryContainer = C.Cream,
    secondary = C.Sky,
    onSecondary = C.Ink,
    secondaryContainer = C.Sky.copy(alpha = 0.3f).compositeOver(C.NightSurface),
    onSecondaryContainer = C.Cream,
    tertiary = C.Lavender,
    onTertiary = C.Ink,
    tertiaryContainer = C.Lavender.copy(alpha = 0.3f).compositeOver(C.NightSurface),
    onTertiaryContainer = C.Cream,
    background = C.NightBackground,
    onBackground = C.Cream,
    surface = C.NightBackground,
    onSurface = C.Cream,
    surfaceVariant = C.NightSurface,
    onSurfaceVariant = C.Peach,
    surfaceContainerLowest = C.NightBackground,
    surfaceContainerLow = C.NightSurface,
    surfaceContainer = C.NightSurface,
    surfaceContainerHigh = C.NightSurface,
    surfaceContainerHighest = C.NightSurface,
    inverseSurface = C.Cream,
    inverseOnSurface = C.Ink,
    outline = C.Wilt,
    outlineVariant = C.Soil,
    error = C.Berry,
    onError = C.Ink,
)

/** Wallko theme. Dynamic colour is intentionally not supported. */
@Composable
fun WallkoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
