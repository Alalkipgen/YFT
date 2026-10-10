package com.alal.yft.detection.tiktok

import com.alal.yft.detection.JsonText
import com.alal.yft.extractor.api.SitePageDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** P40: the page script's answers as the browser tab and the hidden page hand them over. */
class TikTokPageScriptTest {
    @Test
    fun `an item from the page's own script is read with its id`() {
        val answer = TikTokPageScript.parse(evaluated(item(from = "script", answers = 0)))

        assertTrue(answer is TikTokPageScript.Answer.Item)
        answer as TikTokPageScript.Answer.Item
        assertEquals(POST_ID, answer.id)
        assertFalse(answer.fromApi)
        assertEquals(ITEM_JSON, answer.json)
        // Its text holds signed addresses: only sizes are printed.
        assertFalse(answer.toString().contains("tiktokcdn"))
    }

    @Test
    fun `an item from TikTok's own API answers is marked as such`() {
        val answer = TikTokPageScript.parse(evaluated(item(from = "api", answers = 3)))

        answer as TikTokPageScript.Answer.Item
        assertTrue(answer.fromApi)
        assertEquals(3, answer.answers)
    }

    @Test
    fun `no item keeps what the page showed instead`() {
        val text = """{"v":1,"none":true,"status":10204,"check":true,"answers":2}"""

        val answer = TikTokPageScript.parse(evaluated(text))

        assertEquals(
            TikTokPageScript.Answer.None(status = 10204, check = true, answers = 2),
            answer,
        )
        assertEquals(
            TikTokPageScript.Answer.None(noId = true),
            TikTokPageScript.parse(evaluated("""{"v":1,"none":true,"noId":true}""")),
        )
        assertEquals(
            TikTokPageScript.Answer.None(tooLarge = true),
            TikTokPageScript.parse(evaluated("""{"v":1,"none":true,"tooLarge":true}""")),
        )
    }

    @Test
    fun `answers this app does not understand are unreadable`() {
        val unreadable = listOf(
            null,
            "null",
            "",
            "\"not json\"",
            evaluated("""{"v":2,"from":"script","id":"$POST_ID","item":"{}"}"""),
            evaluated("""{"v":1,"from":"script","id":"12ab","item":"{\"id\":1}"}"""),
            evaluated("""{"v":1,"from":"other","id":"$POST_ID","item":"{\"id\":1}"}"""),
            evaluated("""{"v":1,"from":"api","id":"$POST_ID","item":""}"""),
            evaluated("[1,2]"),
        )

        unreadable.forEach { result ->
            assertSame(result, TikTokPageScript.Answer.Unreadable, TikTokPageScript.parse(result))
        }
    }

    @Test
    fun `the item script asks for a well formed id only`() {
        assertEquals("(SRC)('item', \"$POST_ID\");", TikTokPageScript.item("SRC", POST_ID))
        assertEquals("(SRC)('item', null);", TikTokPageScript.item("SRC", null))
        // Never pasted into the script as given.
        assertEquals("(SRC)('item', null);", TikTokPageScript.item("SRC", "1');alert(1);//"))
        assertEquals("(SRC)('store', null);", TikTokPageScript.store("SRC"))
    }

    @Test
    fun `only TikTok's own https origins get the store script`() {
        assertTrue(TikTokPageScript.isScriptOrigin("https://www.tiktok.com/@a/video/$POST_ID"))
        assertTrue(TikTokPageScript.isScriptOrigin("https://m.tiktok.com/v/$POST_ID.html"))
        assertTrue(TikTokPageScript.isScriptOrigin("https://WWW.TikTok.com/foryou"))
        assertFalse(TikTokPageScript.isScriptOrigin("http://www.tiktok.com/foryou"))
        assertFalse(TikTokPageScript.isScriptOrigin("https://www.tiktok.com.example.com/"))
        assertFalse(TikTokPageScript.isScriptOrigin("https://vt.tiktok.com/ZS123/"))
        assertFalse(TikTokPageScript.isScriptOrigin("https://example.com/www.tiktok.com"))
        assertFalse(TikTokPageScript.isScriptOrigin(null))
        assertFalse(TikTokPageScript.isScriptOrigin("not a url"))
        val fixtures = setOf("https://fixture.yft.test")
        assertTrue(TikTokPageScript.isScriptOrigin("https://fixture.yft.test/video", fixtures))
        assertFalse(TikTokPageScript.isScriptOrigin("https://fixture.yft.test:8443/", fixtures))
    }

