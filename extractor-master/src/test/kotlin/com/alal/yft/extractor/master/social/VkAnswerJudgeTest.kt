package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.fixture
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** VK-Master plan §5, Appendix B step 2: VK's answers to the shared classes, final only on evidence. */
class VkAnswerJudgeTest {
    @Test
    fun `the embed's adult answer is main's trigger, the next rung, never final`() {
        val judged = VkAnswerJudge.embed(embed(fixture("vk-pack/embed_adult.html")))
        assertEquals(AnswerClass.VIDEO_WITHOUT_FILES, judged.answerClass)
        assertEquals(Next.NEXT_RUNG, judged.next)
        assertEquals("the embed's adult answer (callback 9)", judged.evidence)
        assertTrue(VkAnswerJudge.isAdult(fixture("vk-pack/embed_adult.html")))

        val russian = page("<div id=\"video_ext_msg\">Видео может содержать материалы для взрослых.</div>")
        assertTrue("VK's own adult words, without the callback", VkAnswerJudge.isAdult(russian))
        assertEquals(AnswerClass.VIDEO_WITHOUT_FILES, VkAnswerJudge.embed(embed(russian)).answerClass)

        val elsewhere = page("<script>var note = 'adult content'; embedErrorCallback(9);</script>")
        assertFalse("words and a callback without VK's message are no adult answer", VkAnswerJudge.isAdult(elsewhere))
        assertEquals(AnswerClass.SHAPE_CHANGED, VkAnswerJudge.embed(embed(elsewhere)).answerClass)
    }

    @Test
    fun `the embed's other message is deleted, private and list-only alike, in any language, never final`() {
        listOf(
            fixture("vk-pack/embed_not_found.html"),
            page("<div id=\"video_ext_msg\">Видеозапись не найдена.</div>"),
            page("<div id=\"video_ext_msg\">Fixture message in another language.</div>"),
        ).forEach { html ->
            val judged = VkAnswerJudge.embed(embed(html))
            assertEquals(AnswerClass.NEEDS_PLAYBACK, judged.answerClass)
            assertNotEquals(Next.FINAL, judged.next)
        }
    }

    @Test
    fun `VK's shell is no answer, and a page without player data a changed shape`() {
        assertEquals(AnswerClass.TRANSIENT, VkAnswerJudge.embed(embed(fixture("vk-pack/embed_shell.html"))).answerClass)
        assertEquals(AnswerClass.SHAPE_CHANGED, VkAnswerJudge.embed(embed(page("<p>fixture</p>"))).answerClass)
        assertEquals(
            AnswerClass.SHAPE_CHANGED,
            VkAnswerJudge.embed(embed(page("<p>fixture</p>"), "https://example.test/video_ext.php")).answerClass,
        )
        assertEquals(
            "another video's data",
            AnswerClass.SHAPE_CHANGED,
            VkAnswerJudge.embed(embed(page("<p>fixture</p>")), VkReading(otherVideo = true)).answerClass,
        )
        assertEquals(
            AnswerClass.SHAPE_CHANGED,
            VkAnswerJudge.embed(embed(page("<p>fixture</p>")), VkReading(item = true, refused = 2)).answerClass,
        )
    }

    @Test
    fun `rows bound to the video go to the checks, by the table directly, by shape or beside refused files partly`() {
        val answer = embed(page("<p>fixture</p>"))
        assertEquals(AnswerClass.DIRECT, VkAnswerJudge.embed(answer, VkReading(rows = 3, item = true)).answerClass)
        assertEquals(AnswerClass.PARTIAL, VkAnswerJudge.embed(answer, VkReading(rows = 1, byShape = true)).answerClass)
        assertEquals(AnswerClass.PARTIAL, VkAnswerJudge.embed(answer, VkReading(rows = 1, refused = 1)).answerClass)
        val api = api("{\"response\":{\"items\":[]}}")
        val judged = VkAnswerJudge.api(api, VkReading(rows = 4, item = true))
        assertEquals(AnswerClass.DIRECT, judged.answerClass)
        assertEquals(Next.CHECK_ROWS, judged.next)
        assertNull(judged.reason)
        assertEquals("rows win over a page signal", AnswerClass.DIRECT, VkAnswerJudge.embed(embed(fixture("vk-pack/embed_adult.html")), VkReading(rows = 1)).answerClass)
    }

