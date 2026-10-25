package com.alal.yft.master

import com.alal.yft.detection.HiddenPageReader
import com.alal.yft.detection.HiddenPageResult
import com.alal.yft.detection.MergeSupport
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageData
import com.alal.yft.extractor.api.SitePageDataSource
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.lookup.AnswerMark
import com.alal.yft.extractor.master.lookup.LookupRecord
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.social.MainAnswer
import com.alal.yft.extractor.master.social.PostItem
import com.alal.yft.extractor.master.social.SiteCensus
import com.alal.yft.extractor.master.social.SiteWays
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S0c: the site census screen's main ([SiteCensusLookup]): main's post and the
 * browser's identity for a link, main's own lookup through the app's adapter coordinator (fresh,
 * its asks in the row's record, the hidden page after a failed page read, its answer marked) and
 * the census over it. Fake clients and fixture posts only; no link of the owner's.
 */
class SiteCensusLookupTest {
    @Test
    fun `identify gives main's post with the browser's agent and the link's cookies, asking nothing`() = runTest {
        val http = FakeHttp()
        val lookup = lookup(http, cookies = { link -> if (link == X_POST) "auth_token=$COOKIE" else " " })

        val identity = checkNotNull(lookup.identify(X_POST))
        assertEquals("x", identity.page.siteId)
        assertEquals(X_ID, identity.page.contentId)
        assertEquals(AGENT, identity.userAgent)
        assertEquals("auth_token=$COOKIE", identity.cookie)
        assertFalse("no value in its text", identity.toString().contains(COOKIE))
        assertNull("a blank cookie is none", checkNotNull(lookup.identify(X_POST_2)).cookie)
        assertEquals("tiktok", lookup.identify(TT_POST)?.page?.siteId)
        assertNull("the census has no YouTube adapter", lookup.identify(YOUTUBE))
        assertNull("not a link", lookup.identify("not a link"))
        val noStore = lookup(http, cookies = { error("no cookie store") }, agent = { null })
        val bare = checkNotNull(noStore.identify(X_POST))
        assertNull(bare.cookie)
        assertNull(bare.userAgent)
        assertTrue("identify asks nothing", http.asked.isEmpty())
    }

    @Test
    fun `a lookup is main's coordinator, fresh each time, with its asks in the row's record`() = runTest {
        val http = FakeHttp(answer = { url -> X_ANSWER.takeIf { url.startsWith(WIDGET) }?.let { json(it, url) } })
        val lookup = lookup(http)
        val identity = checkNotNull(lookup.identify(X_POST))

        val first = LookupRecord()
        val answer = withContext(first) { lookup.lookup(X_POST, identity) }
        assertTrue(answer.toString(), answer is MainAnswer.Found)
        assertEquals("X's three files", 3, (answer as MainAnswer.Found).rows.size)
        assertNull("X has no hidden page", answer.hiddenPage)
        assertEquals("main's ask, in the row's record", 1, first.mainAsks)
        val second = LookupRecord()
        withContext(second) { lookup.lookup(X_POST, identity) }
        assertEquals("fresh: asked again, nothing remembered", 1, second.mainAsks)
        assertEquals(2, http.asked.size)
    }

    @Test
    fun `main's failure keeps its reason and HTTP status, and a link no adapter handles is not handled`() = runTest {
        val http = FakeHttp()
        val lookup = lookup(http)
        val identity = checkNotNull(lookup.identify(X_POST))

        val failed = lookup.lookup(X_POST, identity)
        assertTrue(failed.toString(), failed is MainAnswer.Failed)
        assertEquals("X's answer for a post its widget does not show", SiteExtractionFailure.NO_MEDIA_FOUND, (failed as MainAnswer.Failed).reason)
        assertEquals(404, failed.statusCode)
        assertNull(failed.hiddenPage)
        assertEquals(MainAnswer.NotHandled, lookup.lookup(YOUTUBE, identity))
        assertEquals(MainAnswer.NotHandled, SiteCensusLookup.answerOf(SiteAdapterOutcome.NotHandled, null))
        val status = SiteCensusLookup.answerOf(
            SiteAdapterOutcome.Failed(
                "tiktok", SiteExtractionFailure.HTTP_STATUS, "message", allowsGenericFallback = true,
                details = listOf("adapter tiktok: HTTP_STATUS", "page read: HTTP_STATUS · HTTP 500", "adapter HTTP 503"),
            ),
            AnswerMark.NO_DATA,
        ) as MainAnswer.Failed
        assertEquals("the adapter's own status line", 503, status.statusCode)
        assertEquals(AnswerMark.NO_DATA, status.hiddenPage)
    }