    @Test
    fun `the tab's item for the post is its data with the tab's cookie`() {
        val fromScript = TikTokPageScript.tabData(
            evaluated(item(from = "script", answers = 0)),
            POST_ID,
            cookie = "tt=1",
        )
        val fromApi = TikTokPageScript.tabData(
            evaluated(item(from = "api", answers = 4)),
            POST_ID,
            cookie = "tt=1",
        )

        assertEquals(SitePageDataSource.TAB_SCRIPT, fromScript.pageData?.source)
        assertEquals(ITEM_JSON, fromScript.pageData?.json)
        assertEquals("tt=1", fromScript.cookie)
        assertEquals(
            listOf("tab data: tab · page script · found · API answers kept: 0"),
            fromScript.details,
        )
        assertEquals(SitePageDataSource.TAB_API_ANSWER, fromApi.pageData?.source)
        assertEquals(
            listOf("tab data: tab · API answer · found · API answers kept: 4"),
            fromApi.details,
        )
        assertFalse(fromScript.toString().contains("tt=1"))
    }

    @Test
    fun `the tab gives nothing for another post, no answer or no data`() {
        val other = TikTokPageScript.tabData(
            evaluated(item(from = "script", answers = 1)),
            "7311234567890123999",
            cookie = "tt=1",
        )
        val late = TikTokPageScript.tabData(null, POST_ID, cookie = "tt=1", answered = false)
        val none = TikTokPageScript.tabData(
            evaluated("""{"v":1,"none":true,"status":10204,"answers":3}"""),
            POST_ID,
            cookie = "tt=1",
        )
        val unreadable = TikTokPageScript.tabData("null", POST_ID, cookie = "tt=1")

        assertNull(other.pageData)
        assertNull(other.cookie)
        assertEquals(listOf("tab data: another post's data · API answers kept: 1"), other.details)
        assertNull(late.pageData)
        assertEquals(listOf("tab data: no answer within 1 s"), late.details)
        assertEquals(
            listOf("tab data: no data for this post · TikTok status 10204 · API answers kept: 3"),
            none.details,
        )
        assertNull(none.cookie)
        assertEquals(listOf("tab data: no readable answer"), unreadable.details)
    }

    @Test
    fun `an item larger than 64 KB is not used`() {
        val big = """{"id":"$POST_ID","desc":"${"x".repeat(70_000)}"}"""
        val text = StringBuilder("""{"v":1,"from":"script","id":"$POST_ID","item":""")
            .also { JsonText.appendString(it, big) }
            .append(",\"answers\":0}")
            .toString()

        val tab = TikTokPageScript.tabData(evaluated(text), POST_ID, cookie = "tt=1")

        assertNull(tab.pageData)
        assertNull(tab.cookie)
        assertEquals(
            listOf("tab data: the post's data is larger than 64 KB · API answers kept: 0"),
            tab.details,
        )
    }

    @Test
    fun `the asset is read once and a missing asset gives no script`() {
        var reads = 0
        val source = TikTokPageScript.Source {
            reads += 1
            "(function () {})"
        }
        val missing = TikTokPageScript.Source { throw java.io.FileNotFoundException("asset") }

        assertEquals("(function () {})", source.text())
        assertEquals("(function () {})", source.text())
        assertEquals(1, reads)
        assertNull(missing.text())
        assertNull(TikTokPageScript.Source { " " }.text())
    }

    private fun item(from: String, answers: Int): String =
        StringBuilder("""{"v":1,"from":"$from","id":"$POST_ID","item":""")
            .also { JsonText.appendString(it, ITEM_JSON) }
            .append(",\"answers\":$answers}")
            .toString()

    /** What `evaluateJavascript` hands over for a script that returned [text]. */
    private fun evaluated(text: String): String =
        StringBuilder().also { JsonText.appendString(it, text) }.toString()

    private companion object {
        const val POST_ID = "7311234567890123456"
        const val ITEM_JSON = "{\"id\":\"$POST_ID\",\"video\":{\"playAddr\":" +
            "\"https://v16-webapp.tiktokcdn.com/a/b.mp4?x-expires=1\"}}"
    }
}
