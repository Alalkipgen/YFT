package com.alal.yft.detection.master

import com.alal.yft.BuildConfig
import com.alal.yft.detection.OkHttpExtractorClient
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.lookup.RouteBHttpClient
import com.alal.yft.extractor.master.lookup.RouteBMediaStartReader
import com.alal.yft.extractor.master.lookup.RouteBPlayerScripts
import com.alal.yft.extractor.master.lookup.RouteBScriptClient
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import okhttp3.OkHttpClient

/**
 * Phase 1.4 item 7 (YouTube routing B, `MASTER_KEY_PHASE1_4_ROUTING_PLAN.md`): the build's
 * route-B switch and its app wiring. On in every debug build made with `-Pyft.masterCapture=true`
 * (promoted 2026-10-19, plan §9 4: `-Pyft.youtubeRouteB` is on by default; `=false` turns it off);
 * release, preview and every build without Master capture: off, and each piece here is exactly
 * today's.
 */
internal object YouTubeRouteB {
    val enabled: Boolean get() = BuildConfig.YOUTUBE_ROUTE_B

    /**
     * Route B R4: the route-B trial (the D7 APK `routeb`, `-Pyft.routeBTrial=true` with route B
     * on): a YouTube page main found is answered as main's failure, so Master's route B answers
     * on the phone ([BrowserMasterFallback.trialPrimary]). Every other build: false.
     */
    val trial: Boolean get() = BuildConfig.YOUTUBE_ROUTE_B_TRIAL

    /** Route B R4: the start of route B's Details line (the engine's, R3). */
    const val LINE = "master: route B after "

    /**
     * Route B R4: route B's line of rows Master found (`master: route B after <reason>; replayed N;
     * new M`), for the browser's notice; null for every other answer (no route-B build has one).
     */
    fun notice(outcome: SiteAdapterOutcome): String? =
        (outcome as? SiteAdapterOutcome.Detected)?.details?.firstOrNull { it.startsWith(LINE) }

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

    /**
     * Route B: the player-script client the runners share (the own solver's, main's). In a route-B
     * build Master's script fetches in a route-B run follow route B's rules (counted, nothing new
     * after a 429) and a 429 of main's script fetch is marked in the lookup's record
     * ([RouteBScriptClient]); any other run only passes through. Otherwise it is [http] itself.
     */
    fun scriptClient(http: ExtractorHttpClient, routeB: Boolean = enabled): ExtractorHttpClient =
        if (routeB) RouteBScriptClient(http) else http

    /** Route B R2: the own solver's runner for Master's module, not asked after a 429 in a route-B run. */
    fun masterScripts(runner: PlayerScriptRunner, routeB: Boolean = enabled): PlayerScriptRunner =
        if (routeB) RouteBPlayerScripts.wrap(runner) else runner

    /** Route B R2: the ladder's media-start reader (L's V reads, H's first segment), counted in a route-B run. */
    fun masterReader(reader: MediaStartReader, routeB: Boolean = enabled): MediaStartReader =
        if (routeB) RouteBMediaStartReader(reader) else reader
}
