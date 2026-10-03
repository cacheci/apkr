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
    borderRadius: Dp,
    cardInsidePadding: Dp
) {
    var borderRadius by mutableStateOf(borderRadius, structuralEqualityPolicy())
        internal set
    var cardInsidePadding by mutableStateOf(cardInsidePadding, structuralEqualityPolicy())
        internal set
}

fun actualDefaultThemeValues(
    borderRadius: Dp = 8.dp,
    cardInsidePadding: Dp = 16.dp
): DefaultThemeValues = DefaultThemeValues(
    borderRadius = borderRadius,
    cardInsidePadding = cardInsidePadding,
)

internal val LocalDefaultThemeValues: ProvidableCompositionLocal<DefaultThemeValues> = staticCompositionLocalOf { actualDefaultThemeValues() }