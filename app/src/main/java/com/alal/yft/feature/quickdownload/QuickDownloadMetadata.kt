package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import java.net.URI
import java.util.Locale

/** The site's real whole-file metadata, without requiring a size probe first. */
internal object QuickDownloadMetadata {
    fun asset(candidate: MediaCandidate, now: Long = System.currentTimeMillis()): MediaAsset? {
        if (candidate.kind != MediaKind.DIRECT || candidate.drmHint == true) return null
        val address = runCatching { URI(candidate.mediaUrl) }.getOrNull() ?: return null
        if (!address.scheme.equals("https", true) || address.host == null ||
            address.userInfo != null) return null
        if (candidate.expiresAtEpochMs?.let { it <= now } == true) return null
        val originalMime = candidate.mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.US)
            ?: return null
        val audio = candidate.audioCompanion == null && (originalMime.startsWith("audio/") ||
            candidate.codecs.isNotEmpty() && candidate.codecs.all { it.startsWith("mp4a", true) })
        // A raw generic file/manifest still needs real resolution: never invent its formats.
        if (!audio && (!originalMime.startsWith("video/") ||
                candidate.height == null && candidate.videoId == null)) return null
        val mime = if (audio && originalMime == "video/mp4") "audio/mp4" else originalMime
        // The size is the size the source states, and nothing else. A bitrate and a length are not
        // a size: YouTube states a live format's encoder rate, not what the replay weighs, and the
        // sheet read ~1.7 times the file on the owner's phone. A row the source states no size for
        // says "Size unknown" until its file states one.
        val size = candidate.contentLengthBytes?.takeIf { it > 0 }
        val companion = candidate.audioCompanion
        val facebookEstimate = candidate.videoId?.startsWith("facebook:") == true &&
            candidate.bitrateBitsPerSecond != null
        val variant = MediaVariant(
            id = "direct-0",
            playbackUrl = candidate.mediaUrl,
            kind = MediaKind.DIRECT,
            trackType = if (audio) MediaTrackType.AUDIO else MediaTrackType.AUDIO_VIDEO,
            requestContext = candidate.requestContext,
            mimeType = mime,
            container = when (mime) {
                "video/mp4", "audio/mp4" -> "MP4"
                "video/webm", "audio/webm" -> "WebM"
                "audio/mpeg" -> "MP3"
                else -> null
            },
            codecs = candidate.codecs,
            width = candidate.width,
            height = candidate.height,
            framesPerSecond = candidate.framesPerSecond,
            bitrateBitsPerSecond = candidate.bitrateBitsPerSecond,
            durationMillis = candidate.durationMillis,
            sizeBytes = size,
            sizeAccuracy = when {
                size == null -> null
                companion != null || facebookEstimate -> MediaSizeAccuracy.ESTIMATED
                else -> MediaSizeAccuracy.EXACT
            },
            expiresAtEpochMs = candidate.expiresAtEpochMs,
            audioCompanion = companion,
        )
        return MediaAsset(
            sourcePageUrl = candidate.pageUrl,
            title = candidate.title,
            thumbnailUrl = candidate.thumbnailUrl,
            durationMillis = candidate.durationMillis,
            variants = listOf(variant),
            resolvedAtEpochMs = now,
        )
    }
}
