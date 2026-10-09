package com.alal.yft.extractor.master.android

import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.PlaybackCaptureProvider
import com.alal.yft.extractor.master.ValidationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED contract for the requested automatic main/More selection.
 *
 * These are offline unit fixtures, not live playback, Android gesture or media-probe proof.
 * Candidate facts come from an independent validator fixture, never from DOM duration.
 * The real frame reader, session and engine are exercised without JS play/seek or a fake
 * HTTPS playing URL. Production wiring must satisfy this contract before live validation.
 */
class MasterMainSelectionContractTest {
    private val first = "https://media.test/first.mp4"
    private val second = "https://media.test/second.mp4"

    @Test
    fun instagramMatchesIndependentDurationWithinInclusiveTwoSeconds() = runBlocking {
        val fixture = captured(
            facts = linkedMapOf(first to Facts(62_001), second to Facts(62_000)),
        )
        assertSelected(fixture.extract(), listOf(second))
    }

    @Test
    fun tiktokDoesNotCopyPlayerDurationToAnUnrelatedCapturedFile() = runBlocking {
        val fixture = captured(
            page = TIKTOK,
            expectedId = TIKTOK_ID,
            facts = linkedMapOf(first to Facts(30_000), second to Facts(60_000)),
        )
        assertSelected(fixture.extract(), listOf(second))
    }

    @Test
    fun missingCandidateDurationDoesNotCountAsMatchingKnownPlayerDuration() = runBlocking {
        val fixture = captured(
            facts = linkedMapOf(first to Facts(null), second to Facts(60_000)),
        )
        assertSelected(fixture.extract(), listOf(second))
    }

    @Test
    fun decodedDimensionsRankMatchingFilesWithoutRejectingTheSmallerOne() = runBlocking {
        val fixture = captured(
            facts = linkedMapOf(
                first to Facts(60_000, 720, 1280),
                second to Facts(60_000, 1080, 1920),
            ),
        )
        // First is main; the other eligible file remains available for More.
        assertSelected(fixture.extract(), listOf(second, first))
    }

    @Test
    fun equalCandidatesChooseStableMainAndKeepMoreInsteadOfNeedsSelection() = runBlocking {
        val fixture = captured(
            facts = linkedMapOf(
                first to Facts(60_000, 1080, 1920),
                second to Facts(60_000, 1080, 1920),
            ),
        )
        assertSelected(fixture.extract(), listOf(first, second))
    }

    @Test
    fun unknownPlayerDurationChoosesLongestNonAdAndKeepsMore() = runBlocking {
        val fixture = captured(
            duration = null,
            facts = linkedMapOf(first to Facts(30_000), second to Facts(90_000)),
        )
        // Unknown DOM duration is not permission to bypass a live-manifest/DRM refusal.
        assertSelected(fixture.extract(), listOf(second, first))
    }

    @Test
    fun adDomainAndFarShorterAdDurationCannotBecomeMainOrMore() = runBlocking {
        val fixture = captured(
            facts = linkedMapOf(
                "https://pubads.g.doubleclick.net/video/ad.mp4" to Facts(60_000),
                first to Facts(10_000),
                second to Facts(60_000),
            ),
        )
        assertSelected(fixture.extract(), listOf(second))
    }

    @Test
    fun pausedFixturesNeverAuthorizeSelectionOrStartAProbe() = runBlocking {
        val fixture = captured(paused = true, facts = linkedMapOf(first to Facts(60_000)))
        assertTrue(fixture.extract() is MasterResult.NeedsPlayback)
        assertEquals(0, fixture.probes)
    }

    @Test
    fun oneProgressSampleIsNotPlaybackAuthorization() = runBlocking {
        val fixture = captured(samples = 1, facts = linkedMapOf(first to Facts(60_000)))
        assertTrue(fixture.extract() is MasterResult.NeedsPlayback)
        assertEquals(0, fixture.probes)
    }

    @Test
    fun drmEvidenceRemainsTerminalAndNeverStartsAProbe() = runBlocking {
        val fixture = captured(protected = true, facts = linkedMapOf(first to Facts(60_000)))
        val result = fixture.extract()
        assertTrue(result is MasterResult.Failure)
        assertEquals(SiteExtractionFailure.DRM_PROTECTED, (result as MasterResult.Failure).reason)
        assertEquals(0, fixture.probes)
    }

