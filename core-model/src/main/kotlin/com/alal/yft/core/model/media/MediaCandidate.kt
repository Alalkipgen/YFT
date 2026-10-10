package com.alal.yft.core.model.media

import com.alal.yft.core.model.logging.SensitiveValueRedactor

enum class CandidateSource {
    PASTED_URL,
    DOWNLOAD_LISTENER,
    DOM,
    REQUEST,
    MANIFEST,
    REDIRECT,

    /** P37: the sheet read the browser tab's page again, quietly, for its fresh links. */
    PAGE_REREAD,
}

enum class MediaKind {
    DIRECT,
    HLS,
    DASH,
    UNKNOWN,
}

enum class CandidateConfidence {
    LOW,
    MEDIUM,
    HIGH,
}

/**
 * P24: what a page itself says a file is, on a site without an adapter. Null when it says
 * nothing; the file then counts as a video like before.
 */
enum class PageMediaRole {
    /** The page's own video: JSON-LD, Open Graph, or a manifest its own scripts name. */
    MAIN,

    /** A preview around the page's video: a thumbnail's clip, a muted loop or an ad. */
    PREVIEW,
}

/**
 * P43: what showed that a file is an ad, where it was found: its address or its frame's names an
 * ad network ([AD_HOST]) or ad words ([AD_ADDRESS]), or the page's player fetched it right after
 * asking for an ad break ([AD_BREAK]).
 */
enum class AdSign(val label: String) {
    AD_HOST("ad host"),
    AD_ADDRESS("ad address"),
    AD_BREAK("ad break"),
}

data class MediaCandidate(
    val pageUrl: String,
    val mediaUrl: String,
    val sources: Set<CandidateSource>,
    val kind: MediaKind,
    val mimeType: String? = null,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val durationMillis: Long? = null,
    val contentLengthBytes: Long? = null,
    val requestContext: BrowserRequestContext = BrowserRequestContext(pageUrl, null, null),
    val confidence: CandidateConfidence = CandidateConfidence.LOW,
    val expiresAtEpochMs: Long? = null,
    val drmHint: Boolean? = null,
    val observedAtEpochMs: Long = 0,
    /** Codecs of [mediaUrl] when the source states them, for example `avc1.64001F`. */
    val codecs: List<String> = emptyList(),
    /** Set when [mediaUrl] is video only: the audio that is merged with it on the phone. */
    val audioCompanion: CompanionAudio? = null,
    /**
     * The site's own name for the video, for example `youtube:dQw4w9WgXcQ`. Candidates with the
     * same value are qualities of one video and share one download sheet ([MediaGroups]).
     */
    val videoId: String? = null,
    /** The picture size and rate the source states; null when unknown, never guessed. */
    val width: Int? = null,
    val height: Int? = null,
    val framesPerSecond: Double? = null,
    val bitrateBitsPerSecond: Long? = null,
    /** P24: the page's own word on this file ([PageMediaRole]); null when it says nothing. */
    val pageRole: PageMediaRole? = null,
    /**
     * P28: the page's own name for the video [mediaUrl] belongs to when the page's player setup
     * lists it as one of its qualities (`720p`, `480p`), without a site adapter. Candidates with
     * the same value on one page share one download sheet, like [videoId].
     */
    val pageVideoKey: String? = null,
    /**
     * P43: what showed where it was found that [mediaUrl] is an ad ([AdSign]); null when
     * nothing did. Set with [PageMediaRole.PREVIEW].
     */
    val adSign: AdSign? = null,
) {
    override fun toString(): String = buildString {
        append("MediaCandidate(pageUrl=")
        append(SensitiveValueRedactor.redact(pageUrl))
        append(", mediaUrl=")
        append(SensitiveValueRedactor.redact(mediaUrl))
        append(", sources=")
        append(sources)
        append(", kind=")
        append(kind)
        append(", confidence=")
        append(confidence)
        append(", drmHint=")
        append(drmHint)
        append(", codecs=")
        append(codecs)
        append(", audioCompanion=")
        append(audioCompanion != null)
        append(", height=")
        append(height)
        append(", pageRole=")
        append(pageRole)
        append(", adSign=")
        append(adSign)
        append(')')
    }
}
