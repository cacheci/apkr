package com.cacheci.apkk.ui.component.foundation

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun RoundedCornerShape(
    top: Dp? = null,
    bottom: Dp? = null,
    start: Dp? = null,
    end: Dp? = null,
) =
    RoundedCornerShape(
        topStart = CornerSize(top ?: start ?: 0.dp),
        topEnd = CornerSize(top ?: end ?: 0.dp),
        bottomEnd = CornerSize(bottom ?: start ?: 0.dp),
        bottomStart = CornerSize(bottom ?: end ?: 0.dp),
    )