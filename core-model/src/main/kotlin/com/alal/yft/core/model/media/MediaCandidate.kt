package com.alal.yft.core.model.media

import com.alal.yft.core.model.logging.SensitiveValueRedactor

enum class CandidateSource {
    PASTED_URL,
    DOWNLOAD_LISTENER,
    DOM,
    REQUEST,
    MANIFEST,
    REDIRECT,
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
        append(')')
    }
}
