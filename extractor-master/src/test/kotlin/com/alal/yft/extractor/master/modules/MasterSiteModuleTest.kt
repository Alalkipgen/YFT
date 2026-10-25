package com.alal.yft.extractor.master.modules

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionRequest
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.api.SiteExtractor
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.MasterStage
import com.alal.yft.extractor.master.PlaybackCaptureProvider
import com.alal.yft.extractor.master.RecordingValidator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterSiteModuleTest {
    private val enabled = MasterPolicy(enabled = true)

    @Test
    fun `a claimed page without an adapter answer is the module's alone`() = runTest {
        val site = FakeSite(SiteExtractionResult.Success(listOf(row(MEDIA))))
        val capture = CountingCapture()
        val validator = RecordingValidator()

        val result = engine(site, capture, validator)
            .extract(request(SiteExtractionFailure.UNSUPPORTED_URL)) as MasterResult.Success

        assertEquals(MasterStage.SITE_MODULE, result.stage)
        assertEquals(listOf("fake:$ID"), result.result.candidates.map(MediaCandidate::videoId))
        assertEquals(listOf("master: fake module: 1 rows"), result.result.details)
        assertEquals(1, site.requests.size)
        assertEquals(0, capture.calls)
        assertTrue(validator.seen.isEmpty())
    }

    @Test
    fun `after the adapter's own lookup a claimed page is never asked again or captured`() =
        runTest {
            val site = FakeSite(SiteExtractionResult.Success(listOf(row(MEDIA))))
            val capture = CountingCapture()
            listOf(
                SiteExtractionFailure.NO_MEDIA_FOUND,
                SiteExtractionFailure.RESPONSE_CHANGED,
                SiteExtractionFailure.MALFORMED_RESPONSE,
                SiteExtractionFailure.BOT_CHECK,
                SiteExtractionFailure.NETWORK,
            ).forEach {
                assertEquals(
                    MasterResult.Skipped(it),
                    engine(site, capture).extract(request(it)),
                )
            }
            assertTrue(site.requests.isEmpty())
            assertEquals(0, capture.calls)
        }

    @Test
    fun `a module's bot check is terminal with no capture`() = runTest {
        val site = FakeSite(SiteExtractionResult.Failure(SiteExtractionFailure.BOT_CHECK))
        val capture = CountingCapture()

        val result = engine(site, capture).extract(request(SiteExtractionFailure.UNSUPPORTED_URL))

        assertEquals(
            MasterResult.Failure(
                SiteExtractionFailure.BOT_CHECK,
                listOf("master: fake module: BOT_CHECK"),
            ),
            result,
        )
        assertEquals(0, capture.calls)
    }

    @Test
    fun `a page no module claims keeps the original engine path`() = runTest {
        val site = FakeSite(SiteExtractionResult.Success(listOf(row(MEDIA))), claims = false)
        val capture = CountingCapture()

        val result = engine(site, capture).extract(request(SiteExtractionFailure.NO_MEDIA_FOUND))

        assertTrue(result is MasterResult.NeedsPlayback)
        assertEquals(1, capture.calls)
        assertTrue(site.requests.isEmpty())
    }

    @Test
    fun `a module never runs while Master is off or for a different requested video`() =
        runTest {
            val site = FakeSite(SiteExtractionResult.Success(listOf(row(MEDIA))))
            val off = MasterFallbackEngine(RecordingValidator(), modules = listOf(site.module))
            assertEquals(
                MasterResult.Skipped(SiteExtractionFailure.ADAPTER_DISABLED),
                off.extract(request(SiteExtractionFailure.UNSUPPORTED_URL)),
            )
            val other = engine(site, CountingCapture()).extract(
                request(SiteExtractionFailure.UNSUPPORTED_URL).copy(expectedContentId = "other"),
            )
            assertEquals(
                SiteExtractionFailure.RESPONSE_CHANGED,
                (other as MasterResult.Failure).reason,
            )
            assertTrue(site.requests.isEmpty())
        }

    @Test
    fun `the row check drops plain-HTTP, SABR and protected rows`() = runTest {
        val rows = listOf(
            row(MEDIA),
            row("http://media.example.test/v.mp4"),
            row("https://media.example.test/videoplayback?id=1&sabr=1"),
            row("https://media.example.test/drm.mp4").copy(drmHint = true),
            row(MEDIA).copy(audioCompanion = companion("http://media.example.test/a.m4a")),
        )
        val site = FakeSite(SiteExtractionResult.Success(rows, listOf("site: 5 rows")))

        val result = site.module.extract(siteRequest()) as SiteExtractionResult.Success

        assertEquals(listOf(MEDIA), result.candidates.map(MediaCandidate::mediaUrl))
        assertEquals(
            listOf("site: 5 rows", "master module: 4 rows failed the row check"),
            result.details,
        )
    }

    @Test
    fun `no admissible row is no media found`() = runTest {
        val site = FakeSite(
            SiteExtractionResult.Success(listOf(row("https://m.example.test/v?sabr=1"))),
        )

        val result = site.module.extract(siteRequest()) as SiteExtractionResult.Failure

        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, result.reason)
    }

    private fun engine(
        site: FakeSite,
        capture: PlaybackCaptureProvider,
        validator: RecordingValidator = RecordingValidator(),
    ) = MasterFallbackEngine(validator, capture, enabled, modules = listOf(site.module))

    private fun request(failure: SiteExtractionFailure) =
        MasterRequest(PAGE, 1, NOW, failure)

    private fun siteRequest() = SiteExtractionRequest(
        SitePageIdentity("fake", ID, PAGE),
        BrowserRequestContext(PAGE, null, null),
        NOW,
    )

    private fun row(url: String) = MediaCandidate(
        pageUrl = PAGE,
        mediaUrl = url,
        sources = setOf(CandidateSource.MANIFEST),
        kind = MediaKind.DIRECT,
        mimeType = "video/mp4",
    )

    private fun companion(url: String) = CompanionAudio(
        mediaUrl = url,
        mimeType = "audio/mp4",
        codecs = listOf("mp4a.40.2"),
        requestContext = BrowserRequestContext(PAGE, null, null),
    )

    private class CountingCapture : PlaybackCaptureProvider {
        var calls = 0
        override suspend fun capture(request: MasterRequest): CaptureResult {
            calls++
            return CaptureResult.Unavailable
        }
    }

    private class FakeSite(
        private val answer: SiteExtractionResult,
        private val claims: Boolean = true,
    ) : SiteExtractor {
        val requests = mutableListOf<SiteExtractionRequest>()
        val module = SiteExtractorModule(this)
        override val id = "fake"
        override val displayName = "Fake"
        override fun identify(pageUrl: String) =
            if (claims && pageUrl == PAGE) SitePageIdentity(id, ID, PAGE) else null

        override suspend fun extract(request: SiteExtractionRequest): SiteExtractionResult {
            requests += request
            return answer
        }
    }

    private companion object {
        const val PAGE = "https://video.example.test/watch/abc"
        const val MEDIA = "https://media.example.test/v.mp4"
        const val ID = "abc"
        const val NOW = 1_800_000_000_000L
    }
}
