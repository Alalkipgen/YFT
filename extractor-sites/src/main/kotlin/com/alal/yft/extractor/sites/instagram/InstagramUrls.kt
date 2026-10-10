package com.alal.yft.extractor.sites.instagram

import com.alal.yft.extractor.api.SitePageIdentity
import java.math.BigInteger
import java.net.URI
import java.util.Locale

/**
 * P49: pure, offline URL matching for Instagram posts and reels.
 *
 * `/p/`, `/reel/`, `/reels/` and `/tv/` addresses, with or without the owner's name in front and
 * with any share parameter (`igsh`, `utm_source`), collapse onto `https://www.instagram.com/p/`
 * or `/reel/{code}/`. A carousel link keeps its `img_index`, which names one item of the post.
 * Profiles, stories, the feed and audio pages are not claimed.
 */
internal object InstagramUrls {
    const val SITE_ID = "instagram"

    private val HOSTS = setOf("instagram.com", "www.instagram.com", "m.instagram.com")
    private val SHORTCODE = Regex("^[A-Za-z0-9_-]{8,64}$")
    private val USER_NAME = Regex("^[A-Za-z0-9._]{1,30}$")
    private val POST_KINDS = setOf("p", "reel", "reels", "tv")
    private val IMAGE_INDEX = Regex("[?&]img_index=([0-9]{1,2})(?:&|$)")
    private const val ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private const val MEDIA_ID_CHARS = 11
    private const val MAX_ITEMS = 20

    fun identify(pageUrl: String): SitePageIdentity? {
        val uri = runCatching { URI(pageUrl.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.US) != "https") return null
        if (uri.userInfo != null) return null
        val host = uri.host?.lowercase(Locale.US) ?: return null
        if (host !in HOSTS) return null
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val post = when {
            segments.size >= 2 && segments[0].lowercase(Locale.US) in POST_KINDS ->
                segments[0].lowercase(Locale.US) to segments[1]

            segments.size >= 3 && USER_NAME.matches(segments[0]) &&
                segments[1].lowercase(Locale.US) in POST_KINDS ->
                segments[1].lowercase(Locale.US) to segments[2]

            else -> return null
        }
        val code = post.second.takeIf(SHORTCODE::matches) ?: return null
        val reel = post.first == "reel" || post.first == "reels"
        val item = imageIndexOf(uri.rawQuery)
        return SitePageIdentity(
            siteId = SITE_ID,
            contentId = code,
            canonicalPageUrl = canonicalUrl(code, reel, item),
        )
    }

    fun canonicalUrl(code: String, reel: Boolean, item: Int? = null): String = buildString {
        append("https://www.instagram.com/")
        append(if (reel) "reel" else "p")
        append('/').append(code).append('/')
        item?.let { append("?img_index=").append(it) }
    }

    /** The 1-based carousel item a canonical address names, or null. */
    fun itemOf(canonicalPageUrl: String): Int? =
        imageIndexOf(canonicalPageUrl.substringAfter('?', ""))

    /**
     * The post's numeric media ID, which Instagram's own app API asks with: its shortcode read
     * as base 64 in Instagram's alphabet (the first 11 characters; longer private codes carry
     * more after them).
     */
    fun mediaIdOf(code: String): String? {
        var value = BigInteger.ZERO
        val base = BigInteger.valueOf(ALPHABET.length.toLong())
        code.take(MEDIA_ID_CHARS).forEach { character ->
            val digit = ALPHABET.indexOf(character).takeIf { it >= 0 } ?: return null
            value = value.multiply(base).add(BigInteger.valueOf(digit.toLong()))
        }
        return value.takeIf { it.signum() > 0 }?.toString()
    }

    /** `https://www.instagram.com/p/{code}/embed/captioned/`, the public embed page. */
    fun embedUrl(code: String): String = "https://www.instagram.com/p/$code/embed/captioned/"

    /** Whether a page answer ended on Instagram's login or checkpoint page. */
    fun isLoginWall(url: String): Boolean {
        val path = runCatching { URI(url).path }.getOrNull()?.lowercase(Locale.US) ?: return false
        return path.startsWith("/accounts/login") || path.startsWith("/challenge")
    }

    private fun imageIndexOf(rawQuery: String?): Int? {
        val query = rawQuery?.takeIf(String::isNotBlank) ?: return null
        return IMAGE_INDEX.find("?$query")?.groupValues?.get(1)?.toIntOrNull()
            ?.takeIf { it in 1..MAX_ITEMS }
    }
}
