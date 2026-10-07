package com.alal.yft.core.model.media

import kotlin.math.abs

/**
 * P28: what a page without an adapter states about its own video, apart from any file: its
 * length, title and picture (JSON-LD `VideoObject`, Open Graph, `itemprop`, the page title).
 * Every field is optional. The stated length tells the page's video from the ad its player
 * shows first: a 0:30 file on a page that states 16:24 is not the page's video.
 */
data class PageVideoFacts(
    /** The length the page states for its video; null when it states none. */
    val durationMillis: Long? = null,
    val title: String? = null,
    /** The page's picture of its video, an HTTPS address. */
    val thumbnailUrl: String? = null,
) {
    init {
        require(durationMillis == null || durationMillis > 0)
    }

    val isEmpty: Boolean get() = durationMillis == null && title == null && thumbnailUrl == null

    /** True when the page states a video of two minutes or more. */
    val statesLongVideo: Boolean get() = (durationMillis ?: 0L) >= LONG_STATED_MILLIS

    /** These facts, with what they lack taken from [older]. */
    fun orElse(older: PageVideoFacts?): PageVideoFacts = if (older == null) this else copy(
        durationMillis = durationMillis ?: older.durationMillis,
        title = title ?: older.title,
        thumbnailUrl = thumbnailUrl ?: older.thumbnailUrl,
    )

    /**
     * Whether a file [lengthMillis] long is the video of the stated length: within 2 s, or
     * within 1% for a video over ten minutes (players and manifests round differently).
     */
    fun matchesLength(lengthMillis: Long?): Boolean {
        val stated = durationMillis ?: return false
        val length = lengthMillis?.takeIf { it > 0 } ?: return false
        val slack = if (stated > TEN_MINUTES_MILLIS) stated / PERCENT else LENGTH_SLACK_MILLIS
        return abs(stated - length) <= slack
    }

    /**
     * Whether a file [lengthMillis] long is far shorter than the stated video — under half of
     * it: an ad or a preview, not the page's video.
     */
    fun isFarShorter(lengthMillis: Long?): Boolean {
        val stated = durationMillis ?: return false
        val length = lengthMillis?.takeIf { it > 0 } ?: return false
        return length * 2 < stated
    }

    override fun toString(): String =
        "PageVideoFacts(durationMillis=$durationMillis, title=${title != null}, " +
            "thumbnail=${thumbnailUrl != null})"

    companion object {
        private const val LENGTH_SLACK_MILLIS = 2_000L
        private const val TEN_MINUTES_MILLIS = 600_000L
        private const val PERCENT = 100L
        private const val LONG_STATED_MILLIS = 120_000L
    }
}
