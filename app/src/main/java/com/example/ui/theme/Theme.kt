package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val HighDensityColorScheme = lightColorScheme(
    primary = HighDensityPrimary,
    onPrimary = HighDensityOnPrimary,
    primaryContainer = HighDensityPrimaryContainer,
    onPrimaryContainer = HighDensityOnPrimaryContainer,
    background = HighDensityBackground,
    onBackground = HighDensityOnBackground,
    surface = HighDensitySurface,
    onSurface = HighDensityOnSurface,
    surfaceVariant = HighDensitySurfaceVariant,
    onSurfaceVariant = HighDensityOnSurfaceVariant,
    outline = HighDensitySurfaceVariant,
    errorContainer = HighDensityErrorContainer,
    onErrorContainer = HighDensityOnErrorContainer
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = HighDensityColorScheme,
        typography = Typography,
        content = content
    )
}

