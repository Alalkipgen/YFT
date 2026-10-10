package com.alal.yft.extractor.sites.x

import com.alal.yft.extractor.api.SitePageIdentity
import java.net.URI
import java.util.Locale

/**
 * P48: pure, offline URL matching for X (Twitter) posts.
 *
 * A post's address on x.com or twitter.com, its mobile host, `/i/status/` and `/i/web/status/`
 * all collapse onto one canonical `https://x.com/{user}/status/{id}`. A `/video/N` link keeps its
 * number, because a post can hold up to four videos and the link names one of them. Profiles,
 * searches, lists, the home timeline and `t.co` short links are not claimed: they fall through
 * to the generic detector.
 */
internal object XUrls {
    const val SITE_ID = "x"

    private val HOSTS = setOf(
        "x.com",
        "www.x.com",
        "mobile.x.com",
        "twitter.com",
        "www.twitter.com",
        "mobile.twitter.com",
        "m.twitter.com",
    )

    private val POST_ID = Regex("^[0-9]{1,25}$")
    private val USER_NAME = Regex("^[A-Za-z0-9_]{1,30}$")
    private val MEDIA_NUMBER = Regex("^[1-4]$")
    private val VIDEO_SUFFIX = Regex("/video/([1-4])$")

    /** Paths whose second segment is not a user's post list. */
    private val RESERVED_FIRST = setOf(
        "home", "explore", "search", "settings", "messages", "notifications", "hashtag",
        "intent", "share", "login", "logout", "signup", "compose", "tos", "privacy",
    )

    fun identify(pageUrl: String): SitePageIdentity? {
        val uri = runCatching { URI(pageUrl.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.US) != "https") return null
        if (uri.userInfo != null) return null
        val host = uri.host?.lowercase(Locale.US) ?: return null
        if (host !in HOSTS) return null
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val post = postOf(segments) ?: return null
        return SitePageIdentity(
            siteId = SITE_ID,
            contentId = post.id,
            canonicalPageUrl = canonicalUrl(post.user, post.id, post.video),
        )
    }

    fun canonicalUrl(user: String?, id: String, video: Int? = null): String = buildString {
        append("https://x.com/")
        append(user ?: "i")
        append("/status/")
        append(id)
        video?.let { append("/video/").append(it) }
    }

    /** The 1-based video a canonical address names with `/video/N`, or null for the first. */
    fun videoNumberOf(canonicalPageUrl: String): Int? =
        VIDEO_SUFFIX.find(canonicalPageUrl)?.groupValues?.get(1)?.toIntOrNull()

    private class Post(val user: String?, val id: String, val video: Int?)

    private fun postOf(segments: List<String>): Post? {
        val first = segments.getOrNull(0) ?: return null
        val lowered = first.lowercase(Locale.US)
        return when {
            lowered == "i" && segments.getOrNull(1)?.lowercase(Locale.US) == "web" ->
                post(null, segments.drop(2))

            lowered == "i" -> post(null, segments.drop(1))
            lowered in RESERVED_FIRST -> null
            USER_NAME.matches(first) -> post(first, segments.drop(1))
            else -> null
        }
    }

    /** `status/{id}[/video/N]`, `statuses/{id}`; anything else after the post is ignored. */
    private fun post(user: String?, rest: List<String>): Post? {
        val marker = rest.getOrNull(0)?.lowercase(Locale.US) ?: return null
        if (marker != "status" && marker != "statuses") return null
        val id = rest.getOrNull(1)?.takeIf(POST_ID::matches) ?: return null
        val video = rest.getOrNull(3)
            ?.takeIf { rest[2].lowercase(Locale.US) == "video" && MEDIA_NUMBER.matches(it) }
            ?.toInt()
        return Post(user, id, video)
    }
}
