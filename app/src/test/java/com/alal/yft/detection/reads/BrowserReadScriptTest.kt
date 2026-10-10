package com.alal.yft.detection.reads

import com.alal.yft.core.model.media.BrowserReadRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P45: the browser read's script and how its answer is read. */
class BrowserReadScriptTest {
    @Test
    fun theLinkIsPutInTheScriptAsOneEscapedStringOnly() {
        val request = BrowserReadRequest(
            url = "https://cdn.example.test/v.m3u8?a=\");alert(1);//&b=</script>",
            pageUrl = "https://tube.example.test/watch",
            userAgent = null,
            wantsText = true,
            maxBytes = 2_048,
        )

        val script = BrowserReadScript.start(7, request, timeoutMillis = 9_000)

        assertTrue(script.contains("fetch(\"https://cdn.example.test/v.m3u8?a=\\\");alert(1)"))
        assertFalse(script.contains("a=\");alert"))
        assertTrue(script.contains("var key = \"7\";"))
        assertTrue(script.contains("var wantsText = true;"))
        assertTrue(script.contains("var max = 2048;"))
        assertTrue(script.contains("credentials: \"same-origin\""))
        assertTrue(script.contains("9000"))
        assertEquals("(window.__yftReads && window.__yftReads[\"7\"]) || null",
            BrowserReadScript.poll(7))
    }

    @Test
    fun anAnswerIsReadWithItsStatusTypeLengthAndText() {
        val json = """{"status":200,"url":"https://cdn.example.test/v.m3u8","type":""" +
            """"application/vnd.apple.mpegurl","length":"42","text":"#EXTM3U\n","error":""}"""

        val answer = BrowserReadScript.answer(json, maxChars = 1_024)!!

        assertEquals(200, answer.status)
        assertTrue(answer.isSuccess)
        assertEquals("https://cdn.example.test/v.m3u8", answer.finalUrl)
        assertEquals("application/vnd.apple.mpegurl", answer.contentType)
        assertEquals(42L, answer.contentLength)
        assertEquals("#EXTM3U\n", answer.text)
        assertNull(answer.error)
    }

    @Test
    fun noAnswerYetARefusalAndAnErrorAreToldApart() {
        assertNull(BrowserReadScript.answer(null, 10))
        assertNull(BrowserReadScript.answer("null", 10))
        assertNull(BrowserReadScript.answer("{\"text\":\"no status\"}", 10))

        val refused = BrowserReadScript.answer(
            """{"status":410,"url":"","type":"","length":"","text":null,"error":""}""",
            10,
        )!!
        assertEquals(410, refused.status)
        assertFalse(refused.isSuccess)
        assertNull(refused.finalUrl)
        assertNull(refused.text)

        val failed = BrowserReadScript.answer(
            """{"status":0,"url":"","type":"","length":"","text":null,"error":"TypeError"}""",
            10,
        )!!
        assertEquals(0, failed.status)
        assertEquals("TypeError", failed.error)

        // A text longer than asked, or an address that is not HTTPS, is dropped.
        val long = BrowserReadScript.answer(
            """{"status":200,"url":"http://cdn.example.test/v","text":"0123456789AB"}""",
            10,
        )!!
        assertNull(long.text)
        assertNull(long.finalUrl)
    }

    @Test
    fun theBlankPagesOriginIsThePagesHttpsOrigin() {
        assertEquals(
            "https://www.tube.example.test",
            BrowserReadScript.originOf("https://WWW.tube.example.test/view?key=1"),
        )
        assertEquals(
            "https://tube.example.test:8443",
            BrowserReadScript.originOf("https://tube.example.test:8443/x"),
        )
        assertNull(BrowserReadScript.originOf("http://tube.example.test/x"))
        assertNull(BrowserReadScript.originOf("about:blank"))
    }
}
