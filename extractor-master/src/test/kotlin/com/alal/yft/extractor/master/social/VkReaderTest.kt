package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asArrayOrEmpty
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** VK-Master plan §4 V1, Appendix B step 3: Master's own read of VK's answers, bound to the video. */
class VkReaderTest {
    @Test
    fun `the embed's key table gives the video's files in VK's order, never the trailer, a picture or another video's`() {
        val read = VkReader.embed(vkPack("embed_files.html"), LINK)

        assertEquals(
            listOf("mp4_720", "mp4_360", "hls", "dash_sep"),
            read.files.map { it.key },
        )
        assertEquals(listOf(VkReader.MP4, VkReader.MP4, VkReader.HLS, VkReader.DASH), read.files.map { it.type })
        assertEquals(listOf(720, 360, null, null), read.files.map { it.height })
        read.files.forEach { file ->
            assertTrue(file.url, file.url.startsWith("https://vkvd1.okcdn.ru/fixture/"))
            assertFalse(file.url, "trailer" in file.url || "other" in file.url)
        }
        assertEquals("Fixture embed \"title\"", read.title)
        assertEquals(42L, read.durationSeconds)
        assertEquals("https://iv.okcdn.ru/fixture/large.jpg", read.thumbnail)
        assertEquals(VkReading(rows = 4, item = true), read.reading)
        assertFalse(read.byShape)
    }

    @Test
    fun `video get's key table reads its item only, the failover host and the trailer left out`() {
        val read = VkReader.api(vkPack("video_get_mp4.json"), LINK)

        assertEquals(listOf("mp4_720", "mp4_360", "hls", "dash_sep"), read.files.map { it.key })
        read.files.forEach { assertTrue(it.url, it.url.startsWith("https://vk1-1.vkuser.net/fixture/")) }
        assertEquals("Fixture video & its title", read.title)
        assertEquals(61L, read.durationSeconds)
        assertEquals(VkReading(rows = 4, item = true), read.reading)
        assertEquals(
            "MP4 files 2, HLS 1, DASH 1 (VK's key table); title known; length known",
            read.line(),
        )
    }

    @Test
    fun `a renamed key is read by the shape scan, by its key's shape or its address's type, the poster and previews left out`() {
        val read = VkReader.api(vkPack("video_get_renamed.json"), LINK)

        assertEquals(listOf("stream_hd", "stream_sd", "stream_list"), read.files.map { it.key })
        assertEquals(listOf(VkReader.MP4, VkReader.MP4, VkReader.HLS), read.files.map { it.type })
        assertTrue(read.byShape)
        assertEquals(VkReading(rows = 3, byShape = true, item = true), read.reading)
        read.files.forEach { file ->
            listOf("preview", "poster", "trailer", "other", "cover").forEach { assertFalse(file.url, it in file.url) }
        }
        assertTrue(read.line(), read.line().contains("(the shape scan)"))
    }

    @Test
    fun `a live broadcast keeps no file, another video's entry none, and files elsewhere are counted, not kept`() {
        val live = VkReader.api(vkPack("video_get_live.json"), LINK)
        assertTrue(live.files.isEmpty())
        assertEquals(VkReading(item = true, live = true), live.reading)

        val other = VkReader.api(vkPack("video_get_other_video.json"), LINK)
        assertTrue(other.files.isEmpty())
        assertEquals(VkReading(otherVideo = true), other.reading)
        val elsewhereLink = VkClient.Link(-100000003, 400000003)
        assertEquals(VkReading(otherVideo = true), VkReader.embed(vkPack("embed_files.html"), elsewhereLink).reading)

        val elsewhere = VkReader.api(vkPack("video_get_unknown_host.json"), LINK)
        assertTrue(elsewhere.files.isEmpty())
        assertEquals(VkReading(refused = 2, item = true), elsewhere.reading)
        assertNull(elsewhere.answer(LINK, API))
    }

    @Test
    fun `a video VK does not play is restricted, deleted only with VK's icon, and VK's error or no JSON reads nothing`() {
        assertEquals(
            VkReading(item = true, restricted = true, deleted = true),
            VkReader.api(vkPack("video_get_deleted.json"), LINK).reading,
        )
        val other = vkPack("video_get_deleted.json").replace("delete_outline", "lock_outline")
        assertEquals(VkReading(item = true, restricted = true), VkReader.api(other, LINK).reading)
        assertEquals(VkReading(), VkReader.api(vkPack("video_get_error_5.json"), LINK).reading)
        assertEquals(VkReading(), VkReader.api("<html></html>", LINK).reading)
        assertEquals(VkReading(), VkReader.embed(vkPack("embed_adult.html"), LINK).reading)
    }

    @Test
    fun `the video's answer for VK's recipe names the video, its files and their earliest expiry, as JSON`() {
        val renamed = vkPack("video_get_renamed.json")
            .replace("sd.mp4?expires=$VK_PACK_EXPIRES", "sd.mp4?expires=${VK_PACK_EXPIRES + 1}")
        val read = VkReader.api(renamed, LINK)
        val answer = checkNotNull(read.answer(LINK, API))
        assertEquals(VkClient.SITE_ID, answer.site)
        assertEquals(API, answer.finalUrl)

        val root = checkNotNull(BoundedJsonParser.parse(answer.body)) as JsonValue.Object
        assertEquals("-100000001_400000001", root["id"].asStringOrNull)
        assertEquals("Fixture renamed", root["title"].asStringOrNull)
        assertEquals(61L, root["duration"].asLongOrNull)
        assertEquals("https://iv.okcdn.ru/fixture/large.jpg", root["image"].asStringOrNull)
        assertEquals(VK_PACK_EXPIRES, root["expires"].asLongOrNull)
        val files = root["files"].asArrayOrEmpty
        assertEquals(read.files.map { it.url }, files.map { it["url"].asStringOrNull })
        assertEquals(read.files.map { it.type }, files.map { it["type"].asStringOrNull })
    }

    @Test
    fun `a title with quotes, a backslash and a line break stays one JSON text`() {
        val title = "Fixture \\\"quoted\\\" \\\\ and\\nnext"
        val body = vkPack("video_get_mp4.json").replace("Fixture video &amp; its title", title)
        val read = VkReader.api(body, LINK)
        assertEquals("Fixture \"quoted\" \\ and\nnext", read.title)
        val root = checkNotNull(BoundedJsonParser.parse(checkNotNull(read.answer(LINK, API)).body)) as JsonValue.Object
        assertEquals(read.title, root["title"].asStringOrNull)
        assertEquals(4, root["files"].asArrayOrEmpty.size)
    }

    private companion object {
        val LINK = VkClient.Link(-100000001, 400000001)
        const val API = "https://api.vkvideo.ru/method/video.get?v=5.289&client_id=52461373&owner_id=-100000001" +
            "&videos=-100000001_400000001&extended=1"
    }
}
