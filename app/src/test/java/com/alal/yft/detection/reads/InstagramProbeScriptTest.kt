package com.alal.yft.detection.reads

import com.alal.yft.extractor.master.social.InstagramProbeQuery
import com.alal.yft.extractor.master.social.InstagramTabProbe
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S2d part 1 (decision 19 (v)): Instagram's tab-fetch probe as the browser runs it —
 * only on the post's own page on Instagram's web host; the page's own POST of yt-dlp's signed-out
 * query (same origin, the page's cookies; the form in yt-dlp's order with the page's LSD value; the
 * query's headers, `X-FB-LSD`, `X-CSRFToken` from the tab's cookie); its answer read back bounded
 * and forgotten; no LSD, no script, no answer in time each said. Task 13 (the rung): a tab whose
 * own cookies name a session asks nothing. A fake tab; no live calls.
 */
class InstagramProbeScriptTest {
    @Test
    fun onlyThePostsOwnPageOnInstagramsWebHost() {
        for (page in listOf(
            "https://www.instagram.com/reel/$CODE/",
            "https://www.instagram.com/p/$CODE/",
            "https://www.instagram.com/reels/$CODE",
            "https://www.instagram.com/tv/$CODE/",
            "https://www.instagram.com/fixture_user/reel/$CODE/",
            "https://WWW.Instagram.com/p/$CODE/?img_index=2",
            "https://www.instagram.com:443/p/$CODE/",
        )) {
            assertTrue(page, InstagramProbeScript.onPost(page, CODE))
        }
        for (page in listOf(
            null,
            "https://www.instagram.com/reel/C9fixtOTHER/",
            "https://www.instagram.com/$CODE/",
            "https://www.instagram.com/stories/fixture_user/$CODE/",
            "https://instagram.com/reel/$CODE/",
            "https://m.instagram.com/reel/$CODE/",
            "http://www.instagram.com/reel/$CODE/",
            "https://www.instagram.com.fixture.test/reel/$CODE/",
            "https://www.instagram.com:8443/reel/$CODE/",
            "https://www.instagram.com/accounts/login/",
            "not a link",
        )) {
            assertFalse(page.toString(), InstagramProbeScript.onPost(page, CODE))
        }
        assertFalse(InstagramProbeScript.onPost("https://www.instagram.com/p/$CODE/", " "))
    }

    @Test
    fun theScriptIsThePagesOwnPostOfTheSignedOutQuery() {
        val script = InstagramProbeScript.start(7, MEDIA_ID, timeoutMillis = 4_000)
        assertTrue(script, "fetch(\"${InstagramProbeQuery.ENDPOINT}\"" in script)
        assertTrue(script, "method: \"POST\", mode: \"same-origin\", credentials: \"same-origin\"" in script)
        assertTrue(script, "cache: \"no-store\"" in script)
        // The form's fields in yt-dlp's order, the LSD value the page's.
        val names = InstagramProbeQuery.form(MEDIA_ID, InstagramProbeQuery.LSD_PLACEHOLDER).map { it.first }
        val at = names.map { name -> script.indexOf("[\"$name\", ") }
        assertTrue(at.toString(), at.all { it > 0 } && at == at.sorted())
        assertTrue(script, "[\"lsd\", \"{lsd}\"]" in script)
        assertTrue(script, "field[1] === \"{lsd}\" ? lsd : field[1]" in script)
        assertTrue(script, "[\"variables\", \"{\\\"media_id\\\":\\\"$MEDIA_ID\\\"}\"]" in script)
        assertTrue(script, "[\"doc_id\", \"${InstagramProbeQuery.DOC_ID}\"]" in script)
        // The page's two places of its LSD value; the query's headers and the tab's own two.
        assertTrue(script, "getElementById(\"__eqmc\")" in script && "data.l" in script)
        assertTrue(script, """/\["LSD",\[\],\{"token":"([^"]+)"/""" in script)
        InstagramProbeQuery.HEADERS.forEach { (name, value) -> assertTrue(name, "\"$name\": \"$value\"" in script) }
        assertTrue(script, "headers[\"X-FB-LSD\"] = lsd" in script)
        assertTrue(script, "cookie(\"csrftoken\")" in script && "headers[\"X-CSRFToken\"] = csrf" in script)
        // Bounded: 1 MiB of answer, cut off in time; kept under its key for the app.
        assertTrue(script, "var max = ${InstagramProbeQuery.MAX_ANSWER_CHARS};" in script)
        assertTrue(script, "control.abort(); }, 4000)" in script)
        assertTrue(script, "window.${InstagramProbeScript.STORE}" in script && "var key = \"7\";" in script)
        // No other address and nothing of the post but its media ID.
        assertEquals(1, Regex("https://").findAll(script).count())
        assertFalse(script, CODE in script)
        assertEquals("(window.__yftProbe && window.__yftProbe[\"7\"]) || null", InstagramProbeScript.poll(7))
        assertEquals("if (window.__yftProbe) { delete window.__yftProbe[\"7\"]; }", InstagramProbeScript.forget(7))
    }

