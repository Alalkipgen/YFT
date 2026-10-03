package com.alal.yft.extractor.sites.tiktok

import com.alal.yft.extractor.api.SitePageIdentity
import java.net.URI
import java.util.Locale

/**
 * Pure, offline URL matching for TikTok pages.
 *
 * Matching never performs network access, so adapter selection stays cheap and side-effect free.
 * Short links cannot be expanded offline, so they produce an identity that is explicitly marked as
 * unresolved rather than a guessed numeric ID.
 */
internal object TikTokUrls {
    const val SITE_ID = "tiktok"
    private const val COOKIE_DOMAIN = "tiktok.com"

    private val LONG_HOSTS = setOf("tiktok.com", "www.tiktok.com", "m.tiktok.com")
    private val SHORT_HOSTS = setOf("vm.tiktok.com", "vt.tiktok.com")

    private val NUMERIC_ID = Regex("^[0-9]{5,25}$")
    private val SHORT_CODE = Regex("^[A-Za-z0-9]{5,24}$")

    /** Post kinds TikTok exposes under the same numeric ID space. */
    enum class PostKind(val pathSegment: String) {
        VIDEO("video"),
        PHOTO("photo"),
    }

    fun identify(pageUrl: String): SitePageIdentity? {
        val uri = runCatching { URI(pageUrl.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.US) != "https") return null
        if (uri.userInfo != null) return null
        val host = uri.host?.lowercase(Locale.US) ?: return null
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)

        if (host in SHORT_HOSTS) {
            val code = segments.singleOrNull()?.takeIf(SHORT_CODE::matches) ?: return null
            return SitePageIdentity(
                siteId = SITE_ID,
                contentId = code,
                canonicalPageUrl = "https://$host/$code",
                requiresCanonicalResolution = true,
            )
        }
        if (host !in LONG_HOSTS) return null

        val post = postFrom(segments) ?: return null
        return SitePageIdentity(
            siteId = SITE_ID,
            contentId = post.id,
            canonicalPageUrl = canonicalUrl(post.author, post.id, post.kind),
        )
    }

    /** Builds the canonical page address; the author handle is omitted when the page hid it. */
    fun canonicalUrl(
        author: String?,
        videoId: String,
        kind: PostKind = PostKind.VIDEO,
    ): String = if (author.isNullOrBlank()) {
        "https://www.tiktok.com/${kind.pathSegment}/$videoId"
    } else {
        "https://www.tiktok.com/@$author/${kind.pathSegment}/$videoId"
    }

    /** True when the canonical address describes a photo post rather than a video. */
    fun isPhotoPost(canonicalPageUrl: String): Boolean =
        canonicalPageUrl.contains("/${PostKind.PHOTO.pathSegment}/")

    /** True for `tiktok.com` and its subdomains, the only cookie domains TikTok media may use. */
    fun isTikTokDomain(domain: String): Boolean {
        val host = domain.lowercase(Locale.US).removePrefix(".")
        return host == COOKIE_DOMAIN || host.endsWith(".$COOKIE_DOMAIN")
    }

    private data class Post(val author: String?, val id: String, val kind: PostKind)

    private fun postFrom(segments: List<String>): Post? {
        // /@author/video/123 and /@author/photo/123
        if (segments.size >= 3 && segments[0].startsWith("@")) {
            val kind = kindFor(segments[1]) ?: return null
            val id = segments[2].takeIf(NUMERIC_ID::matches) ?: return null
            return Post(segments[0].removePrefix("@").takeIf(String::isNotBlank), id, kind)
        }
        if (segments.size != 2) return null
        // /v/123.html used by the mobile web host
        if (segments[0].lowercase(Locale.US) == "v") {
            val id = segments[1].removeSuffix(".html").takeIf(NUMERIC_ID::matches) ?: return null
            return Post(null, id, PostKind.VIDEO)
        }
        // /video/123 or /photo/123 without an author handle
        val kind = kindFor(segments[0]) ?: return null
        val id = segments[1].takeIf(NUMERIC_ID::matches) ?: return null
        return Post(null, id, kind)
    }

    private fun kindFor(segment: String): PostKind? =
        PostKind.entries.firstOrNull { it.pathSegment == segment.lowercase(Locale.US) }
}
