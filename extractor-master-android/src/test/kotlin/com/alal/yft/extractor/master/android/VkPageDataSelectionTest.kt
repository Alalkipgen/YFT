package com.alal.yft.extractor.master.android

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.ValidationResult
import com.alal.yft.extractor.master.social.MainPages
import com.alal.yft.extractor.master.social.VkPageData
import com.alal.yft.extractor.master.verify.InspectedMedia
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's retest of 7e101aa4 (2026-10-26): the first Try again offered 480p (the pieces the
 * player had asked for by then) titled "ВКонтакте | VK Видео". In a VK lookup whose captured rows
 * gave nothing, the selection now checks the page's own data for the video first (its MP4 files
 * from the tallest, at most 3) and admits the first that passes, with the data's title; the
 * pieces' files only after that, their row titled by the data. v1.0.2 (item 11): once one is
 * admitted, each other height V passes is a row of its own, one video's qualities (one group, the
 * tallest first, no "More" videos), only the first inspected, at most [VkPageData.MAX_ROWS] read.
 * JVM only; the addresses are made up.
 */
class VkPageDataSelectionTest {
    @Test
    fun `each height of the page data that passes is a row of its own, one video's qualities, the tallest first`() = runBlocking {
        val session = session(data = true)
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val probed = mutableListOf<String>()
        val inspected = mutableListOf<String>()
        val selection = MasterMainSelection(session, inspect = { inspected += it.mediaUrl; InspectedMedia.of(it) })
        val result = withContext(MainPages(site = "vk")) {
            selection.select(request, request.snapshot!!, emptyList()) {
                probed += it.mediaUrl
                ValidationResult.Valid(checked(it))
            }
        } as MasterResult.Success
        val rows = result.result.candidates
        assertEquals(TALLEST_FIRST, rows.map { it.mediaUrl })
        val row = rows.first()
        assertEquals(1_920, row.height)
        assertEquals("Video by Asianese", row.title)
        assertEquals(60_000L, row.durationMillis)
        assertTrue(rows.all { it.title == "Video by Asianese" && it.audioCompanion == null })
        // One read of each file's start (V); the first admitted file alone is inspected.
        assertEquals(TALLEST_FIRST, probed)
        assertEquals(listOf(file("5")), inspected)
        val details = result.result.details
        assertTrue(details.toString(), "master: VK page data: files=6; tried=6; passed=6; admitted=6" in details)
        assertTrue(details.toString(), "master: automatic main selected; more=0" in details)
        assertTrue(details.toString(), "master: fingerprint qualities=6; dropped=0" in details)
        assertTrue(details.none { "VK pieces' files" in it })
        // The sheet's one video with six qualities, never "More" videos.
        val shown = selection.presentation(selection.appCandidates(rows, null)!!)!!
        assertEquals(TALLEST_FIRST, shown.main.candidates.map { it.mediaUrl })
        assertEquals("Video by Asianese", shown.main.title)
        assertTrue(shown.more.isEmpty())
    }

    @Test
    fun `the qualities stop at the lookup's reads, a height named twice is one row`() = runBlocking {
        // Nine heights, 720 named twice (`mp4_720`, then `mp4_0720`): one row per height, at most
        // MAX_ROWS of them read.
        val tail = "\"mp4_720\":\"${file("3")}\"}"
        val many = DATA.replace(
            tail,
            "\"mp4_720\":\"${file("3")}\",\"mp4_0720\":\"${file("9")}\",\"mp4_1440\":\"${file("6")}\"," +
                "\"mp4_2160\":\"${file("7")}\",\"mp4_4320\":\"${file("8")}\"}",
        )
        assertTrue(many != DATA)
        val session = session(data = true, body = many)
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val probed = mutableListOf<String>()
        val result = withContext(MainPages(site = "vk")) {
            MasterMainSelection(session).select(request, request.snapshot!!, emptyList()) {
                probed += it.mediaUrl
                ValidationResult.Valid(checked(it))
            }
        } as MasterResult.Success
        assertEquals(8, VkPageData.MAX_ROWS)
        val read = listOf(file("8"), file("7"), file("6")) + TALLEST_FIRST.dropLast(1)
        assertEquals(read, probed)
        assertEquals(read, result.result.candidates.map { it.mediaUrl })
        assertTrue(result.result.details.toString(), "master: VK page data: files=9; tried=8; passed=8; admitted=8" in result.result.details)
        assertTrue("master: automatic main selected; more=0" in result.result.details)
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
        assertEquals(TALLEST_FIRST.drop(1), second.result.candidates.map { it.mediaUrl })
        assertTrue(second.result.details.toString(), "master: VK page data: files=6; tried=6; passed=5; admitted=5" in second.result.details)

        val pieces = withContext(MainPages(site = "vk")) {
            MasterMainSelection(session).select(request, request.snapshot!!, emptyList()) {
                if ("ct=0" in it.mediaUrl) ValidationResult.Rejected(SiteExtractionFailure.HTTP_STATUS) else ValidationResult.Valid(checked(it))
            }
        } as MasterResult.Success
        val row = pieces.result.candidates.single()
        assertEquals(whole("5", VIDEO), row.mediaUrl)
        assertEquals("Video by Asianese", row.title)
        assertEquals(whole("3", SOUND), row.audioCompanion!!.mediaUrl)
        // None admitted: still at most three of the data's files read, so the pieces keep theirs.
        assertTrue(pieces.result.details.toString(), "master: VK page data: files=6; tried=3; passed=0; admitted=0" in pieces.result.details)
        assertTrue("master: VK pieces' files: pictures passed 3 of 6; sounds passed 2 of 5" in pieces.result.details)
    }

