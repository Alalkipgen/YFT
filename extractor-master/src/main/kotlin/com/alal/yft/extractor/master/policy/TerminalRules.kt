package com.alal.yft.extractor.master.policy

import com.alal.yft.extractor.api.SiteExtractionFailure

/**
 * The single source for when Master must stop. The engine and the app's browser hook both read
 * this object, so the two can never drift apart.
 */
object TerminalRules {
    /** The site adapter's answer is final: no page sample, capture or media check follows. */
    val NEVER_FALLBACK: Set<SiteExtractionFailure> = setOf(
        SiteExtractionFailure.ADAPTER_DISABLED,
        SiteExtractionFailure.DRM_PROTECTED,
        SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        SiteExtractionFailure.GEO_RESTRICTED,
        SiteExtractionFailure.NETWORK,
        SiteExtractionFailure.RATE_LIMITED,
    )

    /** Master may read only what the user's own playback in the visible browser delivered. */
    val BROWSER_REQUIRED: Set<SiteExtractionFailure> = setOf(
        SiteExtractionFailure.LOGIN_REQUIRED,
        SiteExtractionFailure.BOT_CHECK,
        SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED,
    )

    fun blocksFallback(failure: SiteExtractionFailure): Boolean = failure in NEVER_FALLBACK

    fun needsAuthorizedPlayback(failure: SiteExtractionFailure): Boolean =
        failure in BROWSER_REQUIRED
}
