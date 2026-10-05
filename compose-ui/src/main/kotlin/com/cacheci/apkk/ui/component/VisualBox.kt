package com.cacheci.apkk.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.minus
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.cacheci.apkk.ui.theme.AppTheme

@Composable
internal fun VisualBox(
    colors: VisualBoxColors = VisualBoxDefaults.visualBoxColors(),
    visualFeedback: Boolean = false,
    modifier: Modifier = Modifier,
    effectiveWidth: PaddingValues = PaddingValues(2.dp),
    fullPadding: PaddingValues = PaddingValues(2.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(colors.shadow)
            .then(
                if (visualFeedback) {
                    Modifier
                        .padding(
                            start = effectiveWidth.calculateStartPadding(LayoutDirection.Ltr),
                            top = effectiveWidth.calculateTopPadding(),
                        )
                } else {
                    Modifier
                        .padding(
                            end = effectiveWidth.calculateEndPadding(LayoutDirection.Ltr),
                            bottom = effectiveWidth.calculateBottomPadding(),
                        )
                }
            )
            .background(colors.highlight)
            .then(
                if (visualFeedback) {
                    Modifier
                        .padding(
                            end = effectiveWidth.calculateEndPadding(LayoutDirection.Ltr),
                            bottom = effectiveWidth.calculateBottomPadding(),
                        )
                } else {
                    Modifier
                        .padding(
                            start = effectiveWidth.calculateStartPadding(LayoutDirection.Ltr),
                            top = effectiveWidth.calculateTopPadding(),
                        )
                }
            )
            .background(colors.background)
            .padding(
                fullPadding - effectiveWidth
            ),
    ) {
        content()
    }
}

object VisualBoxDefaults {
    @Composable
    fun visualBoxColors(
        highlight: Color = Color.White,
        shadow: Color = Color.Black,
        background: Color = AppTheme.colorScheme.firstContainer
    ): VisualBoxColors = remember(highlight, shadow, background) {
        VisualBoxColors(highlight, shadow, background)
    }
}

@Immutable
data class VisualBoxColors(
    val highlight: Color,
    val shadow: Color,
    val background: Color,
)