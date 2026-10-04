package com.alal.yft.core.model.media

import java.util.Locale

/**
 * Marks a variant whose download is converted to MP3 on the phone (T18).
 *
 * The variant downloads the AAC file of [sourceVariantId] and encodes it as constant bitrate MP3
 * at [bitrateKbps]; previews play that source.
 */
data class Mp3Conversion(
    val bitrateKbps: Int,
    val sourceVariantId: String,
) {
    init {
        require(bitrateKbps in Mp3Variants.BITRATES_KBPS)
        require(sourceVariantId.isNotBlank())
    }
}

/**
 * Adds the MP3 choices to a resolved asset. Pure, so the rules are unit-tested.
 *
 * Only a whole AAC audio file (M4A) can be converted: the phone decodes it with MediaCodec and
 * LAME encodes the MP3. The best such file is the source of both MP3 bitrates.
 */
object Mp3Variants {
    const val MIME_TYPE = "audio/mpeg"
    const val CONTAINER = "mp3"
    const val CODEC = "mp3"

    /** Offered bitrates, best first. */
    val BITRATES_KBPS = listOf(192, 128)

    /** True for a supported, whole-file AAC audio variant the phone can convert. */
    fun isAacSource(variant: MediaVariant): Boolean {
        if (variant.mp3 != null || variant.audioCompanion != null) return false
        if (variant.kind != MediaKind.DIRECT || variant.trackType != MediaTrackType.AUDIO) {
            return false
        }
        if (variant.support != VariantSupport.SUPPORTED) return false
        val codecs = variant.codecs.map { it.trim().lowercase(Locale.US) }
        if (codecs.isNotEmpty()) return codecs.all { it.startsWith(AAC_CODEC_PREFIX) }
        val mime = variant.mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.US)
        val container = variant.container?.trim()?.trimStart('.')?.lowercase(Locale.US)
        return mime in AAC_MIMES || container in AAC_CONTAINERS
    }

    /** The AAC variant MP3s are made from: the highest stated bitrate, else the first. */
    fun sourceOf(variants: List<MediaVariant>): MediaVariant? {
        val sources = variants.filter(::isAacSource)
        return sources.maxByOrNull { it.bitrateBitsPerSecond ?: 0L } ?: sources.firstOrNull()
    }

    /** The MP3 variant of [source] at [bitrateKbps], or null when it cannot be converted. */
    fun of(
        source: MediaVariant,
        bitrateKbps: Int,
        assetDurationMillis: Long? = null,
    ): MediaVariant? {
        if (!isAacSource(source) || bitrateKbps !in BITRATES_KBPS) return null
        val duration = (source.durationMillis ?: assetDurationMillis)?.takeIf { it > 0 }
        val estimate = duration?.let { it * bitrateKbps / BITS_PER_BYTE }
        return source.copy(
            id = "${source.id}$ID_INFIX$bitrateKbps",
            manifestVariantId = null,
            label = "MP3 $bitrateKbps kbps",
            mimeType = MIME_TYPE,
            container = CONTAINER,
            codecs = listOf(CODEC),
            bitrateBitsPerSecond = bitrateKbps * 1_000L,
            durationMillis = duration,
            sizeBytes = estimate,
            sizeAccuracy = estimate?.let { MediaSizeAccuracy.ESTIMATED },
            mp3 = Mp3Conversion(bitrateKbps = bitrateKbps, sourceVariantId = source.id),
        )
    }

    /** [asset] with an MP3 variant per bitrate after its best AAC file; unchanged without one. */
    fun addTo(asset: MediaAsset): MediaAsset {
        if (asset.variants.any { it.mp3 != null }) return asset
        val source = sourceOf(asset.variants) ?: return asset
        val mp3 = BITRATES_KBPS.mapNotNull { of(source, it, asset.durationMillis) }
            .filter { candidate -> asset.variants.none { it.id == candidate.id } }
        if (mp3.isEmpty()) return asset
        val index = asset.variants.indexOf(source) + 1
        val variants = asset.variants.toMutableList().apply { addAll(index, mp3) }
        return asset.copy(variants = variants)
    }

    private const val ID_INFIX = "-mp3-"
    private const val BITS_PER_BYTE = 8
    private const val AAC_CODEC_PREFIX = "mp4a"
    private val AAC_MIMES = setOf("audio/mp4", "audio/x-m4a", "audio/m4a", "audio/aac")
    private val AAC_CONTAINERS = setOf("m4a", "aac")
}
