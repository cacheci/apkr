package com.cacheci.apkk.model

sealed interface AppIconDrawable {
    data class Encoded(
        val bytes: ByteArray,
        val mimeType: String,
    ) : AppIconDrawable

    data class Solid(
        val argb: Long,
    ) : AppIconDrawable

    data class Adaptive(
        val background: AppIconDrawable?,
        val foreground: AppIconDrawable?,
    ) : AppIconDrawable

    data class Layers(
        val items: List<AppIconDrawable>,
    ) : AppIconDrawable

    data class Inset(
        val drawable: AppIconDrawable,
        val left: Float = 0f,
        val top: Float = 0f,
        val right: Float = 0f,
        val bottom: Float = 0f,
    ) : AppIconDrawable
}
