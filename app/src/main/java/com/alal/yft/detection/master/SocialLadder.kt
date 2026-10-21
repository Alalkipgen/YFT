package com.alal.yft.detection.master

import com.alal.yft.BuildConfig
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.social.FacebookLadder
import com.alal.yft.extractor.master.social.MainPageRecorder
import com.alal.yft.extractor.master.social.MainPages
import com.alal.yft.extractor.master.social.SiteWays
import com.alal.yft.extractor.master.social.SocialCooldown
import com.alal.yft.extractor.master.social.SocialRowCheck
import com.alal.yft.extractor.master.social.SocialStage
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.OkHttpClient

/**
 * Social plan S1a (`MASTER_KEY_SOCIAL_PLAN.md` §7.3): the build's social-ladder switch and its app
 * wiring, Facebook first (the pilot). On only in a debug build made with `-Pyft.socialLadder=true`
 * and `-Pyft.masterCapture=true`; release, preview and every other build: off, and each piece here
 * is exactly today's.
 */
internal object SocialLadder {
    val enabled: Boolean get() = BuildConfig.SOCIAL_LADDER

    /**
     * S1a (9): the Facebook trial (the D7 APK `fb-trial`, S1a's phone proof): on only in a debug
     * build made with `-Pyft.socialTrial=true` as well (the ladder and Master capture on); then a
     * Facebook page main found is answered as main's failure, so Master's ladder answers
     * ([AndroidBrowserMasterFallback.trialPrimary]). Every other build: off.
     */
    val trial: Boolean get() = BuildConfig.SOCIAL_TRIAL

    /** The pilot's site ID (main's Facebook adapter; its video key's prefix). */
    const val FACEBOOK_SITE_ID = "facebook"

    /**
     * S1a (5), S7: the hosts cooling after a 429 or a bot check (30 minutes), the app process's:
     * Facebook's ladder does not ask a cooling host in any lookup ([stage], [noteStops]). Memory
     * only; untouched in every other build (no ladder, nothing noted).
     */
    val cooldown = SocialCooldown()

    /**
     * S1a (2): the client main's site adapters share. In a social-ladder build it records each
     * request into the lookup's record (route B's recorder, [YouTubeRouteB.mainClient]) and keeps
     * main's pages in the lookup's [MainPages] ([MainPageRecorder]); a lookup without them only
     * passes through. Otherwise it is today's client (route B's, as before).
     */
    fun mainClient(client: OkHttpClient, social: Boolean = enabled): ExtractorHttpClient =
        pages(YouTubeRouteB.mainClient(client, routeB = YouTubeRouteB.enabled || social), social)

    /** S1a (2): [http] keeping main's pages when [social]; else [http] itself. */
    fun pages(http: ExtractorHttpClient, social: Boolean = enabled): ExtractorHttpClient =
        if (social) MainPageRecorder(http) else http

    /** S1a (2): whether [videoKey] ("site:contentId") is the pilot's: every Facebook link form. */
    fun claims(videoKey: String?): Boolean = videoKey?.substringBefore(':') == FACEBOOK_SITE_ID

    /**
     * S1a (2): a new lookup's context for a Facebook page — its own request record with Facebook's
     * labels (each ask's way and its answer's mark: the S7 stops read them) and its own
     * [MainPages]; memory only, dropped with the lookup.
     */
    fun lookupContext(): CoroutineContext = LookupRecord(labels = SiteWays.FACEBOOK.labels()) + MainPages()

    /**
     * S1a (3): Master's social rung in a social-ladder build: Facebook's ladder ([FacebookLadder]:
     * F1, the plugin player, judged) over Master's own client [http], claiming only a lookup with
     * its own record ([lookupContext]); [identify]: main's adapters. Its recipes come from the same
     * source as the engine's ([MasterRecipeConfig]); S1a (5): its stops cool in [cooldown] (the
     * app's, by default). Every other build: null, the engine as before.
     */
    fun stage(
        http: ExtractorHttpClient,
        identify: (String) -> SitePageIdentity?,
        social: Boolean = enabled,
        cooldown: SocialCooldown = SocialLadder.cooldown,
    ): SocialStage? = if (social) FacebookLadder(http, identify, MasterRecipeConfig::contracts, cooldown) else null

    /**
     * S1a (6), S6: the engine's file check. In a social-ladder build a Facebook lookup (its own
     * record and [MainPages], [lookupContext]) checks every row Master offers with V and the init
     * check ([SocialRowCheck]: the file's first bytes, read by [reader]'s reader under route B's
     * rules) instead of [validator]; every other lookup gets [validator] itself. Every other build:
     * [validator] itself, and [reader] is never called.
     */
    fun validator(
        validator: MasterMediaValidator,
        social: Boolean = enabled,
        reader: () -> MediaStartReader,
    ): MasterMediaValidator = if (social) SocialRowCheck(validator, reader()) else validator

