package com.alal.yft.feature.browser

import com.alal.yft.core.browser.detection.DownloadObservation
import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.browser.webview.BrowserObservationSink
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserHistoryRecorderTest {
    private val visits = mutableListOf<String>()
    private val titles = mutableListOf<String>()
    private val recorder = BrowserHistoryRecorder(
        onVisit = { url, title -> visits += "$url|$title" },
        onTitle = { url, title -> titles += "$url|$title" },
    )

    @Test
    fun aFinishedPageIsAVisitOnceAndAFailedOneIsNone() {
        recorder.pageStarted(NEWS)
        recorder.pageFinished(NEWS, "News")
        recorder.pageFinished(NEWS, "News") // the same document finishing twice

        recorder.pageStarted(MISSING)
        recorder.mainFrameError(MISSING) // HTTP 404 or no connection
        recorder.pageFinished(MISSING, "Not found")

        recorder.pageStarted(NEWS) // reload or back: a new visit
        recorder.pageFinished(NEWS, "News")

        assertEquals(listOf("$NEWS|News", "$NEWS|News"), visits)
    }

    @Test
    fun aRedirectWhileThePageLoadsIsOneVisitOfWhereItLanded() {
        recorder.pageStarted("https://t.example/r/abc")
        recorder.urlChanged("https://example.com/landing", "t.example")
        recorder.pageFinished("https://example.com/landing", "Landing")

        assertEquals(listOf("https://example.com/landing|Landing"), visits)
    }

    @Test
    fun aSinglePageSitesOwnNavigationIsAVisitWhoseTitleFollows() {
        recorder.pageStarted(YOUTUBE)
        recorder.pageFinished(YOUTUBE, "YouTube")
        // The video opens without a new document; the old page's title is not taken.
        recorder.urlChanged(VIDEO, "YouTube")
        recorder.titleChanged(VIDEO, "A song - YouTube")
        recorder.titleChanged(VIDEO, "A song - YouTube")
        // A fragment or a tracking parameter is the same page.
        recorder.urlChanged("$VIDEO#comments", "A song - YouTube")
        recorder.urlChanged("$VIDEO&utm_source=share", "A song - YouTube")
        recorder.urlChanged(SECOND, "Another song - YouTube")

        assertEquals(
            listOf("$YOUTUBE|YouTube", "$VIDEO|null", "$SECOND|Another song - YouTube"),
            visits,
        )
        assertEquals(listOf("$VIDEO|A song - YouTube"), titles)
    }

    @Test
    fun aTitleBeforeThePageFinishedOrOfAnotherPageRenamesNothing() {
        recorder.pageStarted(NEWS)
        recorder.titleChanged(NEWS, "News")
        recorder.pageFinished(NEWS, "News")
        recorder.titleChanged(YOUTUBE, "YouTube")
        recorder.titleChanged(NEWS, "  ")
        recorder.titleChanged(NEWS, "News, updated")

        assertEquals(listOf("$NEWS|News, updated"), titles)
    }

    @Test
    fun theSinkPassesEveryEventOnUnchangedAndTellsTheRecorder() {
        val inner = RecordingSink()
        val sink = HistoryRecordingSink(inner, recorder, currentTitle = { "Now" })

        sink.onPageStarted(NEWS)
        sink.onProgressChanged(50)
        sink.onPageTitle(NEWS, "News")
        sink.onPageFinished(NEWS, "News")
        sink.onMainFrameError("http://plain.example/", "Blocked insecure navigation")
        sink.onDomProbeResult(NEWS, "{}")
        sink.onUrlChanged(SECOND)

        assertEquals(
            listOf(
                "started $NEWS", "progress 50", "title $NEWS News", "finished $NEWS News",
                "error http://plain.example/", "probe $NEWS", "changed $SECOND",
            ),
            inner.events,
        )
        assertEquals(listOf("$NEWS|News", "$SECOND|Now"), visits)
    }

    private class RecordingSink : BrowserObservationSink {
        val events = mutableListOf<String>()
        override fun onPageStarted(url: String) { events += "started $url" }
        override fun onPageFinished(url: String, title: String?) {
            events += "finished $url $title"
        }
        override fun onUrlChanged(url: String) { events += "changed $url" }
        override fun onProgressChanged(progress: Int) { events += "progress $progress" }
        override fun onRequest(observation: RequestObservation) { events += "request" }
        override fun onDownload(observation: DownloadObservation) { events += "download" }
        override fun onDomProbeResult(pageUrl: String, result: String?) {
            events += "probe $pageUrl"
        }
        override fun onMainFrameError(url: String?, description: String) { events += "error $url" }
        override fun onPageTitle(url: String, title: String?) { events += "title $url $title" }
    }

    private companion object {
        const val NEWS = "https://example.com/news"
        const val MISSING = "https://example.com/missing"
        const val YOUTUBE = "https://m.youtube.com/"
        const val VIDEO = "https://m.youtube.com/watch?v=abc"
        const val SECOND = "https://m.youtube.com/watch?v=def"
    }
}
