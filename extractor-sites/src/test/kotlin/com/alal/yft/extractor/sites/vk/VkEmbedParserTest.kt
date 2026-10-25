package com.alal.yft.extractor.sites.vk

import com.alal.yft.core.model.media.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VkEmbedParserTest {
    @Test
    fun `a public video's files are every rendition, the best mp4 first`() {
        val video = found(VkFixtures.video())

        assertEquals(
            listOf(
                "mp4_2160", "mp4_1440", "mp4_1080", "mp4_720", "mp4_480", "mp4_360", "mp4_240",
                "mp4_144", "hls", "hls_fmp4", "dash_sep", "dash_webm", "dash_webm_av1",
            ),
            video.renditions.map(VkRendition::key),
        )
        assertEquals(
            listOf(2160, 1440, 1080, 720, 480, 360, 240, 144) + List(5) { null },
            video.renditions.map(VkRendition::height),
        )
        assertEquals(
            List(8) { MediaKind.DIRECT } + List(2) { MediaKind.HLS } + List(3) { MediaKind.DASH },
            video.renditions.map(VkRendition::kind),
        )
        assertEquals(
            List(8) { "video/mp4" } + List(2) { "application/x-mpegURL" } +
                List(3) { "application/dash+xml" },
            video.renditions.map(VkRendition::mimeType),
        )
        // Each address is its own (the fixture's first path segment is its key).
        video.renditions.forEach { rendition ->
            assertTrue(
                rendition.key,
                rendition.url.startsWith("https://vkvd741.okcdn.ru/${rendition.key}/"),
            )
            assertEquals(rendition.key, EXPIRES_MS, rendition.expiresAtEpochMs)
        }
        assertEquals(13, video.renditions.map(VkRendition::url).distinct().size)
        assertEquals("table", video.foundBy)
        assertEquals(0, video.refusedAddresses)
        assertFalse(video.isLive)
        assertEquals("Добивание Украины // Техник в коме // МОЯ ЗЛОСТЬ №140", video.title)
        assertEquals(4_651_000L, video.durationMillis)
        assertTrue(checkNotNull(video.thumbnailUrl).startsWith("https://sun9-70.userapi.com/"))
    }

    @Test
    fun `a clip and a finished live read the same way`() {
        val clip = found(VkFixtures.clip(), ownerId = 30014565, videoId = 456240946)
        val postlive = found(VkFixtures.postlive(), ownerId = -140332, videoId = 456239111)

        assertEquals(
            listOf(
                "mp4_1080", "mp4_720", "mp4_480", "mp4_360", "mp4_240", "mp4_144", "hls",
                "hls_fmp4", "dash_sep", "dash_webm",
            ),
            clip.renditions.map(VkRendition::key),
        )
        assertEquals("Clip by @diamondoly", clip.title)
        assertEquals(22_000L, clip.durationMillis)
        // The largest image (2304 × 4096) is the thumbnail.
        assertTrue(checkNotNull(clip.thumbnailUrl).startsWith("https://sun9-63.userapi.com/"))

        // A finished broadcast (`postlive`) is a video like any other.
        assertFalse(postlive.isLive)
        assertEquals(
            listOf("mp4_720", "hls_ondemand", "dash_ondemand"),
            postlive.renditions.map(VkRendition::key),
        )
        assertEquals(
            listOf(EXPIRES_MS, EXPIRES_MS, EXPIRES_MS),
            postlive.renditions.map(VkRendition::expiresAtEpochMs),
        )
        assertEquals("КХЛ. «Спартак» - «Автомобилист»", postlive.title)
    }

    @Test
    fun `previews, images and first frames are never renditions`() {
        val video = found(VkFixtures.video())

        assertTrue(video.renditions.none { "trailer" in it.url })
        assertTrue(video.renditions.all { VkUrls.isPlayerMediaUrl(it.url) })
        assertTrue(video.renditions.none { "iv.okcdn.ru" in it.url || "userapi.com" in it.url })
    }

    @Test
    fun `renamed files are found by the shape scan, and still never the preview`() {
        val page = VkFixtures.video()
        check(page.split("\"files\":{").size == 2)

        val video = found(page.replace("\"files\":{", "\"sources_v2\":{"))

        assertEquals("shape scan", video.foundBy)
        assertEquals(
            found(page).renditions.map(VkRendition::url),
            video.renditions.map(VkRendition::url),
        )
        assertTrue(video.renditions.none { "trailer" in it.url })
        assertEquals("Добивание Украины // Техник в коме // МОЯ ЗЛОСТЬ №140", video.title)
    }

    @Test
    fun `data moved out of the item list is found by the shape scan`() {
        val page = VkFixtures.clip()
        check(page.split("\"items\":[").size == 2)

        val clip = found(
            page.replace("\"items\":[", "\"videos\":["),
            ownerId = 30014565,
            videoId = 456240946,
        )

        assertEquals("shape scan", clip.foundBy)
        assertEquals(10, clip.renditions.size)
        assertEquals("Clip by @diamondoly", clip.title)
    }

    @Test
    fun `the shape scan takes vk's video servers only`() {
        val page = VkPages.embed(
            VkPages.item(files = null, more = ",\"sources\":{" +
                "\"mp4_720\":\"https://iv.okcdn.ru/mp4_720/F\"," +
                "\"mp4_480\":\"https://vkvideo1.okcdn.ru/mp4_480/F\"," +
                "\"url360\":\"https://vkvd2.okcdn.ru/url360/F\"," +
                "\"dash_uni\":\"https://vkvd2.okcdn.ru/dash_uni/F\"," +
                "\"hls_live\":\"https://vkvd2.okcdn.ru/hls_live/F\"}"),
        )

        val video = found(page)

        assertEquals("shape scan", video.foundBy)
        assertEquals(listOf("url360"), video.renditions.map(VkRendition::key))
        assertEquals(listOf(360), video.renditions.map(VkRendition::height))
    }

    @Test
    fun `another video's data is never the requested video's`() {
        val page = VkFixtures.video()
        val other = page.replace(
            "\"id\":456244102,\"owner_id\":-50883936",
            "\"id\":456244199,\"owner_id\":-50883936",
        )
        check(other != page)

        assertEquals(
            VkEmbed.Changed("no item for the video"),
            VkEmbedParser.parse(other, VIDEO_OWNER, VIDEO_ID),
        )
        // The clip's page asked for the video.
        assertEquals(
            VkEmbed.Changed("no item for the video"),
            VkEmbedParser.parse(VkFixtures.clip(), VIDEO_OWNER, VIDEO_ID),
        )
        // Next to the requested video, another one's files stay its own.
        val pair = VkPages.embed(
            VkPages.item(files = VkPages.files("mp4_1080"), ownerId = -1, id = 7),
            VkPages.item(files = "\"failover_host\":\"vkvd1.okcdn.ru\""),
        )
        val requested = found(pair)
        assertTrue(requested.renditions.isEmpty())
        assertEquals("Synthetic fixture", requested.title)
    }

    @Test
    fun `a broadcast that is not over is live`() {
        listOf("started", "waiting", "upcoming").forEach { state ->
            val page = VkFixtures.postlive()
                .replace("\"live_status\":\"postlive\"", "\"live_status\":\"$state\"")

            assertTrue(state, found(page, ownerId = -140332, videoId = 456239111).isLive)
        }
        val liveKeysOnly = VkPages.embed(
            VkPages.item(files = VkPages.files("hls_live", "dash_live")),
        )
        val live = found(liveKeysOnly)
        assertTrue(live.isLive)
        assertTrue(live.renditions.isEmpty())
    }

    @Test
    fun `files on hosts that are not vk's are refused and counted`() {
        val page = VkPages.embed(
            VkPages.item(
                files = "\"mp4_720\":\"https://cdn.example.org/mp4_720/F\"," +
                    "\"hls\":\"http://vkvd1.okcdn.ru/hls/F\"," +
                    "\"dash_sep\":\"https://iv.okcdn.ru/dash_sep/F\"," +
                    "\"mp4_480\":\"https://okcdn.ru.example.org/mp4_480/F\"," +
                    "\"mp4_360\":\"https://vkvd2.okcdn.ru/mp4_360/F\"",
            ),
        )

        val video = found(page)

        assertEquals(listOf("mp4_360"), video.renditions.map(VkRendition::key))
        assertEquals(4, video.refusedAddresses)
    }

    @Test
    fun `vk's own texts, its shell and changed pages`() {
        assertEquals(VkEmbed.NotFound, parse(VkFixtures.notFound()))
        assertEquals(VkEmbed.Shell, parse(VkFixtures.shell()))
        // vkvideo.ru's desktop page: an SPA shell whose prefetch cache holds no video.
        assertEquals(VkEmbed.Shell, parse(VkFixtures.desktopPage()))
        assertEquals(
            VkEmbed.Changed("no player data"),
            parse("<html><body>Something else</body></html>"),
        )
        // A cut-off array is not player data.
        assertEquals(
            VkEmbed.Changed("no player data"),
            parse(VkPages.embed(VkPages.item(VkPages.files("mp4_720"))).substringBefore("]}")),
        )
        assertEquals(
            VkEmbed.Changed("no item for the video"),
            parse(VkPages.cache("{\"config\":{\"mp4_720\":\"${VkPages.fileUrl("mp4_720")}\"}}")),
        )
    }

    @Test
    fun `titles decode html entities and fall back when empty or broken`() {
        fun titleOf(raw: String?): String? =
            found(VkPages.embed(VkPages.item(VkPages.files("mp4_720"), title = raw))).title

        assertEquals(
            "Tom & Jerry '1940' \"fixture\" A",
            titleOf("Tom &amp; Jerry &#39;1940&#39; &quot;fixture&quot; &#x41;"),
        )
        assertEquals("a ] b [ c } {", titleOf("a ] b [ c } {"))
        // Escaped quotes and backslashes inside the JSON string.
        assertEquals("say \"hi]\" \\ ok", titleOf("say \\\"hi]\\\" \\\\ ok"))
        assertNull(titleOf("\\ufffd\\ufffd broken"))
        assertNull(titleOf("   "))
        assertNull(titleOf(null))
        assertEquals(
            "&unknown; &#0; &#xD800;",
            VkEmbedParser.decodeEntities("&unknown; &#0; &#xD800;"),
        )
    }

    @Test
    fun `the thumbnail is the largest https image and the duration is bounded`() {
        val images = ",\"image\":[" +
            "{\"url\":\"http://sun9-1.userapi.com/F\",\"width\":4000,\"height\":4000}," +
            "{\"url\":\"https://sun9-2.userapi.com/F\",\"width\":320,\"height\":240}," +
            "{\"url\":\"https://sun9-3.userapi.com/F\",\"width\":800,\"height\":450}]"
        val video = found(VkPages.embed(VkPages.item(VkPages.files("mp4_720"), more = images)))

        assertEquals("https://sun9-3.userapi.com/F", video.thumbnailUrl)
        assertEquals(30_000L, video.durationMillis)
        val endless = VkPages.embed(
            VkPages.item(VkPages.files("mp4_720")).replace("\"duration\":30", "\"duration\":0"),
        )
        assertNull(found(endless).durationMillis)
    }

    private fun parse(html: String): VkEmbed = VkEmbedParser.parse(html, VIDEO_OWNER, VIDEO_ID)

    private fun found(
        html: String,
        ownerId: Long = VIDEO_OWNER,
        videoId: Long = VIDEO_ID,
    ): VkVideo = (VkEmbedParser.parse(html, ownerId, videoId) as VkEmbed.Found).video
}
