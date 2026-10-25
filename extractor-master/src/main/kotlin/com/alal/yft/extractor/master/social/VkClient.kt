/*
 * Provenance (VK-Master plan §4 V2, Appendix B step 1; copied, not moved; main 50fb8d0f): main's VK
 * files came after the provenance base 34a41890, so their rows wait as "# pending" lines in the
 * header of toolkit-provenance.tsv until the base moves past them (the owner's call):
 *   extractor-sites/.../vk/VkWebClientProfile.kt  CLIENT_ID, CLIENT_SECRET, APP_ID, SCOPES,
 *                                       TOKEN_VERSION, API_VERSION, TOKEN_HOST, API_HOST,
 *                                       TOKEN_URL, ORIGIN, REFERER, tokenBody, videoGetUrl
 *   extractor-sites/.../vk/VkApiToken.kt  VkApiToken, VkApiTokenParser (the token's read: 60 s
 *                                       before VK's expiry, at most 24 h)
 *   extractor-sites/.../vk/VkUrls.kt  SITE_ID, COM_HOST, RU_HOST, EMBED_PATH, VkLink, OWNER_ID,
 *                                       VIDEO_ID, LIST, PLAYER_MEDIA_HOST, API_MEDIA_HOST,
 *                                       IMAGE_HOSTS, MAX_MEDIA_URL_CHARS, httpsHostOf, expiresAtOf,
 *                                       epochMillisOf
 *   extractor-sites/.../vk/VkExtractor.kt  apiHeaders, REQUEST_MS, MIN_REQUEST_MS, MAX_TOKEN_BYTES,
 *                                       MAX_API_BYTES
 * Checked at run time as well: VkClientDriftTest runs main's adapter and compares its token ask and
 * its video.get with this file's.
 */
package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import java.net.URI
import java.util.Locale

/**
 * VK-Master plan §4 (V2), §8: vkvideo.ru's own web client as main's path D (VK5) asks with it
 * signed out — an anonymous token from login.vk.ru, then `video.get` on api.vkvideo.ru — copied for
 * Master's heal (V2: one token in memory, one `video.get`, only after the embed's adult answer and
 * a refused value). The identifiers are VK's web client's, read from vkvideo.ru's public JavaScript
 * ([BUNDLED_DATE]); they belong to no user and no user's value is ever added. A token lives in
 * memory only: never stored, logged, shown or put into an address. Media: VK's video servers only,
 * never okcdn.ru's image host.
 */
object VkClient {
    const val SITE_ID = "vk"

    /** vkvideo.ru's web client and its public client secret (`client_id`, `client_secret`). */
    const val CLIENT_ID = "52461373"
    const val CLIENT_SECRET = "o557NLIkAErNhakXrQ7A"

    /** VK's web app the token request names (`app_id`). */
    const val APP_ID = "6287487"
    const val SCOPES = "audio_anonymous,video_anonymous,photos_anonymous,profile_anonymous"
    const val TOKEN_VERSION = "1"
    const val API_VERSION = "5.289"

    /** The day main's values were read from vkvideo.ru's public JavaScript (VK5's recon). */
    const val BUNDLED_DATE = "2026-10-25"

    /** The two hosts the web player asks signed out (and only these, besides the media). */
    const val TOKEN_HOST = "login.vk.ru"
    const val API_HOST = "api.vkvideo.ru"
    const val TOKEN_URL = "https://$TOKEN_HOST/?act=get_anonym_token"
    const val VIDEO_GET_PATH = "/method/video.get"

    /** The web player's page, as its own requests name it. */
    const val ORIGIN = "https://vkvideo.ru"
    const val REFERER = "$ORIGIN/"

    /** Main's embed hosts and page (paths B and B2): the answers V1 re-reads. */
    const val COM_HOST = "vk.com"
    const val RU_HOST = "vk.ru"
    const val EMBED_PATH = "/video_ext.php"

