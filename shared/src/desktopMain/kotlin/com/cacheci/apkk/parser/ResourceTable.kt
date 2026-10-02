package com.cacheci.apkk.parser

internal class ResourceTable private constructor(
    private val values: Map<Long, List<ResourceValue>>,
    val supportedLanguages: List<String>,
) {
    fun resolveString(reference: String, locale: String): String? =
        resolve(reference, locale, Selection.DEFAULT)?.text?.takeUnless(String::looksLikeResourceFile)

    fun resolveFile(reference: String, locale: String): String? {
        if (reference.looksLikeResourceFile()) return reference
        return resolve(reference, locale, Selection.FILE)?.text?.takeIf(String::looksLikeResourceFile)
    }

    fun resolveIcon(reference: String, locale: String): String? =
        resolve(reference, locale, Selection.ICON)?.text?.takeIf(String::looksLikeResourceFile)

    fun resolveColor(reference: String, locale: String): String? {
        if (reference.startsWith("#")) return reference
        return resolve(reference, locale, Selection.DEFAULT)?.let { value ->
            when {
                value.type in COLOR_TYPES -> "@0x${value.data.toString(16).padStart(8, '0')}"
                value.text?.startsWith("#") == true -> value.text
                else -> null
            }
        }
    }

    private fun resolve(
        reference: String,
        locale: String,
        selection: Selection,
        visited: MutableSet<Long> = mutableSetOf(),
    ): ResourceValue? {
        val resourceId = reference.resourceId() ?: return null
        if (!visited.add(resourceId)) return null
        val value = values[resourceId]?.choose(locale, selection) ?: return null
        return if (value.type == TYPE_REFERENCE || value.type == TYPE_DYNAMIC_REFERENCE) {
            resolve(value.reference, locale, selection, visited)
        } else {
            value
        }
    }

    companion object {
        private const val TABLE = 0x0002
        private const val STRING_POOL = 0x0001
        private const val PACKAGE = 0x0200
        private const val TYPE = 0x0201
        private const val ENTRY_FLAG_COMPLEX = 0x0001
        private const val TYPE_FLAG_SPARSE = 0x01
        private const val TYPE_FLAG_OFFSET16 = 0x02

        fun parse(data: ByteArray): ResourceTable {
            require(data.u16(0) == TABLE) { "resources.arsc 不是 Android Resource Table" }
            val values = mutableMapOf<Long, MutableList<ResourceValue>>()
            val languages = sortedSetOf<String>()
            val tableSize = data.u32(4).toInt().coerceAtMost(data.size)
            var globalStrings = emptyList<String>()
            var offset = data.u16(2)

            while (offset + 8 <= tableSize) {
                val chunkType = data.u16(offset)
                val chunkSize = data.u32(offset + 4).toInt()
                if (chunkSize <= 0 || offset + chunkSize > tableSize) break
                when (chunkType) {
                    STRING_POOL -> globalStrings = StringPool.parse(data, offset)
                    PACKAGE -> parsePackage(data, offset, globalStrings, values, languages)
                }
                offset += chunkSize
            }
            return ResourceTable(values, languages.toList())
        }

        private fun parsePackage(
            data: ByteArray,
            packageOffset: Int,
            globalStrings: List<String>,
            values: MutableMap<Long, MutableList<ResourceValue>>,
            languages: MutableSet<String>,
        ) {
            val packageId = data.u32(packageOffset + 8)
            val packageSize = data.u32(packageOffset + 4).toInt()
            val typeStringsOffset = data.u32(packageOffset + 268).toInt()
            val typeStrings = StringPool.parse(data, packageOffset + typeStringsOffset)
            var offset = packageOffset + data.u16(packageOffset + 2)
            while (offset + 8 <= data.size && offset < packageOffset + packageSize) {
                val chunkSize = data.u32(offset + 4).toInt()
                if (chunkSize <= 0 || offset + chunkSize > data.size) break
                if (data.u16(offset) == TYPE) {
                    parseTypeChunk(data, offset, packageId, typeStrings, globalStrings, values, languages)
                }
                offset += chunkSize
            }
        }

        private fun parseTypeChunk(
            data: ByteArray,
            offset: Int,
            packageId: Long,
            typeStrings: List<String>,
            globalStrings: List<String>,
            values: MutableMap<Long, MutableList<ResourceValue>>,
            languages: MutableSet<String>,
        ) {
            val headerSize = data.u16(offset + 2)
            val chunkSize = data.u32(offset + 4).toInt()
            val typeId = data.u8(offset + 8)
            val typeFlags = data.u8(offset + 9)
            val entryCount = data.u32(offset + 12).toInt()
            val entriesStart = data.u32(offset + 16).toInt()
            val configOffset = offset + 20
            val locale = parseLocale(data, configOffset)
            val density = if (data.u32(configOffset).toInt() >= 16) data.u16(configOffset + 14) else 0
            val typeName = typeStrings.getOrNull(typeId - 1).orEmpty()
            if (locale.isNotEmpty()) languages += locale
            val offsetsBase = offset + headerSize
            val entriesBase = offset + entriesStart

            val entryOffsets = when {
                typeFlags and TYPE_FLAG_SPARSE != 0 -> List(entryCount) { sparseIndex ->
                    val itemOffset = offsetsBase + sparseIndex * 4
                    data.u16(itemOffset) to (data.u16(itemOffset + 2).toLong() * 4)
                }
                typeFlags and TYPE_FLAG_OFFSET16 != 0 -> List(entryCount) { entryIndex ->
                    val compactOffset = data.u16(offsetsBase + entryIndex * 2)
                    entryIndex to if (compactOffset == 0xffff) 0xffff_ffffL else compactOffset.toLong() * 4
                }
                else -> List(entryCount) { entryIndex ->
                    entryIndex to data.u32(offsetsBase + entryIndex * 4)
                }
            }

            entryOffsets.forEach { (entryIndex, entryRelative) ->
                if (entryRelative == 0xffff_ffffL) return@forEach
                val entryOffset = entriesBase + entryRelative.toInt()
                if (entryOffset + 8 > offset + chunkSize || entryOffset + 8 > data.size) return@forEach
                val flags = data.u16(entryOffset + 2)
                if (flags and ENTRY_FLAG_COMPLEX != 0) return@forEach
                val entrySize = data.u16(entryOffset)
                val valueOffset = entryOffset + entrySize
                if (valueOffset + 8 > offset + chunkSize || valueOffset + 8 > data.size) return@forEach
                val valueType = data.u8(valueOffset + 3)
                val valueData = data.u32(valueOffset + 4)
                val text = if (valueType == TYPE_STRING) globalStrings.getOrNull(valueData.toInt()) else null
                val resourceId = (packageId shl 24) or (typeId.toLong() shl 16) or entryIndex.toLong()
                values.getOrPut(resourceId, ::mutableListOf) += ResourceValue(
                    locale = locale,
                    density = density,
                    typeName = typeName,
                    type = valueType,
                    data = valueData,
                    text = text,
                )
            }
        }

        private fun parseLocale(data: ByteArray, offset: Int): String {
            if (data.u32(offset).toInt() < 32) return ""
            val language = decodeLanguage(data.u8(offset + 8), data.u8(offset + 9))
            val region = decodeRegion(data.u8(offset + 10), data.u8(offset + 11))
            return when {
                language.isEmpty() -> ""
                region.isEmpty() -> language
                else -> "$language-r$region"
            }
        }

        private fun decodeLanguage(first: Int, second: Int): String {
            if (first == 0 && second == 0) return ""
            if (first and 0x80 != 0) {
                return charArrayOf(
                    ((first and 0x1f) + 'a'.code).toChar(),
                    ((((first and 0x60) shr 5) or ((second and 3) shl 3)) + 'a'.code).toChar(),
                    (((second and 0x7c) shr 2) + 'a'.code).toChar(),
                ).concatToString()
            }
            return byteArrayOf(first.toByte(), second.toByte()).toString(Charsets.UTF_8).trim('\u0000')
        }

        private fun decodeRegion(first: Int, second: Int): String {
            if (first == 0 && second == 0) return ""
            if (first and 0x80 != 0) {
                return charArrayOf(
                    ((first and 0x1f) + '0'.code).toChar(),
                    ((((first and 0x60) shr 5) or ((second and 3) shl 3)) + '0'.code).toChar(),
                    (((second and 0x7c) shr 2) + '0'.code).toChar(),
                ).concatToString()
            }
            return byteArrayOf(first.toByte(), second.toByte()).toString(Charsets.UTF_8).trim('\u0000')
        }
    }
}

