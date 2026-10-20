package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import kotlin.coroutines.coroutineContext

/**
 * Social plan S1a (2): the client main's site adapters share, keeping main's pages. The app wraps
 * its shared adapter client in it only in a social-ladder build (`-Pyft.socialLadder`, debug
 * only). In a lookup whose coroutine context carries [MainPages] each GET goes out unchanged and
 * its 2xx answer is then kept there; a post, a file check and every lookup without [MainPages]
 * only pass through. Nothing is changed, asked again or logged here.
 */
class MainPageRecorder(private val http: ExtractorHttpClient) : ExtractorHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
        val pages = coroutineContext[MainPages] ?: return http.get(url, headers, maxBodyBytes)
        return http.get(url, headers, maxBodyBytes).also { pages.note(url, headers, it) }
    }

    override suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult = http.postJson(url, body, headers, maxBodyBytes)

    override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult =
        http.probe(url, headers, timeoutMillis)

    override fun toString(): String = "MainPageRecorder"
}
