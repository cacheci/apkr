package com.cacheci.apkk.windows

import com.cacheci.apkk.platform.DesktopPlatform
import com.cacheci.apkk.ui.DesktopAppLauncher

fun main(args: Array<String>) {
    DesktopAppLauncher.launch(DesktopPlatform.WINDOWS, args)
}
