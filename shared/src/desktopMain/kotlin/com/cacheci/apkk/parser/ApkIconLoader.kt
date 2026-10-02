package com.cacheci.apkk.parser

import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal class ApkIconLoader(
    private val zip: ZipFile,
    entries: List<ZipEntry>,
    private val resources: ResourceTable,
    private val locale: String,
) {
    private val entriesByName = entries.associateBy(ZipEntry::getName)

    data class Result(val bytes: ByteArray, val mimeType: String, val path: String)

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
        mimeType(path)?.let { return Result(data, it, path) }
        if (!path.endsWith(".xml", ignoreCase = true)) return null
        val node = runCatching { BinaryXmlParser.parse(data) }.getOrNull() ?: return null
        val svg = DrawableRenderer(resources, locale) { reference ->
            val nestedPath = resources.resolveFile(reference, locale)
                ?: reference.takeIf { it.startsWith("res/") }
            nestedPath?.let { loadPath(it, visited.toMutableSet()) }
        }.render(node)
        if (svg != null) return Result(svg.toByteArray(), "image/svg+xml", path)

        return node.resourceReferences()
            .asSequence()
            .mapNotNull { resources.resolveFile(it, locale) }
            .filter { it != path }
            .mapNotNull { loadPath(it, visited.toMutableSet()) }
            .firstOrNull()
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

private class DrawableRenderer(
    private val resources: ResourceTable,
    private val locale: String,
    private val resolveLayer: (String) -> ApkIconLoader.Result?,
) {
    fun render(node: XmlNode): String? = when (node.name) {
        "adaptive-icon" -> renderAdaptive(node)
        "vector" -> renderVector(node)
        "shape" -> renderShape(node)?.let { wrapSvg(it) }
        "layer-list" -> renderLayerList(node)?.let { wrapSvg(it) }
        "inset" -> renderInset(node)?.let { wrapSvg(it) }
        "selector" -> renderSelector(node)
        "bitmap" -> renderBitmap(node)?.let { wrapSvg(it) }
        else -> null
    }

    private fun renderVector(node: XmlNode): String? {
        val width = node.androidAttr("viewportWidth").typedFloat() ?: 24f
        val height = node.androidAttr("viewportHeight").typedFloat() ?: width
        val body = node.children.joinToString("") { renderVectorNode(it) }
        if (body.isEmpty()) return null
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $width $height\">$body</svg>"
    }

    private fun renderVectorNode(node: XmlNode): String {
        if (node.name == "path") {
            val data = node.androidAttr("pathData").xmlEscape()
            if (data.isEmpty()) return ""
            val fill = color(node.androidAttr("fillColor")) ?: "none"
            val stroke = color(node.androidAttr("strokeColor"))
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

    private fun renderAdaptive(node: XmlNode): String? {
        val background = node.children.firstOrNull { it.name == "background" }
        val foreground = node.children.firstOrNull { it.name == "foreground" }
            ?: node.children.firstOrNull { it.name == "monochrome" }
        val backgroundBody = background?.let(::renderContainer)
            ?: "<rect width=\"108\" height=\"108\" fill=\"#f0f0f0\"/>"
        val foregroundBody = foreground?.let(::renderContainer).orEmpty()
        if (backgroundBody.isEmpty() && foregroundBody.isEmpty()) return null
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"18 18 72 72\">$backgroundBody$foregroundBody</svg>"
    }

    private fun renderLayerList(node: XmlNode): String? {
        val body = node.children.filter { it.name == "item" }.joinToString("") { renderContainer(it) }
        return body.takeIf(String::isNotEmpty)
    }

    private fun renderInset(node: XmlNode): String? {
        val content = renderContainer(node)
        if (content.isEmpty()) return null
        val left = node.androidAttr("insetLeft").typedFloat() ?: 0f
        val top = node.androidAttr("insetTop").typedFloat() ?: 0f
        val right = node.androidAttr("insetRight").typedFloat() ?: 0f
        val bottom = node.androidAttr("insetBottom").typedFloat() ?: 0f
        val width = (108f - left - right).coerceAtLeast(0f)
        val height = (108f - top - bottom).coerceAtLeast(0f)
        return "<svg x=\"$left\" y=\"$top\" width=\"$width\" height=\"$height\" viewBox=\"0 0 108 108\">$content</svg>"
    }

    private fun renderSelector(node: XmlNode): String? {
        val items = node.children.filter { it.name == "item" }
        val selected = items.firstOrNull { item -> item.attributes.none { "state_" in it.name } }
            ?: items.firstOrNull()
            ?: return null
        val drawable = selected.androidAttr("drawable")
        if (drawable.isNotEmpty()) {
            val layer = resolveLayer(drawable) ?: return null
            if (layer.mimeType == "image/svg+xml") return layer.bytes.toString(Charsets.UTF_8)
            return wrapSvg(embed(layer))
        }
        return selected.children.firstNotNullOfOrNull(::render)
    }

    private fun renderShape(node: XmlNode): String? {
        val corners = node.children.firstOrNull { it.name == "corners" }
        val radius = corners?.androidAttr("radius")?.typedFloat() ?: 0f
        val oval = node.androidAttr("shape") in setOf("1", "oval")
        fun filledShape(fill: String): String = if (oval) {
            "<ellipse cx=\"54\" cy=\"54\" rx=\"54\" ry=\"54\" fill=\"$fill\"/>"
        } else {
            "<rect width=\"108\" height=\"108\" rx=\"$radius\" fill=\"$fill\"/>"
        }
        val gradient = node.children.firstOrNull { it.name == "gradient" }
        if (gradient != null) {
            val start = color(gradient.androidAttr("startColor")) ?: return null
            val end = color(gradient.androidAttr("endColor")) ?: return null
            val center = color(gradient.androidAttr("centerColor"))
            val id = "gradient"
            val stops = buildString {
                append("<stop offset=\"0%\" stop-color=\"").append(start).append("\"/>")
                if (center != null) append("<stop offset=\"50%\" stop-color=\"").append(center).append("\"/>")
                append("<stop offset=\"100%\" stop-color=\"").append(end).append("\"/>")
            }
            return "<defs><linearGradient id=\"$id\">$stops</linearGradient></defs>" +
                filledShape("url(#$id)")
        }
        val solid = node.children.firstOrNull { it.name == "solid" } ?: return null
        val fill = color(solid.androidAttr("color")) ?: return null
        return filledShape(fill)
    }

    private fun renderBitmap(node: XmlNode): String? {
        val source = node.androidAttr("src").ifEmpty { node.androidAttr("drawable") }
        return resolveLayer(source)?.let(::embed)
    }

    private fun renderContainer(node: XmlNode): String {
        val drawable = node.androidAttr("drawable")
        if (drawable.isNotEmpty()) {
            color(drawable)?.let { return "<rect width=\"108\" height=\"108\" fill=\"$it\"/>" }
            resolveLayer(drawable)?.let { return embed(it) }
        }
        return node.children.joinToString("") { child ->
            render(child)?.let { svg -> embed(ApkIconLoader.Result(svg.toByteArray(), "image/svg+xml", "")) }.orEmpty()
        }
    }

    private fun embed(layer: ApkIconLoader.Result): String {
        val encoded = Base64.getEncoder().encodeToString(layer.bytes)
        return "<image href=\"data:${layer.mimeType};base64,$encoded\" x=\"0\" y=\"0\" width=\"108\" height=\"108\" preserveAspectRatio=\"xMidYMid meet\"/>"
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

    private fun color(value: String): String? {
        val raw = when {
            value.startsWith("#") -> value.removePrefix("#").toLongOrNull(16)?.let { parsed ->
                when (value.length - 1) {
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
            value.startsWith("@0x") && !value.startsWith("@0x7f") && !value.startsWith("@0x01") ->
                value.removePrefix("@0x").toLongOrNull(16)
            else -> resources.resolveColor(value, locale)?.removePrefix("@0x")?.toLongOrNull(16)
        } ?: return null
        val alpha = (raw ushr 24) and 0xff
        val rgb = (raw and 0xffffff).toString(16).padStart(6, '0')
        return if (alpha == 0xffL) "#$rgb" else "#$rgb${alpha.toString(16).padStart(2, '0')}"
    }

    private fun wrapSvg(body: String): String =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 108 108\">$body</svg>"

    private fun String.typedFloat(): Float? = when {
        startsWith("@0x") -> removePrefix("@0x").toLongOrNull(16)?.toInt()?.let(Float::fromBits)
        else -> toFloatOrNull()
    }?.takeIf { it.isFinite() && kotlin.math.abs(it) < 100_000 }

    private fun String.xmlEscape(): String = replace("&", "&amp;").replace("\"", "&quot;")
        .replace("<", "&lt;").replace(">", "&gt;")
}
