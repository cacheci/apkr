package com.cacheci.apkk.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.graphics.Color

class Colors(
    primary: Color,
    error: Color,
    element: Color,
    primaryElement: Color,
    disabledElement: Color,
    summary: Color,
    disabledSummary: Color,
    background: Color,
    firstContainer: Color,
    firstBorder: Color,
    secondContainer: Color,
    secondBorder: Color,
    thirdContainer: Color,
    thirdBorder: Color,
) {
    var primary by mutableStateOf(primary, structuralEqualityPolicy())
        internal set
    var error by mutableStateOf(error, structuralEqualityPolicy())
        internal set
    var element by mutableStateOf(element, structuralEqualityPolicy())
        internal set
    var primaryElement by mutableStateOf(primaryElement, structuralEqualityPolicy())
        internal set
    var disabledElement by mutableStateOf(disabledElement, structuralEqualityPolicy())
        internal set
    var summary by mutableStateOf(summary, structuralEqualityPolicy())
        internal set
    var disabledSummary by mutableStateOf(disabledSummary, structuralEqualityPolicy())
        internal set
    var background by mutableStateOf(background, structuralEqualityPolicy())
        internal set
    var firstContainer by mutableStateOf(firstContainer, structuralEqualityPolicy())
        internal set
    var firstBorder by mutableStateOf(firstBorder, structuralEqualityPolicy())
        internal set
    var secondContainer by mutableStateOf(secondContainer, structuralEqualityPolicy())
        internal set
    var secondBorder by mutableStateOf(secondBorder, structuralEqualityPolicy())
        internal set
    var thirdContainer by mutableStateOf(thirdContainer, structuralEqualityPolicy())
        internal set
    var thirdBorder by mutableStateOf(thirdBorder, structuralEqualityPolicy())
        internal set


    fun copy(
        primary: Color = this.primary,
        error: Color = this.error,
        element: Color = this.element,
        primaryElement: Color = this.primaryElement,
        disabledElement: Color = this.disabledElement,
        summary: Color = this.summary,
        disabledSummary: Color = this.disabledSummary,
        background: Color = this.background,
        firstContainer: Color = this.firstContainer,
        firstBorder: Color = this.firstBorder,
        secondContainer: Color = this.secondContainer,
        secondBorder: Color = this.secondBorder,
        thirdContainer: Color = this.thirdContainer,
        thirdBorder: Color = this.thirdBorder,
    ): Colors = Colors(
        primary, error,
        element, primaryElement, disabledElement,
        summary, disabledSummary,
        background,
        firstContainer, firstBorder,
        secondContainer, secondBorder,
        thirdContainer, thirdBorder,
    )
}


fun lightColorScheme(
    primary: Color = Color(0xFFA19B79),
    error: Color = Color(0xFFB30000),
    element: Color = Color(0xFF000000),
    primaryElement:  Color = Color(0xFFFFFFFF),
    disabledElement: Color = Color(0xFF959595),
    summary: Color = Color(0xFF787878),
    disabledSummary: Color = Color(0xFF454545),
    background: Color = Color(0xFFEBE9D7),
    firstContainer: Color = Color(0xFFF6F5F1),
    firstBorder: Color = Color(0xFFC1C1C1),
    secondContainer: Color = Color(0xFFF3F3F2),
    secondBorder: Color = Color(0xFFADADAD),
    thirdContainer: Color = Color(0xFFE7E7E7),
    thirdBorder: Color = Color(0xFF717171),
): Colors = Colors(
    primary, error,
    element, primaryElement, disabledElement,
    summary, disabledSummary,
    background,
    firstContainer, firstBorder,
    secondContainer, secondBorder,
    thirdContainer, thirdBorder,
)

fun darkColorScheme(
    primary: Color = Color(0xFF7AA9FF),
    error: Color = Color(0xFFFF5E5E),
    element: Color = Color(0xFFFFFFFF),
    primaryElement:  Color = Color(0xFF000000),
    disabledElement: Color = Color(0xFFBBBBBB),
    summary: Color = Color(0xFFDDDDDD),
    disabledSummary: Color = Color(0xFFAAAAAA),
    background: Color = Color(0xFF1D1D18),
    firstContainer: Color = Color(0xFF403E39),
    firstBorder: Color = Color(0xFF808797),
    secondContainer: Color = Color(0xFF686553),
    secondBorder: Color = Color(0xFF8691AD),
    thirdContainer: Color = Color(0xFF635D40),
    thirdBorder: Color = Color(0xFF8CBFC6),
): Colors = Colors(
    primary, error,
    element, primaryElement, disabledElement,
    summary, disabledSummary,
    background,
    firstContainer, firstBorder,
    secondContainer, secondBorder,
    thirdContainer, thirdBorder,
)

@Stable
internal fun Colors.updateColorsFrom(other:Colors) {
    primary = other.primary
    error = other.error
    element = other.element
    primaryElement = other.primaryElement
    disabledElement = other.disabledElement
    summary = other.summary
    disabledSummary = other.disabledSummary
    background = other.background
    firstContainer = other.firstContainer
    firstBorder = other.firstBorder
    secondContainer = other.secondContainer
    secondBorder = other.secondBorder
    thirdContainer = other.thirdContainer
    thirdBorder = other.thirdBorder
}

internal val LocalColors = staticCompositionLocalOf { lightColorScheme() }