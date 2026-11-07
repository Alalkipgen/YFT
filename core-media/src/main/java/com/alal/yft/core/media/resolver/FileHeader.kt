package com.alal.yft.core.media.resolver

/**
 * What the first bytes of a file say about its picture and sound, read without the media.
 *
 * [Mp4HeaderParser] reads an MP4's boxes and [WebmHeaderParser] a WebM's EBML elements; both
 * answer in this one shape, so a row is named the same way whatever the container. Only values
 * the file states are returned; anything it does not state stays null.
 */
data class FileHeader(
    val width: Int? = null,
    val height: Int? = null,
    val framesPerSecond: Double? = null,
    val durationMillis: Long? = null,
    /** The video codec, for example `avc1`, `hvc1`, `av01`, `vp9` or `vp8`. */
    val videoCodec: String? = null,
    /** The audio codec, `mp4a.40.2` for AAC-LC, `opus` or `vorbis` for a WebM. */
    val audioCodec: String? = null,
    val audioBitrateBitsPerSecond: Long? = null,
    val hasVideo: Boolean = false,
    val hasAudio: Boolean = false,
)
