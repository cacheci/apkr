package com.cacheci.apkk.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cacheci.apkk.ui.theme.AppTheme
import com.cacheci.apkk.ui.theme.AppTheme.DefaultThemeValues

@Composable
internal fun SimpleTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    inBoxAlignment: Alignment = Alignment.CenterStart,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onEditFinished: (() -> Unit)? = null,
) {
    var isEditing by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = DefaultThemeValues.cardInsidePadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        VisualBox {
            Box(
                modifier.widthIn(min = 150.dp).padding(8.dp),
                contentAlignment = inBoxAlignment
            ) {
                if (value.isEmpty()) Text(placeholder)
                BasicTextField(
                    modifier = Modifier
                        .onFocusChanged { focusState ->
                            if (isEditing && !focusState.isFocused) onEditFinished?.invoke()
                            isEditing = focusState.isFocused
                        },
                    value = value,
                    textStyle = AppTheme.textStyles.main.copy(
                        color = if (enabled) AppTheme.colorScheme.element else AppTheme.colorScheme.disabledElement
                    ),
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = if (onEditFinished != null) {
                        KeyboardActions(onDone = { focusManager.clearFocus() })
                    } else {
                        KeyboardActions.Default
                    },
                    visualTransformation = visualTransformation,
                    enabled = enabled,
                )
            }
        }
    }
}