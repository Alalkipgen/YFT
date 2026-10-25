package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.testing.Fixtures
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S2d: Instagram's judge, for main's own answers in the lookup (the app API, GraphQL,
 * the post's page, the embed page) that I2 reads with no new ask, and (task 13) the tab-fetch
 * rung's answer by the same JSON rules. Rows of the link's item bound to the post go to the checks
 * (by shape only for a one-item post); a 429 and the rate-limit answer stop the site; 401, 403, the
 * login and checkpoint addresses, a login-required and a checkpoint answer are the browser's (main's
 * rule, §6.3); photos with no video are final (main's words); no post in an answer, the embed's
 * `EmbedIsBroken` and a page without the post's data need playback; the embed's video without
 * files is the rung's (its own class, never a shape change); never REMOVED (no answer read tells a
 * removed post apart). Task 15 (F2): a shape change says what was read, counts and marks only, and
 * a video whose files main's rule reads with no row read is a shape change on any way. Main's
 * fixtures and synthetic answers; no request.
 */
class InstagramAnswerJudgeTest {
    @Test
    fun `a failed answer - a 429 stops the site, 401 and 403 are the browser's, a JSON way's 400 a value, else transient`() {
        val cases = listOf(
            Triple(SiteWays.GRAPHQL, failed(429), Expected(AnswerClass.RATE_LIMITED, "status 429", SiteExtractionFailure.RATE_LIMITED)),
            Triple(SiteWays.PAGE, ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED), Expected(AnswerClass.RATE_LIMITED, "status 429", SiteExtractionFailure.RATE_LIMITED)),
            Triple(SiteWays.EMBED_PAGE, ExtractorHttpResult.Failure(SiteExtractionFailure.BOT_CHECK), Expected(AnswerClass.BOT_CHECK, "a bot check", SiteExtractionFailure.BOT_CHECK)),
            Triple(SiteWays.GRAPHQL, failed(401), Expected(AnswerClass.LOGIN_WALL, "status 401", SiteExtractionFailure.LOGIN_REQUIRED)),
            Triple(SiteWays.PAGE, failed(403), Expected(AnswerClass.LOGIN_WALL, "status 403", SiteExtractionFailure.LOGIN_REQUIRED)),
            Triple(SiteWays.APP_API, failed(400), Expected(AnswerClass.VALUE_REJECTED, "status 400", SiteExtractionFailure.RESPONSE_CHANGED)),
            Triple(SiteWays.GRAPHQL, failed(400), Expected(AnswerClass.VALUE_REJECTED, "status 400", SiteExtractionFailure.RESPONSE_CHANGED)),
            // Task 13: the tab-fetch rung's 400 is a rejected value too (no other value is tried).
            Triple(SiteWays.TAB_FETCH, failed(400), Expected(AnswerClass.VALUE_REJECTED, "status 400", SiteExtractionFailure.RESPONSE_CHANGED)),
            Triple(SiteWays.TAB_FETCH, failed(429), Expected(AnswerClass.RATE_LIMITED, "status 429", SiteExtractionFailure.RATE_LIMITED)),
            Triple(SiteWays.PAGE, failed(400), Expected(AnswerClass.TRANSIENT, "failed: HTTP_STATUS 400", SiteExtractionFailure.HTTP_STATUS)),
            Triple(SiteWays.PAGE, failed(404), Expected(AnswerClass.TRANSIENT, "failed: HTTP_STATUS 404", SiteExtractionFailure.HTTP_STATUS)),
            Triple(SiteWays.EMBED_PAGE, failed(503), Expected(AnswerClass.TRANSIENT, "failed: HTTP_STATUS 503", SiteExtractionFailure.HTTP_STATUS)),
            Triple(SiteWays.GRAPHQL, ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK), Expected(AnswerClass.TRANSIENT, "failed: NETWORK", SiteExtractionFailure.NETWORK)),
        )
        for ((way, answer, expected) in cases) {
            // Rows never come with a failed answer; even so, the failure decides.
            expected.assert("$way $answer", InstagramAnswerJudge.judge(way, answer, InstagramReading(rows = 2, post = true)))
        }
        assertEquals(Next.STOP_SITE, InstagramAnswerJudge.judge(SiteWays.GRAPHQL, failed(429)).next)
        assertEquals(Next.BROWSER, InstagramAnswerJudge.judge(SiteWays.GRAPHQL, failed(401)).next)
        assertEquals(Next.NEXT_RUNG, InstagramAnswerJudge.judge(SiteWays.PAGE, failed(404)).next)
    }

    @Test
    fun `the final address - off Instagram's host is no evidence, the login page and the checkpoint a wall`() {
        val cases = listOf(
            "https://evil.example.test/p/$REEL/" to Expected(AnswerClass.SHAPE_CHANGED, "final address: off Instagram's host", SiteExtractionFailure.RESPONSE_CHANGED),
            "http://www.instagram.com/p/$REEL/" to Expected(AnswerClass.SHAPE_CHANGED, "final address: off Instagram's host", SiteExtractionFailure.RESPONSE_CHANGED),
            "https://instagram.com.evil.test/p/$REEL/" to Expected(AnswerClass.SHAPE_CHANGED, "final address: off Instagram's host", SiteExtractionFailure.RESPONSE_CHANGED),
            "not a link" to Expected(AnswerClass.SHAPE_CHANGED, "final address: off Instagram's host", SiteExtractionFailure.RESPONSE_CHANGED),
            "https://www.instagram.com/accounts/login/" to Expected(AnswerClass.LOGIN_WALL, "final address: login", SiteExtractionFailure.LOGIN_REQUIRED),
            "https://WWW.Instagram.com./Accounts/Login/" to Expected(AnswerClass.LOGIN_WALL, "final address: login", SiteExtractionFailure.LOGIN_REQUIRED),
            "https://www.instagram.com/challenge/" to Expected(AnswerClass.LOGIN_WALL, "final address: checkpoint", SiteExtractionFailure.LOGIN_REQUIRED),
        )
        for ((url, expected) in cases) {
            // Even with rows read: an answer off the host or on a wall is never read for rows.
            for (way in SiteWays.INSTAGRAM.mainWays) {
                expected.assert("$way $url", InstagramAnswerJudge.judge(way, answer(GRAPHQL_REEL, url), InstagramReading(rows = 3, post = true)))
            }
        }
        // Main's rule and §6.3's: the checkpoint is a sign-in wall, the browser's (no stop).
        assertEquals(Next.BROWSER, InstagramAnswerJudge.judge(SiteWays.PAGE, answer("", "https://www.instagram.com/challenge/")).next)
    }

    @Test
    fun `rows of the link's item go to the checks, by shape only for one item, never for another post`() {
        val direct = InstagramAnswerJudge.judge(SiteWays.GRAPHQL, answer(GRAPHQL_REEL), InstagramReading(rows = 3, post = true, videoTyped = true))
        assertEquals(AnswerClass.DIRECT, direct.answerClass)
        assertEquals("rows 3 of the link's item, bound to the post", direct.evidence)
        assertEquals(Next.CHECK_ROWS, direct.next)
        assertNull(direct.reason)

        val partial = InstagramAnswerJudge.judge(SiteWays.PAGE, answer("<html></html>"), InstagramReading(rows = 2, byShape = true, post = true))
        assertEquals(AnswerClass.PARTIAL, partial.answerClass)
        assertEquals("rows 2 by shape (a renamed key), one item", partial.evidence)
        assertEquals(Next.CHECK_ROWS, partial.next)

        // A carousel's rows by shape: its item unknown.
        val carousel = InstagramAnswerJudge.judge(
            SiteWays.GRAPHQL, answer(GRAPHQL_CAROUSEL), InstagramReading(rows = 2, byShape = true, carousel = true, post = true),
        )
        assertEquals(AnswerClass.SHAPE_CHANGED, carousel.answerClass)
        assertEquals("rows by shape in a carousel: the link's item unknown", carousel.evidence)
        // A carousel's rows by the key table: the link's item, as main.
        assertEquals(
            AnswerClass.DIRECT,
            InstagramAnswerJudge.judge(SiteWays.GRAPHQL, answer(GRAPHQL_CAROUSEL), InstagramReading(rows = 1, carousel = true, post = true)).answerClass,
        )

        // Another post's answer: no rows, whatever was read.
        val other = InstagramAnswerJudge.judge(SiteWays.APP_API, answer(API_REEL), InstagramReading(rows = 3, otherPost = true))
        assertEquals(AnswerClass.SHAPE_CHANGED, other.answerClass)
        assertEquals("names another post", other.evidence)
        assertEquals(Next.NEXT_RUNG, other.next)
    }

    @Test
    fun `a JSON answer - the rate-limit words, the checkpoint, login required, photos, errors, no post, no files`() {
        val noVideo = """{"data":{"xdt_shortcode_media":{"shortcode":"$REEL"}}}"""
        val cases = listOf(
            // Main's fixture: the rate-limit answer also says require_login; it stops first.
            Triple(Fixtures.read("instagram/graphql_login.json"), InstagramReading(), Expected(AnswerClass.RATE_LIMITED, "a rate-limit answer", SiteExtractionFailure.RATE_LIMITED)),
            Triple("""{"message":"checkpoint_required","status":"fail"}""", InstagramReading(), Expected(AnswerClass.LOGIN_WALL, "the answer: checkpoint", SiteExtractionFailure.LOGIN_REQUIRED)),
            Triple("""{"require_login":true,"status":"fail"}""", InstagramReading(), Expected(AnswerClass.LOGIN_WALL, "the answer: login required", SiteExtractionFailure.LOGIN_REQUIRED)),
            Triple("""{"message":"login_required","status":"fail"}""", InstagramReading(), Expected(AnswerClass.LOGIN_WALL, "the answer: login required", SiteExtractionFailure.LOGIN_REQUIRED)),
            Triple(Fixtures.read("instagram/graphql_photo.json"), InstagramReading(post = true, photos = true), Expected(AnswerClass.PHOTO_ONLY, "photos only, no video", SiteExtractionFailure.NO_MEDIA_FOUND)),
            // Photos marked, but a video by its type: never final. Task 15 (F2): a shape change says
            // what was read, counts and marks only (the answer's size, the post, a video mark, files).
            Triple(
                GRAPHQL_CAROUSEL,
                InstagramReading(post = true, photos = true, videoTyped = true, carousel = true),
                Expected(
                    AnswerClass.SHAPE_CHANGED,
                    "a video, no files read; the answer: ${GRAPHQL_CAROUSEL.length} characters; the post: yes; a video mark: yes; files: 0",
                    SiteExtractionFailure.RESPONSE_CHANGED,
                ),
            ),
            Triple("""{"errors":[{"message":"fixture"}],"status":"fail"}""", InstagramReading(), Expected(AnswerClass.VALUE_REJECTED, "the answer: errors", SiteExtractionFailure.RESPONSE_CHANGED)),
            Triple(Fixtures.read("instagram/graphql_null.json"), InstagramReading(), Expected(AnswerClass.NEEDS_PLAYBACK, "no post in the answer: removed, private or gated alike", null)),
            Triple(
                GRAPHQL_REEL,
                InstagramReading(post = true, videoTyped = true),
                Expected(
                    AnswerClass.SHAPE_CHANGED,
                    "a video, no files read; the answer: ${GRAPHQL_REEL.length} characters; the post: yes; a video mark: yes; files: 0",
                    SiteExtractionFailure.RESPONSE_CHANGED,
                ),
            ),
            // Task 15 (F2): main's rule read the post's files, the recipe no row: the recipe's shape.
            Triple(
                GRAPHQL_REEL,
                InstagramReading(post = true, videoTyped = true, files = 2),
                Expected(
                    AnswerClass.SHAPE_CHANGED,
                    "a video with files, no rows read; the answer: ${GRAPHQL_REEL.length} characters; the post: yes; a video mark: yes; files: 2",
                    SiteExtractionFailure.RESPONSE_CHANGED,
                ),
            ),
            Triple(
                noVideo,
                InstagramReading(post = true),
                Expected(
                    AnswerClass.SHAPE_CHANGED,
                    "no video read; the answer: ${noVideo.length} characters; the post: yes; a video mark: no; files: 0",
                    SiteExtractionFailure.RESPONSE_CHANGED,
                ),
            ),
            Triple(
                "<html><body>fixture</body></html>",
                InstagramReading(),
                Expected(AnswerClass.SHAPE_CHANGED, "not a JSON object; the answer: 33 characters; the post: no; a video mark: no; files: 0", SiteExtractionFailure.RESPONSE_CHANGED),
            ),
            Triple(
                "[1,2]",
                InstagramReading(),
                Expected(AnswerClass.SHAPE_CHANGED, "not a JSON object; the answer: 5 characters; the post: no; a video mark: no; files: 0", SiteExtractionFailure.RESPONSE_CHANGED),
            ),
        )
        for ((body, reading, expected) in cases) {
            for (way in InstagramAnswerJudge.JSON_WAYS) {
                expected.assert("$way ${body.take(40)}", InstagramAnswerJudge.judge(way, answer(body), reading))
            }
        }
        assertEquals(Next.FINAL, InstagramAnswerJudge.judge(SiteWays.GRAPHQL, answer(Fixtures.read("instagram/graphql_photo.json")), InstagramReading(post = true, photos = true)).next)
        assertEquals(Next.PLAYBACK, InstagramAnswerJudge.judge(SiteWays.GRAPHQL, answer(Fixtures.read("instagram/graphql_null.json"))).next)
    }

    @Test
    fun `a page - photos, the embed's EmbedIsBroken, a video without files, no post data`() {
        val embedBroken = Fixtures.read("instagram/embed_no_video.html")
        val cases = listOf(
            Triple("<html></html>", InstagramReading(post = true, photos = true), Expected(AnswerClass.PHOTO_ONLY, "photos only, no video", SiteExtractionFailure.NO_MEDIA_FOUND)),
            Triple(embedBroken, InstagramReading(), Expected(AnswerClass.NEEDS_PLAYBACK, "not embeddable: removed, private or not embeddable alike", null)),
            // Task 15 (F2): a shape change says what was read, counts and marks only (the page's
            // size, its JSON documents, main's post, a video mark, the post's files): main's post
            // with no video read.
            Triple(
                "<html></html>",
                InstagramReading(post = true, documents = 1, rulePost = true),
                Expected(
                    AnswerClass.SHAPE_CHANGED,
                    "no video read; the page: 13 characters; documents: 1; main's post: yes; a video mark: no; files: 0",
                    SiteExtractionFailure.RESPONSE_CHANGED,
                ),
            ),
            // L3 (the retest of 2026-10-23): a node names the post's code with no data (no video
            // mark, no file) and main's rule finds no post: main's "no post", never a shape change;
            // the line still says what was read.
            Triple(
                "<html></html>",
                InstagramReading(post = true),
                Expected(
                    AnswerClass.NEEDS_PLAYBACK,
                    "no post data in the page, as main's rule reads it; the page: 13 characters; documents: 0; main's post: no; a video mark: no; files: 0",
                    null,
                ),
            ),
            // Main's rule read the post's files, the recipe no row: the recipe's shape, on either page.
            Triple(
                "<html></html>",
                InstagramReading(post = true, videoTyped = true, documents = 2, rulePost = true, files = 1),
                Expected(
                    AnswerClass.SHAPE_CHANGED,
                    "a video with files, no rows read; the page: 13 characters; documents: 2; main's post: yes; a video mark: yes; files: 1",
                    SiteExtractionFailure.RESPONSE_CHANGED,
                ),
            ),
            Triple("<html><body>fixture</body></html>", InstagramReading(), Expected(AnswerClass.NEEDS_PLAYBACK, "no post data in the page", null)),
        )
        for ((body, reading, expected) in cases) {
            for (way in listOf(SiteWays.PAGE, SiteWays.EMBED_PAGE)) {
                expected.assert("$way ${body.take(40)}", InstagramAnswerJudge.judge(way, answer(body), reading))
            }
        }

        // Task 13 (observation d): a video without files. The embed page signed out lists none by
        // its design: the tab-fetch rung's, its own class (W never flags it); the post's page: a
        // shape change, as before (task 15: with what was read).
        val video = InstagramReading(post = true, videoTyped = true, documents = 1, rulePost = true)
        val embed = InstagramAnswerJudge.judge(SiteWays.EMBED_PAGE, answer("<html></html>"), video)
        Expected(AnswerClass.VIDEO_WITHOUT_FILES, "a video, no files read", null).assert("embed", embed)
        assertEquals(Next.NEXT_RUNG, embed.next)
        Expected(
            AnswerClass.SHAPE_CHANGED,
            "a video, no files read; the page: 13 characters; documents: 1; main's post: yes; a video mark: yes; files: 0",
            SiteExtractionFailure.RESPONSE_CHANGED,
        ).assert("page", InstagramAnswerJudge.judge(SiteWays.PAGE, answer("<html></html>"), video))
        // A broken embed and photos come first.
        assertEquals(
            AnswerClass.NEEDS_PLAYBACK,
            InstagramAnswerJudge.judge(SiteWays.EMBED_PAGE, answer(embedBroken), video).answerClass,
        )
    }

    @Test
    fun `never REMOVED or PRIVATE - no answer read tells a removed post apart`() {
        val bodies = listOf(
            GRAPHQL_REEL, API_REEL, GRAPHQL_CAROUSEL, Fixtures.read("instagram/graphql_login.json"),
            Fixtures.read("instagram/graphql_null.json"), Fixtures.read("instagram/graphql_photo.json"),
            Fixtures.read("instagram/embed_no_video.html"), Fixtures.read("instagram/embed_captioned.html"),
            Fixtures.read("instagram/page_signed_in.html"), "", "{}", "null",
        )
        val readings = listOf(
            InstagramReading(), InstagramReading(post = true), InstagramReading(otherPost = true),
            InstagramReading(post = true, photos = true), InstagramReading(post = true, videoTyped = true),
        )
        val final = setOf(AnswerClass.REMOVED, AnswerClass.PRIVATE, AnswerClass.REGION_LIMITED, AnswerClass.DRM)
        for (way in SiteWays.INSTAGRAM.mainWays) {
            for (status in listOf(400, 401, 403, 404, 410, 429, 500)) {
                assertFalse("$way $status", InstagramAnswerJudge.judge(way, failed(status)).answerClass in final)
            }
            for (body in bodies) {
                for (reading in readings) {
                    val judged = InstagramAnswerJudge.judge(way, answer(body), reading)
                    assertFalse("$way ${body.take(30)} $reading", judged.answerClass in final)
                    if (judged.next == Next.FINAL) assertEquals(AnswerClass.PHOTO_ONLY, judged.answerClass)
                }
            }
        }
    }

    @Test
    fun `one answer for the lookup - rows first, a photo post only when no answer shows a video, then the strongest stop`() {
        val direct = judged(AnswerClass.DIRECT, "rows 3 of the link's item, bound to the post")
        val partial = judged(AnswerClass.PARTIAL, "rows 2 by shape (a renamed key), one item")
        val photos = Judged(AnswerClass.PHOTO_ONLY, SiteExtractionFailure.NO_MEDIA_FOUND, "photos only, no video")
        val limited = Judged(AnswerClass.RATE_LIMITED, SiteExtractionFailure.RATE_LIMITED, "status 429")
        val wall = Judged(AnswerClass.LOGIN_WALL, SiteExtractionFailure.LOGIN_REQUIRED, "status 401")
        val playback = judged(AnswerClass.NEEDS_PLAYBACK, "no post data in the page")
        val rejected = Judged(AnswerClass.VALUE_REJECTED, SiteExtractionFailure.RESPONSE_CHANGED, "status 400")
        val changed = Judged(AnswerClass.SHAPE_CHANGED, SiteExtractionFailure.RESPONSE_CHANGED, "no video read")
        val transient = Judged(AnswerClass.TRANSIENT, SiteExtractionFailure.NETWORK, "failed: NETWORK")
        val bare = judged(AnswerClass.VIDEO_WITHOUT_FILES, "a video, no files read")
        val none = InstagramReading()

        assertNull(InstagramAnswerJudge.aggregate(emptyList(), emptyList()))
        // Rows win, the first bound to the post before any by shape, in main's order.
        assertEquals(
            SiteWays.PAGE to direct,
            InstagramAnswerJudge.aggregate(
                listOf(SiteWays.GRAPHQL to limited, SiteWays.GRAPHQL to partial, SiteWays.PAGE to direct, SiteWays.EMBED_PAGE to direct),
                List(4) { none },
            ),
        )
        assertEquals(
            SiteWays.EMBED_PAGE to partial,
            InstagramAnswerJudge.aggregate(listOf(SiteWays.GRAPHQL to limited, SiteWays.EMBED_PAGE to partial), List(2) { none }),
        )
        // A photo post: final only when no answer shows a video.
        val photoReading = InstagramReading(post = true, photos = true)
        assertEquals(
            SiteWays.GRAPHQL to photos,
            InstagramAnswerJudge.aggregate(listOf(SiteWays.GRAPHQL to photos, SiteWays.PAGE to wall), listOf(photoReading, none)),
        )
        val disagree = checkNotNull(
            InstagramAnswerJudge.aggregate(
                listOf(SiteWays.GRAPHQL to photos, SiteWays.PAGE to changed),
                listOf(photoReading, InstagramReading(post = true, videoTyped = true)),
            ),
        )
        assertEquals(SiteWays.GRAPHQL, disagree.first)
        assertEquals(AnswerClass.SHAPE_CHANGED, disagree.second.answerClass)
        assertEquals("the answers disagree: photos in one, a video in another", disagree.second.evidence)
        // The strongest stop, whatever main's order.
        val stops = listOf(
            listOf(SiteWays.GRAPHQL to transient, SiteWays.PAGE to playback, SiteWays.EMBED_PAGE to limited) to (SiteWays.EMBED_PAGE to limited),
            listOf(SiteWays.GRAPHQL to playback, SiteWays.PAGE to wall) to (SiteWays.PAGE to wall),
            listOf(SiteWays.GRAPHQL to changed, SiteWays.PAGE to rejected) to (SiteWays.PAGE to rejected),
            listOf(SiteWays.GRAPHQL to transient, SiteWays.EMBED_PAGE to changed) to (SiteWays.EMBED_PAGE to changed),
            listOf(SiteWays.GRAPHQL to transient, SiteWays.EMBED_PAGE to playback) to (SiteWays.EMBED_PAGE to playback),
            // Task 13: the embed's video without files, after the walls, before every other stop.
            listOf(SiteWays.GRAPHQL to wall, SiteWays.EMBED_PAGE to bare) to (SiteWays.GRAPHQL to wall),
            listOf(SiteWays.GRAPHQL to playback, SiteWays.PAGE to changed, SiteWays.EMBED_PAGE to bare) to (SiteWays.EMBED_PAGE to bare),
            listOf(SiteWays.GRAPHQL to rejected, SiteWays.PAGE to transient, SiteWays.EMBED_PAGE to bare) to (SiteWays.EMBED_PAGE to bare),
            listOf(SiteWays.GRAPHQL to transient) to (SiteWays.GRAPHQL to transient),
        )
        for ((judged, expected) in stops) {
            assertEquals("$judged", expected, InstagramAnswerJudge.aggregate(judged, judged.map { none }))
        }
    }

    @Test
    fun `a carousel by its items' keys or type, Instagram's own host by address`() {
        assertTrue(InstagramAnswerJudge.carousel(GRAPHQL_CAROUSEL))
        assertTrue(InstagramAnswerJudge.carousel("""{"items":[{"carousel_media_count":2}]}"""))
        assertTrue(InstagramAnswerJudge.carousel("""{\"carousel_media\":[]}"""))
        assertTrue(InstagramAnswerJudge.carousel("""{"media_type":8}"""))
        assertTrue(InstagramAnswerJudge.carousel("""{\"media_type\": 8}"""))
        assertFalse(InstagramAnswerJudge.carousel(GRAPHQL_REEL))
        assertFalse(InstagramAnswerJudge.carousel(API_REEL))
        assertFalse(InstagramAnswerJudge.carousel("""{"media_type":80}"""))

        listOf("https://www.instagram.com/p/x/", "https://instagram.com/", "https://m.instagram.com/reel/x/", "https://WWW.INSTAGRAM.COM./")
            .forEach { assertTrue(it, InstagramAnswerJudge.instagramHost(URI(it))) }
        listOf("http://www.instagram.com/", "https://instagram.com.evil.test/", "https://cdninstagram.com/", "https://evil.test/www.instagram.com")
            .forEach { assertFalse(it, InstagramAnswerJudge.instagramHost(URI(it))) }
    }

    private class Expected(val answerClass: AnswerClass, val evidence: String, val reason: SiteExtractionFailure?) {
        fun assert(case: String, judged: Judged) {
            assertEquals(case, answerClass, judged.answerClass)
            assertEquals(case, evidence, judged.evidence)
            assertEquals(case, reason, judged.reason)
        }
    }

    private companion object {
        const val REEL = "C9fixtREEL1"
        val GRAPHQL_REEL = Fixtures.read("instagram/graphql_reel.json")
        val API_REEL = Fixtures.read("instagram/api_info_reel.json")
        val GRAPHQL_CAROUSEL = Fixtures.read("instagram/graphql_carousel.json")

        fun answer(body: String, url: String = "https://www.instagram.com/p/$REEL/") =
            ExtractorHttpResult.Success(200, body, url, "application/json")

        fun failed(status: Int) = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, status)

        fun judged(answerClass: AnswerClass, evidence: String) = Judged(answerClass, null, evidence)
    }
}
