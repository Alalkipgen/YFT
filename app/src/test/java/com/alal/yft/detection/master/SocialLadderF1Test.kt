package com.alal.yft.detection.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.OkHttpExtractorClient
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
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Social plan S1a (3): Facebook's ladder (F1, the plugin player, judged) in the app's lookup, in a
 * social-ladder build: a share link main resolved gets the ladder's rows for its video, and the
 * page's own route (private, in any language) ends the lookup in main's words. Flag off (every CI
 * build): the same lookup answers as today (no record, so the ladder claims nothing; R8's path).
 * Main's real Facebook adapter over synthetic pages; each case passes the switch explicitly.
 * No live calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SocialLadderF1Test {
    @Test
    fun aShareLinkGetsTheLaddersRowsForTheVideoMainResolvedItTo() = runTest {
        val fixture = Fixture(page = SHARE, plugin = embed(REEL_VIDEO), socialLadder = true)

        val detected = fixture.lookup() as SiteAdapterOutcome.Detected

        assertEquals(listOf(hd(REEL_VIDEO), sd(REEL_VIDEO)), detected.candidates.map { it.mediaUrl })
        detected.candidates.forEach { assertEquals("facebook:$REEL_VIDEO", it.videoId) }
        assertEquals(listOf(embedUrl(REEL)), fixture.master.asked)
        assertEquals(1, fixture.main.asked.size)
        assertEquals(0, fixture.captures.get())
    }

    @Test
    fun theLaddersPrivateAnswerEndsTheLookupInMainsWords() = runTest {
        val fixture = Fixture(page = REEL, plugin = errorPage(BURMESE), socialLadder = true)

        val failed = fixture.lookup() as SiteAdapterOutcome.Failed

        assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, failed.reason)
        assertEquals("This Facebook post is private or no longer available.", failed.message)
        // Task 15 (F3b): only a photos-only answer drops Try again; a private post keeps it.
        assertTrue(failed.canRetry)
        assertEquals(listOf(embedUrl(REEL)), fixture.master.asked)
        assertEquals(0, fixture.captures.get())
        assertTrue(failed.details.none { BURMESE in it || REEL_VIDEO in it })
    }

    @Test
    fun flagOffTheSameLookupAnswersAsToday() = runTest {
        assertNull(SocialLadder.stage(OkHttpExtractorClient(OkHttpClient()), { null }, social = false))
        // Today's engine (no ladder), and the ladder present with the switch off: no record, so it
        // claims nothing and R8's path answers, in today's words.
        val today = Fixture(page = REEL, plugin = errorPage(BURMESE), socialLadder = false, ladder = false)
        val off = Fixture(page = REEL, plugin = errorPage(BURMESE), socialLadder = false, ladder = true)

        val todays = today.lookup() as SiteAdapterOutcome.Failed
        val offs = off.lookup() as SiteAdapterOutcome.Failed

        assertEquals(todays, offs)
        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, offs.reason)
        assertEquals("Play the video on this page, then tap Try again.", offs.message)
        assertEquals(today.master.asked, off.master.asked)
        assertEquals(1, off.captures.get())
        assertTrue(offs.details.none { it.startsWith("master: facebook ladder") })
        // A share link: no resolved video for today's path, so nothing is asked about it.
        val share = Fixture(page = SHARE, plugin = embed(REEL_VIDEO), socialLadder = false, ladder = true)
        assertTrue(share.lookup() is SiteAdapterOutcome.Failed)
        assertTrue(share.master.asked.isEmpty())
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
        plugin: String,
        socialLadder: Boolean,
        ladder: Boolean = socialLadder,
    ) {
        // Main's pages carry no data (main fails its reading); the share link redirects to the reel.
        val main = Answers { url ->
            when (url) {
                SHARE_PAGE -> ExtractorHttpResult.Success(200, PAGE_BODY, REEL, "text/html")
                REEL -> ExtractorHttpResult.Success(200, PAGE_BODY, REEL, "text/html")
                else -> null
            }
        }
        val master = Answers { url -> if (url == embedUrl(REEL)) ExtractorHttpResult.Success(200, plugin, url, "text/html") else null }
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
        const val REEL = "https://www.facebook.com/reel/$REEL_VIDEO"
        const val SHARE = "https://www.facebook.com/share/v/FixShare7/"
        const val SHARE_PAGE = "https://www.facebook.com/share/v/FixShare7"
        const val PAGE_BODY = "<html><head><title>Facebook</title></head><body>no data</body></html>"
        const val BURMESE = "ဒီအကြောင်းအရာကို အခု ကြည့်လို့ မရပါ"

        fun embedUrl(page: String) = "https://www.facebook.com/plugins/video.php?href=" +
            URLEncoder.encode(page, StandardCharsets.UTF_8).replace("+", "%20")

        fun hd(video: String) = "https://video.example-cdn.test/v/$video-hd.mp4?oe=F2A52380&oh=fixture"

        fun sd(video: String) = "https://video.example-cdn.test/v/$video-sd.mp4?oe=F2A52380&oh=fixture"

        /** The plugin player's shape (FacebookContractTest's) for [video], with its two files. */
        fun embed(video: String): String =
            "<!DOCTYPE html><html><head><title>Facebook</title></head><body>" +
                "<script nonce=\"fixture\">requireLazy([\"TimeSliceImpl\",\"ServerJS\"],function(TimeSlice,ServerJS){" +
                "var s=(new ServerJS());s.handle({\"instances\":[[\"__inst_fixture\",[\"VideoConfig\"],[{" +
                "\"ad_client_token\":null,\"video_id\":\"$video\",\"videoData\":[{\"is_hls\":false," +
                "\"video_id\":\"$video\",\"hd_src\":\"${hd(video).replace("/", "\\/")}\"," +
                "\"sd_src\":\"${sd(video).replace("/", "\\/")}\",\"dash_manifest\":null," +
                "\"original_height\":720,\"original_width\":1280,\"videoLicenseUriMap\":{}," +
                "\"graphApiVideoLicenseUri\":null,\"OzDrmHelper\":null}]}],1]]});});</script></body></html>"

        /** Facebook's unavailable page: the error route with its root's privacy flag ([title]: any language). */
        fun errorPage(title: String): String =
            "<html><head><title>Facebook</title></head><body>" +
                "<script type=\"application/json\" data-sjs>{\"require\":[[\"RouteInfo\",null,null,[{\"initialRouteInfo\":" +
                "{\"route\":{\"actorID\":\"0\",\"rootView\":{\"resource\":{\"__dr\":\"Root.react\"},\"props\":" +
                "{\"title\":\"$title\",\"privacy\":true}},\"tracePolicy\":\"comet.test\"," +
                "\"canonicalRouteName\":\"comet.fbweb.CometErrorRoute\"}}}]]]}</script></body></html>"
    }
}
