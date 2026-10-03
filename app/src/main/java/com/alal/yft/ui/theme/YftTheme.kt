package com.alal.yft.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import com.alal.yft.core.model.ThemeMode

/**
 * YFT's theme: Mint Teal on cool neutrals, Night for dark, Plus Jakarta Sans and bordered,
 * rounded shapes. Dynamic color is deliberately not used so the app looks the same on every
 * device. See `docs/design/DESIGN-NOTES.md`.
 */
@Composable
fun YftTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val darkTheme = isDarkTheme(themeMode)

    CompositionLocalProvider(
        LocalYftColors provides if (darkTheme) YftDarkPalette else YftLightPalette,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) YftDarkColors else YftLightColors,
            typography = YftTypography,
            shapes = YftMaterialShapes,
            content = content,
        )
    }
}

@Composable
fun isDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

object YftTheme {
    val colors: YftColors
        @Composable
        @ReadOnlyComposable
        get() = LocalYftColors.current
}
