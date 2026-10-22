package com.alal.yft.extractor.master

import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.api.SiteExtractionResult
import com.alal.yft.extractor.master.social.SocialStage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Social plan task 12 (the phone session of 2026-10-22, observation b): the visible tab's own
 * playback (capture) answered by the app's selection kept only the selection's own line, so a
 * social lookup's Details lost the ladder's lines before it (I2's, X1's, the capture's request).
 * Now a lookup a social ladder claims keeps them first and the selection's own last, for every
 * kind of answer; every other lookup (no ladder, or a lookup it does not claim): as before.
 */
class MasterCaptureDetailsTest {
    private val enabled = MasterPolicy(enabled = true)

    @Test
    fun `a claimed lookup's capture answer keeps the ladder's lines first and its own last`() = runTest {
        for (answer in answers()) {
            val result = engine(answer, Ladder(claims = true)).extract(request())
            val lines = linesOf(result)
            assertEquals(answer.javaClass, result.javaClass)
            assertEquals(answer.toString(), LADDER, lines.first())
            assertTrue(lines.toString(), CAPTURE in lines)
            assertTrue(lines.toString(), lines.indexOf(LADDER) < lines.indexOf(CAPTURE))
            assertEquals(answer.toString(), OWN, lines.last())
            if (answer is MasterResult.Failure) assertEquals(answer.reason, (result as MasterResult.Failure).reason)
            if (answer is MasterResult.Success) {
                assertEquals(answer.result.candidates, (result as MasterResult.Success).result.candidates)
                assertEquals(answer.stage, result.stage)
            }
        }
    }

    @Test
    fun `every other lookup's capture answer is the selection's own, as before`() = runTest {
        for (answer in answers()) {
            for (social in listOf(null, Ladder(claims = false))) {
                val result = engine(answer, social).extract(request())
                assertEquals("$social", answer, result)
            }
        }
        // A skipped answer stays as it is, claimed or not.
        val skipped = MasterResult.Skipped(SiteExtractionFailure.NO_MEDIA_FOUND)
        assertSame(skipped, engine(skipped, Ladder(claims = true)).extract(request()))
    }

    @Test
    fun `a long ladder keeps the selection's own lines within the bound`() = runTest {
        val many = (1..40).map { "master: fixture ladder line $it" }
        val own = listOf("master: automatic selection fixture", OWN)
        val result = engine(MasterResult.NeedsPlayback(own), Ladder(claims = true, many)).extract(request())
        val lines = (result as MasterResult.NeedsPlayback).details
        assertEquals(DiagnosticTextSanitizer.MAX_STEPS, lines.size)
        assertEquals(many.first(), lines.first())
        assertEquals(own, lines.takeLast(2))
    }

    /** One of each answer the app's selection gives (its own line [OWN] last). */
    private fun answers(): List<MasterResult> = listOf(
        MasterResult.NeedsPlayback(listOf(OWN)),
        MasterResult.Failure(SiteExtractionFailure.NO_MEDIA_FOUND, listOf("master: automatic selection fixture", OWN)),
        MasterResult.NeedsSelection(listOf(OWN)),
        MasterResult.Success(SiteExtractionResult.Success(listOf(candidate()), listOf(OWN)), MasterStage.PLAYBACK_CAPTURE),
    )

    /** The engine with a tab playback that plays the page's file and the app's selection answering [answer]. */
    private fun engine(answer: MasterResult, social: SocialStage?) = MasterFallbackEngine(
        RecordingValidator(),
        PlaybackCaptureProvider {
            CaptureResult.Available(snapshot(playing = MEDIA, requests = listOf(CapturedRequest(MEDIA))))
        },
        enabled,
        captureSelection = { _, _, _, _ -> answer },
        social = social,
    )

    private fun linesOf(result: MasterResult): List<String> = when (result) {
        is MasterResult.Success -> result.result.details
        is MasterResult.Failure -> result.details
        is MasterResult.NeedsPlayback -> result.details
        is MasterResult.NeedsSelection -> result.details
        is MasterResult.Skipped -> emptyList()
    }

    /** A social ladder stand-in: claims the lookup or not; its [lines], then nothing to read. */
    private class Ladder(private val claims: Boolean, private val lines: List<String> = listOf(LADDER)) : SocialStage {
        override suspend fun claims(request: MasterRequest): Boolean = claims

        override suspend fun stage(
            request: MasterRequest,
            policy: MasterPolicy,
            details: MutableList<String>,
        ): SocialStage.Staged? {
            details += lines
            return null
        }

        override fun toString(): String = "Ladder(claims=$claims)"
    }

    private companion object {
        const val LADDER = "master: fixture ladder after RESPONSE_CHANGED; nothing to read"
        const val CAPTURE = "master: one browser-assisted capture requested"
        const val OWN = "master: fresh natural playback required"
    }
}
