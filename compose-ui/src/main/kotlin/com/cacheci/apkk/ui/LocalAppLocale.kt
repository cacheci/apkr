package com.cacheci.apkk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

internal object LocalAppLocale {
    private var defaultLocale: Locale? = null
    private val compositionLocal = staticCompositionLocalOf { Locale.getDefault().toLanguageTag() }

    @Composable
    infix fun provides(languageTag: String?): ProvidedValue<*> {
        if (defaultLocale == null) defaultLocale = Locale.getDefault()
        val locale = languageTag?.let(Locale::forLanguageTag) ?: defaultLocale!!
        Locale.setDefault(locale)
        return compositionLocal.provides(locale.toLanguageTag())
    }
}
