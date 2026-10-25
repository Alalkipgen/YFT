package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.contract.mainRequest
import com.alal.yft.extractor.master.fixture
import com.alal.yft.extractor.master.lookup.AnswerMark
import com.alal.yft.extractor.master.lookup.AnswerMark.BOT_CHECK
import com.alal.yft.extractor.master.lookup.AnswerMark.DATA
import com.alal.yft.extractor.master.lookup.AnswerMark.NOT_JSON
import com.alal.yft.extractor.master.lookup.AnswerMark.NO_DATA
import com.alal.yft.extractor.master.lookup.AnswerMark.NO_POST
import com.alal.yft.extractor.master.lookup.LookupAsk
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import com.alal.yft.extractor.sites.vk.VkExtractor
import java.net.URI
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VK-Master plan §10, Appendix B step 4: VK's labels ([VkLabels]) on the asks of main's real VK
 * adapter (`extractor-sites`, as the app builds it) over made-up answers (`fixtures/vk-pack/`), in
 * a record labelled as the census labels it: the ways in main's order (the embed page, then path
 * D's anonymous token and `video.get`), each 2xx answer's mark by Master's own reader and judge
 * and its shape; main's manifest reads labelled by their address, any other file GET (V's reads)
 * not; ways from the ask's method and address alone, never a header's value. JVM only, no network.
 */
class VkLabelsTest {
    @Test
    fun `main's asks in main's order - the embed page, the anonymous token and video get, each marked`() = runTest {
        val main = main(vk())

        assertTrue(main.result.toString(), main.result is SiteExtractionResult.Success)
        val asks = main.record.asks()
        assertEquals(listOf(VkLabels.EMBED, VkLabels.TOKEN, VkLabels.VIDEO_GET), asks.map { it.way })
        assertEquals(listOf(NO_POST, DATA, DATA), asks.map { it.mark })
        assertEquals(listOf(LookupAsk.GET, LookupAsk.POST, LookupAsk.GET), asks.map { it.method })
        assertTrue(asks.all { it.asker == LookupRecord.Asker.MAIN })
        assertTrue(PAGE_SHAPE.matches(checkNotNull(asks[0].shape)))
        asks.drop(1).forEach { assertTrue(it.toString(), JSON_SHAPE.matches(checkNotNull(it.shape))) }
        assertTrue(main.record.stopHosts().isEmpty())
        assertEquals(0, main.record.botChecks)

        // The table's main ways are main's order; Master's own way is V2's video.get.
        assertEquals(listOf(VkLabels.EMBED, VkLabels.TOKEN, VkLabels.VIDEO_GET), VkSite.SITE.mainWays)
        assertEquals(VkLabels.VIDEO_GET, VkSite.SITE.masterWay)
    }

    @Test
    fun `the embed's answers - rows are data, VK's message no post on both hosts, the shell no data`() = runTest {
        val files = main(vk(embed = { page(fixture("vk-pack/embed_files.html"), it) }))
        assertTrue(files.result is SiteExtractionResult.Success)
        assertEquals(listOf(VkLabels.EMBED to DATA), files.labels())

        val notFound = main(vk(embed = { page(fixture("vk-pack/embed_not_found.html"), it) }))
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, (notFound.result as SiteExtractionResult.Failure).reason)
        assertEquals(List(2) { VkLabels.EMBED to NO_POST }, notFound.labels())
        assertEquals(
            listOf(VkClient.COM_HOST, VkClient.RU_HOST),
            notFound.pages.pages().map { URI(it.url).host },
        )

        // VK's shell: B, B again on the same host, then B2.
        val shell = main(vk(embed = { page(fixture("vk-pack/embed_shell.html"), it) }))
        assertEquals(List(3) { VkLabels.EMBED to NO_DATA }, shell.labels())

