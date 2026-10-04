package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.LENGTH
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.MIB
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.audio
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.choices
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.group
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.resolvedAsset
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
    @Test
    fun aVideoWithoutAnAudioFileOffersItsOwnSoundAsM4aAndMp3() {
        // Facebook-style: one MP4 with AAC sound and no audio-only file (P3 regression: no Music).
        val file = video(360, 11 * MIB)
        val source = SheetSource(file, resolvedAsset(file, audioBitrate = 96_000), true)

        val choices = QuickDownloadChoices.of(group(listOf(file)), listOf(source))!!

        assertEquals(listOf("music", "mp3", "fast"), choices.rows.map(QuickRow::id))
        val music = choices.music.first()
        assertEquals("M4A · Fast", music.title)
        assertTrue(music.option.variant.audioFromVideo)
        // 252 s at 96 kbps.
        assertEquals("96 kbps · ~${YftFormat.bytes(3_024_000)}", music.detail)
        assertEquals(listOf(QuickDownloadChoices.SLOW), music.chips)
        val mp3 = choices.music.last()
        assertEquals(192, mp3.option.variant.mp3?.bitrateKbps)
        assertTrue(mp3.option.variant.audioFromVideo)
        assertEquals("360p · 30 fps · 11 MB", choices.video.single().detail)
    }

    @Test
    fun fourHeightsGiveFast480pAndHigh720p() {
        val candidates = listOf(
            video(1080, 80 * MIB),
            video(720, 42 * MIB),
            video(480, 18 * MIB),
            video(360, 11 * MIB),
        )

        val choices = choices(candidates)!!

        assertEquals(listOf("Fast", "High"), choices.video.map(QuickRow::title))
        assertEquals(
            listOf("480p · 30 fps · 18 MB", "720p · 30 fps · 42 MB"),
            choices.video.map(QuickRow::detail),
        )
        assertSame(candidates[2], choices.video[0].option.source.candidate)
        assertSame(candidates[1], choices.video[1].option.source.candidate)
        assertEquals("Ocean waves", choices.title)
        assertEquals("youtube.com", choices.source)
        assertEquals(LENGTH, choices.durationMillis)
    }

    @Test
    fun audioOnlyGivesMusicAndMp3Only() {
        val choices = choices(listOf(audio(128, 4 * MIB)))!!

        assertTrue(choices.video.isEmpty())
        assertTrue(choices.isAudioOnly)
        assertEquals(listOf("M4A · Fast", "MP3"), choices.music.map(QuickRow::title))
        assertEquals("128 kbps · 4 MB", choices.music[0].detail)
        // 252 s at 192 kbps.
        assertEquals("192 kbps · ~${YftFormat.bytes(6_048_000)}", choices.music[1].detail)
        assertFalse(choices.music[0].option.variant.audioFromVideo)
    }

    @Test
    fun youtubeRowsKeepTheirAudioCompanionAndMusicPicksTheBestM4a() {
        val choices = choices(QuickDownloadFixtures.youtube())!!

        val high = choices.video.single { it.kind == QuickRowKind.HIGH }
        assertNotNull(high.option.variant.audioCompanion)
        assertEquals("128 kbps · 4 MB", choices.music.first().detail)
        assertEquals(listOf("music", "mp3", "fast", "high"), choices.rows.map(QuickRow::id))
    }

    @Test
    fun moreFormatsListsEveryQualityAndAudioOptionWithChips() {
        val choices = choices(QuickDownloadFixtures.youtube())!!

        val video = choices.more.filter { it.section == OptionSection.VIDEO }
        assertEquals(
            listOf("1080p · Full HD", "720p · HD", "480p", "360p"),
            video.map(SheetOption::title),
        )
        assertEquals("1920 × 1080 · 30 fps · MP4", video.first().detail)
        assertTrue(video.all { it.chips == listOf(QuickDownloadChoices.VIDEO_AND_AUDIO) })
        val audio = choices.more.filter { it.section == OptionSection.AUDIO }
        assertEquals(
            listOf(
                "M4A · 128 kbps",
                "M4A · 48 kbps",
                "MP3 · 320 kbps",
                "MP3 · 192 kbps",
                "MP3 · 128 kbps",
            ),
            audio.map(SheetOption::title),
        )
        assertTrue(
            audio.filter { it.variant.mp3 != null }
                .all { it.chips == listOf(QuickDownloadChoices.SLOW) },
        )
        // 252 s at 320 kbps.
        assertEquals("~${YftFormat.bytes(10_080_000)}", audio[2].size)
        // Every More formats id finds its option; quick row ids find theirs.
        choices.more.forEach { assertSame(it, choices.option(QuickChoices.moreId(it))) }
        assertSame(choices.video.last().option, choices.option("high"))
        assertNull(choices.option("more:missing"))
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

        val option = choices.more.first { it.section == OptionSection.VIDEO }
        assertEquals("MP4 · Quality unknown", option.title)
        assertEquals("Video", choices.video.single().title)
        assertEquals("MP4 · 11 MB", choices.video.single().detail)
        assertTrue(choices.more.none { "Morning" in it.title || "Lake" in it.title })
        assertEquals("Morning swim — Lake", choices.title)
    }

    @Test
    fun facebooksThreeItemsAreOneVideoAndUnmeasuredHdSdRankByTheSitesWord() {
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
        // The DASH manifest is left out until it can be read (P4).
        val sources = listOf(hd, sd).map { SheetSource(it, resolvedAsset(it), true) } +
            SheetSource(dash, null, resolved = false)

        val choices = QuickDownloadChoices.of(group, sources)!!

        assertEquals(3, group.candidates.size)
        assertEquals("Ocean waves", choices.title)
        assertEquals(listOf("Fast", "High"), choices.video.map(QuickRow::title))
        assertEquals(listOf("SD · 9 MB", "HD · 25 MB"), choices.video.map(QuickRow::detail))
        assertEquals(
            listOf("HD · MP4", "SD · MP4"),
            choices.more.filter { it.section == OptionSection.VIDEO }.map(SheetOption::title),
        )
    }

    @Test
    fun onlyTallVideosGiveTheSmallestAsOneVideoRow() {
        val choices = choices(listOf(video(1440), video(1080)))!!

        assertEquals(QuickRowKind.VIDEO, choices.video.single().kind)
        assertEquals("1080p · 30 fps · Size unknown", choices.video.single().detail)
    }

    @Test
    fun audioThatIsNotAacHasNoMp3Row() {
        val webm = choices(listOf(audio(160, mimeType = "audio/webm")))!!

        assertEquals(listOf("WebM · Fast"), webm.music.map(QuickRow::title))
        assertTrue(webm.more.none { it.variant.mp3 != null })
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
        val music = choices(listOf(audio(128)))!!

        fun pick(quality: QualityPreference) = QuickDownloadChoices.preselect(choices, quality)?.id
        assertEquals("high", pick(QualityPreference.HIGHEST))
        assertEquals("high", pick(QualityPreference.UP_TO_720P))
        assertEquals("fast", pick(QualityPreference.UP_TO_480P))
        assertEquals("fast", pick(QualityPreference.LOWEST))
        assertEquals("music", QuickDownloadChoices.preselect(music, QualityPreference.HIGHEST)?.id)
    }
}
