package com.threedd.studio.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CyberpunkScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Ink,
    primaryContainer = NeonCyanDim,
    onPrimaryContainer = TextPrimary,
    secondary = NeonMagenta,
    onSecondary = Ink,
    secondaryContainer = NeonMagentaDim,
    onSecondaryContainer = TextPrimary,
    tertiary = NeonAmber,
    onTertiary = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface2,
    surfaceContainerHigh = Surface3,
    error = Danger,
    onError = Ink,
    outline = Surface3
)

@Composable
fun ThreeDoubleDTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = CyberpunkScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Ink.toArgb()
            window.navigationBarColor = Ink.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = scheme, typography = StudioTypography, content = content)
}
