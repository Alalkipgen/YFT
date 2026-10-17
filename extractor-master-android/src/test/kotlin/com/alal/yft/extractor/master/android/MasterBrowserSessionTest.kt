package com.alal.yft.extractor.master.android

import com.alal.yft.core.browser.detection.RequestObservation
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.CaptureResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MasterBrowserSessionTest {
    private val page = "https://page.test/watch"
    private val media = "https://cdn.test/clip.mp4"

    @Test
    fun navigationClearsRequestsAndRejectsOldFrame() = runTest {
        val session = MasterBrowserSession()
        val first = session.navigate(page)!!
        session.observe(observation(page))
        val old = frame(first, time = 1.0)
        val next = session.navigate("$page/next")!!
        assertTrue(next.generation > first.generation)
        assertFalse(session.accept(old, 100))
        session.observe(observation(page))
        assertTrue(snapshot(session).requests.isEmpty())
    }

    @Test
    fun sameAddressReloadAndClearNeverReuseGeneration() {
        val session = MasterBrowserSession()
        val first = session.navigate(page)!!
        val second = session.navigate(page)!!
        session.clear()
        val third = session.navigate(page)!!
        assertTrue(second.generation > first.generation)
        assertTrue(third.generation > second.generation)
    }

    @Test
    fun singleSamplePausedMediaAndSeekingDoNotAuthorizePlayback() = runTest {
        val session = MasterBrowserSession()
        val scope = session.navigate(page)!!
        session.accept(frame(scope, 1.0), 100)
        assertFalse(snapshot(session).authorizedPlayback)
        session.accept(frame(scope, 1.3, paused = true), 300)
        assertFalse(snapshot(session).authorizedPlayback)
        session.accept(frame(scope, 1.3), 500)
        session.accept(frame(scope, 18.0), 700)
        assertFalse(snapshot(session).authorizedPlayback)
    }

    @Test
    fun twoVisibleProgressSamplesAuthorizeOnlyThisScope() = runTest {
        val session = MasterBrowserSession()
        val scope = session.navigate(page)!!
        session.accept(frame(scope, 1.0), 100)
        session.accept(frame(scope, 1.2), 300)
        assertTrue(snapshot(session).authorizedPlayback)
        assertEquals(media, snapshot(session).playingMediaUrl)
        session.navigate(page)
        assertFalse(snapshot(session).authorizedPlayback)
    }

    @Test
    fun encryptedEvidenceRemainsTerminalUntilNavigation() = runTest {
        val session = MasterBrowserSession()
        val scope = session.navigate(page)!!
        session.accept(frame(scope, 1.0).copy(protected = true), 100)
        session.accept(frame(scope, 1.2), 300)
        assertEquals(SiteExtractionFailure.DRM_PROTECTED, snapshot(session).accessFailure)
        assertFalse(snapshot(session).authorizedPlayback)
        session.navigate(page)
        assertNull(snapshot(session).accessFailure)
    }

    @Test
    fun scriptCredentialsAreIgnoredAndHostContextUsesExactMediaAddress() = runTest {
        val session = MasterBrowserSession()
        val calls = mutableListOf<String>()
        session.setContextProvider { url, _ ->
            calls += url
            BrowserRequestContext("https://page.test/", "Fixture UA", null)
        }
        val scope = session.navigate(page)!!
        val raw = """{"generation":${scope.generation},"pageUrl":"$page","requests":[""" +
            """{"url":"$media","mime":"video/mp4","cookie":"not-a-real-cookie",""" +
            """"headers":{"Authorization":"not-a-real-token"}}]}"""
        session.accept(CaptureFrameReader.read(raw)!!, 100)
        val snapshot = snapshot(session)
        assertEquals(media, calls.first())
        assertNull(snapshot.requests.single().context.cookie)
        assertTrue(snapshot.requests.single().context.observedHeaders.isEmpty())
    }

    @Test
    fun actualNetworkContextIsKeptOnlyForItsExactUrl() = runTest {
        val session = MasterBrowserSession()
        val scope = session.navigate(page)!!
        session.observe(observation(page).copy(cookie = "fixture-session"))
        session.accept(
            frame(scope, 1.0).copy(
                requests = listOf(FrameRequest(media, "video/mp4", false)),
            ),
            100,
        )
        assertTrue(snapshot(session).requests.all { it.context.cookie == "fixture-session" })
    }

    @Test
    fun unsafeNavigationAndNonGetRequestsAreNotCaptured() = runTest {
        val session = MasterBrowserSession()
        assertNull(session.navigate("http://page.test/watch"))
        assertNull(session.navigate("https://name:pass@page.test/watch"))
        session.navigate(page)
        session.observe(observation(page).copy(method = "POST"))
        session.observe(observation(page).copy(requestUrl = "https://cdn.test/image.jpg"))
        assertTrue(snapshot(session).requests.isEmpty())
    }

    @Test
    fun debugTextNeverIncludesPathsAddressesBodiesOrCredentials() {
        val session = MasterBrowserSession()
        val scope = session.navigate(page)!!
        val frame = frame(scope, 1.0)
        for (text in listOf(session.toString(), scope.toString(), frame.toString())) {
            assertFalse(text.contains("page.test"))
            assertFalse(text.contains("cdn.test"))
            assertFalse(text.contains("/watch"))
        }
    }

    private fun observation(url: String) = RequestObservation(
        url, media, "GET", emptyMap(), "Fixture UA", null, 100,
    )

    private fun frame(scope: BrowserCaptureScope, time: Double, paused: Boolean = false) =
        CaptureFrame(
            pageUrl = scope.pageUrl,
            generation = scope.generation,
            html = null,
            payloads = emptyList(),
            requests = emptyList(),
            player = FramePlayer("video:0", media, time, 4, paused, true),
            protected = false,
        )

    private suspend fun snapshot(session: MasterBrowserSession) =
        session.request(SiteExtractionFailure.NO_MEDIA_FOUND, 100)!!.snapshot!!
}