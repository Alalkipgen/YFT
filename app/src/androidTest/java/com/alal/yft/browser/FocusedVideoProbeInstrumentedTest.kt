package com.alal.yft.browser

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.MainActivity
import com.alal.yft.core.browser.detection.FocusedVideo
import com.alal.yft.core.browser.detection.FocusedVideo.Source
import com.alal.yft.core.browser.detection.FocusedVideoProbe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P5: the focused-video script on a real WebView, over sanitized feed fixtures (invented IDs, no
 * real page text) served under the feed's own address. Offline: nothing is fetched.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class FocusedVideoProbeInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var webView: WebView? = null

    @Before
    fun openWindow() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun closeWindow() {
        instrumentation.runOnMainSync {
            webView?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
            webView = null
        }
        scenario.close()
    }

    @Test
    fun theYouTubeHomeFeedVideoInTheMiddleOfTheScreenIsFound() {
        assertEquals(
            FocusedVideo("https://www.youtube.com/watch?v=BBBBBBBBBB2", Source.CENTRE),
            focusedOn("youtube-home.html", YOUTUBE_HOME),
        )
    }

    @Test
    fun aPlayingYouTubePreviewWinsOverTheVideoInTheMiddle() {
        assertEquals(
            FocusedVideo("https://www.youtube.com/watch?v=PPPPPPPPPP5", Source.PLAYING),
            focusedOn("youtube-home-playing.html", YOUTUBE_HOME),
        )
    }

    @Test
    fun aShortIsItsOwnPagesVideo() {
        assertEquals(
            FocusedVideo("https://www.youtube.com/shorts/SSSSSSSSSS6", Source.PAGE),
            focusedOn("youtube-home.html", SHORT_PAGE),
        )
    }

    @Test
    fun theFacebookPostBesideThePlayingVideoIsFound() {
        assertEquals(
            FocusedVideo(
                "https://www.facebook.com/story.php" +
                    "?story_fbid=pfbid0Fixture2Def&id=100000000000002",
                Source.PLAYING,
            ),
            focusedOn("facebook-feed.html", "https://m.facebook.com/"),
        )
    }

    @Test
    fun theTikTokVideoInTheMiddleOfTheScreenIsFound() {
        assertEquals(
            FocusedVideo(
                "https://www.tiktok.com/@second_user/video/7300000000000000002",
                Source.CENTRE,
            ),
            focusedOn("tiktok-feed.html", "https://www.tiktok.com/foryou"),
        )
    }

    @Test
    fun aFeedWithoutVideosAnswersNone() {
        val answer = run(EMPTY_PAGE, YOUTUBE_HOME)

        assertTrue(answer.orEmpty().contains("none"))
        assertNull(FocusedVideoProbe.parse(answer, YOUTUBE_HOME))
    }

    private fun focusedOn(fixture: String, pageUrl: String): FocusedVideo? {
        val html = instrumentation.context.assets.open("focused-video/$fixture")
            .bufferedReader()
            .use { it.readText() }
        return FocusedVideoProbe.parse(run(html, pageUrl), pageUrl)
    }

    /** Loads [html] as the page at [pageUrl] in a full-window WebView and runs the script. */
    private fun run(html: String, pageUrl: String): String? {
        val loaded = CountDownLatch(1)
        scenario.onActivity { activity ->
            val view = WebView(activity).apply {
                @Suppress("SetJavaScriptEnabled")
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) = loaded.countDown()
                }
            }
            activity.addContentView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            webView = view
            view.loadDataWithBaseURL(pageUrl, html, "text/html", "UTF-8", null)
        }
        assertTrue("fixture loaded", loaded.await(LOAD_TIMEOUT_S, TimeUnit.SECONDS))
        // The fixture scrolls itself while it loads; give layout one more frame to settle.
        Thread.sleep(SETTLE_MS)
        val answered = CountDownLatch(1)
        val answer = AtomicReference<String?>()
        instrumentation.runOnMainSync {
            webView!!.evaluateJavascript(FocusedVideoProbe.script) { result ->
                answer.set(result)
                answered.countDown()
            }
        }
        assertTrue("script answered", answered.await(LOAD_TIMEOUT_S, TimeUnit.SECONDS))
        return answer.get()
    }

    private companion object {
        const val YOUTUBE_HOME = "https://m.youtube.com/"
        const val SHORT_PAGE = "https://m.youtube.com/shorts/SSSSSSSSSS6?feature=share"
        const val LOAD_TIMEOUT_S = 15L
        const val SETTLE_MS = 500L
        const val EMPTY_PAGE =
            "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width\">" +
                "</head><body><p>Nothing to watch</p><a href=\"/feed/library\">Library</a>" +
                "</body></html>"
    }
}
