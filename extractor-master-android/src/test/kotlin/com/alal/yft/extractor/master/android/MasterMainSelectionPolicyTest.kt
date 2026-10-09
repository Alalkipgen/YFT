package com.alal.yft.extractor.master.android

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.ValidationResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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

    @Test
    fun existingPassiveCandidatesDoNotHideOrReorderTheOwnedMainMore() = runBlocking {
        val session = session()
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val selector = MasterMainSelection(session)
        val original = listOf(media("first"), media("second"))
        val result = selector.select(request, request.snapshot!!, original) {
            ValidationResult.Valid(it)
        } as MasterResult.Success
        val packed = selector.appCandidates(result.result.candidates, null)!!
        val mixed = listOf(packed.last(), media("unrelated-passive"), packed.first())
        val view = selector.presentation(mixed)
        assertNotNull("An unrelated passive request must not hide verified More", view)
        assertEquals(original.first().mediaUrl, view!!.main.candidates.single().mediaUrl)
        assertEquals(1, view.more.size)
    }

    @Test
    fun aPrimaryResultWithoutOwnedPresentationKeysKeepsItsLegacyUiPath() = runBlocking {
        val session = session()
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val selector = MasterMainSelection(session)
        val original = listOf(media("first"), media("second"))
        val result = selector.select(request, request.snapshot!!, original) {
            ValidationResult.Valid(it)
        } as MasterResult.Success
        selector.appCandidates(result.result.candidates, null)
        assertNull("A coincidentally matching primary URL is not a Master UI result",
            selector.presentation(original))
    }

    @Test
    fun independentlyIdentifiedAudioDoesNotBecomeMainOrMore() = runBlocking {
        val session = session()
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val selector = MasterMainSelection(session, metadata = {
            if (it.mediaUrl.endsWith("sound.mp4")) it.copy(mimeType = "audio/mp4") else it
        })
        val result = selector.select(request, request.snapshot!!,
            listOf(media("sound"), media("video"))) { ValidationResult.Valid(it) }
            as MasterResult.Success
        assertEquals(listOf(media("video").mediaUrl), result.result.candidates.map { it.mediaUrl })
    }

    @Test
    fun missingDimensionsAreNeverUsedToRejectAVerifiedVideo() = runBlocking {
        val session = session()
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val selector = MasterMainSelection(session)
        val candidate = media("unknown-size").copy(width = null, height = null)
        val result = selector.select(request, request.snapshot!!, listOf(candidate)) {
            ValidationResult.Valid(it)
        } as MasterResult.Success
        assertEquals(listOf(candidate), result.result.candidates)
    }

    @Test
    fun aSlowAlternativeDoesNotEraseAnAlreadyVerifiedMain() = runBlocking {
        val session = session()
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val selector = MasterMainSelection(session, metadata = {
            if (it.mediaUrl.endsWith("slow.mp4")) delay(21_000)
            it
        })
        val result = withTimeoutOrNull(18_000) {
            selector.select(request, request.snapshot!!, listOf(media("main"), media("slow"))) {
                ValidationResult.Valid(it)
            }
        }
        assertNotNull("A slow More check must not erase a independently verified main", result)
        assertEquals(listOf(media("main")), (result as MasterResult.Success).result.candidates)
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