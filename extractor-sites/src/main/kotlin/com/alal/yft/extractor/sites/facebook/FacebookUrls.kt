package com.alal.yft.extractor.sites.facebook

import com.alal.yft.extractor.api.SitePageIdentity
import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * Pure, offline URL matching for Facebook video pages.
 *
 * Matching never performs network access, so adapter selection stays cheap and side-effect free.
 * Facebook addresses the same video through several surfaces, so every recognized form collapses
 * onto one canonical page address. Short links, share links and posts cannot be expanded offline,
 * so they produce an identity that is explicitly marked as unresolved instead of a guessed
 * numeric ID.
 */
internal object FacebookUrls {
    const val SITE_ID = "facebook"

    private val LONG_HOSTS = setOf(
        "facebook.com",
        "www.facebook.com",
        "m.facebook.com",
        "web.facebook.com",
        "mbasic.facebook.com",
        "fb.com",
        "www.fb.com",
    )
    private val SHORT_HOSTS = setOf("fb.watch", "www.fb.watch")

    private val NUMERIC_ID = Regex("^[0-9]{6,25}$")
    private val SHORT_CODE = Regex("^[A-Za-z0-9_-]{4,32}$")

    /** A post's ID: numeric, or the opaque `pfbid…` form Facebook uses in post links. */
    private val POST_ID = Regex("^(?:[0-9]{6,25}|pfbid[0-9A-Za-z]{10,100})$")

    /** A profile, page or group name as it appears in a path. */
    private val HANDLE = Regex("^[A-Za-z0-9._-]{1,80}$")

    private const val DESKTOP = "https://www.facebook.com"
    private const val POST_PREFIX = "post:"

    /** Watch surfaces that carry the video ID in the `v` query parameter. */
    private val WATCH_SEGMENTS = setOf("watch", "watch.php", "video", "video.php")

    /** Share links that wrap a video, a reel or a post without exposing its real ID. */
    private val SHARE_SEGMENTS = setOf("v", "r", "p")

    /** Post pages that name the post and its owner in the query: `?story_fbid=…&id=…`. */
    private val STORY_SEGMENTS = setOf("story.php", "permalink.php")

    /** A group's post paths: /groups/{group}/posts/{id} and /groups/{group}/permalink/{id}. */
    private val GROUP_POST_SEGMENTS = setOf("posts", "permalink")

    /**
     * Path heads that are Facebook surfaces rather than profile handles.
     *
     * Without this list a group or marketplace path could be mistaken for `/{handle}/videos/{id}`,
     * which would make the adapter claim a page it cannot parse.
     */
    private val RESERVED_HANDLES = setOf(
        "ad_campaign",
        "ads",
        "business",
        "events",
        "gaming",
        "groups",
        "help",
        "legal",
        "login",
        "marketplace",
        "messages",
        "notes",
        "pages",
        "permalink.php",
        "photo",
        "photo.php",
        "photos",
        "policies",
        "privacy",
        "profile.php",
        "reel",
        "reels",
        "settings",
        "share",
        "stories",
        "story.php",
        "watch",
        "watch.php",
        "video",
        "video.php",
    )

    /** Facebook surfaces that keep their own canonical path for the same numeric ID space. */
    enum class PostKind(val canonicalPath: String) {
        VIDEO("watch"),
        REEL("reel"),
    }

    fun identify(pageUrl: String): SitePageIdentity? {
        val uri = runCatching { URI(pageUrl.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.US) != "https") return null
        if (uri.userInfo != null) return null
        val host = uri.host?.lowercase(Locale.US) ?: return null
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)

        if (host in SHORT_HOSTS) {
            val code = segments.singleOrNull()?.takeIf(SHORT_CODE::matches) ?: return null
            return unresolvedIdentity(code, "https://fb.watch/$code")
        }
        if (host !in LONG_HOSTS) return null

