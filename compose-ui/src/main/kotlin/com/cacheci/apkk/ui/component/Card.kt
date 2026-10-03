package com.cacheci.apkk.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.cacheci.apkk.ui.theme.AppTheme
import com.cacheci.apkk.ui.theme.AppTheme.DefaultThemeValues


@Composable
fun Card(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(),
    borderWidth: Dp? = null,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(shape = RoundedCornerShape(DefaultThemeValues.borderRadius))
            .background(colors.background, shape = RoundedCornerShape(DefaultThemeValues.borderRadius))
            .then(
                if (borderWidth != null) {
                    Modifier.border(
                        width = borderWidth,
                        color = colors.border,
                        shape = RoundedCornerShape(DefaultThemeValues.borderRadius)
                    )
                } else Modifier
            )
    ) {
        content()
    }
}

object CardDefaults {
    @Composable
    fun cardColors(
        background: Color = AppTheme.colorScheme.firstContainer,
        border: Color = AppTheme.colorScheme.firstBorder,
    ): CardColors = remember(background, border) {
        CardColors(
            background = background,
            border = border,
        )
    }
}

@Immutable
data class CardColors(
    val background: Color,
    val border: Color,
)