package com.alal.yft.extractor.master.android

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.social.MainPages
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VK's pack proof (V3's retest 2 of f22c2217, 2026-10-27): on the phone the 18+ video's capture
 * held 18 observations, all pieces VK's player asked for, no page manifest: no row
 * (`automatic selection NO_MEDIA_FOUND`). In a VK lookup the selection then checks the pieces'
 * whole files (at most 3 pictures and 2 sounds, the fullest first) and admits the tallest
 * mergeable picture with the fullest sound as any passed row (its length held to the player's,
 * its sound checked). Another site's lookup: no such row, as before. A failure names in counts
 * what the capture held. JVM only; the addresses are made up.
 */
class VkPieceSelectionTest {
    @Test
    fun `a VK capture of pieces only gives the tallest picture with the fullest sound`() = runBlocking {
        val session = session()
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val selector = MasterMainSelection(session)
        val probed = mutableListOf<String>()
        val result = withContext(MainPages(site = "vk")) {
            selector.select(request, request.snapshot!!, emptyList()) {
                probed += it.mediaUrl
                ValidationResult.Valid(checked(it))
            }
        } as MasterResult.Success
        val row = result.result.candidates.single()
        assertEquals(whole("5", VIDEO), row.mediaUrl)
        assertEquals(1_920, row.height)
        assertEquals(whole("3", SOUND), row.audioCompanion!!.mediaUrl)
        // 3 pictures and 2 sounds, the fullest first; then the row's sound once more (its own check).
        assertEquals(
            listOf(whole("5", VIDEO), whole("3", VIDEO), whole("2", VIDEO), whole("3", SOUND), whole("2", SOUND), whole("3", SOUND)),
            probed,
        )
        assertTrue(result.result.details.toString(), "master: VK pieces' files: pictures passed 3 of 6; sounds passed 2 of 5" in result.result.details)
        assertTrue(probed.none { "bytes=" in it })
    }

    @Test
    fun `a picture the phone cannot merge gives way to a mergeable one`() = runBlocking {
        val session = session()
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val result = withContext(MainPages(site = "vk")) {
            MasterMainSelection(session).select(request, request.snapshot!!, emptyList()) {
                val checked = checked(it)
                ValidationResult.Valid(if (it.mediaUrl == whole("5", VIDEO)) checked.copy(codecs = listOf("av01")) else checked)
            }
        } as MasterResult.Success
        assertEquals(whole("3", VIDEO), result.result.candidates.single().mediaUrl)
    }

