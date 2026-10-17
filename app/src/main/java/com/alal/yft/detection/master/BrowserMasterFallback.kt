package com.alal.yft.detection.master

import android.os.Build
import com.alal.yft.core.download.AudioVideoMuxCompatibility
import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.detection.DeviceMergeSupport
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.OkHttpExtractorClient
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.SiteScope
import com.alal.yft.detection.platformHasDecoder
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.android.CodecSteering
import com.alal.yft.extractor.master.android.WebViewPlaybackCapture
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.api.PlayerScriptRunner
import java.io.File
import com.alal.yft.extractor.master.verify.OkHttpMediaValidator
import com.alal.yft.extractor.master.verify.CapturedMediaMetadata
import com.alal.yft.extractor.master.present.MasterMainPresentation
import com.alal.yft.extractor.master.policy.TerminalRules
import java.net.URI
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** Browser-only, outside the site registry. Home/pasted-link extraction remains unchanged. */
interface BrowserMasterFallback {
    val enabled: Boolean
    val capture: WebViewPlaybackCapture? get() = null
    fun mainAndMore(candidates: List<MediaCandidate>): MasterMainPresentation? = null

    /**
     * Phase 1.4 item 2: whether a page the site adapter found still goes through [recover] —
     * YouTube in the h-only live-check APK only, so the ladder's H gives the rows. False in every
     * other build: a found page never reaches the backup.
     */
    fun masterFirst(lookupUrl: String): Boolean = false

    /**
     * Phase 1.4 item 7 (YouTube routing B) R1: what [lookupUrl]'s lookup (main's adapter, then
     * [recover]) runs in — a new [LookupRecord] for a YouTube page in a route-B build
     * (`-Pyft.youtubeRouteB`, debug only), so main's requests are recorded for that lookup alone.
     * Every other build and page: nothing, the lookup runs as before.
     */
    fun lookupContext(lookupUrl: String): CoroutineContext = EmptyCoroutineContext

    suspend fun recover(
        lookupUrl: String,
        primary: SiteAdapterOutcome,
        nowEpochMs: Long,
        genericOnDemand: Boolean = false,
    ): SiteAdapterOutcome

    data object None : BrowserMasterFallback {
        override val enabled = false
        override suspend fun recover(
            lookupUrl: String,
            primary: SiteAdapterOutcome,
            nowEpochMs: Long,
            genericOnDemand: Boolean,
        ): SiteAdapterOutcome = primary
    }
}

