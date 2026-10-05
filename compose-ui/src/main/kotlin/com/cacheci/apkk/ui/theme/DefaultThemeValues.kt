package com.cacheci.apkk.ui.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
class DefaultThemeValues(
    cardInsidePadding: Dp
) {
    var cardInsidePadding by mutableStateOf(cardInsidePadding, structuralEqualityPolicy())
        internal set
}

fun actualDefaultThemeValues(
    cardInsidePadding: Dp = 16.dp
): DefaultThemeValues = DefaultThemeValues(
    cardInsidePadding = cardInsidePadding,
)

internal val LocalDefaultThemeValues: ProvidableCompositionLocal<DefaultThemeValues> = staticCompositionLocalOf { actualDefaultThemeValues() }