    @Test
    fun `a file off the player's length gives way, a protected one is final, another site reads no page data`() = runBlocking {
        val session = session(data = true)
        val request = session.request(SiteExtractionFailure.PLAYER_SCRIPT_REQUIRED, 1)!!
        val off = withContext(MainPages(site = "vk")) {
            MasterMainSelection(session).select(request, request.snapshot!!, emptyList()) {
                ValidationResult.Valid(checked(it, if (it.mediaUrl == file("5")) 50_000 else 60_000))
            }
        } as MasterResult.Success
        assertEquals(TALLEST_FIRST.drop(1), off.result.candidates.map { it.mediaUrl })
        assertTrue(off.result.details.toString(), "master: VK page data: files=6; tried=6; passed=6; admitted=5" in off.result.details)

        // Another quality off the player's length is left out; another protected is final.
        val offQuality = withContext(MainPages(site = "vk")) {
            MasterMainSelection(session).select(request, request.snapshot!!, emptyList()) {
                ValidationResult.Valid(checked(it, if (it.mediaUrl == file("2")) 50_000 else 60_000))
            }
        } as MasterResult.Success
        assertEquals(TALLEST_FIRST - file("2"), offQuality.result.candidates.map { it.mediaUrl })
        assertTrue(offQuality.result.details.toString(), "master: VK page data: files=6; tried=6; passed=6; admitted=5" in offQuality.result.details)
        val protectedQuality = withContext(MainPages(site = "vk")) {
            MasterMainSelection(session).select(request, request.snapshot!!, emptyList()) {
                if (it.mediaUrl == file("3")) ValidationResult.Rejected(SiteExtractionFailure.DRM_PROTECTED) else ValidationResult.Valid(checked(it))
            }
        } as MasterResult.Failure
        assertEquals(SiteExtractionFailure.DRM_PROTECTED, protectedQuality.reason)

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
    private fun checked(row: MediaCandidate, length: Long = 60_000): MediaCandidate {
        val type = Regex("[?&]type=(\\d+)").find(row.mediaUrl)!!.groupValues[1]
        return if (row.mimeType == "audio/mp4") {
            row.copy(durationMillis = length, contentLengthBytes = SOUND_SIZES.getValue(type))
        } else {
            row.copy(durationMillis = length, height = HEIGHTS.getValue(type), width = 1_080, contentLengthBytes = 1_000_000L * (HEIGHTS.getValue(type) / 100), codecs = listOf("avc1"))
        }
    }

    private fun session(data: Boolean, body: String = DATA): MasterBrowserSession {
        val session = MasterBrowserSession()
        val scope = session.navigate(PAGE)!!
        val requests = (
            listOf("4", "0", "1", "2", "3", "5").map { piece(it, VIDEO, "0-1407") to "video/mp4" } +
                listOf("4", "0", "1", "2", "3").map { piece(it, SOUND, "0-1300") to "audio/mp4" } +
                listOf(piece("2", VIDEO, "1408-900000") to "video/mp4", piece("2", VIDEO, "900001-1800000") to "video/mp4",
                    piece("2", SOUND, "1301-200000") to "audio/mp4")
            ).joinToString(",") { (url, mime) -> """{"url":"$url","mime":"$mime"}""" }
        val payloads = if (data) """"payloads":[${quote(body)}],""" else ""
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
        val HEIGHTS = mapOf(
            "4" to 256, "0" to 426, "1" to 640, "2" to 852, "3" to 1_280, "5" to 1_920,
            "6" to 2_560, "7" to 3_840, "8" to 7_680, "9" to 1_280,
        )
        val SOUND_SIZES = mapOf("4" to 500_000L, "0" to 800_000L, "1" to 1_000_000L, "2" to 1_900_000L, "3" to 3_900_000L)

        fun whole(type: String, ct: String) = "$FILES?expires=1900000000&type=$type&sig=s$type$ct&ct=$ct&id=7"

        fun piece(type: String, ct: String, range: String) = whole(type, ct) + "&bytes=$range"

        /** The page data's whole file of [type] (its sound inside, `ct=0`). */
        fun file(type: String) = "$FILES?expires=1900000000000&type=$type&sig=s${type}0&ct=0&id=7"

        /** The page data's files, one per height, the tallest first (1080, 720, 480, 360, 240, 144). */
        val TALLEST_FIRST = listOf("5", "3", "2", "1", "0", "4").map(::file)

        val DATA = """{"response":{"items":[{"id":400000001,"owner_id":-100000001,"title":"Video by Asianese","duration":60,""" +
            """"files":{"mp4_1080":"${file("5")}","mp4_144":"${file("4")}","mp4_240":"${file("0")}","mp4_360":"${file("1")}",""" +
            """"mp4_480":"${file("2")}","mp4_720":"${file("3")}"}}]}}"""

        fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
