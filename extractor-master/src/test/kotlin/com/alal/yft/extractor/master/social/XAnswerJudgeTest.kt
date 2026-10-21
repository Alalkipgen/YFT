package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S2c: X's judge, for main's answer from X's widget that X's ladder replays. Only the
 * widget's 404 (no such post) and a photo post are final; rows bound to the post ID win; an empty
 * tombstone (the age-limited post's) is the browser's; a tombstone with X's notice link (a deleted
 * post's and a protected account's post's alike, live 2026-10-21) and any other tombstone need
 * playback, never final; `{}` (X's answer without the widget's token) is a rejected value; a 429
 * stops the site; another host, another post and an unknown shape are no evidence. By shape only:
 * the words never decide. Synthetic answers in the live shapes; no request.
 */
class XAnswerJudgeTest {
    @Test
    fun `a failed answer - a 404 is no such post, a 429 stops the site, anything else is transient`() {
        val cases = listOf(
            ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404) to
                Triple(AnswerClass.REMOVED, "status 404: no such post", SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE),
            ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 429) to
                Triple(AnswerClass.RATE_LIMITED, "status 429", SiteExtractionFailure.RATE_LIMITED),
            ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED) to
                Triple(AnswerClass.RATE_LIMITED, "status 429", SiteExtractionFailure.RATE_LIMITED),
            ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 403) to
                Triple(AnswerClass.TRANSIENT, "failed: HTTP_STATUS 403", SiteExtractionFailure.HTTP_STATUS),
            ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 503) to
                Triple(AnswerClass.TRANSIENT, "failed: HTTP_STATUS 503", SiteExtractionFailure.HTTP_STATUS),
            ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK) to
                Triple(AnswerClass.TRANSIENT, "failed: NETWORK", SiteExtractionFailure.NETWORK),
        )
        for ((answer, expected) in cases) {
            // Rows never come with a failed answer; even so, the failure decides.
            val judged = XAnswerJudge.judge(answer, XReading(rows = 2))
            assertEquals("$answer", expected.first, judged.answerClass)
            assertEquals("$answer", expected.second, judged.evidence)
            assertEquals("$answer", expected.third, judged.reason)
        }
        assertEquals(Next.FINAL, XAnswerJudge.judge(ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 404)).next)
        assertEquals(Next.STOP_SITE, XAnswerJudge.judge(ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED)).next)
        assertEquals(Next.NEXT_RUNG, XAnswerJudge.judge(ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK)).next)
    }

    @Test
    fun `rows bound to the post ID go to the checks, by shape only as one item's, after the host`() {
        val direct = XAnswerJudge.judge(answer(VIDEO), XReading(rows = 3))
        assertEquals(AnswerClass.DIRECT, direct.answerClass)
        assertEquals("rows 3, bound to the post ID", direct.evidence)
        assertEquals(Next.CHECK_ROWS, direct.next)
        assertNull(direct.reason)

        val whole = XAnswerJudge.judge(answer(VIDEO), XReading(rows = 2, byShape = true))
        assertEquals(AnswerClass.PARTIAL, whole.answerClass)
        assertEquals("rows 2, main's whole answer read, one item", whole.evidence)
        assertEquals(Next.CHECK_ROWS, whole.next)

        // The widget's host over HTTPS only: rows read elsewhere are no evidence.
        for (finalUrl in listOf("https://example.test/tweet-result?id=1", "http://$HOST/tweet-result?id=1", "not an address")) {
            val judged = XAnswerJudge.judge(answer(VIDEO, finalUrl), XReading(rows = 3))
            assertEquals(finalUrl, AnswerClass.SHAPE_CHANGED, judged.answerClass)
            assertEquals(finalUrl, "final address: off the widget's host", judged.evidence)
            assertEquals(finalUrl, Next.NEXT_RUNG, judged.next)
        }
        assertTrue(XAnswerJudge.widgetHost("https://CDN.Syndication.Twimg.com./tweet-result?id=1"))
        assertFalse(XAnswerJudge.widgetHost("https://cdn.syndication.twimg.com.example.test/tweet-result"))
        // Another post's answer: never read for this one.
        val other = XAnswerJudge.judge(answer(VIDEO), XReading(otherPost = true))
        assertEquals(AnswerClass.SHAPE_CHANGED, other.answerClass)
        assertEquals("names another post", other.evidence)
    }

    @Test
    fun `the tombstones - empty is age-limited, X's notice and any other need playback, never final`() {
        val age = XAnswerJudge.judge(answer(EMPTY_TOMBSTONE))
        assertEquals(AnswerClass.AGE_LIMITED, age.answerClass)
        assertEquals("an empty tombstone", age.evidence)
        assertEquals(Next.BROWSER, age.next)

        // A deleted post's and a protected account's post's answers: the same shape and link.
        for (body in listOf(DELETED, PROTECTED, notice("https://help.twitter.com/en/rules-and-policies/notices-on-twitter"))) {
            val judged = XAnswerJudge.judge(answer(body))
            assertEquals(body, AnswerClass.NEEDS_PLAYBACK, judged.answerClass)
            assertEquals(body, "a tombstone with X's notice: removed, protected or suspended alike", judged.evidence)
            assertEquals(body, Next.PLAYBACK, judged.next)
            assertNull(body, judged.reason) // today's reason stays
        }
        // Main's fixture (the words of an age limit, no link) and a link elsewhere: no notice; the
        // words never decide.
        val other = listOf(
            Fixtures.read("x/tweet_tombstone.json"),
            notice("https://example.test/rules-and-policies/notices-on-x"),
            notice("https://help.x.com/en/using-x/fixture"),
            """{"tombstone":{"text":{"text":"This Post was deleted by the Post author."}}}""",
        )
        for (body in other) {
            val judged = XAnswerJudge.judge(answer(body))
            assertEquals(body, AnswerClass.NEEDS_PLAYBACK, judged.answerClass)
            assertEquals(body, "a tombstone without X's notice", judged.evidence)
            assertEquals(body, Next.PLAYBACK, judged.next)
        }
    }

    @Test
    fun `the empty answer is a rejected value, a photo post is final, a playlist without a length needs playback`() {
        val empty = XAnswerJudge.judge(answer(" {} "))
        assertEquals(AnswerClass.VALUE_REJECTED, empty.answerClass)
        assertEquals("an empty answer, as without the widget's check value", empty.evidence)
        assertEquals(Next.NEXT_RUNG, empty.next)

        val photos = XAnswerJudge.judge(answer(Fixtures.read("x/tweet_photos.json")), XReading(photos = true))
        assertEquals(AnswerClass.PHOTO_ONLY, photos.answerClass)
        assertEquals("photos only, no video", photos.evidence)
        assertEquals(Next.FINAL, photos.next)
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, photos.reason)

        val playlist = XAnswerJudge.judge(answer(VIDEO), XReading(playlistOnly = true))
        assertEquals(AnswerClass.NEEDS_PLAYBACK, playlist.answerClass)
        assertEquals("the item's playlist only, no length", playlist.evidence)
    }

    @Test
    fun `no evidence - not JSON, not an object, rows by shape for two items or none, no video`() {
        val cases = listOf(
            answer("<html>no</html>") to Pair(XReading(), "not a JSON object"),
            answer("[1,2]") to Pair(XReading(), "not a JSON object"),
            answer(VIDEO) to Pair(XReading(shapeItems = 2), "rows by shape for 2 items, not one"),
            answer(VIDEO) to Pair(XReading(shapeItems = 1), "rows by shape, no item named"),
            answer(VIDEO) to Pair(XReading(), "no video; no tombstone"),
        )
        for ((answer, expected) in cases) {
            val judged = XAnswerJudge.judge(answer, expected.first)
            assertEquals(expected.second, AnswerClass.SHAPE_CHANGED, judged.answerClass)
            assertEquals(expected.second, judged.evidence)
            assertEquals(Next.NEXT_RUNG, judged.next)
            assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, judged.reason)
        }
    }

    @Test
    fun `the notice link is found within a bound and only as a link`() {
        val deep = (1..10).fold("\"https://help.x.com/rules-and-policies/notices-on-x\"") { inner, _ -> "{\"a\":$inner}" }
        assertFalse(XAnswerJudge.judge(answer("{\"tombstone\":$deep}")).evidence.contains("notice:"))
        // The live shape: the link six levels into the tombstone (the walk's bound is eight).
        assertTrue(XAnswerJudge.notice(BoundedJsonParser.parse(DELETED)!!["tombstone"]!!))
        // The words "notices-on-x" in a sentence are not a link.
        assertFalse(
            XAnswerJudge.notice(
                BoundedJsonParser.parse("""{"text":"see notices-on-x"}""")!!,
            ),
        )
    }

    private fun answer(body: String, finalUrl: String = "https://$HOST/tweet-result?id=$POST&token=fixture&lang=en") =
        ExtractorHttpResult.Success(200, body, finalUrl, "application/json")

    private companion object {
        const val HOST = "cdn.syndication.twimg.com"
        const val POST = "1785999999999999999"
        val VIDEO: String by lazy { Fixtures.read("x/tweet_video.json") }
        const val EMPTY_TOMBSTONE = """{"__typename":"TweetTombstone","tombstone":{}}"""

        /** The live shape of a tombstone with X's notice (2026-10-21), the words made up. */
        fun notice(link: String, words: String = "This Post is unavailable. Learn more") =
            """{"__typename":"TweetTombstone","tombstone":{"text":{"text":"$words","entities":[""" +
                """{"from_index":26,"to_index":36,"ref":{"__typename":"TimelineUrl","url":"$link",""" +
                """"url_type":"ExternalUrl"}}],"rtl":false}}}"""

        val DELETED = notice("https://help.x.com/rules-and-policies/notices-on-x", "This Post was deleted. Learn more")
        val PROTECTED = notice(
            "https://help.x.com/rules-and-policies/notices-on-x",
            "This account owner limits who can view their Posts. Learn more",
        )
    }
}