    @Test
    fun `another site's lookup and a lookup without the site get no piece row, and say what the capture held`() = runBlocking {
        val session = session()
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val probed = mutableListOf<String>()
        for (context in listOf(MainPages(site = "facebook"), MainPages())) {
            val result = withContext(context) {
                MasterMainSelection(session).select(request, request.snapshot!!, emptyList()) {
                    probed += it.mediaUrl
                    ValidationResult.Valid(checked(it))
                }
            } as MasterResult.Failure
            assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, result.reason)
            assertEquals(
                listOf(
                    "master: automatic selection NO_MEDIA_FOUND",
                    "master: capture held requests=14 (dash=0, hls=0, pieces=14, player's=0); page data=0; player length known",
                    "master: rows checked=0; passed=0; off the player's length=0; refused=none",
                ),
                result.details,
            )
        }
        assertTrue(probed.isEmpty())
    }

    @Test
    fun `sounds that fail leave no row, a protected picture is final`() = runBlocking {
        val session = session()
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val noSound = withContext(MainPages(site = "vk")) {
            MasterMainSelection(session).select(request, request.snapshot!!, emptyList()) {
                if (it.mimeType == "audio/mp4") {
                    ValidationResult.Rejected(SiteExtractionFailure.HTTP_STATUS)
                } else {
                    ValidationResult.Valid(checked(it))
                }
            }
        } as MasterResult.Failure
        assertEquals(SiteExtractionFailure.HTTP_STATUS, noSound.reason)
        assertTrue(noSound.details.toString(), "master: rows checked=0; passed=0; off the player's length=0; refused=HTTP_STATUS=2" in noSound.details)
        assertTrue("master: VK pieces' files: pictures passed 3 of 6; sounds passed 0 of 5" in noSound.details)

        val drm = withContext(MainPages(site = "vk")) {
            MasterMainSelection(session).select(request, request.snapshot!!, emptyList()) {
                ValidationResult.Rejected(SiteExtractionFailure.DRM_PROTECTED)
            }
        } as MasterResult.Failure
        assertEquals(SiteExtractionFailure.DRM_PROTECTED, drm.reason)
    }

    @Test
    fun `a picture off the player's length is no row`() = runBlocking {
        val session = session()
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val result = withContext(MainPages(site = "vk")) {
            MasterMainSelection(session).select(request, request.snapshot!!, emptyList()) {
                ValidationResult.Valid(checked(it).copy(durationMillis = 50_000))
            }
        } as MasterResult.Failure
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, result.reason)
        assertTrue(result.details.toString(), "master: rows checked=0; passed=0; off the player's length=1; refused=none" in result.details)
    }

    /** What the row check says of a whole file: its picture's height by VK's type, its size. */
    private fun checked(row: MediaCandidate): MediaCandidate {
        val type = Regex("[?&]type=(\\d+)").find(row.mediaUrl)!!.groupValues[1]
        return if (row.mimeType == "audio/mp4") {
            row.copy(durationMillis = 60_000, contentLengthBytes = SOUND_SIZES.getValue(type))
        } else {
            row.copy(durationMillis = 60_000, height = HEIGHTS.getValue(type), width = 1_080, contentLengthBytes = 1_000_000L * (HEIGHTS.getValue(type) / 100), codecs = listOf("avc1"))
        }
    }

    private fun session(): MasterBrowserSession {
        val session = MasterBrowserSession()
        val scope = session.navigate(PAGE)!!
        val requests = (
            listOf("4", "0", "1", "2", "3", "5").map { piece(it, VIDEO, "0-1407") to "video/mp4" } +
                listOf("4", "0", "1", "2", "3").map { piece(it, SOUND, "0-1300") to "audio/mp4" } +
                listOf(piece("2", VIDEO, "1408-900000") to "video/mp4", piece("2", VIDEO, "900001-1800000") to "video/mp4",
                    piece("2", SOUND, "1301-200000") to "audio/mp4")
            ).joinToString(",") { (url, mime) -> """{"url":"$url","mime":"$mime"}""" }
        repeat(2) { index ->
            val raw = """{"pageUrl":"$PAGE","generation":${scope.generation},""" +
                (if (index == 0) """"requests":[$requests],""" else "") + """"player":{""" +
                """"key":"video:0","url":"blob:https://m.vkvideo.ru/player",""" +
                """"time":${1 + index * .2},"ready":4,"paused":false,"visible":true,""" +
                """"duration":60.05,"width":478,"height":852}}"""
            session.accept(CaptureFrameReader.read(raw)!!, 1_000L + index * 200)
        }
        return session
    }

    private companion object {
        const val PAGE = "https://m.vkvideo.ru/video-100000001_400000001"
        const val FILES = "https://vk1-2.vkuser.net/"
        const val VIDEO = "11"
        const val SOUND = "12"
        val HEIGHTS = mapOf("4" to 256, "0" to 426, "1" to 640, "2" to 852, "3" to 1_280, "5" to 1_920)
        val SOUND_SIZES = mapOf("4" to 500_000L, "0" to 800_000L, "1" to 1_000_000L, "2" to 1_900_000L, "3" to 3_900_000L)

        fun whole(type: String, ct: String) = "$FILES?expires=1900000000&type=$type&sig=s$type$ct&ct=$ct&id=7"

        fun piece(type: String, ct: String, range: String) = whole(type, ct) + "&bytes=$range"
    }
}
