package com.alal.yft.detection.master

import com.alal.yft.BuildConfig
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.detection.OkHttpExtractorClient
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.social.FacebookLadder
import com.alal.yft.extractor.master.social.InstagramLadder
import com.alal.yft.extractor.master.social.MainPageRecorder
import com.alal.yft.extractor.master.social.MainPages
import com.alal.yft.extractor.master.social.SiteWays
import com.alal.yft.extractor.master.social.SocialCooldown
import com.alal.yft.extractor.master.social.SocialRowCheck
import com.alal.yft.extractor.master.social.SocialStage
import com.alal.yft.extractor.master.social.SocialStages
import com.alal.yft.extractor.master.social.TikTokLadder
import com.alal.yft.extractor.master.social.XLadder
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import com.alal.yft.extractor.sites.x.XExtractor
import java.net.URI
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.OkHttpClient

/**
 * Social plan S1a (`MASTER_KEY_SOCIAL_PLAN.md` §7.3): the build's social-ladder switch and its app
 * wiring, Facebook first (the pilot); S2b: TikTok's too; S2c: X's; S2d: Instagram's ([SITES]). On only in a debug build made
 * with `-Pyft.socialLadder=true` and `-Pyft.masterCapture=true`; release, preview and every other
 * build: off, and each piece here is exactly today's.
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

    /** S2b: TikTok's site ID (main's TikTok adapter; its video key's prefix). */
    const val TIKTOK_SITE_ID = "tiktok"

    /** S2c: X's site ID (main's X adapter; its video key's prefix). */
    const val X_SITE_ID = "x"

    /** S2d: Instagram's site ID (main's Instagram adapter; its video key's prefix). */
    const val INSTAGRAM_SITE_ID = "instagram"

    /**
     * S2b: the sites with a ladder (each lookup's own record and pages, its trial): Facebook,
     * TikTok; S2c: X; S2d: Instagram.
     */
    val SITES: Set<String> = setOf(FACEBOOK_SITE_ID, TIKTOK_SITE_ID, X_SITE_ID, INSTAGRAM_SITE_ID)

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

    /**
     * S2b: main's TikTok adapter's own client (P39: the answers' cookies sent back, today's). In a
     * social-ladder build it also records each request into the lookup's record and keeps main's
     * pages in the lookup's [MainPages] (T2 reads them; the S7 stops and budget count its asks); a
     * lookup without them only passes through. Otherwise it is today's client.
     */
    fun tiktokClient(client: OkHttpClient, social: Boolean = enabled): ExtractorHttpClient {
        val http = OkHttpExtractorClient(
            client = client,
            policy = OkHttpExtractorClient.Policy(sendResponseCookies = true),
        )
        return if (social) pages(LookupRecorder(http), social) else http
    }

    /**
     * S1a (2): whether [videoKey] ("site:contentId") is a ladder's: every Facebook link form; S2b:
     * every TikTok link form too; S2c: every X link form; S2d: every Instagram link form ([SITES]).
     */
    fun claims(videoKey: String?): Boolean = siteOf(videoKey) != null

    /** S2b: [videoKey]'s site when it has a ladder ([SITES]), else null. */
    fun siteOf(videoKey: String?): String? = videoKey?.substringBefore(':')?.takeIf { it in SITES }

    /**
     * S1a (2): a new lookup's context for a ladder's page ([videoKey]'s site; Facebook's without
     * one) — its own request record with the site's labels (each ask's way and its answer's mark:
     * the S7 stops read them) and its own [MainPages], which names the site (S2b: V asks that
     * site's media hosts); memory only, dropped with the lookup.
     */
    fun lookupContext(videoKey: String? = null): CoroutineContext {
        val site = siteOf(videoKey) ?: FACEBOOK_SITE_ID
        val labels = checkNotNull(SiteWays.site(site)) { site }.labels()
        return LookupRecord(labels = labels) + MainPages(site = site)
    }

    /**
     * S1a (3): Master's social rung in a social-ladder build: Facebook's ladder ([FacebookLadder]:
     * F1, the plugin player, judged), S2b TikTok's ([TikTokLadder]: T1, the embed player,
     * judged; T2, main's pages), S2c X's ([XLadder]: X1, main's answer replayed, judged, no new
     * ask) and S2d Instagram's ([InstagramLadder]: I2, main's answers judged, no new ask; the
     * tab-fetch probe, off by default, when the lookup's context has one), each claiming only its
     * own site's lookup with its own record
     * ([lookupContext]; [SocialStages]: the first that claims it), over Master's own client
     * [http]; [identify]: main's adapters. Their recipes come from the same source as the
     * engine's ([MasterRecipeConfig]); S1a (5): their stops cool in [cooldown] (the app's, by
     * default). Every other build: null, the engine as before.
     */
    fun stage(
        http: ExtractorHttpClient,
        identify: (String) -> SitePageIdentity?,
        social: Boolean = enabled,
        cooldown: SocialCooldown = SocialLadder.cooldown,
    ): SocialStage? = if (social) {
        SocialStages(
            listOf(
                FacebookLadder(http, identify, MasterRecipeConfig::contracts, cooldown),
                TikTokLadder(http, identify, MasterRecipeConfig::contracts, cooldown),
                XLadder(http, identify, MasterRecipeConfig::contracts, cooldown),
                InstagramLadder(http, MasterRecipeConfig::contracts, cooldown),
            ),
        )
    } else {
        null
    }

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
     * so a Facebook lookup says what main says for it; S2b: a TikTok lookup's photo post (the
     * ladder's PHOTO_ONLY line with its final step in [details]: TikTok's ladder said it, no other
     * stage) in main's words for it. S2c: an X lookup's photo post (X's PHOTO_ONLY line, its final
     * step) in main's words ([XExtractor.PHOTOS_MESSAGE]); X's ladder has no REMOVED answer (task 0:
     * the widget's 404 alone is no proof the post was deleted, so it is never final and main's
     * reason and words stay). S2d: an Instagram lookup's photo post
     * (Instagram's PHOTO_ONLY line, its final step) in main's words
     * ([InstagramExtractor.PHOTOS_MESSAGE]); Instagram's ladder has no REMOVED answer (decision 19
     * (vi)). Null for every other reason and site (as before).
     */
    fun finalMessage(
        reason: SiteExtractionFailure,
        videoKey: String? = null,
        details: List<String> = emptyList(),
    ): String? = when (siteOf(videoKey) ?: FACEBOOK_SITE_ID) {
        FACEBOOK_SITE_ID -> when (reason) {
            SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE -> "This Facebook post is private or no longer available."
            else -> null
        }
        TIKTOK_SITE_ID -> "This TikTok post has no downloadable video.".takeIf {
            reason == SiteExtractionFailure.NO_MEDIA_FOUND && finalLine(videoKey, details, "PHOTO_ONLY")
        }
        X_SITE_ID -> XExtractor.PHOTOS_MESSAGE.takeIf {
            reason == SiteExtractionFailure.NO_MEDIA_FOUND && finalLine(videoKey, details, "PHOTO_ONLY")
        }
        INSTAGRAM_SITE_ID -> InstagramExtractor.PHOTOS_MESSAGE.takeIf {
            reason == SiteExtractionFailure.NO_MEDIA_FOUND && finalLine(videoKey, details, "PHOTO_ONLY")
        }
        else -> null
    }

    /** S2b: whether [details] has [videoKey]'s site's ladder line of [answerClass] with its final step. */
    private fun finalLine(videoKey: String?, details: List<String>, answerClass: String): Boolean =
        details.any { line -> line.startsWith(line(videoKey)) && answerClass in line && "next: FINAL" in line }

    /** S1a (9): the start of Facebook's lines in Master's details (the ladder's first). */
    const val LINE = "master: facebook"

    /** S2b: the start of [videoKey]'s site's lines in Master's details (Facebook's without one). */
    fun line(videoKey: String?): String = "master: ${siteOf(videoKey) ?: FACEBOOK_SITE_ID}"

    /** S2b: the trial's answer when Master gives none, for [videoKey]'s site (Facebook's: [TRIAL_MESSAGE]). */
    fun trialMessage(videoKey: String?): String =
        "${nameOf(videoKey)} trial: main's rows were set aside and Master found none. Tap Try again."

    /** S2b: the trial's Details line for [videoKey]'s site (Facebook's: [TRIAL_DETAIL]). */
    fun trialDetail(videoKey: String?): String = "${line(videoKey)} trial: main's"

    /** S2b: the "not used" line's start for [videoKey]'s site (Facebook's: [NOT_USED]). */
    fun notUsed(videoKey: String?): String = "${line(videoKey)}: Master's answer not used:"

    /** S2b: [videoKey]'s site's name as main says it (Facebook, TikTok; S2c: X; S2d: Instagram). */
    private fun nameOf(videoKey: String?): String =
        SiteWays.site(siteOf(videoKey) ?: FACEBOOK_SITE_ID)?.name ?: "Facebook"

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
     * yes/no only: never a link or an ID. S2c: X's rows (main's rows' key is X's) compare the item
     * (owner's input 2, decision 11): `the same item: yes` only when the same video's rows also name
     * the one item main's rows name (its file's path on X's video host, [XLadder.itemOf]: one video
     * of a post with several). S2d: Instagram's rows (main's rows' key is Instagram's) compare the
     * item too (decision 19 (iv): the link's item of a carousel): `the same item: yes` only when the
     * same video's rows share a file with main's rows (a row's file path, or its audio's, among
     * main's: Master's rows add the 360p files main drops, so not every row is one of main's), and
     * the line ends `; tallest: Master's H, main's H` (each side's tallest row, `unknown` when its
     * rows state no height).
     */
    fun trialSegment(rows: List<MediaCandidate>, mains: List<MediaCandidate>, video: (String?) -> String?): String {
        val mainVideo = mains.map { video(it.videoId) }.distinct().singleOrNull()
        val sameVideo = mainVideo != null && rows.isNotEmpty() && rows.all { video(it.videoId) == mainVideo }
        // S2c: X's rows name one item of the post (one video of several), as main's must.
        val site = mains.firstOrNull()?.videoId?.substringBefore(':', missingDelimiterValue = "")
        val x = site == X_SITE_ID
        val instagram = site == INSTAGRAM_SITE_ID
        val item = x || instagram
        val mainItem = mains.map { XLadder.itemOf(it.mediaUrl) }.distinct().singleOrNull()
        val same = sameVideo && when {
            x -> mainItem != null && rows.all { XLadder.itemOf(it.mediaUrl) == mainItem }
            // S2d: one item's files: at least one of Master's rows is one of main's files.
            instagram -> pathsOf(mains).let { paths -> rows.any { row -> pathsOf(listOf(row)).any(paths::contains) } }
            else -> true
        }
        val masterMillis = rows.mapNotNull { row -> row.durationMillis?.takeIf { it > 0 } }
        val mainMillis = mains.mapNotNull { row -> row.durationMillis?.takeIf { it > 0 } }
        val duration = when {
            masterMillis.isEmpty() || mainMillis.isEmpty() -> "unknown"
            masterMillis.all { master -> mainMillis.all { abs(master - it) <= DURATION_SLACK_MILLIS } } -> "yes"
            else -> "no"
        }
        val tallest = if (instagram) "; tallest: Master's ${tallestOf(rows)}, main's ${tallestOf(mains)}" else ""
        return "${TRIAL_SEGMENT}Master's rows ${rows.size}, main's ${mains.size}; " +
            "the same ${if (item) "item" else "video"}: ${if (same) "yes" else "no"}; duration within 1 s: $duration" +
            tallest
    }

    /** S2d: the file paths of [rows] (each row's file and its audio's; no host, no query). */
    private fun pathsOf(rows: List<MediaCandidate>): Set<String> = rows.flatMap { row ->
        listOfNotNull(row.mediaUrl, row.audioCompanion?.mediaUrl)
    }.mapNotNull { url -> runCatching { URI(url).path }.getOrNull()?.takeIf { it.isNotEmpty() } }.toSet()

    /** S2d: the tallest height [rows] state, `unknown` when none states one. */
    private fun tallestOf(rows: List<MediaCandidate>): String =
        rows.mapNotNull { row -> row.height?.takeIf { it > 0 } }.maxOrNull()?.toString() ?: "unknown"

    /**
     * S1a (9): the Facebook trial's line of rows Master found (Facebook's line with the trial's
     * segment, [trialSegment]), for the browser's notice; S2b: TikTok's too (any ladder site's
     * line; S2c: X's). Null for every other answer (no other build has one).
     */
    fun trialNotice(outcome: SiteAdapterOutcome): String? =
        (outcome as? SiteAdapterOutcome.Detected)?.details?.firstOrNull { detail ->
            SITES.any { detail.startsWith("master: $it") } && TRIAL_SEGMENT in detail
        }
}
