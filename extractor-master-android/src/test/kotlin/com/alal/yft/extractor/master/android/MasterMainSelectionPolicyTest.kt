package com.alal.yft.extractor.master.android

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.ValidationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MasterMainSelectionPolicyTest {
    @Test
    fun flagOffIsAnExactNoOpWithNoProbeOrPresentation() = runBlocking {
        val session = session()
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val selector = MasterMainSelection(session, enabled = false)
        val candidate = media("main").copy(videoId = "tiktok:fixture")
        val before = candidate.copy()
        val result = selector.select(request, request.snapshot!!, listOf(candidate)) {
            error("Flag-off must not probe")
        }
        assertNull(result)
        assertEquals(before, candidate)
        assertNull(selector.appCandidates(listOf(candidate), "tiktok:fixture"))
        assertNull(selector.presentation(listOf(candidate)))
    }

    @Test
    fun presentationPreservesSourceIdentityChecksAndUsesExistingMainMoreGroups() = runBlocking {
        val session = session()
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val selector = MasterMainSelection(session)
        val candidates = listOf(media("first"), media("second"))
        val result = selector.select(request, request.snapshot!!, candidates) {
            ValidationResult.Valid(it)
        } as MasterResult.Success
        val packed = selector.appCandidates(result.result.candidates, null)!!
        val view = selector.presentation(packed)!!
        assertEquals(candidates.first().mediaUrl, view.main.candidates.single().mediaUrl)
        assertEquals(1, view.more.size)
        assertEquals(2, MediaGroups.pageVideos(packed, adapterSite = false).size)
        assertEquals(1, view.more.single().candidates.size)
        session.navigate(PAGE)
        assertNull(selector.presentation(packed))
    }

    @Test
    fun anExplicitOtherPostIsNeverRelabelledAsTheRequestedPost() = runBlocking {
        val session = session()
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val selector = MasterMainSelection(session)
        val original = media("other").copy(videoId = "tiktok:other")
        val result = selector.select(request, request.snapshot!!, listOf(original)) {
            ValidationResult.Valid(it)
        } as MasterResult.Success
        assertTrue(selector.appCandidates(result.result.candidates, "tiktok:requested")!!.isEmpty())
        assertEquals("tiktok:other", original.videoId)
    }

    private fun media(name: String) = MediaCandidate(
        PAGE, "https://cdn.test/$name.mp4", setOf(CandidateSource.REQUEST), MediaKind.DIRECT,
        mimeType = "video/mp4", durationMillis = 60_000, width = 1080, height = 1920,
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

    companion object {
        private const val PAGE = "https://page.test/watch"
    }
}