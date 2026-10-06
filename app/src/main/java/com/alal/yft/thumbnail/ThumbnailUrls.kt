package com.alal.yft.thumbnail

import com.alal.yft.core.model.media.MediaCandidate
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Which picture shows a video (P19). A YouTube video's picture follows from its ID, so it may
 * load before the lookup ends; other sites use the `thumbnailUrl` the lookup found. Only HTTPS
 * addresses are used.
 */
object ThumbnailUrls {
    private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")
    private const val YOUTUBE_PREFIX = "youtube:"

    /** `https://i.ytimg.com/vi/<id>/hqdefault.jpg` for a `youtube:<id>` key, else null. */
    fun youTube(key: String?): String? {
        val id = key?.takeIf { it.startsWith(YOUTUBE_PREFIX) }?.removePrefix(YOUTUBE_PREFIX)
            ?.takeIf(YOUTUBE_ID::matches) ?: return null
        return "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    }

    /** The picture of one video's [candidates]: YouTube's by ID, else the first one found. */
    fun of(candidates: List<MediaCandidate>): String? =
        candidates.firstNotNullOfOrNull { youTube(it.videoId) }
            ?: candidates.firstNotNullOfOrNull { https(it.thumbnailUrl) }

    /** [url] when it is an HTTPS address, else null. */
    fun https(url: String?): String? =
        url?.takeIf { it.toHttpUrlOrNull()?.isHttps == true }
}
