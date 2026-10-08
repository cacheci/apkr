package com.cacheci.apkk.parser

import com.cacheci.apkk.model.ApkInfo
import com.cacheci.apkk.model.BuildFeatures
import com.cacheci.apkk.model.ComponentInfo
import com.cacheci.apkk.model.IntentFilterInfo
import com.cacheci.apkk.model.ManifestFeature
import com.cacheci.apkk.model.TechFeature
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object ApkParser {
    fun parse(path: String, preferredLocale: String = Locale.getDefault().toLanguageTag()): ApkInfo {
        val file = File(path)
        require(file.isFile) { "APK 文件不存在：$path" }
        require(file.extension.equals("apk", ignoreCase = true)) { "只支持解析 .apk 文件：$path" }

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
        val nativeLibraryDetails = if (zip != null) {
            ApkBinaryInspector.nativeLibraries(file, zip, entries)
        } else {
            emptyList()
        }
        val signatureDetails = if (zip != null) {
            ApkBinaryInspector.signatures(file, zip, entries)
        } else {
            emptyList()
        }
        val componentDetails = application?.let(::componentDetails).orEmpty()
        val manifestMetadata = application?.childrenNamed("meta-data")
            ?.mapNotNull { node ->
                node.androidAttr("name").takeIf(String::isNotEmpty)?.let { name ->
                    com.cacheci.apkk.model.ManifestMetadata(name, node.androidAttr("value").ifEmpty { node.androidAttr("resource") })
                }
            }.orEmpty()
        val buildFeatures = if (zip != null) detectBuildFeatures(zip, entries) else BuildFeatures()
        val usesFeatures = manifest.childrenNamed("uses-feature").mapNotNull { node ->
            node.androidAttr("name").takeIf(String::isNotEmpty)?.let { name ->
                ManifestFeature(name, node.androidAttr("required"), node.androidAttr("version"))
            }
        }
        val usesLibraries = manifest.childrenNamed("uses-library")
            .map { it.androidAttr("name") }.filter(String::isNotEmpty)
        val queryEntries = manifest.childrenNamed("queries").flatMap { query ->
            query.children.flatMap { node ->
                when (node.name) {
                    "package" -> listOf("package:${node.androidAttr("name")}")
                    "provider" -> listOf("provider:${node.androidAttr("authorities")}")
                    "intent" -> node.childrenNamed("action").map { "action:${it.androidAttr("name")}" }
                    else -> emptyList()
                }
            }
        }.filter { !it.endsWith(":") }

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
            permissions = manifest.children.filter { it.name == "uses-permission" || it.name.startsWith("uses-permission-") }
                .map { it.androidAttr("name") }.filter(String::isNotEmpty).distinct().sorted(),
            activities = application.components("activity"),
            services = application.components("service"),
            receivers = application.components("receiver"),
            providers = application.components("provider"),
            nativeLibs = nativeLibs,
            abis = nativeLibs.mapNotNull { it.split('/').getOrNull(1) }.distinct().sorted(),
            signatures = (names.filter { signaturePattern.matches(it) } + signatureDetails.map { it.scheme }).distinct(),
            fileCount = names.size,
            techFeatures = if (zip != null) detectTechFeatures(zip, entries, names, nativeLibs, manifest) else emptyList(),
            manifestMetadata = manifestMetadata,
            componentDetails = componentDetails,
            nativeLibraryDetails = nativeLibraryDetails,
            signatureDetails = signatureDetails,
            buildFeatures = buildFeatures,
            usesFeatures = usesFeatures,
            usesLibraries = usesLibraries,
            queryEntries = queryEntries,
            sharedUserId = manifest.androidAttr("sharedUserId"),
            overlayTargetPackage = manifest.androidAttr("targetPackage"),
        )
    }

    private fun detectTechFeatures(
        zip: ZipFile,
        entries: List<ZipEntry>,
        names: List<String>,
        nativeLibs: List<String>,
        manifest: XmlNode,
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
            if (nativeLibs.any { it.substringAfterLast('/') == "libflutter.so" } || has("Lio/flutter/FlutterInjector;")) {
                add(TechFeature("Flutter", "flutter"))
            }
            if (nativeLibs.any { it.substringAfterLast('/') in setOf("libunity.so", "libil2cpp.so") } || has("Lcom/unity3d/")) {
                add(TechFeature("Unity", "unity"))
            }
            if (has("okhttp3/", "com/squareup/okhttp")) add(TechFeature("OkHttp", "okhttp"))
            if (has("retrofit2/", "Lretrofit2/")) add(TechFeature("Retrofit", "retrofit"))
            if (has("com/google/firebase/", "com.google.firebase.")) add(TechFeature("Firebase", "firebase"))
            if (manifest.children.any { it.name == "uses-feature" && it.androidAttr("name") == "android.hardware.vulkan.version" }) {
                add(TechFeature("Vulkan", "vulkan"))
            }
        }
    }

    private fun detectBuildFeatures(zip: ZipFile, entries: List<ZipEntry>): BuildFeatures {
        val names = entries.map(ZipEntry::getName)
        val tooling = entries.firstOrNull { it.name == "kotlin-tooling-metadata.json" }
            ?.let { runCatching { zip.read(it).toString(Charsets.UTF_8) }.getOrNull() }
        val composeVersion = entries.firstNotNullOfOrNull { entry ->
            if (!entry.name.startsWith("META-INF/androidx.compose.") || !entry.name.endsWith(".version")) return@firstNotNullOfOrNull null
            runCatching { zip.read(entry).toString(Charsets.UTF_8).trim().takeIf(String::isNotEmpty) }.getOrNull()
        }
        val agpMetadata = entries.firstOrNull {
            it.name == "META-INF/com/android/build/gradle/app-metadata.properties" ||
                it.name == "BUNDLE-METADATA/com.android.tools.build.gradle/app-metadata.properties"
        }?.let { runCatching { zip.read(it).toString(Charsets.UTF_8) }.getOrNull() }
        return BuildFeatures(
            kotlinDetected = names.any { it.endsWith(".kotlin_module") } || tooling?.contains("KotlinAndroidPluginWrapper") == true,
            kotlinVersion = tooling?.property("buildPluginVersion"),
            composeDetected = composeVersion != null,
            composeVersion = composeVersion,
            gradleVersion = tooling?.property("buildSystemVersion"),
            agpVersion = agpMetadata?.property("androidGradlePluginVersion"),
        )
    }

    private fun String.property(name: String): String? = lineSequence()
        .firstOrNull { it.substringBefore('=').trim() == name }
        ?.substringAfter('=', "")
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    private fun componentDetails(application: XmlNode): List<ComponentInfo> =
        listOf("activity", "activity-alias", "service", "receiver", "provider").flatMap { type ->
            application.childrenNamed(type).map { node ->
                ComponentInfo(
                    type = type,
                    name = node.androidAttr("name"),
                    exported = node.androidAttr("exported"),
                    permission = node.androidAttr("permission"),
                    process = node.androidAttr("process"),
                    intentFilters = node.childrenNamed("intent-filter").map { filter ->
                        IntentFilterInfo(
                            actions = filter.childrenNamed("action").map { it.androidAttr("name") }.filter(String::isNotEmpty),
                            categories = filter.childrenNamed("category").map { it.androidAttr("name") }.filter(String::isNotEmpty),
                            data = filter.childrenNamed("data").map { data ->
                                listOf("scheme", "host", "port", "path", "pathPrefix", "pathPattern", "mimeType")
                                    .mapNotNull { key -> data.androidAttr(key).takeIf(String::isNotEmpty)?.let { "$key=$it" } }
                                    .joinToString(",")
                            }.filter(String::isNotEmpty),
                        )
                    },
                )
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
