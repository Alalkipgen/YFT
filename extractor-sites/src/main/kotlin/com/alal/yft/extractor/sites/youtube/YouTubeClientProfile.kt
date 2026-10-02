package com.alal.yft.extractor.sites.youtube

/**
 * One YouTube player client the adapter asks, exactly as one of YouTube's own browser surfaces.
 *
 * Client identifiers change often and a working one can stop working without notice, so every
 * profile lives in this single file. Replacing a broken profile is a one-file edit and needs no
 * change to the parser, the adapter or the app.
 *
 * No profile claims to be a phone app, a TV or any device the user does not have, and the
 * user's own user agent is always sent unchanged.
 */
internal data class YouTubeClientProfile(
    val id: String,
    val clientName: String,
    /** The numeric client ID YouTube's own player sends in its client-name header. */
    val clientNameId: Int,
    val clientVersion: String,
    /** Whether the user's cookie is replayed for this client. */
    val replaysSession: Boolean,
    /** The embedding page, set only for the embedded player. */
    val thirdPartyEmbedUrl: String?,
) {
    /**
     * Builds the player request body.
     *
     * Content-gate acknowledgements are deliberately absent: YFT never tells YouTube that an age
     * or sensitivity check is satisfied, so a gated video fails with a structured reason instead
     * of being unlocked. The signature timestamp names the player version the response must
     * match, so the values that version computes are the ones YouTube accepts.
     */
    fun playerRequestBody(
        videoId: String,
        visitorData: String?,
        signatureTimestamp: Int?,
    ): String = buildString {
        append("{\"context\":{\"client\":{\"clientName\":\"")
        append(escape(clientName))
        append("\",\"clientVersion\":\"")
        append(escape(clientVersion))
        append("\",\"hl\":\"en\"")
        if (!visitorData.isNullOrBlank()) {
            append(",\"visitorData\":\"")
            append(escape(visitorData))
            append('"')
        }
        append('}')
        if (thirdPartyEmbedUrl != null) {
            append(",\"thirdParty\":{\"embedUrl\":\"")
            append(escape(thirdPartyEmbedUrl))
            append("\"}")
        }
        append("},\"videoId\":\"")
        append(escape(videoId))
        append("\",\"playbackContext\":{\"contentPlaybackContext\":{")
        append("\"html5Preference\":\"HTML5_PREF_WANTS\"")
        if (signatureTimestamp != null) {
            append(",\"signatureTimestamp\":")
            append(signatureTimestamp)
        }
        append("}}}")
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
     * The embedding page the embedded player reports.
     *
     * YouTube expects an embedded player to name a non-YouTube page, and YFT itself is the page
     * embedding the player, so it names its own project rather than inventing a third party.
     */
    const val YFT_EMBED_URL: String = "https://github.com/Alalkipgen/YFT"

    /** Used only when the watch page does not state its own client version. */
    const val FALLBACK_CLIENT_VERSION: String = "2.20260708.00.00"

    private const val EMBEDDED_CLIENT_NAME = "WEB_EMBEDDED_PLAYER"
    private const val EMBEDDED_CLIENT_ID = 56

    /** Browser clients a watch page may report for itself, with their numeric IDs. */
    private val PAGE_CLIENT_IDS = mapOf("WEB" to 1, "MWEB" to 2)

    private val CLIENT_VERSION = Regex("^2\\.\\d{8}\\.\\d{2}\\.\\d{2}$")

    /**
     * YouTube's embedded player.
     *
     * It is the one browser client that YouTube currently serves stream URLs to without a
     * per-session proof token, but only for videos their owners allow to be embedded. It is
     * asked without the user's cookie, so a public embed stays a public request.
     */
    fun embedded(signals: YouTubePageSignals): YouTubeClientProfile = YouTubeClientProfile(
        id = "embedded",
        clientName = EMBEDDED_CLIENT_NAME,
        clientNameId = EMBEDDED_CLIENT_ID,
        clientVersion = signals.clientVersion?.takeIf(CLIENT_VERSION::matches)
            ?: FALLBACK_CLIENT_VERSION,
        replaysSession = false,
        thirdPartyEmbedUrl = YFT_EMBED_URL,
    )

    /**
     * The client the watch page itself runs, with the user's own session.
     *
     * It sees exactly what the user's browser sees, so its verdict about a video is
     * authoritative. YouTube now also requires a per-session proof token before it streams to
     * this client, which YFT does not generate, so its links can be refused at download time.
     */
    fun page(signals: YouTubePageSignals): YouTubeClientProfile {
        val name = signals.clientName?.takeIf { it in PAGE_CLIENT_IDS } ?: "WEB"
        return YouTubeClientProfile(
            id = "page",
            clientName = name,
            clientNameId = PAGE_CLIENT_IDS.getValue(name),
            clientVersion = signals.clientVersion?.takeIf(CLIENT_VERSION::matches)
                ?: FALLBACK_CLIENT_VERSION,
            replaysSession = true,
            thirdPartyEmbedUrl = null,
        )
    }
}
