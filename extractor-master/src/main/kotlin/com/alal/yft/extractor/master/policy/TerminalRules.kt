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

    /**
     * R2: sites whose bot check, login or player-script answer is final. Their own adapter (or
     * the future YouTube module) is the only reader; capture never retries past these walls.
     * A host matches itself and every subdomain.
     */
    val WALLED_HOSTS: Set<String> = setOf(
        "youtube.com", "youtu.be", "youtube-nocookie.com",
        "reddit.com", "redd.it",
    )

    /** YouTube hosts: only the site adapter (later the YouTube module) answers them. */
    val YOUTUBE_HOSTS: Set<String> = setOf("youtube.com", "youtu.be", "youtube-nocookie.com")

    fun blocksFallback(failure: SiteExtractionFailure): Boolean = failure in NEVER_FALLBACK

    /** [blocksFallback], plus R2's walled hosts for the browser-only failures. */
    fun blocksFallback(failure: SiteExtractionFailure, pageUrl: String?): Boolean =
        blocksFallback(failure) || failure in BROWSER_REQUIRED && walled(pageUrl)

    fun needsAuthorizedPlayback(failure: SiteExtractionFailure): Boolean =
        failure in BROWSER_REQUIRED

    fun walled(url: String?): Boolean = hostIn(url, WALLED_HOSTS)

    fun youtube(url: String?): Boolean = hostIn(url, YOUTUBE_HOSTS)

    /** The host a browser reads: after any user info, before the port; a backslash ends it. */
    private fun hostIn(url: String?, domains: Set<String>): Boolean {
        val authority = url?.trim()?.let(AUTHORITY::find)?.groupValues?.get(1) ?: return false
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase().trimEnd('.')
        return host.isNotEmpty() && domains.any { host == it || host.endsWith(".$it") }
    }

    private val AUTHORITY = Regex("""^[a-z][a-z0-9+.-]*://([^/?#\\]*)""", RegexOption.IGNORE_CASE)
}
