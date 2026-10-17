package com.alal.yft.extractor.master.lookup

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
 * the address, the body or a header.
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
