package com.cacheci.apkk.i18n

import com.cacheci.apkk.model.AppLanguage

class Strings(private val language: AppLanguage) {
    private val zh = mapOf(
        "title" to "APK 基础信息查看器",
        "subtitle" to "选择一个 APK，快速查看包名、版本、SDK、权限、组件、ABI 与签名文件。",
        "settings" to "设置",
        "back" to "返回",
        "install" to "安装",
        "installing" to "安装中",
        "installSuccess" to "安装完成",
        "installFailed" to "安装失败",
        "dropHint" to "拖拽 APK 到这里或点击选择文件",
        "parsing" to "解析中",
        "parseFailed" to "解析失败",
        "summary" to "基础信息",
        "permissions" to "权限",
        "components" to "组件",
        "files" to "原生库",
        "language" to "语言",
        "theme" to "主题",
        "themeLight" to "浅色",
        "themeDark" to "深色",
        "themeSystem" to "跟随系统",
        "adbPath" to "ADB 位置",
        "adbPathHint" to "留空则自动查找",
        "empty" to "暂无数据",
        "undeclared" to "未声明",
        "default" to "默认",
        "noPermission" to "未声明权限",
        "noNative" to "未发现 native so",
        "noSignature" to "未发现 META-INF 签名文件",
        "noComponent" to "无",
        "noAbi" to "无 native so",
        "debugDefault" to "false/未声明",
        "fileName" to "文件名",
        "fileSize" to "文件大小",
        "package" to "包名",
        "appName" to "应用名",
        "versionName" to "版本名",
        "versionCode" to "版本号",
        "minSdk" to "最低 SDK",
        "targetSdk" to "目标 SDK",
        "compileSdk" to "编译 SDK",
        "languages" to "支持语言",
        "fileCount" to "文件数量",
    )

    private val en = mapOf(
        "title" to "APK Info Viewer",
        "subtitle" to "Choose an APK to inspect package, version, SDK, permissions, components, ABI, and signatures.",
        "settings" to "Settings", "back" to "Back", "install" to "Install",
        "installing" to "Installing", "installSuccess" to "Installed", "installFailed" to "Install failed",
        "dropHint" to "Drop APK here or click to choose", "parsing" to "Parsing",
        "parseFailed" to "Parse failed", "summary" to "Summary", "permissions" to "Permissions",
        "components" to "Components", "files" to "Native libraries", "language" to "Language",
        "theme" to "Theme", "themeLight" to "Light", "themeDark" to "Dark", "themeSystem" to "System",
        "adbPath" to "ADB path", "adbPathHint" to "Leave empty to auto-detect", "empty" to "No data",
        "undeclared" to "Undeclared", "default" to "Default", "noPermission" to "No permissions declared",
        "noNative" to "No native so found", "noSignature" to "No META-INF signature files found",
        "noComponent" to "None", "noAbi" to "No native so", "debugDefault" to "false/undeclared",
        "fileName" to "File name", "fileSize" to "File size", "package" to "Package",
        "appName" to "App name", "versionName" to "Version name", "versionCode" to "Version code",
        "minSdk" to "Min SDK", "targetSdk" to "Target SDK", "compileSdk" to "Compile SDK",
        "languages" to "Languages", "fileCount" to "File count",
    )

    operator fun get(key: String): String =
        (if (language == AppLanguage.EN_US) en else zh)[key] ?: zh[key] ?: key
}
