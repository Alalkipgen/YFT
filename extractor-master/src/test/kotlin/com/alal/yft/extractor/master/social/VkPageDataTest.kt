package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.api.SiteExtractionFailure
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.contract.AGENT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's retest of 7e101aa4 (2026-10-26): the page's own data for the video
 * (`video.getByIds`, every quality's whole file, the title, the length), as the capture carries
 * it, read as rows ([VkPageData]) the selection checks before the pieces' files. Bound to the
 * link's video; the trailer, the pictures, another video's data, another host and a body without
 * VK's files give nothing. The addresses are made up.
 */
class VkPageDataTest {
    @Test
    fun `the video's MP4 files are rows, the tallest first, with the data's title and length`() {
        val rows = VkPageData.of(request(), snapshot(listOf(DATA)))
        assertEquals(listOf(1_080, 720, 480, 360, 240, 144), rows.map { it.height })
        val tallest = rows.first()
        assertEquals(file("5"), tallest.mediaUrl)
        assertEquals("video/mp4", tallest.mimeType)
        assertEquals(MediaKind.DIRECT, tallest.kind)
        assertEquals("Video by Asianese", tallest.title)
        assertEquals(118_000L, tallest.durationMillis)
        assertEquals("https://sun9-1.userapi.com/impg/poster.jpg", tallest.thumbnailUrl)
        assertEquals(1_900_000_000_000L, tallest.expiresAtEpochMs)
        assertEquals(PAGE, tallest.pageUrl)
        assertTrue(tallest.videoId.orEmpty(), tallest.videoId.orEmpty().endsWith("-100000001_400000001"))
        assertTrue(rows.all { it.audioCompanion == null })
        // The trailer's files and the manifests are no rows.
        assertTrue(rows.none { "trailer" in it.mediaUrl || "m3u8" in it.mediaUrl || "ct=6" in it.mediaUrl })
    }

    @Test
    fun `another video's data, another host, no files and no VK data give nothing, the first body with files counts`() {
        assertTrue(VkPageData.of(request(), snapshot(listOf(DATA.replace("\"id\":400000001", "\"id\":400000002")))).isEmpty())
        assertTrue(VkPageData.of(request(), snapshot(listOf(DATA.replace("vk1-2.vkuser.net", "cdn.example")))).isEmpty())
        assertTrue(VkPageData.of(request(), snapshot(listOf("""{"response":{"items":[{"id":400000001,"owner_id":-100000001,"title":"t"}]}}"""))).isEmpty())
        assertTrue(VkPageData.of(request(), snapshot(listOf("""{"video_url":"https://cdn.test/main.mp4"}""", "not json {"))).isEmpty())
        assertTrue(VkPageData.of(request(), snapshot(emptyList())).isEmpty())
        val rows = VkPageData.of(request(), snapshot(listOf("{\"mp4_\":1}", DATA.replace("\"id\":400000001", "\"id\":400000002"), DATA)))
        assertEquals(6, rows.size)
    }

    @Test
    fun `the link is the request's identity, else the page address's`() {
        assertEquals(VkClient.Link(-100000001, 400000001), VkPageData.linkOf(request()))
        assertEquals(VkClient.Link(-5, 6), VkPageData.linkOf(request(expected = "-5_6")))
        assertEquals(VkClient.Link(7, 8), VkPageData.linkOf(request(page = "https://vkvideo.ru/clip7_8?list=abc")))
        assertEquals(VkClient.Link(-9, 10), VkPageData.linkOf(request(page = "https://vk.com/feed?z=video-9_10%2Fpl_wall")))
        assertNull(VkPageData.linkOf(request(page = "https://vkvideo.ru/@asianese")))
        assertNull(VkPageData.linkOf(request(page = "https://vkvideo.ru/video0_10")))
        // A request's identity that is no VK content ID: the page address's.
        assertEquals(VkClient.Link(-100000001, 400000001), VkPageData.linkOf(request(expected = "vk:odd")))
    }

    private companion object {
        const val PAGE = "https://m.vkvideo.ru/video-100000001_400000001"
        const val FILES = "https://vk1-2.vkuser.net/"

        fun file(type: String, ct: String = "0") = "$FILES?expires=1900000000000&type=$type&sig=s$type$ct&ct=$ct&id=7"

        val DATA = """{"response":{"items":[{"id":400000001,"owner_id":-100000001,"title":"Video by Asianese",""" +
            """"duration":118,"width":1078,"height":1920,"image":[{"url":"https://sun9-1.userapi.com/impg/poster.jpg","width":1280,"height":720}],""" +
            """"files":{"dash_sep":"${file("1", "6")}","failover_host":"vkvd572.okcdn.ru","hls":"${FILES}video.m3u8?type=2&ct=8",""" +
            """"mp4_1080":"${file("5")}","mp4_144":"${file("4")}","mp4_240":"${file("0")}","mp4_360":"${file("1")}",""" +
            """"mp4_480":"${file("2")}","mp4_720":"${file("3")}"},""" +
            """"trailer":{"mp4_1080":"${FILES}trailer?type=5&ct=0","mp4_144":"${FILES}trailer?type=4&ct=0"},""" +
            """"direct_url":"$PAGE","player":"https://vkvideo.ru/video_ext.php?oid=-100000001&id=400000001"}]}}"""

        fun request(expected: String? = null, page: String = PAGE) = MasterRequest(
            page, 1, NOW, SiteExtractionFailure.NO_MEDIA_FOUND, expectedContentId = expected,
            requestContext = BrowserRequestContext(page, AGENT, null),
        )

        fun snapshot(payloads: List<String>) = PageSnapshot(PAGE, 1, apiResponses = payloads, authorizedPlayback = true)
    }
}
