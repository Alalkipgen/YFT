package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.detection.VideoPlaybackSupport
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.LENGTH
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.MIB
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.audio
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.choices
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.group
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.resolvedAsset
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.sources
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.video
import com.alal.yft.ui.format.YftFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickDownloadChoicesTest {
    private val mp3Titles = listOf("MP3 · 320 kbps", "MP3 · 192 kbps", "MP3 · 128 kbps")

    @Test
    fun exactlyTwoSectionsAudioThenVideoWithOneRowPerResolution() {
        // P3-FIX (owner's phone): "Music" quick rows, "Fast"/"High" and More formats listed the
        // same formats twice. Now Audio (M4A, MP3 bitrates) and Video (one row per resolution).
        val choices = choices(QuickDownloadFixtures.youtube())!!

        assertEquals(
            listOf("1080p · Full HD", "720p · HD", "480p", "360p"),
            choices.video.map(SheetOption::title),
        )
        assertEquals(
            listOf("~80 MB", "~42 MB", "~18 MB", "11 MB"),
            choices.video.map(SheetOption::size),
        )
        assertEquals("1920 × 1080 · 30 fps · MP4", choices.video.first().detail)
        assertEquals(listOf("M4A · 128 kbps") + mp3Titles, choices.audio.map(SheetOption::title))
        assertTrue(choices.audio.all { it.section == OptionSection.AUDIO })
        assertTrue(choices.video.all { it.section == OptionSection.VIDEO && it.chips.isEmpty() })
        assertTrue(choices.options.none { "Fast" in it.title || "High" in it.title })
        assertEquals(choices.audio + choices.video, choices.options)
        // The merged rows keep the sound they are merged with.
        assertNotNull(choices.video.first().variant.audioCompanion)
        // Every row is found by its own id, and only by it.
        choices.options.forEach { assertSame(it, choices.option(it.id)) }
        assertEquals(choices.options.size, choices.options.map(SheetOption::id).distinct().size)
        assertNull(choices.option("missing"))
        assertNull(choices.option(null))
        assertEquals("Ocean waves", choices.title)
        assertEquals("youtube.com", choices.source)
        assertEquals(LENGTH, choices.durationMillis)
    }

    @Test
    fun rowsAreNamedAfterTheNearestStandardHeightAndKeepTheRealPicture() {
        // The owner's Facebook reel: 848 × 478 and 636 × 358 were "478p" and "358p".
        val choices = choices(
            listOf(
                video(478, 2 * MIB, width = 848, index = 1),
                video(358, MIB, width = 636, index = 2),
            ),
        )!!

        assertEquals(listOf("480p", "360p"), choices.video.map(SheetOption::title))
        assertEquals(
            listOf("848 × 478 · 30 fps · MP4", "636 × 358 · 30 fps · MP4"),
            choices.video.map(SheetOption::detail),
        )
        assertEquals(listOf(480, 360), choices.video.map(SheetOption::rankHeight))
    }

    @Test
    fun standardHeightsUseThePicturesShortSideAndNeverRoundUpOnATie() {
        fun name(width: Int?, height: Int) = QuickDownloadChoices.standardHeight(width, height)

        assertEquals(144, name(256, 144))
        assertEquals(240, name(426, 240))
        assertEquals(720, name(1108, 720))
        assertEquals(1080, name(1660, 1078))
        // A portrait reel is named after its width, like the sites do.
        assertEquals(720, name(720, 1280))
        assertEquals(1440, name(2560, 1440))
        assertEquals(2160, name(3840, 2160))
        assertEquals(4320, name(7680, 4320))
        assertEquals(1080, name(null, 1080))
        assertEquals(240, name(null, 300))
        assertEquals(480, name(null, 600))
    }

    @Test
    fun oneRowPerResolutionPrefersSoundThenAWholeFileAndKeepsASilentOnlyResolution() {
        val silent720 = video(720, 30 * MIB, index = 1)
        val sound720 = video(720, 35 * MIB, index = 2)
        val silent1080 = video(1080, 60 * MIB, index = 3)
        val stream720 = video(720, index = 4, kind = MediaKind.HLS)
        val sources = listOf(
            SheetSource(silent720, resolvedAsset(silent720, silent = true), true),
            SheetSource(stream720, resolvedAsset(stream720), true),
            SheetSource(sound720, resolvedAsset(sound720), true),
            SheetSource(silent1080, resolvedAsset(silent1080, silent = true), true),
        )

        val choices = QuickDownloadChoices.of(group(sources.map { it.candidate }), sources)!!

        assertEquals(listOf("1080p · Full HD", "720p · HD"), choices.video.map(SheetOption::title))
        assertSame(sound720, choices.video[1].source.candidate)
        assertEquals(listOf(QuickDownloadChoices.NO_SOUND), choices.video[0].chips)
        assertTrue(choices.video[1].chips.isEmpty())
    }

    @Test
    fun aCompleteFileBeatsAMergeAtTheSameResolution() {
        // Facebook (P4): the HD file and the 720p video track merged with the audio track.
        val hdFile = video(720, 30 * MIB, index = 1, label = "HD")
        val merged720 = video(720, 40 * MIB, merged = true, index = 2)
            .copy(bitrateBitsPerSecond = 2_749_477)
        val merged1080 = video(1080, 60 * MIB, merged = true, index = 3)
            .copy(codecs = listOf("av01.0.08M.08"), bitrateBitsPerSecond = 1_340_000)
        val candidates = listOf(merged1080, merged720, hdFile, audio(57, 2 * MIB))

        val choices = choices(candidates)!!

        assertEquals(listOf("1080p · Full HD", "720p · HD"), choices.video.map(SheetOption::title))
        assertSame(merged1080, choices.video[0].source.candidate)
        assertSame(hdFile, choices.video[1].source.candidate)
        assertEquals("M4A · 57 kbps", choices.audio.first().title)
    }

    @Test
    fun twoKAndFourKRowsThePhoneCannotDecodeAreStillOfferedWithAWarning() {
        // P6: YouTube's 2K and 4K are VP9 WebM merged with Opus; some phones cannot play them.
        fun webm(height: Int) = video(height, 300 * MIB, merged = true).copy(
            mediaUrl = "https://media.example.test/video-$height.webm",
            mimeType = "video/webm",
            codecs = listOf("vp9"),
        )
        val candidates = listOf(webm(2160), webm(1440)) + QuickDownloadFixtures.youtube()
        val asked = mutableListOf<Int?>()
        val noVp9 = VideoPlaybackSupport { variant ->
            asked += variant.height
            variant.codecs.none { it == "vp9" }
        }

        val warned = QuickDownloadChoices.of(group(candidates), sources(candidates), noVp9)!!
        val plain = QuickDownloadChoices.of(group(candidates), sources(candidates))!!

        assertEquals(
            listOf("2160p · 4K", "1440p · 2K", "1080p · Full HD", "720p · HD", "480p", "360p"),
            warned.video.map(SheetOption::title),
        )
        assertEquals("3840 × 2160 · 30 fps · WebM", warned.video.first().detail)
        assertEquals(
            listOf(QuickDownloadChoices.MAY_NOT_PLAY),
            warned.video.first().chips,
        )
        assertEquals(listOf(QuickDownloadChoices.MAY_NOT_PLAY), warned.video[1].chips)
        assertTrue(warned.video.drop(2).all { it.chips.isEmpty() })
        // Full HD and below are never asked about: every phone YFT runs on plays them.
        assertEquals(listOf(2160, 1440), asked)
        assertTrue(plain.video.all { it.chips.isEmpty() })
    }

    @Test
    fun aVideoWithoutAnAudioFileOffersItsOwnSoundAsM4aAndMp3() {
        // Facebook-style: one MP4 with AAC sound and no audio-only file.
        val file = video(360, 11 * MIB)
        val source = SheetSource(file, resolvedAsset(file, audioBitrate = 96_000), true)

        val choices = QuickDownloadChoices.of(group(listOf(file)), listOf(source))!!

        assertEquals(listOf("M4A · 96 kbps") + mp3Titles, choices.audio.map(SheetOption::title))
        val m4a = choices.audio.first()
        assertTrue(m4a.variant.audioFromVideo)
        assertEquals("The video's own sound", m4a.detail)
        // 252 s at 96 kbps.
        assertEquals("~${YftFormat.bytes(3_024_000)}", m4a.size)
        assertEquals(listOf(QuickDownloadChoices.SLOW), m4a.chips)
        assertTrue(
            choices.audio.drop(1).all { it.variant.mp3 != null && it.variant.audioFromVideo },
        )
        assertEquals("360p", choices.video.single().title)
        assertEquals("640 × 360 · 30 fps · MP4", choices.video.single().detail)
        assertEquals("11 MB", choices.video.single().size)
    }

    @Test
    fun audioOnlyGivesTheM4aAndMp3Only() {
        val choices = choices(listOf(audio(128, 4 * MIB), audio(48, 2 * MIB)))!!

        assertTrue(choices.video.isEmpty())
        assertTrue(choices.isAudioOnly)
        assertEquals(listOf("M4A · 128 kbps") + mp3Titles, choices.audio.map(SheetOption::title))
        assertEquals("4 MB", choices.audio[0].size)
        // 252 s at 192 kbps.
        assertEquals("~${YftFormat.bytes(6_048_000)}", choices.audio[2].size)
        assertFalse(choices.audio[0].variant.audioFromVideo)
        assertTrue(choices.audio.drop(1).all { it.chips == listOf(QuickDownloadChoices.SLOW) })
    }

    @Test
    fun aQualityLabelNeverComesFromThePageTitle() {
        // A file found on a page, not by an adapter: no height and the page's title.
        val file = video(
            height = null,
            bytes = 11 * MIB,
            label = null,
            title = "Morning swim — Lake",
            videoId = null,
        )

        val choices = choices(listOf(file))!!

        val row = choices.video.single()
        assertEquals("Quality unknown", row.title)
        assertEquals("MP4", row.detail)
        assertEquals("11 MB", row.size)
        assertTrue(choices.options.none { "Morning" in it.title || "Lake" in it.title })
        assertEquals("Morning swim — Lake", choices.title)
    }

    @Test
    fun facebooksUnmeasuredHdAndSdRankByTheSitesWord() {
        val id = "facebook:1603698891196107"
        val hd = video(height = null, bytes = 25 * MIB, label = "HD", videoId = id, index = 1)
        val sd = video(height = null, bytes = 9 * MIB, label = "SD", videoId = id, index = 2)
        val dash = video(
            height = null,
            label = "DASH",
            kind = MediaKind.DASH,
            videoId = id,
            index = 3,
        )
        val group = group(listOf(hd, sd, dash))
        // A source whose lookup failed is left out.
        val sources = listOf(hd, sd).map { SheetSource(it, resolvedAsset(it), true) } +
            SheetSource(dash, null, resolved = false)

        val choices = QuickDownloadChoices.of(group, sources)!!

        assertEquals(3, group.candidates.size)
        assertEquals("Ocean waves", choices.title)
        assertEquals(listOf("HD", "SD"), choices.video.map(SheetOption::title))
        assertEquals(listOf("25 MB", "9 MB"), choices.video.map(SheetOption::size))
        assertEquals(listOf(720, 480), choices.video.map(SheetOption::rankHeight))
    }

    @Test
    fun audioThatIsNotAacHasNoMp3Row() {
        val webm = choices(listOf(audio(160, mimeType = "audio/webm")))!!

        assertEquals(listOf("WebM · 160 kbps"), webm.audio.map(SheetOption::title))
        assertTrue(webm.options.none { it.variant.mp3 != null })
    }

    @Test
    fun nothingReadableGivesNoChoices() {
        val file = video(720)

        assertNull(
            QuickDownloadChoices.of(
                group(listOf(file)),
                listOf(SheetSource(file, null, resolved = false)),
            ),
        )
    }

    @Test
    fun preselectionFollowsTheDefaultQuality() {
        val choices = choices(QuickDownloadFixtures.youtube())!!
        val tall = choices(listOf(video(1080), video(720)))!!
        val music = choices(listOf(audio(128)))!!

        fun pick(from: QuickChoices, quality: QualityPreference) =
            QuickDownloadChoices.preselect(from, quality)?.title
        assertEquals("1080p · Full HD", pick(choices, QualityPreference.HIGHEST))
        assertEquals("1080p · Full HD", pick(choices, QualityPreference.UP_TO_1080P))
        assertEquals("720p · HD", pick(choices, QualityPreference.UP_TO_720P))
        assertEquals("480p", pick(choices, QualityPreference.UP_TO_480P))
        assertEquals("360p", pick(choices, QualityPreference.LOWEST))
        // Nothing at or below the ceiling: the lowest there is.
        assertEquals("720p · HD", pick(tall, QualityPreference.UP_TO_480P))
        assertEquals("M4A · 128 kbps", pick(music, QualityPreference.HIGHEST))
    }
}
