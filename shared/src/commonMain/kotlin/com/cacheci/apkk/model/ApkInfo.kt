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
    val appIconDrawable: AppIconDrawable?,
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
    val manifestMetadata: List<ManifestMetadata> = emptyList(),
    val componentDetails: List<ComponentInfo> = emptyList(),
    val nativeLibraryDetails: List<NativeLibraryInfo> = emptyList(),
    val signatureDetails: List<SignatureInfo> = emptyList(),
    val buildFeatures: BuildFeatures = BuildFeatures(),
    val usesFeatures: List<ManifestFeature> = emptyList(),
    val usesLibraries: List<String> = emptyList(),
    val queryEntries: List<String> = emptyList(),
    val sharedUserId: String = "",
    val overlayTargetPackage: String = "",
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

data class ManifestMetadata(
    val name: String,
    val value: String,
)

data class ManifestFeature(
    val name: String,
    val required: String,
    val version: String,
)

data class IntentFilterInfo(
    val actions: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val data: List<String> = emptyList(),
)

data class ComponentInfo(
    val type: String,
    val name: String,
    val exported: String,
    val permission: String,
    val process: String,
    val intentFilters: List<IntentFilterInfo> = emptyList(),
)

data class NativeLibraryInfo(
    val path: String,
    val abi: String,
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val compressionMethod: Int,
    val zipDataOffset: Long?,
    val zipAlignedTo4K: Boolean?,
    val zipAlignedTo16K: Boolean?,
    val elfClass: String?,
    val elfMachine: String?,
    val elfEndianness: String?,
    val stripped: Boolean?,
    val loadSegmentAlignment: Long?,
    val supports16KPageSize: Boolean?,
)

data class SignatureInfo(
    val scheme: String,
    val verificationSuccessful: Boolean? = null,
    val signatureAlgorithmId: Int? = null,
    val publicKeyFormat: String? = null,
    val publicKeyAlgorithm: String? = null,
    val publicKeyAlgorithmOid: String? = null,
    val issuer: String? = null,
    val subject: String? = null,
    val certificateSha256: List<String> = emptyList(),
    val certificateSha1: List<String> = emptyList(),
    val algorithm: String? = null,
    val validFrom: String? = null,
    val validUntil: String? = null,
    val certificateValidNow: Boolean? = null,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

data class BuildFeatures(
    val kotlinDetected: Boolean = false,
    val kotlinVersion: String? = null,
    val composeDetected: Boolean = false,
    val composeVersion: String? = null,
    val gradleVersion: String? = null,
    val agpVersion: String? = null,
)

enum class AppLanguage { ZH_CN, EN_US }

enum class AppColorTheme { LIGHT, DARK, SYSTEM }

data class AppSettings(
    val language: AppLanguage = AppLanguage.ZH_CN,
    val theme: AppColorTheme = AppColorTheme.LIGHT,
    val adbPath: String = "",
)
