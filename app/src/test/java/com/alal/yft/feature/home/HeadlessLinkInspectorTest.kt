package com.alal.yft.feature.home

import com.alal.yft.core.browser.detection.HeadlessPageFetcher
import com.alal.yft.core.browser.detection.MediaMetadataProbe
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.detection.SiteAdapterCoordinator
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SiteExtractorRegistry
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.ui.components.PromptboxStatus
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadlessLinkInspectorTest {
    private val fetched = mutableListOf<String>()
    private val probed = mutableListOf<MediaCandidate>()
    private var page: suspend (String) -> HeadlessPageFetcher.Result = { url ->
        HeadlessPageFetcher.Result.Page(url, "<html><title>Nothing</title></html>")
    }
    private var probe: suspend (MediaCandidate) -> MediaMetadataProbe.Result = {
        MediaMetadataProbe.Result.Failed(MediaMetadataProbe.FailureReason.NETWORK)
    }

    private fun inspector(
        extractor: SiteExtractor? = null,
        timeoutMillis: Long = HeadlessLinkInspector.TIMEOUT_MILLIS,
    ) = HeadlessLinkInspector(
        fetchPage = { url ->
            fetched += url
            page(url)
        },
        probeMedia = { candidate ->
            probed += candidate
            probe(candidate)
        },
        siteAdapters = SiteAdapterCoordinator(SiteExtractorRegistry(listOfNotNull(extractor))),
        clock = { NOW },
        timeoutMillis = timeoutMillis,
    )

    @Test
    fun unusableAddressesAreRejectedWithoutTheNetwork() = runTest {
        assertNotFound(
            LinkInspection.NotFound("Only HTTPS pages are supported", canOpenInBrowser = false),
            inspector().inspect("http://a.test/watch"),
        )
        assertNotFound(
            LinkInspection.NotFound("Enter a web address", canOpenInBrowser = false),
            inspector().inspect("about:blank"),
        )
        assertTrue(fetched.isEmpty())
        assertTrue(probed.isEmpty())
    }

    @Test
    fun directMediaLinkIsProbedInsteadOfFetched() = runTest {
        probe = { candidate ->
            MediaMetadataProbe.Result.Detected(
                candidate.copy(mimeType = "video/mp4", contentLengthBytes = 2_048),
            )
        }

        val result = inspector().inspect("cdn.a.test/clip.mp4") as LinkInspection.Found

        val candidate = result.candidates.single()
        assertEquals("https://cdn.a.test/clip.mp4", result.pageUrl)
        assertEquals("https://cdn.a.test/clip.mp4", candidate.mediaUrl)
        assertEquals("video/mp4", candidate.mimeType)
        assertEquals(2_048L, candidate.contentLengthBytes)
        assertEquals(setOf(CandidateSource.PASTED_URL), probed.single().sources)
        assertEquals(NOW, probed.single().observedAtEpochMs)
        assertTrue(fetched.isEmpty())
    }

    @Test
    fun directMediaIsStillOfferedWhenTheProbeFails() = runTest {
        val result = inspector().inspect("https://cdn.a.test/live/master.m3u8")

        val candidate = (result as LinkInspection.Found).candidates.single()
        assertEquals(MediaKind.HLS, candidate.kind)
        assertEquals(setOf(CandidateSource.PASTED_URL, CandidateSource.MANIFEST), candidate.sources)
        assertTrue(fetched.isEmpty())
    }

    @Test
    fun aSiteAdapterAnswersForPagesItSupports() = runTest {
        val result = inspector(FixtureExtractor()).inspect("https://fixture.test/video/42")

        val found = result as LinkInspection.Found
        assertEquals("https://fixture.test/video/42", found.pageUrl)
        assertEquals("Fixture clip", found.pageTitle)
        assertEquals("https://cdn.fixture.test/42.mp4", found.candidates.single().mediaUrl)
        assertTrue(fetched.isEmpty())
    }

    @Test
    fun adapterFailureThatRulesOutTheMarkupIsReportedAsIs() = runTest {
        val extractor = FixtureExtractor(
            SiteExtractionResult.Failure(SiteExtractionFailure.LOGIN_REQUIRED),
        )

        val result = inspector(extractor).inspect("https://fixture.test/video/42")

        assertNotFound(
            LinkInspection.NotFound("Sign in to Fixture Site on this page first, then try again."),
            result,
        )
        assertTrue(fetched.isEmpty())
    }

    @Test
    fun adapterFailureWithFallbackStillReadsThePage() = runTest {
        val extractor = FixtureExtractor(
            SiteExtractionResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND),
        )

        val result = inspector(extractor).inspect("https://fixture.test/video/42")

        assertNotFound(
            LinkInspection.NotFound("This Fixture Site post has no downloadable video."),
            result,
        )
        assertEquals(listOf("https://fixture.test/video/42"), fetched)
    }

    @Test
    fun pageMarkupIsScannedAndItsTitleNamesTheMedia() = runTest {
        page = { url ->
            HeadlessPageFetcher.Result.Page(
                url = "$url/final",
                html = """
                    <html><head><title>Sunset timelapse</title></head>
                    <body><video src="https://cdn.a.test/sunset.mp4"></video></body></html>
                """.trimIndent(),
            )
        }

        val result = inspector().inspect("https://a.test/watch") as LinkInspection.Found

        assertEquals("https://a.test/watch/final", result.pageUrl)
        assertEquals("Sunset timelapse", result.pageTitle)
        val candidate = result.candidates.single()
        assertEquals("https://cdn.a.test/sunset.mp4", candidate.mediaUrl)
        assertEquals("Sunset timelapse", candidate.title)
    }

    @Test
    fun pageWithoutMediaOffersTheBrowser() = runTest {
        val result = inspector().inspect("https://a.test/article")

        assertNotFound(LinkInspection.NotFound(PromptboxStatus.NO_MEDIA_MESSAGE), result)
        assertTrue((result as LinkInspection.NotFound).canOpenInBrowser)
    }

    @Test
    fun linkThatAnswersWithMediaBecomesACandidate() = runTest {
        page = { url -> HeadlessPageFetcher.Result.Media(url, "audio/mpeg", 4_096) }

        val result = inspector().inspect("https://a.test/listen?id=7") as LinkInspection.Found

        val candidate = result.candidates.single()
        assertEquals("https://a.test/listen?id=7", candidate.mediaUrl)
        assertEquals(MediaKind.DIRECT, candidate.kind)
        assertEquals("audio/mpeg", candidate.mimeType)
        assertEquals(4_096L, candidate.contentLengthBytes)
        assertNull(result.pageTitle)
    }

    @Test
    fun fetchFailuresExplainThemselves() = runTest {
        page = { HeadlessPageFetcher.Result.Failed(HeadlessPageFetcher.FailureReason.NETWORK) }

        val result = inspector().inspect("https://a.test/offline")

        assertNotFound(
            LinkInspection.NotFound(
                "The page could not be reached. Check the connection or try the browser.",
            ),
            result,
        )
    }

    @Test
    fun slowPagesGiveUpAndPointToTheBrowser() = runTest {
        page = { awaitCancellation() }

        val result = inspector(timeoutMillis = 1_000).inspect("https://a.test/slow")

        assertNotFound(
            LinkInspection.NotFound("The page took too long to answer. Try it in the browser."),
            result,
        )
    }

    private class FixtureExtractor(
        private val result: SiteExtractionResult? = null,
    ) : SiteExtractor {
        override val id: String = "fixture"
        override val displayName: String = "Fixture Site"

        override fun identify(pageUrl: String): SitePageIdentity? {
            val prefix = "https://fixture.test/video/"
            if (!pageUrl.startsWith(prefix)) return null
            return SitePageIdentity("fixture", pageUrl.removePrefix(prefix), pageUrl)
        }

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult =
            result ?: SiteExtractionResult.Success(
                listOf(
                    MediaCandidate(
                        pageUrl = request.identity.canonicalPageUrl,
                        mediaUrl = "https://cdn.fixture.test/${request.identity.contentId}.mp4",
                        sources = setOf(CandidateSource.MANIFEST),
                        kind = MediaKind.DIRECT,
                        title = "Fixture clip",
                    ),
                ),
            )
    }

    @Test
    fun failedAdapterStepsRemainInOrderWhenGenericLookupAlsoFails() = runTest {
        page = { HeadlessPageFetcher.Result.Failed(HeadlessPageFetcher.FailureReason.NETWORK) }
        val result = inspector(
            FixtureExtractor(
                SiteExtractionResult.Failure(
                    SiteExtractionFailure.HTTP_STATUS,
                    503,
                    details = listOf("page GET 503", "Cookie: redaction-fixture"),
                ),
            ),
        ).inspect("https://fixture.test/video/42") as LinkInspection.NotFound

        assertEquals(
            listOf("adapter fixture: HTTP_STATUS", "adapter HTTP 503", "page GET 503",
                "page GET failed: NETWORK"),
            result.details,
        )
        assertFalse(result.details.joinToString("\n").contains("Cookie"))
    }

    @Test
    fun markupFailureAndTimeoutReportOnlyObservedStages() = runTest {
        val noMedia = inspector().inspect("https://a.test/article") as LinkInspection.NotFound
        assertTrue(noMedia.details.first().startsWith("page GET: HTML ("))
        assertEquals("markup: no media", noMedia.details.last())
        page = { awaitCancellation() }
        val timeout = inspector(timeoutMillis = 1_000)
            .inspect("https://a.test/slow") as LinkInspection.NotFound
        assertEquals(listOf("lookup: timed out after 1000 ms"), timeout.details)
    }

    /** Original behavior assertions; new stage details are checked separately above. */
    private fun assertNotFound(expected: LinkInspection.NotFound, actual: LinkInspection) {
        assertTrue(actual is LinkInspection.NotFound)
        actual as LinkInspection.NotFound
        assertEquals(expected.message, actual.message)
        assertEquals(expected.canOpenInBrowser, actual.canOpenInBrowser)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
