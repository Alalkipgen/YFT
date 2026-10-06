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
        width: Int? = height?.let { it * 16 / 9 },
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
        width = width,
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

    /** P25: YouTube's full ladder, 4K to 144p merged with M4A, the 360p file and two M4As. */
    fun youtubeLadder(): List<MediaCandidate> =
        listOf(2160, 1440, 1080, 720, 480, 240, 144).map { height ->
            video(height, height / 10 * MIB, merged = true)
        } + listOf(video(360, 11 * MIB), audio(128, 4 * MIB), audio(48, 2 * MIB))

    const val FACEBOOK_ID = "facebook:fixture25"

    /**
     * P25: Facebook's HD and SD files as its page lists them: no height, no codecs, no size,
     * named only by the site's word.
     */
    fun facebookFiles(): List<MediaCandidate> = listOf("HD" to 1, "SD" to 2).map { (word, at) ->
        video(null, label = word, videoId = FACEBOOK_ID, index = at).copy(codecs = emptyList())
    }

    /** P25: Facebook's DASH tracks (1080p to 360p, merged with its M4A track) and HD/SD. */
    fun facebookDash(): List<MediaCandidate> =
        listOf(1080, 720, 480, 360).map { height ->
            video(height, merged = true, videoId = FACEBOOK_ID)
                .copy(bitrateBitsPerSecond = height * 2_000L)
        } + facebookFiles() + audio(96, videoId = FACEBOOK_ID)

    const val OTHER_PAGE = "https://videos.example.test/watch/sunrise"

    /**
     * P25: another site's page: its HLS master (1080p, 720p, 480p, sizes estimated from the
     * bitrate) and an MP4 of the same video whose page states 720p but no codecs.
     */
    fun otherSite(): List<SheetSource> {
        val master = video(null, kind = MediaKind.HLS, videoId = null, label = null, index = 1)
            .copy(pageUrl = OTHER_PAGE)
        val file = video(720, 30 * MIB, videoId = null, label = null, index = 2)
            .copy(pageUrl = OTHER_PAGE, codecs = emptyList())
        val stated = resolvedAsset(master).variants.single()
        val ladder = listOf(1080 to 4_000_000L, 720 to 2_500_000L, 480 to 1_200_000L)
            .map { (height, bitrate) ->
                stated.copy(
                    id = "hls-$height",
                    playbackUrl = "${master.mediaUrl}?q=$height",
                    kind = MediaKind.HLS,
                    width = height * 16 / 9,
                    height = height,
                    bitrateBitsPerSecond = bitrate,
                    sizeBytes = bitrate * LENGTH / 8_000,
                    sizeAccuracy = MediaSizeAccuracy.ESTIMATED,
                )
            }
        return listOf(
            SheetSource(master, resolvedAsset(master).copy(variants = ladder), resolved = true),
            SheetSource(file, resolvedAsset(file), resolved = true),
        )
    }

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
        silent: Boolean = false,
    ): MediaAsset {
        val audio = candidate.mimeType?.startsWith("audio/") == true
        val variant = MediaVariant(
            id = "direct-0",
            playbackUrl = candidate.mediaUrl,
            kind = candidate.kind,
            trackType = when {
                audio -> MediaTrackType.AUDIO
                silent -> MediaTrackType.VIDEO
                else -> MediaTrackType.AUDIO_VIDEO
            },
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
