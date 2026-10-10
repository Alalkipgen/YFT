/*
 * Provenance (Master toolkit T9, copied, not moved; main 34a41890):
 *   extractor-sites/.../tiktok/TikTokExtractor.kt      mergedCookie, cookieFor, mediaCookie,
 *                                                      mediaHeaders
 *   extractor-sites/.../facebook/FacebookExtractor.kt  mediaContext (re-anchored to the page)
 *   extractor-sites/.../vimeo/VimeoExtractor.kt        configHeaders, mediaContext
 *   core-media/.../resolver/DefaultVariantResolver.kt  withPageOrigin (Origin from the page)
 *   extractor-api/.../ResponseCookie.kt                matches (depended on, not copied)
 * Adapted: one policy object over BrowserRequestContext; UrlPolicy.context stays the L1/L4 rule.
 */
package com.alal.yft.extractor.master.toolkit

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.extractor.api.ResponseCookie

/**
 * T9: the request policy. A cookie goes only to the origin it was read for (the tab's cookie to
 * the page's origin, an answer's cookie to the hosts it [ResponseCookie.matches]); Referer and
 * Origin are re-anchored to the page; a cross-origin hop strips every credential and the
 * stripped values are never restored later in the chain.
 */
internal object RequestPolicy {
    private val CREDENTIALS = setOf("cookie", "authorization", "proxy-authorization")

    /** Media context re-anchored to [pageUrl]; the tab cookie only when the origin is the page's. */
    fun mediaContext(
        pageUrl: String,
        context: BrowserRequestContext,
        mediaUrl: String,
    ): BrowserRequestContext = UrlPolicy.context(context, pageUrl, mediaUrl, pageUrl)

    /** The headers for one request to [targetUrl] made on behalf of [pageUrl]. */
    fun headers(
        pageUrl: String,
        context: BrowserRequestContext,
        targetUrl: String,
        answerCookies: List<ResponseCookie> = emptyList(),
    ): Map<String, String> = buildMap {
        context.userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
        val sameOrigin = UrlPolicy.origin(pageUrl) != null &&
            UrlPolicy.origin(pageUrl) == UrlPolicy.origin(targetUrl)
        if (sameOrigin) {
            put("Referer", pageUrl.substringBefore('#'))
        } else {
            UrlPolicy.origin(pageUrl)?.let {
                put("Referer", "$it/")
                put("Origin", it)
            }
        }
        val tabCookie = context.cookie.takeIf { sameOrigin }
        mergedCookie(tabCookie, answerCookies.filter { it.matches(targetUrl) })
            ?.let { put("Cookie", it) }
    }

    /**
     * The headers to send after a redirect from [fromUrl] to [toUrl]. Leaving the origin drops
     * the credentials and cuts Referer to the origin; returning later does not bring them back.
     */
    fun afterRedirect(headers: Map<String, String>, fromUrl: String, toUrl: String): Map<String, String> {
        val from = UrlPolicy.origin(fromUrl)
        if (from != null && from == UrlPolicy.origin(toUrl)) return headers
        return headers.filterKeys { it.lowercase() !in CREDENTIALS }.mapValues { (name, value) ->
            if (name.equals("Referer", true)) UrlPolicy.origin(value)?.let { "$it/" } ?: "" else value
        }.filterValues(String::isNotBlank)
    }

    /**
     * The [header]'s cookies with the [answer]'s ones put in: same-named pairs are replaced in
     * place, new ones are added at the end. Null when nothing is left.
     */
    fun mergedCookie(header: String?, answer: List<ResponseCookie>): String? {
        val pairs = LinkedHashMap<String, String>()
        header.orEmpty().split(';').map(String::trim).filter(String::isNotEmpty)
            .forEach { pair -> pairs.putIfAbsent(pair.substringBefore('=').trim(), pair) }
        answer.forEach { cookie -> pairs[cookie.name] = cookie.pair }
        return pairs.values.joinToString("; ").takeIf(String::isNotEmpty)
    }

    /** The cookies a browser would send to [url]: the tab's (same origin) plus matching answers. */
    fun cookieFor(
        url: String,
        pageUrl: String,
        tabCookie: String?,
        answer: List<ResponseCookie>,
    ): String? {
        val sameOrigin = UrlPolicy.origin(url) != null && UrlPolicy.origin(url) == UrlPolicy.origin(pageUrl)
        return mergedCookie(tabCookie.takeIf { sameOrigin }, answer.filter { it.matches(url) })
    }
}
