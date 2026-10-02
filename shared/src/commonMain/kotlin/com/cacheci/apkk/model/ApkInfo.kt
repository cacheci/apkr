package com.cacheci.apkk.model

data class ApkInfo(
    val path: String,
    val fileName: String,
    val size: String,
    val packageName: String,
    val versionName: String,
    val versionCode: String,
    val minSdk: String,
    val targetSdk: String,
    val compileSdk: String,
    val appLabel: String,
    val resolvedAppLabel: String,
    val appIcon: String,
    val resolvedAppIcon: String,
    val appIconBytes: ByteArray?,
    val appIconMimeType: String?,
    val supportedLanguages: List<String>,
    val debuggable: String,
    val permissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val receivers: List<String>,
    val providers: List<String>,
    val nativeLibs: List<String>,
    val abis: List<String>,
    val signatures: List<String>,
    val fileCount: Int,
    val techFeatures: List<TechFeature>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ApkInfo) return false
        return path == other.path && fileName == other.fileName &&
            packageName == other.packageName && versionCode == other.versionCode
    }

    override fun hashCode(): Int = arrayOf(path, fileName, packageName, versionCode).contentHashCode()
}

data class TechFeature(val name: String, val iconKey: String)

enum class AppLanguage { ZH_CN, EN_US }

enum class AppTheme { LIGHT, DARK, SYSTEM }

data class AppSettings(
    val language: AppLanguage = AppLanguage.ZH_CN,
    val theme: AppTheme = AppTheme.LIGHT,
    val adbPath: String = "",
)
