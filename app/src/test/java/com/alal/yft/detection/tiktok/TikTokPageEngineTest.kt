package com.alal.yft.detection.tiktok

import com.alal.yft.detection.HiddenPageResult
import com.alal.yft.detection.JsonText
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SitePageDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P40 step 4: the hidden page. It loads the link with the store script, polls the item script
 * until the post's data appears or 15 s pass, and is always destroyed; one page at a time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TikTokPageEngineTest {
    @Test
    fun `the post's data from the hidden page comes with the page's agent and cookies`() =
        runTest {
            val window = FakeWindow(this, finalUrl = VIDEO) { elapsed ->
                if (elapsed >= 900) item(from = "api", answers = 2) else NOT_YET
            }
            val windows = FakeWindows(window)
            val cookies = FakeCookies("ttwid=page")

            val result = engine(windows, cookies).read(SHORT_LINK, null)

            assertTrue(result is HiddenPageResult.Found)
            result as HiddenPageResult.Found
            assertEquals(SitePageDataSource.HIDDEN_PAGE, result.data.source)
            assertEquals(ITEM_JSON, result.data.json)
            assertEquals(VIDEO, result.finalUrl)
            assertEquals("desktop-agent", result.userAgent)
            assertEquals("ttwid=page", result.cookie)
            assertEquals(
                listOf("hidden page: found in 0.9 s · API answer · API answers kept: 2"),
                result.details,
            )
            assertEquals(listOf(SHORT_LINK), window.loaded)
            assertEquals(listOf("(SRC)('store', null);"), windows.storeScripts)
            assertEquals("(SRC)('item', null);", window.scripts.first())
            assertEquals(1, window.destroyed)
            assertFalse(result.toString().contains("ttwid"))
        }

    @Test
    fun `the item script asks for the link's post id`() = runTest {
        val window = FakeWindow(this) { item(from = "script", answers = 0) }

        val result = engine(FakeWindows(window)).read(VIDEO, POST_ID)

        assertTrue(result is HiddenPageResult.Found)
        assertEquals("(SRC)('item', \"$POST_ID\");", window.scripts.single())
        // Without a redirect the link is the page's address.
        assertEquals(VIDEO, (result as HiddenPageResult.Found).finalUrl)
    }

    @Test
    fun `no data within 15 s ends the read and destroys the page`() = runTest {
        val window = FakeWindow(this) { """{"v":1,"none":true,"answers":2}""" }

        val result = engine(FakeWindows(window)).read(VIDEO, POST_ID)

        assertEquals(
            HiddenPageResult.NotFound(
                reason = null,
                message = null,
                details = listOf(
                    "hidden page: no data after 15.0 s · no data for this post · " +
                        "API answers kept: 2",
                ),
            ),
            result,
        )
        assertEquals(15_000L, currentTime)
        assertEquals(1, window.destroyed)
    }

    @Test
    fun `a check that stays is a bot check the user passes in the browser`() = runTest {
        val window = FakeWindow(this) { """{"v":1,"none":true,"check":true}""" }

        val result = engine(FakeWindows(window)).read(VIDEO, POST_ID)

        assertEquals(
            HiddenPageResult.NotFound(
                reason = SiteExtractionFailure.BOT_CHECK,
                message = TikTokPageEngine.CHECK_MESSAGE,
                details = listOf("hidden page: a check stays on the page · 3.3 s"),
            ),
            result,
        )
        assertEquals(1, window.destroyed)
    }

    @Test
    fun `a check TikTok's own script passes by itself does not stop the read`() = runTest {
        val window = FakeWindow(this) { elapsed ->
            when {
                elapsed < 1_500 -> """{"v":1,"none":true,"check":true}"""
                elapsed < 2_400 -> NOT_YET
                else -> item(from = "script", answers = 0)
            }
        }

        val result = engine(FakeWindows(window)).read(VIDEO, POST_ID)

        assertTrue(result is HiddenPageResult.Found)
    }

    @Test
    fun `TikTok's status for the post is its answer`() = runTest {
        val answers = mapOf(
            10231L to SiteExtractionFailure.GEO_RESTRICTED,
            10222L to SiteExtractionFailure.LOGIN_REQUIRED,
            10204L to SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
        )

        answers.forEach { (status, failure) ->
            val window = FakeWindow(this) { """{"v":1,"none":true,"status":$status}""" }

            val result = engine(FakeWindows(window)).read(VIDEO, POST_ID)

            assertEquals(
                HiddenPageResult.NotFound(
                    failure,
                    null,
                    listOf("hidden page: TikTok status $status · 2.4 s"),
                ),
                result,
            )
            assertEquals(1, window.destroyed)
        }
    }

    @Test
    fun `a page that does not load ends the read`() = runTest {
        val window = FakeWindow(this, error = "ERR_NAME_NOT_RESOLVED") { NOT_YET }

        val result = engine(FakeWindows(window)).read(VIDEO, POST_ID)

        assertEquals(
            listOf("hidden page: the page did not load (ERR_NAME_NOT_RESOLVED) after 0.3 s"),
            result.details,
        )
        assertEquals(1, window.destroyed)
    }

    @Test
    fun `TT_HIDDEN_PAGE=OFF opens no page`() = runTest {
        val windows = FakeWindows(FakeWindow(this) { NOT_YET })

        val result = engine(windows, settings = TikTokPageSettings(hiddenPage = false))
            .read(VIDEO, POST_ID)

        assertSame(HiddenPageResult.Off, result)
        assertEquals(listOf("hidden page: turned off"), result.details)
        assertEquals(0, windows.storeScripts.size)
    }

    @Test
    fun `without a WebView or the script there is no hidden page`() = runTest {
        val noWebView = engine(FakeWindows()).read(VIDEO, POST_ID)
        val windows = FakeWindows(FakeWindow(this) { NOT_YET })
        val noScript = engine(windows, script = null).read(VIDEO, POST_ID)

        assertEquals(listOf("hidden page: no WebView on this phone"), noWebView.details)
        assertEquals(listOf("hidden page: page script missing from this build"), noScript.details)
        assertEquals(0, windows.storeScripts.size)
    }

    @Test
    fun `data larger than 64 KB is not used`() = runTest {
        val big = """{"id":"$POST_ID","desc":"${"x".repeat(70_000)}"}"""
        val window = FakeWindow(this) { item(from = "script", answers = 0, json = big) }

        val result = engine(FakeWindows(window)).read(VIDEO, POST_ID)

        assertEquals(
            listOf("hidden page: the post's data is larger than 64 KB"),
            result.details,
        )
        assertEquals(1, window.destroyed)
    }

    @Test
    fun `one hidden page at a time - the second lookup waits for the first`() = runTest {
        val events = mutableListOf<String>()
        val first = FakeWindow(this, name = "1", events = events) { elapsed ->
            if (elapsed >= 900) item(from = "script", answers = 0) else NOT_YET
        }
        val second = FakeWindow(this, name = "2", events = events) {
            item(from = "script", answers = 0)
        }
        val engine = engine(FakeWindows(first, second, events = events))

        val one = async { engine.read(VIDEO, POST_ID) }
        val two = async { engine.read(VIDEO, POST_ID) }

        assertTrue(one.await() is HiddenPageResult.Found)
        val secondResult = two.await()
        assertEquals(
            listOf("open", "1 load", "1 destroy", "open", "2 load", "2 destroy"),
            events,
        )
        assertEquals(
            listOf(
                "hidden page: found in 0.3 s · page script · API answers kept: 0",
                "hidden page: waited 0.9 s for the lookup before",
            ),
            secondResult.details,
        )
    }

    @Test
    fun `a cancelled lookup still destroys the page`() = runTest {
        val window = FakeWindow(this) { NOT_YET }
        val engine = engine(FakeWindows(window))

        val job = launch { engine.read(VIDEO, POST_ID) }
        advanceTimeBy(1_000)
        runCurrent()
        job.cancel()
        job.join()

        assertEquals(1, window.destroyed)
    }

    @Test
    fun `TT_HOME_COOKIES=OFF clears only the TikTok cookies the page set`() = runTest {
        val cookies = FakeCookies("sessionid=user")
        val window = FakeWindow(this, onLoad = { cookies.set("ttwid=new; msToken=new") }) {
            item(from = "script", answers = 0)
        }

        val result = engine(
            FakeWindows(window),
            cookies,
            settings = TikTokPageSettings(homeCookies = false),
        ).read(VIDEO, POST_ID)

        result as HiddenPageResult.Found
        // The rows still carry the cookies the page used.
        assertEquals("sessionid=user; ttwid=new; msToken=new", result.cookie)
        assertEquals(listOf(setOf("ttwid", "msToken")), cookies.cleared)
        assertEquals(
            listOf(
                "hidden page: found in 0.3 s · page script · API answers kept: 0",
                "hidden page: TikTok cookies it set cleared: 2",
            ),
            result.details,
        )
    }

    @Test
    fun `TT_HOME_COOKIES=ON keeps the page's cookies for the next lookups`() = runTest {
        val cookies = FakeCookies("sessionid=user")
        val window = FakeWindow(this, onLoad = { cookies.set("ttwid=new") }) {
            item(from = "script", answers = 0)
        }

        val result = engine(FakeWindows(window), cookies).read(VIDEO, POST_ID)

        assertEquals(emptyList<Set<String>>(), cookies.cleared)
        assertEquals(1, result.details.size)
    }

    private fun TestScope.engine(
        windows: FakeWindows,
        cookies: FakeCookies = FakeCookies(null),
        settings: TikTokPageSettings = TikTokPageSettings.OWNER,
        script: String? = "SRC",
    ) = TikTokPageEngine(
        settings = settings,
        script = { script },
        windows = windows,
        cookies = cookies,
        clock = { testScheduler.currentTime },
    )

    private class FakeWindows(
        vararg windows: FakeWindow,
        private val events: MutableList<String> = mutableListOf(),
    ) : HiddenPageWindows {
        private val queue = ArrayDeque(windows.toList())
        val storeScripts = mutableListOf<String>()

        override suspend fun open(storeScript: String): HiddenPageWindow? {
            val window = queue.removeFirstOrNull() ?: return null
            storeScripts += storeScript
            events += "open"
            return window
        }
    }

    /** A page whose item script answers by the time since it loaded. */
    private class FakeWindow(
        private val scope: TestScope,
        private val name: String = "1",
        private val events: MutableList<String> = mutableListOf(),
        private val finalUrl: String? = null,
        private val onLoad: () -> Unit = {},
        override val error: String? = null,
        private val answer: (Long) -> String?,
    ) : HiddenPageWindow {
        override val userAgent: String = "desktop-agent"
        override var currentUrl: String? = null
        val loaded = mutableListOf<String>()
        val scripts = mutableListOf<String>()
        var destroyed = 0
        private var loadedAt = 0L

        override suspend fun load(url: String) {
            events += "$name load"
            loaded += url
            loadedAt = scope.testScheduler.currentTime
            currentUrl = finalUrl ?: url
            onLoad()
        }

        override suspend fun evaluate(script: String): String? {
            scripts += script
            return answer(scope.testScheduler.currentTime - loadedAt)?.let(::evaluated)
        }

        override suspend fun destroy() {
            destroyed += 1
            events += "$name destroy"
        }
    }

    private class FakeCookies(private var header: String?) : TikTokCookies {
        val cleared = mutableListOf<Set<String>>()

        fun set(added: String) {
            header = listOfNotNull(header, added).joinToString("; ")
        }

        override fun header(url: String): String? = header

        override fun clear(names: Set<String>) {
            cleared += names
        }
    }

    private companion object {
        const val POST_ID = "7311234567890123456"
        const val VIDEO = "https://www.tiktok.com/@scout/video/$POST_ID"
        const val SHORT_LINK = "https://vt.tiktok.com/ZSabc123/"
        const val NOT_YET = """{"v":1,"none":true,"answers":0}"""
        const val ITEM_JSON = "{\"id\":\"$POST_ID\",\"video\":{\"playAddr\":" +
            "\"https://v16-webapp.tiktokcdn.com/a/b.mp4?x-expires=1\"}}"

        fun item(from: String, answers: Int, json: String = ITEM_JSON): String =
            StringBuilder("""{"v":1,"from":"$from","id":"$POST_ID","item":""")
                .also { JsonText.appendString(it, json) }
                .append(",\"answers\":$answers}")
                .toString()

        fun evaluated(text: String): String =
            StringBuilder().also { JsonText.appendString(it, text) }.toString()
    }
}
