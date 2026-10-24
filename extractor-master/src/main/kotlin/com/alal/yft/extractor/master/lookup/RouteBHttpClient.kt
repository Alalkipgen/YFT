package com.alal.yft.extractor.master.lookup

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import kotlin.coroutines.coroutineContext

/**
 * Phase 1.4 item 7 (YouTube routing B) R2: the HTTP client of Master's YouTube module in a
 * route-B build (`-Pyft.youtubeRouteB`, debug only). In a run that carries a [RouteBLookup]
 * every request follows its rules — main's identical unauthenticated answer replayed, an
 * authorized request refused, nothing new after a 429, an endpoint asked once — and is counted
 * there; without one (flag off, the census, every other site and lookup) it only passes through.
 * Nothing is changed or logged here.
 */
class RouteBHttpClient(private val http: ExtractorHttpClient) : ExtractorHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
        val route = coroutineContext[RouteBLookup] ?: return http.get(url, headers, maxBodyBytes)
        return route.exchange(LookupAsk(LookupAsk.GET, url, null, headers), maxBodyBytes) {
            http.get(url, headers, maxBodyBytes)
        }
    }

    override suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
        maxBodyBytes: Long,
    ): ExtractorHttpResult {
        val route = coroutineContext[RouteBLookup] ?: return http.postJson(url, body, headers, maxBodyBytes)
        return route.exchange(LookupAsk(LookupAsk.POST, url, body, headers), maxBodyBytes) {
            http.postJson(url, body, headers, maxBodyBytes)
        }
    }

    override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult {
        val route = coroutineContext[RouteBLookup] ?: return http.probe(url, headers, timeoutMillis)
        return route.check(LookupAsk(LookupAsk.PROBE, url, null, headers)) { http.probe(url, headers, timeoutMillis) }
    }

    override fun toString(): String = "RouteBHttpClient"
}