    @Test
    fun navigationDiscardsTheOldFrameAndNeverStartsAProbe() = runBlocking {
        val fixture = captured(facts = linkedMapOf(first to Facts(60_000)))
        val request = fixture.request()
        fixture.session.navigate("$INSTAGRAM?next=1")
        assertFalse(fixture.session.accept(fixture.lastFrame, 1_400))
        assertTrue(fixture.extract(request) is MasterResult.NeedsPlayback)
        assertEquals(0, fixture.probes)
    }

    @Test
    fun defaultPolicyIsDisabledWithoutCaptureOrProbe() = runBlocking {
        val fixture = captured(facts = linkedMapOf(first to Facts(60_000)))
        val result = fixture.extract(policy = MasterPolicy())
        assertEquals(MasterResult.Skipped(SiteExtractionFailure.ADAPTER_DISABLED), result)
        assertEquals(0, fixture.probes)
        assertEquals(0, fixture.captures)
    }

    private fun assertSelected(result: MasterResult, expected: List<String>) {
        assertTrue(
            "Automatic main/More required; actual=${result.javaClass.simpleName}",
            result is MasterResult.Success,
        )
        val candidates = (result as MasterResult.Success).result.candidates
        assertEquals(expected, candidates.map { it.mediaUrl })
    }

    private fun captured(
        facts: LinkedHashMap<String, Facts>,
        page: String = INSTAGRAM,
        expectedId: String? = null,
        duration: Double? = 60.0,
        paused: Boolean = false,
        protected: Boolean = false,
        samples: Int = 2,
    ): Fixture {
        val session = MasterBrowserSession()
        val scope = requireNotNull(session.navigate(page))
        val requests = facts.keys.joinToString(",") {
            """{"url":"$it","mime":"video/mp4","preview":false}"""
        }
        var last: CaptureFrame? = null
        repeat(samples) { index ->
            val raw = """
                {"pageUrl":"$page","generation":${scope.generation},
                 "requests":[$requests],"protected":$protected,
                 "player":{"key":"video:0","url":"blob:https://fixture.test/native-player",
                  "time":${1.0 + index * 0.2},"ready":4,"paused":$paused,"visible":true,
                  "duration":$duration,"width":1080,"height":1920}}
            """.trimIndent()
            last = requireNotNull(CaptureFrameReader.read(raw))
            assertTrue(session.accept(last!!, 1_000L + index * 200))
        }
        return Fixture(session, requireNotNull(last), facts, expectedId)
    }

    private data class Facts(
        val durationMillis: Long?,
        val width: Int? = null,
        val height: Int? = null,
    )

    private class Fixture(
        val session: MasterBrowserSession,
        val lastFrame: CaptureFrame,
        private val facts: Map<String, Facts>,
        private val expectedId: String?,
    ) {
        var probes = 0
        var captures = 0

        suspend fun request(): MasterRequest = requireNotNull(
            session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1_000, expectedId),
        ).copy(snapshot = null)

        suspend fun extract(
            request: MasterRequest? = null,
            policy: MasterPolicy = MasterPolicy(enabled = true),
        ): MasterResult {
            val validator = MasterMediaValidator { candidate, _ ->
                probes++
                val metadata = facts.getValue(candidate.mediaUrl)
                ValidationResult.Valid(
                    candidate.copy(
                        durationMillis = metadata.durationMillis,
                        width = metadata.width,
                        height = metadata.height,
                    ),
                )
            }
            val capture = PlaybackCaptureProvider {
                captures++
                session.snapshot(it)
            }
            val input = request ?: this.request()
            val selection = MasterMainSelection(session)
            return MasterFallbackEngine(
                validator, capture, policy, captureSelection = selection::select,
            ).extract(input)
        }
    }

    private companion object {
        const val INSTAGRAM = "https://www.instagram.com/reel/offline-fixture/"
        const val TIKTOK_ID = "1234567890123456789"
        const val TIKTOK = "https://www.tiktok.com/@fixture/video/$TIKTOK_ID"
    }
}