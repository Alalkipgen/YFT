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
 * The owner's retest of 7e101aa4 (2026-10-26): the first Try again offered 480p (the pieces the
 * player had asked for by then) titled "ВКонтакте | VK Видео". In a VK lookup whose captured rows
 * gave nothing, the selection now checks the page's own data for the video first (its MP4 files
 * from the tallest, at most 3) and admits the first that passes, with the data's title; the
 * pieces' files only after that, their row titled by the data. JVM only; the addresses are made up.
 */
class VkPageDataSelectionTest {
    @Test
    fun `the page data's tallest file that passes is the row, before the pieces`() = runBlocking {
        val session = session(data = true)
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val probed = mutableListOf<String>()
        val result = withContext(MainPages(site = "vk")) {
            MasterMainSelection(session).select(request, request.snapshot!!, emptyList()) {
                probed += it.mediaUrl
                ValidationResult.Valid(checked(it))
            }
        } as MasterResult.Success
        val row = result.result.candidates.single()
        assertEquals(file("5"), row.mediaUrl)
        assertEquals(1_920, row.height)
        assertEquals("Video by Asianese", row.title)
        assertEquals(60_000L, row.durationMillis)
        assertNull(row.audioCompanion)
        // The tallest file only: it passed, so nothing else was read.
        assertEquals(listOf(file("5")), probed)
        assertTrue(result.result.details.toString(), "master: VK page data: files=6; tried=1; passed=1; admitted=1" in result.result.details)
        assertTrue(result.result.details.none { "VK pieces' files" in it })
    }

    @Test
    fun `files that fail give way to the next, then to the pieces' row with the data's title`() = runBlocking {
        val session = session(data = true)
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val second = withContext(MainPages(site = "vk")) {
            MasterMainSelection(session).select(request, request.snapshot!!, emptyList()) {
                if (it.mediaUrl == file("5")) ValidationResult.Rejected(SiteExtractionFailure.HTTP_STATUS) else ValidationResult.Valid(checked(it))
            }
        } as MasterResult.Success
        assertEquals(file("3"), second.result.candidates.single().mediaUrl)
        assertTrue(second.result.details.toString(), "master: VK page data: files=6; tried=2; passed=1; admitted=1" in second.result.details)

        val pieces = withContext(MainPages(site = "vk")) {
            MasterMainSelection(session).select(request, request.snapshot!!, emptyList()) {
                if ("ct=0" in it.mediaUrl) ValidationResult.Rejected(SiteExtractionFailure.HTTP_STATUS) else ValidationResult.Valid(checked(it))
            }
        } as MasterResult.Success
        val row = pieces.result.candidates.single()
        assertEquals(whole("5", VIDEO), row.mediaUrl)
        assertEquals("Video by Asianese", row.title)
        assertEquals(whole("3", SOUND), row.audioCompanion!!.mediaUrl)
        assertTrue(pieces.result.details.toString(), "master: VK page data: files=6; tried=3; passed=0; admitted=0" in pieces.result.details)
        assertTrue("master: VK pieces' files: pictures passed 3 of 6; sounds passed 2 of 5" in pieces.result.details)
    }

    @Test
    fun `a file off the player's length gives way, a protected one is final, another site reads no page data`() = runBlocking {
        val session = session(data = true)
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val off = withContext(MainPages(site = "vk")) {
            MasterMainSelection(session).select(request, request.snapshot!!, emptyList()) {
                val checked = checked(it)
                ValidationResult.Valid(if (it.mediaUrl == file("5")) checked.copy(durationMillis = 50_000) else checked)
            }
        } as MasterResult.Success
        assertEquals(file("3"), off.result.candidates.single().mediaUrl)
        assertTrue(off.result.details.toString(), "master: VK page data: files=6; tried=2; passed=2; admitted=1" in off.result.details)

        val drm = withContext(MainPages(site = "vk")) {
            MasterMainSelection(session).select(request, request.snapshot!!, emptyList()) {
                ValidationResult.Rejected(SiteExtractionFailure.DRM_PROTECTED)
            }
        } as MasterResult.Failure
        assertEquals(SiteExtractionFailure.DRM_PROTECTED, drm.reason)

        val probed = mutableListOf<String>()
        val other = withContext(MainPages(site = "facebook")) {
            MasterMainSelection(session).select(request, request.snapshot!!, emptyList()) {
                probed += it.mediaUrl
                ValidationResult.Valid(checked(it))
            }
        } as MasterResult.Failure
        assertEquals(SiteExtractionFailure.NO_MEDIA_FOUND, other.reason)
        assertTrue(probed.isEmpty())
        assertTrue(other.details.toString(), "master: capture held requests=14 (dash=0, hls=0, pieces=14, player's=0); page data=1; player length known" in other.details)
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

    private fun session(data: Boolean): MasterBrowserSession {
        val session = MasterBrowserSession()
        val scope = session.navigate(PAGE)!!
        val requests = (
            listOf("4", "0", "1", "2", "3", "5").map { piece(it, VIDEO, "0-1407") to "video/mp4" } +
                listOf("4", "0", "1", "2", "3").map { piece(it, SOUND, "0-1300") to "audio/mp4" } +
                listOf(piece("2", VIDEO, "1408-900000") to "video/mp4", piece("2", VIDEO, "900001-1800000") to "video/mp4",
                    piece("2", SOUND, "1301-200000") to "audio/mp4")
            ).joinToString(",") { (url, mime) -> """{"url":"$url","mime":"$mime"}""" }
        val payloads = if (data) """"payloads":[${quote(DATA)}],""" else ""
        repeat(2) { index ->
            val raw = """{"pageUrl":"$PAGE","generation":${scope.generation},""" +
                (if (index == 0) """"requests":[$requests],$payloads""" else "") + """"player":{""" +
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

        /** The page data's whole file of [type] (its sound inside, `ct=0`). */
        fun file(type: String) = "$FILES?expires=1900000000000&type=$type&sig=s${type}0&ct=0&id=7"

        val DATA = """{"response":{"items":[{"id":400000001,"owner_id":-100000001,"title":"Video by Asianese","duration":60,""" +
            """"files":{"mp4_1080":"${file("5")}","mp4_144":"${file("4")}","mp4_240":"${file("0")}","mp4_360":"${file("1")}",""" +
            """"mp4_480":"${file("2")}","mp4_720":"${file("3")}"}}]}}"""

        fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
