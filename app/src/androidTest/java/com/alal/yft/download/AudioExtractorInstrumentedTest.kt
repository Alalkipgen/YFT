package com.alal.yft.download

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.download.AndroidAudioExtractor
import com.alal.yft.core.download.Mp3TranscodeResult
import com.alal.yft.core.model.download.DownloadFailureReason
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P3 on a real device or emulator: the sound of a one-second MP4 (MPEG-4 video and AAC, made
 * with ffmpeg) is copied into an M4A that Android's own extractor reads back as AAC only.
 */
@RunWith(AndroidJUnit4::class)
class AudioExtractorInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val directory = File(instrumentation.targetContext.cacheDir, "m4a-test").apply {
        mkdirs()
    }

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun theSoundOfAVideoBecomesAnM4a() = runBlocking {
        val output = File(directory, "sound.m4a")

        val result = AndroidAudioExtractor().extract(asset("mp4/clip-video-aac.mp4"), output)

        val bytes = (result as Mp3TranscodeResult.Completed).bytesWritten
        assertEquals(output.length(), bytes)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(output.path)
            assertEquals(1, extractor.trackCount)
            val format = extractor.getTrackFormat(0)
            assertEquals(MediaFormat.MIMETYPE_AUDIO_AAC, format.getString(MediaFormat.KEY_MIME))
            val duration = format.getLong(MediaFormat.KEY_DURATION) / 1_000
            assertTrue("duration $duration ms", abs(duration - 1_000) <= 150)
        } finally {
            extractor.release()
        }
    }

    @Test
    fun aVideoWithoutSoundFailsAsIncompatible() = runBlocking {
        val result = AndroidAudioExtractor().extract(
            asset("mp4/clip-silent.mp4"),
            File(directory, "none.m4a"),
        )

        assertEquals(
            Mp3TranscodeResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS),
            result,
        )
    }

    private fun asset(name: String): File {
        val file = File(directory, name.substringAfterLast('/'))
        instrumentation.context.assets.open(name).use { input ->
            file.outputStream().use(input::copyTo)
        }
        return file
    }
}
