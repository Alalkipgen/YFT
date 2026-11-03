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
 * YouTube backup route (docs/ARCHITECTURE.md): the build's switch and its app wiring. On in every
 * debug build made with `-Pyft.masterCapture=true` (`-Pyft.youtubeRouteB` is on by default;
 * `=false` turns it off); release, preview and every build without Master capture: off, and each
 * piece here is exactly today's.
 */
internal object YouTubeRouteB {
    val enabled: Boolean get() = BuildConfig.YOUTUBE_ROUTE_B

    /**
     * YouTube backup trial: the trial (the owner test APK `routeb`, `-Pyft.routeBTrial=true` with
     * the route on): a YouTube page main found is answered as main's failure, so Master's backup
     * route answers on the phone ([BrowserMasterFallback.trialPrimary]). Every other build: false.
     */
    val trial: Boolean get() = BuildConfig.YOUTUBE_ROUTE_B_TRIAL

    /**
     * YouTube backup route: the start of its Details line (`route B` in Lookup details; the
     * engine's).
     */
    const val LINE = "master: route B after "

    /**
     * YouTube backup route: its line of rows Master found
     * (`master: route B after <reason>; replayed N; new M`), for the browser's notice; null for
     * every other answer (a build without the route has none).
     */
    fun notice(outcome: SiteAdapterOutcome): String? =
        (outcome as? SiteAdapterOutcome.Detected)?.details?.firstOrNull { it.startsWith(LINE) }

    /**
     * Lookup record: the client main's site adapters share. In a YouTube backup route build it
     * records each request into the lookup's record ([LookupRecorder]; a lookup without one only
     * passes through); otherwise it is today's client.
     */
    fun mainClient(client: OkHttpClient, routeB: Boolean = enabled): ExtractorHttpClient {
        val http = OkHttpExtractorClient(client)
        return if (routeB) LookupRecorder(http) else http
    }

    /**
     * YouTube backup route: the client of Master's YouTube module. In its build its requests follow
     * the lookup's backup-route rules when the engine runs it after main's recoverable failure
     * (main's identical unauthenticated answers replayed, authorized ones refused, nothing new
     * after a 429); in any other run it only passes through. Otherwise it is [http] itself.
     */
    fun masterClient(http: ExtractorHttpClient, routeB: Boolean = enabled): ExtractorHttpClient =
        if (routeB) RouteBHttpClient(http) else http

    /**
     * YouTube backup route: the player-script client the runners share (the own solver's, main's).
     * In a backup-route build Master's script fetches in a backup-route run follow its rules
     * (counted, nothing new after a 429) and a 429 of main's script fetch is marked in the lookup's
     * record ([RouteBScriptClient]); any other run only passes through. Otherwise it is [http]
     * itself.
     */
    fun scriptClient(http: ExtractorHttpClient, routeB: Boolean = enabled): ExtractorHttpClient =
        if (routeB) RouteBScriptClient(http) else http

    /**
     * YouTube backup route: the own solver's runner for Master's module, not asked after a 429 in a
     * backup-route run.
     */
    fun masterScripts(runner: PlayerScriptRunner, routeB: Boolean = enabled): PlayerScriptRunner =
        if (routeB) RouteBPlayerScripts.wrap(runner) else runner

    /**
     * YouTube backup route: the ladder's media-start reader (the shape reader's row checks, the HLS
     * rung's first segment), counted in a backup-route run.
     */
    fun masterReader(reader: MediaStartReader, routeB: Boolean = enabled): MediaStartReader =
        if (routeB) RouteBMediaStartReader(reader) else reader
}
