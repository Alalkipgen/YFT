package com.alal.yft.extractor.master.android

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.browser.detection.DownloadObservation
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.browser.webview.BrowserObservationSink
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WebViewPlaybackCaptureTest {
    @Test
    fun disabledProducerKeepsOriginalSinkAndNoScope() {
        val view = WebView(ApplicationProvider.getApplicationContext())
        val capture = WebViewPlaybackCapture(false)
        val sink = Sink()
        capture.attach(view)
        assertSame(sink, capture.decorate(sink, view))
        assertNull(capture.session.currentScope())
        view.destroy()
    }

    @Test
    fun attachmentReplacementAndOldCallbacksCannotClearTheNewTab() {
        val first = WebView(ApplicationProvider.getApplicationContext())
        val second = WebView(ApplicationProvider.getApplicationContext())
        val capture = WebViewPlaybackCapture(true)
        val sink = Sink()
        capture.attach(first)
        val old = capture.decorate(sink, first)
        old.onPageStarted("https://page.test/old")
        capture.attach(second)
        val current = capture.decorate(sink, second)
        current.onPageStarted("https://page.test/new")
        val scope = capture.session.currentScope()
        old.onPageStarted("https://page.test/stale")
        capture.detach(first)
        assertEquals(scope, capture.session.currentScope())
        assertEquals(2, sink.starts)
        capture.detach(second)
        assertNull(capture.session.currentScope())
        first.destroy()
        second.destroy()
    }

    @Test
    fun webViewAttachmentOffMainFailsBeforeTouchingTheView() {
        val view = WebView(ApplicationProvider.getApplicationContext())
        val error = AtomicReference<Throwable>()
        val capture = WebViewPlaybackCapture(true)
        val thread = Thread {
            try { capture.attach(view) } catch (failure: Throwable) { error.set(failure) }
        }
        thread.start()
        thread.join(2_000)
        assertTrue(error.get() is IllegalStateException)
        assertNull(capture.session.currentScope())
        view.destroy()
    }

    private class Sink : BrowserObservationSink {
        var starts = 0
        override fun onPageStarted(url: String) { starts++ }
        override fun onPageFinished(url: String, title: String?) = Unit
        override fun onUrlChanged(url: String) = Unit
        override fun onProgressChanged(progress: Int) = Unit
        override fun onRequest(observation: RequestObservation) = Unit
        override fun onDownload(observation: DownloadObservation) = Unit
        override fun onDomProbeResult(pageUrl: String, result: String?) = Unit
        override fun onMainFrameError(url: String?, description: String) = Unit
    }
}
