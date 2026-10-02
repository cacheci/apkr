package com.cacheci.apkk.i18n

import com.cacheci.apkk.model.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class StringsTest {
    @Test
    fun resolvesBothLanguages() {
        assertEquals("设置", Strings(AppLanguage.ZH_CN)["settings"])
        assertEquals("Settings", Strings(AppLanguage.EN_US)["settings"])
    }
}
