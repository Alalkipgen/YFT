package com.alal.yft.extractor.sites.vimeo

import com.alal.yft.extractor.api.SitePageIdentity
import java.net.URI
import java.util.Locale

/**
 * Pure, offline URL matching for Vimeo clip and player pages.
 *
 * Matching never performs network access, so adapter selection stays cheap and side-effect free.
 * Channel, group, showcase and embed addresses all collapse onto the same canonical clip address,
 * and an unlisted link keeps its privacy hash because the clip is not reachable without it.
 *
 * On-demand, event and other paid or live surfaces are deliberately not claimed: they are
 * licensed or DRM-protected products, so they fall through to the generic detector instead of
 * being handled by an adapter that would have to work around their access control.
 */
internal object VimeoUrls {
    const val SITE_ID = "vimeo"

    private val CLIP_HOSTS = setOf("vimeo.com", "www.vimeo.com")
    private const val PLAYER_HOST = "player.vimeo.com"

    private val NUMERIC_ID = Regex("^[0-9]{5,12}$")

    /** Vimeo privacy hashes are short lowercase hex strings. */
    private val UNLISTED_HASH = Regex("^[0-9a-f]{6,20}$")

    fun identify(pageUrl: String): SitePageIdentity? {
        val uri = runCatching { URI(pageUrl.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.US) != "https") return null
        if (uri.userInfo != null) return null
        val host = uri.host?.lowercase(Locale.US) ?: return null
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)

        val clip = when {
            host == PLAYER_HOST -> playerClip(segments, queryParameter(uri.rawQuery, "h"))
            host in CLIP_HOSTS -> pageClip(segments)
            else -> null
        } ?: return null

        return SitePageIdentity(
            siteId = SITE_ID,
            contentId = clip.id,
            canonicalPageUrl = canonicalUrl(clip.id, clip.unlistedHash),
        )
    }

    fun canonicalUrl(videoId: String, unlistedHash: String? = null): String =
        if (unlistedHash.isNullOrBlank()) {
            "https://vimeo.com/$videoId"
        } else {
            "https://vimeo.com/$videoId/$unlistedHash"
        }

    /** The player configuration address for a clip, used when the page does not supply one. */
    fun configUrl(videoId: String, unlistedHash: String? = null): String =
        if (unlistedHash.isNullOrBlank()) {
            "https://$PLAYER_HOST/video/$videoId/config"
        } else {
            "https://$PLAYER_HOST/video/$videoId/config?h=$unlistedHash"
        }

    /**
     * True when a page-supplied configuration address may be fetched.
     *
     * The adapter follows only Vimeo's own player host, so a changed page cannot redirect the
     * extraction to an arbitrary third-party endpoint with the user's session attached.
     */
    fun isConfigUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase(Locale.US) != "https") return false
        if (uri.userInfo != null) return false
        if (uri.host?.lowercase(Locale.US) != PLAYER_HOST) return false
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        return segments.size >= 3 &&
            segments[0].lowercase(Locale.US) == "video" &&
            NUMERIC_ID.matches(segments[1]) &&
            segments[2].lowercase(Locale.US) == "config"
    }

    /** Reads the hash out of a canonical clip address, if it carries one. */
    fun unlistedHashOf(canonicalPageUrl: String): String? {
        val uri = runCatching { URI(canonicalPageUrl.trim()) }.getOrNull() ?: return null
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        return segments.getOrNull(1)?.takeIf(UNLISTED_HASH::matches)
    }

    private data class Clip(val id: String, val unlistedHash: String?)

    private fun playerClip(segments: List<String>, hashParameter: String?): Clip? {
        if (segments.size < 2) return null
        if (segments[0].lowercase(Locale.US) != "video") return null
        val id = segments[1].takeIf(NUMERIC_ID::matches) ?: return null
        val hash = hashParameter?.takeIf(UNLISTED_HASH::matches)
            ?: segments.getOrNull(2)?.takeIf(UNLISTED_HASH::matches)
        return Clip(id, hash)
    }

    private fun pageClip(segments: List<String>): Clip? {
        val head = segments.firstOrNull()?.lowercase(Locale.US) ?: return null

        // /123456789 and /123456789/abcdef1234 for unlisted clips
        if (NUMERIC_ID.matches(head)) {
            val hash = segments.getOrNull(1)?.takeIf(UNLISTED_HASH::matches)
            return Clip(head, hash)
        }
        // /channels/{name}/123456789
        if (head == "channels" && segments.size >= 3) {
            return segments[2].takeIf(NUMERIC_ID::matches)?.let { Clip(it, null) }
        }
        // /groups/{name}/videos/123456789, /album/{id}/video/123456789 and
        // /showcase/{id}/video/123456789
        if (head in NESTED_HEADS && segments.size >= 4 && segments[2].lowercase(Locale.US) in
            NESTED_VIDEO_SEGMENTS
        ) {
            return segments[3].takeIf(NUMERIC_ID::matches)?.let { Clip(it, null) }
        }
        return null
    }

    private fun queryParameter(rawQuery: String?, name: String): String? {
        val query = rawQuery?.takeIf(String::isNotBlank) ?: return null
        return query.split('&')
            .firstOrNull { pair ->
                pair.contains('=') && pair.substringBefore('=').lowercase(Locale.US) == name
            }
            ?.substringAfter('=')
            ?.takeIf(String::isNotBlank)
    }

    private val NESTED_HEADS = setOf("groups", "album", "showcase")
    private val NESTED_VIDEO_SEGMENTS = setOf("video", "videos")
}
