package com.alal.yft.extractor.master.modules.youtube.ladder

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import java.net.URI

/**
 * Phase 1.3 D6: the **same-lookup memo** (MASTER_KEY_PHASE1_3_PLAN.md guardrail 7, §4 D6). It
 * wraps the HTTP client of one lookup and has two sides:
 *
 * - [ladder]: the ladder's requests go out unchanged; each successful (2xx) answer of a request
 *   that is not to a media host is kept for this lookup.
 * - [chain]: the R6 chain's requests. A request **byte-identical** to one the ladder made in this
 *   lookup — the same method, address, body and headers (names, values and order) — is answered
 *   from memory **once**: each kept answer serves at most one chain request and is then dropped.
 *   Every other request goes out unchanged.
 *
 * Never across lookups: one memo per lookup ([clear] at its end drops everything). Never for media
 * hosts: no request to a `googlevideo.com` host (streams, HLS playlists) is kept or served, and
 * file checks ([ExtractorHttpClient.probe]) always go out. A failed ladder answer is never kept,
 * so the chain asks again as it does today. A kept answer larger than the chain's body limit is
 * not served (the request goes out). Bounded: at most [maxEntries] answers and [maxKeptChars]
 * characters. Nothing is logged; [toString] and the counts show no address, body or header.
 *
 * Not wired yet: D6 passes [chain] to the R6 chain and [ladder] to the ladder.
 */
internal class LookupMemo(
    private val http: ExtractorHttpClient,
    private val maxEntries: Int = MAX_ENTRIES,
    private val maxKeptChars: Long = MAX_KEPT_CHARS,
) {
    init {
        require(maxEntries in 1..MAX_ENTRIES)
        require(maxKeptChars in 1..MAX_KEPT_CHARS)
    }

    private data class Key(val method: String, val url: String, val body: String?, val headers: List<Pair<String, String>>)

    private val lock = Any()
    private val kept = HashMap<Key, ArrayDeque<ExtractorHttpResult.Success>>()
    private var keptEntries = 0
    private var keptChars = 0L
    private var keptTotal = 0
    private var servedTotal = 0

    /** Answers kept so far in this lookup (served ones included). */
    val keptCount: Int get() = synchronized(lock) { keptTotal }

    /** Chain requests answered from memory in this lookup. */
    val servedCount: Int get() = synchronized(lock) { servedTotal }

    /** The client the ladder uses in this lookup. */
    val ladder: ExtractorHttpClient = object : ExtractorHttpClient {
        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult =
            http.get(url, headers, maxBodyBytes).also { keep(Key(GET, url, null, headers.toList()), it) }

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult =
            http.postJson(url, body, headers, maxBodyBytes).also { keep(Key(POST, url, body, headers.toList()), it) }

        override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult =
            http.probe(url, headers, timeoutMillis)

        override fun toString(): String = "LookupMemo.ladder"
    }

    /** The client the R6 chain uses in this lookup. */
    val chain: ExtractorHttpClient = object : ExtractorHttpClient {
        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult =
            take(Key(GET, url, null, headers.toList()), maxBodyBytes) ?: http.get(url, headers, maxBodyBytes)

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult =
            take(Key(POST, url, body, headers.toList()), maxBodyBytes) ?: http.postJson(url, body, headers, maxBodyBytes)

        override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult =
            http.probe(url, headers, timeoutMillis)

        override fun toString(): String = "LookupMemo.chain"
    }

    /** The lookup's end: every kept answer is dropped. */
    fun clear() {
        synchronized(lock) {
            kept.clear()
            keptEntries = 0
            keptChars = 0
        }
    }

    override fun toString(): String = synchronized(lock) { "LookupMemo(kept=$keptTotal, served=$servedTotal, waiting=$keptEntries)" }

    private fun keep(key: Key, result: ExtractorHttpResult) {
        if (result !is ExtractorHttpResult.Success || !memoable(key.url)) return
        val chars = result.body.length.toLong()
        synchronized(lock) {
            if (keptEntries >= maxEntries || keptChars + chars > maxKeptChars) return
            kept.getOrPut(key) { ArrayDeque() }.addLast(result)
            keptEntries++
            keptChars += chars
            keptTotal++
        }
    }

    private fun take(key: Key, maxBodyBytes: Long): ExtractorHttpResult.Success? {
        if (!memoable(key.url)) return null
        val result = synchronized(lock) {
            val queue = kept[key] ?: return null
            val first = queue.removeFirst()
            if (queue.isEmpty()) kept.remove(key)
            keptEntries--
            keptChars -= first.body.length
            first
        }
        if (result.body.toByteArray(Charsets.UTF_8).size > maxBodyBytes) return null
        synchronized(lock) { servedTotal++ }
        return result
    }

    companion object {
        /** More than the ladder's requests in one lookup (≤ 3 player requests, 1 page, 1 source). */
        const val MAX_ENTRIES = 8
        const val MAX_KEPT_CHARS = 8L * 1024 * 1024
        private const val GET = "GET"
        private const val POST = "POST"
        private const val MEDIA_HOST = "googlevideo.com"

        /** Only HTTPS requests that are not to a media host. */
        fun memoable(url: String): Boolean {
            val address = runCatching { URI(url) }.getOrNull() ?: return false
            val host = address.host?.lowercase()?.trimEnd('.') ?: return false
            if (!address.scheme.equals("https", ignoreCase = true)) return false
            return host != MEDIA_HOST && !host.endsWith(".$MEDIA_HOST")
        }
    }
}
