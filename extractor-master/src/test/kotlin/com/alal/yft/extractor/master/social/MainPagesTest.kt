package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpResult
import com.alal.yft.extractor.api.ExtractorProbeResult
import com.alal.yft.extractor.api.ResponseCookie
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.modules.youtube.testing.FakeExtractorHttpClient
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan S1a (2): [MainPageRecorder] keeps main's 2xx GET answers in the lookup's
 * [MainPages] (the addresses, the status, the credentials flag and the page within the bound;
 * never a cookie the answer set) and only passes through without one, and for posts and file
 * checks. Synthetic answers only; no live calls.
 */
class MainPagesTest {
    private val share = "https://www.facebook.com/share/v/Fixture1/"
    private val resolved = "https://www.facebook.com/watch/?v=1000000000000001"
    private val safari = linkedMapOf("User-Agent" to "Safari", "Accept" to "text/html")
    private val signedIn = linkedMapOf("User-Agent" to "Browser", "Cookie" to "c_user=fixture")

    private fun page(body: String, finalUrl: String = resolved) =
        ExtractorHttpResult.Success(
            200, body, finalUrl, "text/html",
            cookies = listOf(ResponseCookie("fr", "fixture", "facebook.com", hostOnly = false)),
        )

    @Test
    fun `main's 2xx GET answers are kept with their final address and credentials flag, no cookie`() = runTest {
        val pages = MainPages()
        val http = FakeExtractorHttpClient(
            getResponder = { url, headers ->
                when {
                    url == share && headers["Cookie"] != null -> page("<html>session page</html>")
                    url == share -> page("<html>public page</html>", finalUrl = share)
                    else -> null
                }
            },
        )
        val answers = withContext(pages) {
            listOf(
                MainPageRecorder(http).get(share, safari, MAX),
                MainPageRecorder(http).get(share, signedIn, MAX),
                MainPageRecorder(http).get("https://www.facebook.com/missing", safari, MAX),
            )
        }

        // Main's answers go back unchanged (the cookies it set included): only the copy is bare.
        assertEquals(1, (answers[0] as ExtractorHttpResult.Success).cookies.size)
        assertTrue(answers[2] is ExtractorHttpResult.Failure)
        val kept = pages.pages()
        assertEquals(2, kept.size)
        assertEquals(listOf(share, share), kept.map { it.url })
        assertEquals(listOf(share, resolved), kept.map { it.finalUrl })
        assertEquals(listOf(false, true), kept.map { it.authorized })
        assertEquals(listOf("<html>public page</html>", "<html>session page</html>"), kept.map { it.body })
        assertEquals(listOf("text/html", "text/html"), kept.map { it.contentType })
        assertEquals(share, pages.finalUrlOf(share)) // the first answer of the address
        assertNull(pages.finalUrlOf(resolved))
        assertEquals(3, http.requestedUrls.size) // each GET went out once, unchanged
        assertEquals(listOf(safari, signedIn, safari), http.requestedHeaders)
    }

    @Test
    fun `without pages in the lookup, and for posts and file checks, the recorder only passes through`() = runTest {
        val http = FakeExtractorHttpClient(
            getResponder = { _, _ -> page("<html>page</html>") },
            postResponder = { _, _ -> ExtractorHttpResult.Success(200, "{}", resolved, "application/json") },
            probeResponder = { _, _ -> ExtractorProbeResult.Answered(206) },
        )
        val outside = MainPageRecorder(http).get(share, safari, MAX)
        assertTrue(outside is ExtractorHttpResult.Success)

        val pages = MainPages()
        withContext(pages) {
            MainPageRecorder(http).postJson(resolved, "{}", safari, MAX)
            MainPageRecorder(http).probe("https://video.fixture.fbcdn.net/v/1.mp4", emptyMap(), 5_000)
        }
        assertEquals(emptyList<MainPages.Page>(), pages.pages())
        assertEquals(1, http.requestedUrls.size)
        assertEquals(1, http.postedUrls.size)
        assertEquals(1, http.probedUrls.size)
        assertEquals("MainPageRecorder", MainPageRecorder(http).toString())
    }

    @Test
    fun `the bound keeps addresses only and counts the answers over it`() = runTest {
        val pages = MainPages(maxPages = 2, maxChars = 10)
        val http = FakeExtractorHttpClient(
            getResponder = { url, _ ->
                when (url) {
                    share -> page("0123456789")
                    resolved -> page("0123456789ABC")
                    else -> page("x")
                }
            },
        )
        withContext(pages) {
            MainPageRecorder(http).get(share, safari, MAX)
            MainPageRecorder(http).get(resolved, safari, MAX)
            MainPageRecorder(http).get("https://www.facebook.com/third", safari, MAX)
        }
        val kept = pages.pages()
        assertEquals(2, kept.size)
        assertEquals("0123456789", kept[0].body)
        assertNull(kept[1].body) // over the character bound: its addresses only
        assertEquals(resolved, kept[1].finalUrl)
        assertEquals(1, pages.unlisted)
        assertEquals("MainPages(pages=2, bodies=1, unlisted=1)", pages.toString())
        // Neither a page nor an address in the text.
        assertFalse(kept.joinToString().contains("facebook"))
        assertFalse(kept.joinToString().contains("0123"))
    }

    @Test
    fun `a failure is not kept and each lookup has its own pages`() = runTest {
        val first = MainPages()
        val second = MainPages()
        val http = FakeExtractorHttpClient(
            getResponder = { url, _ ->
                if (url == share) null else ExtractorHttpResult.Success(200, "<html>ok</html>", url, "text/html")
            },
            fallback = ExtractorHttpResult.Failure(SiteExtractionFailure.HTTP_STATUS, 429),
        )
        withContext(first) { MainPageRecorder(http).get(share, safari, MAX) }
        withContext(second) { MainPageRecorder(http).get(resolved, safari, MAX) }

        assertEquals(emptyList<MainPages.Page>(), first.pages())
        assertEquals(listOf(resolved), second.pages().map { it.url })
        assertSame(MainPages, MainPages.Key)
    }

    private companion object {
        const val MAX = 6L * 1024 * 1024
    }
}