    /**
     * S1a (5), S7: the hosts that answered a 429 or a bot check in the current lookup (its record:
     * main's asks included) start their 30-minute cooldown in [cooldown]; main's own 429 ends the
     * lookup before Master runs, so the next lookups' F1 does not ask that host. A lookup without
     * a record: nothing.
     */
    suspend fun noteStops(cooldown: SocialCooldown = SocialLadder.cooldown) {
        currentCoroutineContext()[LookupRecord]?.stopHosts()?.let(cooldown::note)
    }

    /**
     * S1a (3): the video key the ladder's rows carry in [lookupUrl]'s lookup: for a link main
     * resolves first (a share link, a post), the one video main's resolved link names (the
     * lookup's [MainPages]); else the lookup's [key].
     */
    suspend fun rowsKey(sites: SiteAdapterCoordinator, lookupUrl: String, key: String?): String? {
        val identity = sites.identity(lookupUrl) ?: return key
        val resolved = currentCoroutineContext()[MainPages]?.resolved(identity, sites::identity) ?: return key
        return "${resolved.siteId}:${resolved.contentId}"
    }

    /**
     * S1a (3): main's words for the ladder's final answer (the page's route: private or removed),
     * so a Facebook lookup says what main says for it; null for every other reason (as before).
     */
    fun finalMessage(reason: SiteExtractionFailure): String? = when (reason) {
        SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE -> "This Facebook post is private or no longer available."
        else -> null
    }

    /** S1a (9): the start of Facebook's lines in Master's details (the ladder's first). */
    const val LINE = "master: facebook"

    /** S1a (9): the Facebook trial's answer when Master gives none (main's rows were set aside). */
    const val TRIAL_MESSAGE = "Facebook trial: main's rows were set aside and Master found none. Tap Try again."

    /** S1a (9): the Facebook trial's Details line (main's row count follows). */
    const val TRIAL_DETAIL = "master: facebook trial: main's"

    /** S1a (9): the start of the trial's segment on Facebook's line ([trialSegment]). */
    const val TRIAL_SEGMENT = "; trial: "

    /**
     * S1a (10): a Facebook lookup's Details line when Master could not answer it (the trial's or
     * main's own answer stays): the step follows (no page to ask, another page, the page changed
     * during the lookup and which change, skipped), then Master's own lines. Words only: never a
     * link or an ID.
     */
    const val NOT_USED = "master: facebook: Master's answer not used:"

    /** S1a (9): the most two durations may differ and still count as the same, in milliseconds. */
    private const val DURATION_SLACK_MILLIS = 1_000L

    /**
     * S1a (9): the Facebook trial's comparison of Master's [rows] with main's set-aside [mains], for
     * Facebook's line: `; trial: Master's rows M, main's N; the same video: yes|no; duration within
     * 1 s: yes|no|unknown`. The same video: every row of Master's names the one video main's rows
     * name ([video]: the video a row's key names in the lookup — main's rows carry the link's own
     * key, so a link main resolves first names the video its resolved link names); a row without a
     * key is not. The duration: every duration Master's rows state within 1 s of every one main's
     * state; unknown when either side states none. S1a (10): Master's rows state their files' own
     * lengths (S6's read of each file's start, [SocialRowCheck]; the plugin player's answer names
     * none, so on step 6's first run every line said unknown); main's rows the page's. Counts and
     * yes/no only: never a link or an ID.
     */
    fun trialSegment(rows: List<MediaCandidate>, mains: List<MediaCandidate>, video: (String?) -> String?): String {
        val mainVideo = mains.map { video(it.videoId) }.distinct().singleOrNull()
        val sameVideo = mainVideo != null && rows.isNotEmpty() && rows.all { video(it.videoId) == mainVideo }
        val masterMillis = rows.mapNotNull { row -> row.durationMillis?.takeIf { it > 0 } }
        val mainMillis = mains.mapNotNull { row -> row.durationMillis?.takeIf { it > 0 } }
        val duration = when {
            masterMillis.isEmpty() || mainMillis.isEmpty() -> "unknown"
            masterMillis.all { master -> mainMillis.all { abs(master - it) <= DURATION_SLACK_MILLIS } } -> "yes"
            else -> "no"
        }
        return "${TRIAL_SEGMENT}Master's rows ${rows.size}, main's ${mains.size}; " +
            "the same video: ${if (sameVideo) "yes" else "no"}; duration within 1 s: $duration"
    }

    /**
     * S1a (9): the Facebook trial's line of rows Master found (Facebook's line with the trial's
     * segment, [trialSegment]), for the browser's notice; null for every other answer (no other
     * build has one).
     */
    fun trialNotice(outcome: SiteAdapterOutcome): String? =
        (outcome as? SiteAdapterOutcome.Detected)?.details?.firstOrNull { it.startsWith(LINE) && TRIAL_SEGMENT in it }
}
