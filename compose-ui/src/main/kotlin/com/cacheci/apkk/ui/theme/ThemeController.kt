package com.cacheci.apkk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

@Stable
enum class ColorSchemeMode {
    System,
    Light,
    Dark,
}

@Stable
class ThemeController(
    colorSchemeMode: ColorSchemeMode = ColorSchemeMode.System,
    lightColors: Colors = lightColorScheme(),
    darkColors: Colors = darkColorScheme(),
    isDark: Boolean? = null,
) {
    val colorSchemeMode: ColorSchemeMode by mutableStateOf(colorSchemeMode)
    val lightColors: Colors by mutableStateOf(lightColors)
    val darkColors: Colors by mutableStateOf(darkColors)
    val isDark: Boolean? by mutableStateOf(isDark)

    @Composable
    fun currentColors(): Colors = when (colorSchemeMode) {
        ColorSchemeMode.System -> {
            val dark = isDark ?: isSystemInDarkTheme()
            when {
                dark -> darkColors
                else -> lightColors
            }
        }

        ColorSchemeMode.Light -> lightColors

        ColorSchemeMode.Dark -> darkColors
    }
}

internal val LocalColorSchemeMode: ProvidableCompositionLocal<ColorSchemeMode?> = staticCompositionLocalOf { null }