    @Test
    fun theTabsReplyIsReadBounded() {
        assertNull(InstagramProbeScript.reply(null))
        assertNull(InstagramProbeScript.reply(""))
        assertNull(InstagramProbeScript.reply("null"))
        assertNull(InstagramProbeScript.reply("{not json"))
        assertNull(InstagramProbeScript.reply("""{"status":200}"""))
        assertEquals(
            InstagramTabProbe.NO_LSD,
            (InstagramProbeScript.reply("""{"asked":false,"why":"no-lsd"}""") as InstagramTabProbe.Reply.NotAsked).why,
        )
        assertEquals(
            InstagramTabProbe.NO_ANSWER,
            (InstagramProbeScript.reply("""{"asked":false,"why":"other"}""") as InstagramTabProbe.Reply.NotAsked).why,
        )
        val answered = InstagramProbeScript.reply(
            """{"asked":true,"status":200,"bytes":42,"text":"{\"data\":{}}","type":"application/json; charset=utf-8",""" +
                """"url":"https://www.instagram.com/api/graphql","wall":true}""",
        ) as InstagramTabProbe.Reply.Answered
        assertEquals(200, answered.status)
        assertEquals(42L, answered.bytes)
        assertEquals("{\"data\":{}}", answered.body)
        assertEquals("application/json; charset=utf-8", answered.contentType)
        assertEquals("https://www.instagram.com/api/graphql", answered.finalUrl)
        assertTrue(answered.wall)
        // No answer (the fetch failed or was cut off), odd values, an address that is not HTTPS.
        val none = InstagramProbeScript.reply(
            """{"asked":true,"status":7000,"bytes":-5,"text":null,"type":"","url":"http://www.instagram.com/x","wall":false}""",
        ) as InstagramTabProbe.Reply.Answered
        assertEquals(0, none.status)
        assertEquals(0L, none.bytes)
        assertNull(none.body)
        assertNull(none.contentType)
        assertNull(none.finalUrl)
        assertFalse(none.wall)
        // Over the bound: its size only.
        val big = "x".repeat(InstagramProbeQuery.MAX_ANSWER_CHARS + 1)
        val over = InstagramProbeScript.reply("""{"asked":true,"status":200,"bytes":${big.length},"text":"$big","wall":false}""")
            as InstagramTabProbe.Reply.Answered
        assertNull(over.body)
        assertEquals(big.length.toLong(), over.bytes)
    }

