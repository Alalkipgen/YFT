package com.alal.yft.extractor.master.android

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.verify.InspectedMedia
import com.alal.yft.extractor.master.verify.MediaFingerprint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R4: automatic main selection with fingerprints. Offline fixtures: the cues stand for what the
 * bounded metadata read found (sidx/EXTINF); nothing is played, decoded or synthesized.
 */
class MasterFingerprintSelectionTest {
    private val ladder = listOf(4_004L, 6_340, 12_346, 15_516, 20_521, 22_356, 26_527, 32_533)
    private val related = listOf(3_003L, 8_008, 10_511, 14_515, 23_858, 25_860, 31_532, 35_536)

    @Test
    fun oneVideosQualitiesBecomeMainsQualitiesNotMoreVideos() = runBlocking {
        val (selector, result) = select(
            "hd" to MediaFingerprint(60_010, ladder),
            "sd" to MediaFingerprint(60_020, ladder),
        )
        assertEquals(listOf("hd", "sd"), names(result))
        val shown = selector.presentation(selector.appCandidates(result.result.candidates, null)!!)!!
        assertEquals(listOf("hd", "sd"), shown.main.candidates.map(::name))
        assertTrue(shown.more.isEmpty())
        assertTrue(result.result.details.any { it.contains("qualities=2; dropped=0") })
    }

    @Test
    fun aLoneSameLengthClipWithOtherKeyframesIsDropped() = runBlocking {
        val (_, result) = select(
            "hd" to MediaFingerprint(60_010, ladder),
            "related" to MediaFingerprint(60_403, related),
            "sd" to MediaFingerprint(60_020, ladder),
        )
        assertEquals(listOf("hd", "sd"), names(result))
        assertTrue(result.result.details.any { it.contains("dropped=1") })
    }

    @Test
    fun anUnconfirmedMainKeepsTheOtherFileAsMore() = runBlocking {
        val (selector, result) = select(
            "hd" to MediaFingerprint(60_010, ladder),
            "related" to MediaFingerprint(60_403, related),
        )
        assertEquals(listOf("hd", "related"), names(result))
        val shown = selector.presentation(selector.appCandidates(result.result.candidates, null)!!)!!
        assertEquals(listOf("hd"), shown.main.candidates.map(::name))
        assertEquals(listOf(listOf("related")), shown.more.map { it.candidates.map(::name) })
    }

    @Test
    fun withoutCuesEveryFileStaysItsOwnVideoAsBeforeR4() = runBlocking {
        val (selector, result) = select(
            "first" to MediaFingerprint(60_000),
            "second" to MediaFingerprint(60_000),
        )
        assertEquals(listOf("first", "second"), names(result))
        val shown = selector.presentation(selector.appCandidates(result.result.candidates, null)!!)!!
        assertEquals(1, shown.main.candidates.size)
        assertEquals(1, shown.more.size)
    }

    @Test
    fun aMainQualityRankedBehindAnotherVideoJoinsTheMainGroup() = runBlocking {
        val (_, result) = select(
            "hd" to MediaFingerprint(60_005, ladder),
            "other" to MediaFingerprint(60_010),
            "sd" to MediaFingerprint(60_030, ladder),
        )
        assertEquals(listOf("hd", "sd", "other"), names(result))
    }

    private suspend fun select(
        vararg files: Pair<String, MediaFingerprint>,
    ): Pair<MasterMainSelection, MasterResult.Success> {
        val session = session()
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val prints = files.toMap()
        val selector = MasterMainSelection(session, inspect = { media ->
            val print = prints.getValue(name(media))
            InspectedMedia(media.copy(durationMillis = print.durationMillis), print)
        })
        val result = selector.select(request, request.snapshot!!, files.map { media(it.first) }) {
            ValidationResult.Valid(it)
        }
        return selector to (result as MasterResult.Success)
    }

    private fun names(result: MasterResult.Success) = result.result.candidates.map(::name)

    private fun name(media: MediaCandidate) =
        media.mediaUrl.substringAfterLast('/').substringBefore('.')

    private fun media(name: String) = MediaCandidate(
        PAGE, "https://cdn.test/$name.mp4", setOf(CandidateSource.REQUEST), MediaKind.DIRECT,
        mimeType = "video/mp4", width = 1080, height = 1920,
    )

    private fun session(): MasterBrowserSession {
        val session = MasterBrowserSession()
        val scope = session.navigate(PAGE)!!
        repeat(2) { index ->
            val raw = """{"pageUrl":"$PAGE","generation":${scope.generation},"player":{""" +
                """"key":"video:0","url":"blob:https://page.test/player",""" +
                """"time":${1 + index * .2},"ready":4,"paused":false,"visible":true,""" +
                """"duration":60,"width":1080,"height":1920}}"""
            session.accept(CaptureFrameReader.read(raw)!!, 1_000L + index * 200)
        }
        return session
    }

    private companion object {
        const val PAGE = "https://page.test/watch"
    }
}
