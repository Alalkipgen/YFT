package com.alal.yft.master

import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.alal.yft.core.browser.detection.DownloadObservation
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.browser.policy.SecureWebViewPolicy
import com.alal.yft.core.browser.webview.BrowserObservationSink
import com.alal.yft.core.browser.webview.SecureBrowserWebViewClient
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.OkHttpMediaValidator
import com.alal.yft.extractor.master.android.WebViewPlaybackCapture
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android Chromium + neutral offline fixtures, never an SSL-error bypass. */
@RunWith(AndroidJUnit4::class)
class MasterCaptureInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val capture = WebViewPlaybackCapture(enabled = true)
    private val completed = ConcurrentLinkedQueue<String>()
    private val probes = AtomicInteger()
    private var browser: WebView? = null
    private val bytes by lazy {
        instrumentation.context.assets.open("mux/video-avc.mp4").use { it.readBytes() }
    }

    @Test
    fun pausedPreloadDoesNotAuthorizeBotCheckRecovery() {
        show()
        val result = extract(SiteExtractionFailure.BOT_CHECK)
        assertTrue(result is MasterResult.NeedsPlayback)
        assertEquals(0, probes.get())
    }

    @Test
    fun userPlaybackFeedsOneCaptureAndRealFilePrefixValidation() {
        show()
        tapPlay()
        val result = extract(SiteExtractionFailure.BOT_CHECK) as MasterResult.Success
        assertEquals(MasterStage.PLAYBACK_CAPTURE, result.stage)
        assertEquals(MEDIA, result.result.candidates.single().mediaUrl)
        assertEquals(bytes.size.toLong(), result.result.candidates.single().contentLengthBytes)
        assertEquals(1, probes.get())
        instrumentation.runOnMainSync {
            assertTrue(checkNotNull(browser).settings.mediaPlaybackRequiresUserGesture)
            assertFalse(checkNotNull(browser).settings.allowFileAccess)
            assertFalse(checkNotNull(browser).settings.allowContentAccess)
        }
    }

    @Test
    fun realNavigationAndDisposalRejectTheOldGeneration() {
        show()
        val old = runBlocking {
            checkNotNull(capture.request(SiteExtractionFailure.NO_MEDIA_FOUND, now()))
        }
        instrumentation.runOnMainSync { checkNotNull(browser).loadUrl(NEXT_PAGE) }
        compose.waitUntil(30_000) { completed.contains(NEXT_PAGE) }
        assertTrue(capture.session.currentScope()!!.generation > old.generation)
        assertEquals(CaptureResult.Unavailable, runBlocking { capture.capture(old) })
        instrumentation.runOnMainSync { capture.detach(checkNotNull(browser)) }
        assertNull(capture.session.currentScope())
        assertEquals(CaptureResult.Unavailable, runBlocking { capture.capture(old) })
    }

    @Test
    fun encryptedPlayerSignalRefusesCaptureBeforeAProbe() {
        show()
        tapPlay()
        instrumentation.runOnMainSync {
            checkNotNull(browser).evaluateJavascript(
                "document.querySelector('video').dispatchEvent(new Event('encrypted'))",
                null,
            )
        }
        val result = extract(SiteExtractionFailure.NO_MEDIA_FOUND) as MasterResult.Failure
        assertEquals(SiteExtractionFailure.DRM_PROTECTED, result.reason)
        assertEquals(0, probes.get())
    }

    private fun extract(failure: SiteExtractionFailure): MasterResult = runBlocking {
        val request = checkNotNull(capture.request(failure, now())).copy(snapshot = null)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            check(chain.request().url.toString() == MEDIA)
            check(chain.request().header("Range") == "bytes=0-511")
            probes.incrementAndGet()
            val prefix = bytes.copyOfRange(0, minOf(512, bytes.size))
            Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(206).message("Partial Content")
                .header("Content-Type", "video/mp4")
                .header("Content-Range", "bytes 0-${prefix.lastIndex}/${bytes.size}")
                .body(prefix.toResponseBody())
                .build()
        }.build()
        MasterFallbackEngine(
            OkHttpMediaValidator(client), capture, MasterPolicy(enabled = true),
        ).extract(request)
    }

    private fun show() {
        val sink = object : BrowserObservationSink {
            override fun onPageStarted(url: String) = Unit
            override fun onPageFinished(url: String, title: String?) { completed.add(url) }
            override fun onUrlChanged(url: String) = Unit
            override fun onProgressChanged(progress: Int) = Unit
            override fun onRequest(observation: RequestObservation) = Unit
            override fun onDownload(observation: DownloadObservation) = Unit
            override fun onDomProbeResult(pageUrl: String, result: String?) = Unit
            override fun onMainFrameError(url: String?, description: String) = Unit
        }
        compose.setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        SecureWebViewPolicy.apply(this)
                        capture.attach(this)
                        val secure = SecureBrowserWebViewClient(
                            capture.decorate(sink, this), { null }, { settings.userAgentString },
                        )
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView, url: String?, favicon: Bitmap?,
                            ) = secure.onPageStarted(view, url, favicon)
                            override fun onPageFinished(view: WebView, url: String?) =
                                secure.onPageFinished(view, url)
                            override fun doUpdateVisitedHistory(
                                view: WebView, url: String?, isReload: Boolean,
                            ) = secure.doUpdateVisitedHistory(view, url, isReload)
                            override fun shouldInterceptRequest(
                                view: WebView, request: WebResourceRequest,
                            ): WebResourceResponse? {
                                secure.shouldInterceptRequest(view, request)?.let { return it }
                                return fixture(request)
                            }
                        }
                        browser = this
                        loadUrl(PAGE)
                    }
                },
                onRelease = { view ->
                    capture.detach(view)
                    view.stopLoading()
                    view.destroy()
                    browser = null
                },
            )
        }
        compose.waitUntil(30_000) { completed.contains(PAGE) }
        // Let onPageFinished's collector bind and the metadata preload settle.
        Thread.sleep(600)
    }

    private fun fixture(request: WebResourceRequest): WebResourceResponse {
        val url = request.url.toString()
        val video = url == MEDIA
        val body = if (video) bytes else if (url.endsWith("/data.json")) {
            """{"video_url":"$MEDIA"}""".toByteArray()
        } else HTML.toByteArray()
        val mime = if (video) "video/mp4" else if (url.endsWith(".json")) {
            "application/json"
        } else "text/html"
        val range = request.requestHeaders.entries
            .firstOrNull { it.key.equals("Range", true) }?.value
        val partial = video && range?.startsWith("bytes=") == true
        val first = if (partial) range!!.substringAfter('=').substringBefore('-')
            .toIntOrNull()?.coerceIn(0, body.lastIndex) ?: 0 else 0
        val last = if (partial) range!!.substringAfter('-').toIntOrNull()
            ?.coerceIn(first, body.lastIndex) ?: body.lastIndex else body.lastIndex
        val segment = body.copyOfRange(first, last + 1)
        val headers = mutableMapOf(
            "Content-Type" to mime, "Content-Length" to segment.size.toString(),
            "Accept-Ranges" to "bytes",
        )
        if (partial) headers["Content-Range"] = "bytes $first-$last/${body.size}"
        return WebResourceResponse(
            mime, if (video) null else "UTF-8", if (partial) 206 else 200,
            if (partial) "Partial Content" else "OK", headers, ByteArrayInputStream(segment),
        )
    }

    private fun tapPlay() {
        val point = IntArray(2)
        instrumentation.runOnMainSync {
            val view = checkNotNull(browser)
            view.getLocationOnScreen(point)
            point[0] += view.width / 2
            point[1] += view.height / 2
        }
        UiDevice.getInstance(instrumentation).click(point[0], point[1])
        Thread.sleep(500)
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val PAGE = "https://master-capture.test/watch"
        private const val NEXT_PAGE = "https://master-capture.test/next"
        private const val MEDIA = "https://master-capture.test/main.mp4"
        private val HTML = """
            <!doctype html><meta name="viewport" content="width=device-width, initial-scale=1">
            <style>body{margin:0}video{width:240px;height:180px}
            button{position:fixed;left:50%;top:50%;transform:translate(-50%,-50%);
            width:160px;height:60px}</style>
            <video muted loop preload="metadata"><source src="$MEDIA" type="video/mp4"></video>
            <button onclick="document.querySelector('video').play();
            fetch('/data.json');">Play fixture</button>
        """.trimIndent()
    }
}
