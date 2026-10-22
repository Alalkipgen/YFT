package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.lookup.LookupAsk
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Social plan S1a (2) (`MASTER_KEY_SOCIAL_PLAN.md` §7.3): the pages main read in one lookup, for
 * Master's rungs that read without asking — F2 (L3 rows on the pages already read, no fetch) and
 * the links main resolves first (Master reads them through main's resolved link: the final
 * address main's page answer reached). One per lookup, carried in the lookup's coroutine context
 * next to its labelled `LookupRecord`, in memory only and dropped with the lookup.
 * [MainPageRecorder] writes main's answers into it; a lookup without one is not kept.
 *
 * Kept per answer ([Page]): main's 2xx answer to a GET — the address asked, the final address
 * after redirects, the status, the content type, whether main's request carried the user's
 * credentials ([LookupAsk.authorized]) and the page itself; never a cookie the answer set, a
 * request header, a post or a file check. Bounded: at most [maxPages] answers listed (more are
 * counted, [unlisted]) and at most [maxChars] characters of pages; a page over the bound keeps its
 * addresses only (no body). Nothing is logged; [toString] shows counts only, never an address or
 * a page.
 *
 * Social plan S2b: [site], the lookup's site (main's site ID: `facebook`, `tiktok`), so the rows'
 * check (V, `SocialRowCheck`) asks only that site's media hosts; null (a lookup built before S2b):
 * the check's own site.
 *
 * Social plan S2c: main's GETs that got no 2xx answer ([Refusal]: a 404, another status, a network
 * failure) are kept too, as their address, status and main's reason only (no page, no header, no
 * cookie), at most [maxPages] of them: X's ladder judges main's refused answer without asking again
 * (a 404 from X's widget is no such post).
 *
 * Social plan S2d part 1: Instagram's four GETs (the app API when signed in, GraphQL, the post's
 * page, the embed page) fit [MAX_PAGES]; I2 reads each way's kept answer or refusal, no new ask.
 */
class MainPages(
    private val maxPages: Int = MAX_PAGES,
    private val maxChars: Long = MAX_CHARS,
    val site: String? = null,
) : AbstractCoroutineContextElement(Key) {
    /** One of main's 2xx GET answers; [body] null when the page was over the bound. */
    class Page(
        val url: String,
        val finalUrl: String,
        val statusCode: Int,
        val contentType: String?,
        val authorized: Boolean,
        val body: String?,
    ) {
        override fun toString(): String =
            "Page(status=$statusCode, authorized=$authorized, chars=${body?.length ?: "none"})"
    }

    /**
     * S2c: one of main's GETs that got no 2xx answer: the address asked, its status (null: no
     * answer, a network failure) and main's [reason] for it.
     */
    class Refusal(val url: String, val statusCode: Int?, val reason: SiteExtractionFailure) {
        override fun toString(): String = "Refusal(status=$statusCode, reason=$reason)"
    }

    private val lock = Any()
    private val listed = ArrayList<Page>()
    private val refused = ArrayList<Refusal>()
    private var keptChars = 0L
    private var unlistedTotal = 0

    /** Main's pages in the order main's answers came. */
    fun pages(): List<Page> = synchronized(lock) { listed.toList() }

    /** The final address of main's first listed 2xx GET of [url], or null. */
    fun finalUrlOf(url: String): String? = synchronized(lock) { listed.firstOrNull { it.url == url }?.finalUrl }

    /** S2c: main's refused GETs in the order they came, at most [maxPages] (more are not kept). */
    fun refusals(): List<Refusal> = synchronized(lock) { refused.toList() }

    /** Main's 2xx GET answers not listed (over [maxPages]). */
    val unlisted: Int get() = synchronized(lock) { unlistedTotal }

    /**
     * S1a (3): the video Master reads for [identity], a link main resolves first (a share link, a
     * post; [SitePageIdentity.requiresCanonicalResolution]): the one video the final addresses of
     * main's listed page answers for it name ([identify]: the same site's, itself resolved). Null
     * when main read no such page in this lookup, none led to a video, or two led to different
     * videos (no guess). Another identity: itself.
     */
    fun resolved(identity: SitePageIdentity, identify: (String) -> SitePageIdentity?): SitePageIdentity? {
        if (!identity.requiresCanonicalResolution) return identity
        val finals = synchronized(lock) { listed.filter { it.url == identity.canonicalPageUrl }.map { it.finalUrl } }
        return finals.mapNotNull { url ->
            identify(url)?.takeIf { it.siteId == identity.siteId && !it.requiresCanonicalResolution }
        }.distinctBy { it.contentId }.singleOrNull()
    }

    /**
     * Main's answer [result] to its GET of [url] with [headers]: a success (2xx) is kept as a
     * [Page]; S2c: a failure as a [Refusal] (no page).
     */
    internal fun note(url: String, headers: Map<String, String>, result: ExtractorHttpResult) {
        val success = when (result) {
            is ExtractorHttpResult.Success -> result
            is ExtractorHttpResult.Failure -> {
                synchronized(lock) {
                    if (refused.size < maxPages) refused += Refusal(url, result.statusCode, result.reason)
                }
                return
            }
        }
        val authorized = LookupAsk(LookupAsk.GET, url, null, headers).authorized
        synchronized(lock) {
            if (listed.size >= maxPages) {
                unlistedTotal++
                return
            }
            val chars = success.body.length.toLong()
            val body = success.body.takeIf { keptChars + chars <= maxChars }
            if (body != null) keptChars += chars
            listed += Page(url, success.finalUrl, success.statusCode, success.contentType, authorized, body)
        }
    }

    override fun toString(): String = synchronized(lock) {
        "MainPages(pages=${listed.size}, bodies=${listed.count { it.body != null }}, unlisted=$unlistedTotal)"
    }

    companion object Key : CoroutineContext.Key<MainPages> {
        /**
         * More than main's Facebook GETs in one lookup (the public page, the page, the AVC ladder)
         * and TikTok's (S2b: the phone page, the desktop page, the download service); X's one (S2c:
         * the widget's answer); Instagram's four at most (S2d: the app API, GraphQL, the page, the
         * embed page).
         */
        const val MAX_PAGES = 4

        /** Main's page bound is 6 MiB; two such pages at most. */
        const val MAX_CHARS = 8L * 1024 * 1024
    }
}
