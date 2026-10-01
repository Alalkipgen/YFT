package com.alal.yft.core.media.resolver

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.VariantSupport
import java.net.URI
import java.util.Locale

internal object HlsManifestParser {
    fun parse(
        manifest: String,
        manifestUrl: String,
        requestContext: BrowserRequestContext,
        expiresAtEpochMs: Long?,
    ): ManifestParseResult {
        val lines = manifest.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        if (lines.firstOrNull() != "#EXTM3U") return ManifestParseResult.Malformed
        if (lines.any(::isProtectedKey)) return ManifestParseResult.DrmProtected

        val streams = mutableListOf<StreamEntry>()
        val audioRenditions = mutableListOf<Map<String, String>>()
        var durationSeconds = 0.0
        var sawDuration = false
        var pendingStream: Map<String, String>? = null

        lines.drop(1).forEach { line ->
            when {
                line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true) -> {
                    pendingStream = parseAttributes(line.substringAfter(':'))
                }
                pendingStream != null && !line.startsWith('#') -> {
                    streams += StreamEntry(
                        attributes = checkNotNull(pendingStream),
                        uri = line,
                    )
                    pendingStream = null
                }
                line.startsWith("#EXT-X-MEDIA:", ignoreCase = true) -> {
                    val attributes = parseAttributes(line.substringAfter(':'))
                    if (attributes["TYPE"].equals("AUDIO", ignoreCase = true)) {
                        audioRenditions += attributes
                    }
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    line.substringAfter(':')
                        .substringBefore(',')
                        .toDoubleOrNull()
                        ?.takeIf { it >= 0 }
                        ?.let {
                            durationSeconds += it
                            sawDuration = true
                        }
                }
            }
        }
        if (pendingStream != null) return ManifestParseResult.Malformed

        val durationMillis = durationSeconds
            .takeIf { sawDuration }
            ?.times(MILLIS_PER_SECOND)
            ?.toLong()

        if (streams.isEmpty() && audioRenditions.isEmpty()) {
            if (!sawDuration && lines.none { it.startsWith("#EXT-X-TARGETDURATION:") }) {
                return ManifestParseResult.Malformed
            }
            return ManifestParseResult.Parsed(
                variants = listOf(
                    MediaVariant(
                        id = "hls-media-0",
                        playbackUrl = manifestUrl,
                        kind = MediaKind.HLS,
                        trackType = MediaTrackType.AUDIO_VIDEO,
                        requestContext = requestContext,
                        label = "HLS stream",
                        mimeType = HLS_MIME_TYPE,
                        container = "HLS",
                        durationMillis = durationMillis,
                        expiresAtEpochMs = expiresAtEpochMs,
                    ),
                ),
                durationMillis = durationMillis,
            )
        }

