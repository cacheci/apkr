package com.cacheci.apkk.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cacheci.apkk.ui.theme.AppTheme

@Composable
internal fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    enabled: Boolean = true,
    colors: TextButtonColors = TextButtonDefaults.textButtonColors(),
    textStyle: TextStyle = AppTheme.textStyles.main,
    borderWidth: Dp? = null,
    onLongClick: () -> Unit = {},
) {
    Button(
        modifier = modifier,
        enabled = enabled,
        colors = colors.button,
        borderWidth = borderWidth,
        onClick = onClick,
        onLongClick = onLongClick,
    ) {
        Text(
            modifier = modifier.padding(textPadding),
            text = text,
            color = if (enabled) colors.text else colors.disabledText,
            style = textStyle,
            minLines = 1,
        )
    }
}

object TextButtonDefaults {

    @Composable
    fun textButtonColors(
        text: Color = AppTheme.colorScheme.element,
        disabledText: Color = AppTheme.colorScheme.disabledElement,
        button: ButtonColors = ButtonDefaults.buttonColors()
    ): TextButtonColors = remember(text, disabledText, button) {
        TextButtonColors(
            text = text,
            disabledText = disabledText,
            button = button,
        )
    }

    @Composable
    fun textButtonPrimaryColors(
        text: Color = AppTheme.colorScheme.primaryElement,
        disabledText: Color = AppTheme.colorScheme.disabledElement,
        button: ButtonColors = ButtonDefaults.buttonPrimaryColors()
    ): TextButtonColors = remember(text, disabledText, button) {
        TextButtonColors(
            text = text,
            disabledText = disabledText,
            button = button,
        )
    }
}

@Immutable
data class TextButtonColors(
    val text: Color,
    val disabledText: Color,
    val button: ButtonColors,
)
