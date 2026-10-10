package com.alal.yft.master

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.browser.detection.DownloadObservation
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.browser.policy.SecureWebViewPolicy
import com.alal.yft.core.browser.webview.BrowserObservationSink
import com.alal.yft.core.browser.webview.SecureBrowserWebViewClient
import com.alal.yft.core.data.network.NetworkConfiguration
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.detection.DeviceMergeSupport
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.detection.SiteAdapterModule
import com.alal.yft.detection.SiteAdapterOutcome
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.PlaybackCaptureProvider
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.android.WebViewPlaybackCapture
import com.alal.yft.extractor.master.parity.ArmObservation
import com.alal.yft.extractor.master.parity.ParityArm
import com.alal.yft.extractor.master.parity.ParityCase
import com.alal.yft.extractor.master.parity.ParityEntry
import com.alal.yft.extractor.master.parity.ParityReport
import com.alal.yft.extractor.master.parity.ParityUrls
import com.alal.yft.extractor.master.parity.ParityVerdict
import com.alal.yft.extractor.master.verify.OkHttpMediaValidator
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R1 live parity (MASTER_KEY_PLAN §4.1): arm A is main's site lookup, wired exactly as the app
 * wires it; arm B is Master in the visible browser with its real one-byte checks. Opt-in only
 * (`-e yft.parity 1`), never in default CI; `scripts/canary.sh` runs it on a phone or emulator.
 *
 * The report (app files `parity/parity-report-*.json`) holds host and content ID only. Master
 * never plays a video: with `-e yft.parity.attended 1` the owner taps Play when the log says
 * "play now" (bot-check and sign-in walls need the user's own playback). Owner-picked links go
 * in an uncommitted `parity/parity-urls.local.json` in the same app folder.
 */
@RunWith(AndroidJUnit4::class)
class MasterParityLiveTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val args get() = InstrumentationRegistry.getArguments()
    private val capture = WebViewPlaybackCapture(enabled = true)
    private val finished = ConcurrentLinkedQueue<String>()
    private val renderBrowser = mutableStateOf(true)
    private var browser: WebView? = null

    @After
    fun releaseBrowser() {
        if (browser == null) return
        compose.runOnIdle { renderBrowser.value = false }
        compose.waitForIdle()
    }

    @Test
    fun writesASanitizedParityReport() {
        assumeTrue("Opt-in only: -e yft.parity 1", args.getString("yft.parity") == "1")
        val context = instrumentation.targetContext
        val folder = File(checkNotNull(context.getExternalFilesDir(null)), "parity")
        folder.mkdirs()
        val local = File(folder, "parity-urls.local.json")
        val list = if (local.isFile) local.readText() else {
            instrumentation.context.assets.open("parity/parity-urls.json")
                .bufferedReader().use { it.readText() }
        }
        val only = args.getString("yft.parity.only")?.split(',')?.map(String::trim)?.toSet()
        val cases = ParityUrls.parse(list).filter { only == null || it.id in only }
        val client = NetworkConfiguration.createClient()
        val sites = coordinator(client)
        show()
        val entries = cases.map { case ->
            val url = case.url ?: return@map ParityEntry(
                case.id, case.site, null, emptyList(), emptyList(), skipped = "owner picks",
            )
            val a = runBlocking { armA(sites, client, url) }
            val b = armB(case, url, a.first, sites, client)
            ParityEntry(
                case.id, case.site, ParityReport.host(url), listOf(a.second, b),
                ParityVerdict.problems(case, a.second, b),
            )
        }
        val json = ParityReport.toJson(entries, "epoch-ms ${System.currentTimeMillis()}")
        val report = File(folder, "parity-report-${System.currentTimeMillis()}.json")
        report.writeText(json)
        Log.i(
            TAG, "parity report ${report.name}: cases=${entries.size} " +
                "problems=${entries.sumOf { it.problems.size }}",
        )
        if (args.getString("yft.parity.strict") == "1") {
            assertTrue("parity problems; see ${report.name}", entries.all { it.problems.isEmpty() })
        }
    }

    /** Main's own adapters and flags (SiteAdapterModule), with no tab data and no hidden page. */
    private fun coordinator(client: OkHttpClient): SiteAdapterCoordinator {
        val context = instrumentation.targetContext
        val extractors = SiteAdapterModule.provideSiteExtractors(
            context, client, SiteAdapterModule.provideExtractorHttpClient(client),
            SiteAdapterModule.providePlayerScriptRunner(context, client),
            SiteAdapterModule.providePoTokenProvider(context, client),
        )
        return SiteAdapterCoordinator(
            SiteExtractorRegistry(extractors, SiteAdapterModule.provideSiteAdapterFlags()),
            DeviceMergeSupport(),
        )
    }

    private suspend fun armA(
        sites: SiteAdapterCoordinator,
        client: OkHttpClient,
        url: String,
    ): Pair<SiteAdapterOutcome, ArmObservation> {
        val started = SystemClock.elapsedRealtime()
        val outcome = sites.inspect(
            url, BrowserRequestContext(url, null, null), System.currentTimeMillis(), fresh = true,
        )
        val total = SystemClock.elapsedRealtime() - started
        val observation = when (outcome) {
            is SiteAdapterOutcome.Detected -> {
                val rows = outcome.candidates.take(MAX_ROWS)
                val validator = OkHttpMediaValidator(client)
                val opened = rows.associateWith {
                    validator.validate(it, System.currentTimeMillis()) is ValidationResult.Valid
                }
                ParityReport.found(ParityArm.A, rows, { opened[it] }, total)
            }
            is SiteAdapterOutcome.Failed ->
                ArmObservation(ParityArm.A, outcome.reason.name, totalMs = total)
            SiteAdapterOutcome.NotHandled ->
                ArmObservation(ParityArm.A, ParityCase.NOT_HANDLED, totalMs = total)
        }
        return outcome to observation
    }

    private fun armB(
        case: ParityCase,
        url: String,
        primary: SiteAdapterOutcome,
        sites: SiteAdapterCoordinator,
        client: OkHttpClient,
    ): ArmObservation {
        val failure = (primary as? SiteAdapterOutcome.Failed)?.reason
            ?: SiteExtractionFailure.RESPONSE_CHANGED
        val started = SystemClock.elapsedRealtime()
        finished.clear()
        instrumentation.runOnMainSync { checkNotNull(browser).loadUrl(url) }
        waitFor(PAGE_WAIT_MS) { finished.isNotEmpty() }
        Thread.sleep(SETTLE_MS)
        if (args.getString("yft.parity.attended") == "1") {
            Log.i(TAG, "play now: ${case.id}")
            val seconds = args.getString("yft.parity.playSec")?.toLongOrNull() ?: 60
            waitFor(seconds * 1_000) { playing() }
        }
        val calls = AtomicInteger()
        val real = OkHttpMediaValidator(client)
        val validator = MasterMediaValidator { found, now ->
            calls.incrementAndGet()
            real.validate(found, now)
        }
        val counted = PlaybackCaptureProvider { calls.incrementAndGet(); capture.capture(it) }
        val result = runBlocking {
            val request = capture.request(
                failure, System.currentTimeMillis(), sites.videoKey(url)?.substringAfter(':'),
            ) ?: return@runBlocking null
            MasterFallbackEngine(validator, counted, MasterPolicy(enabled = true))
                .extract(request.copy(snapshot = null))
        }
        val total = SystemClock.elapsedRealtime() - started
        return when (result) {
            is MasterResult.Success ->
                ParityReport.found(ParityArm.B, result.result.candidates, { true }, total)
            is MasterResult.Failure ->
                ArmObservation(ParityArm.B, result.reason.name, totalMs = total)
            is MasterResult.Skipped -> ArmObservation(
                ParityArm.B, result.reason.name, totalMs = total, afterTerminal = calls.get(),
            )
            is MasterResult.NeedsPlayback -> ArmObservation(ParityArm.B, "needs_playback")
            is MasterResult.NeedsSelection -> ArmObservation(ParityArm.B, "needs_selection")
            null -> ArmObservation(ParityArm.B, "capture_unavailable")
        }
    }

    /** Reads the page's player state only; never plays, seeks or clicks. */
    private fun playing(): Boolean = evaluate(
        "(function(){return Array.from(document.querySelectorAll('video')).some(function(v){" +
            "return !v.paused&&v.readyState>=2&&v.currentTime>0.5;});})()",
    ) == "true"

    private fun evaluate(script: String): String? {
        val response = AtomicReference<String?>()
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync {
            checkNotNull(browser).evaluateJavascript(script) {
                response.set(it)
                done.countDown()
            }
        }
        done.await(10, TimeUnit.SECONDS)
        return response.get()
    }

    private fun waitFor(millis: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + millis
        while (SystemClock.elapsedRealtime() < deadline && !condition()) Thread.sleep(500)
    }

    private fun show() {
        val sink = object : BrowserObservationSink {
            override fun onPageStarted(url: String) = Unit
            override fun onPageFinished(url: String, title: String?) { finished.add(url) }
            override fun onUrlChanged(url: String) = Unit
            override fun onProgressChanged(progress: Int) = Unit
            override fun onRequest(observation: RequestObservation) = Unit
            override fun onDownload(observation: DownloadObservation) = Unit
            override fun onDomProbeResult(pageUrl: String, result: String?) = Unit
            override fun onMainFrameError(url: String?, description: String) = Unit
        }
        compose.setContent {
            if (!renderBrowser.value) return@setContent
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
                            ): WebResourceResponse? = secure.shouldInterceptRequest(view, request)
                        }
                        browser = this
                    }
                },
                onRelease = { view ->
                    capture.detach(view)
                    view.stopLoading()
                    view.onPause()
                    view.destroy()
                    browser = null
                },
            )
        }
        compose.waitUntil(30_000) { browser != null }
    }

    private companion object {
        const val TAG = "YftParity"
        const val MAX_ROWS = 12
        const val PAGE_WAIT_MS = 45_000L
        const val SETTLE_MS = 3_000L
    }
}
