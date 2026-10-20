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
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Social plan S1a (4): F2, main's pages, in the app's lookup, in a social-ladder build: a share
 * link whose page main read (redirected to the reel) under keys main no longer knows gets F2's
 * rows for the reel's video after F1's one ask, nothing more asked; a post whose page is the
 * page's own route (private, in any language) ends the lookup in main's words, nothing asked or
 * captured. Flag off (every CI build): the same lookups answer as today (no record and no main's
 * pages, so the ladder claims nothing; R8's path). Main's real Facebook adapter over synthetic
 * pages; each case passes the switch explicitly. No live calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SocialLadderF2Test {
    @Test
    fun aShareLinkWhosePageMainNoLongerReadsGetsF2sRowsForTheReel() = runTest {
        val fixture = Fixture(page = SHARE, socialLadder = true)

        val detected = fixture.lookup() as SiteAdapterOutcome.Detected

        assertEquals(setOf(hd(REEL_VIDEO), sd(REEL_VIDEO)), detected.candidates.map { it.mediaUrl }.toSet())
        detected.candidates.forEach { assertEquals("facebook:$REEL_VIDEO", it.videoId) }
        // Main's one page; F1's one ask (no data); F2 asked nothing.
        assertEquals(listOf(SHARE_PAGE), fixture.main.asked)
        assertEquals(listOf(embedUrl(REEL)), fixture.master.asked)
        assertEquals(0, fixture.captures.get())
    }

    @Test
    fun aPostsPrivatePageEndsTheLookupInMainsWordsWithNothingAsked() = runTest {
        val fixture = Fixture(page = POST, socialLadder = true)

        val failed = fixture.lookup() as SiteAdapterOutcome.Failed

        assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, failed.reason)
        assertEquals("This Facebook post is private or no longer available.", failed.message)
        assertEquals(1, fixture.main.asked.size)
        assertTrue(fixture.master.asked.isEmpty())
        assertEquals(0, fixture.captures.get())
        assertTrue(failed.details.toString(), "master: facebook F2 main's pages: next: FINAL" in failed.details)
        assertTrue(failed.details.none { BURMESE in it || POST_ID in it })
    }

    @Test
    fun flagOffTheSameLookupsAnswerAsToday() = runTest {
        // Today's engine (no ladder), and the ladder present with the switch off: no record and no
        // main's pages, so it claims nothing and R8's path answers (a link main resolves first is
        // not asked about), in today's words.
        val cases = listOf(
            SHARE to SiteExtractionFailure.RESPONSE_CHANGED,
            POST to SiteExtractionFailure.NO_MEDIA_FOUND,
        )
        for ((page, reason) in cases) {
            val today = Fixture(page = page, socialLadder = false, ladder = false)
            val off = Fixture(page = page, socialLadder = false, ladder = true)

            val todays = today.lookup() as SiteAdapterOutcome.Failed
            val offs = off.lookup() as SiteAdapterOutcome.Failed

            assertEquals(todays, offs)
            assertEquals(reason, offs.reason)
            assertEquals("Play the video on this page, then tap Try again.", offs.message)
            assertTrue(off.master.asked.isEmpty())
            assertEquals(1, off.captures.get())
            assertTrue(offs.details.none { it.startsWith("master: facebook") })
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

    private class Fixture(
        val page: String,
        socialLadder: Boolean,
        ladder: Boolean = socialLadder,
    ) {
        // The share link redirects to the reel's page, its files under keys main no longer knows;
        // the post's page is Facebook's unavailable page in Burmese. The plugin player has no data.
        val main = Answers { url ->
            when (url) {
                SHARE_PAGE -> ExtractorHttpResult.Success(200, movedKeyPage(REEL_VIDEO), REEL, "text/html")
                postPage -> ExtractorHttpResult.Success(200, errorPage(BURMESE), url, "text/html")
                else -> null
            }
        }
        val master = Answers { url ->
            if (url == embedUrl(REEL)) ExtractorHttpResult.Success(200, PAGE_BODY, url, "text/html") else null
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
        private val scope = capture.session.navigate(page)!!
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
                social = SocialLadder.stage(master, sites::identity, social = ladder),
            ),
            MergeSupport { true },
            requestFactory = { reason, now, expected -> MasterRequest(page, scope.generation, now, reason, expected) },
            socialLadder = socialLadder,
        )

        suspend fun lookup(): SiteAdapterOutcome = withContext(bridge.lookupContext(page)) {
            val primary = sites.inspect(page, BrowserRequestContext(page, "Browser", "c_user=fixture"), NOW)
            bridge.recover(page, primary, NOW)
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val REEL_VIDEO = "1000000000000004"
        const val OTHER_VIDEO = "1000000000000009"
        const val POST_ID = "1000000000000006"
        const val REEL = "https://www.facebook.com/reel/$REEL_VIDEO"
        const val SHARE = "https://www.facebook.com/share/v/FixShare7/"
        const val SHARE_PAGE = "https://www.facebook.com/share/v/FixShare7"
        const val POST = "https://www.facebook.com/fixturepage/posts/$POST_ID"
        const val PAGE_BODY = "<html><head><title>Facebook</title></head><body>no data</body></html>"
        const val BURMESE = "ဒီအကြောင်းအရာကို အခု ကြည့်လို့ မရပါ"

        /** The address main's adapter reads for the post. */
        val postPage: String = checkNotNull(FacebookExtractor(Answers { null }).identify(POST)).canonicalPageUrl

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

        /** Facebook's unavailable page: the error route with its root's privacy flag ([title]: any language). */
        fun errorPage(title: String): String =
            "<html><head><title>Facebook</title></head><body>" +
                "<script type=\"application/json\" data-sjs>{\"require\":[[\"RouteInfo\",null,null,[{\"initialRouteInfo\":" +
                "{\"route\":{\"actorID\":\"0\",\"rootView\":{\"resource\":{\"__dr\":\"Root.react\"},\"props\":" +
                "{\"title\":\"$title\",\"privacy\":true}},\"tracePolicy\":\"comet.test\"," +
                "\"canonicalRouteName\":\"comet.fbweb.CometErrorRoute\"}}}]]]}</script></body></html>"
    }
}
