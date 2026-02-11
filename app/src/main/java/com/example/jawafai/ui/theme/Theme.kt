package com.example.jawafai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = lightColorScheme(
    primary = primary500,
    secondary = secondary500,
    tertiary = accent500,
    background = background900,
    surface = background800,
    onPrimary = text50,
    onSecondary = text50,
    onTertiary = text50,
    onBackground = text100,
    onSurface = text100,
    surfaceVariant = background700,
    outline = text400
)

@Composable
fun JawafaiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content
    )
}
