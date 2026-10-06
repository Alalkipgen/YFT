package com.alal.yft.core.model.media

/**
 * P24: the video element a page is playing (or has started) when the Download button is tapped,
 * as the page reports it. [url] is its HTTPS address; null when the page builds the stream itself
 * (a `blob:` source through Media Source Extensions), so [durationMillis] and
 * [startedAtEpochMs] are what tell its candidates apart ([MediaGroups.mainVideo]).
 */
data class PlayingVideo(
    val url: String? = null,
    /** True when the element reads a stream the page builds (`blob:`), which names no file. */
    val pageBuilt: Boolean = false,
    /** The element's length; null when unknown or live. */
    val durationMillis: Long? = null,
    /** When the element started, by the page's clock: now minus its position. */
    val startedAtEpochMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val muted: Boolean = false,
    val loop: Boolean = false,
    val autoplay: Boolean = false,
    /**
     * True for an element inside a frame rather than the page itself. Only the page's own
     * frames can be read, so this is not a third-party ad: it only ranks after the page's own.
     */
    val framed: Boolean = false,
    /** True for an element inside a link to another page or a thumbnail box. */
    val inThumbnail: Boolean = false,
) {
    init {
        require(durationMillis == null || durationMillis > 0)
        require(width == null || width > 0)
        require(height == null || height > 0)
    }

    /** A muted loop or a thumbnail's clip: what a preview looks like. */
    val looksLikePreview: Boolean get() = muted && loop || inThumbnail

    override fun toString(): String =
        "PlayingVideo(url=${if (url == null) "none" else "[REDACTED]"}, pageBuilt=$pageBuilt, " +
            "durationMillis=$durationMillis, height=$height, preview=$looksLikePreview)"
}
