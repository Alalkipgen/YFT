package com.alal.yft.extractor.sites.youtube

import java.security.MessageDigest

/**
 * The authorization YouTube's own web player adds to its API requests for a signed-in user.
 *
 * YouTube answers API requests that carry a signed-in cookie but no such header as if nobody
 * were signed in. The header is a hash of the user's own session cookie, the time and
 * YouTube's origin, computed the way YouTube's web player computes it, and it travels only
 * with that same cookie to YouTube's own API. Nothing here signs in or stores anything.
 */
internal object YouTubeSessionAuth {
    const val ORIGIN: String = "https://www.youtube.com"

    /** The session cookies YouTube hashes, in the order its player sends the schemes. */
    private val SCHEMES = listOf(
        "SAPISIDHASH" to listOf("SAPISID", "__Secure-3PAPISID"),
        "SAPISID1PHASH" to listOf("__Secure-1PAPISID"),
        "SAPISID3PHASH" to listOf("__Secure-3PAPISID"),
    )

    private val COOKIE_VALUE = Regex("^[A-Za-z0-9_\\-./+=]{8,256}$")
    private val SESSION_ID = Regex("^[A-Za-z0-9_\\-]{1,128}$")

    /**
     * Headers for one API request, or none when [cookie] holds no signed-in session.
     *
     * The user session part of the hash comes from the page's own data-sync identifier and is
     * used only when the page says the user is signed in.
     */
    fun headers(
        cookie: String?,
        signals: YouTubePageSignals,
        nowEpochSeconds: Long,
    ): Map<String, String> {
        val values = cookieValues(cookie ?: return emptyMap())
        val sessions = SCHEMES.mapNotNull { (scheme, names) ->
            names.firstNotNullOfOrNull(values::get)?.let { scheme to it }
        }
        if (sessions.isEmpty()) return emptyMap()

        val ids = if (signals.loggedIn == true) dataSyncIds(signals.dataSyncId) else null
        val delegated = ids?.first
        val user = ids?.second
        val authorization = sessions.joinToString(" ") { (scheme, sid) ->
            "$scheme ${proof(nowEpochSeconds, sid, user)}"
        }
        return buildMap {
            put("Authorization", authorization)
            put("X-Origin", ORIGIN)
            delegated?.let { put("X-Goog-PageId", it) }
            if (delegated != null || signals.sessionIndex != null) {
                put("X-Goog-AuthUser", (signals.sessionIndex ?: 0).toString())
            }
            if (signals.loggedIn == true) put("X-Youtube-Bootstrap-Logged-In", "true")
        }
    }

    /** `<time>_<sha1>` or, with a user session, `<time>_<sha1>_u`. */
    internal fun proof(nowEpochSeconds: Long, sid: String, userSessionId: String?): String {
        val input = listOfNotNull(userSessionId, nowEpochSeconds.toString(), sid, ORIGIN)
            .joinToString(" ")
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return if (userSessionId == null) {
            "${nowEpochSeconds}_$hex"
        } else {
            "${nowEpochSeconds}_${hex}_u"
        }
    }

    /**
     * Splits YouTube's data-sync identifier, `delegated||user` for a secondary channel and
     * `user||` for the account's own channel, into its two parts.
     */
    private fun dataSyncIds(dataSyncId: String?): Pair<String?, String?>? {
        val value = dataSyncId?.takeIf(String::isNotBlank) ?: return null
        val first = value.substringBefore("||").takeIf(SESSION_ID::matches)
        val second = value.substringAfter("||", "").takeIf(SESSION_ID::matches)
        return if (second != null) first to second else null to first
    }

    private fun cookieValues(cookie: String): Map<String, String> = buildMap {
        cookie.split(';').forEach { pair ->
            val separator = pair.indexOf('=')
            if (separator > 0) {
                val name = pair.substring(0, separator).trim()
                val value = pair.substring(separator + 1).trim()
                if (name !in this && COOKIE_VALUE.matches(value)) put(name, value)
            }
        }
    }
}
