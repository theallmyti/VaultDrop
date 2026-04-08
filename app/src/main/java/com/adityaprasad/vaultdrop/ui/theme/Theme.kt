package com.adityaprasad.vaultdrop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VaultDropColorScheme = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = BgPrimary,
    secondary = AccentDim,
    onSecondary = TextPrimary,
    tertiary = StatusSuccess,
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = BgSurface,
    onSurface = TextPrimary,
    surfaceVariant = BgElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = StatusError,
    onError = TextPrimary,
)

@Composable
fun VaultDropTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VaultDropColorScheme,
        typography = VaultDropTypography,
        shapes = VaultDropShapes,
        content = content
    )
}
