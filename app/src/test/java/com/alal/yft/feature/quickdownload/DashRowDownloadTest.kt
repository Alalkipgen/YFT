package com.alal.yft.feature.quickdownload

import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.feature.downloads.DownloadRequest
import com.alal.yft.feature.preview.PreviewDownloadStatus
import com.alal.yft.feature.quickdownload.DashManifestSheet.Companion.text
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * v1.0.2 fix R2: a DASH row of the sheet downloads its own Representation. The owner's Repro A
 * (DASH-IF's on-demand test: `SegmentBase` files under an http `BaseURL`) and Repro B (Unified
 * Streaming's AV1 Tears of Steel: a `SegmentTemplate` with a `SegmentTimeline` under `dash/`),
 * as their sites served them on 2026-10-31, through the sheet's Download as on the phone. On
 * Preview #161 every row queued the manifest's first Representation: the audio "1" for A's
 * 360p, `audio_eng=64008` for B's 240p and for its 128 kbps M4A.
 */
class DashRowDownloadTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `Repro A's 360p row downloads video 5 with sound 1, its M4A row the sound`() = runTest {
        val sheet = DashManifestSheet(A_URL, text(A))
        val rows = rows(sheet)
        assertEquals(listOf("360p"), rows.video.map(SheetOption::title))
        assertEquals(listOf("M4A"), rows.audio.map(SheetOption::title))

        val video = download(sheet, rows.video.single())
        assertEquals("5", video.variant.manifestVariantId)
        assertEquals("1", video.variant.audioCompanion?.representationId)
        val merge = video.request as DownloadRequest.Mux
        assertEquals("5", merge.plan.video.representationId)
        assertEquals(MediaTrackType.VIDEO, merge.plan.video.trackType)
        assertEquals("1", merge.plan.audio.representationId)
        assertEquals(MediaTrackType.AUDIO, merge.plan.audio.trackType)
        assertEquals("video/mp4", merge.plan.outputMimeType)
        assertEquals(480 to 360, video.variant.width to video.variant.height)

        val sound = download(sheet, rows.audio.single()).request as DownloadRequest.Dash
        assertEquals("1", sound.plan.representationId)
        assertEquals(MediaTrackType.AUDIO, sound.plan.trackType)
    }

    @Test
    fun `Repro B's silent rows download their own videos, its M4A row the 128 kbps sound`() =
        runTest {
            // AV1 merges off: the video rows are "No sound".
            val sheet = DashManifestSheet(B_URL, text(B))
            val rows = rows(sheet)
            assertEquals(listOf("720p · HD", "480p", "240p"), rows.video.map(SheetOption::title))
            rows.video.forEach { assertEquals(listOf(QuickDownloadChoices.NO_SOUND), it.chips) }
            assertEquals(listOf("M4A"), rows.audio.map(SheetOption::title))
            val expected = mapOf(
                "720p · HD" to "video_eng=2928000",
                "480p" to "video_eng=793000",
                "240p" to "video_eng=208000",
            )

            for (row in rows.video) {
                val video = download(sheet, row)
                val dash = video.request as DownloadRequest.Dash
                assertEquals(row.title, expected.getValue(row.title), dash.plan.representationId)
                assertEquals(MediaTrackType.VIDEO, dash.plan.trackType)
                assertEquals("video/mp4", dash.mimeType)
                assertTrue(dash.fileName, dash.fileName.endsWith(".mp4"))
                if (row.title == "240p") {
                    assertEquals(640 to 284, video.variant.width to video.variant.height)
                }
            }

            val sound = download(sheet, rows.audio.single()).request as DownloadRequest.Dash
            assertEquals("audio_eng=128002", sound.plan.representationId)
            assertEquals(MediaTrackType.AUDIO, sound.plan.trackType)
        }

    @Test
    fun `a row whose Representation left the manifest fails and queues nothing`() = runTest {
        // A's 360p when the manifest has no video 5 at Download, then none of its sound 1:
        // never another quality, nor the video without its sound.
        for ((gone, served) in listOf("5" to text(A).without("5"), "1" to text(A).without("1"))) {
            val sheet = DashManifestSheet(A_URL, text(A))
            val viewModel = sheet.open()
            advanceUntilIdle()
            viewModel.select(requireNotNull(viewModel.uiState.value.choices).video.single().id)
            sheet.served = served
            viewModel.download()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(
                gone,
                PreviewDownloadStatus.Rejected(QuickDownloadViewModel.QUALITY_UNAVAILABLE),
                state.downloadStatus,
            )
            assertEquals(
                listOf("Not in the manifest now: Representation 5 (video) with sound 1"),
                state.downloadDetails,
            )
            assertEquals(emptyList<Any>(), sheet.starter.queued)
        }

        // B's 240p and its M4A: never another video, nor the first sound audio_eng=64008.
        val b = DashManifestSheet(B_URL, text(B))
        val rows = rows(b)
        val small = rows.video.single { it.title == "240p" }
        val missing = mapOf(
            small to "Representation video_eng=208000 (video only)",
            rows.audio.single() to "Representation audio_eng=128002 (audio)",
        )
        for ((row, line) in missing) {
            b.served = text(B)
            val viewModel = b.open()
            advanceUntilIdle()
            viewModel.select(row.id)
            b.served = text(B).without(row.variant.manifestVariantId!!)
            viewModel.download()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(
                PreviewDownloadStatus.Rejected(QuickDownloadViewModel.QUALITY_UNAVAILABLE),
                state.downloadStatus,
            )
            assertEquals(listOf("Not in the manifest now: $line"), state.downloadDetails)
            assertEquals(emptyList<Any>(), b.starter.queued)
        }
    }

    private fun TestScope.rows(sheet: DashManifestSheet): QuickChoices {
        val viewModel = sheet.open()
        advanceUntilIdle()
        return requireNotNull(viewModel.uiState.value.choices)
    }

    /** Opens the sheet, picks [row] and taps Download: what is queued and what it fetches. */
    private fun TestScope.download(sheet: DashManifestSheet, row: SheetOption): Queued {
        val viewModel = sheet.open()
        advanceUntilIdle()
        viewModel.select(row.id)
        viewModel.download()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        val (asset, variant) = sheet.starter.queued.removeLastOrNull()
            ?: throw AssertionError("${row.title}: ${state.downloadStatus}")
        return Queued(variant, DashManifestSheet.plan(asset, variant))
    }

    private data class Queued(val variant: MediaVariant, val request: DownloadRequest)

    internal companion object {
        const val A_URL =
            "https://dash.akamaized.net/dash264/TestCases/1a/netflix/exMPD_BIP_TC1.mpd"
        const val A = "exMPD_BIP_TC1.mpd"
        const val B_URL = "https://demo.unified-streaming.com/k8s/features/stable/video/" +
            "tears-of-steel/tears-of-steel-av1.ism/.mpd"
        const val B = "tears-of-steel-av1.mpd"

        /** The manifest without its Representation [id]. */
        fun String.without(id: String): String {
            val representation = Regex(
                "<Representation\\s+id=\"${Regex.escape(id)}\"[\\s\\S]*?</Representation>\\s*",
            )
            require(representation.containsMatchIn(this)) { id }
            return replace(representation, "")
        }
    }
}
