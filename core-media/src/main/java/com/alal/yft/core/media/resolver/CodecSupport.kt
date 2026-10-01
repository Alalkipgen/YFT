package com.alal.yft.core.media.resolver

internal object CodecSupport {
    private val videoPrefixes = setOf(
        "av01",
        "avc1",
        "avc3",
        "hev1",
        "hvc1",
        "vp09",
        "vp9",
    )
    private val audioPrefixes = setOf(
        "ac-3",
        "ec-3",
        "flac",
        "mp3",
        "mp4a",
        "opus",
        "vorbis",
    )

    fun isSupported(codecs: List<String>): Boolean =
        codecs.isEmpty() || codecs.all { codec ->
            codecPrefix(codec) in videoPrefixes || codecPrefix(codec) in audioPrefixes
        }

    fun hasVideo(codecs: List<String>): Boolean =
        codecs.any { codecPrefix(it) in videoPrefixes }

    fun hasAudio(codecs: List<String>): Boolean =
        codecs.any { codecPrefix(it) in audioPrefixes }

    fun audioCodecs(codecs: List<String>): List<String> =
        codecs.filter { codecPrefix(it) in audioPrefixes }

    private fun codecPrefix(codec: String): String =
        codec.trim().lowercase().substringBefore('.')
}