    /** Every host main's VK lookup or V2 asks: the stop rules' and the cooldown's names. */
    val HOSTS: Set<String> = setOf(COM_HOST, RU_HOST, TOKEN_HOST, API_HOST)

    /** Main's bounds for path D's two asks; V2 keeps them. */
    const val MAX_TOKEN_BYTES: Long = 16L * 1024
    const val MAX_API_BYTES: Long = 1L * 1024 * 1024
    const val REQUEST_MS: Long = 8_000
    const val MIN_REQUEST_MS: Long = 250

    private const val MAX_MEDIA_URL_CHARS = 8_192

    /** VK's video servers: the embed's `vkvd{N}.okcdn.ru` and the API's `vk{N}-{N}.vkuser.net`. */
    private val PLAYER_MEDIA_HOST = Regex("^vkvd[0-9]{1,6}\\.okcdn\\.ru$")
    private val API_MEDIA_HOST = Regex("^vk[0-9]{1,4}-[0-9]{1,4}\\.vkuser\\.net$")

    /** okcdn.ru's image host (first frames, previews): never media. */
    private val IMAGE_HOSTS = setOf("iv.okcdn.ru")

    /** `{oid}_{id}`: bounded digit strings only, as main's identity names the video. */
    private val CONTENT_ID = Regex("^(-?[0-9]{1,19})_([0-9]{1,19})$")
    private val LIST = Regex("^[A-Za-z0-9_-]{1,64}$")

    /** Where a set of [Values] came from: its line's word. */
    enum class Source(val label: String) {
        /** Main's values, bundled with this build ([BUNDLED]). */
        BUNDLED("bundled"),

        /** The signed config's values (§8, agent C's source). */
        SIGNED("signed"),

        /** Values read from the web player's public script (§8, agent C's source). */
        SCRIPT("script"),

        /** A debug build's mutate hook (the owner's phone proof). */
        DEBUG("debug"),
    }

    /**
     * One set of the web client's values, checked: digits, letters and VK's own separators only, so
     * no value can carry another parameter, a header or a JSON field. Its text names its source and
     * date, never a value; a refusal names the field, never its value.
     */
    data class Values(
        val clientId: String,
        val clientSecret: String,
        val appId: String,
        val scopes: String,
        val tokenVersion: String,
        val apiVersion: String,
        val source: Source,
        val date: String,
    ) {
        init {
            require(NUMBER.matches(clientId)) { "client id: not VK's shape" }
            require(SECRET.matches(clientSecret)) { "client secret: not VK's shape" }
            require(NUMBER.matches(appId)) { "app id: not VK's shape" }
            require(SCOPE_LIST.matches(scopes)) { "scopes: not VK's shape" }
            require(TOKEN_VERSION_SHAPE.matches(tokenVersion)) { "token version: not VK's shape" }
            require(API_VERSION_SHAPE.matches(apiVersion)) { "API version: not VK's shape" }
            require(DATE.matches(date)) { "date: not a day" }
        }

        /** The same six identifiers as [other] (source and date aside). */
        fun sameAs(other: Values): Boolean =
            clientId == other.clientId && clientSecret == other.clientSecret && appId == other.appId &&
                scopes == other.scopes && tokenVersion == other.tokenVersion && apiVersion == other.apiVersion

        /** The token request's JSON body, in main's order; login.vk.ru reads it as the web client's form. */
        val tokenBody: String
            get() = listOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "scopes" to scopes,
                "isApiOauthAnonymEnabled" to "false",
                "version" to tokenVersion,
                "app_id" to appId,
            ).joinToString(",", prefix = "{", postfix = "}") { (name, value) -> "\"$name\":\"$value\"" }

