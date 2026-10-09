package com.alal.yft.tiktok

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.detection.HiddenPageResult
import com.alal.yft.detection.tiktok.TikTokApiCapture
import com.alal.yft.detection.tiktok.TikTokCookies
import com.alal.yft.detection.tiktok.TikTokPageEngine
import com.alal.yft.detection.tiktok.TikTokPageScript
import com.alal.yft.detection.tiktok.TikTokPageSettings
import com.alal.yft.detection.tiktok.WebViewHiddenPages
import com.alal.yft.extractor.api.SitePageDataSource
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P40: the page script on a real WebView, over sanitized fixtures (invented ids and addresses)
 * served at https://fixture.yft.test by the test itself. Offline: nothing is fetched.
 *
 * The tab reads TikTok's own data (the page's script and the API answers its own code got),
 * the page still gets its answers unchanged, and the hidden page finds a post (or gives up
 * after its timeout) and is always destroyed.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class TikTokPageDataInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private lateinit var source: String
    private var tab: WebView? = null

    @Before
    fun readScript() {
        source = requireNotNull(TikTokPageScript.Source(app).text()) { "page script asset" }
    }

    @After
    fun closeTab() {
        instrumentation.runOnMainSync {
            tab?.destroy()
            tab = null
        }
    }

    @Test
    fun theTabGivesThePostFromThePagesOwnScript() {
        openTab(VIDEO_PAGE)

        val result = evaluate(TikTokPageScript.item(source, POST_ID))
        val tabData = TikTokPageScript.tabData(result, POST_ID, cookie = "tt=fixture")

        assertEquals(SitePageDataSource.TAB_SCRIPT, tabData.pageData?.source)
        assertTrue(tabData.pageData!!.json.contains("fixture-1080.mp4"))
        assertEquals("tt=fixture", tabData.cookie)
        // The address's post when the lookup names none.
        val byAddress = TikTokPageScript.parse(evaluate(TikTokPageScript.item(source, null)))
        assertEquals(POST_ID, (byAddress as TikTokPageScript.Answer.Item).id)
    }

    @Test
    fun theTabGivesALaterPostFromTheApiAnswerThePagesOwnCodeGot() {
        openTab(VIDEO_PAGE)

        val answer = TikTokPageScript.parse(evaluate(TikTokPageScript.item(source, API_POST_ID)))

        assertTrue(answer.toString(), answer is TikTokPageScript.Answer.Item)
        answer as TikTokPageScript.Answer.Item
        assertEquals(API_POST_ID, answer.id)
        assertTrue(answer.fromApi)
        assertEquals(1, answer.answers)
        assertTrue(answer.json.contains("feed-457-1080.mp4"))
        val unknown = TikTokPageScript.parse(evaluate(TikTokPageScript.item(source, UNKNOWN_ID)))
        assertTrue(unknown is TikTokPageScript.Answer.None)
    }

    @Test
    fun thePageGetsItsApiAnswerUnchanged() {
        openTab(VIDEO_PAGE)

        val text = JSONTokener(evaluate("window.__feed.text")).nextValue()

        assertEquals(asset("item_list.json"), text)
        assertEquals("false", evaluate("'error' in window.__feed"))
    }

    @Test
    fun theHiddenPageFindsThePostAndIsDestroyed() {
        val destroyed = CountDownLatch(1)
        val engine = engine(destroyed)

        val started = System.nanoTime()
        val result = runBlocking { engine.read(VIDEO_PAGE, POST_ID) }
        val seconds = (System.nanoTime() - started) / 1e9

        assertTrue(result.details.toString(), result is HiddenPageResult.Found)
        result as HiddenPageResult.Found
        assertEquals(SitePageDataSource.HIDDEN_PAGE, result.data.source)
        assertTrue(result.data.json.contains("fixture-1080.mp4"))
        assertEquals(VIDEO_PAGE, result.finalUrl)
        assertTrue(result.userAgent.orEmpty().contains("Windows NT"))
        assertTrue("found in $seconds s", seconds < TikTokPageEngine.TIMEOUT_MILLIS / 1_000.0)
        assertTrue("destroyed", destroyed.await(WAIT_S, TimeUnit.SECONDS))
    }

    @Test
    fun theHiddenPageGivesAPostOfTheApiAnswersToo() {
        val destroyed = CountDownLatch(1)

        val result = runBlocking { engine(destroyed).read(VIDEO_PAGE, API_POST_ID) }

        assertTrue(result.details.toString(), result is HiddenPageResult.Found)
        assertTrue(result.details.first().contains("API answer"))
        assertTrue("destroyed", destroyed.await(WAIT_S, TimeUnit.SECONDS))
    }

    @Test
    fun aPageWithoutThePostEndsAfterTheTimeoutAndIsDestroyed() {
        val destroyed = CountDownLatch(1)
        val engine = engine(destroyed, timeoutMillis = SHORT_TIMEOUT_MILLIS)

        val result = runBlocking { engine.read(NO_ITEM_PAGE, UNKNOWN_ID) }

        assertTrue(result is HiddenPageResult.NotFound)
        assertTrue(
            result.details.toString(),
            result.details.first().startsWith("hidden page: no data after 3.0 s"),
        )
        assertTrue("destroyed", destroyed.await(WAIT_S, TimeUnit.SECONDS))
    }

    @Test
    fun theHiddenPageOpensNoOtherSite() {
        val destroyed = CountDownLatch(1)

        val result = runBlocking {
            engine(destroyed, timeoutMillis = SHORT_TIMEOUT_MILLIS)
                .read("https://example.com/@fixture_user/video/$POST_ID", POST_ID)
        }

        assertTrue(result is HiddenPageResult.NotFound)
        assertTrue(result.details.first().contains("address not allowed"))
        assertTrue("destroyed", destroyed.await(WAIT_S, TimeUnit.SECONDS))
        assertFalse(requested.any { it.contains("example.com") })
    }

    private val requested = java.util.concurrent.CopyOnWriteArrayList<String>()

    private fun engine(
        destroyed: CountDownLatch,
        timeoutMillis: Long = TikTokPageEngine.TIMEOUT_MILLIS,
    ): TikTokPageEngine = TikTokPageEngine(
        settings = TikTokPageSettings.OWNER,
        script = { source },
        windows = WebViewHiddenPages(
            app,
            scriptOrigins = setOf(ORIGIN),
            allowedHosts = setOf(HOST),
            fixtures = ::serve,
            onDestroyed = destroyed::countDown,
        ),
        cookies = NoCookies,
        timeoutMillis = timeoutMillis,
    )

    /** A tab like the browser's: the store at document start, else when the page starts. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun openTab(url: String) {
        val loaded = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val view = WebView(app)
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            val store = TikTokPageScript.store(source)
            val atStart = TikTokApiCapture.install(view, store, setOf(ORIGIN))
            view.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse = serve(request)

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    if (!atStart && TikTokPageScript.isScriptOrigin(url, setOf(ORIGIN))) {
                        view.evaluateJavascript(store, null)
                    }
                }

                override fun onPageFinished(view: WebView, url: String?) = loaded.countDown()
            }
            tab = view
            view.loadUrl(url)
        }
        assertTrue("fixture loaded", loaded.await(WAIT_S, TimeUnit.SECONDS))
        val polls = AtomicInteger()
        while (evaluate("window.__feed && (window.__feed.done || !!window.__feed.error)") !=
            "true"
        ) {
            assertTrue("the page's own API call finished", polls.incrementAndGet() < MAX_POLLS)
            Thread.sleep(POLL_MS)
        }
    }

    /** [script]'s result on the tab, as `evaluateJavascript` hands it over. */
    private fun evaluate(script: String): String? {
        val answered = CountDownLatch(1)
        val answer = AtomicReference<String?>()
        instrumentation.runOnMainSync {
            tab!!.evaluateJavascript(script) { result ->
                answer.set(result)
                answered.countDown()
            }
        }
        assertTrue("script answered", answered.await(WAIT_S, TimeUnit.SECONDS))
        return answer.get()
    }

    /** The fixture site: the video page, the page without the post, and the feed's API. */
    private fun serve(request: WebResourceRequest): WebResourceResponse {
        val url = request.url
        requested += url.toString()
        val file = when {
            url.host != HOST -> null
            url.path == "/@fixture_user/video/$POST_ID" -> "video.html"
            url.path == "/@fixture_user/video/$UNKNOWN_ID" -> "no_item.html"
            url.path == "/api/recommend/item_list/" -> "item_list.json"
            else -> null
        } ?: return WebResourceResponse(
            "text/plain",
            "UTF-8",
            404,
            "Not Found",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )
        val type = if (file.endsWith(".json")) "application/json" else "text/html"
        return WebResourceResponse(
            type,
            "UTF-8",
            200,
            "OK",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(asset(file).toByteArray()),
        )
    }

    private fun asset(name: String): String =
        instrumentation.context.assets.open("tiktok/$name").bufferedReader().use { it.readText() }

    private object NoCookies : TikTokCookies {
        override fun header(url: String): String? = null

        override fun clear(names: Set<String>) = Unit
    }

    private companion object {
        const val HOST = "fixture.yft.test"
        const val ORIGIN = "https://$HOST"
        const val POST_ID = "7311234567890123456"
        const val API_POST_ID = "7311234567890123457"
        const val UNKNOWN_ID = "7311234567890123999"
        const val VIDEO_PAGE = "$ORIGIN/@fixture_user/video/$POST_ID"
        const val NO_ITEM_PAGE = "$ORIGIN/@fixture_user/video/$UNKNOWN_ID"
        const val SHORT_TIMEOUT_MILLIS = 3_000L
        const val WAIT_S = 15L
        const val POLL_MS = 100L
        const val MAX_POLLS = 100
    }
}
