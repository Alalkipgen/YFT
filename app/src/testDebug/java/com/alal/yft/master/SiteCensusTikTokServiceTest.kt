package com.alal.yft.master

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.detection.HiddenPageReader
import com.alal.yft.detection.HiddenPageResult
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.detection.SiteLookupCache
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageData
import com.alal.yft.extractor.api.SitePageDataSource
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.lookup.AnswerMark
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.social.MainAnswer
import com.alal.yft.extractor.master.social.SiteCensus
import com.alal.yft.extractor.master.social.SiteWays
import com.alal.yft.extractor.sites.tiktok.TikTokAgents
import com.alal.yft.extractor.sites.tiktok.TikTokExtractor
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan task 10 (decision 22, `MASTER_KEY_SOCIAL_PLAN.md` §8): the site census's main is
 * the app's ([SiteCensusLookup.registry]), so it asks no download service (`tikwm.com`) either.
 * A post TikTok's pages and the hidden page give no file is main's failure with TikTok's own
 * reason (no ask of the service in the row's record, no "download service" line in main's
 * Details); a post TikTok's pages give keeps the rows P46's census gave. P46's TikTok adapter
 * (the service on) over the same fakes still takes the service's files. Fake clients and made-up
 * posts only; no link of the owner's.
 */
class SiteCensusTikTokServiceTest {
    @Test
    fun `a post both pages call private stays TikTok's reason, the service never asked`() = runTest {
        val http = FakeHttp(phone = PRIVATE_PAGE, desktop = PRIVATE_PAGE)
        val pages = FakePages(NO_DATA)
        val lookup = lookup(http, pages)
        val identity = checkNotNull(lookup.identify(POST))
        val record = LookupRecord(labels = SiteWays.TIKTOK.labels())

        val failed = withContext(record) { lookup.lookup(POST, identity) } as MainAnswer.Failed

        assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, failed.reason)
        assertEquals(AnswerMark.NO_DATA, failed.hiddenPage)
        assertEquals("the hidden page still read, once", listOf(POST), pages.links)
        assertEquals(
            "main's two pages in the row's record, no download service",
            listOf(SiteWays.PHONE_PAGE, SiteWays.DESKTOP_PAGE),
            record.asks().map { it.way },
        )
        assertNoService(http, details(FakeHttp(PRIVATE_PAGE, PRIVATE_PAGE), FakePages(NO_DATA)))