    @Test
    fun `TikTok's failed page read goes to the hidden page, and its answer is the row's mark`() = runTest {
        val http = FakeHttp(answer = { ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 503) })
        val pages = FakePages(
            HiddenPageResult.NotFound(SiteExtractionFailure.BOT_CHECK, "check", listOf("hidden page: a check stayed")),
            HiddenPageResult.NotFound(null, null, listOf("hidden page: no data after 20 s")),
            HiddenPageResult.NotFound(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, null, listOf("hidden page: status")),
        )
        val lookup = lookup(http, pages = pages)
        val identity = checkNotNull(lookup.identify(TT_POST))

        val checked = lookup.lookup(TT_POST, identity) as MainAnswer.Failed
        assertEquals("the page said why", SiteExtractionFailure.BOT_CHECK, checked.reason)
        assertEquals(AnswerMark.BOT_CHECK, checked.hiddenPage)
        val empty = lookup.lookup(TT_POST, identity) as MainAnswer.Failed
        assertEquals("the page read's own reason", SiteExtractionFailure.HTTP_STATUS, empty.reason)
        assertEquals(AnswerMark.NO_DATA, empty.hiddenPage)
        val gone = lookup.lookup(TT_POST, identity) as MainAnswer.Failed
        assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, gone.reason)
        assertEquals("TikTok's status for the post", AnswerMark.NO_POST, gone.hiddenPage)
        assertEquals("the post's page, once per lookup", List(3) { TT_POST }, pages.links)

        val noPage = lookup(http, pages = FakePages()).lookup(X_POST, checkNotNull(lookup.identify(X_POST)))
        assertNull("not read: no mark", noPage.hiddenPage)
        assertEquals(AnswerMark.DATA, SiteCensusLookup.markOf(found()))
        assertNull("turned off: not read", SiteCensusLookup.markOf(HiddenPageResult.Off))
    }

    @Test
    fun `0_4 - main's rows carry the post's item from its answer line, through the tapped adapters`() = runTest {
        val http = FakeHttp(answer = { url -> X_ANSWER.takeIf { url.startsWith(WIDGET) }?.let { json(it, url) } })
        val lookup = lookup(http)

        val answer = lookup.lookup(X_POST, checkNotNull(lookup.identify(X_POST)))
        assertTrue(answer.toString(), answer is MainAnswer.Found)
        assertEquals("X's answer line: video 1, 1 in the post", PostItem(1, 1), (answer as MainAnswer.Found).item)
        assertEquals(3, answer.rows.size)
        assertEquals(
            "the adapters as the app builds them",
            listOf("tiktok", "facebook", "x", "instagram", "vk"),
            SiteCensusLookup.registry(http, http) { AGENT }.adapterIds,
        )
        val detected = SiteAdapterOutcome.Detected("instagram", answer.rows)
        val carousel = SiteCensusLookup.answerOf(detected, null, listOf("item 2 of 3: 1 files (1080), DASH none"))
        assertEquals(PostItem(2, 3), (carousel as MainAnswer.Found).item)
        assertNull("no answer line: no item", (SiteCensusLookup.answerOf(detected, null) as MainAnswer.Found).item)
    }

    @Test
    fun `the census over the screen's main checks X's rows and runs Master's trial on main's answer`() = runTest {
        val http = FakeHttp(
            answer = { url -> X_ANSWER.takeIf { url.startsWith(WIDGET) }?.let { json(it, url) } },
            file = ExtractorProbeResult.Answered(206, 1_000L),
        )
        val census = SiteCensus(lookup(http), http, VALID, clock = { NOW })

        val outcome = census.run(listOf(SiteWays.X), mapOf("x" to mapOf(1 to X_POST)), null)

        assertEquals("YFT site census: X recoverable 0 of 1, Master 0", outcome.firstLine)
        val row = parse(outcome.json)["sites"][0]["rows"][0]
        assertEquals("rows", row["class"].asStringOrNull)
        assertEquals("pass", row["v"]["verdict"].asStringOrNull)
        assertEquals("trial", row["master"]["run"].asStringOrNull)
        assertEquals("main's identical answer, replayed", 1L, row["master"]["replayed"].asLongOrNull)
        assertEquals(0L, row["master"]["new"].asLongOrNull)
        assertEquals("the widget once, main's", 1, http.asked.size)
        assertEquals("V: main's tallest file, then Master's", 2, http.probed.size)
        for (text in listOf(outcome.report, outcome.json, outcome.stateJson)) {
            for (value in listOf(X_POST, X_ID, "fixture_user", "video.twimg.com")) {
                assertFalse("$value in the output", text.contains(value))
            }
        }
    }

    private fun lookup(
        http: FakeHttp,
        pages: HiddenPageReader = FakePages(),
        cookies: (String) -> String? = { null },
        agent: suspend () -> String? = { AGENT },
    ): SiteCensusLookup {
        val recorder = LookupRecorder(http)
        return SiteCensusLookup(
            registry = SiteCensusLookup.registry(recorder, recorder, agent),
            hiddenPages = pages,
            userAgent = agent,
            cookies = cookies,
            mergeSupport = { MergeSupport { true } },
            clock = { NOW },
        )
    }

    private fun parse(text: String): JsonValue = checkNotNull(BoundedJsonParser.parse(text)) { "not JSON" }

    private fun found() = HiddenPageResult.Found(
        data = SitePageData("{\"id\":\"7311234567890123456\"}", SitePageDataSource.HIDDEN_PAGE),
        finalUrl = TT_POST,
        userAgent = AGENT,
        cookie = null,
        details = emptyList(),
    )

    /** Answers [answer]'s responses (else a 404); one-byte checks only with [file]. */
    private class FakeHttp(
        private val answer: (String) -> ExtractorHttpResult? = { null },
        private val file: ExtractorProbeResult? = null,
    ) : ExtractorHttpClient {
        val asked = mutableListOf<String>()
        val probed = mutableListOf<String>()

        override suspend fun get(url: String, headers: Map<String, String>, maxBodyBytes: Long): ExtractorHttpResult {
            asked += url
            return answer(url) ?: NOT_FOUND
        }

        override suspend fun postJson(
            url: String,
            body: String,
            headers: Map<String, String>,
            maxBodyBytes: Long,
        ): ExtractorHttpResult {
            asked += url
            return answer(url) ?: NOT_FOUND
        }

        override suspend fun probe(url: String, headers: Map<String, String>, timeoutMillis: Long): ExtractorProbeResult {
            val result = file ?: return ExtractorProbeResult.Unsupported
            probed += url
            return result
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
        const val AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"
        const val COOKIE = "fixtureCookieValue"
        const val WIDGET = "https://cdn.syndication.twimg.com/tweet-result"
        const val X_ID = "1785999999999999999"
        const val X_POST = "https://x.com/fixture_user/status/$X_ID"
        const val X_POST_2 = "https://x.com/fixture_user/status/1785999999999999998"
        const val TT_POST = "https://www.tiktok.com/@fixture_user/video/7311234567890123456"
        const val YOUTUBE = "https://www.youtube.com/watch?v=aqz-KE-bpKQ"

        val NOT_FOUND = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)
        val VALID = MasterMediaValidator { candidate, _ -> ValidationResult.Valid(candidate) }

        /** extractor-sites' fixture `x/tweet_video.json` (a made-up post): X's widget answer. */
        const val X_ANSWER =
            "{\"__typename\":\"Tweet\",\"lang\":\"en\",\"favorite_count\":1520,\"possibly_sensitive\":false,\"create" +
            "d_at\":\"2024-05-02T10:15:00.000Z\",\"display_text_range\":[0,61],\"entities\":{\"hashtags\":[],\"ur" +
            "ls\":[],\"user_mentions\":[],\"symbols\":[],\"media\":[{\"display_url\":\"pic.x.com/fixture\",\"expand" +
            "ed_url\":\"https://x.com/fixture_user/status/1785999999999999999/video/1\",\"indices\":[62,85]," +
            "\"url\":\"https://t.co/fixture\"}]},\"id_str\":\"1785999999999999999\",\"text\":\"Morning run by the " +
            "river, fixture clip\\nsecond line https://t.co/fixture\",\"user\":{\"id_str\":\"1001\",\"name\":\"Fix" +
            "ture User\",\"profile_image_url_https\":\"https://pbs.twimg.com/profile_images/1/fixture_norma" +
            "l.jpg\",\"screen_name\":\"fixture_user\",\"verified\":false,\"is_blue_verified\":false,\"profile_ima" +
            "ge_shape\":\"Circle\"},\"edit_control\":{\"edit_tweet_ids\":[\"1785999999999999999\"],\"editable_unt" +
            "il_msecs\":\"1714648500000\",\"is_edit_eligible\":true,\"edits_remaining\":\"5\"},\"mediaDetails\":[{" +
            "\"display_url\":\"pic.x.com/fixture\",\"expanded_url\":\"https://x.com/fixture_user/status/178599" +
            "9999999999999/video/1\",\"ext_media_availability\":{\"status\":\"Available\"},\"indices\":[62,85],\"" +
            "media_url_https\":\"https://pbs.twimg.com/ext_tw_video_thumb/1785999990000000000/pu/img/fixt" +
            "ure.jpg\",\"original_info\":{\"height\":1280,\"width\":720,\"focus_rects\":[]},\"sizes\":{\"large\":{\"h" +
            "\":1280,\"resize\":\"fit\",\"w\":720}},\"type\":\"video\",\"url\":\"https://t.co/fixture\",\"video_info\":{" +
            "\"aspect_ratio\":[9,16],\"duration_millis\":31533,\"variants\":[{\"content_type\":\"application/x-m" +
            "pegURL\",\"url\":\"https://video.twimg.com/ext_tw_video/1785999990000000000/pu/pl/fixture.m3u8" +
            "?tag=12&v=1\"},{\"bitrate\":632000,\"content_type\":\"video/mp4\",\"url\":\"https://video.twimg.com/" +
            "ext_tw_video/1785999990000000000/pu/vid/avc1/320x568/a320.mp4?tag=12\"},{\"bitrate\":2176000," +
            "\"content_type\":\"video/mp4\",\"url\":\"https://video.twimg.com/ext_tw_video/1785999990000000000" +
            "/pu/vid/avc1/720x1280/a720.mp4?tag=12\"},{\"bitrate\":950000,\"content_type\":\"video/mp4\",\"url\"" +
            ":\"https://video.twimg.com/ext_tw_video/1785999990000000000/pu/vid/avc1/480x852/a480.mp4?ta" +
            "g=12\"}]}}],\"photos\":[],\"video\":{\"aspectRatio\":[9,16],\"contentType\":\"media_entity\",\"duratio" +
            "nMs\":31533,\"mediaAvailability\":{\"status\":\"available\"},\"poster\":\"https://pbs.twimg.com/ext_" +
            "tw_video_thumb/1785999990000000000/pu/img/fixture.jpg\",\"variants\":[{\"type\":\"application/x-" +
            "mpegURL\",\"src\":\"https://video.twimg.com/ext_tw_video/1785999990000000000/pu/pl/fixture.m3u" +
            "8?tag=12&v=1\"},{\"type\":\"video/mp4\",\"src\":\"https://video.twimg.com/ext_tw_video/17859999900" +
            "00000000/pu/vid/avc1/720x1280/a720.mp4?tag=12\"}],\"videoId\":{\"type\":\"tweet\",\"id\":\"178599999" +
            "9999999999\"},\"viewCount\":0},\"conversation_count\":12,\"news_action_type\":\"conversation\",\"isE" +
            "dited\":false,\"isStaleEdit\":false}"

        fun json(body: String, url: String) = ExtractorHttpResult.Success(200, body, url, "application/json")
    }
}
