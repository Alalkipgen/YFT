package com.alal.yft.extractor.sites.vimeo

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateConfidence
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SitePageIdentity

/**
 * Vimeo adapter for public, unlisted and non-DRM clips.
 *
 * The adapter reads the clip page the user's browser already receives and, when that page only
 * names its player configuration, follows that address on Vimeo's own player host. It performs no
 * signing, no DRM handling and no password, paywall or access-control bypass: a clip the user
 * cannot open in the browser fails with a structured reason.
 */
class VimeoExtractor(
    private val http: ExtractorHttpClient,
    private val maxPageBytes: Long = DEFAULT_MAX_PAGE_BYTES,
    private val maxConfigBytes: Long = DEFAULT_MAX_CONFIG_BYTES,
) : SiteExtractor {
    override val id: String = VimeoUrls.SITE_ID

    override val displayName: String = "Vimeo"

    override fun identify(pageUrl: String): SitePageIdentity? = VimeoUrls.identify(pageUrl)

    override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
        require(request.identity.siteId == id) { "This adapter only handles Vimeo identities" }
        val pageUrl = request.identity.canonicalPageUrl

        val page = when (
            val result = http.get(
                url = pageUrl,
                headers = pageHeaders(request.requestContext),
                maxBodyBytes = maxPageBytes,
            )
        ) {
            is ExtractorHttpResult.Failure -> return SiteExtractionResult.Failure(
                reason = result.reason,
                httpStatusCode = result.statusCode,
            )

            is ExtractorHttpResult.Success -> result
        }

        val fetched = when (val outcome = VimeoConfigParser.parsePage(page.body)) {
            is VimeoPageOutcome.InlineConfig -> ConfigFetch.Loaded(outcome.json)
            is VimeoPageOutcome.ConfigAddress -> configFetch(outcome.url, pageUrl, request)
            is VimeoPageOutcome.Failure -> if (
                outcome.reason == SiteExtractionFailure.RESPONSE_CHANGED
            ) {
                // The page markup changed, but Vimeo's own player configuration is still
                // addressable for this clip, so the adapter asks for it directly.
                configFetch(fallbackConfigUrl(request.identity), pageUrl, request)
            } else {
                ConfigFetch.Failed(SiteExtractionResult.Failure(outcome.reason))
            }
        }
        val configJson = when (fetched) {
            is ConfigFetch.Failed -> return fetched.failure
            is ConfigFetch.Loaded -> fetched.json
        }

        val clip = when (val parsed = VimeoConfigParser.parseConfig(configJson)) {
            is VimeoParseResult.Failure -> return SiteExtractionResult.Failure(parsed.reason)
            is VimeoParseResult.Success -> parsed.clip
        }

        // Vimeo signs every file in one configuration with a single expiry, so an expired
        // configuration cannot be queued and has to be reloaded instead.
        val expiresAtEpochMs = clip.expiresAtEpochMs
        if (expiresAtEpochMs != null && expiresAtEpochMs <= request.nowEpochMs) {
            return SiteExtractionResult.Failure(SiteExtractionFailure.EXPIRED_LINK)
        }

        val candidates = clip.renditions.map { rendition ->
            MediaCandidate(
                pageUrl = pageUrl,
                mediaUrl = rendition.url,
                sources = setOf(CandidateSource.MANIFEST),
                kind = when (rendition.delivery) {
                    VimeoDelivery.PROGRESSIVE -> MediaKind.DIRECT
                    VimeoDelivery.HLS -> MediaKind.HLS
                    VimeoDelivery.DASH -> MediaKind.DASH
                },
                mimeType = rendition.mimeType,
                title = displayTitle(clip, rendition.label),
                thumbnailUrl = clip.thumbnailUrl,
                durationMillis = clip.durationMillis,
                contentLengthBytes = rendition.sizeBytes,
                requestContext = mediaContext(pageUrl, request.requestContext),
                confidence = CandidateConfidence.HIGH,
                expiresAtEpochMs = expiresAtEpochMs,
                drmHint = false,
                observedAtEpochMs = request.nowEpochMs,
            )
        }
        return SiteExtractionResult.Success(candidates)
    }

    private sealed interface ConfigFetch {
        data class Loaded(val json: String) : ConfigFetch

        data class Failed(val failure: SiteExtractionResult.Failure) : ConfigFetch
    }

    private suspend fun configFetch(
        configUrl: String,
        pageUrl: String,
        request: SiteExtractionRequest,
    ): ConfigFetch {
        // Only Vimeo's own player host is ever followed, so a changed page cannot point the
        // extraction at a third-party endpoint with the user's session attached.
        if (!VimeoUrls.isConfigUrl(configUrl)) {
            return ConfigFetch.Failed(
                SiteExtractionResult.Failure(SiteExtractionFailure.RESPONSE_CHANGED),
            )
        }
        return when (
            val result = http.get(
                url = configUrl,
                headers = configHeaders(pageUrl, request.requestContext),
                maxBodyBytes = maxConfigBytes,
            )
        ) {
            is ExtractorHttpResult.Failure -> ConfigFetch.Failed(
                SiteExtractionResult.Failure(result.reason, result.statusCode),
            )

            is ExtractorHttpResult.Success -> ConfigFetch.Loaded(result.body)
        }
    }

    private fun fallbackConfigUrl(identity: SitePageIdentity): String = VimeoUrls.configUrl(
        videoId = identity.contentId,
        unlistedHash = VimeoUrls.unlistedHashOf(identity.canonicalPageUrl),
    )

    /** Title stays metadata-only: the clip title, the owner name, then the quality label. */
    private fun displayTitle(clip: VimeoClip, label: String?): String? {
        val base = clip.title?.trim()?.takeIf(String::isNotEmpty)
            ?: clip.ownerName?.trim()?.takeIf(String::isNotEmpty)?.let { "$it on Vimeo" }
            ?: return label
        return if (label.isNullOrBlank()) base else "$base — $label"
    }

    private fun pageHeaders(context: BrowserRequestContext): Map<String, String> = buildMap {
        context.userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
        context.cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
        put("Accept-Language", "en-US,en;q=0.9")
        put("Referer", "https://vimeo.com/")
    }

    /** The configuration request is anchored to the clip page, as the embedded player does. */
    private fun configHeaders(
        pageUrl: String,
        context: BrowserRequestContext,
    ): Map<String, String> = buildMap {
        context.userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
        context.cookie?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
        put("Accept", "application/json, text/plain;q=0.9")
        put("Accept-Language", "en-US,en;q=0.9")
        put("Referer", pageUrl)
    }

    /**
     * Media requests keep the user agent and cookie but are re-anchored to the clip page.
     *
     * The downstream resolver strips these again for cross-origin hops, so a CDN on another origin
     * never receives the session cookie.
     */
    private fun mediaContext(
        pageUrl: String,
        context: BrowserRequestContext,
    ): BrowserRequestContext = BrowserRequestContext(
        pageUrl = pageUrl,
        userAgent = context.userAgent,
        cookie = context.cookie,
    )

    companion object {
        const val DEFAULT_MAX_PAGE_BYTES: Long = 3L * 1024 * 1024
        const val DEFAULT_MAX_CONFIG_BYTES: Long = 1L * 1024 * 1024
    }
}
