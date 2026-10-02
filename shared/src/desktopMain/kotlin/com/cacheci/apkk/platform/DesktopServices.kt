package com.cacheci.apkk.platform

import com.cacheci.apkk.model.AppLanguage
import com.cacheci.apkk.model.AppSettings
import com.cacheci.apkk.model.AppTheme
import java.io.File
import java.util.prefs.Preferences

enum class DesktopPlatform { WINDOWS, MACOS }

object SettingsRepository {
    private val preferences = Preferences.userRoot().node("com/cacheci/apkk")

    fun load(): AppSettings = AppSettings(
        language = runCatching { AppLanguage.valueOf(preferences.get("language", AppLanguage.ZH_CN.name)) }
            .getOrDefault(AppLanguage.ZH_CN),
        theme = runCatching { AppTheme.valueOf(preferences.get("theme", AppTheme.LIGHT.name)) }
            .getOrDefault(AppTheme.LIGHT),
        adbPath = preferences.get("adbPath", ""),
    )

    fun save(settings: AppSettings) {
        preferences.put("language", settings.language.name)
        preferences.put("theme", settings.theme.name)
        preferences.put("adbPath", settings.adbPath)
    }
}

object AdbInstaller {
    fun install(apkPath: String, configuredPath: String, platform: DesktopPlatform): String {
        require(File(apkPath).isFile) { "APK 文件不存在" }
        val adb = resolveAdb(configuredPath, platform)
        val process = ProcessBuilder(adb.absolutePath, "install", "-r", apkPath)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val code = process.waitFor()
        check(code == 0) { output.ifEmpty { "adb install 失败：退出码 $code" } }
        return output.ifEmpty { "安装完成" }
    }

    private fun resolveAdb(configuredPath: String, platform: DesktopPlatform): File {
        if (configuredPath.isNotBlank()) {
            val configured = File(configuredPath.trim())
            require(configured.isFile) { "配置的 adb 不存在：$configuredPath" }
            return configured
        }

        val executableNames = if (platform == DesktopPlatform.WINDOWS) listOf("adb.exe", "adb") else listOf("adb", "adb.exe")
        val pathDirectories = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).filter(String::isNotBlank).map(::File)
        val home = File(System.getProperty("user.home"))
        val commonDirectories = when (platform) {
            DesktopPlatform.WINDOWS -> listOf(home.resolve("AppData/Local/Android/Sdk/platform-tools"))
            DesktopPlatform.MACOS -> listOf(
                home.resolve("Library/Android/sdk/platform-tools"),
                File("/opt/homebrew/bin"), File("/usr/local/bin"), File("/usr/bin"),
            )
        }
        return (pathDirectories + commonDirectories).asSequence()
            .flatMap { directory -> executableNames.asSequence().map(directory::resolve) }
            .firstOrNull(File::isFile)
            ?: error("未找到 adb${if (platform == DesktopPlatform.WINDOWS) ".exe" else ""}，请在设置中配置 ADB 位置")
    }
}