        val variants = buildList {
            streams.forEachIndexed { index, stream ->
                val codecs = stream.attributes["CODECS"].toCodecs()
                val resolution = stream.attributes["RESOLUTION"].toResolution()
                val bitrate = (
                    stream.attributes["AVERAGE-BANDWIDTH"]
                        ?: stream.attributes["BANDWIDTH"]
                    )?.toLongOrNull()?.takeIf { it > 0 }
                val audioGroupId = stream.attributes["AUDIO"]
                val trackType = when {
                    audioGroupId != null && CodecSupport.hasVideo(codecs) ->
                        MediaTrackType.VIDEO
                    CodecSupport.hasVideo(codecs) && CodecSupport.hasAudio(codecs) ->
                        MediaTrackType.AUDIO_VIDEO
                    CodecSupport.hasVideo(codecs) -> MediaTrackType.VIDEO
                    CodecSupport.hasAudio(codecs) -> MediaTrackType.AUDIO
                    audioGroupId != null -> MediaTrackType.VIDEO
                    else -> MediaTrackType.AUDIO_VIDEO
                }
                val estimate = estimateSize(bitrate, durationMillis)
                add(
                    MediaVariant(
                        id = "hls-stream-$index",
                        playbackUrl = resolve(manifestUrl, stream.uri)
                            ?: return ManifestParseResult.Malformed,
                        kind = MediaKind.HLS,
                        trackType = trackType,
                        requestContext = requestContext,
                        label = qualityLabel(
                            height = resolution?.second,
                            bitrate = bitrate,
                            fallback = "HLS variant ${index + 1}",
                        ),
                        mimeType = HLS_MIME_TYPE,
                        container = "HLS",
                        codecs = codecs,
                        width = resolution?.first,
                        height = resolution?.second,
                        framesPerSecond = stream.attributes["FRAME-RATE"]
                            ?.toDoubleOrNull()
                            ?.takeIf { it > 0 },
                        bitrateBitsPerSecond = bitrate,
                        durationMillis = durationMillis,
                        sizeBytes = estimate.bytes,
                        sizeAccuracy = estimate.accuracy,
                        audioGroupId = audioGroupId,
                        support = codecs.toSupport(),
                        expiresAtEpochMs = expiresAtEpochMs,
                    ),
                )
            }

            val audioCodecsByGroup = streams
                .groupBy { it.attributes["AUDIO"] }
                .mapValues { (_, entries) ->
                    entries.flatMap { it.attributes["CODECS"].toCodecs() }
                        .let(CodecSupport::audioCodecs)
                        .distinct()
                }
            audioRenditions.forEachIndexed { index, attributes ->
                val uri = attributes["URI"] ?: return@forEachIndexed
                val groupId = attributes["GROUP-ID"]
                val codecs = audioCodecsByGroup[groupId].orEmpty()
                add(
                    MediaVariant(
                        id = "hls-audio-$index",
                        playbackUrl = resolve(manifestUrl, uri)
                            ?: return ManifestParseResult.Malformed,
                        kind = MediaKind.HLS,
                        trackType = MediaTrackType.AUDIO,
                        requestContext = requestContext,
                        label = attributes["NAME"] ?: "Audio ${index + 1}",
                        mimeType = HLS_MIME_TYPE,
                        container = "HLS",
                        codecs = codecs,
                        durationMillis = durationMillis,
                        language = attributes["LANGUAGE"],
                        audioGroupId = groupId,
                        support = codecs.toSupport(),
                        expiresAtEpochMs = expiresAtEpochMs,
                    ),
                )
            }
        }

        if (variants.isEmpty()) return ManifestParseResult.Malformed
        return ManifestParseResult.Parsed(variants, durationMillis)
    }

    private fun isProtectedKey(line: String): Boolean {
        if (
            !line.startsWith("#EXT-X-KEY:", ignoreCase = true) &&
            !line.startsWith("#EXT-X-SESSION-KEY:", ignoreCase = true)
        ) {
            return false
        }
        val method = parseAttributes(line.substringAfter(':'))["METHOD"]
        return !method.equals("NONE", ignoreCase = true)
    }

    private fun parseAttributes(raw: String): Map<String, String> {
        val output = linkedMapOf<String, String>()
        var start = 0
        var inQuotes = false
        val pieces = mutableListOf<String>()
        raw.forEachIndexed { index, character ->
            when (character) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) {
                    pieces += raw.substring(start, index)
                    start = index + 1
                }
            }
        }
        pieces += raw.substring(start)
        pieces.forEach { piece ->
            val key = piece.substringBefore('=', missingDelimiterValue = "")
                .trim()
                .uppercase(Locale.US)
            val value = piece.substringAfter('=', missingDelimiterValue = "")
                .trim()
                .removeSurrounding("\"")
            if (key.isNotEmpty() && value.isNotEmpty()) output[key] = value
        }
        return output
    }

    private fun String?.toCodecs(): List<String> = this
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        .orEmpty()

    private fun String?.toResolution(): Pair<Int, Int>? {
        val width = this?.substringBefore('x')?.toIntOrNull()
        val height = this?.substringAfter('x', missingDelimiterValue = "")?.toIntOrNull()
        return if (width != null && width > 0 && height != null && height > 0) {
            width to height
        } else {
            null
        }
    }

    private fun List<String>.toSupport(): VariantSupport =
        if (CodecSupport.isSupported(this)) {
            VariantSupport.SUPPORTED
        } else {
            VariantSupport.UNSUPPORTED_CODEC
        }

    private fun resolve(base: String, child: String): String? = runCatching {
        URI(base).resolve(child).toString()
    }.getOrNull()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    private fun qualityLabel(height: Int?, bitrate: Long?, fallback: String): String = when {
        height != null -> "${height}p"
        bitrate != null -> "${bitrate / 1_000} kbps"
        else -> fallback
    }

    private data class StreamEntry(
        val attributes: Map<String, String>,
        val uri: String,
    )

    private const val HLS_MIME_TYPE = "application/x-mpegURL"
    private const val MILLIS_PER_SECOND = 1_000.0
}