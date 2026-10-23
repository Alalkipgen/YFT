package com.alal.yft.detection.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.PlaybackCaptureProvider
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.android.WebViewPlaybackCapture
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.social.SocialCooldown
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import com.alal.yft.feature.browser.BrowserViewModel
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.testing.MainDispatcherRule
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Social plan S1a (9): the Facebook trial APK (D7 `social-trial`, `-Pyft.socialTrial` with
 * `-Pyft.socialLadder`, debug only) for S1a's phone proof. A Facebook page main found (a watch
 * link, a reel, a short link) is answered as main's `RESPONSE_CHANGED` after main asked, its rows
 * set aside for that lookup only, so Master's ladder answers; Facebook's line then compares
 * Master's rows with main's (the same video, the duration within 1 s). S2b: a TikTok page main
 * found is set aside the same way (TikTok's words and line). Main's failures (final ones too),
 * YouTube's pages and every build without the trial: as before. Main's real Facebook adapter over
 * synthetic pages with made-up IDs; each case passes the switches explicitly. No live calls.
 *
 * S1a (10), step 6's first run: the plugin player's answer names no length (as here now), so
 * Master's rows state their files' own (S6's read of each file's start, made in code here) and
 * the line compares them; the ladder's rows stay while the same video stays open (the page's
 * address changed or it started again during the lookup), and when Master's answer is not used
 * the Details say which step.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SocialTrialTest {
    @get:Rule val dispatcher = MainDispatcherRule()

    @Test
    fun withoutTheTrialEveryAnswerStaysAsItIs() {
        val found = mainsRows()
        // The ladder alone, the trial without the ladder, Master off, no backup: main's answer itself.
        for (fixture in listOf(
            Fixture(WATCH, socialTrial = false),
            Fixture(WATCH, socialLadder = false),
            Fixture(WATCH, enabledBuild = false),
        )) {
            assertSame(found, fixture.bridge.trialPrimary(WATCH, found))
        }
        assertSame(found, BrowserMasterFallback.None.trialPrimary(WATCH, found))
        assertFalse(SocialLadder.trial) // no unit-test build passes -Pyft.socialTrial
    }

    @Test
    fun theTrialAnswersMainsFoundFacebookPageAsMainsFailureForEveryLinkForm() = runTest {
        for (page in listOf(WATCH, REEL, SHORT)) {
            val fixture = Fixture(page)
            val primary = fixture.inspect()
            val found = primary as SiteAdapterOutcome.Detected

            val trial = fixture.bridge.trialPrimary(page, primary) as SiteAdapterOutcome.Failed

            assertEquals(page, "facebook", trial.adapterId)
            assertEquals(page, SiteExtractionFailure.RESPONSE_CHANGED, trial.reason)
            assertEquals(page, SocialLadder.TRIAL_MESSAGE, trial.message)
            assertEquals(page, "Facebook trial: main's rows were set aside and Master found none. Tap Try again.", trial.message)
            assertFalse(page, trial.allowsGenericFallback)
            assertTrue(page, trial.canRetry)
            assertEquals(page, listOf("master: facebook trial: main's ${found.candidates.size} rows set aside"), trial.details)
            assertEquals(page, 2, found.candidates.size)
            assertBare(page, trial.details)
        }
    }

    @Test
    fun aPagePostsDesktopFormFindsNoneWithItsStepInDetails() = runTest {
        // Task 7 (link 9's desktop form, decision 18): main reads the post's page and finds its one
        // video; the trial sets main's rows aside. Main resolved no video link (the post's page names
        // the post, not the video), so the plugin player (F1) is not asked and main's pages (F2) give
        // no row bound to the post. The tab's playback (F3): on a feed site the capture's focus check
        // needs the video in focus to be the post's ID, which a post's video never is, so it starts
        // the page again at the same address (WebViewPlaybackCapture; made up here as that reload).
        // Master's answer is then not used: the trial's "Master found none", the step in Details.
        // No behavior changed here; this pins it.
        val fixture = Fixture(STORY)
        val found = fixture.inspect() as SiteAdapterOutcome.Detected
        assertEquals(2, found.candidates.size)
        assertTrue(fixture.main.asked.toString(), fixture.main.asked.all { it.startsWith(POSTS) })
        fixture.duringCapture = { fixture.capture.session.navigate(STORY) }

        val failed = fixture.lookup() as SiteAdapterOutcome.Failed

        val lines = failed.details.toString()
        assertEquals(lines, SiteExtractionFailure.RESPONSE_CHANGED, failed.reason)
        assertEquals(lines, SocialLadder.TRIAL_MESSAGE, failed.message)
        assertEquals(lines, "Facebook trial: main's rows were set aside and Master found none. Tap Try again.", failed.message)
        assertFalse(lines, failed.allowsGenericFallback)
        assertTrue(lines, failed.canRetry)
        assertEquals(lines, "master: facebook trial: main's 2 rows set aside", failed.details.first())
        assertEquals(
            lines,
            "master: facebook: Master's answer not used: the page changed during the lookup: the same address again",
            failed.details[1],
        )
        assertTrue(lines, "master: facebook ladder: a link main resolves first, no video in main's answer; not asked" in failed.details)
        // The plugin player is never asked; the tab's playback once.
        assertTrue(fixture.master.asked.toString(), fixture.master.asked.isEmpty())
        assertEquals(1, fixture.captures.get())
        assertBare("a Page post", failed.details)
        listOf(POST_ID, PAGE_NAME).forEach { word -> assertFalse(word, failed.details.any { word in it }) }

        // Without the trial (yft-d7-normal's way): main's rows of the post's one video, as they are.
        val plain = Fixture(STORY, socialTrial = false)
        val primary = plain.inspect()
        assertSame(primary, plain.bridge.trialPrimary(STORY, primary))
        assertEquals(2, (primary as SiteAdapterOutcome.Detected).candidates.size)
    }

    @Test
    fun mainsFailuresAndOtherSitesStayAsTheyAre() {
        val fixture = Fixture(WATCH, youtubeRouteB = true)
        val removed = SiteAdapterOutcome.Failed(
            "facebook", SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
            "This Facebook post is private or no longer available.", false,
        )
        val changed = SiteAdapterOutcome.Failed("facebook", SiteExtractionFailure.RESPONSE_CHANGED, "Main's words", true)
        for (link in listOf(WATCH, REEL, SHORT)) {
            assertSame(link, removed, fixture.bridge.trialPrimary(link, removed))
            assertSame(link, changed, fixture.bridge.trialPrimary(link, changed))
        }
        assertSame(SiteAdapterOutcome.NotHandled, fixture.bridge.trialPrimary(WATCH, SiteAdapterOutcome.NotHandled))
        // YouTube's page main found: as is (the trial claims no other site). S2b: TikTok's page
        // main found is set aside too, with TikTok's message and line.
        val youtubeFound = SiteAdapterOutcome.Detected("youtube", listOf(row(YOUTUBE_PAGE, "youtube:Yft0Fixture")))
        val tiktokFound = SiteAdapterOutcome.Detected("tiktok", listOf(row(TIKTOK_PAGE, "tiktok:11111")))
        assertSame(youtubeFound, fixture.bridge.trialPrimary(YOUTUBE_PAGE, youtubeFound))
        val tiktokTrial = SiteAdapterOutcome.Failed(
            "tiktok", SiteExtractionFailure.RESPONSE_CHANGED,
            "TikTok trial: main's rows were set aside and Master found none. Tap Try again.", false,
            listOf("master: tiktok trial: main's 1 rows set aside"),
        )
        assertEquals(tiktokTrial, fixture.bridge.trialPrimary(TIKTOK_PAGE, tiktokFound))
        // The route-B trial with the Facebook trial: YouTube's answer exactly as routeb's.
        val both = Fixture(WATCH, youtubeRouteB = true, routeBTrial = true)
        val routeB = both.bridge.trialPrimary(YOUTUBE_PAGE, youtubeFound) as SiteAdapterOutcome.Failed
        assertEquals(
            SiteAdapterOutcome.Failed(
                "youtube", SiteExtractionFailure.RESPONSE_CHANGED, AndroidBrowserMasterFallback.ROUTE_B_TRIAL_MESSAGE, false,
                listOf("master: route B trial: main's 1 rows set aside"),
            ),
            routeB,
        )
        assertEquals(tiktokTrial, both.bridge.trialPrimary(TIKTOK_PAGE, tiktokFound))
    }

    @Test
    fun aRemovedPostStaysMainsFinalAnswer() = runTest {
        val fixture = Fixture(WATCH, mainPage = errorPage(BURMESE))

        val primary = fixture.inspect() as SiteAdapterOutcome.Failed
        val answer = fixture.recover(primary)

        assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, primary.reason)
        assertSame(primary, answer)
        assertTrue(fixture.master.asked.isEmpty())
    }

    @Test
    fun theLadderAnswersEveryLinkFormAndItsLineComparesTheRowsWithMains() = runTest {
        for (page in listOf(WATCH, REEL, SHORT)) {
            val fixture = Fixture(page)

            val found = fixture.lookup() as SiteAdapterOutcome.Detected

            assertEquals(page, "facebook", found.adapterId)
            assertEquals(page, listOf(hd(VIDEO), sd(VIDEO)), found.candidates.map { it.mediaUrl })
            found.candidates.forEach { assertEquals(page, "facebook:$VIDEO", it.videoId) }
            assertEquals(page, listOf(embedUrl(if (page == REEL) REEL else WATCH)), fixture.master.asked)
            assertEquals(page, 0, fixture.captures.get())
            val line = SocialLadder.trialNotice(found)
            assertEquals(
                page,
                "master: facebook ladder after RESPONSE_CHANGED; replayed 0; new 1; " +
                    "trial: Master's rows 2, main's 2; the same video: yes; duration within 1 s: yes$NO_HEIGHTS",
                line,
            )
            assertEquals(page, line, found.details.first { it.startsWith("master: facebook") })
            assertBare(page, found.details)
        }
    }

    @Test
    fun theLineSaysWhenTheDurationsDifferOrAreNotKnown() = runTest {
        // S1a (10): Master's length is its files' (their starts'), as the plugin player names none.
        val later = Fixture(WATCH, fileMillis = MAIN_MILLIS + 1_001).lookup() as SiteAdapterOutcome.Detected
        val within = Fixture(WATCH, fileMillis = MAIN_MILLIS - 1_000).lookup() as SiteAdapterOutcome.Detected
        val masterNone = Fixture(WATCH, fileMillis = null).lookup() as SiteAdapterOutcome.Detected
        val mainNone = Fixture(WATCH, mainPage = mainPage(VIDEO, null)).lookup() as SiteAdapterOutcome.Detected

        assertTrue(SocialLadder.trialNotice(later)!!.endsWith("; the same video: yes; duration within 1 s: no$NO_HEIGHTS"))
        assertTrue(SocialLadder.trialNotice(within)!!.endsWith("; the same video: yes; duration within 1 s: yes$NO_HEIGHTS"))
        assertTrue(SocialLadder.trialNotice(masterNone)!!.endsWith("; the same video: yes; duration within 1 s: unknown$NO_HEIGHTS"))
        assertTrue(SocialLadder.trialNotice(mainNone)!!.endsWith("; the same video: yes; duration within 1 s: unknown$NO_HEIGHTS"))
    }

    @Test
    fun mastersRowsStateTheirFilesOwnLengthsAsThePluginPlayerNamesNone() = runTest {
        // Step 6's first run: every trial line said "unknown" — the plugin player's answer has no
        // length (nor has this one), and Master's rows stated none.
        assertFalse(embed(VIDEO).contains("duration"))
        val fixture = Fixture(WATCH)

        val found = fixture.lookup() as SiteAdapterOutcome.Detected

        assertEquals(listOf(hd(VIDEO), sd(VIDEO)), found.candidates.map { it.mediaUrl })
        found.candidates.forEach { assertEquals(MASTER_MILLIS, it.durationMillis) }
        // Each file's start read once (S6's own read, nothing more asked for the length).
        assertEquals(listOf(hd(VIDEO), sd(VIDEO)), fixture.reads.toList())
        assertTrue(SocialLadder.trialNotice(found)!!.endsWith("duration within 1 s: yes$NO_HEIGHTS"))
        // The ladder alone without the trial: the same rows and lengths, no comparison — the
        // ladder's own line (task 15, F1).
        val ladder = Fixture(WATCH, socialTrial = false, mainPage = PAGE_BODY).lookup() as SiteAdapterOutcome.Detected
        ladder.candidates.forEach { assertEquals(MASTER_MILLIS, it.durationMillis) }
        assertLadderLine(SocialLadder.trialNotice(ladder))
        // Flag off: the engine's own check, no file read, no length (as today).
        val off = Fixture(WATCH, socialLadder = false, socialTrial = false, mainPage = PAGE_BODY)
        val today = off.lookup() as SiteAdapterOutcome.Detected
        today.candidates.forEach { assertNull(it.durationMillis) }
        assertTrue(off.reads.isEmpty())
    }

    @Test
    fun mastersRowsStateTheirFilesOwnPictureSizesAndTheLineGivesEachSidesHeights() = runTest {
        // Task 16 (F6): link 1's rows showed "Quality unknown" — the plugin player's answer names no
        // size (as it names no length): each row takes its file's own, from the start S6 reads.
        val fixture = Fixture(WATCH)
        fixture.picture = { url -> if (url == hd(VIDEO)) 1280 to 720 else 640 to 360 }

        val found = fixture.lookup() as SiteAdapterOutcome.Detected

        assertEquals(listOf(hd(VIDEO), sd(VIDEO)), found.candidates.map { it.mediaUrl })
        assertEquals(listOf(1280 to 720, 640 to 360), found.candidates.map { it.width to it.height })
        found.candidates.forEach { assertEquals(MASTER_MILLIS, it.durationMillis) }
        // Each file's start read once, as before: nothing more read or asked for the size.
        assertEquals(listOf(hd(VIDEO), sd(VIDEO)), fixture.reads.toList())
        assertEquals(listOf(embedUrl(WATCH)), fixture.master.asked)
        // Main's rows here state no height (its page has no manifest): Master's heights, then main's.
        assertTrue(SocialLadder.trialNotice(found)!!.endsWith("duration within 1 s: yes; heights 720,360 vs unknown"))
        assertBare("sized", found.details)
        // Files whose starts state no size: unknown, as before.
        val unsized = Fixture(WATCH).lookup() as SiteAdapterOutcome.Detected
        unsized.candidates.forEach { assertNull(it.width); assertNull(it.height) }
        assertTrue(SocialLadder.trialNotice(unsized)!!.endsWith(NO_HEIGHTS))
        // Flag off: the engine's own check, no file read, no size (as today).
        val off = Fixture(WATCH, socialLadder = false, socialTrial = false, mainPage = PAGE_BODY)
        off.picture = { 1280 to 720 }
        val today = off.lookup() as SiteAdapterOutcome.Detected
        today.candidates.forEach { assertNull(it.height) }
        assertTrue(off.reads.isEmpty())
    }

    @Test
    fun theLaddersRowsStayWhileTheSameVideoStaysOpenDuringTheLookup() = runTest {
        // The page starts again at its address, or its address changes to the same video's, while
        // Master asks (step 6's first lookups of a watch link, VPN, most likely): the rows stay.
        for (change in listOf(WATCH, VIDEO_PAGE)) {
            val fixture = Fixture(WATCH)
            fixture.duringAsk = { fixture.capture.session.navigate(change) }

            val found = fixture.lookup() as SiteAdapterOutcome.Detected

            assertEquals(change, listOf(hd(VIDEO), sd(VIDEO)), found.candidates.map { it.mediaUrl })
            assertEquals(change, change, fixture.capture.session.currentScope()?.pageUrl)
            assertTrue(change, SocialLadder.trialNotice(found)!!.endsWith("the same video: yes; duration within 1 s: yes$NO_HEIGHTS"))
        }
    }

    @Test
    fun anotherPageDuringTheLookupDropsTheRowsAndTheDetailsSayWhichStep() = runTest {
        val fixture = Fixture(WATCH)
        fixture.duringAsk = { fixture.capture.session.navigate(OTHER_WATCH) }

        val failed = fixture.lookup() as SiteAdapterOutcome.Failed

        // The trial's own answer (reason, message, retry), with the step and Master's own lines.
        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, failed.reason)
        assertEquals(SocialLadder.TRIAL_MESSAGE, failed.message)
        assertFalse(failed.allowsGenericFallback)
        assertTrue(failed.canRetry)
        assertEquals(
            listOf(
                "master: facebook trial: main's 2 rows set aside",
                "master: facebook: Master's answer not used: the page changed during the lookup: another page",
            ),
            failed.details.take(2),
        )
        assertTrue(failed.details.toString(), failed.details.drop(2).any { it.startsWith("master: facebook ladder after") })
        assertBare("another page", failed.details)
    }

    @Test
    fun theTabsOwnPlaybackStillNeedsTheSamePageAndTheDetailsSaySo() = runTest {
        // No rung but the tab's playback could answer; its page starts again while it is read (as
        // its focus check does when the tab shows another video): no answer from it, as before.
        val fixture = Fixture(WATCH, mainPage = PAGE_BODY, plugin = embed(VIDEO, files = false))
        fixture.duringCapture = { fixture.capture.session.navigate(WATCH) }

        val failed = withContext(fixture.bridge.lookupContext(WATCH)) {
            fixture.inspect()
            fixture.bridge.recover(WATCH, fixture.bridge.trialPrimary(WATCH, mainsRows()), NOW)
        } as SiteAdapterOutcome.Failed

        assertEquals(1, fixture.captures.get())
        assertEquals(SocialLadder.TRIAL_MESSAGE, failed.message)
        assertEquals(
            "master: facebook: Master's answer not used: the page changed during the lookup: the same address again",
            failed.details[1],
        )
        assertTrue(failed.details.toString(), "master: one browser-assisted capture requested" in failed.details)
        assertBare("playback", failed.details)
    }

    @Test
    fun flagOffAPageChangeDuringTheLookupAnswersAsToday() = runTest {
        // Main fails (its page names no file), R8 asks the plugin player: rows on a settled page.
        val settled = Fixture(WATCH, socialLadder = false, socialTrial = false, mainPage = PAGE_BODY)
        assertTrue(settled.lookup() is SiteAdapterOutcome.Detected)
        // The same video's other address meanwhile: main's own answer itself, no line (today's).
        for (change in listOf(VIDEO_PAGE, OTHER_WATCH)) {
            val fixture = Fixture(WATCH, socialLadder = false, socialTrial = false, mainPage = PAGE_BODY)
            fixture.duringAsk = { fixture.capture.session.navigate(change) }
            val primary = fixture.inspect()

            assertTrue(change, primary is SiteAdapterOutcome.Failed)
            assertSame(change, primary, fixture.bridge.recover(WATCH, primary, NOW))
            assertEquals(change, listOf(embedUrl(WATCH)), fixture.master.asked)
        }
    }

    @Test
    fun theLineSaysWhenMainsRowsNameAnotherVideo() = runTest {
        val fixture = Fixture(WATCH)
        // Main's rows (made up here) for another video than the one Master's rows name.
        val others = SiteAdapterOutcome.Detected(
            "facebook",
            listOf(row(WATCH, "facebook:$OTHER_VIDEO", MAIN_MILLIS), row(WATCH, "facebook:$OTHER_VIDEO", MAIN_MILLIS)),
        )

        val found = withContext(fixture.bridge.lookupContext(WATCH)) {
            fixture.inspect() // main asks, as in every lookup
            fixture.bridge.recover(WATCH, fixture.bridge.trialPrimary(WATCH, others), NOW)
        } as SiteAdapterOutcome.Detected

        assertEquals(
            "master: facebook ladder after RESPONSE_CHANGED; replayed 0; new 1; " +
                "trial: Master's rows 2, main's 2; the same video: no; duration within 1 s: yes$NO_HEIGHTS",
            SocialLadder.trialNotice(found),
        )
    }

    @Test
    fun theComparisonReadsTheVideosAndTheDurations() {
        fun video(id: String?) = id
        val mains = listOf(row(WATCH, "facebook:$VIDEO", 60_000), row(WATCH, "facebook:$VIDEO", 60_400))
        fun segment(rows: List<MediaCandidate>, main: List<MediaCandidate> = mains) =
            SocialLadder.trialSegment(rows, main, ::video)

        assertEquals(
            "; trial: Master's rows 1, main's 2; the same video: yes; duration within 1 s: yes$NO_HEIGHTS",
            segment(listOf(row(WATCH, "facebook:$VIDEO", 61_000))),
        )
        // Within 1 s of every one of main's; one of main's 1.4 s away: no.
        assertTrue(segment(listOf(row(WATCH, "facebook:$VIDEO", 59_300))).endsWith("duration within 1 s: no$NO_HEIGHTS"))
        assertTrue(segment(listOf(row(WATCH, "facebook:$VIDEO", 61_401))).endsWith("duration within 1 s: no$NO_HEIGHTS"))
        assertTrue(segment(listOf(row(WATCH, "facebook:$VIDEO", null))).endsWith("duration within 1 s: unknown$NO_HEIGHTS"))
        assertTrue(
            segment(listOf(row(WATCH, "facebook:$VIDEO", 60_000)), listOf(row(WATCH, "facebook:$VIDEO", null)))
                .endsWith("duration within 1 s: unknown$NO_HEIGHTS"),
        )
        // Task 16 (F6): each side's heights, each once, tallest first — Master's, then main's;
        // a side whose rows state none: unknown. Numbers only.
        val sized = listOf(row(WATCH, "facebook:$VIDEO", 60_000, 720), row(WATCH, "facebook:$VIDEO", 60_400, 360))
        assertTrue(
            segment(listOf(row(WATCH, "facebook:$VIDEO", 60_000, 360), row(WATCH, "facebook:$VIDEO", 60_000, 720)), sized)
                .endsWith("duration within 1 s: yes; heights 720,360 vs 720,360"),
        )
        assertTrue(segment(listOf(row(WATCH, "facebook:$VIDEO", 60_000, 720)), sized).endsWith("; heights 720 vs 720,360"))
        assertTrue(segment(listOf(row(WATCH, "facebook:$VIDEO", 60_000)), sized).endsWith("; heights unknown vs 720,360"))
        // Another video, a row without a video key, main's rows of two videos, no row: not the same.
        assertTrue(segment(listOf(row(WATCH, "facebook:$OTHER_VIDEO", 60_000))).contains("the same video: no"))
        assertTrue(segment(listOf(row(WATCH, null, 60_000))).contains("the same video: no"))
        assertTrue(
            segment(listOf(row(WATCH, "facebook:$VIDEO", 60_000)), mains + row(WATCH, "facebook:$OTHER_VIDEO", 60_000))
                .contains("the same video: no"),
        )
        assertTrue(segment(emptyList()).startsWith("; trial: Master's rows 0, main's 2; the same video: no"))
        // A link main resolves first: main's rows carry the link's key, read as the video it names.
        assertTrue(
            SocialLadder.trialSegment(
                listOf(row(WATCH, "facebook:$VIDEO", 60_000)),
                listOf(row(SHORT, "facebook:$SHORT_CODE", 60_000)),
            ) { id -> if (id == "facebook:$SHORT_CODE") "facebook:$VIDEO" else id }
                .contains("the same video: yes"),
        )
        assertBare("segment", listOf(segment(listOf(row(WATCH, "facebook:$OTHER_VIDEO", 60_000)))))
    }

    @Test
    fun noSetAsideRowsReachALaterLookup() = runTest {
        val fixture = Fixture(WATCH)
        val first = fixture.lookup() as SiteAdapterOutcome.Detected
        assertTrue(SocialLadder.trialNotice(first)!!.contains("; trial: Master's rows 2, main's 2;"))

        // The next lookup: main's own failure, nothing set aside — Master's rows, no comparison
        // (the banner: the ladder's own line; task 15, F1).
        fixture.mainPage = PAGE_BODY
        val second = fixture.lookup() as SiteAdapterOutcome.Detected
        assertEquals(listOf(hd(VIDEO), sd(VIDEO)), second.candidates.map { it.mediaUrl })
        assertLadderLine(SocialLadder.trialNotice(second))
        assertTrue(second.details.none { SocialLadder.TRIAL_SEGMENT in it })

        // A lookup that set rows aside and never reached Master (cancelled): its rows are not
        // another answer's — neither a recover of another answer's nor the next lookup's.
        fixture.mainPage = mainPage(VIDEO, MAIN_MILLIS)
        val unfinished = withContext(fixture.bridge.lookupContext(WATCH)) {
            fixture.bridge.trialPrimary(WATCH, fixture.inspect())
        }
        assertTrue(unfinished is SiteAdapterOutcome.Failed)
        val mainsOwn = SiteAdapterOutcome.Failed("facebook", SiteExtractionFailure.RESPONSE_CHANGED, "Main's words", true)
        val other = fixture.recover(mainsOwn) as SiteAdapterOutcome.Detected
        assertLadderLine(SocialLadder.trialNotice(other))
        fixture.mainPage = PAGE_BODY
        val next = fixture.lookup() as SiteAdapterOutcome.Detected
        assertLadderLine(SocialLadder.trialNotice(next))
        // The unfinished lookup's own answer after a later lookup started: nothing left for it either.
        val late = fixture.recover(unfinished) as SiteAdapterOutcome.Detected
        assertLadderLine(SocialLadder.trialNotice(late))
    }

    @Test
    fun whenMasterFindsNoRowsTheTrialsLineStaysFirstInDetails() = runTest {
        // The plugin player names no file and main's page holds none to read again (main's rows
        // made up here): no rung gives rows, and the tab's playback gives none either.
        val fixture = Fixture(WATCH, mainPage = PAGE_BODY, plugin = embed(VIDEO, files = false))

        val failed = withContext(fixture.bridge.lookupContext(WATCH)) {
            fixture.inspect()
            fixture.bridge.recover(WATCH, fixture.bridge.trialPrimary(WATCH, mainsRows()), NOW)
        } as SiteAdapterOutcome.Failed

        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, failed.reason)
        assertEquals("master: facebook trial: main's 2 rows set aside", failed.details.first())
        assertFalse(failed.allowsGenericFallback)
        assertTrue(failed.canRetry)
        assertEquals(1, fixture.captures.get())
        assertBare("no rows", failed.details)
    }

    @Test
    fun theBrowserNamesFacebooksLineWithTheTrialsComparison() = runTest {
        for (trial in listOf(true, false)) {
            val fixture = Fixture(WATCH, socialTrial = trial)
            val store = DetectedMediaStore()
            val vm = BrowserViewModel(OkHttpClient(), fixture.sites, store, masterFallback = fixture.bridge)

            vm.onPageStarted(WATCH)
            vm.onPageFinished(WATCH, "Fixture")
            settle { fixture.main.asked.isNotEmpty() && !vm.uiState.value.pageLookupRunning }

            val notice = vm.uiState.value.siteNotice
            assertTrue("trial $trial", vm.openPageVideo())
            val rows = store.selection.value!!.candidates.map { it.mediaUrl }
            if (trial) {
                assertEquals(
                    "master: facebook ladder after RESPONSE_CHANGED; replayed 0; new 1; " +
                        "trial: Master's rows 2, main's 2; the same video: yes; duration within 1 s: yes$NO_HEIGHTS",
                    notice,
                )
                assertEquals(listOf(hd(VIDEO), sd(VIDEO)).toSet(), rows.toSet())
            } else {
                // The ladder alone: main's rows, no notice, Master never asked.
                assertNull(notice)
                assertEquals(listOf(mainHd(VIDEO), mainSd(VIDEO)).toSet(), rows.toSet())
                assertTrue(fixture.master.asked.isEmpty())
            }
        }
    }

    /** The engine runs on a background thread; the test thread runs the browser's coroutines. */
    private fun TestScope.settle(done: () -> Boolean) {
        repeat(500) {
            runCurrent()
            if (done()) return
            Thread.sleep(10)
        }
        fail("the lookup did not finish")
    }

    /**
     * Task 15 (F1): Master's rows without the trial's comparison (main failed, or no rows set
     * aside) — the banner reads Facebook's ladder line alone, never a comparison.
     */
    private fun assertLadderLine(notice: String?) {
        val line = notice.orEmpty()
        assertTrue(line, line.startsWith("master: facebook ladder after "))
        assertFalse(line, SocialLadder.TRIAL_SEGMENT in line)
        assertFalse(line, "\n" in line)
    }

    /** S8: no link, host, ID or page words in a line. */
    private fun assertBare(case: String, lines: List<String>) {
        lines.forEach { line ->
            listOf("://", VIDEO, OTHER_VIDEO, SHORT_CODE, BURMESE, "example-cdn", "fbcdn", "fixture").forEach { word ->
                assertFalse("$case: $line", word in line)
            }
        }
    }

    /** Synthetic answers by address (main's pages or the plugin player); anything else a 404. */
    private class Answers(private val answer: (String) -> ExtractorHttpResult?) : ExtractorHttpClient {
        val asked = CopyOnWriteArrayList<String>()

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            asked += url
            return answer(url) ?: ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)
        }

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult = ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
    }

    /** A stand-in adapter for another site's pages (YouTube's, TikTok's); never asked here. */
    private class OtherSite(override val id: String, private val prefix: String) : SiteExtractor {
        override val displayName = "Fixture"

        override fun identify(pageUrl: String): SitePageIdentity? =
            if (pageUrl.startsWith(prefix)) SitePageIdentity(id, pageUrl.substringAfterLast('/'), pageUrl) else null

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult =
            SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND)
    }

    /**
     * One Facebook lookup as the app wires it in the trial APK ([socialTrial] with [socialLadder]):
     * main's real adapter over [mainPage] (the short link resolves to the watch page), Master's
     * ladder over the plugin player [plugin] (null: a 404), S6's row check reading each file's start
     * (made in code: an MP4 start whose movie header states [fileMillis]; null: none), capture with
     * nothing to give. [duringAsk] runs when the plugin player is asked, [duringCapture] when the
     * tab's playback is read (a page change during the lookup).
     */
    private class Fixture(
        val page: String,
        socialLadder: Boolean = true,
        socialTrial: Boolean = true,
        enabledBuild: Boolean = true,
        youtubeRouteB: Boolean = false,
        routeBTrial: Boolean = false,
        var mainPage: String = mainPage(VIDEO, MAIN_MILLIS),
        plugin: String? = embed(VIDEO),
        var fileMillis: Long? = MASTER_MILLIS,
    ) {
        /** Task 16 (F6): each file's picture size its start states, by address (none: no track header). */
        var picture: (String) -> Pair<Int, Int>? = { null }
        var duringAsk: () -> Unit = {}
        var duringCapture: () -> Unit = {}
        val main = Answers { url ->
            when (url) {
                WATCH, REEL -> ok(mainPage, url)
                SHORT -> ok(mainPage, WATCH)
                // Task 7: a Page post's page (decision 18's address), its one video, no redirect.
                POSTS, "$POSTS/" -> ok(mainPage, url)
                else -> null
            }
        }
        val master = Answers { url ->
            plugin?.takeIf { url == embedUrl(WATCH) || url == embedUrl(REEL) }?.let {
                duringAsk()
                ok(it, url)
            }
        }

        /** The files' starts S6 read, by address (Facebook's media host only; another host: a 404). */
        val reads = CopyOnWriteArrayList<String>()
        private val files = MediaStartReader { url, _, maxBytes ->
            reads += url
            if (!url.startsWith("https://$MEDIA_HOST/")) {
                MediaStart.Failed(404)
            } else {
                val bytes = mp4Start(fileMillis, picture(url)).copyOf(maxBytes)
                MediaStart.Read(206, "video/mp4", "bytes 0-${bytes.size - 1}/$FILE_BYTES", bytes.size.toLong(), bytes)
            }
        }
        val captures = AtomicInteger()
        val capture = WebViewPlaybackCapture(enabledBuild)
        val sites = SiteAdapterCoordinator(
            SiteExtractorRegistry(
                listOf(
                    FacebookExtractor(SocialLadder.pages(LookupRecorder(main), socialLadder)),
                    OtherSite("youtube", YOUTUBE_PREFIX),
                    OtherSite("tiktok", TIKTOK_PREFIX),
                ),
                SiteAdapterFlags { true },
            ),
            MergeSupport { true },
        )
        private val scope = capture.session.navigate(page)!!
        private val cooldown = SocialCooldown()
        val bridge = AndroidBrowserMasterFallback(
            capture,
            sites,
            MasterFallbackEngine(
                // The app's wiring: S6's row check in a Facebook lookup with the ladder, else the engine's V.
                SocialLadder.validator(
                    MasterMediaValidator { found, _ -> ValidationResult.Valid(found.copy(contentLengthBytes = 4096)) },
                    socialLadder,
                ) { files },
                PlaybackCaptureProvider {
                    captures.incrementAndGet()
                    duringCapture()
                    CaptureResult.Unavailable
                },
                MasterPolicy(enabled = true, youtubeRouteB = youtubeRouteB),
                contracts = SiteContracts(master),
                social = SocialLadder.stage(master, sites::identity, social = socialLadder, cooldown = cooldown),
            ),
            MergeSupport { true },
            requestFactory = { reason, now, expected -> MasterRequest(page, scope.generation, now, reason, expected) },
            youtubeRouteB = youtubeRouteB,
            routeBTrial = routeBTrial,
            socialLadder = socialLadder,
            socialCooldown = cooldown,
            socialTrial = socialTrial,
        )

        /** Main's answer for the page, asked afresh (Try again's way: nothing remembered). */
        suspend fun inspect(): SiteAdapterOutcome =
            sites.inspect(page, BrowserRequestContext(page, "Browser", "c_user=fixture"), NOW, fresh = true)

        /** Master's answer to [primary] in a lookup of its own (main asks first, as in the app). */
        suspend fun recover(primary: SiteAdapterOutcome): SiteAdapterOutcome = withContext(bridge.lookupContext(page)) {
            inspect()
            bridge.recover(page, primary, NOW)
        }

        /** The app's lookup: main's answer, the trial's, then Master's. */
        suspend fun lookup(): SiteAdapterOutcome = withContext(bridge.lookupContext(page)) {
            bridge.recover(page, bridge.trialPrimary(page, inspect()), NOW)
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val VIDEO = "1000000000000009"
        const val OTHER_VIDEO = "1000000000000010"
        const val SHORT_CODE = "FixTrial9"
        const val WATCH = "https://www.facebook.com/watch/?v=$VIDEO"
        const val OTHER_WATCH = "https://www.facebook.com/watch/?v=$OTHER_VIDEO"

        /** The same video's page under its Page's address (`/<page>/videos/<id>/`, a made-up Page). */
        const val VIDEO_PAGE = "https://www.facebook.com/fixture/videos/$VIDEO/"
        const val REEL = "https://www.facebook.com/reel/$VIDEO"

        /**
         * Task 7: link 9's form — a Page post's desktop link as the app's browser opens it (Facebook's
         * mobile `story.php` naming the Page, a made-up one) and the address main reads for it
         * (decision 18: the Page's posts path).
         */
        const val POST_ID = "1000000000000077"
        const val PAGE_NAME = "YftTrialPage"
        const val STORY = "https://m.facebook.com/story.php?story_fbid=$POST_ID&id=$PAGE_NAME"
        const val POSTS = "https://www.facebook.com/$PAGE_NAME/posts/$POST_ID"
        const val SHORT = "https://fb.watch/$SHORT_CODE"
        const val YOUTUBE_PREFIX = "https://www.youtube.com/watch/"
        const val YOUTUBE_PAGE = "${YOUTUBE_PREFIX}Yft0Fixture"
        const val TIKTOK_PREFIX = "https://www.tiktok.com/@fixture/video/"
        const val TIKTOK_PAGE = "${TIKTOK_PREFIX}11111"
        const val MAIN_MILLIS = 61_500L
        const val MASTER_MILLIS = 61_800L
        const val PAGE_BODY = "<html><head><title>Facebook</title></head><body>no data</body></html>"
        const val BURMESE = "ဒီအကြောင်းအရာကို အခု ကြည့်လို့ မရပါ"

        fun ok(body: String, url: String) = ExtractorHttpResult.Success(200, body, url, "text/html")

        fun embedUrl(page: String) = "https://www.facebook.com/plugins/video.php?href=" +
            URLEncoder.encode(page, StandardCharsets.UTF_8).replace("+", "%20")

        /** Facebook's media host's form (a made-up name under it, as S6's tests): S6 reads only such files. */
        const val MEDIA_HOST = "video.fixture.fbcdn.net"
        const val FILE_BYTES = 9_437_184L

        fun hd(video: String) = "https://$MEDIA_HOST/v/$video-hd.mp4?oe=F2A52380&oh=fixture"

        fun sd(video: String) = "https://$MEDIA_HOST/v/$video-sd.mp4?oe=F2A52380&oh=fixture"

        fun mainHd(video: String) = "https://video.example-cdn.test/m/$video-hd.mp4?oe=F2A52380&oh=main"

        fun mainSd(video: String) = "https://video.example-cdn.test/m/$video-sd.mp4?oe=F2A52380&oh=main"

        fun row(page: String, videoId: String?, millis: Long? = null, height: Int? = null) = MediaCandidate(
            page, "https://video.example-cdn.test/r.mp4", setOf(CandidateSource.MANIFEST), MediaKind.DIRECT,
            mimeType = "video/mp4", durationMillis = millis, videoId = videoId, height = height,
        )

        /** Task 16 (F6): the line's end when neither side's rows state a height (these fixtures' pages and files). */
        const val NO_HEIGHTS = "; heights unknown vs unknown"

        /** Main's two rows for the watch page (as main's adapter gives them). */
        fun mainsRows() = SiteAdapterOutcome.Detected(
            "facebook",
            listOf(row(WATCH, "facebook:$VIDEO", MAIN_MILLIS), row(WATCH, "facebook:$VIDEO", MAIN_MILLIS)),
        )

        /** Main's page for [video]: one data script with the video's object, its two files and duration. */
        fun mainPage(video: String, millis: Long?): String {
            val duration = millis?.let { ",\"playable_duration_in_ms\":$it" }.orEmpty()
            val files = "\"browser_native_hd_url\":\"${mainHd(video)}\",\"browser_native_sd_url\":\"${mainSd(video)}\""
            return "<html><head><title>Facebook</title></head><body>" +
                "<script type=\"application/json\" data-sjs>{\"require\":[[\"VideoPlayerRelay\",null,null,[{" +
                "\"video\":{\"video_id\":\"$video\"$duration,\"videoDeliveryLegacyFields\":{$files}}}]]]}</script>" +
                "</body></html>"
        }

        /**
         * The plugin player's shape (FacebookContractTest's) for [video]: its two files ([files]).
         * S1a (10): no length, as the real answer (its video data names none).
         */
        fun embed(video: String, files: Boolean = true): String {
            val hd = if (files) "\"${hd(video).replace("/", "\\/")}\"" else "null"
            val sd = if (files) "\"${sd(video).replace("/", "\\/")}\"" else "null"
            return "<!DOCTYPE html><html><head><title>Facebook</title></head><body>" +
                "<script nonce=\"fixture\">requireLazy([\"TimeSliceImpl\",\"ServerJS\"],function(TimeSlice,ServerJS){" +
                "var s=(new ServerJS());s.handle({\"instances\":[[\"__inst_fixture\",[\"VideoConfig\"],[{" +
                "\"ad_client_token\":null,\"video_id\":\"$video\",\"videoData\":[{\"is_hls\":false," +
                "\"video_id\":\"$video\",\"hd_src\":$hd,\"sd_src\":$sd,\"dash_manifest\":null," +
                "\"original_height\":720,\"original_width\":1280,\"videoLicenseUriMap\":{}," +
                "\"graphApiVideoLicenseUri\":null,\"OzDrmHelper\":null}]}],1]]});});</script></body></html>"
        }

        /**
         * A progressive MP4 file's first 4096 bytes, made in code: the file type, then a `moov` longer
         * than the read whose movie header (timescale 1000) states [millis] (null: 0, no length).
         * Task 16 (F6): with a [picture] (width to height), a whole picture track first, whose header
         * (`tkhd`, version 0, the identity matrix) states it.
         */
        fun mp4Start(millis: Long?, picture: Pair<Int, Int>? = null): ByteArray {
            fun u32(value: Long) = ByteArray(4) { ((value ushr (24 - 8 * it)) and 0xFF).toByte() }
            fun box(type: String, payload: ByteArray) = u32(8L + payload.size) + type.toByteArray() + payload
            val ftyp = box("ftyp", "isom".toByteArray() + u32(512) + "isom".toByteArray() + "mp41".toByteArray())
            val mvhd = box("mvhd", u32(0) + u32(0) + u32(0) + u32(1_000) + u32(millis ?: 0) + ByteArray(80))
            val track = picture?.let { (width, height) ->
                val identity = listOf(0x10000L, 0L, 0L, 0L, 0x10000L, 0L, 0L, 0L, 0x40000000L).fold(ByteArray(0)) { bytes, n -> bytes + u32(n) }
                box("trak", box("tkhd", ByteArray(40) + identity + u32(width.toLong() shl 16) + u32(height.toLong() shl 16)))
            } ?: ByteArray(0)
            val moov = u32(65_536) + "moov".toByteArray() + mvhd + track + u32(60_000) + "trak".toByteArray()
            return (ftyp + moov).copyOf(4_096)
        }

        /** Facebook's unavailable page: the error route with its root's privacy flag ([title]: any language). */
        fun errorPage(title: String): String =
            "<html><head><title>Facebook</title></head><body>" +
                "<script type=\"application/json\" data-sjs>{\"require\":[[\"RouteInfo\",null,null,[{\"initialRouteInfo\":" +
                "{\"route\":{\"actorID\":\"0\",\"rootView\":{\"resource\":{\"__dr\":\"Root.react\"},\"props\":" +
                "{\"title\":\"$title\",\"privacy\":true}},\"tracePolicy\":\"comet.test\"," +
                "\"canonicalRouteName\":\"comet.fbweb.CometErrorRoute\"}}}]]]}</script></body></html>"
    }
}
