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
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.android.CodecSteering
import com.alal.yft.extractor.master.android.WebViewPlaybackCapture
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.api.PlayerScriptRunner
import java.io.File
import com.alal.yft.extractor.master.verify.OkHttpMediaValidator
import com.alal.yft.extractor.master.verify.CapturedMediaMetadata
import com.alal.yft.extractor.master.present.MasterMainPresentation
import com.alal.yft.extractor.master.policy.TerminalRules
import com.alal.yft.extractor.master.social.SocialCooldown
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
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
     * F5: the record names the page's video, so it also keeps main's answer to that video's watch
     * page for Master's one cookie-less replay. Every other build and page: nothing, the lookup
     * runs as before.
     */
    fun lookupContext(lookupUrl: String): CoroutineContext = EmptyCoroutineContext

    /**
     * Phase 1.4 item 7 (YouTube routing B) R4: main's answer as the lookup takes it. In the
     * route-B trial APK (D7 `routeb`: `-Pyft.routeBTrial` with `-Pyft.youtubeRouteB`, debug only)
     * a YouTube page main found is answered as main's reading failing (`RESPONSE_CHANGED`), after
     * main asked (its requests stay in the lookup's record), so Master's route B answers on the
     * phone. Social plan S1a (9): in the Facebook trial APK (D7 `social-trial`: `-Pyft.socialTrial` with
     * `-Pyft.socialLadder`, debug only) a Facebook page main found is answered so too, so Master's
     * social ladder answers. Every other build, page and answer: [primary] itself.
     */
    fun trialPrimary(lookupUrl: String, primary: SiteAdapterOutcome): SiteAdapterOutcome = primary

    /**
     * Social plan task 12 (the phone session of 2026-10-22, observations a and g): whether
     * [lookupUrl]'s lookup asks main even when the app's shared lookup (P17) remembers main's answer
     * for the video, as Try again does — a Facebook, TikTok, X or Instagram page in the trial APK
     * (D7 `social-trial`: `-Pyft.socialTrial` with `-Pyft.socialLadder`, debug only), so main's
     * answers are in the lookup's own record for the ladder (I2, X1, F2, T2) and the trial compares
     * this lookup's rows. The phone's first lookups found main's answer remembered (an earlier
     * lookup of the video: Home's or an earlier page's), main asked nothing in them, and the ladder
     * had nothing to read. Every other build and page: false, P17 as before.
     */
    fun asksMainFresh(lookupUrl: String): Boolean = false

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
    /**
     * Phase 1.4 item 7 R4: true only in the route-B trial APK (D7 `routeb`, [YouTubeRouteB.trial],
     * with [youtubeRouteB]); then [trialPrimary] sets main's found YouTube rows aside. Every other
     * build: false.
     */
    private val routeBTrial: Boolean = false,
    /**
     * Phase 1.4 item 7 F7: in the route-B trial APK only, the one-byte check of Master's files that
     * route B's line reports ([RouteBTrialCheck]); null in every other build (and no check).
     */
    private val trialFileCheck: RouteBTrialCheck.FileCheck? = null,
    /**
     * Social plan S1a (2): true only in a social-ladder build (`-Pyft.socialLadder`, debug only,
     * [SocialLadder]); then each Facebook lookup gets its own labelled record and main's pages
     * ([lookupContext]; S2b: each TikTok lookup too). Every other build: false, no record.
     */
    private val socialLadder: Boolean = false,
    /**
     * Social plan S1a (5), S7: where a social-ladder build's Facebook lookup notes the hosts that
     * answered a 429 or a bot check ([SocialLadder.noteStops]); the app's own by default.
     */
    private val socialCooldown: SocialCooldown = SocialLadder.cooldown,
    /**
     * Social plan S1a (9): true only in the Facebook trial APK (D7 `social-trial`, [SocialLadder.trial],
     * with [socialLadder]); then [trialPrimary] sets main's found Facebook rows aside (S2b: TikTok's
     * too). Every other build: false.
     */
    private val socialTrial: Boolean = false,
    /**
     * Social plan task 14 (observation e): whether the lookup's tab showed a video (a frame of the
     * request's page and generation, `MasterBrowserSession.videoShown`); read only for a social
     * lookup's "needs playback" answer in a social-ladder build ([SocialLadder.playbackWords]).
     */
    private val tabVideo: (MasterRequest) -> Boolean = { capture.session.videoShown(it.pageUrl, it.generation) },
) : BrowserMasterFallback {
    override val enabled: Boolean get() = capture.enabled

    /**
     * Item 7 F7: main's rows the route-B trial set aside, by video, until that lookup's [recover]
     * compares Master's rows with them (memory only; the trial APK only, else always empty).
     */
    private val setAside = ConcurrentHashMap<String, List<MediaCandidate>>()

    /**
     * S1a (9): main's rows the Facebook trial set aside, by video, with the trial's answer that set
     * them aside, until that lookup's [recover] compares Master's rows with them (memory only; the
     * Facebook trial APK only, else always empty).
     */
    private val socialSetAside = ConcurrentHashMap<String, SetAside>()

    /** S1a (9): main's [rows] the trial's [answer] set aside (one lookup's). */
    private class SetAside(val answer: SiteAdapterOutcome, val rows: List<MediaCandidate>)

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
        val key = sites.videoKey(lookupUrl)
        // Social plan S1a (3): a Facebook lookup in a social-ladder build — Facebook's ladder's rows
        // name the video main's resolved link names, and its final answer gets main's words; S2b:
        // a TikTok lookup too (TikTok's ladder); S2c: an X lookup (X's). Every other build and
        // page: false, each step as before.
        val social = socialLadder && SocialLadder.claims(key)
        // Item 2, run 4: the h-only trial's found page never gets the adapter's rows back (run 4's
        // Try again listed them); when Master cannot answer, the lookup fails and names the step.
        val hOnly = primary is SiteAdapterOutcome.Detected
        fun unanswered(step: String): SiteAdapterOutcome = if (!hOnly) primary else failed(
            primary, SiteExtractionFailure.NO_MEDIA_FOUND, PLAY_FIRST, listOf("master: h-only: $step"),
        )
        // S1a (10): a social lookup Master could not answer (no page to ask, another page, the page
        // changed during the lookup, skipped) keeps its own answer (the trial's or main's: reason,
        // message, retry) with a Details line naming the step, then Master's [lines] (how far it
        // got); step 6's first run showed "Master found none" without a step. A site without a
        // backup and main's final answer stay as they are. Every other lookup: [unanswered].
        fun notUsed(step: String, lines: List<String> = emptyList()): SiteAdapterOutcome =
            if (social && primary is SiteAdapterOutcome.Failed) {
                failed(primary, primary.reason, primary.message, listOf("${SocialLadder.notUsed(key)} $step") + lines)
            } else {
                unanswered(step)
            }
        if (!sites.allowsMasterFallback(lookupUrl)) return unanswered("no backup for this site")
        val failure = (primary as? SiteAdapterOutcome.Failed)?.reason
            // Item 2, run 4 fix: Master's engine lets a site's module (YouTube: the ladder, H)
            // answer only when no site adapter answered (UNSUPPORTED_URL); with any other
            // reason a claimed page is skipped and the adapter's rows came back. So the h-only
            // trial's found page reaches Master as unanswered (the only Detected reaching here).
            ?: if (primary is SiteAdapterOutcome.Detected) SiteExtractionFailure.UNSUPPORTED_URL
            else SiteExtractionFailure.NO_MEDIA_FOUND
        // S1a (5), S7: such a lookup's hosts that answered a 429 or a bot check (main's asks; its
        // 429 is final below) cool for 30 minutes for Master's rungs that ask. Else nothing.
        if (social) SocialLadder.noteStops(socialCooldown)
        // Item 7 F7: the rows the trial set aside for this lookup (the trial APK only, else null).
        val mains = if (routeBTrial) key?.let(setAside::remove) else null
        // S1a (9): the rows the Facebook trial set aside for this lookup — with [primary], its own
        // answer — taken once (the Facebook trial APK only, else null); S2b: TikTok's too.
        val socialMains = if (social && socialTrial) key?.let { setAsideFor(it, primary) } else null
        // Phase 1.4 item 7 (YouTube routing B) F2: route B's page — a YouTube page main failed, in
        // a route-B build. When route B gives it no answer, main's failure stays the lookup's answer
        // (its reason, message and retry) with a Details line saying why (`master: route B not run:
        // <why>`). Every other build: false, each step answers as before.
        val routeBPage = youtubeRouteB && primary is SiteAdapterOutcome.Failed &&
            key?.substringBefore(':') == YOUTUBE_SITE_ID
        fun mains(details: List<String>) =
            failed(primary, failure, (primary as SiteAdapterOutcome.Failed).message, details)
        fun notRun(why: String) = mains(listOf("$ROUTE_B_NOT_RUN $why"))
        // R2: a walled site's bot check, login or player-script answer is final, before capture.
        if (TerminalRules.blocksFallback(failure, lookupUrl)) {
            return if (routeBPage) notRun("${failure.name} is final") else unanswered("final answer")
        }
        // Item 7 R3: in a route-B build a YouTube page main failed with a recoverable reason is
        // answered by Master's module (the engine's rule, inside the lookup's record). When it gives
        // no rows, main's failure stays the lookup's answer — its reason, message and retry — with
        // Master's details added. Every other build: false.
        val routeB = routeBPage && TerminalRules.routeBRecoverable(failure)
        fun noRows(reason: SiteExtractionFailure, message: String, details: List<String>, final: Boolean = false) =
            if (routeB) mains(details) else failed(primary, reason, message, details, final)
        // F2: route B's lookup that never reached Master says why; every other lookup as before.
        fun unrouted(step: String) = if (routeB) notRun(step) else notUsed(step)
        val request = requestFactory(failure, nowEpochMs, key?.substringAfter(':'))
            ?: return unrouted("no page to ask")
        if (TerminalRules.blocksFallback(failure, request.pageUrl) ||
            !sites.allowsMasterFallback(request.pageUrl) ||
            !relatedPage(lookupUrl, request.pageUrl, key)
        ) return unrouted("another page")
        val result = try {
            // Fresh one-shot capture avoids a cached passive snapshot masking changed playback.
            // R8: the adapter's identity lets Master ask the site's own endpoint for this video.
            withContext(Dispatchers.Default) {
                engine.extract(request.copy(snapshot = null, identity = sites.identity(lookupUrl)))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return noRows(failure, PLAY_FIRST, listOf("master: capture unavailable"))
        }
        // F2: the engine skipped route B (no record, or main asked nothing in it): the line says
        // which, from the lookup's record; main's failure stays, whatever the page shows now.
        if (routeB && result is MasterResult.Skipped) return notRun(skipped(result.reason))
        val scope = capture.session.currentScope()
        val samePage = scope != null && scope.generation == request.generation &&
            scope.pageUrl == request.pageUrl
        // Item 2, run 4: the site's module answers for the video, not for the page's state, so in
        // the h-only trial the same video still open (reloaded, or a changed address) keeps it.
        // Item 7 R3: route B's answer is the module's too.
        val videoOpen = scope != null && key != null && sites.videoKey(scope.pageUrl) == key
        val sameVideo = (hOnly || routeB) && videoOpen
        // S1a (10): a social lookup's ladder rows (F1, the plugin player; F2, main's pages) are the
        // video's too, so the same video still open keeps them — step 6's first lookup of a watch
        // link (VPN) most likely lost its rows so, while the page settled. The tab's own playback
        // (F3) and every other answer still need the same page.
        val ladderRows = social && videoOpen && result is MasterResult.Success &&
            (result.stage == MasterStage.CONTRACT || result.stage == MasterStage.PAGE_DATA)
        if (!samePage && !sameVideo && !ladderRows) {
            // F2: route B's answer was for a video the page no longer shows: dropped, and said so.
            if (routeB) return mains(listOf(ROUTE_B_DROPPED))
            // S1a (10): a social lookup's line says which change (words only) and how far Master got.
            val change = when {
                !social -> ""
                scope == null -> ": the page closed"
                scope.pageUrl == request.pageUrl -> ": the same address again"
                videoOpen -> ": another address of the same video"
                else -> ": another page"
            }
            return notUsed("the page changed during the lookup$change", linesOf(result))
        }
        return when (result) {
            is MasterResult.Success -> {
                // S1a (3): the ladder's rows carry the key of the video it read (a share link's video);
                // S1a (4): F2's rows too (main's pages: the page data; this request has no snapshot).
                val rowsKey = if (
                    social && (result.stage == MasterStage.CONTRACT || result.stage == MasterStage.PAGE_DATA)
                ) {
                    SocialLadder.rowsKey(sites, lookupUrl, key)
                } else {
                    key
                }
                val focused = capture.appCandidates(result.result.candidates, rowsKey)
                    ?: result.result.candidates.filter {
                    rowsKey == null || it.videoId == rowsKey
                }
                val kept = focused.filter(mergeSupport::canMerge)
                when {
                    focused.isEmpty() -> noRows(
                        failure, PLAY_FIRST,
                        result.result.details + "master: focused content identity required",
                    )
                    kept.isEmpty() -> noRows(
                        SiteExtractionFailure.NO_MEDIA_FOUND,
                        "This phone cannot merge the captured tracks.",
                        result.result.details + "master: device merge gate rejected tracks",
                    )
                    else -> SiteAdapterOutcome.Detected(
                        (primary as? SiteAdapterOutcome.Failed)?.adapterId ?: "master",
                        kept,
                        // Item 7 R4: route B's rows carry its details (its line first), so a
                        // route-B build shows its counts; every other answer: none, as before.
                        // F7: in the trial its line also compares the rows with main's set aside.
                        // S1a (9): in the Facebook trial, Facebook's line compares them so too
                        // (S2b: TikTok's line, in a TikTok lookup).
                        details = if (routeB) {
                            compared(DiagnosticTextSanitizer.details(result.result.details), kept, mains)
                        } else if (socialMains != null) {
                            socialCompared(
                                DiagnosticTextSanitizer.details(result.result.details), kept, socialMains, lookupUrl, key,
                            )
                        } else emptyList(),
                    )
                }
            }
            // Task 14 (observation e): a social lookup's words follow the judge's class and the tab
            // (a wall: the sign-in words; no video in the tab: main's words, with Try again; the
            // playback words only when the tab has a video to play), with a line saying which and
            // why before Master's own. Only the words change: the reason stays main's. Every other
            // lookup and build: the playback words, as before.
            is MasterResult.NeedsPlayback -> if (social && primary is SiteAdapterOutcome.Failed) {
                val pick = SocialLadder.playbackWords(key, result.details, tabVideo(request))
                val message = when (pick.words) {
                    SocialLadder.Words.PLAYBACK -> PLAY_FIRST
                    SocialLadder.Words.SIGN_IN -> primary.message
                        .takeIf { primary.reason == SiteExtractionFailure.LOGIN_REQUIRED }
                        ?: SocialLadder.signInMessage(key)
                    SocialLadder.Words.MAINS -> primary.message
                }
                noRows(failure, message, listOf(SocialLadder.wordsLine(key, pick)) + result.details)
            } else {
                noRows(failure, PLAY_FIRST, result.details)
            }
            is MasterResult.NeedsSelection -> noRows(
                failure,
                "Play only the video you want, then tap Try again.",
                result.details,
            )
            is MasterResult.Failure -> noRows(
                result.reason,
                // S1a (3): the ladder's final answer (private or removed) in main's words; S2b:
                // TikTok's photo post too; S2c: X's photo post (task 0: X's widget 404 is never final).
                SocialLadder.finalMessage(result.reason, key, result.details)?.takeIf { social }
                    ?: if (result.reason == SiteExtractionFailure.DRM_PROTECTED) {
                        "Protected media cannot be captured."
                    } else {
                        "No verified media was captured. Play the video and tap Try again."
                    },
                result.details,
                // Task 15 (F3b): a ladder's photos-only answer is final (no Try again: the post has
                // no video, so asking again cannot change it); every other answer as before.
                final = social && SocialLadder.photosOnly(result.reason, key, result.details),
            )
            is MasterResult.Skipped -> notUsed("skipped (${result.reason})")
        }
    }

    /** S1a (10): Master's own Details lines of [result] (none for a skipped lookup). */
    private fun linesOf(result: MasterResult): List<String> = when (result) {
        is MasterResult.Success -> result.result.details
        is MasterResult.Failure -> result.details
        is MasterResult.NeedsPlayback -> result.details
        is MasterResult.NeedsSelection -> result.details
        is MasterResult.Skipped -> emptyList()
    }

    /**
     * Item 7 F2: why the engine skipped route B after main's [reason], from the lookup's record —
     * none, or main's requests in it (none: how main's answer reached the lookup without asking,
     * [LookupRecord.reuse], P17; some: the engine's own rule declined).
     */
    private suspend fun skipped(reason: SiteExtractionFailure): String {
        val record = currentCoroutineContext()[LookupRecord]
            ?: return "skipped (${reason.name}); no lookup record"
        val reuse = record.reuse?.let { "; main's answer reused: ${it.label}" }.orEmpty()
        return "skipped (${reason.name}); main's requests in this lookup: ${record.mainAsks}$reuse"
    }

    /**
     * Item 7 F7: route B's line with the trial's comparison of Master's [rows] with main's
     * set-aside [mains] appended ([RouteBTrialCheck]: a one-byte check of the files now, whether
     * the links and download headers are main's, names only). No set-aside rows: [details] as is.
     */
    private suspend fun compared(
        details: List<String>,
        rows: List<MediaCandidate>,
        mains: List<MediaCandidate>?,
    ): List<String> {
        if (mains == null) return details
        val at = details.indexOfFirst { it.startsWith(YouTubeRouteB.LINE) }
        if (at < 0) return details
        // The trial's check never fails the lookup: a failure only says so on the line.
        val segment = try {
            RouteBTrialCheck.segment(rows, mains, trialFileCheck)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            "; trial check failed (${error.javaClass.simpleName})"
        }
        return details.toMutableList().also { it[at] = it[at] + segment }
    }

    /**
     * S1a (9): Facebook's line (Master's first `master: facebook` line, the ladder's) with the
     * Facebook trial's comparison of Master's [rows] with main's set-aside [mains] appended
     * ([SocialLadder.trialSegment]; main's rows carry [key], the link's, so a link main resolves
     * first counts as the video its resolved link names, as Master's rows do); details without
     * such a line (the tab's own playback alone) get one first. The comparison never fails the
     * lookup: a failure only says so on the line. S2b: a TikTok lookup's line (`master: tiktok`,
     * [SocialLadder.line]) so too; S2c: an X lookup's (`master: x`), comparing the item.
     */
    private suspend fun socialCompared(
        details: List<String>,
        rows: List<MediaCandidate>,
        mains: List<MediaCandidate>,
        lookupUrl: String,
        key: String?,
    ): List<String> {
        val segment = try {
            val resolved = SocialLadder.rowsKey(sites, lookupUrl, key)
            SocialLadder.trialSegment(rows, mains) { id -> if (id != null && id == key) resolved else id }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            "${SocialLadder.TRIAL_SEGMENT}check failed (${error.javaClass.simpleName})"
        }
        val line = SocialLadder.line(key)
        val at = details.indexOfFirst { it.startsWith(line) }
        if (at < 0) return listOf(line + segment) + details
        return details.toMutableList().also { it[at] = it[at] + segment }
    }

    /**
     * S1a (9): the rows the Facebook trial set aside for [key] with [answer] (that lookup's own
     * answer, [trialPrimary]), taken once; null for any other answer (none reused by a later one).
     */
    private fun setAsideFor(key: String, answer: SiteAdapterOutcome): List<MediaCandidate>? {
        val kept = socialSetAside[key]?.takeIf { it.answer === answer } ?: return null
        return kept.rows.takeIf { socialSetAside.remove(key, kept) }
    }

    /** Item 2: whether [lookupUrl]'s site is the h-only trial's (never in another build). */
    override fun masterFirst(lookupUrl: String): Boolean {
        val site = masterFirstSite ?: return false
        return enabled && sites.videoKey(lookupUrl)?.substringBefore(':') == site
    }

    /**
     * Item 7 R4: the route-B trial's main answer — a YouTube page main found becomes main's
     * `RESPONSE_CHANGED` (main's adapter, Try again, no page files), so route B answers from the
     * lookup's record; when Master gives no rows the trial's message stays with Master's details.
     * Main's failures (a bot check stays final), another site's page and every other build: as is.
     * F7: the rows set aside are kept (memory) until that lookup's [recover] compares Master's
     * rows with them.
     */
    override fun trialPrimary(lookupUrl: String, primary: SiteAdapterOutcome): SiteAdapterOutcome {
        // Social plan S1a (9): the Facebook trial's own branch (null: not its build or lookup);
        // S2b: TikTok's pages too; S2c: X's.
        socialTrial(lookupUrl, primary)?.let { return it }
        if (!routeBTrial || !youtubeRouteB || !enabled) return primary
        val key = sites.videoKey(lookupUrl)?.takeIf { it.substringBefore(':') == YOUTUBE_SITE_ID } ?: return primary
        // F7: this lookup's set-aside rows only — none from an earlier lookup of the video.
        setAside.remove(key)
        if (primary !is SiteAdapterOutcome.Detected) return primary
        setAside[key] = primary.candidates
        return SiteAdapterOutcome.Failed(
            adapterId = primary.adapterId,
            reason = SiteExtractionFailure.RESPONSE_CHANGED,
            message = ROUTE_B_TRIAL_MESSAGE,
            allowsGenericFallback = false,
            details = listOf("$ROUTE_B_TRIAL_DETAIL ${primary.candidates.size} rows set aside"),
        )
    }

    /**
     * Social plan S1a (9): the Facebook trial's main answer (D7 `social-trial`) — a Facebook page main
     * found, every link form, becomes main's `RESPONSE_CHANGED` (main's adapter, Try again, no page
     * files), so Master's ladder answers. Main asked as today: its pages and its resolved link stay
     * in the lookup's `MainPages` (F2, the links main resolves first). Main's failures (final ones
     * too) stay as they are. Its rows are kept (memory) until that lookup's [recover] compares
     * Master's rows with them. S2b: a TikTok page main found so too (the same APK; TikTok's message
     * and line, [SocialLadder.trialMessage]). Null in every other build and for every other site's
     * page.
     */
    private fun socialTrial(lookupUrl: String, primary: SiteAdapterOutcome): SiteAdapterOutcome? {
        if (!socialTrial || !socialLadder || !enabled) return null
        val key = sites.videoKey(lookupUrl)?.takeIf { SocialLadder.claims(it) } ?: return null
        // This lookup's set-aside rows only — none from an earlier lookup of the video.
        socialSetAside.remove(key)
        if (primary !is SiteAdapterOutcome.Detected) return primary
        val trial = SiteAdapterOutcome.Failed(
            adapterId = primary.adapterId,
            reason = SiteExtractionFailure.RESPONSE_CHANGED,
            message = SocialLadder.trialMessage(key),
            allowsGenericFallback = false,
            details = listOf("${SocialLadder.trialDetail(key)} ${primary.candidates.size} rows set aside"),
        )
        socialSetAside[key] = SetAside(trial, primary.candidates)
        return trial
    }

    /** Task 12: the trial's social lookups ask main every time (Try again's way); else false. */
    override fun asksMainFresh(lookupUrl: String): Boolean =
        socialTrial && socialLadder && enabled && SocialLadder.claims(sites.videoKey(lookupUrl))

    /**
     * Item 7 R1: a new record per YouTube lookup in a route-B build (memory only); else nothing.
     * F5: it names the lookup's video, so main's watch page GET of it is kept for Master's replay.
     * Social plan S1a (2): in a social-ladder build a Facebook lookup (every link form) gets its
     * own record with Facebook's labels and its own `MainPages` ([SocialLadder.lookupContext]);
     * S2b: a TikTok lookup its own with TikTok's; S2c: an X lookup its own with X's.
     */
    override fun lookupContext(lookupUrl: String): CoroutineContext {
        val key = sites.videoKey(lookupUrl)
        return if (youtubeRouteB && enabled && key?.substringBefore(':') == YOUTUBE_SITE_ID) {
            LookupRecord(watchPageVideoId = key.substringAfter(':'))
        } else if (socialLadder && enabled && SocialLadder.claims(key)) {
            SocialLadder.lookupContext(key)
        } else {
            EmptyCoroutineContext
        }
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
        // Task 15 (F3b): a final answer whatever its reason (a ladder's photos-only answer).
        final: Boolean = false,
    ): SiteAdapterOutcome.Failed {
        val old = primary as? SiteAdapterOutcome.Failed
        return SiteAdapterOutcome.Failed(
            adapterId = old?.adapterId ?: "master",
            reason = reason,
            message = message,
            allowsGenericFallback = old?.allowsGenericFallback ?: true,
            details = DiagnosticTextSanitizer.details(old?.details.orEmpty() + details),
            final = final,
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

        /** Item 7 R4: the route-B trial's answer when Master gives no rows (main's were set aside). */
        internal const val ROUTE_B_TRIAL_MESSAGE =
            "Route B trial: main's rows were set aside and Master found none. Tap Try again."

        /** Item 7 R4: the route-B trial's Details line (main's row count follows). */
        internal const val ROUTE_B_TRIAL_DETAIL = "master: route B trial: main's"

        /** Item 7 F2: route B's page without route B's answer — the Details line (why follows). */
        internal const val ROUTE_B_NOT_RUN = "master: route B not run:"

        /** Item 7 F2: route B answered for a video the page no longer shows. */
        internal const val ROUTE_B_DROPPED = "master: route B answer dropped: the page changed during the lookup"

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
            // Phase 1.4 item 7 R4: the route-B trial (D7 `routeb`, -Pyft.routeBTrial, debug only).
            routeBTrial: Boolean = YouTubeRouteB.trial,
            // Social plan S1a (2): the social ladder's per-lookup record (-Pyft.socialLadder, debug only).
            socialLadder: Boolean = SocialLadder.enabled,
            // Social plan S1a (9): the Facebook trial (D7 `social-trial`, -Pyft.socialTrial, debug only).
            socialTrial: Boolean = SocialLadder.trial,
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
                    // Item 7 R3: the engine's route-B rule only in a route-B build.
                    // Social plan S1a (6): S6's row check in a ladder's lookup only with -Pyft.socialLadder (debug);
                    // task 9: one check and reader for every site, each read on its own lookup's site's hosts.
                    SocialLadder.validator(OkHttpMediaValidator(client), client, socialLadder), capture,
                    MasterPolicy(enabled = true, youtubeRouteB = youtubeRouteB),
                    captureSelection = capture::selectMain,
                    // R6: YouTube pages are Master's own module's, never capture's.
                    // Phase 1.3 D6: the client ladder only with -Pyft.clientLadder (debug).
                    // Phase 1.4 item 7 R2: route B's wrappers only with -Pyft.youtubeRouteB (debug).
                    modules = listOf(
                        MasterLadderSwitch.youTube(http, client, playerScripts, filesDir, routeB = youtubeRouteB),
                    ),
                    // R8: Vimeo/X/Facebook/TikTok/Instagram contract endpoints, then capture;
                    // R9: their recipes from a signed config when the build carries its key.
                    contracts = MasterRecipeConfig.contracts(http),
                    // Social plan S1a (3): Facebook's ladder only with -Pyft.socialLadder (debug).
                    social = SocialLadder.stage(http, sites::identity, socialLadder),
                ),
                // Phase 1.4 item 2: in the h-only APK a found YouTube page goes through H too.
                masterFirstSite = if (MasterLadderSwitch.hOnly) YOUTUBE_SITE_ID else null,
                youtubeRouteB = youtubeRouteB,
                routeBTrial = routeBTrial,
                // Item 7 F7: the trial's one-byte file check (the trial APK only).
                trialFileCheck = if (routeBTrial && youtubeRouteB) RouteBTrialCheck.OkHttpFileCheck(client) else null,
                socialLadder = socialLadder,
                socialTrial = socialTrial,
            )
        }
    }
}
