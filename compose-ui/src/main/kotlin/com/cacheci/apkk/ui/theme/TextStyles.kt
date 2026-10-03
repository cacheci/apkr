package com.cacheci.apkk.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp


@Stable
class TextStyles(
    main: TextStyle,
    h1: TextStyle,
    summary: TextStyle,
    ref: TextStyle,
) {
    var main by mutableStateOf(main, structuralEqualityPolicy())
        internal set
    var h1 by mutableStateOf(h1, structuralEqualityPolicy())
        internal set
    var summary by mutableStateOf(summary, structuralEqualityPolicy())
        internal set
    var ref by mutableStateOf(ref, structuralEqualityPolicy())
        internal set

    fun copy(
        main: TextStyle = this.main,
        h1: TextStyle = this.h1,
        summary: TextStyle = this.summary,
        ref: TextStyle = this.ref,
    ): TextStyles = TextStyles(
        main,
        h1,
        summary,
        ref,
    )
}

fun defaultTextStyles(
    main: TextStyle = Main,
    h1: TextStyle = H1,
    summary: TextStyle = Summary,
    ref: TextStyle = Ref,
): TextStyles = TextStyles(
    main,
    h1,
    summary,
    ref,
)


private val Main: TextStyle
    get() =
        TextStyle(
            fontSize = 17.sp,
        )

private val H1: TextStyle
    get() =
        TextStyle(
            fontSize = 32.sp,
        )

private val Summary: TextStyle
    get() =
        TextStyle(
            fontSize = 16.sp,
        )

private val Ref: TextStyle
    get() =
        TextStyle(
            fontSize = 14.sp,
        )

@Stable
internal fun TextStyles.updateTextStylesFrom(other: TextStyles) {
    main = other.main
    h1 = other.h1
}

internal val LocalTextStyles = staticCompositionLocalOf { defaultTextStyles() }