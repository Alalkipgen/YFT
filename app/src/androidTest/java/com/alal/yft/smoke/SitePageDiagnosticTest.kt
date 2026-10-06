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
    fun facebookShareLink() = diagnose("fb-share", FACEBOOK_SHARE)

    @Test
    fun facebookShareLinkWideViewport() =
        diagnose("fb-share-wide", FACEBOOK_SHARE, wideViewport = true)

    @Test
    fun tiktokVideo() = diagnose("tt-video", TIKTOK_VIDEO)

    private fun diagnose(case: String, url: String, wideViewport: Boolean = false) {
        runCatching {
            composeRule.waitUntil(20_000) { hasNode("home-open-browser") }
            composeRule.onNodeWithTag("home-open-browser").performClick()
            navigateTo(WARM_UP_PAGE)
            composeRule.waitUntil(30_000) { findWebView() != null }
            val webView = checkNotNull(findWebView())
            SystemClock.sleep(3_000)
            if (wideViewport) {
                instrumentation.runOnMainSync {
                    webView.settings.useWideViewPort = true
                    webView.settings.loadWithOverviewMode = true
                }
            }
            navigateTo(url)
            // The compose clock only advances while the test talks to compose: poll through it,
            // so the navigation starts at once and the Download button's first time is known.
            val chain = linkedSetOf<String>()
            val start = SystemClock.uptimeMillis()
            var fabAt = -1L
            var early = ""
            while (SystemClock.uptimeMillis() - start < LOAD_WAIT_MS) {
                chain += safeAddress(mainFrameUrl(webView))
                if (fabAt < 0 && hasDownloadButton()) {
                    fabAt = (SystemClock.uptimeMillis() - start) / 1_000
                }
                if (early.isEmpty() && SystemClock.uptimeMillis() - start > EARLY_PROBE_MS) {
                    early = evaluate(webView)
                    log(case, "early", "${pixelStats(webView)} $early")
                }
                SystemClock.sleep(500)
            }
            log(
                case,
                "load",
                "chain=${chain.joinToString(">")} fabAt=${fabAt}s " +
                    "found=${hasNode("media-found-button")} " +
                    "notice=${hasNode("browser-site-notice")} ${pixelStats(webView)} " +
                    evaluate(webView),
            )
            saveScreenshot("p2-$case")
            tapCenter(webView)
            SystemClock.sleep(TAP_WAIT_MS)
            log(
                case,
                "tap",
                "url=${safeAddress(mainFrameUrl(webView))} " +
                    "fab=${hasDownloadButton()} " +
                    "notice=${hasNode("browser-site-notice")} ${pixelStats(webView)} " +
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

    /** P13: a video page shows the wide Download button, other pages the round one. */
    private fun hasDownloadButton(): Boolean =
        hasNode("browser-download-fab") || hasNode("browser-download-wide")

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
        const val LOAD_WAIT_MS = 20_000L
        const val EARLY_PROBE_MS = 7_000L
        const val TAP_WAIT_MS = 6_000L
        private const val SAFE_PAIR = """[A-Za-z]+=[A-Za-z0-9._/:-]{0,160}"""
        val SAFE_PROBE = Regex("$SAFE_PAIR( $SAFE_PAIR)*")

        /** Host and path only, with anything unusual replaced: no query, fragment or user info. */
        fun safeAddress(url: String?): String {
            val uri = url?.let(Uri::parse) ?: return "none"
            val scheme = uri.scheme ?: return "none"
            if (scheme != "https" && scheme != "http") return scheme.take(12)
            val path = (uri.encodedPath ?: "").replace(Regex("[^A-Za-z0-9._/-]"), "_").take(48)
            return "${uri.host}$path"
        }

        val PAGE_PROBE = """
            (function () {
              function c(s) { return String(s).replace(/[^A-Za-z0-9._\/:-]/g, '_').slice(0, 40); }
              var b = document.body, t = (b && b.innerText) || '';
              var vs = document.querySelectorAll('video');
              var vv = window.visualViewport;
              var o = ['ready=' + c(document.readyState), 'text=' + t.length,
                'videos=' + vs.length, 'imgs=' + document.images.length,
                'win=' + innerWidth + 'x' + innerHeight, 'dpr=' + devicePixelRatio,
                'vv=' + (vv ? Math.round(vv.width) + 'x' + Math.round(vv.height) : 'none'),
                'doc=' + document.documentElement.clientWidth + 'x' +
                  document.documentElement.clientHeight,
                'scroll=' +
                  (document.scrollingElement ? document.scrollingElement.scrollHeight : 0),
                'dark=' + matchMedia('(prefers-color-scheme: dark)').matches,
                'bg=' + c(b ? getComputedStyle(b).backgroundColor : 'none'),
                'login=' + /log in|log into|sign in|sign up/i.test(t),
                'wv=' + /; wv\)/.test(navigator.userAgent)];
              var e = document.elementFromPoint(innerWidth / 2, innerHeight / 2);
              o.push('center=' + (e ? c(e.tagName) : 'none'));
              var names = ['a', 'b', 'c'];
              for (var i = 0; i < Math.min(vs.length, 3); i++) {
                var v = vs[i], r = v.getBoundingClientRect(), st = getComputedStyle(v);
                o.push('v' + names[i] + '=' + v.readyState + '-' + v.networkState + '-' +
                  (v.paused ? 'P' : 'R') + '-' + v.videoWidth + 'x' + v.videoHeight + '-' +
                  Math.round(r.width) + 'x' + Math.round(r.height) + '-' + c(st.display) + '-' +
                  c(st.visibility) + '-' + c(st.height) + '-' + c(st.position) + '-' +
                  (v.error ? v.error.code : 0) + '-' + c((v.currentSrc || '').split(':')[0]));
                var a = v.parentElement, chain = [];
                for (var k = 0; k < 6 && a; k++, a = a.parentElement) {
                  var ar = a.getBoundingClientRect(), as = getComputedStyle(a);
                  chain.push(c(a.tagName) + Math.round(ar.height) + c(as.display).slice(0, 4) +
                    c(as.position).slice(0, 3));
                }
                o.push('p' + names[i] + '=' + chain.join('-'));
              }
              return o.join(' ');
            })()
        """.trimIndent()
    }
}
