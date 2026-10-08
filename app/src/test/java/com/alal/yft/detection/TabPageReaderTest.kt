package com.alal.yft.detection

import com.alal.yft.core.browser.detection.HeadlessPageFetcher
import com.alal.yft.core.model.media.CandidateSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P37 (G7): the sheet's quiet second read of the browser tab's page: asked as the tab would ask
 * (its agent and its cookies, no cache), one read at a time; a page without player data is not
 * used.
 */
class TabPageReaderTest {
    private val page = "https://clips.example.test/watch/5"
    private val agent = "Mozilla/5.0 (Linux; Android 15; wv) WebView/test"

    @Test
    fun thePagesPlayerFilesComeBackMarkedAsReadAgainWithTheTabsAgent() = runTest {
        var session: HeadlessPageFetcher.TabSession? = null
        val reader = TabPageReader(
            fetch = { url, tab ->
                session = tab
                HeadlessPageFetcher.Result.Page(url, PLAYER_PAGE)
            },
            cookies = { "tab=fixture" },
            clock = { 42L },
        )

        val read = reader.read(page, agent) as PageReread.Found

        val file = read.candidates.single()
        assertEquals("https://media.example.test/v5/720.mp4?validto=1700000600", file.mediaUrl)
        assertTrue(CandidateSource.PAGE_REREAD in file.sources)
        assertEquals("Harbour lights", file.title)
        assertEquals(42L, file.observedAtEpochMs)
        assertEquals(agent, file.requestContext.userAgent)
        assertNull(file.requestContext.cookie)
        assertEquals(agent, session?.userAgent)
        assertEquals("tab=fixture", session?.cookieFor?.invoke(page))
        assertFalse(read.toString().contains("media.example.test"))
        assertEquals(BrowserPageReader.DEFAULT_REREADS, reader.rereads)
    }

    @Test
    fun aNoticeWithoutPlayerDataIsNotUsedAndAFailedReadSaysSo() = runTest {
        val notice = TabPageReader(
            fetch = { url, _ -> HeadlessPageFetcher.Result.Page(url, NOTICE_PAGE) },
            cookies = { null },
        )
        val failed = TabPageReader(
            fetch = { _, _ ->
                HeadlessPageFetcher.Result.Failed(HeadlessPageFetcher.FailureReason.HTTP_STATUS)
            },
            cookies = { null },
        )

        assertEquals(PageReread.NoPlayer, notice.read(page, agent))
        assertEquals(PageReread.Failed, failed.read(page, null))
        assertEquals(0, BrowserPageReader.None.rereads)
    }

    @Test
    fun readsRunOneAtATime() = runTest {
        var running = 0
        var most = 0
        val reader = TabPageReader(
            fetch = { url, _ ->
                running++
                most = maxOf(most, running)
                delay(100)
                running--
                HeadlessPageFetcher.Result.Page(url, PLAYER_PAGE)
            },
            cookies = { null },
        )

        listOf(async { reader.read(page, agent) }, async { reader.read(page, agent) }).awaitAll()

        assertEquals(1, most)
    }

    private companion object {
        val PLAYER_PAGE = """
            <html><head><title>Harbour lights</title></head><body>
            <video controls>
              <source src="https://media.example.test/v5/720.mp4?validto=1700000600"
                type="video/mp4">
            </video>
            </body></html>
        """.trimIndent()

        val NOTICE_PAGE = """
            <html><head><title>Are you 18?</title></head><body>
            <p>This site is for adults. <a href="/enter">Enter</a></p>
            </body></html>
        """.trimIndent()
    }
}
