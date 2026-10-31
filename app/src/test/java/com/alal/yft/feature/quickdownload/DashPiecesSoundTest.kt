package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.detection.DashSoundPairing
import com.alal.yft.detection.DeviceMergeSupport
import com.alal.yft.feature.downloads.DownloadRequest
import com.alal.yft.feature.quickdownload.DashManifestSheet.Companion.text
import com.alal.yft.feature.quickdownload.DashRowDownloadTest.Companion.B
import com.alal.yft.feature.quickdownload.DashRowDownloadTest.Companion.B_URL
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * v1.0.2 fix R3: the owner's `bbb_30fps.mpd` (Akamai's Big Buck Bunny at 30 fps, as its site
 * served it on 2026-10-31): one `SegmentTemplate` for each adaptation set; its sound `bbb_a64k`,
 * HE-AAC (`mp4a.40.5`) at 67 kbps with no language, an `Accessibility` and a `Role`. The sound
 * was read, and is the sheet's M4A; but on Preview #161 every video row was "No sound": the
 * sheet gave a manifest's sound only to one-file (`SegmentBase`) qualities (item 59). Now each
 * quality has `bbb_a64k`, and its Download fetches that video and that sound.
 */
class DashPiecesSoundTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `bbb_30fps's sheet has its M4A and every video with that sound`() = runTest {
        val rows = rows(DashManifestSheet(C_URL, text(C)))

        assertEquals(listOf("M4A"), rows.audio.map(SheetOption::title))
        val m4a = rows.audio.single()
        assertEquals("67 kbps", m4a.detail)
        assertEquals("~5.1 MB", m4a.size)
        assertEquals("bbb_a64k", m4a.variant.manifestVariantId)
        assertEquals(EXPECTED.keys.toList(), rows.video.map(SheetOption::title))
        rows.video.forEach { row ->
            assertFalse(row.title, QuickDownloadChoices.NO_SOUND in row.chips)
            assertEquals(row.title, MediaTrackType.AUDIO_VIDEO, row.variant.trackType)
            assertEquals(row.title, "bbb_a64k", row.variant.audioCompanion?.representationId)
        }
    }

    @Test
    fun `each of bbb_30fps's rows downloads its own video with bbb_a64k, its M4A the sound`() =
        runTest {
            val sheet = DashManifestSheet(C_URL, text(C))
            val rows = rows(sheet)

            for (row in rows.video) {
                val merge = download(sheet, row) as DownloadRequest.Mux
                val video = merge.plan.video
                assertEquals(row.title, EXPECTED.getValue(row.title), video.representationId)
                assertEquals(MediaTrackType.VIDEO, video.trackType)
                assertEquals("bbb_a64k", merge.plan.audio.representationId)
                assertEquals(MediaTrackType.AUDIO, merge.plan.audio.trackType)
                assertEquals(listOf("mp4a.40.5"), merge.plan.audio.codecs)
                assertEquals("video/mp4", merge.plan.outputMimeType)
            }
            val sound = download(sheet, rows.audio.single()) as DownloadRequest.Dash
            assertEquals("bbb_a64k", sound.plan.representationId)
            assertEquals(MediaTrackType.AUDIO, sound.plan.trackType)
        }

    @Test
    fun `the manifest as the browser found it has the same rows`() = runTest {
        // The in-app browser's request, with the length and tallest picture its probe read.
        val browser = MediaCandidate(
            pageUrl = C_URL,
            mediaUrl = C_URL,
            sources = setOf(CandidateSource.REQUEST),
            kind = MediaKind.DASH,
            mimeType = "application/dash+xml",
            width = 3_840,
            height = 2_160,
            durationMillis = 634_566,
            requestContext = BrowserRequestContext(C_URL, "Mozilla/5.0 (Linux; Android 16)", null),
        )
        val pasted = rows(DashManifestSheet(C_URL, text(C)))
        val found = rows(DashManifestSheet(C_URL, text(C), browser))

        fun SheetOption.shown() =
            listOf(title, detail, size, variant.audioCompanion?.representationId)
        assertEquals(pasted.audio.map { it.shown() }, found.audio.map { it.shown() })
        assertEquals(pasted.video.map { it.shown() }, found.video.map { it.shown() })
        assertEquals(EXPECTED.keys.toList(), found.video.map(SheetOption::title))
        assertTrue(found.video.all { QuickDownloadChoices.NO_SOUND !in it.chips })
        assertTrue(found.video.all { it.variant.audioCompanion?.representationId == "bbb_a64k" })
    }

    @Test
    fun `Repro B's AV1 videos have its 128 kbps sound only while AV1 merges are on`() {
        // Item 26's debug switch on (Android 14, an AV1 decoder): every quality with sound; off,
        // as in the release, each stays "No sound" and downloads video only (fix R2).
        val asset = DashManifestSheet(B_URL, text(B)).read()
        fun videos(av1: Boolean): List<MediaVariant> {
            val phone = DeviceMergeSupport(sdkInt = 34, hasDecoder = { true }, av1Enabled = { av1 })
            return DashSoundPairing.pair(asset.variants, phone::canMerge)
                .filter { it.trackType != MediaTrackType.AUDIO }
        }

        val silent = videos(av1 = false)
        assertEquals(7, silent.size)
        assertTrue(silent.all { it.trackType == MediaTrackType.VIDEO && it.audioCompanion == null })
        val merged = videos(av1 = true)
        assertEquals(7, merged.size)
        assertEquals(
            setOf("audio_eng=128002"),
            merged.map { it.audioCompanion?.representationId }.toSet(),
        )
        val small = merged.single { it.manifestVariantId == "video_eng=208000" }
        val request = DashManifestSheet.plan(asset, small, av1 = true) as DownloadRequest.Mux
        assertEquals("video_eng=208000", request.plan.video.representationId)
        assertEquals("audio_eng=128002", request.plan.audio.representationId)
    }

    private fun TestScope.rows(sheet: DashManifestSheet): QuickChoices {
        val viewModel = sheet.open()
        advanceUntilIdle()
        return requireNotNull(viewModel.uiState.value.choices)
    }

    /** Opens the sheet, picks [row] and taps Download: what the queued download fetches. */
    private fun TestScope.download(sheet: DashManifestSheet, row: SheetOption): DownloadRequest {
        val viewModel = sheet.open()
        advanceUntilIdle()
        viewModel.select(row.id)
        viewModel.download()
        advanceUntilIdle()
        val (asset, variant) = sheet.starter.queued.removeLastOrNull()
            ?: throw AssertionError("${row.title}: ${viewModel.uiState.value.downloadStatus}")
        return DashManifestSheet.plan(asset, variant)
    }

    private companion object {
        const val C_URL = "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd"
        const val C = "bbb_30fps.mpd"

        /** Each row and the Representation its download fetches with `bbb_a64k`. */
        val EXPECTED = linkedMapOf(
            "2160p · 4K" to "bbb_30fps_3840x2160_12000k",
            "1080p · Full HD" to "bbb_30fps_1920x1080_8000k",
            "720p · HD" to "bbb_30fps_1280x720_4000k",
            "480p" to "bbb_30fps_1024x576_2500k",
            "360p" to "bbb_30fps_640x360_1000k",
            "240p" to "bbb_30fps_480x270_600k",
            "144p" to "bbb_30fps_320x180_400k",
        )
    }
}
