package com.alal.yft.smoke

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.alal.yft.MainActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONTokener
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P2 evidence: how public Facebook and TikTok pages render in the production browser on a real
 * WebView.
 *
 * Diagnostic only. It logs safe numbers and markers (never page text, cookies or complete
 * addresses) as `YFT-DIAG` lines that `ci-smoke-diagnostics.py` turns into one annotation, and it
 * never fails the run: live sites change, and CI networks get login walls a phone does not.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SitePageDiagnosticTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    fun facebookShareLink() = diagnose("fb-share", FACEBOOK_SHARE, chromeLike = false)

    @Test
    fun facebookShareLinkChromeIdentity() =
        diagnose("fb-share-ua", FACEBOOK_SHARE, chromeLike = true)

    @Test
    fun facebookMobileShareLink() = diagnose("fb-mshare", FACEBOOK_M_SHARE, chromeLike = false)

    @Test
    fun facebookMobileShareLinkChromeIdentity() =
        diagnose("fb-mshare-ua", FACEBOOK_M_SHARE, chromeLike = true)

    @Test
    fun tiktokVideo() = diagnose("tt-video", TIKTOK_VIDEO, chromeLike = false)

    @Test
    fun tiktokVideoChromeIdentity() = diagnose("tt-video-ua", TIKTOK_VIDEO, chromeLike = true)

    private fun diagnose(case: String, url: String, chromeLike: Boolean) {
        runCatching {
            composeRule.waitUntil(20_000) { hasNode("home-open-browser") }
            composeRule.onNodeWithTag("home-open-browser").performClick()
            navigateTo(WARM_UP_PAGE)
            composeRule.waitUntil(30_000) { findWebView() != null }
            val webView = checkNotNull(findWebView())
            SystemClock.sleep(3_000)
            if (chromeLike) {
                instrumentation.runOnMainSync {
                    webView.settings.userAgentString =
                        chromeLikeUserAgent(webView.settings.userAgentString)
                }
            }
            navigateTo(url)
            val chain = linkedSetOf<String>()
            val deadline = SystemClock.uptimeMillis() + LOAD_WAIT_MS
            while (SystemClock.uptimeMillis() < deadline) {
                chain += safeAddress(mainFrameUrl(webView))
                SystemClock.sleep(500)
            }
            log(
                case,
                "load",
                "chain=${chain.joinToString(">")} fab=${hasNode("browser-download-fab")} " +
                    "found=${hasNode("media-found-button")} ${pixelStats(webView)} " +
                    evaluate(webView),
            )
            saveScreenshot("p2-$case")
            tapCenter(webView)
            SystemClock.sleep(TAP_WAIT_MS)
            log(
                case,
                "tap",
                "url=${safeAddress(mainFrameUrl(webView))} " +
                    "fab=${hasNode("browser-download-fab")} ${pixelStats(webView)} " +
                    evaluate(webView),
            )
            saveScreenshot("p2-$case-tap")
        }.onFailure { error ->
            Log.w(TAG, "YFT-DIAG $case error type=${error.javaClass.simpleName}")
        }
    }

    private fun navigateTo(url: String) {
        composeRule.onNodeWithTag("browser-address")
            .performClick()
            .performTextReplacement(url)
        composeRule.onNodeWithTag("browser-go").performClick()
    }

    private fun hasNode(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun findWebView(): WebView? {
        var found: WebView? = null
        instrumentation.runOnMainSync {
            found = composeRule.activity.window.decorView.firstWebView()
        }
        return found
    }

    private fun View.firstWebView(): WebView? = when (this) {
        is WebView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).firstWebView() }
        else -> null
    }

    private fun mainFrameUrl(webView: WebView): String? {
        var url: String? = null
        instrumentation.runOnMainSync { url = webView.url }
        return url
    }

    private fun evaluate(webView: WebView): String {
        val latch = CountDownLatch(1)
        var result: String? = null
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(PAGE_PROBE) { value ->
                result = value
                latch.countDown()
            }
        }
        if (!latch.await(10, TimeUnit.SECONDS)) return "probe=timeout"
        val decoded = runCatching { JSONTokener(result).nextValue() as? String }.getOrNull()
        return decoded?.takeIf { SAFE_PROBE.matches(it) } ?: "probe=unreadable"
    }

    private fun screenRect(webView: WebView): Rect {
        val rect = Rect()
        instrumentation.runOnMainSync {
            val location = IntArray(2)
            webView.getLocationOnScreen(location)
            rect.set(
                location[0],
                location[1],
                location[0] + webView.width,
                location[1] + webView.height,
            )
        }
        return rect
    }

    /** Share of near-black and near-white pixels and how many colours the page area shows. */
    private fun pixelStats(webView: WebView): String {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return "pixels=none"
        try {
            val area = screenRect(webView)
            if (!area.intersect(0, 0, bitmap.width, bitmap.height)) return "pixels=offscreen"
            var total = 0
            var dark = 0
            var light = 0
            val colours = HashSet<Int>()
            var y = area.top
            while (y < area.bottom) {
                var x = area.left
                while (x < area.right) {
                    val pixel = bitmap.getPixel(x, y)
                    val red = Color.red(pixel)
                    val green = Color.green(pixel)
                    val blue = Color.blue(pixel)
                    total++
                    if (maxOf(red, green, blue) < 40) dark++
                    if (minOf(red, green, blue) > 215) light++
                    colours += (red shr 4 shl 8) or (green shr 4 shl 4) or (blue shr 4)
                    x += 4
                }
                y += 4
            }
            if (total == 0) return "pixels=empty"
            return "dark=${percent(dark, total)} light=${percent(light, total)} " +
                "colours=${colours.size}"
        } finally {
            bitmap.recycle()
        }
    }

    private fun percent(part: Int, total: Int): String = "${part * 100 / total}pc"

    private fun tapCenter(webView: WebView) {
        val area = screenRect(webView)
        UiDevice.getInstance(instrumentation).click(area.centerX(), area.centerY())
    }

    private fun saveScreenshot(name: String) {
        val context = instrumentation.targetContext
        val directory = context.getExternalFilesDir("smoke-screenshots") ?: return
        if (!directory.exists() && !directory.mkdirs()) return
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        try {
            File(directory, "$name.png").outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun log(case: String, phase: String, text: String) {
        Log.i(TAG, "YFT-DIAG $case $phase ${text.replace(Regex("\\s+"), " ").trim()}")
    }

    private companion object {
        const val TAG = "YFTSmoke"
        const val WARM_UP_PAGE = "https://example.com/"
        const val FACEBOOK_SHARE = "https://www.facebook.com/share/v/1Q3kAyptrS/"
        const val FACEBOOK_M_SHARE = "https://m.facebook.com/share/v/1Q3kAyptrS/"
        const val TIKTOK_VIDEO = "https://www.tiktok.com/@scout2015/video/6718335390845095173"
        const val LOAD_WAIT_MS = 25_000L
        const val TAP_WAIT_MS = 6_000L
        private const val SAFE_PAIR = """[A-Za-z]+=[A-Za-z0-9._/:-]{0,60}"""
        val SAFE_PROBE = Regex("$SAFE_PAIR( $SAFE_PAIR)*")

        /** Host and path only, with anything unusual replaced: no query, fragment or user info. */
        fun safeAddress(url: String?): String {
            val uri = url?.let(Uri::parse) ?: return "none"
            val scheme = uri.scheme ?: return "none"
            if (scheme != "https" && scheme != "http") return scheme.take(12)
            val path = (uri.encodedPath ?: "").replace(Regex("[^A-Za-z0-9._/-]"), "_").take(48)
            return "${uri.host}$path"
        }

        /** The WebView identity without the `; wv` token and `Version/4.0`, like Chrome. */
        fun chromeLikeUserAgent(webViewAgent: String): String =
            webViewAgent.replace("; wv)", ")").replace(Regex("""Version/\d+(\.\d+)* """), "")

        val PAGE_PROBE = """
            (function () {
              function c(s) { return String(s).replace(/[^A-Za-z0-9._\/:-]/g, '_').slice(0, 60); }
              var b = document.body, t = (b && b.innerText) || '';
              var v = document.querySelectorAll('video'), f = v[0];
              var o = ['ready=' + c(document.readyState), 'text=' + t.length,
                'videos=' + v.length, 'iframes=' + document.querySelectorAll('iframe').length,
                'imgs=' + document.images.length,
                'bg=' + c(b ? getComputedStyle(b).backgroundColor : 'none'),
                'login=' + /log in|log into|sign in|sign up/i.test(t),
                'openApp=' + !!document.querySelector('[aria-label="Open app"]'),
                'unavailable=' + /isn.t available|not available|unavailable/i.test(t),
                'unsupported=' + /unsupported browser|update your browser/i.test(t),
                'wv=' + /; wv\)/.test(navigator.userAgent)];
              if (f) {
                var r = f.getBoundingClientRect(), s = f.currentSrc || f.src || '';
                o.push('vReady=' + f.readyState, 'vNet=' + f.networkState,
                  'vPaused=' + f.paused, 'vSize=' + f.videoWidth + 'x' + f.videoHeight,
                  'vBox=' + Math.round(r.width) + 'x' + Math.round(r.height),
                  'vSrc=' + c(s.split(':')[0]), 'vErr=' + (f.error ? f.error.code : 0),
                  'vPoster=' + !!f.poster);
              }
              return o.join(' ');
            })()
        """.trimIndent()
    }
}
