package com.alal.yft.extractor.sites.youtube

/**
 * One YouTube player client the adapter asks.
 *
 * Client identifiers change often and a working one can stop working without notice, so every
 * profile lives in this single file. Replacing a broken profile is a one-file edit and needs no
 * change to the parser, the adapter or the app.
 *
 * Device clients present themselves as YouTube's own app on that device, with the identifiers,
 * user agent and device fields that app sends, which the owner allowed in ADR-006. Browser
 * clients keep the user's own user agent.
 */
internal data class YouTubeClientProfile(
    val id: String,
    val clientName: String,
    /** The numeric client ID YouTube's own player sends in its client-name header. */
    val clientNameId: Int,
    val clientVersion: String,
    /** The user agent the client's own app sends, or null to keep the user's own agent. */
    val userAgent: String? = null,
    /** What the client's own app reports about its device; null for browser clients. */
    val device: YouTubeDevice? = null,
    /** Whether the user's cookie is replayed for this client. */
    val replaysSession: Boolean,
    /**
     * Whether this client's stream values are computed by YouTube's player script, so the
     * request names the player version the response must match.
     */
    val usesPlayerScript: Boolean,
    /** Whether YouTube expects this client's media requests to carry a proof-of-origin token. */
    val usesPoToken: Boolean = false,
    /** The embedding page, set only for the embedded player. */
    val thirdPartyEmbedUrl: String? = null,
) {
    /**
     * Builds the player request body.
     *
     * Content warnings that every viewer can click through are acknowledged, as YouTube's own
     * player does after that click (ADR-006). Age checks are not: an age-restricted video still
     * fails with a structured reason unless the user's own session may watch it. The signature
     * timestamp names the player version the response must match, so the values that version
     * computes are the ones YouTube accepts.
     */
    fun playerRequestBody(
        videoId: String,
        visitorData: String?,
        signatureTimestamp: Int?,
        poToken: String? = null,
    ): String = buildString {
        append("{\"context\":{\"client\":{")
        field("clientName", clientName)
        append(',')
        field("clientVersion", clientVersion)
        device?.let { device ->
            device.make?.let { append(','); field("deviceMake", it) }
            device.model?.let { append(','); field("deviceModel", it) }
            device.androidSdkVersion?.let { append(",\"androidSdkVersion\":").append(it) }
            append(',')
            field("osName", device.osName)
            append(',')
            field("osVersion", device.osVersion)
        }
        userAgent?.let { append(','); field("userAgent", it) }
        append(",\"hl\":\"en\",\"timeZone\":\"UTC\",\"utcOffsetMinutes\":0")
        if (!visitorData.isNullOrBlank()) {
            append(',')
            field("visitorData", visitorData)
        }
        append('}')
        if (thirdPartyEmbedUrl != null) {
            append(",\"thirdParty\":{")
            field("embedUrl", thirdPartyEmbedUrl)
            append('}')
        }
        append("},")
        field("videoId", videoId)
        append(",\"playbackContext\":{\"contentPlaybackContext\":{")
        append("\"html5Preference\":\"HTML5_PREF_WANTS\"")
        if (usesPlayerScript && signatureTimestamp != null) {
            append(",\"signatureTimestamp\":")
            append(signatureTimestamp)
        }
        append("}},\"contentCheckOk\":true,\"racyCheckOk\":true")
        if (!poToken.isNullOrBlank()) {
            append(",\"serviceIntegrityDimensions\":{")
            field("poToken", poToken)
            append('}')
        }
        append('}')
    }

    /** Device clients are named by their own app; their identifiers are not secret. */
    override fun toString(): String =
        "YouTubeClientProfile(id=$id, clientName=$clientName, clientVersion=$clientVersion)"

    private fun StringBuilder.field(name: String, value: String) {
        append('"').append(name).append("\":\"").append(escape(value)).append('"')
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

/** What a device client's own app reports about the device it runs on. */
internal data class YouTubeDevice(
    val make: String?,
    val model: String?,
    val osName: String,
    val osVersion: String,
    val androidSdkVersion: Int? = null,
)

internal object YouTubeClientProfiles {
    /**
     * Where the device client values come from: yt-dlp's `INNERTUBE_CLIENTS` table
     * (`yt_dlp/extractor/youtube/_base.py`, Unlicense) at this version. Refresh every device
     * value together from one yt-dlp version and update this marker.
     */
    const val DEVICE_VALUES_SOURCE: String = "yt-dlp 2026.08.19"

    /**
     * The embedding page the embedded player reports.
     *
     * YouTube expects an embedded player to name a non-YouTube page, and YFT itself is the page
     * embedding the player, so it names its own project rather than inventing a third party.
     */
    const val YFT_EMBED_URL: String = "https://github.com/Alalkipgen/YFT"

    /** Used only when the watch page does not state its own client version. */
    const val FALLBACK_CLIENT_VERSION: String = "2.20260708.00.00"

    /** The mobile web client's version (yt-dlp `mweb`). */
    const val MOBILE_CLIENT_VERSION: String = "2.20260708.05.00"

    private const val EMBEDDED_CLIENT_NAME = "WEB_EMBEDDED_PLAYER"
    private const val EMBEDDED_CLIENT_ID = 56
    private const val MOBILE_CLIENT_NAME = "MWEB"

    /** Browser clients a watch page may report for itself, with their numeric IDs. */
    private val PAGE_CLIENT_IDS = mapOf("WEB" to 1, MOBILE_CLIENT_NAME to 2)

    private val CLIENT_VERSION = Regex("^2\\.\\d{8}\\.\\d{2}\\.\\d{2}$")

    /**
     * YouTube's visionOS app (yt-dlp `visionos`).
     *
     * It is yt-dlp's first choice: its streams carry direct addresses that need neither the
     * player script nor a proof-of-origin token. It offers separate video and audio tracks
     * only, so on its own it gives audio downloads until video and audio can be merged (T17).
     * It is asked without the user's cookie, as the app it names would be.
     */
    val VISION_OS: YouTubeClientProfile = YouTubeClientProfile(
        id = "visionos",
        clientName = "VISIONOS",
        clientNameId = 101,
        clientVersion = "1.02",
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/26.0 Safari/605.1.15",
        device = YouTubeDevice(
            make = "Apple",
            model = "RealityDevice17,1",
            osName = "visionOS",
            osVersion = "26.5.23O471",
        ),
        replaysSession = false,
        usesPlayerScript = false,
    )

    /**
     * YouTube's Android app (yt-dlp `android`).
     *
     * Its answer includes the progressive 360p stream with both tracks and a direct address,
     * which is the video-with-sound download until merging exists. yt-dlp notes that YouTube
     * may start asking this client's media requests for a token; the later clients cover that.
     */
    val ANDROID: YouTubeClientProfile = YouTubeClientProfile(
        id = "android",
        clientName = "ANDROID",
        clientNameId = 3,
        clientVersion = "21.26.364",
        userAgent = "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",
        device = YouTubeDevice(
            make = null,
            model = null,
            osName = "Android",
            osVersion = "11",
            androidSdkVersion = 30,
        ),
        replaysSession = false,
        usesPlayerScript = false,
    )

    /** Device clients in the order they are asked. */
    val DEVICE_CLIENTS: List<YouTubeClientProfile> = listOf(VISION_OS, ANDROID)

    /**
     * YouTube's embedded player.
     *
     * It serves stream addresses without a proof-of-origin token, but only for videos their
     * owners allow to be embedded. It is asked without the user's cookie, so a public embed
     * stays a public request.
     */
    fun embedded(signals: YouTubePageSignals): YouTubeClientProfile = YouTubeClientProfile(
        id = "embedded",
        clientName = EMBEDDED_CLIENT_NAME,
        clientNameId = EMBEDDED_CLIENT_ID,
        clientVersion = signals.clientVersion?.takeIf(CLIENT_VERSION::matches)
            ?: FALLBACK_CLIENT_VERSION,
        replaysSession = false,
        usesPlayerScript = true,
        thirdPartyEmbedUrl = YFT_EMBED_URL,
    )

    /**
     * The client the watch page itself runs, with the user's own session.
     *
     * It sees exactly what the user's browser sees, so its verdict about a video is
     * authoritative. YouTube expects its media requests to carry a proof-of-origin token.
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
            usesPlayerScript = true,
            usesPoToken = true,
        )
    }

    /**
     * YouTube's mobile site, with the user's own session, for pages served as the desktop site.
     *
     * The desktop client is often offered only through YouTube's SABR protocol, while the
     * mobile site still lists a progressive stream. It sends yt-dlp's `mweb` version and tablet
     * user agent, the combination yt-dlp found YouTube serving most readily.
     */
    fun mobileWeb(): YouTubeClientProfile = YouTubeClientProfile(
        id = "mweb",
        clientName = MOBILE_CLIENT_NAME,
        clientNameId = PAGE_CLIENT_IDS.getValue(MOBILE_CLIENT_NAME),
        clientVersion = MOBILE_CLIENT_VERSION,
        userAgent = "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)",
        replaysSession = true,
        usesPlayerScript = true,
        usesPoToken = true,
    )
}
