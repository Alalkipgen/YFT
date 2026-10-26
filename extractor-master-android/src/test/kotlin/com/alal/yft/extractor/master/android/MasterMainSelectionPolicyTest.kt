package com.alal.yft.extractor.master.android

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.CompanionAudio
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

    /**
     * VK's pack proof (2026-10-26): VK's player plays WebM pieces of the DASH manifest it fetched
     * (the pieces name byte ranges; the sound's are audio). The manifest, as long as the player's
     * video, is the main; F1 (V3's retest): no piece is checked at all.
     */
    @Test
    fun thePlayersOwnDashManifestIsTheMainWhenItIsAsLongAsThePlayersVideo() = runBlocking {
        val session = session(duration = "4651.78")
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val selector = MasterMainSelection(session)
        val manifest = MediaCandidate(
            PAGE, "https://media.test/?type=4&id=1", setOf(CandidateSource.REQUEST),
            MediaKind.DASH, mimeType = "application/dash+xml",
        )
        val piece = MediaCandidate(
            PAGE, "https://media.test/?type=2&id=1&bytes=0-99", setOf(CandidateSource.REQUEST),
            MediaKind.DIRECT, mimeType = "video/webm",
        )
        val sound = piece.copy(
            mediaUrl = "https://media.test/?type=1&id=1&bytes=0-99", mimeType = "audio/webm",
        )
        val probed = mutableListOf<String>()
        val result = selector.select(request, request.snapshot!!, listOf(piece, sound, manifest)) {
            probed += it.mediaUrl
            ValidationResult.Valid(
                if (it.kind == MediaKind.DASH) {
                    it.copy(durationMillis = 4_651_800, width = 3840, height = 2160)
                } else it,
            )
        } as MasterResult.Success
        assertEquals(listOf(manifest.mediaUrl), result.result.candidates.map { it.mediaUrl })
        assertEquals(MediaKind.DASH, result.result.candidates.single().kind)
        assertEquals(listOf(manifest.mediaUrl), probed)
    }

    /**
     * F1 (V3's retest, 2026-10-26): lookup 2 offered VK's init piece (`…&bytes=0-1427`, 1.4 KB) as
     * a row: its own start stated the whole video's length. A piece is never checked, never
     * selected: with pieces only there is no row.
     */
    @Test
    fun anInitPieceWithThePlayersLengthIsNeverSelected() = runBlocking {
        val session = session()
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val selector = MasterMainSelection(session)
        val pieces = listOf(vkPiece(type = 2, range = "0-1427"), vkPiece(type = 2, range = "1428-90000"))
        val probed = mutableListOf<String>()
        val result = selector.select(request, request.snapshot!!, pieces) {
            probed += it.mediaUrl
            ValidationResult.Valid(it.copy(contentLengthBytes = 1_428))
        }
        assertTrue(probed.isEmpty())
        assertEquals(
            SiteExtractionFailure.NO_MEDIA_FOUND, (result as MasterResult.Failure).reason,
        )
        assertNull(selector.presentation(pieces))
    }

    /** F1: next to the page's manifest the pieces are no rows; the manifest is the main. */
    @Test
    fun withTheManifestTheManifestIsTheMainAndNoPieceIsARow() = runBlocking {
        val session = session()
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val selector = MasterMainSelection(session)
        val manifest = MediaCandidate(
            PAGE, "https://vkvd1.media.test/?expires=1&type=4&id=7", setOf(CandidateSource.REQUEST),
            MediaKind.DASH, mimeType = "application/dash+xml",
        )
        val sound = vkPiece(type = 1, range = "0-1301").copy(mimeType = "audio/mp4")
        val probed = mutableListOf<String>()
        val result = selector.select(
            request, request.snapshot!!,
            listOf(vkPiece(type = 2, range = "0-1427"), sound, manifest, vkPiece(type = 2, range = "1428-90000")),
        ) {
            probed += it.mediaUrl
            ValidationResult.Valid(if (it.kind == MediaKind.DASH) it.copy(durationMillis = 60_000) else it)
        } as MasterResult.Success
        assertEquals(listOf(manifest.mediaUrl), result.result.candidates.map { it.mediaUrl })
        assertEquals(listOf(manifest.mediaUrl), probed)
    }

    /**
     * F1: a piece whose address names no range: the page's request named none and its answer was
     * a piece (a 206; the script marks it). It is never a row; another file of the page still is.
     */
    @Test
    fun aPieceThePageGotAsA206WithoutBytesInItsAddressIsNeverARow() = runBlocking {
        val session = session()
        val scope = session.currentScope()!!
        val raw = """{"pageUrl":"$PAGE","generation":${scope.generation},"requests":[""" +
            """{"url":"https://cdn.test/piece.mp4","mime":"video/mp4","piece":true},""" +
            """{"url":"https://cdn.test/main.mp4","mime":"video/mp4"}],"player":{""" +
            """"key":"video:0","url":"blob:https://page.test/player","time":1.4,"ready":4,""" +
            """"paused":false,"visible":true,"duration":60,"width":1080,"height":1920}}"""
        assertTrue(session.accept(CaptureFrameReader.read(raw)!!, 1_400L))
        assertTrue(session.piece("https://cdn.test/piece.mp4"))
        assertFalse(session.piece(media("main").mediaUrl))
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val selector = MasterMainSelection(session)
        val probed = mutableListOf<String>()
        val result = selector.select(request, request.snapshot!!, listOf(media("piece"), media("main"))) {
            probed += it.mediaUrl
            ValidationResult.Valid(it)
        } as MasterResult.Success
        assertEquals(listOf(media("main").mediaUrl), result.result.candidates.map { it.mediaUrl })
        assertEquals(listOf(media("main").mediaUrl), probed)
        // A file the check names as a piece is no row either.
        val onlyPiece = selector.select(request, request.snapshot!!, listOf(media("other"))) {
            ValidationResult.Valid(it.copy(mediaUrl = "https://cdn.test/piece.mp4"))
        }
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, (onlyPiece as MasterResult.Failure).reason)
    }

    /**
     * VK's page manifest's row (V3's retest): a picture file with its sound to merge. The sound's
     * check is asked with the sound file's own length, never the picture's (as the engine's).
     */
    @Test
    fun theSoundsCheckIsAskedWithTheSoundFilesOwnLength() = runBlocking {
        val session = session()
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val selector = MasterMainSelection(session)
        val sound = CompanionAudio(
            "https://cdn.test/sound.m4a", "audio/mp4", listOf("mp4a.40.2"),
            BrowserRequestContext(PAGE, null, null), contentLengthBytes = 1_900_000,
        )
        val video = media("video").copy(contentLengthBytes = 9_876_543, audioCompanion = sound)
        val asked = mutableMapOf<String, Long?>()
        val result = selector.select(request, request.snapshot!!, listOf(video)) {
            asked[it.mediaUrl] = it.contentLengthBytes
            ValidationResult.Valid(it)
        } as MasterResult.Success
        assertEquals(mapOf(video.mediaUrl to 9_876_543L, sound.mediaUrl to 1_900_000L), asked)
        assertEquals(sound.mediaUrl, result.result.candidates.single().audioCompanion?.mediaUrl)
    }

    private fun vkPiece(type: Int, range: String) = MediaCandidate(
        PAGE, "https://vkvd1.media.test/?expires=1&type=$type&id=7&bytes=$range",
        setOf(CandidateSource.REQUEST), MediaKind.DIRECT, mimeType = "video/mp4",
        durationMillis = 60_000, width = 478, height = 852,
    )

    private fun media(name: String) = MediaCandidate(
        PAGE, "https://cdn.test/$name.mp4", setOf(CandidateSource.REQUEST), MediaKind.DIRECT,
        mimeType = "video/mp4", durationMillis = 60_000, width = 1080, height = 1920,
    )

    private fun session(duration: String = "60"): MasterBrowserSession {
        val session = MasterBrowserSession()
        val scope = session.navigate(PAGE)!!
        repeat(2) { index ->
            val raw = """{"pageUrl":"$PAGE","generation":${scope.generation},"player":{""" +
                """"key":"video:0","url":"blob:https://page.test/player",""" +
                """"time":${1 + index * .2},"ready":4,"paused":false,"visible":true,""" +
                """"duration":$duration,"width":1080,"height":1920}}"""
            session.accept(CaptureFrameReader.read(raw)!!, 1_000L + index * 200)
        }
        return session
    }

    companion object {
        private const val PAGE = "https://page.test/watch"
    }
}