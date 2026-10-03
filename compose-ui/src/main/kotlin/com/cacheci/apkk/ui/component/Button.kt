package com.cacheci.apkk.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
internal fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: () -> Unit = {},
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    borderWidth: Dp? = null,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(shape = RoundedCornerShape(DefaultThemeValues.borderRadius))
            .background(
                color = colors.background,
                shape = RoundedCornerShape(DefaultThemeValues.borderRadius)
            )
            .then(
                if (borderWidth != null) {
                    Modifier.border(width = borderWidth, color = colors.border, shape = RoundedCornerShape(DefaultThemeValues.borderRadius) )
                } else Modifier
            )

    ) {
        Box(
            modifier = if (enabled) Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) else Modifier
        ) {
            content()
        }
    }
}

object ButtonDefaults {

    @Composable
    fun buttonColors(
        background: Color = AppTheme.colorScheme.firstContainer,
        border: Color = AppTheme.colorScheme.firstBorder,
    ): ButtonColors = remember(background, border) {
        ButtonColors(
            background = background,
            border = border,
        )
    }

    @Composable
    fun buttonPrimaryColors(
        background: Color = AppTheme.colorScheme.primary,
        border: Color = AppTheme.colorScheme.element,
    ): ButtonColors = remember(background, border) {
        ButtonColors(
            background = background,
            border = border,
        )
    }
}

@Immutable
data class ButtonColors(
    val background: Color,
    val border: Color,
)
