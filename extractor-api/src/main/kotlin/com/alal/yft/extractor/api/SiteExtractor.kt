package com.alal.yft.extractor.api

import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import com.alal.yft.core.model.logging.SensitiveValueRedactor
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate

/**
 * Canonical identity of a page a site adapter recognizes.
 *
 * [canonicalPageUrl] is the stable, shareable address for the same content, so short links and
 * tracking parameters collapse onto one identity. [contentId] is the site's own identifier and is
 * never guessed; an adapter that cannot read it reports no identity at all.
 */
data class SitePageIdentity(
    val siteId: String,
    val contentId: String,
    val canonicalPageUrl: String,
    /**
     * True when [contentId] is a short-link code, so the real content address is still unknown.
     *
     * Short links cannot be expanded offline, and guessing the numeric ID would be fabrication.
     * The adapter resolves the redirect during extraction instead.
     */
    val requiresCanonicalResolution: Boolean = false,
) {
    init {
        require(siteId.isNotBlank())
        require(contentId.isNotBlank())
        require(canonicalPageUrl.startsWith("https://")) {
            "A canonical page URL must be HTTPS"
        }
    }
}

/**
 * Everything an adapter may use for one extraction.
 *
 * [requestContext] carries the user's own browser session. Adapters replay it only to the site
 * they matched and never persist or log it.
 */
data class SiteExtractionRequest(
    val identity: SitePageIdentity,
    val requestContext: BrowserRequestContext,
    val nowEpochMs: Long,
) {
    override fun toString(): String = buildString {
        append("SiteExtractionRequest(siteId=")
        append(identity.siteId)
        append(", contentId=")
        append(identity.contentId)
        append(", canonicalPageUrl=")
        append(SensitiveValueRedactor.redact(identity.canonicalPageUrl))
        append(", nowEpochMs=")
        append(nowEpochMs)
        append(')')
    }
}

/** Closed set of reasons an adapter can fail, so the UI never has to parse a message. */
enum class SiteExtractionFailure {
    /** The adapter does not recognize this URL. */
    UNSUPPORTED_URL,

    /** The adapter is turned off by a feature flag. */
    ADAPTER_DISABLED,

    /** The page loaded but exposes no downloadable media, for example an image-only post. */
    NO_MEDIA_FOUND,

    /** The site requires the user to be signed in on the page first. */
    LOGIN_REQUIRED,

    /** The content is private, age-restricted or removed. */
    PRIVATE_OR_UNAVAILABLE,

    /** The content is region-locked for this network. */
    GEO_RESTRICTED,

    /** The site protects this content with DRM. YFT does not attempt circumvention. */
    DRM_PROTECTED,

    /** The media link the page exposed has already expired. */
    EXPIRED_LINK,

    /** The page parsed, but its structure no longer matches what the adapter understands. */
    RESPONSE_CHANGED,

    /**
     * The site only exposes this media behind values its own player script computes, and those
     * values could not be computed here.
     *
     * YFT does not reimplement those scripts. When no player-script host is available, or the
     * host could not run the site's current script, the adapter reports this instead of
     * guessing a URL. The generic detector cannot help either, because the same protection
     * applies to it.
     */
    PLAYER_SCRIPT_REQUIRED,

    /** The site asked the client to slow down. */
    RATE_LIMITED,

    /** The site returned an unexpected HTTP status. */
    HTTP_STATUS,

    /** The response could not be read as the adapter expects. */
    MALFORMED_RESPONSE,

    /** The response exceeded the adapter's read budget. */
    RESPONSE_TOO_LARGE,

    /** The network was unreachable or the call timed out. */
    NETWORK,
}

sealed interface SiteExtractionResult {
    /**
     * Candidates the adapter is confident about.
     *
     * An adapter must never return a guessed or unrelated URL to avoid an empty result; an empty
     * extraction is reported as [SiteExtractionFailure.NO_MEDIA_FOUND] instead.
     */
    data class Success(val candidates: List<MediaCandidate>) : SiteExtractionResult {
        init {
            require(candidates.isNotEmpty()) {
                "A successful extraction must contain at least one candidate"
            }
        }
    }

    data class Failure(
        val reason: SiteExtractionFailure,
        val httpStatusCode: Int? = null,
        /** Short diagnostic steps only. Consumers sanitize again before copy/share. */
        val details: List<String> = emptyList(),
    ) : SiteExtractionResult {
        init {
            require(httpStatusCode == null || httpStatusCode in 100..599)
        }

        override fun toString(): String =
            "Failure(reason=$reason, httpStatusCode=$httpStatusCode, " +
                "details=${DiagnosticTextSanitizer.details(details)})"

        /** Whether falling back to the generic detector is worth attempting. */
        val allowsGenericFallback: Boolean
            get() = when (reason) {
                SiteExtractionFailure.DRM_PROTECTED,
                SiteExtractionFailure.LOGIN_REQUIRED,
                SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
                SiteExtractionFailure.GEO_RESTRICTED,
                SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED,
                -> false

                else -> true
            }
    }
}

/**
 * One isolated website adapter.
 *
 * Adapters live only in `:extractor-sites`. The browser UI and the download engine never parse
 * site markup, so a site change can break exactly one adapter and nothing else.
 */
interface SiteExtractor {
    /** Stable identifier used by feature flags, logs and the support matrix. */
    val id: String

    /** Human-readable site name for the support matrix and UI copy. */
    val displayName: String

    /**
     * Returns the canonical identity for [pageUrl], or null when this adapter does not handle it.
     *
     * This must stay pure and offline so adapter selection never performs network access.
     */
    fun identify(pageUrl: String): SitePageIdentity?

    /** Resolves real candidates for an already matched page. */
    suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult
}
