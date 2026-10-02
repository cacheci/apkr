package com.cacheci.apkk.parser

import com.cacheci.apkk.model.ApkInfo
import com.cacheci.apkk.model.TechFeature
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object ApkParser {
    fun parse(path: String, preferredLocale: String = Locale.getDefault().toLanguageTag()): ApkInfo {
        val file = File(path)
        require(file.isFile) { "APK 文件不存在：$path" }

        val standalone = file.inputStream().use { input ->
            val header = ByteArray(8)
            input.read(header) == header.size && header.u16(0) in setOf(0x0000, 0x0003)
        }

        return if (standalone) {
            val manifest = BinaryXmlParser.parse(file.readBytes())
            createInfo(file, manifest, emptyList(), null, null, preferredLocale)
        } else {
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().toList()
                val manifest = findManifest(zip, entries)
                val resources = entries.firstOrNull { it.name == "resources.arsc" }
                    ?.let { runCatching { ResourceTable.parse(zip.read(it)) }.getOrNull() }
                createInfo(file, manifest, entries, zip, resources, preferredLocale)
            }
        }
    }

    private fun findManifest(zip: ZipFile, entries: List<ZipEntry>): XmlNode {
        val candidates = buildList {
            entries.firstOrNull { it.name == "AndroidManifest.xml" }?.let(::add)
            addAll(entries.filter { it.name.endsWith(".xml") && it.name != "AndroidManifest.xml" })
        }
        return candidates.firstNotNullOfOrNull { entry ->
            runCatching { BinaryXmlParser.parse(zip.read(entry)) }
                .getOrNull()?.takeIf { it.name == "manifest" }
        } ?: error("APK 中没有可识别的 AndroidManifest.xml")
    }

    private fun createInfo(
        file: File,
        manifest: XmlNode,
        entries: List<ZipEntry>,
        zip: ZipFile?,
        resources: ResourceTable?,
        preferredLocale: String,
    ): ApkInfo {
        val names = entries.map(ZipEntry::getName)
        val application = manifest.childrenNamed("application").firstOrNull()
        val usesSdk = manifest.childrenNamed("uses-sdk").firstOrNull()
        val appLabel = application?.androidAttr("label").orEmpty()
        val appIcon = application?.androidAttr("icon").orEmpty()
        val roundIcon = application?.androidAttr("roundIcon").orEmpty()
        val iconLoader = if (zip != null && resources != null) {
            ApkIconLoader(zip, entries, resources, preferredLocale)
        } else {
            null
        }
        val icon = iconLoader?.load(appIcon, roundIcon)
        val resolvedIcon = icon?.path
            ?: iconLoader?.resolvePath(appIcon)
            ?: iconLoader?.resolvePath(roundIcon)
            ?: ""
        val nativeLibs = names.filter { it.startsWith("lib/") && it.endsWith(".so") }

        fun sdk(name: String): String = usesSdk?.androidAttr(name).orEmpty()
            .ifEmpty { manifest.androidAttr(name) }
            .ifEmpty { manifest.findAndroidAttr(name) }

        return ApkInfo(
            path = file.absolutePath,
            fileName = file.name,
            size = humanSize(file.length()),
            packageName = manifest.attr("package"),
            versionName = manifest.androidAttr("versionName"),
            versionCode = manifest.androidAttr("versionCode"),
            minSdk = sdk("minSdkVersion"),
            targetSdk = sdk("targetSdkVersion"),
            compileSdk = sdk("compileSdkVersion").ifEmpty { manifest.attr("platformBuildVersionCode") },
            appLabel = appLabel,
            resolvedAppLabel = resources?.resolveString(appLabel, preferredLocale).orEmpty(),
            appIcon = appIcon,
            resolvedAppIcon = resolvedIcon,
            appIconDrawable = icon?.drawable,
            appIconBytes = icon?.bytes,
            appIconMimeType = icon?.mimeType,
            supportedLanguages = resources?.supportedLanguages.orEmpty(),
            debuggable = application?.androidAttr("debuggable").orEmpty(),
            permissions = manifest.childrenNamed("uses-permission")
                .map { it.androidAttr("name") }.filter(String::isNotEmpty).distinct().sorted(),
            activities = application.components("activity"),
            services = application.components("service"),
            receivers = application.components("receiver"),
            providers = application.components("provider"),
            nativeLibs = nativeLibs,
            abis = nativeLibs.mapNotNull { it.split('/').getOrNull(1) }.distinct().sorted(),
            signatures = names.filter { signaturePattern.matches(it) },
            fileCount = names.size,
            techFeatures = if (zip != null) detectTechFeatures(zip, entries, names, nativeLibs) else emptyList(),
        )
    }

    private fun detectTechFeatures(
        zip: ZipFile,
        entries: List<ZipEntry>,
        names: List<String>,
        nativeLibs: List<String>,
    ): List<TechFeature> {
        val dex = entries.filter { it.name.endsWith(".dex") }
            .mapNotNull { runCatching { zip.read(it) }.getOrNull() }

        fun has(vararg patterns: String): Boolean = dex.any { bytes ->
            patterns.any { pattern -> bytes.contains(pattern.toByteArray()) }
        }

        return buildList {
            if (names.any { it.endsWith(".kotlin_module") } || has("Lkotlin/Metadata;", "kotlin/")) {
                add(TechFeature("Kotlin", "kotlin"))
            }
            if (has("androidx/compose/", "androidx.compose.", "ComposerKt")) {
                add(TechFeature("Compose", "compose"))
            }
            if (names.any { "gradle" in it.lowercase() } || has("com.android.tools.build", "Gradle")) {
                add(TechFeature("Gradle", "gradle"))
            }
            if (has("kotlinx/coroutines/", "kotlinx.coroutines.")) add(TechFeature("Coroutines", "coroutines"))
            if (has("androidx/room/", "androidx.room.")) add(TechFeature("Room", "room"))
            if (nativeLibs.isNotEmpty()) add(TechFeature("Native", "native"))
        }
    }

    private fun ByteArray.contains(needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > size) return false
        outer@ for (index in 0..size - needle.size) {
            for (offset in needle.indices) if (this[index + offset] != needle[offset]) continue@outer
            return true
        }
        return false
    }

    private fun ZipFile.read(entry: ZipEntry): ByteArray = getInputStream(entry).use { it.readBytes() }

    private fun XmlNode?.components(name: String): List<String> = this?.childrenNamed(name)
        ?.map { it.androidAttr("name") }?.filter(String::isNotEmpty).orEmpty()

    private fun humanSize(size: Long): String {
        var value = size.toDouble()
        for (unit in listOf("B", "KB", "MB", "GB")) {
            if (value < 1024 || unit == "GB") return if (unit == "B") "$size B" else "%.1f %s".format(value, unit)
            value /= 1024
        }
        return "$size B"
    }

    private val signaturePattern = Regex("(?i)^META-INF/[^/]+\\.(RSA|DSA|EC)$")
}
