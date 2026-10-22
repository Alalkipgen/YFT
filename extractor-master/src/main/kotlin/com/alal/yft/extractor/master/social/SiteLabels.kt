/*
 * Provenance (social plan S0a, copied, not moved; main 34a41890; listed in toolkit-provenance.tsv):
 *   extractor-sites/.../facebook/FacebookPageIdentity.kt  AVC_LADDER_USER_AGENT (desktop Safari,
 *                                        the public page and the AVC ladder, never with a cookie)
 *   extractor-sites/.../facebook/FacebookUrls.kt  LONG_HOSTS, SHORT_HOSTS (Facebook's hosts)
 *   extractor-sites/.../tiktok/TikTokAgents.kt  desktop (the desktop page's Windows agent)
 *   extractor-sites/.../tiktok/TikTokDownloadService.kt  SERVICE_ORIGIN (the download service)
 *   extractor-sites/.../instagram/InstagramExtractor.kt  ask, graphQlUrl (the app API's and
 *                                        GraphQL's paths; the post's page is the rest)
 *   extractor-sites/.../instagram/InstagramUrls.kt  embedUrl (the embed page's path)
 *   extractor-sites/.../x/XSyndication.kt  HOST, url (the widget's tweet-result)
 */
package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.master.lookup.AskLabels
import com.alal.yft.extractor.master.lookup.AskReading
import com.alal.yft.extractor.master.lookup.LookupAsk
import java.net.URI

/**
 * S0a: Facebook's ways. The plugin player is Master's; desktop Safari without a cookie is main's
 * public page before main's page with the browser's identity and its AVC ladder after it (main asks
 * them in that order, one at a time); any other Facebook ask is main's page.
 */
internal class FacebookLabels : AskLabels {
    private val lock = Any()
    private var pageAsked = false

    override fun way(ask: LookupAsk): String? {
        if (ask.method == LookupAsk.PROBE) return SiteWays.FILE_CHECK
        val uri = AskParts.uri(ask.url) ?: return null
        if (!AskParts.hostIn(uri, FACEBOOK_HOSTS, subdomains = false)) return null
        if (uri.path.orEmpty().lowercase() == PLUGIN_PATH) return SiteWays.PLUGIN_PLAYER
        val public = AskParts.header(ask, USER_AGENT) == SAFARI && AskParts.header(ask, COOKIE).isNullOrBlank()
        return synchronized(lock) {
            when {
                !public -> SiteWays.PAGE.also { pageAsked = true }
                pageAsked -> SiteWays.AVC_LADDER
                else -> SiteWays.PUBLIC_PAGE
            }
        }
    }

    override fun read(ask: LookupAsk, way: String?, result: ExtractorHttpResult.Success): AskReading? =
        if (way in PAGES) SiteReadings.facebookPage(result) else null

    private companion object {
        const val PLUGIN_PATH = "/plugins/video.php"
        const val SAFARI = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.5 Safari/605.1.15"
        val PAGES = setOf(SiteWays.PUBLIC_PAGE, SiteWays.PAGE, SiteWays.AVC_LADDER, SiteWays.PLUGIN_PLAYER)
    }
}

/**
 * S0a: TikTok's ways: the download service on its own host; on TikTok, the embed player (Master's)
 * by its path, the desktop page by its Windows agent, else the phone page.
 */
internal class TikTokLabels : AskLabels {
    override fun way(ask: LookupAsk): String? {
        if (ask.method == LookupAsk.PROBE) return SiteWays.FILE_CHECK
        val uri = AskParts.uri(ask.url) ?: return null
        if (AskParts.hostIn(uri, listOf(SERVICE_HOST))) return SiteWays.DOWNLOAD_SERVICE
        if (!AskParts.hostIn(uri, listOf(TIKTOK_HOST))) return null
        if (uri.path.orEmpty().startsWith(EMBED_PATH)) return SiteWays.EMBED_PLAYER
        val agent = AskParts.header(ask, USER_AGENT).orEmpty()
        return if (agent.contains(DESKTOP_MARK)) SiteWays.DESKTOP_PAGE else SiteWays.PHONE_PAGE
    }

    override fun read(ask: LookupAsk, way: String?, result: ExtractorHttpResult.Success): AskReading? = when (way) {
        SiteWays.PHONE_PAGE, SiteWays.DESKTOP_PAGE -> SiteReadings.tiktokPage(result)
        SiteWays.EMBED_PLAYER -> SiteReadings.tiktokEmbed(result)
        SiteWays.DOWNLOAD_SERVICE -> SiteReadings.downloadService(result)
        else -> null
    }

