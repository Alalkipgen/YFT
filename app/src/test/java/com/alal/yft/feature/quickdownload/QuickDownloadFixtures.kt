package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroup
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant

internal object QuickDownloadFixtures {
    const val PAGE = "https://m.youtube.com/watch?v=fixture0001"
    const val TITLE = "Ocean waves"
    const val VIDEO_ID = "youtube:fixture0001"
    const val LENGTH = 252_000L
    const val MIB = 1_048_576L

    /** A whole MP4 a site adapter named; [merged] ones carry their sound as a companion. */
    fun video(
        height: Int?,
        bytes: Long? = null,
        merged: Boolean = false,
        fps: Double? = if (height != null) 30.0 else null,
        label: String? = height?.let { "${it}p" },
        title: String? = TITLE,
        kind: MediaKind = MediaKind.DIRECT,
        drm: Boolean? = null,
        videoId: String? = VIDEO_ID,
        index: Int = height ?: label.hashCode(),
        page: String = PAGE,
    ) = MediaCandidate(
        pageUrl = page,
        mediaUrl = "https://media.example.test/video-$index.mp4",
        sources = setOf(CandidateSource.PASTED_URL),
        kind = kind,
        mimeType = if (kind == MediaKind.DIRECT) "video/mp4" else null,
        codecs = if (merged) listOf("avc1.4d401f") else listOf("avc1.42001e", "mp4a.40.2"),
        title = listOfNotNull(title, label).joinToString(" — ").ifEmpty { null },
        durationMillis = LENGTH,
        contentLengthBytes = bytes,
        drmHint = drm,
        audioCompanion = if (merged) companion() else null,
        videoId = videoId,
        width = height?.let { it * 16 / 9 },
        height = height,
        framesPerSecond = fps,
    )

    fun audio(
        kbps: Int,
        bytes: Long? = null,
        mimeType: String = "audio/mp4",
        videoId: String? = VIDEO_ID,
    ) = MediaCandidate(
        pageUrl = PAGE,
        mediaUrl = "https://media.example.test/audio-$kbps.m4a",
        sources = setOf(CandidateSource.PASTED_URL),
        kind = MediaKind.DIRECT,
        mimeType = mimeType,
        codecs = if (mimeType == "audio/mp4") listOf("mp4a.40.2") else listOf("opus"),
        title = "$TITLE — Audio $kbps kbps",
        durationMillis = LENGTH,
        contentLengthBytes = bytes,
        videoId = videoId,
        bitrateBitsPerSecond = kbps * 1_000L,
    )

    fun companion() = CompanionAudio(
        mediaUrl = "https://media.example.test/audio-128.m4a",
        mimeType = "audio/mp4",
        codecs = listOf("mp4a.40.2"),
        requestContext = BrowserRequestContext(PAGE, null, null),
        contentLengthBytes = 4 * MIB,
    )

    /** What a YouTube lookup returns: progressive 360p, merged 480p/720p/1080p and M4A audio. */
    fun youtube(): List<MediaCandidate> = listOf(
        video(1080, 80 * MIB, merged = true),
        video(720, 42 * MIB, merged = true),
        video(480, 18 * MIB, merged = true),
        video(360, 11 * MIB),
        audio(128, 4 * MIB),
        audio(48, 2 * MIB),
    )

    fun group(candidates: List<MediaCandidate>): MediaGroup = MediaGroups.of(candidates).single()

    /** Each candidate looked up like the resolver does for a whole file. */
    fun sources(candidates: List<MediaCandidate>): List<SheetSource> = candidates.map { candidate ->
        SheetSource(candidate, resolvedAsset(candidate), resolved = true)
    }

    fun choices(candidates: List<MediaCandidate>): QuickChoices? =
        QuickDownloadChoices.of(group(candidates), sources(candidates))

    /** One whole-file variant with what the candidate states, as the resolver reads it. */
    fun resolvedAsset(
        candidate: MediaCandidate,
        height: Int? = candidate.height,
        audioBitrate: Long? = null,
    ): MediaAsset {
        val audio = candidate.mimeType?.startsWith("audio/") == true
        val variant = MediaVariant(
            id = "direct-0",
            playbackUrl = candidate.mediaUrl,
            kind = candidate.kind,
            trackType = if (audio) MediaTrackType.AUDIO else MediaTrackType.AUDIO_VIDEO,
            requestContext = candidate.requestContext,
            mimeType = candidate.mimeType,
            codecs = candidate.codecs,
            width = candidate.width ?: height?.let { it * 16 / 9 },
            height = height,
            framesPerSecond = candidate.framesPerSecond,
            bitrateBitsPerSecond = candidate.bitrateBitsPerSecond,
            audioBitrateBitsPerSecond = audioBitrate,
            durationMillis = candidate.durationMillis,
            sizeBytes = candidate.contentLengthBytes,
            sizeAccuracy = candidate.contentLengthBytes?.let {
                if (candidate.audioCompanion != null) {
                    MediaSizeAccuracy.ESTIMATED
                } else {
                    MediaSizeAccuracy.EXACT
                }
            },
            audioCompanion = candidate.audioCompanion,
        )
        return MediaAsset(
            sourcePageUrl = candidate.pageUrl,
            title = candidate.title,
            thumbnailUrl = null,
            durationMillis = candidate.durationMillis,
            variants = listOf(variant),
            resolvedAtEpochMs = 1,
        )
    }
}
