package com.alal.yft.detection

import android.media.MediaCodecList
import com.alal.yft.core.model.media.MediaVariant
import java.util.Locale

/**
 * Whether this phone has a decoder that plays a video's picture (P6). YouTube's 2K and 4K are
 * VP9 or AV1, which older or cheaper phones may not decode at that size; the download sheet
 * still offers them, with a warning, because the file itself is fine.
 */
fun interface VideoPlaybackSupport {
    /** False only when the phone surely has no decoder for [variant]; unknown codecs play. */
    fun canPlay(variant: MediaVariant): Boolean

    companion object {
        /** Every video plays: for tests and when the phone cannot be asked. */
        val ANY = VideoPlaybackSupport { true }
    }
}

/** One video decoder of the phone: its type, such as `video/x-vnd.on2.vp9`, and its sizes. */
class VideoDecoder(
    val mimeType: String,
    private val playsSize: (width: Int, height: Int) -> Boolean,
    private val playsHeight: (height: Int) -> Boolean,
) {
    /** Whether a [width] × [height] picture fits, either way up; without a width any height. */
    fun plays(width: Int?, height: Int): Boolean = when (width) {
        null -> playsHeight(height)
        else -> playsSize(width, height) || playsSize(height, width)
    }
}

/**
 * The phone's own answer, from its decoder list (read once). A video of a codec YFT cannot name
 * or of unknown size counts as playable, and so does every video when the list cannot be read.
 */
class DeviceVideoPlaybackSupport(
    decoders: () -> List<VideoDecoder> = ::platformVideoDecoders,
) : VideoPlaybackSupport {
    private val decoders: List<VideoDecoder> by lazy(decoders)

    override fun canPlay(variant: MediaVariant): Boolean {
        val height = variant.height?.takeIf { it > 0 } ?: return true
        val mimeType = decoderMimeTypeFor(variant.codecs) ?: return true
        if (decoders.isEmpty()) return true
        val width = variant.width?.takeIf { it > 0 }
        return decoders.any { it.mimeType == mimeType && it.plays(width, height) }
    }

    companion object {
        /** The decoder type for a video's codecs, such as `vp09.00.51.08`; null when unknown. */
        fun decoderMimeTypeFor(codecs: List<String>): String? =
            codecs.firstNotNullOfOrNull { codec ->
                val name = codec.trim().lowercase(Locale.US)
                DECODER_TYPES.entries.firstOrNull { (prefix, _) ->
                    name == prefix || name.startsWith("$prefix.")
                }?.value
            }

        private val DECODER_TYPES = linkedMapOf(
            "vp9" to "video/x-vnd.on2.vp9",
            "vp09" to "video/x-vnd.on2.vp9",
            "av01" to "video/av01",
            "avc1" to "video/avc",
            "avc3" to "video/avc",
            "hvc1" to "video/hevc",
            "hev1" to "video/hevc",
        )
    }
}

/** The phone's video decoders; a failing codec list is an empty one. */
internal fun platformVideoDecoders(): List<VideoDecoder> = runCatching {
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filter { !it.isEncoder }
        .flatMap { info ->
            info.supportedTypes
                .filter { it.startsWith("video/", ignoreCase = true) }
                .mapNotNull { type ->
                    val video = runCatching { info.getCapabilitiesForType(type) }
                        .getOrNull()?.videoCapabilities ?: return@mapNotNull null
                    VideoDecoder(
                        mimeType = type.lowercase(Locale.US),
                        playsSize = { width, height -> video.isSizeSupported(width, height) },
                        playsHeight = { height -> video.supportedHeights.contains(height) },
                    )
                }
        }
}.getOrDefault(emptyList())
