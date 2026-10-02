package com.cacheci.apkk.parser

import org.apache.batik.transcoder.SVGAbstractTranscoder
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.PNGTranscoder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

internal object SvgRasterizer {
    private const val ICON_SIZE = 256f

    fun toPng(svg: ByteArray): ByteArray? = runCatching {
        val transcoder = PNGTranscoder().apply {
            addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, ICON_SIZE)
            addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, ICON_SIZE)
        }
        ByteArrayOutputStream().use { output ->
            ByteArrayInputStream(svg).use { input ->
                transcoder.transcode(TranscoderInput(input), TranscoderOutput(output))
            }
            output.toByteArray()
        }
    }.getOrNull()
}
