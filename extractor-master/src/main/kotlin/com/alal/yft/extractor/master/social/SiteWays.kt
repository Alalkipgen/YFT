/*
 * Provenance (social plan S0a, copied, not moved; main 34a41890; listed in toolkit-provenance.tsv):
 *   extractor-sites/.../tiktok/TikTokUrls.kt  isPlayerMedia, MEDIA_HOSTS (TikTok's video files)
 *   extractor-sites/.../x/XSyndication.kt  VIDEO_HOST (X's video files)
 */
package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionFailure.GEO_RESTRICTED
import com.alal.yft.extractor.api.SiteExtractionFailure.LOGIN_REQUIRED
import com.alal.yft.extractor.api.SiteExtractionFailure.NO_MEDIA_FOUND
import com.alal.yft.extractor.api.SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE
import com.alal.yft.extractor.api.SiteExtractionFailure.RESPONSE_CHANGED
import com.alal.yft.extractor.master.lookup.AskLabels
import java.net.URI

/**
 * Social plan S0a (`MASTER_KEY_SOCIAL_PLAN.md` §7.1): what one census slot's kind expects of a
 * lookup. [rows]: the kind should download (a video kind); otherwise the site's final answer
 * about the post, one of [reasons], is the right answer and anything else is "not as expected".
 * [rowsWithSession]: a sign-in-only kind downloads when the browser has the site's session.
 * [minHeight]: the tallest row should be at least this tall (a post only the desktop page lists
 * in HD). Social plan 0.4: [ambiguousWithoutSession]: main's reasons that, without the site's
 * session in the browser, say nothing about this kind (Instagram asks every signed-out visitor to
 * sign in, S0d): such a row is neither as expected nor not, but "ambiguous (no session)".
 */
data class Expect(
    val rows: Boolean,
    val reasons: Set<SiteExtractionFailure> = emptySet(),
    val minHeight: Int? = null,
    val rowsWithSession: Boolean = false,
    val ambiguousWithoutSession: Set<SiteExtractionFailure> = emptySet(),
) {
    /** Whether the slot should download, with or without the site's [session] in the browser. */
    fun wantsRows(session: Boolean): Boolean = rows || rowsWithSession && session
}

/** S0a: a census slot's kind ([label], §6's census lists) and what it [expect]s. */
data class Kind(val label: String, val expect: Expect)

/**
 * S0a: one site of the census: its way labels (main's [mainWays] in main's order, Master's
 * [masterWay], today's R8 contract), its [slots] (the owner's links, numbered from 1), the media
 * hosts its one-row check (V) may ask, and its labels for a record ([labels], a new instance per
 * census row: Facebook's tell the public page from the AVC ladder by order).
 */
class Site internal constructor(
    val id: String,
    val name: String,
    val mainWays: List<String>,
    val masterWay: String,
    private val mediaHosts: List<String>,
    val slots: List<Kind>,
    private val mediaPath: ((URI) -> Boolean)?,
    private val newLabels: () -> AskLabels,
) {
    /** A new set of labels for one census row's record. */
    fun labels(): AskLabels = newLabels()

    /** The kind of slot [slot] (from 1), or null outside the site's list. */
    fun kind(slot: Int): Kind? = slots.getOrNull(slot - 1)

    /** Whether V may ask [url]: HTTPS, no user info, one of the site's media hosts (and paths). */
    fun mediaHost(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null) return false
        val host = uri.host?.lowercase()?.trimEnd('.')?.takeIf(String::isNotEmpty) ?: return false
        if (mediaHosts.none { host == it || host.endsWith(".$it") }) return false
        return mediaPath?.invoke(uri) ?: true
    }

    override fun toString(): String = "Site($id, ${slots.size} slots)"
}

/**
 * S0a: the census's site table: the four sites, their way labels and their census lists (§6). The
 * labels name the way an ask belongs to from its address and identity alone (never a cookie's
 * value); the readings ([SiteReadings]) mark what a 2xx answer held. The census kinds:
 *
 * - **Facebook** (13): 3 videos, 3 reels, a watch link, a share link, a post with a video, a group
 *   post, a live replay; sign-in-only (rows with the session, else sign-in is asked; signed out,
 *   main's private or changed-page answer is ambiguous: 0.4 (c)); removed.
 * - **TikTok** (10): 4 single videos, a long video; a photo post (no media); private (private or
 *   sign-in); region-limited (region, or private: TikTok's status for one page is not final, P46);
 *   removed; a post only the desktop page lists in HD (rows, at least 720 lines).
 * - **Instagram** (10): 3 single videos, 3 reels, a carousel with videos; photo-only (no media);
 *   sign-in-only (as Facebook's); removed (signed out, main's sign-in answer is ambiguous: 0.4).
 * - **X** (8): 3 videos, a GIF, a quoted post's video, several videos; age-limited and removed. X's
 *   main answers a post its widget does not show (a 404, an empty answer, a tombstone) with no
 *   media and its own message (P48), so those kinds also accept [NO_MEDIA_FOUND].
 */
object SiteWays {
    /** Any site: a one-byte file check (`ExtractorHttpClient.probe`). */
    const val FILE_CHECK = "file check"

    const val PUBLIC_PAGE = "public page"
    const val PAGE = "page"
    const val AVC_LADDER = "AVC ladder"
    const val PLUGIN_PLAYER = "plugin player"

    const val PHONE_PAGE = "phone page"
    const val DESKTOP_PAGE = "desktop page"