class AndroidBrowserMasterFallback(
    override val capture: WebViewPlaybackCapture,
    private val sites: SiteAdapterCoordinator,
    private val engine: MasterFallbackEngine,
    private val mergeSupport: MergeSupport = DeviceMergeSupport(),
    private val requestFactory: suspend (SiteExtractionFailure, Long, String?) -> MasterRequest? =
        capture::request,
    /**
     * Phase 1.4 item 2: the site whose found pages still go through Master — YouTube in the
     * h-only live-check APK only (`-Pyft.ladderSkip=h-only`, [MasterLadderSwitch.hOnly]), so the
     * ladder's H gives the rows (on run 3 the normal path's rows came first and H never ran).
     * Every other build: null, and a found page returns at once as before.
     */
    private val masterFirstSite: String? = null,
    /**
     * Phase 1.4 item 7 (YouTube routing B) R1: true only in a route-B build
     * (`-Pyft.youtubeRouteB`, debug only, [YouTubeRouteB]); then each YouTube lookup gets its own
     * record ([lookupContext]). Every other build: false, no record.
     */
    private val youtubeRouteB: Boolean = false,
) : BrowserMasterFallback {
    override val enabled: Boolean get() = capture.enabled
    override fun mainAndMore(candidates: List<MediaCandidate>): MasterMainPresentation? =
        capture.mainAndMore(candidates)

    override suspend fun recover(
        lookupUrl: String,
        primary: SiteAdapterOutcome,
        nowEpochMs: Long,
        genericOnDemand: Boolean,
    ): SiteAdapterOutcome {
        // Successful/disabled primary paths never sample the page or launch another probe
        // (except the h-only trial's site, item 2: Master's ladder answers instead).
        if (!enabled) return primary
        if (primary is SiteAdapterOutcome.Detected && !masterFirst(lookupUrl)) return primary
        if (primary === SiteAdapterOutcome.NotHandled && !genericOnDemand) return primary
        // Item 2, run 4: the h-only trial's found page never gets the adapter's rows back (run 4's
        // Try again listed them); when Master cannot answer, the lookup fails and names the step.
        val hOnly = primary is SiteAdapterOutcome.Detected
        fun unanswered(step: String): SiteAdapterOutcome = if (!hOnly) primary else failed(
            primary, SiteExtractionFailure.NO_MEDIA_FOUND, PLAY_FIRST, listOf("master: h-only: $step"),
        )
        if (!sites.allowsMasterFallback(lookupUrl)) return unanswered("no backup for this site")
        val failure = (primary as? SiteAdapterOutcome.Failed)?.reason
            // Item 2, run 4 fix: Master's engine lets a site's module (YouTube: the ladder, H)
            // answer only when no site adapter answered (UNSUPPORTED_URL); with any other
            // reason a claimed page is skipped and the adapter's rows came back. So the h-only
            // trial's found page reaches Master as unanswered (the only Detected reaching here).
            ?: if (primary is SiteAdapterOutcome.Detected) SiteExtractionFailure.UNSUPPORTED_URL
            else SiteExtractionFailure.NO_MEDIA_FOUND
        // R2: a walled site's bot check, login or player-script answer is final, before capture.
        if (TerminalRules.blocksFallback(failure, lookupUrl)) return unanswered("final answer")
        val key = sites.videoKey(lookupUrl)
        val request = requestFactory(failure, nowEpochMs, key?.substringAfter(':'))
            ?: return unanswered("no page to ask")
        if (TerminalRules.blocksFallback(failure, request.pageUrl) ||
            !sites.allowsMasterFallback(request.pageUrl) ||
            !relatedPage(lookupUrl, request.pageUrl, key)
        ) return unanswered("another page")
        val result = try {
            // Fresh one-shot capture avoids a cached passive snapshot masking changed playback.
            // R8: the adapter's identity lets Master ask the site's own endpoint for this video.
            withContext(Dispatchers.Default) {
                engine.extract(request.copy(snapshot = null, identity = sites.identity(lookupUrl)))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return failed(primary, failure, PLAY_FIRST, listOf("master: capture unavailable"))
        }
        val scope = capture.session.currentScope()
        val samePage = scope != null && scope.generation == request.generation &&
            scope.pageUrl == request.pageUrl
        // Item 2, run 4: the site's module answers for the video, not for the page's state, so in
        // the h-only trial the same video still open (reloaded, or a changed address) keeps it.
        val sameVideo = hOnly && scope != null && key != null && sites.videoKey(scope.pageUrl) == key
        if (!samePage && !sameVideo) return unanswered("the page changed during the lookup")
        return when (result) {
            is MasterResult.Success -> {
                val focused = capture.appCandidates(result.result.candidates, key)
                    ?: result.result.candidates.filter {
                    key == null || it.videoId == key
                }
                val kept = focused.filter(mergeSupport::canMerge)
                when {
                    focused.isEmpty() -> failed(
                        primary, failure, PLAY_FIRST,
                        result.result.details + "master: focused content identity required",
                    )
                    kept.isEmpty() -> failed(
                        primary, SiteExtractionFailure.NO_MEDIA_FOUND,
                        "This phone cannot merge the captured tracks.",
                        result.result.details + "master: device merge gate rejected tracks",
                    )
                    else -> SiteAdapterOutcome.Detected(
                        (primary as? SiteAdapterOutcome.Failed)?.adapterId ?: "master",
                        kept,
                    )
                }
            }
            is MasterResult.NeedsPlayback ->
                failed(primary, failure, PLAY_FIRST, result.details)
            is MasterResult.NeedsSelection -> failed(
                primary, failure,
                "Play only the video you want, then tap Try again.",
                result.details,
            )
            is MasterResult.Failure -> failed(
                primary, result.reason,
                if (result.reason == SiteExtractionFailure.DRM_PROTECTED) {
                    "Protected media cannot be captured."
                } else {
                    "No verified media was captured. Play the video and tap Try again."
                },
                result.details,
            )
            is MasterResult.Skipped -> unanswered("skipped (${result.reason})")
        }
    }

    /** Item 2: whether [lookupUrl]'s site is the h-only trial's (never in another build). */
    override fun masterFirst(lookupUrl: String): Boolean {
        val site = masterFirstSite ?: return false
        return enabled && sites.videoKey(lookupUrl)?.substringBefore(':') == site
    }

    /** Item 7 R1: a new record per YouTube lookup in a route-B build (memory only); else nothing. */
    override fun lookupContext(lookupUrl: String): CoroutineContext =
        if (youtubeRouteB && enabled && sites.videoKey(lookupUrl)?.substringBefore(':') == YOUTUBE_SITE_ID) {
            LookupRecord()
        } else {
            EmptyCoroutineContext
        }

    private fun relatedPage(lookup: String, page: String, requestedKey: String?): Boolean {
        if (lookup == page || sites.sameContent(lookup, page)) return true
        // A feed's focused permalink can share its tab, but a different named post cannot.
        if (requestedKey == null || sites.videoKey(page) != null) return false
        val first = runCatching { URI(lookup).host }.getOrNull() ?: return false
        val second = runCatching { URI(page).host }.getOrNull() ?: return false
        return SiteScope.sameSite(first, second)
    }

    private fun failed(
        primary: SiteAdapterOutcome,
        reason: SiteExtractionFailure,
        message: String,
        details: List<String>,
    ): SiteAdapterOutcome.Failed {
        val old = primary as? SiteAdapterOutcome.Failed
        return SiteAdapterOutcome.Failed(
            adapterId = old?.adapterId ?: "master",
            reason = reason,
            message = message,
            allowsGenericFallback = old?.allowsGenericFallback ?: true,
            details = DiagnosticTextSanitizer.details(old?.details.orEmpty() + details),
        )
    }

    companion object {
        /**
         * R5: the page's player is steered to what [DeviceMergeSupport] merges — VP9/Opus WebM
         * from Android 10, AV1 only while its merges are on and a decoder plays it.
         */
        fun codecSteering(sdkInt: Int = Build.VERSION.SDK_INT): CodecSteering = CodecSteering(
            vp9 = sdkInt >= AudioVideoMuxCompatibility.WEBM_OPUS_MIN_SDK,
            av1 = AudioVideoMuxCompatibility.AV1_MP4_ENABLED &&
                sdkInt >= AudioVideoMuxCompatibility.AV1_MP4_MIN_SDK &&
                platformHasDecoder("video/av01"),
        )

        private const val PLAY_FIRST = "Play the video on this page, then tap Try again."

        /** The YouTube adapter's site ID (its video key's prefix). */
        internal const val YOUTUBE_SITE_ID = "youtube"

        fun create(
            enabled: Boolean,
            client: OkHttpClient,
            sites: SiteAdapterCoordinator,
            // Phase 1.3 D5a: the app's player-script runner, for the ladder's S only.
            playerScripts: PlayerScriptRunner? = null,
            // Phase 1.3 D7: the app's files directory, for the ladder's health file only.
            filesDir: (() -> File)? = null,
            // Phase 1.4 item 7 R1: route B's per-lookup record (-Pyft.youtubeRouteB, debug only).
            youtubeRouteB: Boolean = YouTubeRouteB.enabled,
        ): BrowserMasterFallback {
            if (!enabled) return BrowserMasterFallback.None
            val capture = WebViewPlaybackCapture(
                enabled = true,
                contentIdOf = { url -> sites.videoKey(url)?.substringAfter(':') },
                inspect = CapturedMediaMetadata(client)::inspect,
                codecs = codecSteering(),
            )
            val http = OkHttpExtractorClient(client)
            return AndroidBrowserMasterFallback(
                capture,
                sites,
                MasterFallbackEngine(
                    OkHttpMediaValidator(client), capture, MasterPolicy(enabled = true),
                    captureSelection = capture::selectMain,
                    // R6: YouTube pages are Master's own module's, never capture's.
                    // Phase 1.3 D6: the client ladder only with -Pyft.clientLadder (debug).
                    modules = listOf(MasterLadderSwitch.youTube(http, client, playerScripts, filesDir)),
                    // R8: Vimeo/X/Facebook/TikTok/Instagram contract endpoints, then capture;
                    // R9: their recipes from a signed config when the build carries its key.
                    contracts = MasterRecipeConfig.contracts(http),
                ),
                // Phase 1.4 item 2: in the h-only APK a found YouTube page goes through H too.
                masterFirstSite = if (MasterLadderSwitch.hOnly) YOUTUBE_SITE_ID else null,
                youtubeRouteB = youtubeRouteB,
            )
        }
    }
}
