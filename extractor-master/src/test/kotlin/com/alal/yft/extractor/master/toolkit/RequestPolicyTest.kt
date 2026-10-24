package com.alal.yft.extractor.master.toolkit

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.extractor.api.ResponseCookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** R3 T9: RequestPolicy. */
class RequestPolicyTest {
    private val page = "https://www.site.test/watch/1#t=3"
    private val context = BrowserRequestContext(page, "UA", "sid=secret")
    private val chain = ResponseCookie("tt_chain_token", "abc", "media.site.test", hostOnly = true)

    @Test
    fun `same-origin keeps the tab cookie and the page Referer`() {
        val headers = RequestPolicy.headers(page, context, "https://www.site.test/api/v")
        assertEquals("sid=secret", headers["Cookie"])
        assertEquals("https://www.site.test/watch/1", headers["Referer"])
        assertNull(headers["Origin"])
        assertEquals("UA", headers["User-Agent"])
    }

    @Test
    fun `cross-origin media gets only its own answer cookies and an origin-only Referer`() {
        val media = RequestPolicy.headers(page, context, "https://media.site.test/v.mp4", listOf(chain))
        assertEquals("tt_chain_token=abc", media["Cookie"])
        assertEquals("https://www.site.test/", media["Referer"])
        assertEquals("https://www.site.test", media["Origin"])
        val cdn = RequestPolicy.headers(page, context, "https://cdn.other.test/v.mp4", listOf(chain))
        assertFalse(cdn.containsKey("Cookie"))
        assertNull(RequestPolicy.mediaContext(page, context, "https://cdn.other.test/v.mp4").cookie)
        assertEquals("sid=secret", RequestPolicy.mediaContext(page, context, "https://www.site.test/v.mp4").cookie)
    }

    @Test
    fun `a cross-origin hop strips credentials and they are not restored`() {
        val start = mapOf("Cookie" to "a=1", "Authorization" to "x", "Referer" to "https://www.site.test/watch/1", "User-Agent" to "UA")
        assertEquals(start, RequestPolicy.afterRedirect(start, "https://www.site.test/a", "https://www.site.test/b"))
        val hop = RequestPolicy.afterRedirect(start, "https://www.site.test/a", "https://cdn.other.test/b")
        assertEquals(mapOf("Referer" to "https://www.site.test/", "User-Agent" to "UA"), hop)
        val back = RequestPolicy.afterRedirect(hop, "https://cdn.other.test/b", "https://www.site.test/c")
        assertFalse(back.containsKey("Cookie"))
    }

    @Test
    fun `merged cookie replaces same-named pairs in place`() {
        assertEquals("a=1; tt_chain_token=abc; b=2", RequestPolicy.mergedCookie("a=1; tt_chain_token=old; b=2", listOf(chain)))
        assertNull(RequestPolicy.mergedCookie(" ; ", emptyList()))
        assertEquals("tt_chain_token=abc", RequestPolicy.cookieFor("https://media.site.test/v", page, "sid=secret", listOf(chain)))
        assertEquals("sid=secret", RequestPolicy.cookieFor("https://www.site.test/v", page, "sid=secret", listOf(chain)))
    }
}
