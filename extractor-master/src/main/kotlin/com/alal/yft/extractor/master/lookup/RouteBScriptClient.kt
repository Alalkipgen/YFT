package com.alal.yft.extractor.master.lookup

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import kotlin.coroutines.coroutineContext

/**
 * Phase 1.4 item 7 (YouTube routing B): the player-script client in a route-B build
 * (`-Pyft.youtubeRouteB`, debug only). The runners (the own solver, main's) fetch the player
 * script with it, not with the adapters' client, so neither [LookupRecorder] nor
 * [RouteBHttpClient] sees those fetches. In Master's route-B run (a [RouteBLookup] in the
 * context) each fetch follows route B's rules for it ([RouteBLookup.script]: an authorized one
 * refused, nothing new after a 429 in the lookup — the runner's other address included — and
 * each one sent counted as new and listed as Master's). In main's lookup (a [LookupRecord] only)
 * each fetch goes out unchanged and a 429 (or a rate limit) is marked in the record
 * ([LookupRecord.unlisted]), so route B sends nothing new after it. Without either it only passes
 * through. Nothing is changed or logged here.
 */
class RouteBScriptClient(private val http: ExtractorHttpClient) : ExtractorHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
        val context = coroutineContext
        val route = context[RouteBLookup]
            ?: return http.get(url, headers, maxBodyBytes).also { context[LookupRecord]?.unlisted(it) }
        return route.script(LookupAsk(LookupAsk.GET, url, null, headers)) { http.get(url, headers, maxBodyBytes) }
    }

    override suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult {
        val context = coroutineContext
        val route = context[RouteBLookup]
            ?: return http.postJson(url, body, headers, maxBodyBytes).also { context[LookupRecord]?.unlisted(it) }
        return route.script(LookupAsk(LookupAsk.POST, url, body, headers)) {
            http.postJson(url, body, headers, maxBodyBytes)
        }
    }

    override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult {
        val context = coroutineContext
        val route = context[RouteBLookup]
            ?: return http.probe(url, headers, timeoutMillis).also { context[LookupRecord]?.unlisted(it) }
        return route.check(LookupAsk(LookupAsk.PROBE, url, null, headers)) { http.probe(url, headers, timeoutMillis) }
    }

    override fun toString(): String = "RouteBScriptClient"
}
