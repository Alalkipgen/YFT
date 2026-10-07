package com.alal.yft.core.browser.detection

import com.alal.yft.core.browser.detection.PlayerSetupScanner.Source
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.PageMediaRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P28: the files a page's own player is set up with, read by player, never by site. */
class PlayerSetupScannerTest {
    private val page = "https://tube.example.test/watch/77"

    @Test
    fun aJwPlayerSetupListsItsQualitiesWithTheirHeights() {
        val script = """
            jwplayer("player").setup({
              image: "https://img.example.test/v77/poster.jpg",
              sources: [
                {file: "https://cdn.example.test/v77/480.mp4", label: "480p", type: "video/mp4"},
                {file: "https://cdn.example.test/v77/720.mp4", label: "720p HD"}
              ],
              tracks: [{file: "/v77/captions.vtt", kind: "captions"}],
              advertising: {client: "vast", tag: "https://ads.example.test/vast.xml"}
            });
        """.trimIndent()

        assertEquals(
            listOf(
                Source("https://cdn.example.test/v77/480.mp4", 480, "video/mp4"),
                Source("https://cdn.example.test/v77/720.mp4", 720, null),
            ),
            PlayerSetupScanner.sources(script, page),
        )
    }

    @Test
    fun aSingleFileSetupAndAnHlsSourceAreRead() {
        val script = """
            var p = jwplayer('box').setup({ file: '/media/v77/master.m3u8', width: '100%' });
        """.trimIndent()

        assertEquals(
            listOf(Source("https://tube.example.test/media/v77/master.m3u8", null, null)),
            PlayerSetupScanner.sources(script, page),
        )
    }

    @Test
    fun kvsFlashvarsNameTheirFilesAndLabels() {
        val script = """
            var flashvars = {
              video_id: '77',
              video_url: 'https://cdn.example.test/get_file/1/abc/77/77.mp4/?br=900',
              video_url_text: '480p',
              video_alt_url: 'https://cdn.example.test/get_file/1/def/77/77_720p.mp4/',
              video_alt_url_text: '720p',
              preview_url: 'https://cdn.example.test/contents/77/preview.jpg'
            };
        """.trimIndent()

        val sources = PlayerSetupScanner.sources(script, page)

        assertEquals(listOf(480, 720), sources.map { it.height })
        assertEquals(listOf("video/mp4", "video/mp4"), sources.map { it.mimeType })
    }

    @Test
    fun aKvsAddressBuiltByScriptIsNotChased() {
        val script = """
            var flashvars = { video_url: 'function/0/https://cdn.example.test/get_file/77.mp4/' };
        """.trimIndent()

        assertTrue(PlayerSetupScanner.sources(script, page).isEmpty())
    }

    @Test
    fun aQualityListInPageDataIsReadEvenWhenEscaped() {
        val script = """
            window.page = {"mediaDefinitions":[
              {"format":"hls","quality":"1080","videoUrl":"https:\/\/c.example.test\/1080.m3u8"},
              {"format":"mp4","quality":"480","videoUrl":"https:\/\/c.example.test\/480.mp4"}
            ]};
        """.trimIndent()

        assertEquals(
            listOf(1080, 480),
            PlayerSetupScanner.sources(script, page).map { it.height },
        )
    }

    @Test
    fun previewsAdsAndLongListsAreNotThePlayersFiles() {
        val previews = """
            player.setup({ sources: [
              {file: "https://cdn.example.test/previews/77.mp4", label: "360p"},
              {file: "https://ads.example.test/creative/1.mp4", label: "720p"}
            ]});
        """.trimIndent()
        assertTrue(PlayerSetupScanner.sources(previews, page).isEmpty())

        val related = (1..12).joinToString(",", "var related = {sources: [", "]};") { index ->
            """{"src":"https://cdn.example.test/v/$index.mp4","label":"480p"}"""
        }
        assertTrue(PlayerSetupScanner.sources(related, page).isEmpty())
        assertTrue(PlayerSetupScanner.sources("var a = 'https://cdn.example.test/a.mp4';", page)
            .isEmpty())
    }

    @Test
    fun labelsNameAHeightOnlyWhenTheyStateOne() {
        assertEquals(720, PlayerSetupScanner.height("720p"))
        assertEquals(1080, PlayerSetupScanner.height("1080p60"))
        assertEquals(2160, PlayerSetupScanner.height("4K"))
        assertEquals(1440, PlayerSetupScanner.height("2K"))
        assertEquals(720, PlayerSetupScanner.height("1280x720"))
        assertEquals(null, PlayerSetupScanner.height("HD"))
        assertEquals(null, PlayerSetupScanner.height("Auto"))
    }

    @Test
    fun theScannerOffersAVideoJsSetupAsOneVideoWithItsQualities() {
        val html = """
            <video id="v" class="video-js" data-setup='{"sources":[
              {"src":"https://cdn.example.test/v77/480.mp4","type":"video/mp4","label":"480p"},
              {"src":"https://cdn.example.test/v77/720.mp4","type":"video/mp4","label":"720p"}]}'>
            </video>
        """.trimIndent()

        val result = HtmlMediaScanner().scan(html, page, observedAtEpochMs = 0)

        val videos = MediaGroups.pageVideos(result.candidates)
        assertEquals(1, videos.size)
        assertEquals(listOf(480, 720), videos.single().candidates.map { it.height })
        assertTrue(videos.single().candidates.all { it.pageRole == PageMediaRole.MAIN })
        assertTrue(videos.single().candidates.all { it.kind == MediaKind.DIRECT })
    }

    @Test
    fun aPageWithoutAPlayerSetupKeepsWhatItHadBefore() {
        val html = """
            <video controls src="https://cdn.example.test/v/also.mp4"></video>
            <script>var config = { theme: "dark", autoplay: false };</script>
        """.trimIndent()

        val result = HtmlMediaScanner().scan(html, page, observedAtEpochMs = 0)

        assertEquals(listOf(null), result.candidates.map { it.pageRole })
        assertEquals(listOf(null), result.candidates.map { it.pageVideoKey })
    }
}
