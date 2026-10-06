package com.alal.yft.detection

import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P17: one lookup per video for a few minutes, shared by Home, the browser and the sheet. */
class SiteLookupCacheTest {
    private val cache = SiteLookupCache()
    private val extractor = CountingExtractor()
    private val coordinator = SiteAdapterCoordinator(
        SiteExtractorRegistry(listOf(extractor)),
        lookups = cache,
    )

    @Test
    fun aSecondLookupOfOneVideoTakesTheFirstAnswerUnderItsOwnAddress() = runTest {
        val first = coordinator.inspect("$PAGE?from=home", PUBLIC, NOW)
        val second = coordinator.inspect("$PAGE?from=browser", PUBLIC, NOW + 1_000)

        assertEquals(1, extractor.requests.size)
        assertTrue(first is SiteAdapterOutcome.Detected)
        second as SiteAdapterOutcome.Detected
        assertEquals(listOf("$PAGE?from=browser"), second.candidates.map { it.pageUrl })
        assertEquals("fixture:42", second.candidates.single().videoId)
        // Another video is its own lookup.
        coordinator.inspect("https://fixture.test/video/7", PUBLIC, NOW)
        assertEquals(2, extractor.requests.size)
    }

    @Test
    fun anAnswerIsKeptUntilItsLinksExpireOrTenMinutesWhicheverComesFirst() = runTest {
        extractor.expiresAt = NOW + 60_000
        coordinator.inspect(PAGE, PUBLIC, NOW)
        coordinator.inspect(PAGE, PUBLIC, NOW + 59_999)
        assertEquals(1, extractor.requests.size)
        coordinator.inspect(PAGE, PUBLIC, NOW + 60_000)
        assertEquals(2, extractor.requests.size)

        // Links that last for hours: ten minutes is the limit.
        extractor.expiresAt = NOW + 6 * 60 * 60_000L
        val other = "https://fixture.test/video/7"
        coordinator.inspect(other, PUBLIC, NOW)
        coordinator.inspect(other, PUBLIC, NOW + SiteLookupCache.MAX_AGE_MS - 1)
        assertEquals(3, extractor.requests.size)
        coordinator.inspect(other, PUBLIC, NOW + SiteLookupCache.MAX_AGE_MS)
        assertEquals(4, extractor.requests.size)
    }

    @Test
    fun aLookupWhileTheFirstRunsWaitsForItAndTheLastToStopStopsIt() = runTest {
        val gate = CompletableDeferred<Unit>()
        extractor.gate = gate
        val home = async { coordinator.inspect(PAGE, PUBLIC, NOW) }
        val sheet = async { coordinator.inspect(PAGE, PUBLIC, NOW) }
        val browser = async { coordinator.inspect(PAGE, PUBLIC, NOW) }
        runCurrent()
        assertEquals(1, extractor.requests.size)

        // One of them stops waiting: the others still get the same single answer.
        home.cancel()
        gate.complete(Unit)
        assertTrue(sheet.await() is SiteAdapterOutcome.Detected)
        assertTrue(browser.await() is SiteAdapterOutcome.Detected)
        assertEquals(1, extractor.requests.size)
        assertEquals(0, extractor.cancelled)

        // Everyone stops waiting: the site is no longer asked.
        extractor.gate = CompletableDeferred()
        val other = "https://fixture.test/video/7"
        val alone = async { coordinator.inspect(other, PUBLIC, NOW) }
        runCurrent()
        assertEquals(2, extractor.requests.size)
        alone.cancel()
        runCurrent()
        assertEquals(1, extractor.cancelled)
        // Nothing was kept, so the next lookup asks again.
        extractor.gate = null
        coordinator.inspect(other, PUBLIC, NOW)
        assertEquals(3, extractor.requests.size)
    }

    @Test
    fun answersReadWithTheSessionNeverReachALookupWithoutIt() = runTest {
        coordinator.inspect(PAGE, SESSION, NOW)
        coordinator.inspect(PAGE, PUBLIC, NOW)
        assertEquals(2, extractor.requests.size)
        val sentSession = extractor.requests.map { it.requestContext.cookie != null }
        assertEquals(listOf(true, false), sentSession)

        // Plan adapted: a public answer may serve the browser's session lookup (Home -> browser).
        val other = "https://fixture.test/video/7"
        coordinator.inspect(other, PUBLIC, NOW)
        val withSession = coordinator.inspect(other, SESSION, NOW) as SiteAdapterOutcome.Detected
        assertEquals(3, extractor.requests.size)
        assertNull(withSession.candidates.single().requestContext.cookie)
    }

    @Test
    fun tryAgainAlwaysAsksTheSiteAndAFailureForgetsTheAnswer() = runTest {
        coordinator.inspect(PAGE, PUBLIC, NOW)
        coordinator.inspect(PAGE, PUBLIC, NOW, fresh = true)
        assertEquals(2, extractor.requests.size)
        coordinator.inspect(PAGE, PUBLIC, NOW)
        assertEquals(2, extractor.requests.size)

        // Try again fails: the old answer is gone too, so the next lookup asks the site.
        extractor.failure = SiteExtractionFailure.NETWORK
        val failed = coordinator.inspect(PAGE, PUBLIC, NOW, fresh = true)
        assertTrue(failed is SiteAdapterOutcome.Failed)
        extractor.failure = null
        coordinator.inspect(PAGE, PUBLIC, NOW)
        assertEquals(4, extractor.requests.size)
        // A failure is never kept either.
        extractor.failure = SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE
        coordinator.inspect("https://fixture.test/video/7", PUBLIC, NOW)
        coordinator.inspect("https://fixture.test/video/7", PUBLIC, NOW)
        assertEquals(6, extractor.requests.size)
    }

