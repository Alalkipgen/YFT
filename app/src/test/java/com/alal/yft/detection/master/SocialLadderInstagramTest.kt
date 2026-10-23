package com.alal.yft.detection.master

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.social.MainPages
import com.alal.yft.extractor.master.social.SocialCooldown
import com.alal.yft.extractor.master.social.SocialStages
import com.alal.yft.extractor.sites.instagram.InstagramExtractor
import com.alal.yft.extractor.sites.x.XExtractor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Social plan S2d part 1: the app's Instagram wiring. Flag off: no stage. Flag on: Master's social
 * rung has Instagram's ladder too, claiming only an Instagram lookup with its record (Instagram's
 * labels, a MainPages naming Instagram); Instagram's Details lines and trial words; a photo post in
 * main's words only on Instagram's final line (no REMOVED answer: decision 19 (vi)); the trial
 * compares the item (one of main's files among Master's rows, which add the 360p files main drops)
 * and says each side's tallest row; the probe's own switch, off by default, only in a social-ladder
 * build. Each case passes the switch explicitly, so it holds whatever the build's flags are.
 * Synthetic rows and made-up IDs; no live calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SocialLadderInstagramTest {
    @Test
    fun theStageClaimsAnInstagramLookupOnlyWithItsRecordAndAsksNothing() = runTest {
        val asks = NoAsks()
        val sites = sites(asks)
        assertNull(SocialLadder.stage(asks, sites::identity, social = false, cooldown = SocialCooldown()))
        val stage = checkNotNull(SocialLadder.stage(asks, sites::identity, social = true, cooldown = SocialCooldown()))
        assertTrue(stage.toString(), stage is SocialStages)
        assertTrue(stage.toString(), "InstagramLadder" in stage.toString())

        val identity = sites.identity(REEL)
        assertEquals("instagram", identity?.siteId)
        assertEquals(KEY, sites.videoKey(REEL))
        assertTrue(SocialLadder.claims(KEY))
        withContext(SocialLadder.lookupContext(KEY)) {
            assertTrue(stage.claims(request(REEL, identity)))
            // Instagram's lookup: Instagram's labels and a MainPages naming Instagram.
            assertEquals("instagram", coroutineContext[MainPages]?.site)
            assertNotNull(coroutineContext[LookupRecord])
        }
        // No record (flag off, or no lookup context): not claimed, the engine as before.
        assertFalse(stage.claims(request(REEL, identity)))
        assertEquals(0, asks.asked.get()) // claiming asks nothing
    }

    @Test
    fun instagramsLinesAndTrialWords() {
        assertEquals("master: instagram", SocialLadder.line(KEY))
        assertEquals(
            "Instagram trial: main's rows were set aside and Master found none. Tap Try again.",
            SocialLadder.trialMessage(KEY),
        )
        assertEquals("master: instagram trial: main's", SocialLadder.trialDetail(KEY))
        assertEquals("master: instagram: Master's answer not used:", SocialLadder.notUsed(KEY))
        assertEquals("instagram", SocialLadder.siteOf(KEY))
        assertTrue(SocialLadder.INSTAGRAM_SITE_ID in SocialLadder.SITES)
        // The others' as before.
        assertEquals(SocialLadder.TRIAL_MESSAGE, SocialLadder.trialMessage("facebook:1000000000000001"))
        assertEquals("master: x", SocialLadder.line(X_KEY))
    }

    @Test
    fun aPhotoPostEndsInMainsWordsOnlyOnInstagramsFinalLine() {
        val final = "master: instagram I2: PHOTO_ONLY (GraphQL: photos only, no video); next: FINAL"
        val perWay = "master: instagram I2 main's GraphQL: PHOTO_ONLY (photos only, no video)"
        val ladder = "master: instagram ladder after NO_MEDIA_FOUND; main's answers read, no new ask"
        assertEquals(
            InstagramExtractor.PHOTOS_MESSAGE, // main's own for a photo post
            SocialLadder.finalMessage(SiteExtractionFailure.NO_MEDIA_FOUND, KEY, listOf(ladder, perWay, final)),
        )
        assertEquals("This post has photos only, no video.", InstagramExtractor.PHOTOS_MESSAGE)
        // Task 15 (F3b): that answer is final (no Try again); without the final line it is not.
        assertTrue(SocialLadder.photosOnly(SiteExtractionFailure.NO_MEDIA_FOUND, KEY, listOf(ladder, perWay, final)))
        assertFalse(SocialLadder.photosOnly(SiteExtractionFailure.NO_MEDIA_FOUND, KEY, listOf(ladder, perWay)))
        assertFalse(SocialLadder.photosOnly(SiteExtractionFailure.LOGIN_REQUIRED, KEY, listOf(ladder, final)))
        assertFalse(SocialLadder.photosOnly(SiteExtractionFailure.NO_MEDIA_FOUND, X_KEY, listOf(final)))
        // Not Instagram's final line: null (the engine's own words, as before).
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.NO_MEDIA_FOUND, KEY))
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.NO_MEDIA_FOUND, KEY, listOf(ladder, perWay)))
        assertNull(
            SocialLadder.finalMessage(
                SiteExtractionFailure.NO_MEDIA_FOUND,
                KEY,
                listOf("master: x X1 main's answer: PHOTO_ONLY (photos only, no video); next: FINAL"),
            ),
        )
        // Instagram's ladder has no REMOVED answer and no private one: never said for it.
        for (reason in listOf(
            SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
            SiteExtractionFailure.LOGIN_REQUIRED,
            SiteExtractionFailure.RATE_LIMITED,
            SiteExtractionFailure.BOT_CHECK,
        )) {
            assertNull(reason.name, SocialLadder.finalMessage(reason, KEY, listOf(ladder, final)))
        }
        // Instagram's line never gives another site's words.
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.NO_MEDIA_FOUND, X_KEY, listOf(final)))
        assertNull(SocialLadder.finalMessage(SiteExtractionFailure.NO_MEDIA_FOUND, "tiktok:11111", listOf(final)))
    }

    @Test
    fun instagramsTrialComparesTheItemAndSaysTheTallest() {
        val key: (String?) -> String? = { it }
        val mains = listOf(row(FILE_1080, KEY, 9_000, 1_080), row(FILE_720, KEY, 9_000, 720))
        // Master's rows: main's two files (their own query) and the 360p file main drops.
        val masters = listOf(
            row("$FILE_1080&_nc_ohc=other", KEY, 9_200, 1_080),
            row(FILE_720, KEY, 9_200, 720),
            row(FILE_360, KEY, 9_200, 360),
        )
        assertEquals(
            "; trial: Master's rows 3, main's 2; the same item: yes; duration within 1 s: yes; " +
                "tallest: Master's 1080, main's 1080",
            SocialLadder.trialSegment(masters, mains, key),
        )
        // Only the 360p file: none of main's files, so not the same item.
        assertEquals(
            "; trial: Master's rows 1, main's 2; the same item: no; duration within 1 s: yes; " +
                "tallest: Master's 360, main's 1080",
            SocialLadder.trialSegment(listOf(row(FILE_360, KEY, 9_000, 360)), mains, key),
        )
        // Another item of the carousel (its own files), another post's key, no rows: not the same.
        assertTrue(SocialLadder.trialSegment(listOf(row(OTHER_ITEM, KEY, 12_000, 1_080)), mains, key).contains("the same item: no"))
        assertTrue(SocialLadder.trialSegment(listOf(row(FILE_720, "instagram:C9fixtOTHER", 9_000, 720)), mains, key).contains("the same item: no"))
        assertTrue(SocialLadder.trialSegment(emptyList(), mains, key).contains("the same item: no; duration within 1 s: unknown; tallest: Master's unknown"))
        // A shared audio file names the item too; no heights: unknown on both sides.
        val audio = "https://scontent.cdninstagram.com/o1/v/t2/f2/m78/audio_fixture.mp4?efg=1"
        assertEquals(
            "; trial: Master's rows 1, main's 1; the same item: yes; duration within 1 s: unknown; " +
                "tallest: Master's unknown, main's unknown",
            SocialLadder.trialSegment(
                listOf(row(OTHER_ITEM, KEY, null, null, audio)),
                listOf(row(FILE_720, KEY, null, null, "$audio&x=2")),
                key,
            ),
        )
        // X's and Facebook's lines as before: no tallest.
        val x = SocialLadder.trialSegment(
            listOf(row(X_FILE, X_KEY, 9_000, 720)),
            listOf(row(X_FILE, X_KEY, 9_000, 720)),
            key,
        )
        assertEquals("; trial: Master's rows 1, main's 1; the same item: yes; duration within 1 s: yes", x)
        val facebook = "facebook:1000000000000001"
        assertEquals(
            "; trial: Master's rows 1, main's 1; the same video: yes; duration within 1 s: yes",
            SocialLadder.trialSegment(
                listOf(row("https://video.fixture.fbcdn.net/v/a.mp4", facebook, 9_000, 720)),
                listOf(row("https://video.fixture.fbcdn.net/v/b.mp4", facebook, 9_000, 1_080)),
                key,
            ),
        )
    }

    @Test
    fun theTrialsNoticeReadsInstagramsLineToo() {
        val segment = "; trial: Master's rows 3, main's 2; the same item: yes; duration within 1 s: yes; " +
            "tallest: Master's 1080, main's 1080"
        val ladder = "master: instagram ladder after RESPONSE_CHANGED; main's answers read, no new ask"
        val outcome = SiteAdapterOutcome.Detected("instagram", listOf(row(FILE_720, KEY, 9_000, 720)), listOf(ladder + segment))
        assertEquals(ladder + segment, SocialLadder.trialNotice(outcome))
        val none = SiteAdapterOutcome.Detected("instagram", listOf(row(FILE_720, KEY, 9_000, 720)), listOf(ladder))
        assertNull(SocialLadder.trialNotice(none))

        // Task 13 (observation f): the tab-fetch rung's line too, on its own line, with rows.
        val rung = "master: instagram probe: HTTP 200, 2048 bytes; the post's video in the answer: yes; " +
            "a wall on the page: no; rows 1 (DIRECT)"
        val both = SiteAdapterOutcome.Detected("instagram", listOf(row(FILE_720, KEY, 9_000, 720)), listOf(ladder + segment, rung))
        assertEquals(ladder + segment + "\n" + rung, SocialLadder.trialNotice(both))
        val alone = SiteAdapterOutcome.Detected("instagram", listOf(row(FILE_720, KEY, 9_000, 720)), listOf(ladder, rung))
        assertEquals(rung, SocialLadder.trialNotice(alone))
        // The proof's lines: the rung's answer, its last.
        val proof = SiteAdapterOutcome.Detected(
            "instagram", listOf(row(FILE_720, KEY, 9_000, 720)),
            listOf(ladder, "master: instagram probe: I2's rows set aside (the rung's proof)", rung),
        )
        assertEquals(rung, SocialLadder.trialNotice(proof))
        // Only the rung's own line (its label and a colon), never another of Instagram's.
        val other = SiteAdapterOutcome.Detected(
            "instagram", listOf(row(FILE_720, KEY, 9_000, 720)), listOf(ladder, "master: instagram I2: DIRECT (GraphQL: rows 1)"),
        )
        assertNull(SocialLadder.trialNotice(other))
    }

    @Test
    fun theRungsSwitchAndItsProofAreOffByDefaultAndOnlyInASocialLadderBuild() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(InstagramProbeSwitch.PREFERENCES, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        // Every other build: no rung at all, whatever the preferences say.
        assertNull(InstagramProbeSwitch.reader(context, social = false))
        assertNull(InstagramProbeSwitch.proofReader(context, social = false))
        val reader = checkNotNull(InstagramProbeSwitch.reader(context, social = true))
        val proof = checkNotNull(InstagramProbeSwitch.proofReader(context, social = true))
        assertFalse(reader()) // off by default
        assertFalse(proof())
        // Task 13: part 1's probe setting is not carried over (a new key).
        preferences.edit().putBoolean("tab_fetch_probe", true).commit()
        assertFalse(reader())
        assertEquals("tab_fetch_rung", InstagramProbeSwitch.KEY)
        InstagramProbeSwitch.set(context, true)
        assertTrue(reader())
        assertFalse(proof()) // its own switch
        assertTrue(InstagramProbeSwitch.on(context, social = true))
        assertFalse(InstagramProbeSwitch.on(context, social = false))
        InstagramProbeSwitch.setProof(context, true)
        assertTrue(proof())
        assertTrue(InstagramProbeSwitch.proof(context, social = true))
        assertFalse(InstagramProbeSwitch.proof(context, social = false))
        InstagramProbeSwitch.set(context, false)
        InstagramProbeSwitch.setProof(context, false)
        assertFalse(reader())
        assertFalse(proof())
    }

    private fun row(
        url: String,
        key: String,
        millis: Long?,
        height: Int?,
        audio: String? = null,
    ) = MediaCandidate(
        REEL, url, setOf(CandidateSource.MANIFEST), MediaKind.DIRECT,
        mimeType = "video/mp4", videoId = key, durationMillis = millis, height = height,
        audioCompanion = audio?.let { CompanionAudio(it, "audio/mp4", emptyList(), BrowserRequestContext(REEL, null, null)) },
    )

    private fun request(page: String, identity: SitePageIdentity?) = MasterRequest(
        pageUrl = page,
        generation = 1,
        nowEpochMs = 1,
        primaryFailure = SiteExtractionFailure.RESPONSE_CHANGED,
        identity = identity,
    )

    private fun sites(http: ExtractorHttpClient) = SiteAdapterCoordinator(
        SiteExtractorRegistry(listOf(InstagramExtractor(http), XExtractor(http)), SiteAdapterFlags { true }),
        MergeSupport { true },
    )

    /** A client the stage never asks while it only claims. */
    private class NoAsks : ExtractorHttpClient {
        val asked = AtomicInteger()
        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            asked.incrementAndGet()
            return ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        }

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult {
            asked.incrementAndGet()
            return ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)
        }
    }

    private companion object {
        const val REEL = "https://www.instagram.com/reel/C9fixtREEL1/"
        const val KEY = "instagram:C9fixtREEL1"
        const val FILE_1080 = "https://scontent.cdninstagram.com/o1/v/t16/f2/m69/fixture_1080.mp4?efg=1"
        const val FILE_720 = "https://scontent.cdninstagram.com/o1/v/t16/f2/m69/fixture_720.mp4?efg=1"
        const val FILE_360 = "https://scontent.cdninstagram.com/o1/v/t16/f2/m69/fixture_vp360.mp4?efg=1"
        const val OTHER_ITEM = "https://scontent.cdninstagram.com/o1/v/t16/f2/m69/other_item.mp4?efg=1"
        const val X_KEY = "x:1000000000000000001"
        const val X_FILE = "https://video.twimg.com/amplify_video/1000000000000000002/vid/avc1/1280x720/a.mp4?tag=16"
    }
}