        val shapes = listOf(files, notFound, shell).map { it.record.asks().first().shape }
        assertEquals(shapes.toString(), 3, shapes.toSet().size)
    }

    @Test
    fun `path D's answers - VK's captcha a bot check that stops its host, refused values and VK's other errors no data, can_play 0 no post`() = runTest {
        val captcha = main(vk(token = { json(fixture("vk-pack/token_captcha.json"), VkClient.TOKEN_URL) }))
        assertEquals(listOf(VkLabels.EMBED to NO_POST, VkLabels.TOKEN to BOT_CHECK), captcha.labels())
        assertEquals(listOf(VkClient.TOKEN_HOST), captcha.record.stopHosts())
        assertEquals(1, captcha.record.botChecks)

        val apiCaptcha = main(vk(api = answers("video_get_captcha.json")))
        assertEquals(VkLabels.VIDEO_GET to BOT_CHECK, apiCaptcha.labels().last())
        assertEquals(listOf(VkClient.API_HOST), apiCaptcha.record.stopHosts())

        val cases = mapOf(
            "video_get_error_5.json" to NO_DATA,
            "video_get_deleted.json" to NO_POST,
            "video_get_live.json" to NO_DATA,
            "video_get_other_video.json" to NO_DATA,
            "video_get_unknown_host.json" to NO_DATA,
            "video_get_renamed.json" to DATA,
        )
        cases.forEach { (name, mark) ->
            val main = main(vk(api = answers(name)))
            assertEquals(name, listOf(VkLabels.EMBED to NO_POST, VkLabels.TOKEN to DATA, VkLabels.VIDEO_GET to mark), main.labels())
            assertTrue(name, main.record.stopHosts().isEmpty())
        }

        // VK's rate error is no data here (V1's judge stops the site on it, not the record).
        val rate = main(vk(api = { json(RATE_ERROR, it) }))
        assertEquals(VkLabels.VIDEO_GET to NO_DATA, rate.labels().last())
        assertTrue(rate.record.stopHosts().isEmpty())

        // A token answer without a value of VK's shape is no data; not JSON is not JSON.
        val noValue = main(vk(token = { json(fixture("vk-pack/token_no_value.json"), VkClient.TOKEN_URL) }))
        assertEquals(listOf(VkLabels.EMBED to NO_POST, VkLabels.TOKEN to NO_DATA), noValue.labels())
        val tokenPage = main(vk(token = { page(NOT_JSON_PAGE, VkClient.TOKEN_URL) }))
        assertEquals(VkLabels.TOKEN to NOT_JSON, tokenPage.labels().last())
        assertNull(tokenPage.record.asks().last().shape)
        val apiPage = main(vk(api = { page(NOT_JSON_PAGE, it) }))
        assertEquals(VkLabels.VIDEO_GET to NOT_JSON, apiPage.labels().last())

        // The same structure with other values keeps its shape; a renamed key changes it.
        val okay = main(vk()).record.asks()[1].shape
        val other = main(
            vk(token = { json(fixture("vk-pack/token_okay.json").replace("0123456789", "9876543210"), VkClient.TOKEN_URL) }),
        ).record.asks()[1].shape
        val renamed = main(
            vk(token = { json(fixture("vk-pack/token_okay.json").replace("\"expires\"", "\"expiry\""), VkClient.TOKEN_URL) }),
        ).record.asks()[1]
        assertEquals(okay, other)
        assertNotEquals(okay, renamed.shape)
        assertEquals(DATA, renamed.mark)
    }

    @Test
    fun `main's manifest reads are labelled manifest - only for an answer without MP4, by the address`() = runTest {
        val manifestsOnly = fixture("vk-pack/embed_files.html").replace(MP4_ENTRY, "")
        val main = main(
            vk(
                embed = { page(manifestsOnly, it) },
                files = { ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 403) },
            ),
        )

        assertTrue(main.result.toString(), main.result is SiteExtractionResult.Success)
        assertEquals(
            listOf(VkLabels.EMBED to DATA, VkLabels.MANIFEST to null, VkLabels.MANIFEST to null),
            main.labels(),
        )
        val manifests = main.record.asks().drop(1)
        assertTrue(manifests.all { it.asker == LookupRecord.Asker.MAIN && it.method == LookupAsk.GET })
        assertTrue(manifests.all { it.shape == null })

        // With MP4 files main reads no manifest.
        val files = main(vk(embed = { page(fixture("vk-pack/embed_files.html"), it) }))
        assertTrue(files.labels().none { it.first == VkLabels.MANIFEST })
    }

    @Test
    fun `ways from the ask's method and address alone - never a header's value, no other host or path`() {
        val labels = VkLabels()
        val credentials = mapOf("Cookie" to "remixsid=FIXTURE", "Authorization" to "Bearer fixture.not-real")
        fun way(method: String, url: String, headers: Map<String, String> = emptyMap(), body: String? = null) =
            labels.way(LookupAsk(method, url, body, headers))

        val embed = "https://vk.com/video_ext.php?oid=-100000001&id=400000001"
        assertEquals(VkLabels.EMBED, way(LookupAsk.GET, embed))
        assertEquals(VkLabels.EMBED, way(LookupAsk.GET, embed, credentials))
        assertEquals(VkLabels.EMBED, way(LookupAsk.GET, "https://vk.ru/video_ext.php?oid=-100000001&id=400000001&list=FIXTURE"))
        assertEquals(VkLabels.TOKEN, way(LookupAsk.POST, VkClient.TOKEN_URL, body = VkClient.BUNDLED.tokenBody))
        assertEquals(VkLabels.TOKEN, way(LookupAsk.POST, VkClient.TOKEN_URL, credentials, body = "{}"))
        val videoGet = VkClient.BUNDLED.videoGetUrl(LINK)
        assertEquals(VkLabels.VIDEO_GET, way(LookupAsk.GET, videoGet))
        assertEquals(VkLabels.VIDEO_GET, way(LookupAsk.GET, videoGet, credentials))
        assertEquals(VkLabels.MANIFEST, way(LookupAsk.GET, "$EMBED_FILES/video.m3u8?expires=1900000000&srcAg=FIXTURE"))
        assertEquals(VkLabels.MANIFEST, way(LookupAsk.GET, "$API_FILES/video.mpd?expires=1900000000"))
        assertEquals(SiteWays.FILE_CHECK, way(LookupAsk.PROBE, "$API_FILES/720.mp4"))

        listOf(
            // V's reads: a file that is not a manifest; a manifest off VK's video servers.
            LookupAsk.GET to "$API_FILES/720.mp4?expires=1900000000&srcAg=FIXTURE",
            LookupAsk.GET to "$EMBED_FILES/720.mp4",
            LookupAsk.GET to "https://iv.okcdn.ru/fixture/video.m3u8",
            LookupAsk.GET to "https://cdn.example.test/fixture/video.m3u8",
            LookupAsk.POST to "$EMBED_FILES/video.m3u8",
            // Another host, scheme, user info, method or path.
            LookupAsk.GET to "https://m.vk.com/video_ext.php?oid=-100000001&id=400000001",
            LookupAsk.GET to "http://vk.com/video_ext.php?oid=-100000001&id=400000001",
            LookupAsk.GET to "https://fixture@vk.com/video_ext.php?oid=-100000001&id=400000001",
            LookupAsk.POST to embed,
            LookupAsk.GET to "https://vk.com/video-100000001_400000001",
            LookupAsk.GET to VkClient.TOKEN_URL,
            LookupAsk.POST to "https://login.vk.ru/?act=fixture",
            LookupAsk.POST to "https://login.vk.com/?act=get_anonym_token",
            LookupAsk.GET to "https://api.vk.com/method/video.get?v=5.289&videos=-100000001_400000001",
            LookupAsk.GET to "https://api.vkvideo.ru/method/users.get?v=5.289",
            LookupAsk.GET to "not an address",
        ).forEach { (method, url) -> assertNull("$method $url", way(method, url, credentials)) }
    }

    @Test
    fun `a reading is for the video its ask names - another video's entry is no data, an ask without a video reads no rows`() {
        val labels = VkLabels()
        val embed = "https://vk.com/video_ext.php?oid=-100000001&id=400000001"
        val other = "https://vk.com/video_ext.php?oid=-100000002&id=400000002"
        val bad = "https://vk.com/video_ext.php?oid=0&id=400000001"
        val body = fixture("vk-pack/embed_files.html")
        fun read(url: String, answer: String, way: String = VkLabels.EMBED) =
            labels.read(LookupAsk(LookupAsk.GET, url, null), way, ExtractorHttpResult.Success(200, answer, url, "text/html"))

        assertEquals(DATA, read(embed, body)?.mark)
        // The other video's entry has its own file: that ask reads its rows.
        assertEquals(DATA, read(other, body)?.mark)
        assertEquals(NO_DATA, read(bad, body)?.mark)
        assertEquals(NO_POST, read(bad, fixture("vk-pack/embed_adult.html"))?.mark)

        val api = VkClient.BUNDLED.videoGetUrl(LINK)
        val otherApi = VkClient.BUNDLED.videoGetUrl(VkClient.Link(-100000002, 400000002))
        val mp4 = fixture("vk-pack/video_get_mp4.json")
        fun readApi(url: String, answer: String) = labels.read(
            LookupAsk(LookupAsk.GET, url, null),
            VkLabels.VIDEO_GET,
            ExtractorHttpResult.Success(200, answer, url, "application/json"),
        )
        assertEquals(DATA, readApi(api, mp4)?.mark)
        assertEquals(DATA, readApi(VkClient.BUNDLED.videoGetUrl(LINK.copy(list = "FIXTURE")), mp4)?.mark)
        assertEquals(NO_DATA, readApi(otherApi, mp4)?.mark)
        assertEquals(BOT_CHECK, readApi(otherApi, fixture("vk-pack/video_get_captcha.json"))?.mark)

        // An answer off the way's host is not VK's answer; a file check and a manifest read nothing.
        assertEquals(
            NO_DATA,
            labels.read(
                LookupAsk(LookupAsk.GET, embed, null),
                VkLabels.EMBED,
                ExtractorHttpResult.Success(200, body, "https://cdn.example.test/video_ext.php", "text/html"),
            )?.mark,
        )
        val manifest = "$EMBED_FILES/video.m3u8"
        listOf(SiteWays.FILE_CHECK, VkLabels.MANIFEST, null).forEach { way ->
            val answer = ExtractorHttpResult.Success(200, "#EXTM3U", manifest, "application/x-mpegURL")
            assertNull(way, labels.read(LookupAsk(LookupAsk.GET, manifest, null), way, answer))
        }
    }

    /** Main's lookup in a labelled record: its answer, the record and the pages it kept. */
    private class Main(val result: SiteExtractionResult, val record: LookupRecord, val pages: MainPages) {
        /** Each ask's way and mark, in order. */
        fun labels(): List<Pair<String?, AnswerMark?>> = record.asks().map { it.way to it.mark }
    }

    /** Main's real VK adapter over [http] in a record labelled with VK's labels. */
    private suspend fun main(http: FakeExtractorHttpClient): Main {
        val record = LookupRecord(labels = VkLabels())
        val pages = MainPages(site = SITE)
        val result = withContext(record + pages) {
            VkExtractor(MainPageRecorder(LookupRecorder(http)), clockMillis = { 0L }).extract(mainRequest(identify(PAGE)))
        }
        return Main(result, record, pages)
    }

    private companion object {
        const val SITE = "vk"
        const val PAGE = "https://vk.com/video-100000001_400000001"
        const val API_FILES = "https://vk1-1.vkuser.net/fixture"
        const val EMBED_FILES = "https://vkvd1.okcdn.ru/fixture"
        const val RATE_ERROR = """{"error":{"error_code":6,"error_msg":"Fixture message"}}"""
        const val NOT_JSON_PAGE = "<!DOCTYPE html><html><body>Fixture page</body></html>"
        val LINK = VkClient.Link(-100000001, 400000001)
        val PAGE_SHAPE = Regex("^h[0-9a-f]{8}$")
        val JSON_SHAPE = Regex("^j[0-9a-f]{8}$")

        /** An `mp4_{height}` entry of the embed's key table (the video's, the trailer's, another video's). */
        val MP4_ENTRY = Regex("\"mp4_[0-9]+\":\"[^\"]*\",?")

        fun identify(url: String): SitePageIdentity = checkNotNull(VkExtractor(FakeExtractorHttpClient()).identify(url))

        /**
         * VK as one client answers it: each embed page by [embed], each token ask by [token], each
         * `video.get` by [api], each file on VK's video servers by [files] (all by their address).
         * Main's defaults: the embed's adult answer, a token, the video's files under VK's keys.
         */
        fun vk(
            embed: (String) -> ExtractorHttpResult = { page(fixture("vk-pack/embed_adult.html"), it) },
            token: (String) -> ExtractorHttpResult = { json(fixture("vk-pack/token_okay.json"), VkClient.TOKEN_URL) },
            api: (String) -> ExtractorHttpResult = answers("video_get_mp4.json"),
            files: (String) -> ExtractorHttpResult? = { null },
        ) = FakeExtractorHttpClient(
            getResponder = { url, _ ->
                val uri = URI(url)
                when {
                    uri.path == VkClient.EMBED_PATH -> embed(url)
                    uri.host == VkClient.API_HOST -> api(url)
                    VkClient.mediaFile(url) -> files(url)
                    else -> null
                }
            },
            postResponder = { url, body -> if (url == VkClient.TOKEN_URL) token(body) else null },
        )

        fun page(body: String, url: String) = FakeExtractorHttpClient.html(body, url)

        fun json(body: String, url: String) = FakeExtractorHttpClient.json(body, url)

        /** Each ask answered with the fixture [name] from its own address. */
        fun answers(name: String): (String) -> ExtractorHttpResult = { url -> json(fixture("vk-pack/$name"), url) }
    }
}
