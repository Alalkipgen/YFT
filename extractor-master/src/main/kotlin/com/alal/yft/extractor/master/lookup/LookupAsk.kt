package com.alal.yft.extractor.master.lookup

import java.net.URI
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Objects

/**
 * Phase 1.4 item 7 (YouTube routing B, `MASTER_KEY_PHASE1_4_ROUTING_PLAN.md`) R1: one request of
 * a lookup as its [LookupRecord] sees it.
 *
 * Two asks are the same request only when the method, address, body and headers (names, values
 * and order) are all the same, as in the ladder's same-lookup memo. [authorized]: the request
 * carries the user's session or credentials (a non-blank `Cookie`, `Authorization` or
 * `Proxy-Authorization` header); such an answer is never kept or replayed. [endpoint] is the
 * method, address and body alone (an endpoint main asked is not asked again by Master, R2);
 * [hash] is a short hash for counts only. [toString] shows the method and the short hash, never
 * the address, the body or a header. F5: [watchPage] marks a GET of a YouTube watch (or embed)
 * page, and [withoutCookie] is the same request without its `Cookie` header — what the record
 * keys main's cookie GET of the lookup's watch page by.
 */
class LookupAsk(
    val method: String,
    val url: String,
    val body: String?,
    headers: Map<String, String> = emptyMap(),
) {
    /** The headers in the order the caller gave them. */
    val headers: List<Pair<String, String>> = headers.toList()

    val authorized: Boolean = this.headers.any { (name, value) ->
        name.trim().lowercase() in CREDENTIAL_HEADERS && value.isNotBlank()
    }

    /** The method, address and body (no header), as a digest. */
    val endpoint: String = digest(method, url, body)

    /** A short hash of the method, address, body and [authorized]; for counts and details only. */
    val hash: String = digest(method, url, body, authorized.toString()).take(HASH_CHARS)

    /** F5: a GET of a YouTube watch page or embed page (guardrail 7: one page per lookup). */
    val watchPage: Boolean = method == GET && isWatchPage(url)

    /** F5: whether the request carries a non-blank `Cookie` and no other credential header. */
    internal val cookieOnly: Boolean
        get() = headers.any { (name, value) -> name.trim().equals(COOKIE, ignoreCase = true) && value.isNotBlank() } &&
            headers.none { (name, value) ->
                name.trim().lowercase() in CREDENTIAL_HEADERS && !name.trim().equals(COOKIE, ignoreCase = true) &&
                    value.isNotBlank()
            }

    /** F5: the same request without its `Cookie` headers (the others in their order). */
    internal fun withoutCookie(): LookupAsk = LookupAsk(
        method,
        url,
        body,
        headers.filterNot { (name, _) -> name.trim().equals(COOKIE, ignoreCase = true) }
            .toMap(LinkedHashMap()),
    )

    override fun equals(other: Any?): Boolean = other is LookupAsk && method == other.method &&
        url == other.url && body == other.body && headers == other.headers

    override fun hashCode(): Int = Objects.hash(method, url, body, headers)

    override fun toString(): String = "LookupAsk($method #$hash${if (authorized) ", authorized" else ""})"

    companion object {
        const val GET = "GET"
        const val POST = "POST"

        /** A one-byte file check (`ExtractorHttpClient.probe`); never kept or replayed. */
        const val PROBE = "PROBE"

        const val HASH_CHARS = 12

        private val CREDENTIAL_HEADERS = setOf("cookie", "authorization", "proxy-authorization")
        private const val COOKIE = "cookie"
        private const val YOUTUBE = "youtube.com"

        /** An HTTPS address of a YouTube watch page (`/watch`) or embed page (`/embed/...`). */
        private fun isWatchPage(url: String): Boolean {
            val address = runCatching { URI(url) }.getOrNull() ?: return false
            if (!address.scheme.equals("https", ignoreCase = true)) return false
            val host = address.host?.lowercase()?.trimEnd('.') ?: return false
            if (host != YOUTUBE && !host.endsWith(".$YOUTUBE")) return false
            val path = address.path.orEmpty()
            return path == "/watch" || path == "/watch/" || path.startsWith("/embed/")
        }

        /** SHA-256 over length-prefixed parts (a missing part differs from an empty one). */
        private fun digest(vararg parts: String?): String {
            val sha = MessageDigest.getInstance("SHA-256")
            for (part in parts) {
                if (part == null) {
                    sha.update(0.toByte())
                } else {
                    val bytes = part.toByteArray(Charsets.UTF_8)
                    sha.update(1.toByte())
                    sha.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
                    sha.update(bytes)
                }
            }
            return sha.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
