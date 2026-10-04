package com.alal.yft.extractor.api

/**
 * Narrow bridge to the attestation a site's own web player performs before it streams.
 *
 * YouTube's web player runs YouTube's own BotGuard script, which returns a proof-of-origin
 * token that the player attaches to its media requests. The owner allowed YFT to do the same
 * (ADR-006): the host fetches the site's own current challenge, runs the site's own script in
 * an isolated offscreen engine and returns only the token. YFT never reimplements the script.
 *
 * The interface lives here so adapters stay pure JVM code with a fake in tests, while the real
 * implementation lives in the Android app, which owns a WebView.
 */
interface PoTokenProvider {
    /** Whether a host is wired at all, so an adapter can skip the round trip early. */
    val isAvailable: Boolean

    suspend fun mint(request: PoTokenRequest): PoTokenResult
}

data class PoTokenRequest(
    /**
     * What the token is bound to: for YouTube's player token the video ID; for its media token
     * the video ID, the visitor data or the account's data-sync identifier, as the page says.
     */
    val contentBinding: String,
    /** Absolute HTTPS address of the site's player script, which names its attestation key. */
    val playerScriptUrl: String,
    /** Page the token is minted for, sent as the referer of the host's own requests. */
    val pageUrl: String,
) {
    init {
        require(contentBinding.isNotBlank()) { "A token must be bound to something" }
        require(playerScriptUrl.startsWith("https://")) { "A player script URL must be HTTPS" }
        require(pageUrl.startsWith("https://")) { "A token page URL must be HTTPS" }
    }

    override fun toString(): String =
        "PoTokenRequest(contentBinding=<${contentBinding.length} chars>, " +
            "playerScriptUrl=$playerScriptUrl)"
}

sealed interface PoTokenResult {
    /** A token for the requested binding. It is a session secret and never prints. */
    data class Minted(val token: String) : PoTokenResult {
        init {
            require(token.isNotBlank())
        }

        override fun toString(): String = "PoTokenResult.Minted(token=<${token.length} chars>)"
    }

    /** No host is wired, or this device cannot run one. */
    data object Unavailable : PoTokenResult

    /** The host tried and failed; the reason is already a structured adapter failure. */
    data class Failed(val reason: SiteExtractionFailure) : PoTokenResult
}

/**
 * Default provider for builds and tests with no attestation host.
 *
 * Adapters then continue without a token, which some media requests still accept.
 */
object NoPoTokenProvider : PoTokenProvider {
    override val isAvailable: Boolean = false

    override suspend fun mint(request: PoTokenRequest): PoTokenResult = PoTokenResult.Unavailable
}
