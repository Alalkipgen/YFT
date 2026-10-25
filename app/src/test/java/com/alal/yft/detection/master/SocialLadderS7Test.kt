package com.alal.yft.detection.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteAdapterFlags
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractorRegistry
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
import com.alal.yft.extractor.master.social.SocialCooldown
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Social plan S1a (5), S7, in the app's lookup, in a social-ladder build: main's 429 ends its
 * lookup (main's answer, nothing asked by Master) and cools Facebook's host for 30 minutes, so the
 * next lookups' F1 does not ask while F2 still reads main's pages; after 30 minutes F1 asks again.
 * Flag off (every CI build): the same lookups answer as today, R8 asks as before and nothing cools.
 * Main's real Facebook adapter over synthetic pages, a fake clock and the test's own cooldown;
 * each case passes the switch explicitly. No live calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SocialLadderS7Test {
    @Test
    fun mainsRateLimitEndsItsLookupAndCoolsF1ForThirtyMinutes() = runTest {
        val fixture = Fixture(socialLadder = true)

        fixture.mainAnswer = ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429)
        val limited = fixture.lookup() as SiteAdapterOutcome.Failed
        assertEquals(SiteExtractionFailure.RATE_LIMITED, limited.reason)
        assertTrue(fixture.master.asked.isEmpty())
        assertEquals(0, fixture.captures.get())
        assertTrue(fixture.cooldown.cooling(HOST))

        // Ten minutes later main's page answers (under keys main no longer knows): F1 cools, F2's rows.
        fixture.now += 10 * MINUTE
        fixture.mainAnswer = ExtractorHttpResult.Success(200, movedKeyPage(VIDEO), watchPage, "text/html")
        val cooling = fixture.lookup() as SiteAdapterOutcome.Detected
        assertEquals(setOf(hd(VIDEO), sd(VIDEO)), cooling.candidates.map { it.mediaUrl }.toSet())
        cooling.candidates.forEach { assertEquals("facebook:$VIDEO", it.videoId) }
        assertTrue(fixture.master.asked.isEmpty())

        // Thirty-one minutes after main's 429: F1 asks again (once).
        fixture.now += 21 * MINUTE
        val again = fixture.lookup() as SiteAdapterOutcome.Detected
        assertEquals(setOf(hd(VIDEO), sd(VIDEO)), again.candidates.map { it.mediaUrl }.toSet())
        assertEquals(listOf(embedUrl(watchPage)), fixture.master.asked)
        assertEquals(3, fixture.main.asked.size)
        assertEquals(0, fixture.captures.get())
    }

    @Test
    fun flagOffTheSameLookupsAnswerAsTodayAndNothingCools() = runTest {
        // Today's engine (no ladder), and the ladder present with the switch off: main's 429 is
        // main's answer; the next lookup asks R8's endpoint as before, in today's words.
        val today = Fixture(socialLadder = false, ladder = false)
        val off = Fixture(socialLadder = false, ladder = true)
        for (fixture in listOf(today, off)) {
            fixture.mainAnswer = ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429)
        }
        val todaysLimited = today.lookup() as SiteAdapterOutcome.Failed
        val offsLimited = off.lookup() as SiteAdapterOutcome.Failed
        assertEquals(todaysLimited, offsLimited)
        assertEquals(SiteExtractionFailure.RATE_LIMITED, offsLimited.reason)

        for (fixture in listOf(today, off)) {
            fixture.now += 10 * MINUTE
            fixture.mainAnswer = ExtractorHttpResult.Success(200, movedKeyPage(VIDEO), watchPage, "text/html")
        }
        val todays = today.lookup() as SiteAdapterOutcome.Failed
        val offs = off.lookup() as SiteAdapterOutcome.Failed
        assertEquals(todays, offs)
        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, offs.reason)
        assertEquals("Play the video on this page, then tap Try again.", offs.message)
        for (fixture in listOf(today, off)) {
            assertEquals(listOf(embedUrl(watchPage)), fixture.master.asked)
            assertEquals(1, fixture.captures.get())
            assertFalse(fixture.cooldown.cooling(HOST))
        }
        // R8's line only: no ladder line.
        assertTrue(offs.details.none { it.startsWith("master: facebook ladder") || it.startsWith("master: facebook F") })
    }

    /** Synthetic answers by address (main's page or the plugin player); anything else a 404. */
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

    private class Fixture(socialLadder: Boolean, ladder: Boolean = socialLadder) {
        var now = NOW
        var mainAnswer: ExtractorHttpResult = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)

        /** The test's own cooldown (the app's stays untouched), on the fixture's clock. */
        val cooldown = SocialCooldown({ now })
        val main = Answers { url -> mainAnswer.takeIf { url == watchPage } }

        // The plugin player has no data.
        val master = Answers { url ->
            if (url == embedUrl(watchPage)) ExtractorHttpResult.Success(200, PAGE_BODY, url, "text/html") else null
        }
        val captures = AtomicInteger()
        val capture = WebViewPlaybackCapture(true)
        val sites = SiteAdapterCoordinator(
            SiteExtractorRegistry(
                listOf(FacebookExtractor(SocialLadder.pages(LookupRecorder(main), socialLadder))),
                SiteAdapterFlags { true },
            ),
            MergeSupport { true },
        )
        private val scope = capture.session.navigate(WATCH)!!
        val bridge = AndroidBrowserMasterFallback(
            capture,
            sites,
            MasterFallbackEngine(
                MasterMediaValidator { found, _ -> ValidationResult.Valid(found.copy(contentLengthBytes = 4096)) },
                PlaybackCaptureProvider {
                    captures.incrementAndGet()
                    CaptureResult.Unavailable
                },
                MasterPolicy(enabled = true),
                contracts = SiteContracts(master),
                social = SocialLadder.stage(master, sites::identity, social = ladder, cooldown = cooldown),
            ),
            MergeSupport { true },
            requestFactory = { reason, at, expected -> MasterRequest(WATCH, scope.generation, at, reason, expected) },
            socialLadder = socialLadder,
            socialCooldown = cooldown,
        )

        suspend fun lookup(): SiteAdapterOutcome = withContext(bridge.lookupContext(WATCH)) {
            val primary = sites.inspect(WATCH, BrowserRequestContext(WATCH, "Browser", "c_user=fixture"), NOW)
            bridge.recover(WATCH, primary, NOW)
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val MINUTE = 60_000L
        const val HOST = "www.facebook.com"
        const val VIDEO = "1000000000000003"
        const val OTHER_VIDEO = "1000000000000009"
        const val WATCH = "https://www.facebook.com/watch/?v=$VIDEO"
        const val PAGE_BODY = "<html><head><title>Facebook</title></head><body>no data</body></html>"

        /** The address main's adapter reads for the video. */
        val watchPage: String = checkNotNull(FacebookExtractor(Answers { null }).identify(WATCH)).canonicalPageUrl

        fun embedUrl(page: String) = "https://www.facebook.com/plugins/video.php?href=" +
            URLEncoder.encode(page, StandardCharsets.UTF_8).replace("+", "%20")

        fun hd(video: String) = "https://video.example-cdn.test/v/$video-hd.mp4?oe=F2A52380&oh=fixture"

        fun sd(video: String) = "https://video.example-cdn.test/v/$video-sd.mp4?oe=F2A52380&oh=fixture"

        /** The video's files under renamed keys (main reads it RESPONSE_CHANGED), a related video's beside them. */
        fun movedKeyPage(video: String): String =
            "<html><head><title>Facebook</title></head><body>" +
                "<script type=\"application/json\" data-sjs>{\"require\":[[\"VideoPlayerRelay\",null,null,[{" +
                "\"video\":{\"video_id\":\"$video\",\"browser_native_hd_url_v2\":\"${hd(video)}\"," +
                "\"browser_native_sd_url_v2\":\"${sd(video)}\"},\"related\":[{\"video_id\":\"$OTHER_VIDEO\"," +
                "\"browser_native_hd_url_v2\":\"${hd(OTHER_VIDEO)}\"}]}]]]}</script></body></html>"
    }
}
