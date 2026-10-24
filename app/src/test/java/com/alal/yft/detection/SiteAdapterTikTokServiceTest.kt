package com.alal.yft.detection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.detection.master.SocialLadder
import com.alal.yft.extractor.api.NoPoTokenProvider
import com.alal.yft.extractor.api.PlayerScriptRequest
import com.alal.yft.extractor.api.PlayerScriptResult
import com.alal.yft.extractor.api.PlayerScriptRunner
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SitePageData
import com.alal.yft.extractor.api.SitePageDataSource
import com.alal.yft.extractor.sites.tiktok.TikTokAgents
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Social plan task 10 (decision 22, `MASTER_KEY_SOCIAL_PLAN.md` §8; P46's choice B reversed,
 * `TT_SERVICE=OFF`): main's TikTok adapter as [SiteAdapterModule] builds it never asks the
 * public download service (`tikwm.com`). A post TikTok's own pages and the hidden page give no
 * file fails with TikTok's own reason, its Details without a "download service" line; a post
 * TikTok's pages give keeps the rows P46's wiring gave. P46's wiring (the service on) over the
 * same fakes still takes the service's files: the fakes would have answered it. Made-up posts,
 * IDs and addresses; an interceptor answers every request; no live calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SiteAdapterTikTokServiceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a post both pages call private fails with TikTok's reason, the service never asked`() = runTest {
        val network = FakeNetwork(phone = PRIVATE_PAGE, desktop = PRIVATE_PAGE)
        val pages = HiddenPages(NO_DATA)

        val failed = app(network, pages).inspect(POST, BROWSER, NOW) as SiteAdapterOutcome.Failed

        assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, failed.reason)
        assertEquals("TikTok's two pages, nothing else", listOf(POST, POST), network.asked)
        assertEquals("the hidden page still read, once", listOf(POST), pages.links)
        assertTrue(failed.details.toString(), "hidden page: no data after 20 s" in failed.details)
        assertNoService(network, failed.details)

        // P46's wiring over the same fakes took the service's files.
        val before = FakeNetwork(phone = PRIVATE_PAGE, desktop = PRIVATE_PAGE)
        val found = p46(before, HiddenPages(NO_DATA)).inspect(POST, BROWSER, NOW) as SiteAdapterOutcome.Detected
        assertEquals(listOf(SERVICE_HD, SERVICE_SD), found.candidates.map { it.mediaUrl })
        assertTrue(before.asked.toString(), before.asked.any { it.startsWith("$SERVICE/api/?url=") })
    }

    @Test
    fun `a post whose files TikTok refuses stays without a file, the service never asked`() = runTest {
        val network = FakeNetwork(phone = postPage(REFUSED), desktop = postPage(REFUSED))
        val pages = HiddenPages(found(item(REFUSED)))

        val failed = app(network, pages).inspect(POST, BROWSER, NOW) as SiteAdapterOutcome.Failed

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, failed.reason)
        assertEquals("the hidden page still read, once", listOf(POST), pages.links)
        assertTrue("TikTok's own files were checked", network.asked.any { it.startsWith("$CDN$REFUSED/") })
        assertTrue(failed.details.toString(), "answer: none · no file opened" in failed.details)
        assertTrue(failed.details.toString(), "data: no file opened" in failed.details)
        assertNoService(network, failed.details)

        // P46's wiring over the same fakes took the service's files.
        val before = FakeNetwork(phone = postPage(REFUSED), desktop = postPage(REFUSED))
        val found = p46(before, HiddenPages(found(item(REFUSED)))).inspect(POST, BROWSER, NOW)
            as SiteAdapterOutcome.Detected
        assertEquals(listOf(SERVICE_HD, SERVICE_SD), found.candidates.map { it.mediaUrl })
    }

    @Test
    fun `a post TikTok's pages give keeps the rows P46's wiring gave, the service never asked`() = runTest {
        // The phone page gives the post; the phone page says private and the desktop page gives it.
        for ((phone, desktop) in listOf(postPage() to postPage(), PRIVATE_PAGE to postPage())) {
            val network = FakeNetwork(phone, desktop)
            val pages = HiddenPages()
            val before = FakeNetwork(phone, desktop)

            val rows = app(network, pages).inspect(POST, BROWSER, NOW) as SiteAdapterOutcome.Detected
            val p46Rows = p46(before, HiddenPages()).inspect(POST, BROWSER, NOW) as SiteAdapterOutcome.Detected

            assertEquals(listOf(FILE_1080, FILE_720, FILE_540), rows.candidates.map { it.mediaUrl })
            assertEquals("the rows P46's wiring gave", p46Rows.candidates, rows.candidates)
            assertEquals("the same asks", before.asked.sorted(), network.asked.sorted())
            assertTrue("no hidden page", pages.links.isEmpty())
            assertNoService(network)
            assertNoService(before)
        }
    }

    private fun app(network: FakeNetwork, pages: HiddenPageReader): SiteAdapterCoordinator {
        val client = network.client()
        val extractors = SiteAdapterModule.provideSiteExtractors(
            context = context,
            client = client,
            http = SiteAdapterModule.provideExtractorHttpClient(client),
            playerScripts = NoScripts,
            poTokens = NoPoTokenProvider,
        )
        return coordinator(extractors, pages)
    }

    /** P46's wiring (the owner's choice B, `TT_SERVICE=ON`): the app's TikTok adapter before decision 22. */
    private fun p46(network: FakeNetwork, pages: HiddenPageReader): SiteAdapterCoordinator {
        val tiktok = TikTokExtractor(
            http = SocialLadder.tiktokClient(network.client()),
            agents = TikTokAgents(),
            askDownloadService = true,
        )
        return coordinator(listOf(tiktok), pages)
    }

    private fun coordinator(extractors: List<SiteExtractor>, pages: HiddenPageReader) = SiteAdapterCoordinator(
        registry = SiteAdapterModule.provideSiteExtractorRegistry(
            extractors,
            SiteAdapterModule.provideSiteAdapterFlags(),
        ),
        mergeSupport = MergeSupport { true },
        lookups = SiteLookupCache(),
        hiddenPages = pages,
    )

    /** No ask of the download service; a failure's [details], no "download service" line. */
    private fun assertNoService(network: FakeNetwork, details: List<String>? = null) {
        assertFalse(network.asked.toString(), network.asked.any { it.startsWith(SERVICE) })
        details?.let { lines ->
            assertTrue(lines.toString(), lines.isNotEmpty() && lines.none { SERVICE_LINE in it })
        }
    }

    private fun found(item: String) = HiddenPageResult.Found(
        data = SitePageData(item, SitePageDataSource.HIDDEN_PAGE),
        finalUrl = POST,
        userAgent = AGENT,
        cookie = null,
        details = listOf("hidden page: data found"),
    )

    /**
     * TikTok's pages ([phone]; [desktop] for desktop Chrome's agent), the download service's
     * answer, and one-byte file checks: every file opens but those under [REFUSED] (403).
     */
    private class FakeNetwork(private val phone: String, private val desktop: String) : Interceptor {
        val asked: MutableList<String> = CopyOnWriteArrayList()

        fun client(): OkHttpClient = OkHttpClient.Builder().addInterceptor(this).build()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url
            asked += url.toString()
            val desktopAgent = request.header("User-Agent").orEmpty().contains("Windows NT")
            return when {
                url.host == "www.tiktok.com" -> answer(chain, 200, if (desktopAgent) desktop else phone, HTML)
                url.toString().startsWith(SERVICE) -> answer(chain, 200, SERVICE_ANSWER, JSON)
                !url.host.endsWith(".example-cdn.test") -> answer(chain, 404, "", TEXT)
                url.encodedPath.startsWith("$REFUSED/") -> answer(chain, 403, "", TEXT)
                else -> answer(chain, 206, "\u0000", MP4).newBuilder()
                    .header("Content-Range", "bytes 0-0/$FILE_BYTES")
                    .build()
            }
        }

        private fun answer(chain: Interceptor.Chain, code: Int, body: String, type: String): Response =
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fixture")
                .header("Content-Type", type)
                .body(body.toResponseBody(type.toMediaType()))
                .build()
    }

    /** TikTok's hidden page, answering [answers] in order; [links] are the pages it opened. */
    private class HiddenPages(vararg answers: HiddenPageResult) : HiddenPageReader {
        private val answers = ArrayDeque(answers.toList())
        val links = mutableListOf<String>()

        override fun handles(siteId: String): Boolean = siteId == "tiktok"

        override suspend fun read(link: String, postId: String?): HiddenPageResult {
            links += link
            return answers.removeFirstOrNull() ?: HiddenPageResult.Off
        }
    }

    /** No YouTube player scripts (no YouTube link here). */
    private object NoScripts : PlayerScriptRunner {
        override val isAvailable: Boolean = false

        override suspend fun resolve(request: PlayerScriptRequest): PlayerScriptResult = PlayerScriptResult.Unavailable
    }

    private companion object {
        const val NOW = 1_791_000_000_000L
        const val POST_ID = "7311234567890123456"
        const val POST = "https://www.tiktok.com/@fixture_user/video/$POST_ID"
        const val AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"
        val BROWSER = BrowserRequestContext(POST, AGENT, null)
        const val CDN = "https://v16-webapp.example-cdn.test"
        const val REFUSED = "/refused"
        const val FILE_1080 = "$CDN/video/play/fixture-1080.mp4?expire=4102444800"
        const val FILE_720 = "$CDN/video/play/fixture-720.mp4?expire=4102444800"
        const val FILE_540 = "$CDN/video/play/fixture-540.mp4?expire=4102444800"
        const val FILE_BYTES = 5_308_416L
        const val HTML = "text/html; charset=utf-8"
        const val JSON = "application/json"
        const val TEXT = "text/plain"
        const val MP4 = "video/mp4"
        /** The download service's origin and its Details lines' label (extractor-sites' `TikTokDownloadService`). */
        const val SERVICE = "https://www.tikwm.com"
        const val SERVICE_LINE = "download service"
        const val SERVICE_HD = "https://v16m.example-cdn.test/hd/$POST_ID.mp4"
        const val SERVICE_SD = "https://v16m.example-cdn.test/sd/$POST_ID.mp4"
        const val SERVICE_WM = "https://v16m.example-cdn.test/wm/$POST_ID.mp4"
        val NO_DATA = HiddenPageResult.NotFound(null, null, listOf("hidden page: no data after 20 s"))

        /** extractor-sites' fixture `tiktok/universal_private.html`: TikTok's "private" status. */
        const val PRIVATE_PAGE = "<!doctype html><html lang=\"en\"><head><title>Video currently unavailable</title>" +
            "</head><body><script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\" type=\"application/json\">" +
            "{\"__DEFAULT_SCOPE__\":{\"webapp.video-detail\":{\"statusCode\":10216,\"statusMsg\":\"author_private\"," +
            "\"itemInfo\":{}}}}</script></body></html>"

        /** The download service's answer for the post (extractor-sites' `TikTokDownloadServiceTest`). */
        const val SERVICE_ANSWER = "{\"code\":0,\"msg\":\"success\",\"data\":{\"id\":\"$POST_ID\"," +
            "\"title\":\"Fixture caption\",\"cover\":\"https://p16.example-cdn.test/c.jpg\",\"duration\":12," +
            "\"play\":\"$SERVICE_SD\",\"hdplay\":\"$SERVICE_HD\",\"wmplay\":\"$SERVICE_WM\",\"size\":2000000," +
            "\"hd_size\":5000000,\"author\":{\"unique_id\":\"fixture_user\"}}}"

        /** extractor-sites' fixture `tiktok/page_data_script_item.json`, its files under [prefix]. */
        fun item(prefix: String = ""): String {
            val play = "$CDN$prefix/video/play"
            fun gear(name: String, bitrate: Int, file: String, size: String) =
                "{\"GearName\":\"$name\",\"Bitrate\":$bitrate,\"CodecType\":\"h264\",\"PlayAddr\":{\"UrlList\":" +
                    "[\"$play/fixture-$file.mp4?expire=4102444800\"],\"DataSize\":\"$size\"}}"
            return "{\"id\":\"$POST_ID\",\"desc\":\"Sunrise over the harbour #fixture\",\"author\":{\"uniqueId\":" +
                "\"fixture_user\"},\"video\":{\"duration\":17,\"width\":576,\"height\":1024,\"ratio\":\"540p\"," +
                "\"definition\":\"540p\",\"codecType\":\"h264\",\"bitrate\":1142000,\"cover\":" +
                "\"https://p16-sign.example-cdn.test/obj/cover-fixture\",\"playAddr\":" +
                "\"$play/fixture-standard.mp4?expire=4102444800\",\"downloadAddr\":" +
                "\"$CDN$prefix/video/download/fixture-download.mp4?expire=4102444800\",\"bitrateInfo\":[" +
                gear("normal_1080_0", 2496000, "1080", "5308416") + "," +
                gear("normal_720_0", 1142000, "720", "2428928") + "," +
                gear("lower_540_0", 528000, "540", "1122304") + "]}}"
        }

        /** TikTok's page with [item]'s post (extractor-sites' fixture `tiktok/universal_video.html`). */
        fun postPage(prefix: String = ""): String =
            "<!doctype html><html lang=\"en\"><head><title>Fixture user on TikTok</title></head><body>" +
                "<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\" type=\"application/json\">{\"__DEFAULT_SCOPE__\":" +
                "{\"webapp.video-detail\":{\"statusCode\":0,\"statusMsg\":\"\",\"itemInfo\":{\"itemStruct\":" +
                item(prefix) + "}}}}</script></body></html>"
    }
}
