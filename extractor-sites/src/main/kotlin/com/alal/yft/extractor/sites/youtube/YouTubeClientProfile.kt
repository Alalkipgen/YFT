package com.alal.yft.extractor.sites.youtube

/**
 * One InnerTube client the adapter is willing to ask as YouTube's own web surfaces do.
 *
 * Client identifiers change often and a working one can stop working without notice, so every
 * profile lives in this single file. Replacing a broken profile is a one-file edit and needs no
 * change to the parser, the adapter or the app.
 *
 * No profile claims to be a phone, a TV box or any device the user does not have. These are the
 * same client names the watch page and the embedded player send from a browser, so the request
 * stays something the user's own session could have made.
 */
internal data class YouTubeClientProfile(
    val id: String,
    val clientName: String,
    /** Fallback version used only when the page does not state its own. */
    val fallbackClientVersion: String,
    /** Whether this client is expected to answer with unprotected stream URLs. */
    val expectsPlainUrls: Boolean,
    /** Whether the user's own cookie and user agent are replayed for this client. */
    val replaysSession: Boolean,
    val referer: String,
) {
    /**
     * Builds the player request body.
     *
     * Content-gate acknowledgements are deliberately absent: YFT does not tell YouTube that an
     * age or sensitivity check is satisfied, so a gated video fails with a structured reason
     * instead of being unlocked.
     */
    fun playerRequestBody(
        videoId: String,
        clientVersion: String?,
        visitorData: String?,
    ): String = buildString {
        append("{\"context\":{\"client\":{\"clientName\":\"")
        append(escape(clientName))
        append("\",\"clientVersion\":\"")
        append(escape(clientVersion?.takeIf { it.isNotBlank() } ?: fallbackClientVersion))
        append("\",\"hl\":\"en\",\"gl\":\"US\"")
        if (!visitorData.isNullOrBlank()) {
            append(",\"visitorData\":\"")
            append(escape(visitorData))
            append('"')
        }
        append("}},\"videoId\":\"")
        append(escape(videoId))
        append("\"}")
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when {
                character == '"' -> append("\\\"")
                character == '\\' -> append("\\\\")
                character.code < 0x20 -> append("\\u%04x".format(character.code))
                else -> append(character)
            }
        }
    }
}

internal object YouTubeClientProfiles {
    /**
     * The watch page's own client.
     *
     * It sees exactly what the signed-in user sees, which is the only client that can reach an
     * unlisted or members-only video the user legitimately has access to. Its streams are
     * normally protected, so it usually needs a player-script host.
     */
    val WEB = YouTubeClientProfile(
        id = "web",
        clientName = "WEB",
        fallbackClientVersion = "2.20240711.01.00",
        expectsPlainUrls = false,
        replaysSession = true,
        referer = "https://www.youtube.com/",
    )

    /**
     * The client YouTube serves to an embedded player.
     *
     * Embeds historically receive unprotected URLs because an embed cannot run the full player
     * script, which makes this the profile most likely to produce a directly usable link. It is
     * asked without the user's cookie so a public embed stays a public request.
     */
    val EMBEDDED = YouTubeClientProfile(
        id = "embedded",
        clientName = "WEB_EMBEDDED_PLAYER",
        fallbackClientVersion = "1.20240711.01.00",
        expectsPlainUrls = true,
        replaysSession = false,
        referer = "https://www.youtube.com/",
    )

    /**
     * Order the adapter tries.
     *
     * The embedded client goes first because a plain URL needs no player script at all; the web
     * client follows so private-to-the-user videos still resolve when a script host exists.
     */
    val DEFAULT_ORDER: List<YouTubeClientProfile> = listOf(EMBEDDED, WEB)
}