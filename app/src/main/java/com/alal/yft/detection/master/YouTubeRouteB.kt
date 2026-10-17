package com.alal.yft.detection.master

import com.alal.yft.BuildConfig
import com.alal.yft.detection.OkHttpExtractorClient
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.lookup.RouteBHttpClient
import com.alal.yft.extractor.master.lookup.RouteBMediaStartReader
import com.alal.yft.extractor.master.lookup.RouteBPlayerScripts
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
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

    /**
     * Route B R2: the client of Master's YouTube module. In a route-B build its requests follow
     * the lookup's route-B rules when the engine runs it after main's recoverable failure (R3; main's
     * identical unauthenticated answers replayed, authorized ones refused, nothing new after a 429);
     * in any other run it only passes through. Otherwise it is [http] itself.
     */
    fun masterClient(http: ExtractorHttpClient, routeB: Boolean = enabled): ExtractorHttpClient =
        if (routeB) RouteBHttpClient(http) else http

    /** Route B R2: the own solver's runner for Master's module, not asked after a 429 in a route-B run. */
    fun masterScripts(runner: PlayerScriptRunner, routeB: Boolean = enabled): PlayerScriptRunner =
        if (routeB) RouteBPlayerScripts.wrap(runner) else runner

    /** Route B R2: the ladder's media-start reader (L's V reads, H's first segment), counted in a route-B run. */
    fun masterReader(reader: MediaStartReader, routeB: Boolean = enabled): MediaStartReader =
        if (routeB) RouteBMediaStartReader(reader) else reader
}
