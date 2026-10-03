package com.cacheci.apkk.ui.component

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.cacheci.apkk.ui.theme.AppTheme
import com.cacheci.apkk.ui.theme.AppTheme.DefaultThemeValues


@Composable
internal fun <T : Enum<T>> EnumSelector(
    label: String,
    selected: Int,
    values: List<String>,
    onValueChange: (Int) -> Unit,
) {
    DropdownSelector(
        label = label,
        selected = selected,
        values = values,
        onValueChange = onValueChange,
    )
}

@Composable
internal fun DropdownSelector(
    label: String,
    selected: Int,
    values: List<String>,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: DropdownColors = DropdownDefaults.dropdownColors(),
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val visibilityState = remember { MutableTransitionState(false) }

    LaunchedEffect(expanded, enabled) {
        visibilityState.targetState = expanded && enabled
    }

    Row (
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = DefaultThemeValues.cardInsidePadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Column (horizontalAlignment = Alignment.End) {
            TextButton(
                text = values[selected],
                enabled = enabled,
                colors = colors.textButton,
                onClick = { expanded = true },
            )
            if (visibilityState.currentState || visibilityState.targetState) {
                Popup(
                    alignment = Alignment.TopEnd,
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visibleState = visibilityState,
                        enter = expandVertically(
                            animationSpec = tween(durationMillis = 160),
                            expandFrom = Alignment.Top
                        ),
                        exit = shrinkVertically(
                            animationSpec = tween(durationMillis = 120),
                            shrinkTowards = Alignment.Top
                        ),
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                background = colors.textButton.button.background,
                                border = colors.textButton.button.border,
                            ),
                            borderWidth = 1.dp,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                TextButton(
                                    text = values[selected],
                                    onClick = {
                                        expanded = false
                                    },
                                    colors = colors.textButton,
                                )
                                values.forEachIndexed { index, option ->
                                    if (index != selected) {
                                        TextButton(
                                            text = option,
                                            onClick = {
                                                onValueChange(index)
                                                expanded = false
                                            },
                                            colors = colors.textButton,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

object DropdownDefaults {
    @Composable
    fun dropdownColors(
        background: Color = Color.Transparent,
        textButton: TextButtonColors = TextButtonDefaults.textButtonColors(
            button = ButtonDefaults.buttonColors(
                background = AppTheme.colorScheme.secondContainer
            ),
        )
    ): DropdownColors = remember(background, textButton) {
        DropdownColors(
            background = background,
            textButton = textButton,
        )
    }
}
@Immutable
data class DropdownColors(
    val background: Color,
    val textButton: TextButtonColors,
)