package com.alal.yft.extractor.master.toolkit

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.extractor.generic.classifier.MediaFileUrls
import com.alal.yft.extractor.generic.classifier.MediaUrlClassifier
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal object UrlPolicy {
    fun secure(url: String?): String? = url?.trim()?.takeIf {
        val uri = runCatching { URI(it) }.getOrNull() ?: return@takeIf false
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }

    fun origin(url: String?): String? {
        val uri = runCatching { URI(secure(url) ?: return null) }.getOrNull() ?: return null
        val port = uri.port.takeUnless { it == -1 || it == 443 }?.let { ":$it" }.orEmpty()
        return "https://${uri.host.lowercase()}$port"
    }

    fun samePage(first: String, second: String): Boolean =
        pageKey(first)?.let { it == pageKey(second) } == true

    private fun pageKey(url: String): String? {
        val uri = runCatching { URI(secure(url) ?: return null).normalize() }
            .getOrNull() ?: return null
        return origin(url) + uri.rawPath.orEmpty().ifEmpty { "/" } +
            uri.rawQuery?.let { "?$it" }.orEmpty()
    }

    fun whole(url: String): String = MediaFileUrls.wholeFile(url).substringBefore('#')

    fun resolve(pageUrl: String, value: String?): String? {
        val address = value?.trim()?.replace("&amp;", "&") ?: return null
        return runCatching { URI(pageUrl).resolve(address).toString() }
            .getOrNull()?.let(::secure)
    }

    fun expiry(url: String): Long? {
        val query = runCatching { URI(url).rawQuery }.getOrNull() ?: return null
        return query.split('&').mapNotNull { entry ->
            val key = entry.substringBefore('=').lowercase()
            val value = runCatching {
                URLDecoder.decode(entry.substringAfter('=', ""), StandardCharsets.UTF_8)
            }.getOrNull() ?: return@mapNotNull null
            val seconds = when (key) {
                "expire", "expires", "_nc_exp", "x-expires" -> value.toLongOrNull()
                "oe" -> value.toLongOrNull(16)
                else -> null
            } ?: return@mapNotNull null
            seconds.takeIf { it >= 0 && it <= Long.MAX_VALUE / 1_000 }?.times(1_000)
        }.minOrNull()
    }

    /**
     * Sensitive headers belong to an exact scheme/host/port, not merely a parent domain.
     * Once a redirect leaves that origin, the caller must not restore its credentials later.
     */
    fun context(
        context: BrowserRequestContext,
        credentialUrl: String,
        targetUrl: String,
        pageUrl: String,
        captured: Boolean = false,
    ): BrowserRequestContext {
        val observedReferer = context.observedHeaders.entries
            .firstOrNull { it.key.equals("Referer", true) }?.value?.let(::secure)
        val referer = if (captured && observedReferer != null) {
            observedReferer.substringBefore('#')
        } else if (origin(pageUrl) == origin(targetUrl)) {
            pageUrl.substringBefore('#')
        } else {
            origin(pageUrl)?.let { "$it/" }
        }
        if (origin(credentialUrl) != null && origin(credentialUrl) == origin(targetUrl)) {
            return context.copy(pageUrl = referer)
        }
        return BrowserRequestContext(
            pageUrl = referer,
            userAgent = context.userAgent,
            cookie = null,
            observedHeaders = context.observedHeaders.filterKeys {
                it.lowercase() in PUBLIC_HEADERS
            },
        )
    }

    fun publicHeaders(headers: Map<String, String>): Map<String, String> =
        headers.filterKeys { it.lowercase() in PUBLIC_HEADERS }.mapValues { (name, value) ->
            if (name.equals("Referer", true)) origin(value)?.let { "$it/" }.orEmpty() else value
        }.filterValues(String::isNotBlank)

    /** Reuse an adapter's grouping namespace, but only with an observed/caller-supplied ID. */
    fun videoKey(pageUrl: String, contentId: String): String {
        val host = runCatching { URI(pageUrl).host?.lowercase() }.getOrNull().orEmpty()
        fun belongs(domain: String) = host == domain || host.endsWith(".$domain")
        val site = when {
            belongs("youtube.com") || belongs("youtu.be") -> "youtube"
            belongs("facebook.com") || belongs("fb.watch") -> "facebook"
            belongs("tiktok.com") -> "tiktok"
            belongs("instagram.com") -> "instagram"
            belongs("x.com") || belongs("twitter.com") -> "x"
            belongs("vimeo.com") -> "vimeo"
            else -> "master:$host"
        }
        return "$site:$contentId"
    }

    fun looksLikeAd(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return true
        val host = uri.host.orEmpty().lowercase()
        val path = uri.path.orEmpty().lowercase()
        return AD_HOSTS.any { host == it || host.endsWith(".$it") } ||
            AD_PATH.containsMatchIn(path)
    }

    /**
     * Social plan task 18 (O1, S4's phone session of 2026-10-23): a site's own build resource —
     * Meta's static-resource path (`/rsrc.php/`: its pages' scripts, styles and sounds, and the
     * plugin player's stand-in file, one 400 × 224 frame of 0.03 s) — is never a post's video.
     */
    fun buildResource(url: String): Boolean =
        runCatching { URI(url).path }.getOrNull().orEmpty().contains(BUILD_RESOURCE_PATH, ignoreCase = true)

    /** The extension of [url]'s file name (lowercase; "" for none). */
    fun extension(url: String): String =
        runCatching { URI(url).path }.getOrNull().orEmpty()
            .substringAfterLast('/').substringAfterLast('.', "").lowercase()

    /** Whether [url]'s file name ends in a media file's extension (`.mp4`, `.m3u8`, `.mpd`, …). */
    fun mediaExtension(url: String): Boolean {
        val ext = extension(url)
        return ext == "m3u8" || ext == "mpd" || MediaUrlClassifier.isDirectExtension(ext)
    }

    /** Whether one of [url]'s path segments (not its query) is exactly [segment]. */
    fun pathSegment(url: String, segment: String): Boolean =
        segment.isNotEmpty() && runCatching { URI(url).path }.getOrNull().orEmpty().split('/').any { it == segment }

    /** T10 (main VimeoConfigParser.PLAUSIBLE_EXPIRY_SECONDS): epoch seconds a config may state. */
    val PLAUSIBLE_EXPIRY_SECONDS = 1_400_000_000L..4_102_444_800L

    private const val BUILD_RESOURCE_PATH = "/rsrc.php/"
    private val PUBLIC_HEADERS = setOf("user-agent", "accept", "accept-language", "referer")
    private val AD_HOSTS = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
    )
    private val AD_PATH = Regex("""/(?:ads?|adverts?|preroll|pre-roll|vast)(?:/|[._-]|$)""")
}