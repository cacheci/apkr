package com.cacheci.apkk.macos

import com.cacheci.apkk.platform.DesktopPlatform
import com.cacheci.apkk.ui.DesktopAppLauncher

fun main(args: Array<String>) {
    DesktopAppLauncher.launch(DesktopPlatform.MACOS, args)
}
