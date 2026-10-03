package com.cacheci.apkk.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

@Composable
fun AppTheme(
    controller: ThemeController,
    textStyles: TextStyles = AppTheme.textStyles,
    content: @Composable () -> Unit,
) {
    val rawColors = controller.currentColors()
    val appColors = remember { rawColors.copy() }.apply { updateColorsFrom(rawColors) }
    val appTextStyles = remember { textStyles.copy() }.apply { updateTextStylesFrom(textStyles) }

    CompositionLocalProvider(
        LocalColors provides appColors,
        LocalTextStyles provides appTextStyles,
        LocalContentColor provides appColors.element,
        LocalColorSchemeMode provides controller.colorSchemeMode,
    ) {
        content()
    }
}

object AppTheme {
    val colorScheme: Colors
        @Composable @ReadOnlyComposable
        get() = LocalColors.current

    val textStyles: TextStyles
        @Composable @ReadOnlyComposable
        get() = LocalTextStyles.current

    val colorSchemeMode: ColorSchemeMode?
        @Composable @ReadOnlyComposable
        get() = LocalColorSchemeMode.current

    val DefaultThemeValues: DefaultThemeValues
        @Composable @ReadOnlyComposable
        get() = LocalDefaultThemeValues.current

}

val LocalContentColor = compositionLocalOf { Color.Black }