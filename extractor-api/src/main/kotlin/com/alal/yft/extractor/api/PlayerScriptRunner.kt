package com.alal.yft.extractor.api

/**
 * Narrow bridge to the player script a site already serves to its own web player.
 *
 * Some sites hand their player a short JavaScript transform and expect the client to apply it to
 * a stream URL before use. YFT does not reimplement those transforms, and it never ships a copy
 * of them: the host evaluates the site's own script in the user's own session and returns only
 * the resulting values.
 *
 * The interface lives here so adapters stay pure JVM code with a fake in tests, while the real
 * implementation lives in the Android app next to the browser session that owns the script.
 */
interface PlayerScriptRunner {
    /** Whether a host is wired at all, so an adapter can fail early without a round trip. */
    val isAvailable: Boolean

    suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult
}

/** What a challenge asks the site's own script to compute. */
enum class PlayerScriptChallengeKind {
    /** A stream signature the site expects appended to the media URL. */
    SIGNATURE,

    /** A delivery-rate parameter the site expects echoed back in the media URL. */
    RATE_PARAM,
}

/**
 * One value the adapter needs computed.
 *
 * [key] is the adapter's own correlation key, so results come back without positional guessing.
 */
data class PlayerScriptChallenge(
    val key: String,
    val kind: PlayerScriptChallengeKind,
    val input: String,
) {
    init {
        require(key.isNotBlank())
        require(input.isNotBlank())
    }

    /** Challenge inputs are session-derived values, so they never reach a log verbatim. */
    override fun toString(): String =
        "PlayerScriptChallenge(key=$key, kind=$kind, input=${redactLength(input)})"
}

data class PlayerScriptRequest(
    /** Absolute HTTPS address of the site's own player script. */
    val playerScriptUrl: String,
    /** Page the script belongs to, used as the script's origin and referer. */
    val pageUrl: String,
    val challenges: List<PlayerScriptChallenge>,
    /**
     * True when the site additionally requires a per-session proof token for media delivery.
     *
     * The token is read from the same session; it is never hardcoded and never persisted.
     */
    val requiresSessionToken: Boolean = false,
) {
    init {
        require(playerScriptUrl.startsWith("https://")) { "A player script URL must be HTTPS" }
        require(pageUrl.startsWith("https://")) { "A player page URL must be HTTPS" }
        require(challenges.isNotEmpty() || requiresSessionToken) {
            "A player script request must ask for something"
        }
        require(challenges.map(PlayerScriptChallenge::key).toSet().size == challenges.size) {
            "Challenge keys must be unique"
        }
    }

    override fun toString(): String = buildString {
        append("PlayerScriptRequest(playerScriptUrl=")
        append(playerScriptUrl)
        append(", challenges=")
        append(challenges.size)
        append(", requiresSessionToken=")
        append(requiresSessionToken)
        append(')')
    }
}

sealed interface PlayerScriptResult {
    /**
     * Values the site's script produced, keyed by [PlayerScriptChallenge.key].
     *
     * A missing key means the script did not answer that challenge; the adapter drops that
     * stream rather than shipping an unsigned URL.
     */
    data class Success(
        val resolved: Map<String, String>,
        val sessionToken: String? = null,
    ) : PlayerScriptResult {
        /** Resolved values are session secrets, so only their shape is printable. */
        override fun toString(): String =
            "PlayerScriptResult.Success(resolved=${resolved.keys}, sessionToken=" +
                "${if (sessionToken == null) "absent" else "present"})"
    }

    /** No host is wired, or the host cannot run a script in this build. */
    data object Unavailable : PlayerScriptResult

    /** The host tried and failed; the reason is already a structured adapter failure. */
    data class Failed(val reason: SiteExtractionFailure) : PlayerScriptResult
}

/**
 * Default runner for builds and tests with no player-script host.
 *
 * Adapters then report [SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED] instead of degrading into
 * guesswork, which keeps the failure honest in headless environments.
 */
object NoPlayerScriptRunner : PlayerScriptRunner {
    override val isAvailable: Boolean = false

    override suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult =
        PlayerScriptResult.Unavailable
}

private fun redactLength(value: String): String = "<${value.length} chars>"