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
import com.alal.yft.extractor.master.layers.KEY_TABLE_CHANGED
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.social.FacebookLadder
import com.alal.yft.extractor.master.social.InstagramLadder
import com.alal.yft.extractor.master.social.MainPageRecorder
import com.alal.yft.extractor.master.social.MainPages
import com.alal.yft.extractor.master.social.OkHttpRowReader
import com.alal.yft.extractor.master.social.RungsOff
import com.alal.yft.extractor.master.social.SiteWays
import com.alal.yft.extractor.master.social.SocialCooldown
import com.alal.yft.extractor.master.social.SocialRowCheck
import com.alal.yft.extractor.master.social.SocialStage
import com.alal.yft.extractor.master.social.SocialStages
import com.alal.yft.extractor.master.social.TikTokLadder
import com.alal.yft.extractor.master.social.TrialCompare
import com.alal.yft.extractor.master.social.VkClient
import com.alal.yft.extractor.master.social.VkClientValues
import com.alal.yft.extractor.master.social.VkLadder
import com.alal.yft.extractor.master.social.XLadder
import com.alal.yft.extractor.master.solver.AtomicTextFile
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import com.alal.yft.extractor.sites.vk.VkExtractor
import com.alal.yft.extractor.sites.x.XExtractor
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.OkHttpClient

/**
 * Social plan S1a (`MASTER_KEY_SOCIAL_PLAN.md` §7.3): the build's social-ladder switch and its app
 * wiring, Facebook first (the pilot); S2b: TikTok's too; S2c: X's; S2d: Instagram's; VK-Master wave 2 (agent D): VK's
 * ([SITES]). On only in a debug build made
 * with `-Pyft.socialLadder=true` and `-Pyft.masterCapture=true`; release, preview and every other
 * build: off, and each piece here is exactly today's.
 */
internal object SocialLadder {
    val enabled: Boolean get() = BuildConfig.SOCIAL_LADDER

    /**
     * S1a (9): the Facebook trial (the D7 APK `social-trial`, S1a's phone proof): on only in a debug
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

    /** VK-Master wave 2 (agent D): VK's site ID (main's VK adapter; its video key's prefix). */
    const val VK_SITE_ID = "vk"

