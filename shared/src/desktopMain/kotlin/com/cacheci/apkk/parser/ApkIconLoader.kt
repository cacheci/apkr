package com.cacheci.apkk.parser

import com.cacheci.apkk.model.AppIconDrawable
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal class ApkIconLoader(
    private val zip: ZipFile,
    entries: List<ZipEntry>,
    private val resources: ResourceTable,
    private val locale: String,
) {
    private val entriesByName = entries.associateBy(ZipEntry::getName)

    data class Result(
        val drawable: AppIconDrawable,
        val path: String,
        val bytes: ByteArray?,
        val mimeType: String?,
    )

    fun load(vararg references: String): Result? = references.asSequence()
        .filter(String::isNotEmpty)
        .mapNotNull { reference ->
            val path = resources.resolveIcon(reference, locale)
                ?: resources.resolveFile(reference, locale)
                ?: reference.takeIf { it.startsWith("res/") }
            path?.let { loadPath(it, mutableSetOf()) }
        }
        .firstOrNull()

    fun resolvePath(reference: String): String? = resources.resolveIcon(reference, locale)
        ?: resources.resolveFile(reference, locale)

    private fun loadPath(path: String, visited: MutableSet<String>): Result? {
        if (!visited.add(path)) return null
        val entry = entriesByName[path] ?: return null
        val data = zip.getInputStream(entry).use { it.readBytes() }
        mimeType(path)?.let { mimeType ->
            val drawable = AppIconDrawable.Encoded(data, mimeType)
            return Result(drawable, path, data, mimeType)
        }
        if (!path.endsWith(".xml", ignoreCase = true)) return null
        val node = runCatching { BinaryXmlParser.parse(data) }.getOrNull() ?: return null
        decodeNode(node, visited)?.let { drawable ->
            val encoded = drawable as? AppIconDrawable.Encoded
            return Result(drawable, path, encoded?.bytes, encoded?.mimeType)
        }

        return node.resourceReferences()
            .asSequence()
            .mapNotNull { resources.resolveFile(it, locale) }
            .filter { it != path }
            .mapNotNull { loadPath(it, visited.toMutableSet()) }
            .firstOrNull()
    }

    private fun decodeNode(node: XmlNode, visited: Set<String>): AppIconDrawable? = when (node.name) {
        "adaptive-icon" -> decodeAdaptive(node, visited)
        "vector" -> SvgLeafRenderer(::resolveColor).renderVector(node)?.asSvgDrawable()
        "shape" -> SvgLeafRenderer(::resolveColor).renderShape(node)?.asSvgDrawable()
        "layer-list" -> decodeLayerList(node, visited)
        "inset" -> decodeInset(node, visited)
        "selector" -> decodeSelector(node, visited)
        "bitmap" -> decodeReference(node.androidAttr("src").ifEmpty { node.androidAttr("drawable") }, visited)
        else -> decodeContainer(node, visited)
    }

    private fun decodeAdaptive(node: XmlNode, visited: Set<String>): AppIconDrawable? {
        val background = node.children.firstOrNull { it.name == "background" }
            ?.let { decodeContainer(it, visited) }
        val foreground = (node.children.firstOrNull { it.name == "foreground" }
            ?: node.children.firstOrNull { it.name == "monochrome" })
            ?.let { decodeContainer(it, visited) }
        if (background == null && foreground == null) return null
        return AppIconDrawable.Adaptive(background, foreground)
    }

    private fun decodeLayerList(node: XmlNode, visited: Set<String>): AppIconDrawable? {
        val layers = node.children.filter { it.name == "item" }.mapNotNull { item ->
            decodeContainer(item, visited)?.let { drawable -> item.applyInsets(drawable) }
        }
        return layers.takeIf { it.isNotEmpty() }?.let { AppIconDrawable.Layers(it) }
    }

    private fun decodeInset(node: XmlNode, visited: Set<String>): AppIconDrawable? {
        val drawable = decodeContainer(node, visited) ?: return null
        return node.applyInsets(drawable)
    }

    private fun decodeSelector(node: XmlNode, visited: Set<String>): AppIconDrawable? {
        val items = node.children.filter { it.name == "item" }
        val selected = items.firstOrNull { item -> item.attributes.none { "state_" in it.name } }
            ?: items.firstOrNull()
            ?: return null
        return decodeContainer(selected, visited)
    }

    private fun decodeContainer(node: XmlNode, visited: Set<String>): AppIconDrawable? {
        val reference = node.androidAttr("drawable").ifEmpty { node.androidAttr("src") }
        if (reference.isNotEmpty()) decodeReference(reference, visited)?.let { return it }
        return node.children.firstNotNullOfOrNull { decodeNode(it, visited) }
    }

    private fun decodeReference(reference: String, visited: Set<String>): AppIconDrawable? {
        if (reference.isEmpty()) return null
        resolveColor(reference)?.let { return AppIconDrawable.Solid(it) }
        val path = resources.resolveFile(reference, locale)
            ?: reference.takeIf { it.startsWith("res/") }
            ?: return null
        return loadPath(path, visited.toMutableSet())?.drawable
    }

    private fun XmlNode.applyInsets(drawable: AppIconDrawable): AppIconDrawable {
        val left = androidAttr("insetLeft").ifEmpty { androidAttr("left") }.typedFloat() ?: 0f
        val top = androidAttr("insetTop").ifEmpty { androidAttr("top") }.typedFloat() ?: 0f
        val right = androidAttr("insetRight").ifEmpty { androidAttr("right") }.typedFloat() ?: 0f
        val bottom = androidAttr("insetBottom").ifEmpty { androidAttr("bottom") }.typedFloat() ?: 0f
        return if (left == 0f && top == 0f && right == 0f && bottom == 0f) drawable else {
            AppIconDrawable.Inset(drawable, left, top, right, bottom)
        }
    }

    private fun resolveColor(value: String): Long? {
        val encoded = when {
            value.startsWith("#") -> value.removePrefix("#")
            value.startsWith("@0x") && !value.startsWith("@0x7f") && !value.startsWith("@0x01") ->
                value.removePrefix("@0x")
            else -> resources.resolveColor(value, locale)?.removePrefix("@0x")
        } ?: return null
        val parsed = encoded.toLongOrNull(16) ?: return null
        return when (encoded.length) {
            3 -> ((parsed and 0xf00) shl 12) or ((parsed and 0xf00) shl 8) or
                ((parsed and 0x0f0) shl 8) or ((parsed and 0x0f0) shl 4) or
                ((parsed and 0x00f) shl 4) or (parsed and 0x00f) or 0xff000000
            4 -> ((parsed and 0xf000) shl 16) or ((parsed and 0xf000) shl 12) or
                ((parsed and 0x0f00) shl 12) or ((parsed and 0x0f00) shl 8) or
                ((parsed and 0x00f0) shl 8) or ((parsed and 0x00f0) shl 4) or
                ((parsed and 0x000f) shl 4) or (parsed and 0x000f)
            6 -> parsed or 0xff000000
            8 -> parsed
            else -> null
        }
    }

    private fun mimeType(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "jpg", "jpeg" -> "image/jpeg"
        "svg" -> "image/svg+xml"
        else -> null
    }
}