    @Test
    fun theProbeRunsInTheTabOnlyOnThePostAndForgetsItsAnswer() = runTest {
        // Off the post's page: nothing evaluated.
        val away = FakeTab()
        val notOnPost = InstagramProbeScript.run(REQUEST, "https://www.instagram.com/", away::evaluate)
        assertEquals(InstagramTabProbe.NOT_ON_POST, (notOnPost as InstagramTabProbe.Reply.NotAsked).why)
        assertTrue(away.scripts.isEmpty())

        // The script did not run (the tab gave nothing back): nothing went out.
        val silent = FakeTab(started = null)
        val notRun = InstagramProbeScript.run(REQUEST, PAGE, silent::evaluate)
        assertEquals(InstagramTabProbe.NO_ANSWER, (notRun as InstagramTabProbe.Reply.NotAsked).why)
        assertEquals(1, silent.scripts.size)

        // Answered after two polls; the answer is forgotten.
        val tab = FakeTab(polls = listOf("null", "null", """{"asked":true,"status":200,"bytes":2,"text":"{}","wall":false}"""))
        val reply = InstagramProbeScript.run(REQUEST, PAGE, tab::evaluate) as InstagramTabProbe.Reply.Answered
        assertEquals(200, reply.status)
        assertEquals("{}", reply.body)
        assertEquals(5, tab.scripts.size)
        assertTrue(tab.scripts.first().startsWith("(function () {"))
        assertTrue(tab.scripts[1].startsWith("(window.__yftProbe"))
        assertTrue(tab.scripts.last().startsWith("if (window.__yftProbe) { delete"))

        // No LSD value on the page: said so.
        val noLsd = FakeTab(polls = listOf("""{"asked":false,"why":"no-lsd"}"""))
        val said = InstagramProbeScript.run(REQUEST, PAGE, noLsd::evaluate)
        assertEquals(InstagramTabProbe.NO_LSD, (said as InstagramTabProbe.Reply.NotAsked).why)

        // No answer in time: it may have gone out, so a fetch with no HTTP answer; forgotten too.
        val slow = FakeTab(polls = emptyList())
        val late = InstagramProbeScript.run(REQUEST, PAGE, slow::evaluate, waitMillis = 1_000) as InstagramTabProbe.Reply.Answered
        assertEquals(0, late.status)
        assertNull(late.body)
        assertTrue(slow.scripts.last().startsWith("if (window.__yftProbe) { delete"))
    }

    @Test
    fun aSignedInTabAsksNothing() = runTest {
        // Task 13: the tab's own cookies for Instagram's web host name a session: nothing runs.
        val tab = FakeTab()
        val signedIn = InstagramProbeScript.run(
            REQUEST, PAGE, tab::evaluate, tabCookie = { "csrftoken=fixture; sessionid=1%3Afixture; ds_user_id=1" },
        )
        assertEquals(InstagramTabProbe.SIGNED_IN, (signedIn as InstagramTabProbe.Reply.NotAsked).why)
        assertTrue(InstagramTabProbe.SIGNED_IN in InstagramTabProbe.REASONS)
        assertTrue(tab.scripts.isEmpty())
        assertEquals("https://www.instagram.com/", InstagramProbeScript.COOKIE_PAGE)

        // Signed out: no session, or an empty one; the script runs.
        for (cookie in listOf(null, "", "csrftoken=fixture; mid=fixture", "sessionid=", "sessionid= ; csrftoken=fixture", "xsessionid=1")) {
            assertFalse(cookie.toString(), InstagramProbeScript.signedIn(cookie))
        }
        assertTrue(InstagramProbeScript.signedIn("mid=fixture;sessionid=1%3Afixture"))
        val out = FakeTab(started = null)
        val asked = InstagramProbeScript.run(REQUEST, PAGE, out::evaluate, tabCookie = { "csrftoken=fixture" })
        assertEquals(InstagramTabProbe.NO_ANSWER, (asked as InstagramTabProbe.Reply.NotAsked).why)
        assertEquals(1, out.scripts.size)
    }

    /** A tab that answers the start script with [started] and each poll with the next of [polls] ("null" after). */
    private class FakeTab(private val started: String? = "true", polls: List<String> = emptyList()) {
        val scripts = mutableListOf<String>()
        private val replies = ArrayDeque(polls)

        fun evaluate(script: String, reply: (String?) -> Unit) {
            scripts += script
            when {
                script.startsWith("(function () {") -> started?.let(reply)
                script.startsWith("(window.") -> reply(replies.removeFirstOrNull() ?: "null")
                else -> reply(null)
            }
        }
    }

    private companion object {
        const val CODE = "C9fixtREEL1"
        const val MEDIA_ID = "3413599992010523381"
        const val PAGE = "https://www.instagram.com/reel/$CODE/"
        val REQUEST = InstagramTabProbe.Request(CODE, MEDIA_ID, PAGE)
    }
}
