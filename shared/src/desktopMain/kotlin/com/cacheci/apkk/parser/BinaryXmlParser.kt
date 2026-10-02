package com.cacheci.apkk.parser

internal data class XmlNode(
    val name: String,
    val attributes: List<XmlAttribute>,
    val children: MutableList<XmlNode> = mutableListOf(),
) {
    fun attr(name: String): String = attributes.firstOrNull { it.name == name }?.value.orEmpty()
    fun typedAttr(name: String): XmlAttribute? = attributes.firstOrNull { it.name == name }
    fun androidAttr(name: String): String = attr("android:$name").ifEmpty { attr(name) }
    fun childrenNamed(name: String): List<XmlNode> = children.filter { it.name == name }
    fun findAndroidAttr(name: String): String =
        androidAttr(name).ifEmpty { children.firstNotNullOfOrNull { it.findAndroidAttr(name).takeIf(String::isNotEmpty) }.orEmpty() }
}

internal data class XmlAttribute(
    val name: String,
    val value: String,
    val valueType: Int,
    val valueData: Long,
)

internal object BinaryXmlParser {
    private const val STRING_POOL = 0x0001
    private const val XML = 0x0003
    private const val START_ELEMENT = 0x0102
    private const val END_ELEMENT = 0x0103
    private const val RESOURCE_MAP = 0x0180
    private const val TYPE_STRING = 0x03
    private const val TYPE_FLOAT = 0x04
    private const val TYPE_DIMENSION = 0x05
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_INT_BOOLEAN = 0x12
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    fun parse(data: ByteArray): XmlNode {
        require(data.size >= 8) { "AndroidManifest.xml 数据不完整" }
        val firstType = data.u16(0)
        val firstHeader = data.u16(2)
        require(firstType == XML || (firstType == 0 && firstHeader == 8)) {
            "AndroidManifest.xml 不是 Android 二进制 XML"
        }

        var strings = emptyList<String>()
        var resourceIds = emptyList<Long>()
        var root: XmlNode? = null
        val stack = ArrayDeque<XmlNode>()
        var offset = firstHeader

        while (offset + 8 <= data.size) {
            val type = data.u16(offset)
            val headerSize = data.u16(offset + 2)
            val chunkSize = data.u32(offset + 4).toInt()
            if (chunkSize <= 0 || offset + chunkSize > data.size) break

            when (type) {
                STRING_POOL -> strings = StringPool.parse(data, offset)
                RESOURCE_MAP -> resourceIds = (0 until (chunkSize - headerSize) / 4)
                    .map { data.u32(offset + headerSize + it * 4) }
                START_ELEMENT -> stack.addLast(parseStartElement(data, offset, strings, resourceIds))
                END_ELEMENT -> if (stack.isNotEmpty()) {
                    val node = stack.removeLast()
                    if (stack.isEmpty()) root = node else stack.last().children += node
                }
            }
            offset += chunkSize
        }
        return requireNotNull(root) { "AndroidManifest.xml 没有 manifest 根节点" }
    }

    private fun parseStartElement(
        data: ByteArray,
        offset: Int,
        strings: List<String>,
        resourceIds: List<Long>,
    ): XmlNode {
        val name = strings.at(data.u32(offset + 20).toInt())
        val attributeStart = data.u16(offset + 24)
        val attributeSize = data.u16(offset + 26)
        val attributeCount = data.u16(offset + 28)
        val base = offset + 16 + attributeStart
        val attributes = buildList {
            repeat(attributeCount) { index ->
                val item = base + index * attributeSize
                if (item + 20 > data.size) return@repeat
                val namespace = strings.at(data.u32(item).toInt())
                val nameIndex = data.u32(item + 4).toInt()
                val rawIndex = data.u32(item + 8).toInt()
                val valueType = data.u8(item + 15)
                val valueData = data.u32(item + 16)
                val mappedName = resourceIds.getOrNull(nameIndex)?.let(::attributeNameForId).orEmpty()
                val poolName = strings.at(nameIndex)
                val attrName = if (namespace == ANDROID_NS && mappedName in sdkAttributes) {
                    mappedName
                } else {
                    poolName.ifEmpty { mappedName }
                }
                if (attrName.isNotEmpty()) {
                    val key = if (namespace == ANDROID_NS) "android:$attrName" else attrName
                    val raw = strings.at(rawIndex)
                    add(XmlAttribute(key, typedValue(strings, valueType, valueData, raw), valueType, valueData))
                }
            }
        }
        return XmlNode(name, attributes)
    }

    private fun typedValue(strings: List<String>, type: Int, value: Long, raw: String): String {
        if (raw.isNotEmpty()) return raw
        return when (type) {
            TYPE_STRING -> strings.at(value.toInt())
            TYPE_FLOAT -> Float.fromBits(value.toInt()).toString()
            TYPE_DIMENSION -> complexToFloat(value.toInt()).toString()
            TYPE_INT_BOOLEAN -> (value != 0L).toString()
            TYPE_INT_DEC -> value.toString()
            TYPE_INT_HEX -> "0x${value.toString(16).padStart(8, '0')}"
            else -> if (value == 0L) "" else "@0x${value.toString(16).padStart(8, '0')}"
        }
    }

