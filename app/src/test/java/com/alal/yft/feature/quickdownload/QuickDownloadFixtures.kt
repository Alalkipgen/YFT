package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind

internal object QuickDownloadFixtures {
    const val PAGE = "https://m.youtube.com/watch?v=fixture0001"
    const val TITLE = "Ocean waves"
    const val MIB = 1_048_576L

    fun video(
        label: String?,
        bytes: Long? = null,
        title: String? = TITLE,
        merged: Boolean = false,
        kind: MediaKind = MediaKind.DIRECT,
        drm: Boolean? = null,
        index: Int = label.hashCode(),
    ) = MediaCandidate(
        pageUrl = PAGE,
        mediaUrl = "https://media.example.test/video-$index.mp4",
        sources = setOf(CandidateSource.PASTED_URL),
        kind = kind,
        mimeType = if (kind == MediaKind.DIRECT) "video/mp4" else null,
        title = listOfNotNull(title, label).joinToString(" — ").ifEmpty { null },
        durationMillis = 252_000,
        contentLengthBytes = bytes,
        drmHint = drm,
        audioCompanion = if (merged) companion() else null,
    )

    fun audio(kbps: Int, bytes: Long? = null, mimeType: String = "audio/mp4") = MediaCandidate(
        pageUrl = PAGE,
        mediaUrl = "https://media.example.test/audio-$kbps.m4a",
        sources = setOf(CandidateSource.PASTED_URL),
        kind = MediaKind.DIRECT,
        mimeType = mimeType,
        title = "$TITLE — Audio $kbps kbps",
        durationMillis = 252_000,
        contentLengthBytes = bytes,
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
        video("1080p", 80 * MIB, merged = true),
        video("720p", 42 * MIB, merged = true),
        video("480p", 18 * MIB, merged = true),
        video("360p", 11 * MIB),
        audio(128, 4 * MIB),
        audio(48, 2 * MIB),
    )
}
