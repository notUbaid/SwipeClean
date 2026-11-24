package com.antigravity.swipeclean.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Dark-only theme by design for distraction-free photo and video review on OLED screens.
 */
private val DarkColorScheme = darkColorScheme(
    primary = TextPrimary,
    onPrimary = OledBlack,
    secondary = LaterBlue,
    onSecondary = OledBlack,
    tertiary = KeepGreen,
    onTertiary = OledBlack,
    background = OledBlack,
    onBackground = TextPrimary,
    surface = SurfaceBase,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = DeleteRed,
    onError = TextPrimary
)

@Composable
fun SwipeCleanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = SwipeCleanTypography,
        content = content
    )
}
