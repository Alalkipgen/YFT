package com.alal.yft.feature.downloads

import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DownloadTaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** After a merged download's tracks are in, the card says what it does and how far (P27). */
class MergeStageLabelsTest {
    @Test
    fun `a merging download shows the merge and its percent, not 99 percent`() {
        val row = rowOf(mergedTask("m", AudioVideoMuxStage.MUXING, stepDone = 45, stepTotal = 100))

        assertEquals("Merging audio and video · 45%", progressDetail(row))
        assertEquals("Merging audio and video · 45%", progressDetail(row, withSpeed = false))
        assertEquals("45%", percentLabel(row))
        assertEquals(0.45f, row.progressFraction ?: -1f, 0.001f)
        assertNull(row.secondsLeft)
    }

    @Test
    fun `saving names where the merged file goes, with its percent`() {
        fun detail(kind: DownloadDestinationKind): String = progressDetail(
            rowOf(mergedTask("s", AudioVideoMuxStage.SAVING, 80, 100, destinationKind = kind)),
        )

        assertEquals("Saving to Download/YFT · 80%", detail(DownloadDestinationKind.MEDIA_STORE))
        assertEquals("Saving to App storage · 80%", detail(DownloadDestinationKind.APP_PRIVATE))
        assertEquals(
            "Saving to the chosen folder · 80%",
            detail(DownloadDestinationKind.SAF_DOCUMENT),
        )
        val done = rowOf(mergedTask("s", AudioVideoMuxStage.SAVING, 100, 100))
        assertEquals("100%", percentLabel(done))
        assertEquals(1f, done.progressFraction ?: -1f, 0.001f)
    }

    @Test
    fun `before its first sample the merge has no percent and the bar moves on its own`() {
        listOf(AudioVideoMuxStage.READY_TO_MUX, AudioVideoMuxStage.MUXING).forEach { stage ->
            val row = rowOf(mergedTask("r", stage))

            assertEquals("Merging audio and video", progressDetail(row))
            assertNull(row.progressFraction)
            assertNull(percentLabel(row))
        }
        val saving = rowOf(mergedTask("c", AudioVideoMuxStage.SAVING))
        assertEquals("Saving to Download/YFT", progressDetail(saving))
        assertNull(saving.progressFraction)
    }

    @Test
    fun `downloading tracks, pausing and paused merged downloads keep their usual line`() {
        val downloading = rowOf(mergedTask("d", AudioVideoMuxStage.DOWNLOADING_TRACKS))
        val paused = rowOf(
            mergedTask("p", AudioVideoMuxStage.MUXING, 45, 100, DownloadDestinationKind.MEDIA_STORE)
                .copy(status = DownloadTaskStatus.PAUSED),
        )
        val pausing = rowOf(
            mergedTask("q", AudioVideoMuxStage.MUXING, 45, 100)
                .copy(status = DownloadTaskStatus.PAUSING),
        )

        assertFalse(progressDetail(downloading).startsWith("Merging"))
        assertEquals("99%", percentLabel(downloading))
        assertTrue(progressDetail(paused).endsWith("Paused"))
        assertEquals("Pausing", progressDetail(pausing))
    }

    private fun rowOf(task: StoredDownloadTask): DownloadRowUiState =
        DownloadsUiState.from(listOf(task)).rows.single()
}
