package com.alal.yft.detection.potoken

/**
 * Somewhere with a browser environment and no network that runs YouTube's BotGuard challenge.
 *
 * The real engine is an offscreen WebView ([WebViewBotGuardEngine]); tests use a fake.
 */
internal interface BotGuardEngine {
    val isAvailable: Boolean

    /** The browser identity the challenge runs under, sent with this engine's attestation calls. */
    suspend fun userAgent(): String?

    /** Loads the host page for [challenge], or null when the engine could not start. */
    suspend fun open(challenge: BotGuardProtocol.Challenge): BotGuardSession?
}

/** One loaded host page. Its minter lives in the page, so a session stays open while used. */
internal interface BotGuardSession {
    /** Runs the challenge and returns BotGuard's answer, or null. */
    suspend fun snapshot(): String?

    /** Hands the integrity token to BotGuard's minter; false when no minter came back. */
    suspend fun createMinter(integrityToken: String): Boolean

    /** A proof-of-origin token for [binding], or null. */
    suspend fun mint(binding: String): String?

    /** Destroys the page. Safe to call from any thread, more than once. */
    fun close()
}
