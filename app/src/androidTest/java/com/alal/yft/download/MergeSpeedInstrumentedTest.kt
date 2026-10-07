package com.alal.yft.download

import android.annotation.TargetApi
import android.content.ContentResolver
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.alal.yft.core.download.AndroidMp4AudioVideoMuxer
import com.alal.yft.core.download.AudioVideoMuxEngine
import com.alal.yft.core.download.DashTransferRunner
import com.alal.yft.core.download.DownloadDestination
import com.alal.yft.core.download.MediaStoreDownloadDestination
import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.AudioVideoMuxResult
import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.StreamChunkCheckpoint
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType
import java.io.File
import java.nio.ByteBuffer
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P27 on the CI emulator with the real MediaStore and MediaMuxer: the merge writes straight into
 * a new pending row in `Download/YFT/` (no second copy) and gives a playable file with one video
 * and one sound track. A 20-minute input, made by repeating the one-second test tracks'
 * fragments, is merged in place and through app storage, and read once with MediaExtractor
 * alone; each run prints its split in a `YFT-DIAG mux-timing` line. Each test deletes the rows
 * it created, also when it fails.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
@TargetApi(Build.VERSION_CODES.Q)
class MergeSpeedInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val resolver: ContentResolver = context.contentResolver
    private val directory = File(context.cacheDir, "p27-merge").apply { mkdirs() }
    private val workspace = File(context.cacheDir, "p27-merge-workspace")
    private val rows = mutableListOf<Uri>()

    @After
    fun cleanUp() {
        rows.forEach { uri -> runCatching { resolver.delete(uri, null, null) } }
        directory.deleteRecursively()
        workspace.deleteRecursively()
    }

    @Test
    fun aDirectMergeIntoANewMediaStoreItemGivesAPlayableFileWithBothTracks() = runBlocking<Unit> {
        val run = merge(asset("mux/video-avc.mp4"), asset("mux/audio-aac.m4a"))

        assertTrue(run.log, run.log.startsWith("Merge done (in place)"))
        assertTrue(run.log, " copy " !in run.log)
        assertEquals(100, run.mergePercents.maxOrNull())
        read(run.uri) { extractor ->
            val mimeTypes = (0 until extractor.trackCount).map { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            }
            assertEquals(
                listOf(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_AUDIO_AAC),
                mimeTypes,
            )
            val video = extractor.getTrackFormat(0)
            assertEquals(160, video.getInteger(MediaFormat.KEY_WIDTH))
            assertEquals(90, video.getInteger(MediaFormat.KEY_HEIGHT))
            assertEquals(15, samples(extractor, track = 0).count)
            assertTrue(samples(extractor, track = 1).count > 0)
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, run.uri)
            assertEquals(
                "yes",
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO),
            )
            assertEquals(
                "yes",
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO),
            )
            assertNotNull("a picture decodes", retriever.getFrameAtTime(0))
        } finally {
            retriever.release()
        }
    }

    @Test
    fun aTwentyMinuteInputMergesInPlaceAndThroughAppStorageAndPrintsTheSplit() =
        runBlocking<Unit> {
            val video = File(directory, "long-video.mp4")
            val audio = File(directory, "long-audio.m4a")
            val made = SystemClock.elapsedRealtime()
            LongFragmentedMp4.write(assetBytes("mux/video-avc.mp4"), LONG_INPUT_US, video)
            LongFragmentedMp4.write(assetBytes("mux/audio-aac.m4a"), LONG_INPUT_US, audio)
            val makeMs = SystemClock.elapsedRealtime() - made

            val reading = SystemClock.elapsedRealtime()
            val videoSamples = readFile(video)
            val audioSamples = readFile(audio)
            val readMs = SystemClock.elapsedRealtime() - reading
            assertTrue(
                "video ${videoSamples.durationUs} us",
                videoSamples.durationUs >= LONG_INPUT_US - ONE_SECOND_US,
            )
            val samples = videoSamples.count + audioSamples.count
            diag(
                "read",
                "minutes" to "${videoSamples.durationUs / 60_000_000}",
                "samples" to "$samples",
                "size" to "${(video.length() + audio.length()) / ONE_MIB}MB",
                "make" to "${makeMs}ms",
                "read" to "${readMs}ms",
            )

            val inPlace = merge(video, audio)
            val copy = merge(video, audio, sdkInt = TODAYS_PATH_SDK)

            for ((case, run) in listOf("inplace" to inPlace, "copy" to copy)) {
                val steps = stepMillis(run.log)
                diag(
                    case,
                    "samples" to "$samples",
                    "size" to "${run.bytes / ONE_MIB}MB",
                    "merge" to "${steps["merge"]}ms",
                    "copy" to "${steps["copy"] ?: 0}ms",
                    "sync" to "${steps["sync"] ?: 0}ms",
                    "commit" to "${steps["commit"] ?: 0}ms",
                    "total" to "${run.wallMs}ms",
                    "percents" to "${run.mergePercents.distinct().size}",
                )
                assertTrue(run.log, run.mergePercents.distinct().size >= MIN_PERCENTS)
                read(run.uri) { extractor ->
                    assertEquals(2, extractor.trackCount)
                    val durationUs = extractor.getTrackFormat(0).getLong(MediaFormat.KEY_DURATION)
                    assertTrue("merged $durationUs us", durationUs >= LONG_INPUT_US - ONE_SECOND_US)
                }
            }
            assertTrue(inPlace.log, inPlace.log.startsWith("Merge done (in place)"))
            assertTrue(copy.log, copy.log.startsWith("Merge done (copy)"))
            assertEquals(100, copy.savePercents.maxOrNull())
        }

    /** Merges [video] and [audio] into a new pending row through the real engine. */
    private suspend fun merge(
        video: File,
        audio: File,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): MergeRun {
        val destination = MediaStoreDownloadDestination.create(resolver, newName(), VIDEO_MP4)
        val uri = Uri.parse(destination.recoveryUri)
        rows += uri
        val lines = Collections.synchronizedList(mutableListOf<String>())
        val checkpoints = Collections.synchronizedList(mutableListOf<AudioVideoMuxCheckpoint>())
        val engine = AudioVideoMuxEngine(
            dashTransfer = FileTrackRunner(video, audio),
            muxer = AndroidMp4AudioVideoMuxer(),
            workspaceRoot = workspace,
            sdkInt = sdkInt,
            log = { line -> lines += line },
        )
        val started = SystemClock.elapsedRealtime()
        val result = engine.transfer(
            plan = plan(),
            destination = destination,
            onCheckpoint = { checkpoint -> checkpoints += checkpoint },
        )
        val wallMs = SystemClock.elapsedRealtime() - started
        assertTrue("$result $lines", result is AudioVideoMuxResult.Completed)
        val log = lines.single { it.startsWith("Merge done") }
        fun percents(stage: AudioVideoMuxStage) =
            checkpoints.filter { it.stage == stage }.mapNotNull { it.stepPercent }
        return MergeRun(
            uri = uri,
            bytes = (result as AudioVideoMuxResult.Completed).bytesWritten,
            log = log,
            wallMs = wallMs,
            mergePercents = percents(AudioVideoMuxStage.MUXING),
            savePercents = percents(AudioVideoMuxStage.SAVING),
        )
    }

    private fun read(uri: Uri, block: (MediaExtractor) -> Unit) {
        val descriptor = requireNotNull(resolver.openFileDescriptor(uri, "r")) { "Cannot read" }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(descriptor.fileDescriptor)
            block(extractor)
        } finally {
            extractor.release()
            descriptor.close()
        }
    }

    /** Every sample of [file]'s one track, read as the merge reads them, and its duration. */
    private fun readFile(file: File): Samples {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val format = extractor.getTrackFormat(0)
            val samples = samples(extractor, track = 0)
            if (!format.containsKey(MediaFormat.KEY_DURATION)) return samples
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            return samples.copy(durationUs = maxOf(durationUs, samples.durationUs))
        } finally {
            extractor.release()
        }
    }

    private fun samples(extractor: MediaExtractor, track: Int): Samples {
        extractor.selectTrack(track)
        val buffer = ByteBuffer.allocateDirect(SAMPLE_BUFFER_BYTES)
        var count = 0L
        var lastUs = 0L
        while (extractor.sampleTime >= 0) {
            buffer.clear()
            extractor.readSampleData(buffer, 0)
            count += 1
            lastUs = maxOf(lastUs, extractor.sampleTime)
            extractor.advance()
        }
        extractor.unselectTrack(track)
        return Samples(count, lastUs)
    }

    /** "merge 12 s", "copy 850 ms", "sync 1 min 2 s" in the engine's line, in milliseconds. */
    private fun stepMillis(line: String): Map<String, Long> =
        STEP.findAll(line).associate { match ->
            val (name, minutes, seconds, decimal, millis) = match.destructured
            name to when {
                minutes.isNotEmpty() -> minutes.toLong() * 60_000 + seconds.toLong() * 1_000
                decimal.isNotEmpty() -> (decimal.toDouble() * 1_000).toLong()
                else -> millis.toLong()
            }
        }

    private fun diag(case: String, vararg values: Pair<String, String>) {
        val fields = values.joinToString(" ") { (key, value) -> "$key=$value" }
        Log.i(TAG, "YFT-DIAG mux-timing $case sdk=${Build.VERSION.SDK_INT} $fields")
    }

    private fun plan(): AudioVideoMuxDownloadPlan = AudioVideoMuxDownloadPlan(
        taskId = "p27-${UUID.randomUUID().toString().take(8)}",
        video = trackPlan("video", MediaTrackType.VIDEO, VIDEO_MP4, "avc1.42c00d"),
        audio = trackPlan("audio", MediaTrackType.AUDIO, "audio/mp4", "mp4a.40.2"),
        suggestedFileName = "merge.mp4",
    )

    private fun trackPlan(
        id: String,
        type: MediaTrackType,
        mimeType: String,
        codec: String,
    ): DashDownloadPlan = DashDownloadPlan(
        taskId = "p27-$id",
        manifestUrl = "https://media.example.test/p27/manifest.mpd",
        representationId = id,
        trackType = type,
        suggestedFileName = "$id.mp4",
        requestContext = BrowserRequestContext(
            pageUrl = "https://page.example.test/watch",
            userAgent = "YFT P27 test",
            cookie = null,
        ),
        mimeType = mimeType,
        codecs = listOf(codec),
    )

    private fun asset(name: String): File {
        val file = File(directory, name.substringAfterLast('/'))
        file.writeBytes(assetBytes(name))
        return file
    }

    private fun assetBytes(name: String): ByteArray =
        instrumentation.context.assets.open(name).use { it.readBytes() }

    private fun newName(): String = "yft-p27-${UUID.randomUUID().toString().take(8)}.mp4"

    /** A finished track download: the file is copied in, as if it had come from the network. */
    private class FileTrackRunner(
        private val video: File,
        private val audio: File,
    ) : DashTransferRunner {
        override suspend fun transfer(
            plan: DashDownloadPlan,
            destination: DownloadDestination,
            resumeFrom: DashTransferCheckpoint?,
            onProgress: suspend (DownloadProgress) -> Unit,
            onCheckpoint: suspend (DashTransferCheckpoint) -> Unit,
        ): DashTransferResult {
            val source = if (plan.trackType == MediaTrackType.VIDEO) video else audio
            val length = source.length()
            onProgress(DownloadProgress(0, length))
            destination.prepare(length)
            destination.open().use { output ->
                val buffer = ByteArray(ONE_MIB)
                var position = 0L
                source.inputStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(position, buffer, 0, read)
                        position += read
                    }
                }
                output.sync()
            }
            destination.commit()
            val checkpoint = DashTransferCheckpoint(
                manifestFingerprint = (if (source == video) "a" else "b").repeat(64),
                chunks = listOf(
                    StreamChunkCheckpoint(index = 0, downloadedBytes = length, completed = true),
                ),
            )
            onCheckpoint(checkpoint)
            return DashTransferResult.Completed(length, checkpoint)
        }

        override suspend fun discard(plan: DashDownloadPlan) = Unit
    }

    private data class MergeRun(
        val uri: Uri,
        val bytes: Long,
        val log: String,
        val wallMs: Long,
        val mergePercents: List<Int>,
        val savePercents: List<Int>,
    )

    private data class Samples(val count: Long, val durationUs: Long)

    private companion object {
        const val TAG = "YftMergeSpeed"
        const val VIDEO_MP4 = "video/mp4"
        const val ONE_MIB = 1_024 * 1_024
        const val SAMPLE_BUFFER_BYTES = 1_024 * 1_024
        const val ONE_SECOND_US = 1_000_000L
        const val LONG_INPUT_US = 20L * 60 * ONE_SECOND_US

        /** A long merge shows its percent move on, not one jump at the end. */
        const val MIN_PERCENTS = 25

        /** Android 7.x for the engine: today's path, a merge in app storage and a copy. */
        const val TODAYS_PATH_SDK = 25
        val STEP = Regex("""([a-z]+) (?:(\d+) min (\d+) s|(\d+\.\d+|\d+) s|(\d+) ms)""")
    }
}
