package com.alal.yft.detection.master

import com.alal.yft.BuildConfig
import com.alal.yft.detection.OkHttpExtractorClient
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.master.lookup.LookupRecorder
import okhttp3.OkHttpClient

/**
 * Phase 1.4 item 7 (YouTube routing B, `MASTER_KEY_PHASE1_4_ROUTING_PLAN.md`): the build's
 * route-B switch and its app wiring. On only in a debug build made with `-Pyft.youtubeRouteB=true`
 * and `-Pyft.masterCapture=true`; release, preview and every other build: off, and each piece here
 * is exactly today's.
 */
internal object YouTubeRouteB {
    val enabled: Boolean get() = BuildConfig.YOUTUBE_ROUTE_B

    /**
     * Route B R1: the client main's site adapters share. In a route-B build it records each request
     * into the lookup's record ([LookupRecorder]; a lookup without one only passes through);
     * otherwise it is today's client.
     */
    fun mainClient(client: OkHttpClient, routeB: Boolean = enabled): ExtractorHttpClient {
        val http = OkHttpExtractorClient(client)
        return if (routeB) LookupRecorder(http) else http
    }
}
