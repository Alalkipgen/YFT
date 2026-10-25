package com.alal.yft.extractor.sites.vk

/**
 * Path D's web client (VK5, the owner's decision under ADR-006 decision 1): what vkvideo.ru's own
 * player uses signed out — an anonymous token from login.vk.ru, then `video.get` on
 * api.vkvideo.ru. The identifiers are the web app's own, read from vkvideo.ru's public
 * JavaScript on 2026-10-25 (its vkvideo.ru client, the API version of its config); they belong to
 * VK's web client, not to a user, and no user's value is ever added. When VK rotates them, path D
 * fails cleanly with v1's message and only this file changes.
 */
internal object VkWebClientProfile {
    /** vkvideo.ru's web client and its public client secret (`client_id`, `client_secret`). */
    const val CLIENT_ID = "52461373"
    const val CLIENT_SECRET = "o557NLIkAErNhakXrQ7A"

    /** VK's web app the token request names (`app_id`). */
    const val APP_ID = "6287487"
    const val SCOPES = "audio_anonymous,video_anonymous,photos_anonymous,profile_anonymous"
    const val TOKEN_VERSION = "1"
    const val API_VERSION = "5.289"

    /** The hosts vkvideo.ru's player asks signed out (and only these, besides the media). */
    const val TOKEN_HOST = "login.vk.ru"
    const val API_HOST = "api.vkvideo.ru"
    const val TOKEN_URL = "https://$TOKEN_HOST/?act=get_anonym_token"

    /** The web player's page, as its own requests name it. */
    const val ORIGIN = "https://vkvideo.ru"
    const val REFERER = "$ORIGIN/"

    /** The token request's JSON body; login.vk.ru reads it as the web client's form. */
    val tokenBody: String = listOf(
        "client_id" to CLIENT_ID,
        "client_secret" to CLIENT_SECRET,
        "scopes" to SCOPES,
        "isApiOauthAnonymEnabled" to "false",
        "version" to TOKEN_VERSION,
        "app_id" to APP_ID,
    ).joinToString(",", prefix = "{", postfix = "}") { (name, value) -> "\"$name\":\"$value\"" }

    /**
     * `video.get` for the link's video, as the web player asks it: `videos` is
     * `{oid}_{id}[_{list}]` (the link's `list` is VK's access key), `extended=1`. The token goes
     * in the `Authorization` header, never into the address.
     */
    fun videoGetUrl(link: VkUrls.VkLink): String = buildString {
        append("https://").append(API_HOST).append("/method/video.get")
        append("?v=").append(API_VERSION)
        append("&client_id=").append(CLIENT_ID)
        append("&owner_id=").append(link.ownerId)
        append("&videos=").append(link.contentId)
        link.list?.let { append('_').append(it) }
        append("&extended=1")
    }
}