    @Test
    fun aDownloadThatGets403Or410DropsItsVideosAnswer() = runTest {
        coordinator.inspect(PAGE, PUBLIC, NOW)
        cache.rememberDownload("task-1", "fixture:42")
        cache.rememberDownload("task-2", "fixture:42")

        // Running and finished downloads change nothing.
        cache.forgetBrokenDownloads(
            listOf(
                task("task-1", DownloadTaskStatus.RUNNING),
                task("task-2", DownloadTaskStatus.COMPLETED),
            ),
        )
        coordinator.inspect(PAGE, PUBLIC, NOW)
        assertEquals(1, extractor.requests.size)

        cache.forgetBrokenDownloads(
            listOf(task("task-1", DownloadTaskStatus.FAILED, DownloadFailureReason.ACCESS_DENIED)),
        )
        coordinator.inspect(PAGE, PUBLIC, NOW)
        assertEquals(2, extractor.requests.size)

        // A 410, or a link the engine must refresh, does the same; other failures do not.
        cache.forgetBrokenDownloads(
            listOf(task("task-2", DownloadTaskStatus.FAILED, DownloadFailureReason.NETWORK)),
        )
        coordinator.inspect(PAGE, PUBLIC, NOW)
        assertEquals(2, extractor.requests.size)
        cache.forgetBrokenDownloads(
            listOf(task("task-2", DownloadTaskStatus.FAILED, DownloadFailureReason.GONE)),
        )
        coordinator.inspect(PAGE, PUBLIC, NOW)
        assertEquals(3, extractor.requests.size)
        cache.rememberDownload("task-3", "fixture:42")
        cache.forgetBrokenDownloads(listOf(task("task-3", DownloadTaskStatus.NEEDS_REFRESH)))
        coordinator.inspect(PAGE, PUBLIC, NOW)
        assertEquals(4, extractor.requests.size)
    }

    @Test
    fun atMostTwentyVideosAreKeptAndClearingBrowsingDataDropsThemAll() = runTest {
        (1..SiteLookupCache.MAX_ENTRIES + 1).forEach { id ->
            coordinator.inspect("https://fixture.test/video/$id", PUBLIC, NOW)
        }
        assertEquals(21, extractor.requests.size)
        // The oldest went; the newest is still there.
        coordinator.inspect("https://fixture.test/video/21", PUBLIC, NOW)
        assertEquals(21, extractor.requests.size)
        coordinator.inspect("https://fixture.test/video/1", PUBLIC, NOW)
        assertEquals(22, extractor.requests.size)

        cache.clear()
        coordinator.inspect("https://fixture.test/video/21", PUBLIC, NOW)
        assertEquals(23, extractor.requests.size)
        // Keys never name the video in logs.
        assertFalse(SiteLookupKey("fixture", "42", false).toString().contains("42"))
    }

    private fun task(
        id: String,
        status: DownloadTaskStatus,
        failure: DownloadFailureReason? = null,
    ) = StoredDownloadTask(
        id = id,
        displayName = "$id.mp4",
        status = status,
        totalBytes = 100,
        downloadedBytes = 0,
        mimeType = "video/mp4",
        destinationKind = DownloadDestinationKind.APP_PRIVATE,
        destinationUri = null,
        preferredSegmentCount = 1,
        requiresLinkRefresh = false,
        failureReason = failure,
        checkpoint = DirectTransferCheckpoint(
            totalBytes = 100,
            entityTag = null,
            lastModified = null,
            segments = listOf(DownloadSegment(0, 0, 99, 0)),
        ),
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )

    private class CountingExtractor : SiteExtractor {
        override val id: String = "fixture"
        override val displayName: String = "Fixture Site"
        val requests = mutableListOf<SiteExtractionRequest>()
        var gate: CompletableDeferred<Unit>? = null
        var cancelled = 0
        var expiresAt: Long? = null
        var failure: SiteExtractionFailure? = null

        override fun identify(pageUrl: String): SitePageIdentity? {
            val prefix = "https://fixture.test/video/"
            if (!pageUrl.startsWith(prefix)) return null
            val contentId = pageUrl.removePrefix(prefix).substringBefore('?')
            return SitePageIdentity("fixture", contentId, "$prefix$contentId")
        }

        override fun isPlayerMediaRequest(requestUrl: String): Boolean = false

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
            requests += request
            try {
                gate?.await()
            } catch (cancellation: CancellationException) {
                cancelled += 1
                throw cancellation
            }
            failure?.let { return SiteExtractionResult.Failure(it) }
            val page = request.identity.canonicalPageUrl
            return SiteExtractionResult.Success(
                listOf(
                    MediaCandidate(
                        pageUrl = page,
                        mediaUrl = "https://cdn.fixture.test/${request.identity.contentId}.mp4",
                        sources = setOf(CandidateSource.MANIFEST),
                        kind = MediaKind.DIRECT,
                        requestContext = BrowserRequestContext(page, "fixture-agent", null),
                        expiresAtEpochMs = expiresAt,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val PAGE = "https://fixture.test/video/42"
        const val NOW = 1_700_000_000_000L
        val PUBLIC = BrowserRequestContext(PAGE, "fixture-agent", cookie = null)
        val SESSION = BrowserRequestContext(PAGE, "fixture-agent", cookie = "sessionid=fixture")
    }
}
