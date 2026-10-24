package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.policy.TerminalRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S1a: the answer judge and Facebook's rules. Every page here is synthetic: the
 * route's structure §7.3 item 0.3 found (made-up IDs, signed out), with words of the test's own.
 */
class SiteAnswerJudgeTest {
    private val judged = mutableListOf<Judged>()

    private fun judge(
        answer: ExtractorHttpResult,
        rows: Int = 0,
        fewerHeights: Boolean = false,
        needsPlayback: Boolean = false,
        drm: Boolean = false,
    ): Judged = FacebookAnswerJudge.judge(answer, rows, fewerHeights, needsPlayback, drm).also(judged::add)

    @Test
    fun `the unavailable page is PRIVATE and final by its route, in English and in Burmese alike`() {
        for (title in listOf(ENGLISH, BURMESE)) {
            val result = judge(ok(page(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = title)))
            assertEquals(title, AnswerClass.PRIVATE, result.answerClass)
            assertEquals(Next.FINAL, result.next)
            assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, result.reason)
            assertEquals("route: error (privacy)", result.evidence)
        }
        assertEquals(
            FacebookAnswerJudge.Route(FacebookAnswerJudge.ERROR_ROUTE, privacy = true),
            FacebookAnswerJudge.route(page(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = BURMESE)),
        )
        assertEveryEvidenceClean()
    }

    @Test
    fun `the video's not-found route is REMOVED and final`() {
        val result = judge(ok(page(FacebookAnswerJudge.NOT_FOUND_ROUTE, title = "Popular videos")))
        assertEquals(AnswerClass.REMOVED, result.answerClass)
        assertEquals(Next.FINAL, result.next)
        assertEquals(SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE, result.reason)
        assertEquals("route: video not found", result.evidence)
        assertEveryEvidenceClean()
    }

    @Test
    fun `words, a broken or doubled route, the reel's null video and a login form are no evidence`() {
        val error = page(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = ENGLISH)
        val cases = mapOf(
            "the words alone" to "<html><body><h1>$ENGLISH</h1></body></html>",
            "the error route without privacy" to page(FacebookAnswerJudge.ERROR_ROUTE, privacy = false, title = ENGLISH),
            "the error route without props" to page(FacebookAnswerJudge.ERROR_ROUTE, privacy = null, title = ENGLISH),
            "two routes that disagree" to error + page(REELS_ROUTE, title = ""),
            "a broken route" to "<script>{\"initialRouteInfo\":{\"route\":{\"canonicalRouteName\":</script>",
            "the route's name outside the route" to
                "<script>{\"modules\":[\"${FacebookAnswerJudge.NOT_FOUND_ROUTE}\",\"${FacebookAnswerJudge.ERROR_ROUTE}\"]}</script>",
            "the key renamed" to error.replace("canonicalRouteName", "routeName"),
            "the route renamed" to error.replace(FacebookAnswerJudge.ERROR_ROUTE, "comet.fbweb.CometErrorRouteV2"),
            "the reel's null video" to page(REELS_ROUTE, title = "") +
                "<script>{\"data\":{\"video\":null},\"extensions\":{\"is_final\":true}}</script>",
            "a login form" to "<form id=\"login_form\" method=\"POST\"></form>",
        )
        for ((name, html) in cases) {
            val result = judge(ok(html))
            assertEquals(name, AnswerClass.SHAPE_CHANGED, result.answerClass)
            assertEquals(name, Next.NEXT_RUNG, result.next)
            assertEquals(name, SiteExtractionFailure.RESPONSE_CHANGED, result.reason)
        }
        assertEquals("no rows; route: error (no privacy)", judge(ok(cases.getValue("the error route without privacy"))).evidence)
        assertEquals("no rows; no route", judge(ok(cases.getValue("two routes that disagree"))).evidence)
        assertEquals("no rows; route: another", judge(ok(cases.getValue("the reel's null video"))).evidence)
        assertNull(FacebookAnswerJudge.route(cases.getValue("a broken route")))
        assertEquals(
            "the same route twice is one route",
            AnswerClass.PRIVATE,
            judge(ok(error + error)).answerClass,
        )
        assertEveryEvidenceClean()
    }

    @Test
    fun `rows bound to the video win over any page signal, and fewer heights are PARTIAL`() {
        val error = ok(page(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = ENGLISH))
        val direct = judge(error, rows = 3)
        assertEquals(AnswerClass.DIRECT, direct.answerClass)
        assertEquals(Next.CHECK_ROWS, direct.next)
        assertNull(direct.reason)
        assertEquals("rows 3, bound to the video ID", direct.evidence)
        val partial = judge(ok(page(FacebookAnswerJudge.NOT_FOUND_ROUTE, title = "")), rows = 1, fewerHeights = true)
        assertEquals(AnswerClass.PARTIAL, partial.answerClass)
        assertEquals(Next.CHECK_ROWS, partial.next)
        assertEquals(AnswerClass.DIRECT, judge(error, rows = 1, drm = true, needsPlayback = true).answerClass)
        assertEveryEvidenceClean()
    }

    @Test
    fun `a 429 or a checkpoint stops the site, a login address is the browser's, other failures are transient`() {
        val limited = judge(ExtractorHttpResult.Failure(SiteExtractionFailure.RATE_LIMITED, 429))
        assertEquals(AnswerClass.RATE_LIMITED, limited.answerClass)
        assertEquals(Next.STOP_SITE, limited.next)
        assertEquals("status 429", limited.evidence)
        assertEquals(
            "a 429 under another reason",
            AnswerClass.RATE_LIMITED,
            judge(ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 429)).answerClass,
        )
        val botCheck = judge(ExtractorHttpResult.Failure(SiteExtractionFailure.BOT_CHECK))
        assertEquals(AnswerClass.BOT_CHECK, botCheck.answerClass)
        assertEquals(Next.STOP_SITE, botCheck.next)

        val login = judge(ok("<html></html>", "https://www.facebook.com/login/?next=%2Fwatch%2F"), rows = 2)
        assertEquals("a wall before the rows", AnswerClass.LOGIN_WALL, login.answerClass)
        assertEquals(Next.BROWSER, login.next)
        assertEquals(SiteExtractionFailure.LOGIN_REQUIRED, login.reason)
        assertEquals(AnswerClass.LOGIN_WALL, judge(ok("", "https://m.facebook.com/login.php")).answerClass)
        val checkpoint = judge(ok("", "https://m.facebook.com/checkpoint/block/"))
        assertEquals(AnswerClass.BOT_CHECK, checkpoint.answerClass)
        assertEquals("final address: checkpoint", checkpoint.evidence)
        assertEquals(
            "a path that only starts like the login's",
            AnswerClass.SHAPE_CHANGED,
            judge(ok("", "https://www.facebook.com/loginhelp")).answerClass,
        )
        val offSite = judge(ok(page(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = ENGLISH), "https://example.com/"))
        assertEquals(AnswerClass.SHAPE_CHANGED, offSite.answerClass)
        assertEquals("final address: off Facebook", offSite.evidence)
        assertEquals(
            "a host that only ends like Facebook's",
            AnswerClass.SHAPE_CHANGED,
            judge(ok("", "https://notfacebook.com/login/")).answerClass,
        )

        val network = judge(ExtractorHttpResult.Failure(SiteExtractionFailure.NETWORK))
        assertEquals(AnswerClass.TRANSIENT, network.answerClass)
        assertEquals(Next.NEXT_RUNG, network.next)
        assertEquals(SiteExtractionFailure.NETWORK, network.reason)
        val status = judge(ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 500))
        assertEquals(AnswerClass.TRANSIENT, status.answerClass)
        assertEquals(SiteExtractionFailure.HTTP_STATUS, status.reason)
        assertEquals("failed: HTTP_STATUS 500", status.evidence)
        assertEveryEvidenceClean()
    }

    @Test
    fun `needs playback goes before a route, and DRM is the reader's evidence`() {
        val error = ok(page(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = ENGLISH))
        val playback = judge(error, needsPlayback = true)
        assertEquals(AnswerClass.NEEDS_PLAYBACK, playback.answerClass)
        assertEquals(Next.PLAYBACK, playback.next)
        assertNull(playback.reason)
        val drm = judge(ok("<html></html>"), drm = true)
        assertEquals(AnswerClass.DRM, drm.answerClass)
        assertEquals(Next.FINAL, drm.next)
        assertEquals(SiteExtractionFailure.DRM_PROTECTED, drm.reason)
        assertEveryEvidenceClean()
    }

    @Test
    fun `each class's next step agrees with the terminal rules, and only positive evidence is final`() {
        for (answerClass in AnswerClass.entries) {
            val reason = answerClass.reason
            when (answerClass.next) {
                Next.FINAL -> assertTrue(
                    answerClass.name,
                    reason in TerminalRules.NEVER_FALLBACK || reason == SiteExtractionFailure.NO_MEDIA_FOUND,
                )
                Next.BROWSER -> assertTrue(answerClass.name, reason in TerminalRules.BROWSER_REQUIRED)
                Next.STOP_SITE -> assertTrue(
                    answerClass.name,
                    reason in TerminalRules.NEVER_FALLBACK || reason in TerminalRules.BROWSER_REQUIRED,
                )
                Next.NEXT_RUNG -> assertTrue(answerClass.name, reason == null || TerminalRules.routeBRecoverable(reason))
                Next.CHECK_ROWS, Next.PLAYBACK -> assertNull(answerClass.name, reason)
            }
        }
        val error = page(FacebookAnswerJudge.ERROR_ROUTE, privacy = true, title = BURMESE)
        val mutations = listOf(
            error.replace("\"privacy\":true", "\"privacy\":\"true\""),
            error.replace("\"privacy\":true", "\"private\":true"),
            error.replace("\"rootView\"", "\"view\""),
            error.replace("\"route\":{", "\"routes\":{"),
            error.replace("initialRouteInfo", "initialRoute"),
            error.replace(FacebookAnswerJudge.ERROR_ROUTE, FacebookAnswerJudge.ERROR_ROUTE.lowercase()),
            page(FacebookAnswerJudge.NOT_FOUND_ROUTE, title = "").replace("VideoNotFound", "VideoFound"),
        )
        for (mutated in mutations) {
            assertFalse(mutated, judge(ok(mutated)).next == Next.FINAL)
        }
        assertEveryEvidenceClean()
    }

    /** The evidence holds no page words, no address and no ID. */
    private fun assertEveryEvidenceClean() {
        for (result in judged) {
            val evidence = result.evidence
            assertFalse(evidence, ENGLISH in evidence || BURMESE in evidence || "Popular" in evidence)
            assertFalse(evidence, "http" in evidence || "facebook.com" in evidence || VIDEO_ID in evidence)
        }
    }

    private fun ok(body: String, finalUrl: String = "https://www.facebook.com/watch/?v=$VIDEO_ID") =
        ExtractorHttpResult.Success(200, body, finalUrl)

    /** A page of the route's structure: the initial route's name and its root's props. */
    private fun page(route: String, privacy: Boolean? = null, title: String): String {
        val props = buildList {
            add("\"title\":\"$title\"")
            privacy?.let { add("\"privacy\":$it") }
        }.joinToString(",")
        return "<html><head><title>Facebook</title></head><body>" +
            "<script type=\"application/json\" data-sjs>{\"require\":[[\"RouteInfo\",null,null,[{\"initialRouteInfo\":" +
            "{\"route\":{\"actorID\":\"0\",\"rootView\":{\"resource\":{\"__dr\":\"Root.react\"},\"props\":{$props}}," +
            "\"tracePolicy\":\"comet.test\",\"canonicalRouteName\":\"$route\"}}}]]]}</script></body></html>"
    }

    private companion object {
        const val VIDEO_ID = "1000000000000001"
        const val REELS_ROUTE = "comet.fbweb.CometFBReelsRoute"
        const val ENGLISH = "This content is not here for you now"
        const val BURMESE = "ဒီအကြောင်းအရာကို အခု ကြည့်လို့ မရပါ"
    }
}
