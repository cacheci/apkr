package com.cacheci.apkk.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.cacheci.apkk.ui.theme.AppTheme

@Composable
internal fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: () -> Unit = {},
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable () -> Unit = {},
) {
    val interactionSource = remember {
        MutableInteractionSource()
    }
    val isPressed by interactionSource.collectIsPressedAsState()

    VisualBox (
        visualFeedback = isPressed
    ) {
        Box(
            modifier = modifier
                .background(color = colors.background),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = if (enabled) Modifier
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                        interactionSource = interactionSource,
                        indication = null,
                    ) else Modifier,
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
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