        shareCode(segments)?.let { (segment, code) ->
            return unresolvedIdentity(code, "https://www.facebook.com/share/$segment/$code")
        }
        val query = queryParameters(uri.rawQuery)
        postAddress(segments, query)?.let { (postId, address) ->
            return unresolvedIdentity("$POST_PREFIX$postId", address)
        }
        val post = postFrom(segments, query) ?: return null
        return SitePageIdentity(
            siteId = SITE_ID,
            contentId = post.id,
            canonicalPageUrl = canonicalUrl(post.id, post.kind),
        )
    }

    fun canonicalUrl(videoId: String, kind: PostKind = PostKind.VIDEO): String = when (kind) {
        PostKind.VIDEO -> "https://www.facebook.com/watch/?v=$videoId"
        PostKind.REEL -> "https://www.facebook.com/${PostKind.REEL.canonicalPath}/$videoId"
    }

    /** Keeps a resolved ID on the surface it came from, so a reel stays a reel. */
    fun kindOf(canonicalPageUrl: String): PostKind =
        if (canonicalPageUrl.contains("/${PostKind.REEL.canonicalPath}/")) {
            PostKind.REEL
        } else {
            PostKind.VIDEO
        }

    fun isNumericId(value: String): Boolean = NUMERIC_ID.matches(value)

    /**
     * True for a post or a post's share link: the page may hold no video at all, so a page
     * without one is a post without a video rather than a changed page format.
     */
    fun isPost(identity: SitePageIdentity): Boolean =
        identity.contentId.startsWith(POST_PREFIX) ||
            identity.canonicalPageUrl.startsWith("$DESKTOP/share/p/")

    /** True when Facebook answered with a login or checkpoint wall instead of the page. */
    fun isAccessWall(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.US) ?: return false
        if (host !in LONG_HOSTS && host !in SHORT_HOSTS) return false
        val path = uri.path.orEmpty().lowercase(Locale.US)
        return WALL_PATHS.any { path == it || path.startsWith("$it/") }
    }

    /**
     * Reads the expiry Facebook already stamped into a media URL.
     *
     * The CDN encodes it as the hexadecimal `oe` parameter, with `_nc_exp` and `expire` used by
     * other surfaces. A value outside a sane window is ignored rather than reported as an expiry,
     * so the download engine never times out a link because of a misread parameter.
     */
    fun mediaExpiryEpochMs(mediaUrl: String): Long? {
        val rawQuery = runCatching { URI(mediaUrl.trim()) }.getOrNull()?.rawQuery ?: return null
        val parameters = queryParameters(rawQuery)
        val seconds = parameters["oe"]?.toLongOrNull(radix = 16)
            ?: parameters["_nc_exp"]?.toLongOrNull()?.let(::toSeconds)
            ?: parameters["expire"]?.toLongOrNull()?.let(::toSeconds)
            ?: return null
        return if (seconds in PLAUSIBLE_EXPIRY_SECONDS) seconds * 1_000 else null
    }

    private fun toSeconds(value: Long): Long =
        if (value > PLAUSIBLE_EXPIRY_SECONDS.last) value / 1_000 else value

    private fun unresolvedIdentity(code: String, canonicalPageUrl: String): SitePageIdentity =
        SitePageIdentity(
            siteId = SITE_ID,
            contentId = code,
            canonicalPageUrl = canonicalPageUrl,
            requiresCanonicalResolution = true,
        )

    private data class Post(val id: String, val kind: PostKind)

    /**
     * The address of a post that may hold a video, with the post's ID (P3-FIX).
     *
     * story.php and permalink.php need the owner's `id`, profiles and pages use /posts/, groups
     * /posts/ or /permalink/. Only the post's page names its video, so the identity stays
     * unresolved. Facebook's desktop page sends story.php and permalink.php to its login wall
     * but redirects /{owner}/posts/{id} to the video's own page (sandbox live check,
     * 2026-10-05), so that is the address the adapter asks.
     */
    private fun postAddress(
        segments: List<String>,
        query: Map<String, String>,
    ): Pair<String, String>? {
        val head = segments.firstOrNull()?.lowercase(Locale.US) ?: return null
        if (head in STORY_SEGMENTS) {
            if (segments.size != 1) return null
            val story = query["story_fbid"]?.takeIf(POST_ID::matches) ?: return null
            val owner = query["id"]?.takeIf(NUMERIC_ID::matches) ?: return null
            return story to "$DESKTOP/$owner/posts/$story"
        }
        if (head == "groups") {
            val group = segments.getOrNull(1)?.takeIf(HANDLE::matches) ?: return null
            if (segments.getOrNull(2)?.lowercase(Locale.US) !in GROUP_POST_SEGMENTS) return null
            val story = segments.getOrNull(3)?.takeIf(POST_ID::matches) ?: return null
            return story to "$DESKTOP/groups/$group/posts/$story"
        }
        if (head in RESERVED_HANDLES) return null
        if (segments.getOrNull(1)?.lowercase(Locale.US) != "posts") return null
        val handle = segments[0].takeIf(HANDLE::matches) ?: return null
        val story = segments.getOrNull(2)?.takeIf(POST_ID::matches) ?: return null
        return story to "$DESKTOP/$handle/posts/$story"
    }

    private fun postFrom(segments: List<String>, query: Map<String, String>): Post? {
        val head = segments.firstOrNull()?.lowercase(Locale.US) ?: return null

        // /watch/?v=123, /watch/live/?v=123 and /video.php?v=123
        if (head in WATCH_SEGMENTS) {
            val id = query["v"]?.takeIf(NUMERIC_ID::matches) ?: return null
            return Post(id, PostKind.VIDEO)
        }
        // /reel/123 and /reels/123
        if (head == PostKind.REEL.canonicalPath || head == "reels") {
            val id = segments.getOrNull(1)?.takeIf(NUMERIC_ID::matches) ?: return null
            return Post(id, PostKind.REEL)
        }
        if (head in RESERVED_HANDLES) return null
        // /{handle}/videos/123 and /{handle}/videos/{slug}/123
        if (segments.getOrNull(1)?.lowercase(Locale.US) != "videos") return null
        val id = segments.drop(2).lastOrNull(NUMERIC_ID::matches) ?: return null
        return Post(id, PostKind.VIDEO)
    }

    private fun shareCode(segments: List<String>): Pair<String, String>? {
        if (segments.size < 3) return null
        if (segments[0].lowercase(Locale.US) != "share") return null
        val segment = segments[1].lowercase(Locale.US).takeIf(SHARE_SEGMENTS::contains)
            ?: return null
        val code = segments[2].takeIf(SHORT_CODE::matches) ?: return null
        return segment to code
    }

    private fun queryParameters(rawQuery: String?): Map<String, String> {
        val query = rawQuery?.takeIf(String::isNotBlank) ?: return emptyMap()
        return query.split('&').mapNotNull { pair ->
            if (!pair.contains('=')) return@mapNotNull null
            val name = pair.substringBefore('=').lowercase(Locale.US).takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            name to decode(pair.substringAfter('='))
        }.toMap()
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private val WALL_PATHS = listOf("/login", "/login.php", "/checkpoint", "/recover", "/r.php")

    /** 2014-05 to 2100-01: wide enough for real links, narrow enough to reject noise. */
    private val PLAUSIBLE_EXPIRY_SECONDS = 1_400_000_000L..4_102_444_800L
}