        // P46's census over the same fakes took the service's files.
        val p46Record = LookupRecord(labels = SiteWays.TIKTOK.labels())
        val p46 = withContext(p46Record) {
            p46(FakeHttp(PRIVATE_PAGE, PRIVATE_PAGE), FakePages(NO_DATA)).lookup(POST, identity)
        } as MainAnswer.Found
        assertEquals(listOf(SERVICE_HD, SERVICE_SD), p46.rows.map { it.mediaUrl })
        assertTrue(SiteWays.DOWNLOAD_SERVICE in p46Record.asks().map { it.way })
    }

    @Test
    fun `a post whose files TikTok refuses stays without a file, the service never asked`() = runTest {
        val http = FakeHttp(phone = postPage(REFUSED), desktop = postPage(REFUSED))
        val pages = FakePages(found(item(REFUSED)))
        val lookup = lookup(http, pages)
        val identity = checkNotNull(lookup.identify(POST))
        val record = LookupRecord(labels = SiteWays.TIKTOK.labels())

        val failed = withContext(record) { lookup.lookup(POST, identity) } as MainAnswer.Failed

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, failed.reason)
        assertEquals("the hidden page's data, its files refused too", AnswerMark.DATA, failed.hiddenPage)
        assertTrue("TikTok's own files were checked", http.asked.any { it.startsWith("$CDN$REFUSED/") })
        assertFalse(SiteWays.DOWNLOAD_SERVICE in record.asks().map { it.way })
        assertNoService(http, details(FakeHttp(postPage(REFUSED), postPage(REFUSED)), FakePages(found(item(REFUSED)))))

        // P46's census over the same fakes took the service's files.
        val p46 = p46(FakeHttp(postPage(REFUSED), postPage(REFUSED)), FakePages(found(item(REFUSED))))
            .lookup(POST, identity) as MainAnswer.Found
        assertEquals(listOf(SERVICE_HD, SERVICE_SD), p46.rows.map { it.mediaUrl })
    }

    @Test
    fun `a post TikTok's pages give keeps the rows P46's census gave, the service never asked`() = runTest {
        // The phone page gives the post; the phone page says private and the desktop page gives it.
        for ((phone, desktop) in listOf(postPage() to postPage(), PRIVATE_PAGE to postPage())) {
            val http = FakeHttp(phone, desktop)
            val before = FakeHttp(phone, desktop)
            val lookup = lookup(http, FakePages())
            val identity = checkNotNull(lookup.identify(POST))

            val rows = lookup.lookup(POST, identity) as MainAnswer.Found
            val p46 = p46(before, FakePages()).lookup(POST, identity) as MainAnswer.Found

            assertEquals(listOf(FILE_1080, FILE_720, FILE_540), rows.rows.map { it.mediaUrl })
            assertEquals("the rows P46's census gave", p46.rows, rows.rows)
            assertEquals("the same asks", before.asked.sorted(), http.asked.sorted())
            assertNoService(http)
            assertNoService(before)
        }
    }

    @Test
    fun `the census row of a post only the service gave keeps TikTok's reason, the service not reached`() = runTest {
        val http = FakeHttp(PRIVATE_PAGE, PRIVATE_PAGE)
        val census = SiteCensus(lookup(http, FakePages(NO_DATA)), http, VALID, clock = { NOW })

        val report = census.run(listOf(SiteWays.TIKTOK), mapOf("tiktok" to mapOf(9 to POST)), null).report

        assertTrue(report, report.contains("slot 9 removed: failed PRIVATE_OR_UNAVAILABLE"))
        assertTrue(report, report.contains("not reached: ${SiteWays.DOWNLOAD_SERVICE}"))
        assertFalse(report, report.contains("${SiteWays.DOWNLOAD_SERVICE} GET"))
        assertNoService(http)

        // P46's census: main's rows from the service, its ask in main's line.
        val before = FakeHttp(PRIVATE_PAGE, PRIVATE_PAGE)
        val p46 = SiteCensus(p46(before, FakePages(NO_DATA)), before, VALID, clock = { NOW })
            .run(listOf(SiteWays.TIKTOK), mapOf("tiktok" to mapOf(9 to POST)), null).report
        assertTrue(p46, p46.contains("${SiteWays.DOWNLOAD_SERVICE} GET 200"))
        assertFalse(p46, p46.contains("not reached: ${SiteWays.DOWNLOAD_SERVICE}"))
    }

    private fun lookup(http: FakeHttp, pages: HiddenPageReader): SiteCensusLookup {
        val recorder = LookupRecorder(http)
        return SiteCensusLookup(
            registry = SiteCensusLookup.registry(recorder, recorder) { AGENT },
            hiddenPages = pages,
            userAgent = { AGENT },
            cookies = { null },
            mergeSupport = { MergeSupport { true } },
            clock = { NOW },
        )
    }

    /** P46's census (the owner's choice B, `TT_SERVICE=ON`): its TikTok adapter before decision 22. */
    private fun p46(http: FakeHttp, pages: HiddenPageReader): SiteCensusLookup = SiteCensusLookup(
        registry = SiteExtractorRegistry(
            listOf(
                TikTokExtractor(
                    http = LookupRecorder(http),
                    agents = TikTokAgents(webViewAgent = { AGENT }),
                    askDownloadService = true,
                ),
            ),
        ),
        hiddenPages = pages,
        userAgent = { AGENT },
        cookies = { null },
        mergeSupport = { MergeSupport { true } },
        clock = { NOW },
    )

    /** No ask of the download service; a failure's [details] (main's), no "download service" line. */
    private fun assertNoService(http: FakeHttp, details: List<String>? = null) {
        assertFalse(http.asked.toString(), http.asked.any { it.startsWith(SERVICE) })
        details?.let { lines ->
            assertTrue(lines.toString(), lines.isNotEmpty() && lines.none { SiteWays.DOWNLOAD_SERVICE in it })
        }
    }

    /** Main's Details for the post: the app's coordinator over the census's adapters. */
    private suspend fun details(http: FakeHttp, pages: HiddenPageReader): List<String> {
        val outcome = SiteAdapterCoordinator(
            SiteCensusLookup.registry(http, http) { AGENT },
            MergeSupport { true },
            SiteLookupCache(),
            pages,
        ).inspect(POST, BrowserRequestContext(POST, AGENT, null), NOW, fresh = true)
        return when (outcome) {
            is SiteAdapterOutcome.Failed -> outcome.details
            is SiteAdapterOutcome.Detected -> outcome.details
            SiteAdapterOutcome.NotHandled -> emptyList()
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
    private class FakeHttp(private val phone: String, private val desktop: String) : ExtractorHttpClient {
        val asked = mutableListOf<String>()

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            asked += url
            val desktopAgent = headers["User-Agent"].orEmpty().contains("Windows NT")
            return when {
                url.startsWith("https://www.tiktok.com/") ->
                    ExtractorHttpResult.Success(200, if (desktopAgent) desktop else phone, url, "text/html")
                url.startsWith(SERVICE) ->
                    ExtractorHttpResult.Success(200, SERVICE_ANSWER, url, "application/json")
                else -> ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)
            }
        }

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult {
            asked += url
            return ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)
        }

        override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult {
            asked += url
            return if ("$REFUSED/" in url) {
                ExtractorProbeResult.Refused(SiteExtractionFailure.HTTP_STATUS, 403)
            } else {
                ExtractorProbeResult.Answered(206, FILE_BYTES)
            }
        }
    }

    /** TikTok's hidden page, answering [answers] in order; [links] are the pages it opened. */
    private class FakePages(vararg answers: HiddenPageResult) : HiddenPageReader {
        private val answers = ArrayDeque(answers.toList())
        val links = mutableListOf<String>()

        override fun handles(siteId: String): Boolean = siteId == "tiktok"

        override suspend fun read(link: String, postId: String?): HiddenPageResult {
            links += link
            return answers.removeFirstOrNull() ?: HiddenPageResult.Off
        }
    }

    private companion object {
        const val NOW = 1_791_000_000_000L
        const val POST_ID = "7311234567890123456"
        const val POST = "https://www.tiktok.com/@fixture_user/video/$POST_ID"
        const val AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"
        const val CDN = "https://v16-webapp.example-cdn.test"
        const val REFUSED = "/refused"
        const val FILE_1080 = "$CDN/video/play/fixture-1080.mp4?expire=4102444800"
        const val FILE_720 = "$CDN/video/play/fixture-720.mp4?expire=4102444800"
        const val FILE_540 = "$CDN/video/play/fixture-540.mp4?expire=4102444800"
        const val FILE_BYTES = 5_308_416L
        /** The download service's origin (extractor-sites' `TikTokDownloadService`). */
        const val SERVICE = "https://www.tikwm.com"
        const val SERVICE_HD = "https://v16m.example-cdn.test/hd/$POST_ID.mp4"
        const val SERVICE_SD = "https://v16m.example-cdn.test/sd/$POST_ID.mp4"
        const val SERVICE_WM = "https://v16m.example-cdn.test/wm/$POST_ID.mp4"
        val NO_DATA = HiddenPageResult.NotFound(null, null, listOf("hidden page: no data after 20 s"))
        val VALID = MasterMediaValidator { candidate, _ -> ValidationResult.Valid(candidate) }

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
