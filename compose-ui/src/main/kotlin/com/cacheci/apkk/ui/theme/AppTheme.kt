package com.cacheci.apkk.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.cacheci.apkk.ui.resources.MapleMonoVF
import com.cacheci.apkk.ui.resources.MiSansVF
import com.cacheci.apkk.ui.resources.Res
import org.jetbrains.compose.resources.Font

@Composable
fun AppTheme(
    controller: ThemeController,
    textStyles: TextStyles = AppTheme.textStyles,
    content: @Composable () -> Unit,
) {
    val rawColors = controller.currentColors()
    val appColors = remember { rawColors.copy() }.apply { updateColorsFrom(rawColors) }
    val appFontFamily = FontFamily(
        Font(Res.font.MiSansVF, FontWeight.Normal),
        Font(Res.font.MiSansVF, FontWeight.Medium),
        Font(Res.font.MiSansVF, FontWeight.SemiBold),
    )
    val monospaceFontFamily = FontFamily(
        Font(Res.font.MapleMonoVF, FontWeight.Normal),
        Font(Res.font.MapleMonoVF, FontWeight.Medium),
        Font(Res.font.MapleMonoVF, FontWeight.SemiBold),
    )
    val miSansTextStyles = textStyles.copy(
        main = textStyles.main.copy(fontFamily = appFontFamily),
        h1 = textStyles.h1.copy(fontFamily = appFontFamily),
        summary = textStyles.summary.copy(fontFamily = appFontFamily),
        ref = textStyles.ref.copy(fontFamily = appFontFamily),
    )
    val appTextStyles = remember { miSansTextStyles.copy() }.apply {
        updateTextStylesFrom(miSansTextStyles)
    }

    CompositionLocalProvider(
        LocalColors provides appColors,
        LocalTextStyles provides appTextStyles,
        LocalMonospaceFontFamily provides monospaceFontFamily,
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

    val monospaceFontFamily: FontFamily
        @Composable @ReadOnlyComposable
        get() = LocalMonospaceFontFamily.current

    val colorSchemeMode: ColorSchemeMode?
        @Composable @ReadOnlyComposable
        get() = LocalColorSchemeMode.current

    val DefaultThemeValues: DefaultThemeValues
        @Composable @ReadOnlyComposable
        get() = LocalDefaultThemeValues.current

}

private val LocalMonospaceFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

val LocalContentColor = compositionLocalOf { Color.Black }