    /** TikTok's hidden page (YFT's own WebView): not an ask of the client, so never on a record. */
    const val HIDDEN_PAGE = "hidden page"
    const val DOWNLOAD_SERVICE = "download service"
    const val EMBED_PLAYER = "embed player"

    const val APP_API = "app API"
    const val GRAPHQL = "GraphQL"
    const val EMBED_PAGE = "embed page"

    const val TWEET_RESULT = "tweet-result"

    private val video = Kind("video", Expect(rows = true))
    private val reel = Kind("reel", Expect(rows = true))
    private val single = Kind("single video", Expect(rows = true))
    private val removed = Kind("removed", Expect(rows = false, reasons = setOf(PRIVATE_OR_UNAVAILABLE)))
    private val signInOnly = Kind(
        "sign-in-only",
        Expect(rows = false, reasons = setOf(LOGIN_REQUIRED), rowsWithSession = true),
    )

    val FACEBOOK = Site(
        id = "facebook",
        name = "Facebook",
        mainWays = listOf(PUBLIC_PAGE, PAGE, AVC_LADDER),
        masterWay = PLUGIN_PLAYER,
        mediaHosts = listOf("fbcdn.net"),
        slots = List(3) { video } + List(3) { reel } + listOf(
            Kind("watch link", Expect(rows = true)),
            Kind("share link", Expect(rows = true)),
            Kind("post with a video", Expect(rows = true)),
            Kind("group post", Expect(rows = true)),
            Kind("live replay", Expect(rows = true)),
            // 0.4 (c): signed out, Facebook answers a friends-only post as it answers a missing one,
            // so without the session that answer is neither way; no sign-in answer is guessed.
            Kind("sign-in-only", signInOnly.expect.copy(ambiguousWithoutSession = setOf(PRIVATE_OR_UNAVAILABLE, RESPONSE_CHANGED))),
            removed,
        ),
        mediaPath = null,
        newLabels = ::FacebookLabels,
    )

    val TIKTOK = Site(
        id = "tiktok",
        name = "TikTok",
        mainWays = listOf(PHONE_PAGE, DESKTOP_PAGE, HIDDEN_PAGE, DOWNLOAD_SERVICE),
        masterWay = EMBED_PLAYER,
        mediaHosts = listOf(
            "tiktok.com", "tiktokcdn.com", "tiktokcdn-us.com", "tiktokcdn-eu.com",
            "tiktokv.com", "tiktokv.us", "tiktokv.eu", "byteoversea.com",
        ),
        slots = List(4) { single } + listOf(
            Kind("long video", Expect(rows = true)),
            Kind("photo post", Expect(rows = false, reasons = setOf(NO_MEDIA_FOUND))),
            Kind("private", Expect(rows = false, reasons = setOf(PRIVATE_OR_UNAVAILABLE, LOGIN_REQUIRED))),
            Kind("region-limited", Expect(rows = false, reasons = setOf(GEO_RESTRICTED, PRIVATE_OR_UNAVAILABLE))),
            removed,
            Kind("desktop-only HD", Expect(rows = true, minHeight = 720)),
        ),
        // Main's TikTokUrls.isPlayerMedia: a video file's address, not any page of the site.
        mediaPath = { uri ->
            val path = uri.rawPath.orEmpty()
            path.contains("/video/tos/") || path.startsWith("/aweme/v1/play") ||
                uri.rawQuery.orEmpty().contains("mime_type=video_mp4")
        },
        newLabels = ::TikTokLabels,
    )

    val INSTAGRAM = Site(
        id = "instagram",
        name = "Instagram",
        mainWays = listOf(APP_API, GRAPHQL, PAGE, EMBED_PAGE),
        masterWay = EMBED_PAGE,
        mediaHosts = listOf("cdninstagram.com", "fbcdn.net"),
        slots = List(3) { single } + List(3) { reel } + listOf(
            Kind("carousel with videos", Expect(rows = true)),
            Kind("photo-only", Expect(rows = false, reasons = setOf(NO_MEDIA_FOUND))),
            signInOnly,
            // 0.4: Instagram answers every signed-out ask with sign-in (S0d), so without the
            // session that answer cannot tell a removed post from a shown one.
            Kind("removed", removed.expect.copy(ambiguousWithoutSession = setOf(LOGIN_REQUIRED))),
        ),
        mediaPath = null,
        newLabels = ::InstagramLabels,
    )

    val X = Site(
        id = "x",
        name = "X",
        mainWays = listOf(TWEET_RESULT),
        masterWay = TWEET_RESULT,
        mediaHosts = listOf("video.twimg.com"),
        slots = List(3) { video } + listOf(
            Kind("GIF", Expect(rows = true)),
            Kind("quoted post's video", Expect(rows = true)),
            Kind("several videos", Expect(rows = true)),
            Kind("age-limited", Expect(rows = false, reasons = setOf(LOGIN_REQUIRED, PRIVATE_OR_UNAVAILABLE, NO_MEDIA_FOUND))),
            Kind("removed", Expect(rows = false, reasons = setOf(PRIVATE_OR_UNAVAILABLE, NO_MEDIA_FOUND))),
        ),
        mediaPath = null,
        newLabels = ::XLabels,
    )

    /** The census's sites, in the screen's order. */
    val ALL: List<Site> = listOf(FACEBOOK, TIKTOK, INSTAGRAM, X)

    /** The site with [id] (main's site ID), or null. */
    fun site(id: String): Site? = ALL.firstOrNull { it.id == id }
}
