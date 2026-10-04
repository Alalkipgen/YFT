package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.settings.QualityPreference
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.MIB
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.audio
import com.alal.yft.feature.quickdownload.QuickDownloadFixtures.video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class QuickDownloadChoicesTest {
    @Test
    fun onlyA360pVideoGivesOneFastRow() {
        val choices = QuickDownloadChoices.of(listOf(video("360p", 11 * MIB)))!!

        assertEquals(listOf(QuickRowKind.FAST), choices.video.map(QuickRow::kind))
        assertEquals("360p · 11 MB", choices.video.single().detail)
        assertNull(choices.music)
        assertEquals("Ocean waves", choices.title)
        assertEquals(252_000L, choices.durationMillis)
    }

    @Test
    fun fourHeightsGiveFast480pAndHigh720p() {
        val candidates = listOf(
            video("1080p", 80 * MIB),
            video("720p", 42 * MIB),
            video("480p", 18 * MIB),
            video("360p", 11 * MIB),
        )

        val choices = QuickDownloadChoices.of(candidates)!!

        assertEquals(listOf("Fast", "High"), choices.video.map(QuickRow::title))
        assertEquals(listOf("480p · 18 MB", "720p · 42 MB"), choices.video.map(QuickRow::detail))
        assertSame(candidates[2], choices.video[0].candidate)
        assertSame(candidates[1], choices.video[1].candidate)
        assertEquals(4, choices.candidateCount)
    }

    @Test
    fun audioOnlyGivesMusicOnly() {
        val choices = QuickDownloadChoices.of(listOf(audio(128, 4 * MIB)))!!

        assertEquals(emptyList<QuickRow>(), choices.video)
        assertEquals("M4A · Fast", choices.music!!.title)
        assertEquals("Audio 128 kbps · 4 MB", choices.music!!.detail)
    }

    @Test
    fun youtubeRowsKeepTheirAudioCompanionAndMusicPicksTheBestM4a() {
        val choices = QuickDownloadChoices.of(QuickDownloadFixtures.youtube())!!

        val high = choices.video.single { it.kind == QuickRowKind.HIGH }
        assertNotNull(high.candidate.audioCompanion)
        assertEquals("Audio 128 kbps · 4 MB", choices.music!!.detail)
        assertEquals(listOf("music", "fast", "high"), choices.rows.map(QuickRow::id))
    }

    @Test
    fun hdAndSdLabelsRankWithoutAMadeUpHeight() {
        val choices = QuickDownloadChoices.of(listOf(video("HD"), video("SD")))!!

        assertEquals(listOf("Fast", "High"), choices.video.map(QuickRow::title))
        assertEquals(listOf("SD", "HD"), choices.video.map(QuickRow::detail))
    }

    @Test
    fun onlyTallVideosGiveTheLowestAsOneVideoRow() {
        val choices = QuickDownloadChoices.of(listOf(video("1440p"), video("1080p")))!!

        assertEquals(QuickRowKind.VIDEO, choices.video.single().kind)
        assertEquals("1080p", choices.video.single().detail)
    }

    @Test
    fun oneUnlabelledVideoIsOfferedWithItsFormat() {
        val choices = QuickDownloadChoices.of(listOf(video(label = null, title = null)))!!

        assertEquals(QuickRowKind.VIDEO, choices.video.single().kind)
        assertEquals("MP4", choices.video.single().detail)
        assertEquals("Video", choices.title)
    }

    @Test
    fun severalVideosOrAdaptiveStreamsKeepTheFoundList() {
        val twoVideos = listOf(video("720p", title = "One"), video("720p", title = "Two"))
        val unlabelled = listOf(video(null, index = 1), video(null, index = 2))
        val adaptive = listOf(video(null, kind = MediaKind.HLS))
        val protectedOnly = listOf(video("720p", drm = true))

        assertNull(QuickDownloadChoices.of(twoVideos))
        assertNull(QuickDownloadChoices.of(unlabelled))
        assertNull(QuickDownloadChoices.of(adaptive))
        assertNull(QuickDownloadChoices.of(protectedOnly))
        assertNull(QuickDownloadChoices.of(emptyList()))
    }

    @Test
    fun preselectionFollowsTheDefaultQuality() {
        val choices = QuickDownloadChoices.of(QuickDownloadFixtures.youtube())!!
        val music = QuickDownloadChoices.of(listOf(audio(128)))!!

        fun pick(quality: QualityPreference) = QuickDownloadChoices.preselect(choices, quality)?.id
        assertEquals("high", pick(QualityPreference.HIGHEST))
        assertEquals("high", pick(QualityPreference.UP_TO_720P))
        assertEquals("fast", pick(QualityPreference.UP_TO_480P))
        assertEquals("fast", pick(QualityPreference.LOWEST))
        assertEquals("music", QuickDownloadChoices.preselect(music, QualityPreference.HIGHEST)?.id)
    }
}
