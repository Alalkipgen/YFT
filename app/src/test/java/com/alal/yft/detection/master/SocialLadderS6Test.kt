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
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import com.alal.yft.extractor.master.social.SocialCooldown
import com.alal.yft.extractor.sites.facebook.FacebookExtractor
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Social plan S1a (6), S6, in the app's lookup, in a social-ladder build: a Facebook lookup offers
 * a row only after its file's first bytes (read once, without the user's session) pass V and the
 * init check; when the bytes prove the rows wrong none is offered and the lookup ends with
 * Master's failure, as a lookup with no row does. Flag off (every CI build): the engine's file check is
 * the one given, no file is read and the same lookups answer as today. Main's real Facebook
 * adapter over synthetic pages, a fake file reader; each case passes the switch explicitly. No
 * live calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SocialLadderS6Test {
    @Test
    fun aFacebookLookupOffersOnlyRowsWhoseFilesPassVAndTheInitCheck() = runTest {
        val fixture = Fixture(socialLadder = true)
        fixture.mainAnswer = ExtractorHttpResult.Success(200, movedKeyPage(VIDEO), watchPage, "text/html")

        val found = fixture.lookup() as SiteAdapterOutcome.Detected
        assertEquals(setOf(hd(VIDEO), sd(VIDEO)), found.candidates.map { it.mediaUrl }.toSet())
        // Social plan task 15 (F1): Master's rows after main's failure carry its lines, so the
        // browser's banner names Facebook's ladder line (no trial here: nothing to compare).
        val notice = SocialLadder.trialNotice(found)
        assertTrue(notice.toString(), notice?.startsWith("master: facebook ladder after RESPONSE_CHANGED") == true)
        assertTrue(found.details.toString(), found.details.none { "://" in it })
        found.candidates.forEach { assertEquals(TOTAL, it.contentLengthBytes) }
        // Each file read once, never with the user's session; the engine's own check not asked.
        assertEquals(setOf(hd(VIDEO), sd(VIDEO)), fixture.files.asked.toSet())
        assertEquals(2, fixture.files.asked.size)
        assertTrue(fixture.files.headers.all { headers -> headers.keys.none { it.equals("Cookie", ignoreCase = true) } })
        assertEquals(0, fixture.checks.get())

        // The files' bytes are no MP4 start (a page): no row is offered.
        fixture.files.answer = MediaStart.Read(206, "video/mp4", "bytes 0-31/$TOTAL", 32, ByteArray(32) { '<'.code.toByte() })
        val wrong = fixture.lookup() as SiteAdapterOutcome.Failed
        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, wrong.reason)
        assertEquals(4, fixture.files.asked.size)
        assertEquals(0, fixture.checks.get())
    }

    @Test
    fun flagOffTheEnginesOwnCheckIsTodaysAndNoFileIsRead() = runTest {
        val own = MasterMediaValidator { found, _ -> ValidationResult.Valid(found) }
        var built = 0
        assertSame(own, SocialLadder.validator(own, social = false) { built++; Files() })
        assertEquals(0, built)

        // Today's engine (no ladder, its own check), and the social pieces present with the switch
        // off: the same lookups answer alike, in today's words, and no file is read.
        val today = Fixture(socialLadder = false, ladder = false)
        val off = Fixture(socialLadder = false, ladder = true)
        for (fixture in listOf(today, off)) {
            fixture.mainAnswer = ExtractorHttpResult.Success(200, movedKeyPage(VIDEO), watchPage, "text/html")
        }
        val todays = today.lookup() as SiteAdapterOutcome.Failed
        val offs = off.lookup() as SiteAdapterOutcome.Failed
        assertEquals(todays, offs)
        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, offs.reason)
        assertEquals("Play the video on this page, then tap Try again.", offs.message)
        for (fixture in listOf(today, off)) {
            assertTrue(fixture.files.asked.isEmpty())
            assertEquals(listOf(embedUrl(watchPage)), fixture.master.asked)
        }
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

    /** A fake file reader: an MP4 start of a 32 KiB file unless [answer] is changed. */
    private class Files : MediaStartReader {
        val asked = CopyOnWriteArrayList<String>()
        val headers = CopyOnWriteArrayList<Map<String, String>>()
        var answer: MediaStart = MediaStart.Read(206, "video/mp4", "bytes 0-${MP4.size - 1}/$TOTAL", MP4.size.toLong(), MP4)

        override suspend fun read(url: String, headers: Map<String, String>, maxBytes: Int): MediaStart {
            asked += url
            this.headers += headers
            return answer
        }
    }

    private class Fixture(socialLadder: Boolean, ladder: Boolean = socialLadder) {
        var mainAnswer: ExtractorHttpResult = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)

        /** The test's own cooldown (the app's stays untouched). */
        val cooldown = SocialCooldown()
        val main = Answers { url -> mainAnswer.takeIf { url == watchPage } }

        // The plugin player has no data.
        val master = Answers { url ->
            if (url == embedUrl(watchPage)) ExtractorHttpResult.Success(200, PAGE_BODY, url, "text/html") else null
        }
        val files = Files()

        /** The engine's own file check: how often it was asked. */
        val checks = AtomicInteger()
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
                SocialLadder.validator(
                    MasterMediaValidator { found, _ ->
                        checks.incrementAndGet()
                        ValidationResult.Valid(found.copy(contentLengthBytes = 4096))
                    },
                    social = ladder,
                ) { files },
                PlaybackCaptureProvider { CaptureResult.Unavailable },
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
        const val TOTAL = 32_768L
        const val VIDEO = "1000000000000003"
        const val OTHER_VIDEO = "1000000000000009"
        const val WATCH = "https://www.facebook.com/watch/?v=$VIDEO"
        const val PAGE_BODY = "<html><head><title>Facebook</title></head><body>no data</body></html>"

        /** An MP4 file's start: an `ftyp` box, then nothing the init check reads as a marker. */
        val MP4: ByteArray = ByteArray(64).apply {
            this[3] = 24
            "ftypisom".toByteArray().copyInto(this, 4)
        }

        /** The address main's adapter reads for the video. */
        val watchPage: String = checkNotNull(FacebookExtractor(Answers { null }).identify(WATCH)).canonicalPageUrl

        fun embedUrl(page: String) = "https://www.facebook.com/plugins/video.php?href=" +
            URLEncoder.encode(page, StandardCharsets.UTF_8).replace("+", "%20")

        fun hd(video: String) = "https://video.fixture.fbcdn.net/v/$video-hd.mp4?oe=F2A52380&oh=fixture"

        fun sd(video: String) = "https://video.fixture.fbcdn.net/v/$video-sd.mp4?oe=F2A52380&oh=fixture"

        /** The video's files under renamed keys (main reads it RESPONSE_CHANGED), a related video's beside them. */
        fun movedKeyPage(video: String): String =
            "<html><head><title>Facebook</title></head><body>" +
                "<script type=\"application/json\" data-sjs>{\"require\":[[\"VideoPlayerRelay\",null,null,[{" +
                "\"video\":{\"video_id\":\"$video\",\"browser_native_hd_url_v2\":\"${hd(video)}\"," +
                "\"browser_native_sd_url_v2\":\"${sd(video)}\"},\"related\":[{\"video_id\":\"$OTHER_VIDEO\"," +
                "\"browser_native_hd_url_v2\":\"${hd(OTHER_VIDEO)}\"}]}]]]}</script></body></html>"
    }
}
