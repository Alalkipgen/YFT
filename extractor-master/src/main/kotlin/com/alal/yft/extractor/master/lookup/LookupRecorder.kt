package com.alal.yft.extractor.master.lookup

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import kotlin.coroutines.coroutineContext

/**
 * Phase 1.4 item 7 (YouTube routing B) R1: the client main's site adapters share, recording. The
 * app wraps its shared adapter client in it only in a route-B build (`-Pyft.youtubeRouteB`, debug
 * only). In a lookup whose coroutine context carries a [LookupRecord] each request goes out
 * unchanged and its answer is then recorded there (one that never answers still counts as asked);
 * without a record it only passes through. Nothing is changed or logged here.
 */
class LookupRecorder(private val http: ExtractorHttpClient) : ExtractorHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
        val record = coroutineContext[LookupRecord] ?: return http.get(url, headers, maxBodyBytes)
        val ask = LookupAsk(LookupAsk.GET, url, null, headers)
        return recorded(record, ask) { http.get(url, headers, maxBodyBytes) }
    }

    override suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult {
        val record = coroutineContext[LookupRecord] ?: return http.postJson(url, body, headers, maxBodyBytes)
        val ask = LookupAsk(LookupAsk.POST, url, body, headers)
        return recorded(record, ask) { http.postJson(url, body, headers, maxBodyBytes) }
    }

    override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult {
        val record = coroutineContext[LookupRecord] ?: return http.probe(url, headers, timeoutMillis)
        val ask = LookupAsk(LookupAsk.PROBE, url, null, headers)
        val started = record.now()
        var result: ExtractorProbeResult? = null
        try {
            return http.probe(url, headers, timeoutMillis).also { result = it }
        } finally {
            result?.let { record.record(ask, it, started) } ?: record.unanswered(ask, started)
        }
    }

    override fun toString(): String = "LookupRecorder"

    private suspend fun recorded(
        record: LookupRecord,
        ask: LookupAsk,
        send: suspend () -> ExtractorHttpResult,
    ): ExtractorHttpResult {
        val started = record.now()
        var result: ExtractorHttpResult? = null
        try {
            return send().also { result = it }
        } finally {
            result?.let { record.record(ask, it, started) } ?: record.unanswered(ask, started)
        }
    }
}