private fun XmlNode.resourceReferences(): List<String> = buildList {
    addAll(attributes.map(XmlAttribute::value).filter { it.startsWith("@0x") })
    children.forEach { addAll(it.resourceReferences()) }
}

private class SvgLeafRenderer(
    private val resolveColor: (String) -> Long?,
) {
    fun renderVector(node: XmlNode): String? {
        val viewportWidth = node.androidAttr("viewportWidth").typedFloat() ?: 24f
        val viewportHeight = node.androidAttr("viewportHeight").typedFloat() ?: viewportWidth
        val width = node.androidAttr("width").typedFloat() ?: viewportWidth
        val height = node.androidAttr("height").typedFloat() ?: viewportHeight
        val body = node.children.joinToString("") { renderVectorNode(it) }
        if (body.isEmpty()) return null
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" " +
            "viewBox=\"0 0 $viewportWidth $viewportHeight\">$body</svg>"
    }

    private fun renderVectorNode(node: XmlNode): String {
        if (node.name == "path") {
            val data = node.androidAttr("pathData").xmlEscape()
            if (data.isEmpty()) return ""
            val fill = resolveColor(node.androidAttr("fillColor"))?.svgColor() ?: "none"
            val stroke = resolveColor(node.androidAttr("strokeColor"))?.svgColor()
            return buildString {
                append("<path d=\"").append(data).append("\" fill=\"").append(fill).append('"')
                node.androidAttr("fillAlpha").typedFloat()?.let { append(" fill-opacity=\"").append(it).append('"') }
                if (stroke != null) append(" stroke=\"").append(stroke).append('"')
                node.androidAttr("strokeWidth").typedFloat()?.let { append(" stroke-width=\"").append(it).append('"') }
                node.androidAttr("strokeAlpha").typedFloat()?.let { append(" stroke-opacity=\"").append(it).append('"') }
                if (node.androidAttr("fillType") in setOf("1", "evenOdd")) {
                    append(" fill-rule=\"evenodd\" clip-rule=\"evenodd\"")
                }
                append("/>")
            }
        }
        if (node.name == "clip-path") return ""
        val content = node.children.joinToString("") { renderVectorNode(it) }
        if (content.isEmpty()) return ""
        val transform = node.svgTransform()
        return if (transform.isEmpty()) content else "<g transform=\"${transform.xmlEscape()}\">$content</g>"
    }

    fun renderShape(node: XmlNode): String? {
        val corners = node.children.firstOrNull { it.name == "corners" }
        val radius = corners?.androidAttr("radius")?.typedFloat() ?: 0f
        val oval = node.androidAttr("shape") in setOf("1", "oval")
        fun filledShape(fill: String): String = if (oval) {
            "<ellipse cx=\"54\" cy=\"54\" rx=\"54\" ry=\"54\" fill=\"$fill\"/>"
        } else {
            "<rect width=\"108\" height=\"108\" rx=\"$radius\" fill=\"$fill\"/>"
        }
        val gradient = node.children.firstOrNull { it.name == "gradient" }
        val body = if (gradient != null) {
            val start = resolveColor(gradient.androidAttr("startColor"))?.svgColor() ?: return null
            val end = resolveColor(gradient.androidAttr("endColor"))?.svgColor() ?: return null
            val center = resolveColor(gradient.androidAttr("centerColor"))?.svgColor()
            val stops = buildString {
                append("<stop offset=\"0%\" stop-color=\"").append(start).append("\"/>")
                if (center != null) append("<stop offset=\"50%\" stop-color=\"").append(center).append("\"/>")
                append("<stop offset=\"100%\" stop-color=\"").append(end).append("\"/>")
            }
            "<defs><linearGradient id=\"gradient\">$stops</linearGradient></defs>" + filledShape("url(#gradient)")
        } else {
            val solid = node.children.firstOrNull { it.name == "solid" } ?: return null
            val fill = resolveColor(solid.androidAttr("color"))?.svgColor() ?: return null
            filledShape(fill)
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 108 108\">$body</svg>"
    }

    private fun XmlNode.svgTransform(): String {
        val translateX = androidAttr("translateX").typedFloat() ?: 0f
        val translateY = androidAttr("translateY").typedFloat() ?: 0f
        val pivotX = androidAttr("pivotX").typedFloat() ?: 0f
        val pivotY = androidAttr("pivotY").typedFloat() ?: 0f
        val rotation = androidAttr("rotation").typedFloat() ?: 0f
        val scaleX = androidAttr("scaleX").typedFloat() ?: 1f
        val scaleY = androidAttr("scaleY").typedFloat() ?: 1f
        return buildList {
            if (translateX != 0f || translateY != 0f) add("translate($translateX $translateY)")
            if (pivotX != 0f || pivotY != 0f) add("translate($pivotX $pivotY)")
            if (rotation != 0f) add("rotate($rotation)")
            if (scaleX != 1f || scaleY != 1f) add("scale($scaleX $scaleY)")
            if (pivotX != 0f || pivotY != 0f) add("translate(${-pivotX} ${-pivotY})")
        }.joinToString(" ")
    }
}

private fun String.asSvgDrawable(): AppIconDrawable =
    AppIconDrawable.Encoded(toByteArray(), "image/svg+xml")

private fun Long.svgColor(): String {
    val alpha = (this ushr 24) and 0xff
    val rgb = (this and 0xffffff).toString(16).padStart(6, '0')
    return if (alpha == 0xffL) "#$rgb" else "#$rgb${alpha.toString(16).padStart(2, '0')}"
}

private fun String.typedFloat(): Float? = when {
    startsWith("@0x") -> removePrefix("@0x").toLongOrNull(16)?.toInt()?.let(Float::fromBits)
    else -> toFloatOrNull()
}?.takeIf { it.isFinite() && kotlin.math.abs(it) < 100_000 }

private fun String.xmlEscape(): String = replace("&", "&amp;").replace("\"", "&quot;")
    .replace("<", "&lt;").replace(">", "&gt;")