        /**
         * `video.get` for [link]'s video, as the web player asks it: `videos` is `{oid}_{id}[_{list}]`
         * (the link's `list` is VK's access key), `extended=1`. The token goes in the
         * `Authorization` header, never into the address.
         */
        fun videoGetUrl(link: Link): String = buildString {
            append("https://").append(API_HOST).append(VIDEO_GET_PATH)
            append("?v=").append(apiVersion)
            append("&client_id=").append(clientId)
            append("&owner_id=").append(link.ownerId)
            append("&videos=").append(link.contentId)
            link.list?.let { append('_').append(it) }
            append("&extended=1")
        }

        override fun toString(): String = "Values(${source.label}, $date)"

        companion object {
            private val NUMBER = Regex("^[0-9]{1,12}$")
            private val SECRET = Regex("^[A-Za-z0-9]{8,64}$")
            private val SCOPE_LIST = Regex("^[a-z_]{1,40}(?:,[a-z_]{1,40}){0,15}$")
            private val TOKEN_VERSION_SHAPE = Regex("^[0-9]{1,3}$")
            private val API_VERSION_SHAPE = Regex("^[0-9]{1,2}\\.[0-9]{1,4}$")
            private val DATE = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")

            /** [Values] when every value has VK's shape, else null; never throws. */
            fun of(
                clientId: String,
                clientSecret: String,
                appId: String,
                scopes: String,
                tokenVersion: String,
                apiVersion: String,
                source: Source,
                date: String,
            ): Values? = try {
                Values(clientId, clientSecret, appId, scopes, tokenVersion, apiVersion, source, date)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    /** Main's values: what main's path D asked with in this lookup. */
    val BUNDLED: Values = Values(
        CLIENT_ID,
        CLIENT_SECRET,
        APP_ID,
        SCOPES,
        TOKEN_VERSION,
        API_VERSION,
        Source.BUNDLED,
        BUNDLED_DATE,
    )

    /**
     * The link's video as main's identity names it (`{oid}_{id}`), with the canonical link's `list`
     * (VK's access key). Its text names no ID.
     */
    data class Link(val ownerId: Long, val videoId: Long, val list: String? = null) {
        val contentId: String
            get() = "${ownerId}_$videoId"

        override fun toString(): String = "Link(${if (list != null) "with list" else "no list"})"

        companion object {
            /** [identity]'s video (a VK identity's bounded `{oid}_{id}`), or null; never throws. */
            fun of(identity: SitePageIdentity): Link? {
                if (identity.siteId != SITE_ID) return null
                val match = CONTENT_ID.matchEntire(identity.contentId) ?: return null
                val owner = match.groupValues[1].toLongOrNull() ?: return null
                val id = match.groupValues[2].toLongOrNull() ?: return null
                if (owner == 0L || id <= 0L) return null
                return Link(owner, id, listIn(identity.canonicalPageUrl))
            }

            private fun listIn(url: String): String? = try {
                URI(url.trim()).rawQuery?.split('&')
                    ?.firstOrNull { it.substringBefore('=') == "list" }
                    ?.substringAfter('=', "")
                    ?.takeIf(LIST::matches)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * VK's anonymous token for signed-out viewers (main's `VkApiToken`), never a user's: in memory
     * for one V2 only, usable until [usableUntilEpochMs]. Its text names no value.
     */
    class Token internal constructor(internal val value: String, val usableUntilEpochMs: Long) {
        fun usableAt(nowEpochMs: Long): Boolean = nowEpochMs < usableUntilEpochMs

        override fun toString(): String = "Token(usableUntilEpochMs=$usableUntilEpochMs)"
    }

    private const val SLACK_MS: Long = 60_000
    private const val MAX_LIFE_MS: Long = 24L * 3600 * 1000
    private const val MIN_TOKEN_CHARS = 16
    private const val MAX_TOKEN_CHARS = 2_048

    /**
     * login.vk.ru's `get_anonym_token` answer, read as main reads it (`{"type":"okay","data":{…}}`):
     * the token, used until 60 s before VK's expiry and at most 24 h by the phone's clock; null for
     * any other answer (VK's error, a changed shape, a bad value).
     */
    internal fun token(json: String, nowEpochMs: Long): Token? {
        val root = BoundedJsonParser.parse(json) as? JsonValue.Object ?: return null
        if (root["type"].asStringOrNull != "okay") return null
        val data = root["data"]
        val value = data["access_token"].asStringOrNull
            ?.takeIf { it.length in MIN_TOKEN_CHARS..MAX_TOKEN_CHARS }
            ?.takeIf { token -> token.all { it in '\u0021'..'\u007e' } }
            ?: return null
        val expiresAt = epochMillisOf(data["expires"].asLongOrNull, nowEpochMs)
            ?: epochMillisOf(data["expired_at"].asLongOrNull, nowEpochMs)
            ?: nowEpochMs
        val until = minOf(expiresAt, nowEpochMs + MAX_LIFE_MS) - SLACK_MS
        return Token(value, until)
    }

    /** VK's epoch seconds (10 digits), epoch ms (13 digits) or, below that, seconds from now. */
    private fun epochMillisOf(value: Long?, nowEpochMs: Long): Long? = when {
        value == null || value <= 0 -> null
        value >= 1_000_000_000_000L -> value
        value >= 1_000_000_000L -> value * 1_000
        else -> nowEpochMs + value * 1_000
    }

    /**
     * The token ask's and `video.get`'s headers, main's path D's: the browser's user agent (VK binds
     * the files' `srcAg` to it), VK's web origin, [token] as a bearer; never a cookie.
     */
    internal fun headers(userAgent: String?, token: Token?): Map<String, String> = buildMap {
        userAgent?.takeIf(String::isNotBlank)?.let { put("User-Agent", it) }
        put("Accept", "*/*")
        put("Origin", ORIGIN)
        put("Referer", REFERER)
        token?.let { put("Authorization", "Bearer ${it.value}") }
    }

    /**
     * A media file of VK's: HTTPS on `vkvd{N}.okcdn.ru` or `vk{N}-{N}.vkuser.net` (main's shape
     * scan's hosts), no user info, never okcdn.ru's image host. Both of V's reads keep only these.
     */
    fun mediaFile(url: String): Boolean {
        val host = httpsHostOf(url) ?: return false
        if (host in IMAGE_HOSTS) return false
        return PLAYER_MEDIA_HOST.matches(host) || API_MEDIA_HOST.matches(host)
    }

    /**
     * A signed address's expiry: the `expires` query value or an `/expires/{value}/` path segment,
     * in epoch milliseconds (VK's 13-digit value as is, a 10-digit one in seconds).
     */
    fun expiresAtOf(url: String): Long? = try {
        val uri = URI(url.trim())
        val fromQuery = uri.rawQuery?.split('&')
            ?.firstOrNull { it.substringBefore('=') == "expires" }
            ?.substringAfter('=', "")
        val segments = uri.rawPath.orEmpty().split('/')
        val fromPath = segments.indexOf("expires").takeIf { it >= 0 }
            ?.let { segments.getOrNull(it + 1) }
        epochMillisOf(fromQuery) ?: epochMillisOf(fromPath)
    } catch (_: Exception) {
        null
    }

    private fun epochMillisOf(value: String?): Long? {
        if (value == null || value.any { it !in '0'..'9' }) return null
        return when (value.length) {
            13 -> value.toLongOrNull()
            10 -> value.toLongOrNull()?.times(1_000)
            else -> null
        }
    }

    /** [url]'s host, lower case, when it is HTTPS without user info and within the bound. */
    internal fun httpsHostOf(url: String): String? = try {
        val text = url.trim()
        if (text.length > MAX_MEDIA_URL_CHARS) {
            null
        } else {
            val uri = URI(text)
            if (uri.scheme?.lowercase(Locale.US) != "https" || uri.rawUserInfo != null) {
                null
            } else {
                uri.host?.lowercase(Locale.US)?.takeIf(String::isNotEmpty)
            }
        }
    } catch (_: Exception) {
        null
    }
}
