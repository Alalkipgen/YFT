package com.alal.yft.download

import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.Mp3Encoding
import com.alal.yft.feature.downloads.mergedTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P34: which step a task is at, and the freeze detector's arithmetic. */
class BackgroundWorkTest {
    @Test
    fun everyStepOfATaskIsWorkAndOnlyDownloadingUsesTheNetwork() {
        assertEquals(TaskWork.DOWNLOADING, backgroundTask("a", downloaded = MB).work())
        assertEquals(TaskWork.DOWNLOADING, backgroundTask("a", total = null).work())
        assertEquals(
            TaskWork.MERGING,
            mergedTask("m", AudioVideoMuxStage.READY_TO_MUX).work(),
        )
        assertEquals(TaskWork.MERGING, mergedTask("m", AudioVideoMuxStage.MUXING).work())
        assertEquals(TaskWork.SAVING, mergedTask("m", AudioVideoMuxStage.SAVING).work())
        assertEquals(
            TaskWork.CONVERTING,
            backgroundTask("s", downloaded = MB, total = MB, mimeType = Mp3Encoding.MIME_TYPE)
                .work(),
        )
        assertEquals(TaskWork.SAVING, backgroundTask("c", downloaded = MB, total = MB).work())
        assertNull(backgroundTask("q", DownloadTaskStatus.QUEUED).work())
        assertNull(backgroundTask("p", DownloadTaskStatus.PAUSED).work())
        assertTrue(TaskWork.DOWNLOADING.usesNetwork)
        assertFalse(TaskWork.MERGING.usesNetwork)
    }

    @Test
    fun workOfSeveralTasksSaysWhetherBytesMoveAndWhetherMediaIsProcessed() {
        val both = BackgroundWork.of(
            listOf(
                backgroundTask("a", downloaded = MB),
                mergedTask("m", AudioVideoMuxStage.MUXING),
                backgroundTask("q", DownloadTaskStatus.QUEUED),
            ),
        )

        assertTrue(both.running)
        assertTrue(both.downloading)
        assertTrue(both.processing)
        assertEquals(TaskWork.DOWNLOADING, both.stage)
        assertEquals(
            BackgroundWork.Idle,
            BackgroundWork.of(listOf(backgroundTask("q", DownloadTaskStatus.QUEUED))),
        )
    }

    @Test
    fun onlyATickMoreThanTenSecondsLateWithTheWakeLockIsAFreeze() {
        val detector = FreezeDetector()

        assertNull(detector.tick(0, wakeLockHeld = true, stage = TaskWork.MERGING))
        assertNull(detector.tick(11_000, wakeLockHeld = true, stage = TaskWork.MERGING))
        assertNull(detector.tick(40_000, wakeLockHeld = false, stage = TaskWork.MERGING))
        assertEquals(
            BackgroundFreeze(lostMs = 99_000, stage = TaskWork.MERGING),
            detector.tick(140_000, wakeLockHeld = true, stage = TaskWork.MERGING),
        )
        detector.reset()
        assertNull(detector.tick(900_000, wakeLockHeld = true, stage = TaskWork.MERGING))
    }

    @Test
    fun freezeMessageSaysHowLongThePhonePausedYft() {
        assertEquals(
            "Your phone paused YFT in the background for 1 min 40 s.",
            freezeMessage(100_000),
        )
        assertEquals("12 s", pauseLength(11_600))
        assertEquals("2 min", pauseLength(120_000))
        assertEquals("1 h 5 min", pauseLength(3_900_000))
    }
}
