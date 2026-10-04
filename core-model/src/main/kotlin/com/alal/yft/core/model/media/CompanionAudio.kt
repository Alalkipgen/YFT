package com.alal.yft.core.model.media

/**
 * The audio file a video-only download needs.
 *
 * Sites such as YouTube serve their higher qualities as a video-only file and a separate audio
 * file. A candidate or variant that carries one is downloaded as both files, which are merged on
 * the phone into one MP4 with sound. [mediaUrl] can hold short-lived credentials, so neither it
 * nor the request context ever prints.
 */
data class CompanionAudio(
    val mediaUrl: String,
    val mimeType: String,
    val codecs: List<String>,
    val requestContext: BrowserRequestContext,
    val contentLengthBytes: Long? = null,
    val bitrateBitsPerSecond: Long? = null,
    val expiresAtEpochMs: Long? = null,
) {
    init {
        require(mediaUrl.isNotBlank())
        require(mimeType.isNotBlank())
        require(codecs.none(String::isBlank))
        require(contentLengthBytes == null || contentLengthBytes > 0)
        require(bitrateBitsPerSecond == null || bitrateBitsPerSecond > 0)
    }

    override fun toString(): String = buildString {
        append("CompanionAudio(mediaUrl=[REDACTED], mimeType=")
        append(mimeType)
        append(", codecs=")
        append(codecs)
        append(", requestContext=[REDACTED], contentLengthBytes=")
        append(contentLengthBytes)
        append(", bitrateBitsPerSecond=")
        append(bitrateBitsPerSecond)
        append(", expiresAtEpochMs=")
        append(expiresAtEpochMs)
        append(')')
    }
}
