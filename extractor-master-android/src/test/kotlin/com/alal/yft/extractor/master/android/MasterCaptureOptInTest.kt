package com.alal.yft.extractor.master.android

import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.CaptureResult
import com.alal.yft.extractor.master.CapturedRequest
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.PlaybackCaptureProvider
import com.alal.yft.extractor.master.ValidationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MasterCaptureOptInTest {
    @Test
    fun disabledPolicyNeverCallsNewHookCaptureOrValidatorForAnyPrimaryFailure() = runBlocking {
        val engine = MasterFallbackEngine(
            MasterMediaValidator { _, _ -> error("Flag-off probe") },
            PlaybackCaptureProvider { error("Flag-off capture") },
            MasterPolicy(),
            captureSelection = { _, _, _, _ -> error("Flag-off hook") },
        )
        SiteExtractionFailure.entries.forEach { reason ->
            val request = MasterRequest(PAGE, 1, 1, reason)
            assertEquals(
                MasterResult.Skipped(SiteExtractionFailure.ADAPTER_DISABLED),
                engine.extract(request),
            )
        }
    }

    @Test
    fun nullHookKeepsTheLegacyAmbiguousRefusalAndMakesNoNewProbe() = runBlocking {
        var probes = 0
        val engine = MasterFallbackEngine(
            MasterMediaValidator { candidate, _ -> probes++; ValidationResult.Valid(candidate) },
            PlaybackCaptureProvider { CaptureResult.Available(snapshot()) },
            MasterPolicy(enabled = true),
        )
        assertTrue(engine.extract(request()) is MasterResult.NeedsSelection)
        assertEquals(0, probes)
    }

    @Test
    fun disabledAndroidSelectorIsEquivalentToTheUnwiredLegacyEngine() = runBlocking {
        val session = MasterBrowserSession()
        session.navigate(PAGE)
        val selector = MasterMainSelection(session, enabled = false)
        val capture = PlaybackCaptureProvider { CaptureResult.Available(snapshot()) }
        val validator = MasterMediaValidator { _, _ -> error("Legacy refusal must not probe") }
        val legacy = MasterFallbackEngine(validator, capture, MasterPolicy(enabled = true))
        val off = MasterFallbackEngine(
            validator, capture, MasterPolicy(enabled = true), captureSelection = selector::select,
        )
        assertEquals(legacy.extract(request()), off.extract(request()))
    }

    @Test
    fun nullHookRetainsTheLegacyExactPlayingAddressSuccess() = runBlocking {
        val snapshot = snapshot().copy(playingMediaUrl = FIRST)
        val engine = MasterFallbackEngine(
            MasterMediaValidator { candidate, _ -> ValidationResult.Valid(candidate) },
            PlaybackCaptureProvider { CaptureResult.Available(snapshot) },
            MasterPolicy(enabled = true),
        )
        val result = engine.extract(request()) as MasterResult.Success
        assertEquals(listOf(FIRST), result.result.candidates.map { it.mediaUrl })
    }

    private fun request() = MasterRequest(
        PAGE, 1, 1, SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED,
    )

    private fun snapshot() = PageSnapshot(
        PAGE, 1, authorizedPlayback = true, playingMediaUrl = "blob:https://page.test/player",
        requests = listOf(CapturedRequest(FIRST), CapturedRequest(SECOND)),
    )

    companion object {
        private const val PAGE = "https://page.test/watch"
        private const val FIRST = "https://cdn.test/first.mp4"
        private const val SECOND = "https://cdn.test/second.mp4"
    }
}