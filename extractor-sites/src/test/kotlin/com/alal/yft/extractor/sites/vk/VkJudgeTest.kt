package com.alal.yft.extractor.sites.vk

import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.sites.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test

class VkJudgeTest {
    @Test
    fun `vk's own masters and mpd carry video`() {
        assertEquals(
            VkManifestVerdict.VIDEO,
            VkJudge.hls(Fixtures.read("vk/hls-master-video.m3u8")),
        )
        assertEquals(
            VkManifestVerdict.VIDEO,
            VkJudge.hls(Fixtures.read("vk/hls-fmp4-master-video.m3u8")),
        )
        assertEquals(VkManifestVerdict.VIDEO, VkJudge.dash(Fixtures.read("vk/dash-sep-video.mpd")))
        assertEquals(
            VkManifestVerdict.VIDEO,
            VkJudge.judge(MediaKind.HLS, Fixtures.read("vk/hls-master-video.m3u8")),
        )
        assertEquals(
            VkManifestVerdict.VIDEO,
            VkJudge.judge(MediaKind.DASH, Fixtures.read("vk/dash-sep-video.mpd")),
        )
    }

    @Test
    fun `a master or an mpd whose tracks are all audio is audio-only`() {
        assertEquals(VkManifestVerdict.AUDIO_ONLY, VkJudge.hls(audioOnlyMaster()))
        assertEquals(VkManifestVerdict.AUDIO_ONLY, VkJudge.dash(audioOnlyMpd()))
        assertEquals(VkManifestVerdict.AUDIO_ONLY, VkJudge.judge(MediaKind.DASH, audioOnlyMpd()))
    }

    @Test
    fun `a variant's video codec counts as video without a resolution`() {
        val master = "#EXTM3U\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=1000,CODECS=\"avc1.4d401f,mp4a.40.2\"\nFIXTURE\n"
        val mpd = "<MPD><Period><AdaptationSet>" +
            "<Representation codecs=\"vp09.00.10.08\" bandwidth=\"1\"/>" +
            "</AdaptationSet></Period></MPD>"

        assertEquals(VkManifestVerdict.VIDEO, VkJudge.hls(master))
        assertEquals(VkManifestVerdict.VIDEO, VkJudge.dash(mpd))
    }

    @Test
    fun `what the judge cannot tell is never called audio-only`() {
        // A media playlist (here VK's own audio rendition) lists no variants to judge.
        assertEquals(
            VkManifestVerdict.UNKNOWN,
            VkJudge.hls(Fixtures.read("vk/hls-audio-rendition.m3u8")),
        )
        assertEquals(VkManifestVerdict.UNKNOWN, VkJudge.hls("<html><body>Error</body></html>"))
        assertEquals(VkManifestVerdict.UNKNOWN, VkJudge.hls(""))
        assertEquals(VkManifestVerdict.UNKNOWN, VkJudge.dash("<html><body>Error</body></html>"))
        assertEquals(VkManifestVerdict.UNKNOWN, VkJudge.dash("<MPD><Period/></MPD>"))
        assertEquals(
            VkManifestVerdict.UNKNOWN,
            VkJudge.judge(MediaKind.DIRECT, Fixtures.read("vk/hls-master-video.m3u8")),
        )
    }

    companion object {
        /**
         * Synthetic (VK0 found no real audio-only master): the hls_fmp4 master's own audio
         * renditions with one audio variant and no video variant.
         */
        fun audioOnlyMaster(): String {
            val master = Fixtures.read("vk/hls-fmp4-master-video.m3u8")
            val media = master.lineSequence().filter { it.startsWith("#EXT-X-MEDIA:TYPE=AUDIO") }
                .toList()
            check(media.isNotEmpty())
            val variant = listOf(
                "#EXT-X-STREAM-INF:BANDWIDTH=130000,CODECS=\"mp4a.40.2\",AUDIO=\"12_3\"",
                "FIXTURE",
            )
            return (listOf("#EXTM3U", "#EXT-X-VERSION:6") + media + variant)
                .joinToString("\n", postfix = "\n")
        }

        /** Synthetic: the dash_sep MPD with its video adaptation set removed. */
        fun audioOnlyMpd(): String {
            val mpd = Fixtures.read("vk/dash-sep-video.mpd")
            val video = Regex(
                "<AdaptationSet id=\"1\".*?</AdaptationSet>",
                RegexOption.DOT_MATCHES_ALL,
            )
            check(video.containsMatchIn(mpd))
            return mpd.replace(video, "")
        }
    }
}