    private companion object {
        const val SERVICE_HOST = "tikwm.com"
        const val TIKTOK_HOST = "tiktok.com"
        const val EMBED_PATH = "/embed/"
        const val DESKTOP_MARK = "Windows NT"
    }
}

/** S0a: Instagram's ways by path: the app API, GraphQL, the embed page, else the post's page. */
internal class InstagramLabels : AskLabels {
    override fun way(ask: LookupAsk): String? {
        if (ask.method == LookupAsk.PROBE) return SiteWays.FILE_CHECK
        val uri = AskParts.uri(ask.url) ?: return null
        if (!AskParts.hostIn(uri, listOf(INSTAGRAM_HOST))) return null
        val path = uri.path.orEmpty().lowercase()
        return when {
            path == TAB_FETCH_PATH || path.startsWith("$TAB_FETCH_PATH/") -> SiteWays.TAB_FETCH
            path.startsWith(API_PATH) -> SiteWays.APP_API
            path.startsWith(GRAPHQL_PATH) -> SiteWays.GRAPHQL
            path.contains(EMBED_PATH) -> SiteWays.EMBED_PAGE
            else -> SiteWays.PAGE
        }
    }

    override fun read(ask: LookupAsk, way: String?, result: ExtractorHttpResult.Success): AskReading? = when (way) {
        SiteWays.APP_API -> SiteReadings.instagramJson(result, appApi = true)
        SiteWays.GRAPHQL -> SiteReadings.instagramJson(result, appApi = false)
        SiteWays.PAGE, SiteWays.EMBED_PAGE -> SiteReadings.instagramPage(result)
        SiteWays.TAB_FETCH -> SiteReadings.instagramTabFetch(result)
        else -> null
    }

    private companion object {
        const val INSTAGRAM_HOST = "instagram.com"
        /** S2d part 1: the tab-fetch probe's query (`InstagramProbeQuery.ENDPOINT`). */
        const val TAB_FETCH_PATH = "/api/graphql"
        const val API_PATH = "/api/v1/"
        const val GRAPHQL_PATH = "/graphql/"
        const val EMBED_PATH = "/embed"
    }
}

/** S0a: X's one way, the widget's `tweet-result` (main's and Master's). */
internal class XLabels : AskLabels {
    override fun way(ask: LookupAsk): String? {
        if (ask.method == LookupAsk.PROBE) return SiteWays.FILE_CHECK
        val uri = AskParts.uri(ask.url) ?: return null
        if (!AskParts.hostIn(uri, listOf(WIDGET_HOST), subdomains = false)) return null
        return SiteWays.TWEET_RESULT.takeIf { uri.path.orEmpty().startsWith(WIDGET_PATH) }
    }

    override fun read(ask: LookupAsk, way: String?, result: ExtractorHttpResult.Success): AskReading? =
        if (way == SiteWays.TWEET_RESULT) SiteReadings.tweetResult(result) else null

    private companion object {
        const val WIDGET_HOST = "cdn.syndication.twimg.com"
        const val WIDGET_PATH = "/tweet-result"
    }
}

/** Facebook's hosts (main's long and short ones), exactly: the labels' and the login wall's. */
internal val FACEBOOK_HOSTS = listOf(
    "facebook.com", "www.facebook.com", "m.facebook.com", "web.facebook.com", "mbasic.facebook.com", "fb.com",
    "www.fb.com", "fb.watch", "www.fb.watch",
)

private const val USER_AGENT = "User-Agent"
private const val COOKIE = "Cookie"

/** S0a: the parts of an ask the labels read: its address's host and path, a header by name. */
internal object AskParts {
    fun uri(url: String): URI? = runCatching { URI(url.trim()) }.getOrNull()
        ?.takeIf { it.scheme.equals("https", ignoreCase = true) && it.rawUserInfo == null && it.host != null }

    /** Whether [uri]'s host is one of [hosts] (or, with [subdomains], under one). */
    fun hostIn(uri: URI, hosts: List<String>, subdomains: Boolean = true): Boolean {
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        return hosts.any { host == it || subdomains && host.endsWith(".$it") }
    }

    /** [ask]'s first header named [name] (any case), or null. */
    fun header(ask: LookupAsk, name: String): String? =
        ask.headers.firstOrNull { (key, _) -> key.trim().equals(name, ignoreCase = true) }?.second
}