    @Test
    fun `a 429 or a bot check stops the site, any other failure is no answer`() {
        val limited = VkAnswerJudge.embed(ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 429))
        assertEquals(AnswerClass.RATE_LIMITED, limited.answerClass)
        assertEquals(Next.STOP_SITE, limited.next)
        assertEquals(AnswerClass.RATE_LIMITED, VkAnswerJudge.api(ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED)).answerClass)
        assertEquals(AnswerClass.BOT_CHECK, VkAnswerJudge.api(ExtractorHttpResult.Failure(SiteExtractionFailure.BOT_CHECK, 403)).answerClass)
        listOf(404, 500, 503, null).forEach { status ->
            val judged = VkAnswerJudge.embed(ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, status))
            assertEquals(AnswerClass.TRANSIENT, judged.answerClass)
            assertEquals(Next.NEXT_RUNG, judged.next)
        }
        assertEquals(AnswerClass.TRANSIENT, VkAnswerJudge.api(ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)).answerClass)
    }

    @Test
    fun `the token answer - a usable token goes on, a refusal is a stale value, a captcha a bot check`() {
        assertNull(VkAnswerJudge.token(token(fixture("vk-pack/token_okay.json")), usable = true))
        listOf(400, 401).forEach { status ->
            assertEquals(
                AnswerClass.VALUE_REJECTED,
                VkAnswerJudge.token(ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, status), usable = false)?.answerClass,
            )
        }
        assertEquals(
            AnswerClass.TRANSIENT,
            VkAnswerJudge.token(ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 500), usable = false)?.answerClass,
        )
        assertEquals(
            AnswerClass.RATE_LIMITED,
            VkAnswerJudge.token(ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 429), usable = false)?.answerClass,
        )
        assertEquals(AnswerClass.BOT_CHECK, VkAnswerJudge.token(token(fixture("vk-pack/token_captcha.json")), usable = false)?.answerClass)
        assertEquals(
            AnswerClass.VALUE_REJECTED,
            VkAnswerJudge.token(token("{\"type\":\"error\",\"error_code\":1}"), usable = false)?.answerClass,
        )
        assertEquals(AnswerClass.VALUE_REJECTED, VkAnswerJudge.token(token("{\"error\":\"invalid_client\"}"), usable = false)?.answerClass)
        assertEquals(
            AnswerClass.SHAPE_CHANGED,
            VkAnswerJudge.token(token("{\"type\":\"okay\",\"data\":{}}"), usable = false)?.answerClass,
        )
        assertEquals(AnswerClass.SHAPE_CHANGED, VkAnswerJudge.token(token("<html></html>"), usable = false)?.answerClass)
        assertEquals(
            AnswerClass.SHAPE_CHANGED,
            VkAnswerJudge.token(FakeExtractorHttpClient.json(fixture("vk-pack/token_okay.json"), "https://example.test/"), usable = true)?.answerClass,
        )
    }

    @Test
    fun `video get's errors - the web client's values, a captcha, the rate, and every other code no answer`() {
        assertEquals(AnswerClass.VALUE_REJECTED, VkAnswerJudge.api(api(fixture("vk-pack/video_get_error_5.json"))).answerClass)
        listOf(5L, 8L, 28L, 34L, 101L).forEach { assertEquals("$it", AnswerClass.VALUE_REJECTED, VkAnswerJudge.apiError(it).answerClass) }
        assertEquals(AnswerClass.BOT_CHECK, VkAnswerJudge.apiError(14).answerClass)
        listOf(6L, 9L, 29L).forEach { assertEquals("$it", AnswerClass.RATE_LIMITED, VkAnswerJudge.apiError(it).answerClass) }
        listOf(1L, 10L, 15L, 18L, 30L, 204L, 3610L).forEach { assertEquals("$it", AnswerClass.TRANSIENT, VkAnswerJudge.apiError(it).answerClass) }
        assertEquals(AnswerClass.TRANSIENT, VkAnswerJudge.api(api("{\"error\":{\"error_msg\":\"Fixture\"}}")).answerClass)
        (0L..4000L).forEach { code -> assertNotEquals("$code", Next.FINAL, VkAnswerJudge.apiError(code).next) }
    }

    @Test
    fun `only VK's deleted icon makes a restriction final, any other is no answer`() {
        val removed = VkAnswerJudge.api(
            api(fixture("vk-pack/video_get_deleted.json")),
            VkReading(item = true, restricted = true, deleted = true),
        )
        assertEquals(AnswerClass.REMOVED, removed.answerClass)
        assertEquals(Next.FINAL, removed.next)
        assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, removed.reason)
        val unknown = VkAnswerJudge.api(api(fixture("vk-pack/video_get_deleted.json")), VkReading(item = true, restricted = true))
        assertEquals(AnswerClass.TRANSIENT, unknown.answerClass)
        assertNotEquals(Next.FINAL, unknown.next)
    }

    @Test
    fun `video get without the video's files is never final`() {
        val body = fixture("vk-pack/video_get_mp4.json")
        assertEquals(AnswerClass.VIDEO_WITHOUT_FILES, VkAnswerJudge.api(api(body), VkReading(item = true, live = true)).answerClass)
        assertEquals(AnswerClass.SHAPE_CHANGED, VkAnswerJudge.api(api(body), VkReading(otherVideo = true)).answerClass)
        assertEquals(AnswerClass.SHAPE_CHANGED, VkAnswerJudge.api(api(body), VkReading()).answerClass)
        assertEquals(AnswerClass.SHAPE_CHANGED, VkAnswerJudge.api(api(body), VkReading(item = true, refused = 4)).answerClass)
        assertEquals(AnswerClass.SHAPE_CHANGED, VkAnswerJudge.api(api(body), VkReading(item = true)).answerClass)
        assertEquals(AnswerClass.SHAPE_CHANGED, VkAnswerJudge.api(api("<html>")).answerClass)
        assertEquals(AnswerClass.SHAPE_CHANGED, VkAnswerJudge.api(FakeExtractorHttpClient.json(body, "https://example.test/x")).answerClass)
    }

    @Test
    fun `evidence names a status, a code or a marker's kind, never a link, an ID or VK's words`() {
        val answers = listOf(
            VkAnswerJudge.embed(embed(fixture("vk-pack/embed_adult.html"))),
            VkAnswerJudge.embed(embed(fixture("vk-pack/embed_not_found.html"))),
            VkAnswerJudge.embed(embed(fixture("vk-pack/embed_shell.html"))),
            VkAnswerJudge.api(api(fixture("vk-pack/video_get_error_5.json"))),
            VkAnswerJudge.api(api(fixture("vk-pack/video_get_deleted.json")), VkReading(item = true, restricted = true, deleted = true)),
            VkAnswerJudge.api(api(fixture("vk-pack/video_get_mp4.json")), VkReading(rows = 4, item = true)),
            checkNotNull(VkAnswerJudge.token(token(fixture("vk-pack/token_captcha.json")), usable = false)),
        )
        answers.forEach { judged ->
            listOf("http", "vk.com", "100000001", "400000001", "Fixture", "adult content", "not found", "captcha_sid").forEach {
                assertFalse("${judged.evidence} names $it", judged.evidence.contains(it, ignoreCase = true))
            }
        }
    }

    private fun page(body: String) = "<!DOCTYPE html><html><body class=\"video_ext\">$body</body></html>"

    private fun embed(html: String, finalUrl: String = EMBED) = FakeExtractorHttpClient.html(html, finalUrl)

    private fun api(body: String) = FakeExtractorHttpClient.json(body, API)

    private fun token(body: String) = FakeExtractorHttpClient.json(body, VkClient.TOKEN_URL)

    private companion object {
        const val EMBED = "https://vk.com/video_ext.php?oid=-100000001&id=400000001"
        const val API = "https://api.vkvideo.ru/method/video.get?v=5.289&client_id=52461373&owner_id=-100000001" +
            "&videos=-100000001_400000001&extended=1"
    }
}