    private fun complexToFloat(value: Int): Float {
        val multipliers = floatArrayOf(1f, 1f / 128f, 1f / 32768f, 1f / 8388608f)
        val mantissa = (value shr 8).shl(8).shr(8)
        return mantissa * multipliers[(value shr 4) and 3]
    }

    private val sdkAttributes = setOf("minSdkVersion", "targetSdkVersion", "compileSdkVersion")

    private fun attributeNameForId(id: Long): String = when (id) {
        0x01010001L -> "label"
        0x01010002L -> "icon"
        0x0101052cL -> "roundIcon"
        0x01010003L -> "name"
        0x0101021bL -> "versionCode"
        0x0101021cL -> "versionName"
        0x0101000fL -> "debuggable"
        0x0101020cL -> "minSdkVersion"
        0x01010270L -> "targetSdkVersion"
        0x01010572L -> "compileSdkVersion"
        0x01010402L -> "drawable"
        0x010103fbL -> "height"
        0x010103fcL -> "width"
        0x01010405L -> "viewportWidth"
        0x01010406L -> "viewportHeight"
        0x01010408L -> "fillColor"
        0x01010409L -> "pathData"
        0x0101040cL -> "strokeColor"
        0x0101040dL -> "strokeWidth"
        0x01010411L -> "fillType"
        0x0101031aL -> "pivotX"
        0x0101031bL -> "pivotY"
        0x01010320L -> "translateX"
        0x01010321L -> "translateY"
        0x0101031cL -> "scaleX"
        0x0101031dL -> "scaleY"
        0x0101031eL -> "rotation"
        0x01010176L -> "insetLeft"
        0x01010177L -> "insetRight"
        0x01010178L -> "insetTop"
        0x01010179L -> "insetBottom"
        0x0101019dL -> "color"
        0x0101019eL -> "startColor"
        0x0101019fL -> "endColor"
        0x010101a0L -> "angle"
        0x0101020bL -> "centerColor"
        0x0101051dL -> "offset"
        else -> "attr_0x${id.toString(16).padStart(8, '0')}"
    }
}

internal object StringPool {
    fun parse(data: ByteArray, offset: Int): List<String> {
        val headerSize = data.u16(offset + 2)
        val count = data.u32(offset + 8).toInt()
        val utf8 = data.u32(offset + 16) and 0x100L != 0L
        val stringsStart = data.u32(offset + 20).toInt()
        val offsetsStart = offset + headerSize
        val base = offset + stringsStart
        return List(count) { index ->
            val cursor = base + data.u32(offsetsStart + index * 4).toInt()
            if (cursor !in data.indices) "" else if (utf8) data.readUtf8PoolString(cursor) else data.readUtf16PoolString(cursor)
        }
    }
}

internal fun ByteArray.u8(offset: Int): Int = getOrNull(offset)?.toInt()?.and(0xff)
    ?: error("二进制数据读取越界")

internal fun ByteArray.u16(offset: Int): Int = u8(offset) or (u8(offset + 1) shl 8)

internal fun ByteArray.u32(offset: Int): Long =
    u8(offset).toLong() or (u8(offset + 1).toLong() shl 8) or
        (u8(offset + 2).toLong() shl 16) or (u8(offset + 3).toLong() shl 24)

private fun List<String>.at(index: Int): String = getOrNull(index).orEmpty()

private fun ByteArray.readUtf8PoolString(start: Int): String {
    var cursor = skipLength8(start)
    val (byteLength, next) = readLength8(cursor)
    cursor = next
    require(cursor + byteLength <= size) { "字符串池 UTF-8 越界" }
    return copyOfRange(cursor, cursor + byteLength).toString(Charsets.UTF_8)
}

private fun ByteArray.readUtf16PoolString(start: Int): String {
    val (charLength, cursor) = readLength16(start)
    require(cursor + charLength * 2 <= size) { "字符串池 UTF-16 越界" }
    return CharArray(charLength) { index -> u16(cursor + index * 2).toChar() }.concatToString()
}

private fun ByteArray.skipLength8(offset: Int): Int = readLength8(offset).second

private fun ByteArray.readLength8(offset: Int): Pair<Int, Int> {
    val first = u8(offset)
    return if (first and 0x80 != 0) (((first and 0x7f) shl 8) or u8(offset + 1)) to offset + 2
    else first to offset + 1
}

private fun ByteArray.readLength16(offset: Int): Pair<Int, Int> {
    val first = u16(offset)
    return if (first and 0x8000 != 0) (((first and 0x7fff) shl 16) or u16(offset + 2)) to offset + 4
    else first to offset + 2
}