    /**
     * S2b: the sites with a ladder (each lookup's own record and pages, its trial): Facebook,
     * TikTok; S2c: X; S2d: Instagram; VK-Master wave 2: VK.
     */
    val SITES: Set<String> = setOf(FACEBOOK_SITE_ID, TIKTOK_SITE_ID, X_SITE_ID, INSTAGRAM_SITE_ID, VK_SITE_ID)

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
     * every TikTok link form too; S2c: every X link form; S2d: every Instagram link form; VK-Master
     * wave 2: every VK link form ([SITES]).
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
     * tab-fetch probe, off by default, when the lookup's context has one) and VK-Master wave 2's
     * VK's ([VkLadder]: V1, main's answers judged, no new ask; V2, the web client's own token and
     * `video.get` with [vkValues]' values, the one VK refused healed; V3, the tab's playback), each
     * claiming only its own site's lookup with its own record
     * ([lookupContext]; [SocialStages]: the first that claims it), over Master's own client
     * [http]; [identify]: main's adapters. Their recipes come from the same source as the
     * engine's ([MasterRecipeConfig]); S1a (5): their stops cool in [cooldown] (the app's, by
     * default). [vkValues] is asked only here, in a social-ladder build (main's values only by
     * default; the app's: [vkValues]). Every other build: null, the engine as before.
     */
    fun stage(
        http: ExtractorHttpClient,
        identify: (String) -> SitePageIdentity?,
        social: Boolean = enabled,
        cooldown: SocialCooldown = SocialLadder.cooldown,
        vkValues: () -> VkClientValues = { VkClientValues.bundled() },
    ): SocialStage? = if (social) {
        SocialStages(
            listOf(
                FacebookLadder(http, identify, MasterRecipeConfig::contracts, cooldown),
                TikTokLadder(http, identify, MasterRecipeConfig::contracts, cooldown),
                XLadder(http, identify, MasterRecipeConfig::contracts, cooldown),
                InstagramLadder(http, MasterRecipeConfig::contracts, cooldown),
                VkLadder(http, MasterRecipeConfig::contracts, vkValues(), cooldown),
            ),
        )
    } else {
        null
    }

    /** VK-Master wave 2: the file V2's healed values are kept in, under the app's files directory. */
    const val VK_HEALTH_FILE = "master/site-health.json"

    @Volatile
    private var vkShared: VkClientValues? = null

    /**
     * VK-Master wave 2 (agent D, plan §8 items 3 and 5): the process's VK values for V2 — main's
     * (as the trial's VK value break sends them: [mutate], [VkTrialBreak]) and the one VK refused
     * healed from vkvideo.ru's public page and script ([VkClientValues.healing]), read over
     * [client] (Master's own: the Backup finder's gate) with the cookies vkvideo.ru's own
     * redirects set sent back, and kept, after an OK answer whose rows passed V, in the app's
     * [VK_HEALTH_FILE] ([filesDir]; memory only without one). One per process, made on first use:
     * every tab shares its day's memory and its file.
     */
    fun vkValues(
        client: OkHttpClient,
        filesDir: (() -> File)?,
        mutate: ((VkClient.Values) -> VkClient.Values)? = null,
    ): VkClientValues = vkShared ?: synchronized(this) {
        vkShared ?: VkClientValues.healing(
            http = OkHttpExtractorClient(
                client = client,
                policy = OkHttpExtractorClient.Policy(sendResponseCookies = true),
            ),
            file = filesDir?.let { dir -> AtomicTextFile { File(dir(), VK_HEALTH_FILE) } },
            mutate = mutate,
        ).also { vkShared = it }
    }

    /**
     * S1a (6), S6: the engine's file check. In a social-ladder build a ladder's lookup (its own
     * record and [MainPages], [lookupContext]: Facebook's, TikTok's, X's, Instagram's) checks every
     * row Master offers with V and the init check ([SocialRowCheck]: the file's first bytes, read
     * by [reader]'s reader under route B's rules, on the lookup's own site's media hosts) instead
     * of [validator]; every other lookup gets [validator] itself. Every other build: [validator]
     * itself, and [reader] is never called.
     */
    fun validator(
        validator: MasterMediaValidator,
        social: Boolean = enabled,
        reader: () -> MediaStartReader,
    ): MasterMediaValidator = if (social) SocialRowCheck(validator, reader()) else validator

    /**
     * Task 9: [validator]'s S6 as the app's lookups and the site census wire it: one check over one
     * [OkHttpRowReader] on Master's [client] for every lookup and site. Each read asks only the
     * media hosts of the site the check chose for its lookup (`RowReadSite`: the lookup's
     * [MainPages] site), never a site fixed when it was built; a file off them is refused before
     * any ask (counted refused, never sent). Every other build: [validator] itself, no reader.
     */
    fun validator(
        validator: MasterMediaValidator,
        client: OkHttpClient,
        social: Boolean = enabled,
    ): MasterMediaValidator = validator(validator, social) { OkHttpRowReader(client) }

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
     * (vi)). VK-Master wave 2: a VK lookup's final answer — the video removed (VK's deleted icon)
     * in main's words ([VkExtractor.NOT_FOUND_MESSAGE]), VK's region limit in the app's. Null for
     * every other reason and site (as before).
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
        VK_SITE_ID -> when (reason) {
            SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE -> VkExtractor.NOT_FOUND_MESSAGE
            SiteExtractionFailure.GEO_RESTRICTED -> VK_REGION_MESSAGE
            else -> null
        }
        else -> null
    }

    /** VK-Master wave 2: VK's region limit (API error 13300), in the app's words for the reason. */
    const val VK_REGION_MESSAGE = "VK does not allow this video in your region."

    /**
     * Social plan task 14 (observation e): the words of a social lookup Master answered "needs
     * playback" (the tab's own playback, I3 or F3, found nothing). [PLAYBACK]: "Play the video on
     * this page, then tap Try again."; [SIGN_IN]: the sign-in words ([signInMessage], or main's own
     * when its reason is `LOGIN_REQUIRED`); [MAINS]: main's words. Only the words change: the reason
     * stays main's (Try again where it is not final), as before.
     */
    enum class Words(val label: String) { PLAYBACK("playback"), SIGN_IN("sign-in"), MAINS("main's") }

    /** Task 14: the [words] picked and [why] (words only: never a link or an ID). */
    data class WordsPick(val words: Words, val why: String)

    /**
     * Task 14 (observation e): which words a social lookup's "needs playback" answer shows, from
     * whether the tab showed a video ([tabVideo], `MasterBrowserSession.videoShown`) and the judge's
     * classes in [videoKey]'s site's lines of [details] (Master's own; another site's are not
     * read): (1) the tab showed a video → [Words.PLAYBACK]; (2) the site's answers name the post's
     * video (rows read and none kept, `DIRECT` or `PARTIAL`; Instagram's `VIDEO_WITHOUT_FILES` or
     * its rung's "the post's video in the answer: yes"; Facebook's plugin player, TikTok's "names
     * the video", X's item playlist) → [Words.PLAYBACK], the page can play it; (3) a sign-in or age
     * wall (`LOGIN_WALL`, `AGE_LIMITED`) → [Words.SIGN_IN]; (4) else, no video in the tab →
     * [Words.MAINS] (X's widget 404 or tombstone, Instagram's "no post", nothing read). Social plan
     * S4: before (1), the tab's own playback off in the signed config ([RungsOff.captureOff]) →
     * [Words.MAINS] (no capture was asked, so playing the video again gives nothing).
     */
    fun playbackWords(videoKey: String?, details: List<String>, tabVideo: Boolean): WordsPick {
        // Task 15 (F4): the rung's proof left no row (debug): main's words (the trial's), as the
        // proof gives none again on Try again; before the tab's video, which the proof never reads.
        if (InstagramLadder.PROOF_STOP in details) return WordsPick(Words.MAINS, PROOF_WHY)
        // S4: the signed config turned the tab's own playback off: playing it again gives nothing.
        if (RungsOff.captureOff(details)) return WordsPick(Words.MAINS, CAPTURE_OFF_WHY)
        if (tabVideo) return WordsPick(Words.PLAYBACK, "the tab shows a video")
        val lines = details.filter { it.startsWith(line(videoKey)) }
        lines.firstNotNullOfOrNull { line ->
            VIDEO_CLASSES.find(line)?.value ?: VIDEO_MARKS.firstOrNull(line::contains)
        }?.let { return WordsPick(Words.PLAYBACK, "the site's answer names the video: $it") }
        lines.firstNotNullOfOrNull { WALL_CLASSES.find(it)?.value }
            ?.let { return WordsPick(Words.SIGN_IN, "$it; no video in the tab") }
        return WordsPick(Words.MAINS, "no video in the tab")
    }

    /** Task 14: the Details line of [pick], before Master's lines: `master: <site> words: <words> (<why>)`. */
    fun wordsLine(videoKey: String?, pick: WordsPick): String = "${line(videoKey)} words: ${pick.words.label} (${pick.why})"

    /**
     * Task 14: the sign-in words for [videoKey]'s site where main's reason is not `LOGIN_REQUIRED`
     * (X's age wall: main's reason is `NO_MEDIA_FOUND`) — the app's own words for that reason.
     */
    fun signInMessage(videoKey: String?): String = "Sign in to ${nameOf(videoKey)} on this page first, then try again."

    /** S4: the words line's why when the signed config turned the tab's own playback off. */
    private const val CAPTURE_OFF_WHY = "the tab's playback off (config)"

    /** Task 15 (F4): the words line's why when the rung's proof left no row. */
    private const val PROOF_WHY = "the rung's proof: I2's rows set aside, the rung gave none"

    /** Task 14: the judge's classes of a read video (rows read, none kept; a video without files). */
    private val VIDEO_CLASSES = Regex("""\b(DIRECT|PARTIAL|VIDEO_WITHOUT_FILES)\b""")

    /** Task 14: the judges' evidence that names the post's video (each site's "needs playback" with a video). */
    private val VIDEO_MARKS = listOf(
        "the post's video in the answer: yes",
        "the plugin player",
        "names the video",
        "the item's playlist only",
    )

    /** Task 14: the judge's classes of a sign-in or age wall. */
    private val WALL_CLASSES = Regex("""\b(LOGIN_WALL|AGE_LIMITED)\b""")

    /**
     * Social plan task 15 (F3b): whether Master's answer for [videoKey] is a ladder's photos-only
     * answer — TikTok's, X's or Instagram's `NO_MEDIA_FOUND` with its site's `PHOTO_ONLY` line
     * ending in `FINAL` (the post has no video: [finalMessage]'s words) — so the browser offers no
     * Try again. Facebook's and every other answer: false (Try again as before).
     */
    fun photosOnly(reason: SiteExtractionFailure, videoKey: String?, details: List<String>): Boolean =
        reason == SiteExtractionFailure.NO_MEDIA_FOUND &&
            siteOf(videoKey) in setOf(TIKTOK_SITE_ID, X_SITE_ID, INSTAGRAM_SITE_ID) &&
            finalLine(videoKey, details, "PHOTO_ONLY")

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

    /** S2b: [videoKey]'s site's name as main says it (Facebook, TikTok; S2c: X; S2d: Instagram; VK). */
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
     * rows state no height). Task 6b: the comparison is [TrialCompare]'s (the site census's ladder
     * part reads the same), the words unchanged. Task 16 (F6): Facebook's line ends `; heights M vs
     * N` (Master's rows' heights, then main's: each height once, tallest first, `720,360`; `unknown`
     * when a side's rows state none), so a retest checks each of Master's heights against main's;
     * Master's rows state their files' own sizes ([SocialRowCheck]). Numbers only.
     */
    fun trialSegment(rows: List<MediaCandidate>, mains: List<MediaCandidate>, video: (String?) -> String?): String =
        TRIAL_SEGMENT + TrialCompare.compare(rows, mains, video).trialText()

    /**
     * S1a (9): the Facebook trial's line of rows Master found (Facebook's line with the trial's
     * segment, [trialSegment]), for the browser's notice; S2b: TikTok's too (any ladder site's
     * line; S2c: X's). Task 13 (observation f): with Instagram's tab-fetch rung's lines in the
     * answer ([InstagramLadder.PROBE]), its last too (its answer: its status, bytes, video, wall and
     * rows; else why it was not asked), on its own line after the trial's, so a lookup with rows
     * shows it as well. Task 15 (F1): every lookup that ends with Master's rows — without the
     * trial's comparison (main failed: no rows to compare), the site's ladder line (`master: <site>
     * ladder after <reason>; …`) — and (F4) each of Instagram's probe lines ([probeLines]: the
     * proof's line too). S4: before the probe's, a contract's line whose rows came by shape — its
     * key table read none ([keyTableLines]: a renamed key, R9's data fix; the drill's config). Null
     * for an answer without a ladder site's line, a probe line or such a line (every other build
     * and site).
     */
    fun trialNotice(outcome: SiteAdapterOutcome): String? {
        val details = (outcome as? SiteAdapterOutcome.Detected)?.details ?: return null
        // Task 15 (F1): without the trial's comparison (main failed), the site's ladder line.
        val site = details.firstOrNull { detail ->
            SITES.any { detail.startsWith("master: $it") } && TRIAL_SEGMENT in detail
        } ?: details.firstOrNull { detail -> SITES.any { detail.startsWith("master: $it ladder") } }
        return (listOfNotNull(site) + keyTableLines(details) + probeLines(details))
            .takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    /**
     * S4: a ladder site's contract lines that read its rows by shape (`master: <site> contract: N
     * files by shape (key table changed)`), each once, at most [MAX_KEY_TABLE_LINES]: the only
     * sign on a lookup with rows that the key table read none (rows, counts and words otherwise
     * as with it), so the R9 drill's renamed key shows on the phone.
     */
    private fun keyTableLines(details: List<String>): List<String> = details.filter { detail ->
        SITES.any { detail.startsWith("master: $it ") } && detail.endsWith(" $KEY_TABLE_CHANGED")
    }.distinct().take(MAX_KEY_TABLE_LINES)

    /** S4: one lookup's contracts by shape (a site's own, and a second read's). */
    private const val MAX_KEY_TABLE_LINES = 2

    /**
     * Task 15 (F4): the browser's notice of a failed lookup in which the rung's proof set I2's rows
     * aside (debug, the census screen's box): [outcome]'s words, then Instagram's probe lines (the
     * proof's, the rung's answer or why it was not asked, the proof's stop). Null for every other
     * failure (its words alone, as before).
     */
    fun proofNotice(outcome: SiteAdapterOutcome.Failed): String? {
        if (InstagramLadder.PROOF !in outcome.details) return null
        return (listOf(outcome.message) + probeLines(outcome.details)).joinToString("\n")
    }

    /**
     * Task 13 (observation f), task 15 (F1, F4): Instagram's probe lines in [details], in order — the
     * rung's proof's (when it set I2's rows aside), the rung's (its answer: status, bytes, the post's
     * video, wall and rows; else why it was not asked) and the proof's stop — the rung's own label
     * and a colon only, never another of Instagram's lines; at most [MAX_PROBE_LINES].
     */
    private fun probeLines(details: List<String>): List<String> =
        details.filter { it.startsWith(InstagramLadder.PROBE + ":") }.distinct().takeLast(MAX_PROBE_LINES)

    /** The proof's line, the rung's and the proof's stop: one lookup's probe lines. */
    private const val MAX_PROBE_LINES = 3
}
