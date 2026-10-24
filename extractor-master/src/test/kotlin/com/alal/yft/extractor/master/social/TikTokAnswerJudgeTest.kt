package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S2b: TikTok's judge, for T1's embed answer and each of main's pages T2 reads. Only a
 * photo post and DRM (as the reader states them) are final; rows bound to the item win over any
 * page signal; a 429 or a verify page stops the site; TikTok's login address is the browser's;
 * TikTok's status and an answer that names the video without a file are "needs playback", never
 * final; anything else is no evidence. The photo address check reads the address only. Synthetic
 * answers; no request.
 */
class TikTokAnswerJudgeTest {
    @Test
    fun `a failed answer - a 429 or a bot check stops the site, anything else is transient`() {
        val cases = listOf(
            ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 429) to
                Triple(AnswerClass.RATE_LIMITED, "status 429", SiteExtractionFailure.RATE_LIMITED),
            ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED) to
                Triple(AnswerClass.RATE_LIMITED, "status 429", SiteExtractionFailure.RATE_LIMITED),
            ExtractorHttpResult.Failure(SiteExtractionFailure.BOT_CHECK, 403) to
                Triple(AnswerClass.BOT_CHECK, "the client: bot check", SiteExtractionFailure.BOT_CHECK),
            ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 503) to
                Triple(AnswerClass.TRANSIENT, "failed: HTTP_STATUS 503", SiteExtractionFailure.HTTP_STATUS),
            ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK) to
                Triple(AnswerClass.TRANSIENT, "failed: NETWORK", SiteExtractionFailure.NETWORK),
        )
        for ((answer, expected) in cases) {
            val judged = TikTokAnswerJudge.judge(answer, rows = 2)
            assertEquals("$answer", expected.first, judged.answerClass)
            assertEquals("$answer", expected.second, judged.evidence)
            assertEquals("$answer", expected.third, judged.reason)
        }
        assertEquals(Next.STOP_SITE, TikTokAnswerJudge.judge(ExtractorHttpResult.Failure(SiteExtractionFailure.BOT_CHECK)).next)
        assertEquals(Next.NEXT_RUNG, TikTokAnswerJudge.judge(ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)).next)
    }

    @Test
    fun `the final address first - login is the browser's, the home page or another host is no evidence`() {
        val cases = listOf(
            "https://www.tiktok.com/login?redirect_url=fixture" to
                Pair(AnswerClass.LOGIN_WALL, "final address: login"),
            "https://www.tiktok.com/login/phone-or-email" to Pair(AnswerClass.LOGIN_WALL, "final address: login"),
            "https://www.tiktok.com/" to Pair(AnswerClass.SHAPE_CHANGED, "final address: the home page"),
            "https://www.tiktok.com/foryou?lang=en" to Pair(AnswerClass.SHAPE_CHANGED, "final address: the home page"),
            "https://example.test/@fixture_user/video/7311234567890123456" to
                Pair(AnswerClass.SHAPE_CHANGED, "final address: off TikTok"),
            "https://notiktok.com/@fixture_user/video/7311234567890123456" to
                Pair(AnswerClass.SHAPE_CHANGED, "final address: off TikTok"),
            "not an address" to Pair(AnswerClass.SHAPE_CHANGED, "final address: off TikTok"),
        )
        for ((finalUrl, expected) in cases) {
            // Even with rows read from it: the address the answer reached decides first.
            val judged = TikTokAnswerJudge.judge(page("<html>${status(10216)}</html>", finalUrl), rows = 1)
            assertEquals(finalUrl, expected.first, judged.answerClass)
            assertEquals(finalUrl, expected.second, judged.evidence)
        }
        val login = TikTokAnswerJudge.judge(page("<html></html>", "https://www.tiktok.com/login"))
        assertEquals(Next.BROWSER, login.next)
        assertEquals(SiteExtractionFailure.LOGIN_REQUIRED, login.reason)
    }

    @Test
    fun `rows bound to the item win, then the reader's DRM and photo post are final`() {
        val withStatus = page("<html>${status(10216)} verify</html>")
        val rows = TikTokAnswerJudge.judge(withStatus, rows = 3, drm = true, photo = true)
        assertEquals(AnswerClass.DIRECT, rows.answerClass)
        assertEquals("rows 3, bound to the item ID", rows.evidence)
        assertEquals(Next.CHECK_ROWS, rows.next)

        val drm = TikTokAnswerJudge.judge(withStatus, drm = true, photo = true)
        assertEquals(AnswerClass.DRM, drm.answerClass)
        assertEquals("the reader: DRM", drm.evidence)
        assertEquals(Next.FINAL, drm.next)
        assertEquals(SiteExtractionFailure.DRM_PROTECTED, drm.reason)

        val photo = TikTokAnswerJudge.judge(withStatus, photo = true)
        assertEquals(AnswerClass.PHOTO_ONLY, photo.answerClass)
        assertEquals("the reader: a photo post", photo.evidence)
        assertEquals(Next.FINAL, photo.next)
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, photo.reason)
    }

    @Test
    fun `a verify page without TikTok's data stops the site, with its data it is no bot check`() {
        listOf("Please verify to continue", "CAPTCHA", "Please wait...", "a WAF challenge").forEach { words ->
            val judged = TikTokAnswerJudge.judge(page("<html><body>$words</body></html>"))
            assertEquals(words, AnswerClass.BOT_CHECK, judged.answerClass)
            assertEquals(words, "a verify page", judged.evidence)
            assertEquals(words, Next.STOP_SITE, judged.next)
        }
        listOf("__UNIVERSAL_DATA_FOR_REHYDRATION__", "SIGI_STATE", "__FRONTITY_CONNECT_STATE__").forEach { script ->
            val body = "<html><script id=\"$script\">{}</script><body>verify your captcha</body></html>"
            assertFalse(script, TikTokAnswerJudge.botCheck(body))
            assertEquals(script, AnswerClass.SHAPE_CHANGED, TikTokAnswerJudge.judge(page(body)).answerClass)
        }
    }

    @Test
    fun `TikTok's status and an answer that names the video are needs playback, never final`() {
        val cases = listOf(
            status(10216) to "TikTok status: private or unavailable; one anonymous answer",
            status(10204, v2 = true) to "TikTok status: private or unavailable; one anonymous answer",
            "${status(0)},${status(10231)}" to "TikTok status: region; one anonymous answer",
            status(99999) to "TikTok status: another; one anonymous answer",
        )
        for ((json, evidence) in cases) {
            val judged = TikTokAnswerJudge.judge(page("<html><script id=\"SIGI_STATE\">{$json}</script></html>"))
            assertEquals(json, AnswerClass.NEEDS_PLAYBACK, judged.answerClass)
            assertEquals(json, evidence, judged.evidence)
            assertEquals(json, Next.PLAYBACK, judged.next)
            assertEquals(json, null, judged.reason)
        }
        assertEquals(null, TikTokAnswerJudge.status("{${status(0)}}"))
        assertEquals(10216L, TikTokAnswerJudge.status("{\"statusCode\" : 10216}"))

        val names = TikTokAnswerJudge.judge(page("<html>{\"id\":\"fixture\"}</html>"), namesVideo = true)
        assertEquals(AnswerClass.NEEDS_PLAYBACK, names.answerClass)
        assertEquals("names the video, no file", names.evidence)

        val none = TikTokAnswerJudge.judge(page("<html><body>This video is unavailable</body></html>"))
        assertEquals(AnswerClass.SHAPE_CHANGED, none.answerClass)
        assertEquals("no rows; no status", none.evidence)
        assertEquals(Next.NEXT_RUNG, none.next)
    }

    @Test
    fun `a photo address is read from the address alone`() {
        listOf(
            "https://www.tiktok.com/@fixture_user/photo/7311234567890123457",
            "https://m.tiktok.com/photo/7311234567890123457",
            "https://tiktok.com/@fixture_user/PHOTO/7311234567890123457?lang=en",
            " https://www.tiktok.com./@fixture_user/photo/7311234567890123457 ",
        ).forEach { assertTrue(it, TikTokLadder.photoAddress(it)) }
        listOf(
            "https://www.tiktok.com/@fixture_user/video/7311234567890123457",
            "https://www.tiktok.com/@fixture_user/photo/fixture",
            "https://www.tiktok.com/@photo/video/7311234567890123457",
            "https://example.test/@fixture_user/photo/7311234567890123457",
            "https://notiktok.com/photo/7311234567890123457",
            "https://vm.tiktok.com/ZMabcdef1/",
            "not an address",
        ).forEach { assertFalse(it, TikTokLadder.photoAddress(it)) }
    }

    /** Every evidence label is short and bare: no ID, address or page words. */
    @Test
    fun `the evidence names no ID, address or page words`() {
        val answers = listOf(
            page("<html>${status(10216)}</html>"),
            page("<html><body>Please verify 7311234567890123456</body></html>"),
            page("<html></html>", "https://www.tiktok.com/login?redirect_url=fixture"),
            page("<html></html>", "https://example.test/fixture"),
            ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404),
        )
        answers.forEach { answer ->
            val evidence = TikTokAnswerJudge.judge(answer).evidence
            listOf("7311234567890123456", "tiktok.com", "example.test", "fixture", "Please").forEach {
                assertFalse("$evidence has $it", evidence.contains(it))
            }
        }
    }

    private fun page(body: String, finalUrl: String = PAGE) = ExtractorHttpResult.Success(200, body, finalUrl, "text/html")

    private fun status(code: Long, v2: Boolean = false) = "\"statusCode${if (v2) "V2" else ""}\":$code"

    private companion object {
        const val PAGE = "https://www.tiktok.com/@fixture_user/video/7311234567890123456"
    }
}