private enum class Selection { DEFAULT, FILE, ICON }

private data class ResourceValue(
    val locale: String,
    val density: Int,
    val typeName: String,
    val type: Int,
    val data: Long,
    val text: String?,
) {
    val reference: String get() = "@0x${data.toString(16).padStart(8, '0')}"
}

private fun List<ResourceValue>.choose(preferredLocale: String, selection: Selection): ResourceValue? {
    val normalized = preferredLocale.substringBefore('.').replace('_', '-').replace("-r", "-")
    val exact = filter { it.locale.replace("-r", "-").equals(normalized, ignoreCase = true) }
    val language = normalized.substringBefore('-')
    val languageMatch = filter { it.locale.replace("-r", "-").equals(language, ignoreCase = true) }
    val defaults = filter { it.locale.isEmpty() }
    val candidates = exact.ifEmpty { languageMatch }.ifEmpty { defaults }.ifEmpty { this }
    return candidates.maxByOrNull { if (selection == Selection.ICON) it.iconDensityRank() else it.normalizedDensity() }
}

private fun ResourceValue.normalizedDensity(): Int = when (density) {
    DENSITY_ANY, DENSITY_NONE -> Int.MAX_VALUE
    else -> density
}

private fun ResourceValue.iconDensityRank(): Int = when {
    density == DENSITY_ANY && text?.endsWith(".xml") == true -> 800
    density == 640 -> 700
    density == 480 -> 600
    density == 320 -> 500
    density == 240 -> 400
    density == 160 -> 300
    density == DENSITY_NONE -> 200
    density == DENSITY_ANY -> 100
    else -> density.coerceAtMost(99)
}

private fun String.resourceId(): Long? = when {
    startsWith("@0x") -> substring(3).toLongOrNull(16)
    startsWith("@") -> substring(1).toLongOrNull(16)
    else -> null
}

private fun String.looksLikeResourceFile(): Boolean = startsWith("res/") ||
    endsWith(".xml", ignoreCase = true) || endsWith(".png", ignoreCase = true) ||
    endsWith(".webp", ignoreCase = true) || endsWith(".jpg", ignoreCase = true) ||
    endsWith(".jpeg", ignoreCase = true) || endsWith(".svg", ignoreCase = true)

private const val TYPE_REFERENCE = 0x01
private const val TYPE_DYNAMIC_REFERENCE = 0x07
private const val TYPE_STRING = 0x03
private const val DENSITY_ANY = 0xfffe
private const val DENSITY_NONE = 0xffff
private val COLOR_TYPES = 0x1c..0x1f
