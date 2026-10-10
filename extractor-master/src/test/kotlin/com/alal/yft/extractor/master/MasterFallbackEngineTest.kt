package com.alal.yft.extractor.master

import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.api.SiteExtractionFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterFallbackEngineTest {
    private val enabled = MasterPolicy(enabled = true)

    @Test
    fun `default is disabled without production wiring`() = runTest {
        val probe = RecordingValidator()
        val result = MasterFallbackEngine(probe).extract(request())
        assertEquals(MasterResult.Skipped(SiteExtractionFailure.ADAPTER_DISABLED), result)
        assertTrue(probe.seen.isEmpty())
    }

    @Test
    fun `access DRM and network failures do not launch another lookup`() = runTest {
        var captures = 0
        val capture = PlaybackCaptureProvider { captures++; CaptureResult.Unavailable }
        val probe = RecordingValidator()
        listOf(
            SiteExtractionFailure.DRM_PROTECTED,
            SiteExtractionFailure.PRIVATE_OR_UNAVAILABLE,
            SiteExtractionFailure.GEO_RESTRICTED,
            SiteExtractionFailure.RATE_LIMITED,
            SiteExtractionFailure.NETWORK,
            SiteExtractionFailure.ADAPTER_DISABLED,
        ).forEach {
            assertEquals(
                MasterResult.Skipped(it),
                MasterFallbackEngine(probe, capture, enabled).extract(request(failure = it)),
            )
        }
        assertEquals(0, captures)
        assertTrue(probe.seen.isEmpty())
    }

    @Test
    fun `page payload success does not request playback capture`() = runTest {
        var captures = 0
        val capture = PlaybackCaptureProvider { captures++; CaptureResult.Unavailable }
        val result = MasterFallbackEngine(RecordingValidator(), capture, enabled).extract(
            request(snapshot(fixture("instagram-reel.json")), id = "ig-fixture"),
        ) as MasterResult.Success
        assertEquals(MasterStage.PAGE_DATA, result.stage)
        assertEquals(2, result.result.candidates.size)
        assertEquals(0, captures)
    }

    @Test
    fun `changed payload falls back to one fresh capture`() = runTest {
        var captures = 0
        val capture = PlaybackCaptureProvider {
            captures++
            CaptureResult.Available(snapshot(
                playing = MEDIA, requests = listOf(CapturedRequest(MEDIA)),
            ))
        }
        val result = MasterFallbackEngine(RecordingValidator(), capture, enabled)
            .extract(request(snapshot("{}"))) as MasterResult.Success
        assertEquals(MasterStage.PLAYBACK_CAPTURE, result.stage)
        assertEquals(MEDIA, result.result.candidates.single().mediaUrl)
        assertEquals(1, captures)
    }

    @Test
    fun `the failed site adapter is never an engine dependency`() = runTest {
        var captures = 0
        val capture = PlaybackCaptureProvider { captures++; CaptureResult.Unavailable }
        val result = MasterFallbackEngine(RecordingValidator(), capture, enabled)
            .extract(request(snapshot("{}")))
        assertTrue(result is MasterResult.NeedsPlayback)
        assertEquals(1, captures)
    }

    @Test
    fun `stale page data stops without a network probe`() = runTest {
        val probe = RecordingValidator()
        val result = MasterFallbackEngine(probe, policy = enabled).extract(
            request(snapshot(fixture("instagram-reel.json")).copy(generation = 0)),
        ) as MasterResult.Failure
        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, result.reason)
        assertTrue(probe.seen.isEmpty())
    }

    @Test
    fun `capture for another page is rejected`() = runTest {
        val capture = PlaybackCaptureProvider {
            CaptureResult.Available(snapshot().copy(pageUrl = "$PAGE/other"))
        }
        val result = MasterFallbackEngine(RecordingValidator(), capture, enabled)
            .extract(request()) as MasterResult.Failure
        assertEquals(SiteExtractionFailure.RESPONSE_CHANGED, result.reason)
    }

    @Test
    fun `bot check requires authorized browser playback`() = runTest {
        val probe = RecordingValidator()
        val result = MasterFallbackEngine(probe, policy = enabled).extract(
            request(snapshot(fixture("instagram-reel.json")),
                SiteExtractionFailure.BOT_CHECK, "ig-fixture"),
        )
        assertTrue(result is MasterResult.NeedsPlayback)
        assertTrue(probe.seen.isEmpty())
    }

    @Test
    fun `own successful playback can supply decoded media after a bot check`() = runTest {
        val result = MasterFallbackEngine(RecordingValidator(), policy = enabled).extract(
            request(
                snapshot(
                    playing = MEDIA,
                    requests = listOf(CapturedRequest(MEDIA)),
                    authorized = true,
                ),
                SiteExtractionFailure.BOT_CHECK,
            ),
        ) as MasterResult.Success
        assertEquals(MEDIA, result.result.candidates.single().mediaUrl)
    }

    @Test
    fun `page access verdict is final even with an old media address`() = runTest {
        val probe = RecordingValidator()
        val result = MasterFallbackEngine(probe, policy = enabled).extract(
            request(snapshot(fixture("instagram-reel.json")).copy(
                accessFailure = SiteExtractionFailure.LOGIN_REQUIRED,
            )),
        ) as MasterResult.Failure
        assertEquals(SiteExtractionFailure.LOGIN_REQUIRED, result.reason)
        assertTrue(probe.seen.isEmpty())
    }

    @Test
    fun `two page videos without focus require selection`() = runTest {
        val probe = RecordingValidator()
        val body = """[{"id":"one","video_url":"https://cdn.example.test/one.mp4"},""" +
            """{"id":"two","video_url":"https://cdn.example.test/two.mp4"}]"""
        val result = MasterFallbackEngine(probe, policy = enabled)
            .extract(request(snapshot(body)))
        assertTrue(result is MasterResult.NeedsSelection)
        assertTrue(probe.seen.isEmpty())
    }

    @Test
    fun `playing file wins over prefetched feed files`() = runTest {
        val result = MasterFallbackEngine(RecordingValidator(), policy = enabled).extract(
            request(snapshot(
                playing = MEDIA,
                requests = listOf(
                    CapturedRequest("https://cdn.example.test/next.mp4"),
                    CapturedRequest(MEDIA),
                ),
            )),
        ) as MasterResult.Success
        assertEquals(listOf(MEDIA), result.result.candidates.map { it.mediaUrl })
    }

    @Test
    fun `preview and known ad files are not returned`() = runTest {
        val probe = RecordingValidator()
        val result = MasterFallbackEngine(probe, policy = enabled).extract(
            request(snapshot(
                playing = MEDIA,
                requests = listOf(
                    CapturedRequest("https://cdn.example.test/ads/pre.mp4",
                        pageRole = PageMediaRole.MAIN),
                    CapturedRequest("https://cdn.example.test/preview.mp4",
                        pageRole = PageMediaRole.PREVIEW),
                    CapturedRequest(MEDIA),
                ),
            )),
        ) as MasterResult.Success
        assertEquals(listOf(MEDIA), result.result.candidates.map { it.mediaUrl })
        assertEquals(1, probe.seen.size)
    }

    @Test
    fun `an expired page address is never validated`() = runTest {
        val probe = RecordingValidator()
        MasterFallbackEngine(probe, policy = enabled).extract(
            request(snapshot("""{"video_url":"$MEDIA?expire=1"}""")),
        )
        assertTrue(probe.seen.isEmpty())
    }

    @Test
    fun `expected content identity excludes the next post`() = runTest {
        val body = """[{"id":"wrong","video_url":"https://cdn.example.test/wrong.mp4"},""" +
            """{"id":"right","video_url":"$MEDIA"}]"""
        val result = MasterFallbackEngine(RecordingValidator(), policy = enabled)
            .extract(request(snapshot(body), id = "right")) as MasterResult.Success
        assertEquals(MEDIA, result.result.candidates.single().mediaUrl)
    }

    @Test
    fun `snapshot byte budget fails before discovery or capture`() = runTest {
        val probe = RecordingValidator()
        val result = MasterFallbackEngine(
            probe, policy = enabled.copy(maxSnapshotChars = 4),
        ).extract(request(snapshot("12345"))) as MasterResult.Failure
        assertEquals(SiteExtractionFailure.RESPONSE_TOO_LARGE, result.reason)
        assertTrue(probe.seen.isEmpty())
    }

    @Test
    fun `shared probe budget bounds both discovery stages`() = runTest {
        val probe = RecordingValidator {
            ValidationResult.Rejected(SiteExtractionFailure.HTTP_STATUS)
        }
        var captures = 0
        val capture = PlaybackCaptureProvider { captures++; CaptureResult.Unavailable }
        val result = MasterFallbackEngine(probe, capture, enabled.copy(maxCandidates = 1))
            .extract(request(snapshot(fixture("instagram-reel.json")), id = "ig-fixture"))
        assertTrue(result is MasterResult.Failure)
        assertEquals(1, probe.seen.size)
        assertEquals(0, captures)
    }

    @Test
    fun `validator cannot return another page or an insecure media address`() = runTest {
        val probe = RecordingValidator {
            ValidationResult.Valid(it.copy(pageUrl = "$PAGE/other", mediaUrl = "http://bad.test/v"))
        }
        val result = MasterFallbackEngine(probe, policy = enabled)
            .extract(request(snapshot("""{"video_url":"$MEDIA"}""")))
        assertFalse(result is MasterResult.Success)
    }

    @Test
    fun `fallback timeout is a bounded failure`() = runTest {
        val capture = PlaybackCaptureProvider { delay(2_000); CaptureResult.Unavailable }
        val result = MasterFallbackEngine(
            RecordingValidator(), capture, enabled.copy(timeoutMillis = 100),
        ).extract(request()) as MasterResult.Failure
        assertEquals(SiteExtractionFailure.NETWORK, result.reason)
    }

    @Test
    fun `parent cancellation is not converted into a result`() = runTest {
        val capture = PlaybackCaptureProvider { awaitCancellation() }
        val task = async {
            withTimeout(10) {
                MasterFallbackEngine(RecordingValidator(), capture, enabled).extract(request())
            }
        }
        var cancelled = false
        try {
            task.await()
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }
}