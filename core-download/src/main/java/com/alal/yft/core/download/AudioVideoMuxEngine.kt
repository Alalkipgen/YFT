package com.alal.yft.core.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.AudioVideoMuxResult
import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DashTransferResult
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadProgress
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class MuxIncompatibilityReason {
    OUTPUT_CONTAINER,
    VIDEO_CONTAINER,
    AUDIO_CONTAINER,
    VIDEO_CODEC,
    AUDIO_CODEC,
}

sealed interface MuxCompatibility {
    data object Compatible : MuxCompatibility

    data class Incompatible(val reason: MuxIncompatibilityReason) : MuxCompatibility
}

/**
 * Deliberately conservative compatibility gate for Android's platform MP4 muxer.
 *
 * YFT does not bundle FFmpeg. Phase 4 therefore accepts only separate AVC/AAC ISO-BMFF tracks
 * that MediaExtractor can read and MediaMuxer can write on the minimum supported Android API.
 * AV1 video is accepted from [AV1_MP4_MIN_SDK], the first release whose MP4 muxer writes it
 * (Phase 11 P4: Facebook's desktop ladder is AV1).
 */
object AudioVideoMuxCompatibility {
    /** Android 14: MediaMuxer writes AV1 into MPEG-4 from this API level on. */
    const val AV1_MP4_MIN_SDK = 34

    fun evaluate(
        plan: AudioVideoMuxDownloadPlan,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): MuxCompatibility {
        if (plan.outputMimeType.normalizedMime() != MP4_OUTPUT_MIME) {
            return MuxCompatibility.Incompatible(MuxIncompatibilityReason.OUTPUT_CONTAINER)
        }
        if (plan.video.mimeType.normalizedMime() !in VIDEO_MP4_MIMES) {
            return MuxCompatibility.Incompatible(MuxIncompatibilityReason.VIDEO_CONTAINER)
        }
        if (plan.audio.mimeType.normalizedMime() !in AUDIO_MP4_MIMES) {
            return MuxCompatibility.Incompatible(MuxIncompatibilityReason.AUDIO_CONTAINER)
        }
        if (
            plan.video.codecs.isEmpty() ||
            plan.video.codecs.any { codec -> !canWriteVideo(codec, sdkInt) }
        ) {
            return MuxCompatibility.Incompatible(MuxIncompatibilityReason.VIDEO_CODEC)
        }
        if (
            plan.audio.codecs.isEmpty() ||
            plan.audio.codecs.any { codec ->
                AUDIO_CODEC_PREFIXES.none(codec.lowercase(Locale.US)::startsWith)
            }
        ) {
            return MuxCompatibility.Incompatible(MuxIncompatibilityReason.AUDIO_CODEC)
        }
        return MuxCompatibility.Compatible
    }

    /** Whether the MP4 muxer of Android [sdkInt] writes video in [codec], such as `avc1.64001f`. */
    fun canWriteVideo(codec: String, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        val normalized = codec.trim().lowercase(Locale.US)
        return VIDEO_CODEC_PREFIXES.any(normalized::startsWith) ||
            (normalized.startsWith(AV1_CODEC_PREFIX) && sdkInt >= AV1_MP4_MIN_SDK)
    }

    private fun String?.normalizedMime(): String? =
        this?.substringBefore(';')?.trim()?.lowercase(Locale.US)

    private const val AV1_CODEC_PREFIX = "av01"
    private const val MP4_OUTPUT_MIME = "video/mp4"
    private val VIDEO_MP4_MIMES = setOf("video/mp4", "video/iso.segment")
    private val AUDIO_MP4_MIMES = setOf("audio/mp4", "audio/iso.segment")
    private val VIDEO_CODEC_PREFIXES = setOf("avc1", "avc3")
    private val AUDIO_CODEC_PREFIXES = setOf("mp4a.40.")
}

sealed interface LocalMuxResult {
    data class Completed(val bytesWritten: Long) : LocalMuxResult

    data class Failure(val reason: DownloadFailureReason) : LocalMuxResult
}

interface LocalAudioVideoMuxer {
    fun mux(
        videoFile: File,
        audioFile: File,
        outputFile: File,
    ): LocalMuxResult
}

/**
 * Platform-only remuxer. Inputs must already be complete, unencrypted ISO-BMFF tracks.
 *
 * MediaExtractor/MediaMuxer behavior is device-dependent and remains an explicit device test.
 */
class AndroidMp4AudioVideoMuxer(
    private val maxSampleBufferBytes: Int = DEFAULT_MAX_SAMPLE_BUFFER_BYTES,
) : LocalAudioVideoMuxer {
    init {
        require(maxSampleBufferBytes in MIN_SAMPLE_BUFFER_BYTES..MAX_SAMPLE_BUFFER_BYTES)
    }

    override fun mux(
        videoFile: File,
        audioFile: File,
        outputFile: File,
    ): LocalMuxResult {
        if (!videoFile.isFile || videoFile.length() <= 0) {
            return LocalMuxResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
        }
        if (!audioFile.isFile || audioFile.length() <= 0) {
            return LocalMuxResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
        }
        val parent = outputFile.parentFile
            ?: return LocalMuxResult.Failure(DownloadFailureReason.STORAGE_UNAVAILABLE)
        if (!parent.isDirectory && !parent.mkdirs()) {
            return LocalMuxResult.Failure(DownloadFailureReason.STORAGE_UNAVAILABLE)
        }
        if (outputFile.exists() && !outputFile.delete()) {
            return LocalMuxResult.Failure(DownloadFailureReason.STORAGE_UNAVAILABLE)
        }

        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        return try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)
            val videoTrack = videoExtractor.findTrack(VIDEO_MIME_PREFIX)
                ?: return LocalMuxResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
            val audioTrack = audioExtractor.findTrack(AUDIO_MIME_PREFIX)
                ?: return LocalMuxResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
            videoExtractor.selectTrack(videoTrack.index)
            audioExtractor.selectTrack(audioTrack.index)

            muxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
            val outputVideoTrack = muxer.addTrack(videoTrack.format)
            val outputAudioTrack = muxer.addTrack(audioTrack.format)
            muxer.start()
            muxerStarted = true
            copySamples(
                video = SampleSource(videoExtractor, outputVideoTrack),
                audio = SampleSource(audioExtractor, outputAudioTrack),
                muxer = muxer,
                bufferBytes = sampleBufferBytes(videoTrack.format, audioTrack.format),
            )
            muxer.stop()
            muxerStarted = false
            muxer.release()
            muxer = null
            val bytes = outputFile.length()
            if (bytes > 0) {
                LocalMuxResult.Completed(bytes)
            } else {
                outputFile.delete()
                LocalMuxResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
            }
        } catch (error: IOException) {
            outputFile.delete()
            LocalMuxResult.Failure(error.storageFailureReason())
        } catch (_: IllegalArgumentException) {
            outputFile.delete()
            LocalMuxResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
        } catch (_: IllegalStateException) {
            outputFile.delete()
            LocalMuxResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
        } catch (_: SecurityException) {
            outputFile.delete()
            LocalMuxResult.Failure(DownloadFailureReason.INCOMPATIBLE_TRACKS)
        } finally {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            videoExtractor.release()
            audioExtractor.release()
        }
    }

    private fun copySamples(
        video: SampleSource,
        audio: SampleSource,
        muxer: MediaMuxer,
        bufferBytes: Int,
    ) {
        val buffer = ByteBuffer.allocateDirect(bufferBytes)
        val info = MediaCodec.BufferInfo()
        while (!video.finished || !audio.finished) {
            val source = when {
                video.finished -> audio
                audio.finished -> video
                video.extractor.sampleTime <= audio.extractor.sampleTime -> video
                else -> audio
            }
            val presentationTimeUs = source.extractor.sampleTime
            if (presentationTimeUs < 0) {
                source.finished = true
                continue
            }
            if (source.extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0) {
                throw IllegalArgumentException("Encrypted samples cannot be muxed")
            }
            buffer.clear()
            val sampleBytes = source.extractor.readSampleData(buffer, 0)
            if (sampleBytes < 0) {
                source.finished = true
                continue
            }
            if (sampleBytes == 0 || sampleBytes > buffer.capacity()) {
                throw IllegalArgumentException("Invalid or oversized media sample")
            }
            info.set(
                0,
                sampleBytes,
                presentationTimeUs,
                bufferFlagsFor(source.extractor.sampleFlags),
            )
            muxer.writeSampleData(source.outputTrack, buffer, info)
            if (!source.extractor.advance()) source.finished = true
        }
    }

    /**
     * Translates extractor sample flags into muxer buffer flags.
     *
     * The two constant sets overlap numerically but do not mean the same thing:
     * `SAMPLE_FLAG_ENCRYPTED` shares a value with `BUFFER_FLAG_CODEC_CONFIG` and
     * `SAMPLE_FLAG_PARTIAL_FRAME` shares a value with `BUFFER_FLAG_END_OF_STREAM`, so forwarding
     * the raw value could mark a normal sample as codec config or end of stream. Encrypted samples
     * are already rejected before this point, and only the key-frame flag is carried over.
     */
    private fun bufferFlagsFor(sampleFlags: Int): Int =
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            MediaCodec.BUFFER_FLAG_KEY_FRAME
        } else {
            0
        }

    private fun MediaExtractor.findTrack(mimePrefix: String): InputTrack? {
        repeat(trackCount) { index ->
            val format = getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith(mimePrefix, ignoreCase = true) == true) {
                return InputTrack(index, format)
            }
        }
        return null
    }

    private fun sampleBufferBytes(video: MediaFormat, audio: MediaFormat): Int =
        listOf(video, audio)
            .mapNotNull { format ->
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                } else {
                    null
                }
            }
            .maxOrNull()
            ?.coerceIn(MIN_SAMPLE_BUFFER_BYTES, maxSampleBufferBytes)
            ?: MIN_SAMPLE_BUFFER_BYTES

    private data class InputTrack(
        val index: Int,
        val format: MediaFormat,
    )

    private data class SampleSource(
        val extractor: MediaExtractor,
        val outputTrack: Int,
        var finished: Boolean = false,
    )

    private companion object {
        const val VIDEO_MIME_PREFIX = "video/"
        const val AUDIO_MIME_PREFIX = "audio/"
        const val MIN_SAMPLE_BUFFER_BYTES = 256 * 1_024
        const val DEFAULT_MAX_SAMPLE_BUFFER_BYTES = 8 * 1_024 * 1_024
        const val MAX_SAMPLE_BUFFER_BYTES = 32 * 1_024 * 1_024
    }
}

interface AudioVideoMuxRunner {
    suspend fun transfer(
        plan: AudioVideoMuxDownloadPlan,
        destination: DownloadDestination,
        resumeFrom: AudioVideoMuxCheckpoint? = null,
        onProgress: suspend (DownloadProgress) -> Unit = {},
        onCheckpoint: suspend (AudioVideoMuxCheckpoint) -> Unit = {},
    ): AudioVideoMuxResult

    suspend fun discard(plan: AudioVideoMuxDownloadPlan)
}

class AudioVideoMuxEngine(
    private val dashTransfer: DashTransferRunner,
    private val muxer: LocalAudioVideoMuxer,
    private val workspaceRoot: File,
    private val bufferBytes: Int = 64 * 1_024,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : AudioVideoMuxRunner {
    init {
        require(bufferBytes in 1_024..1024 * 1_024)
    }

    override suspend fun transfer(
        plan: AudioVideoMuxDownloadPlan,
        destination: DownloadDestination,
        resumeFrom: AudioVideoMuxCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (AudioVideoMuxCheckpoint) -> Unit,
    ): AudioVideoMuxResult = withContext(Dispatchers.IO) {
        val emptyCheckpoint = AudioVideoMuxCheckpoint()
        if (plan.expiresAtEpochMs?.let { it <= clock() } == true) {
            return@withContext failure(
                DownloadFailureReason.EXPIRED_URL,
                emptyCheckpoint,
            )
        }
        if (AudioVideoMuxCompatibility.evaluate(plan, sdkInt) !is MuxCompatibility.Compatible) {
            return@withContext failure(
                DownloadFailureReason.INCOMPATIBLE_TRACKS,
                emptyCheckpoint,
            )
        }
        val workspace = try {
            prepareWorkspace(plan.taskId)
        } catch (error: IOException) {
            return@withContext failure(error.storageFailureReason(), emptyCheckpoint)
        }
        val initial = try {
            reconcileCheckpoint(workspace, resumeFrom)
        } catch (error: IOException) {
            return@withContext failure(error.storageFailureReason(), emptyCheckpoint)
        }
        val tracker = MuxCheckpointTracker(initial, onProgress, onCheckpoint)
        try {
            tracker.emit()
            val trackFailures = coroutineScope {
                buildList {
                    if (!initial.videoReady) {
                        add(
                            async {
                                transferTrack(
                                    kind = TrackKind.VIDEO,
                                    plan = plan,
                                    workspace = workspace,
                                    resumeFrom = initial.video,
                                    tracker = tracker,
                                )
                            },
                        )
                    }
                    if (!initial.audioReady) {
                        add(
                            async {
                                transferTrack(
                                    kind = TrackKind.AUDIO,
                                    plan = plan,
                                    workspace = workspace,
                                    resumeFrom = initial.audio,
                                    tracker = tracker,
                                )
                            },
                        )
                    }
                }.awaitAll().filterNotNull()
            }
            val trackFailure = trackFailures.firstOrNull()
            if (trackFailure != null) {
                val checkpoint = tracker.snapshot()
                if (trackFailure.reason in NON_RESUMABLE_FAILURES) {
                    cleanupAll(plan, workspace)
                }
                return@withContext AudioVideoMuxResult.Failure(trackFailure, checkpoint)
            }

            tracker.setStage(AudioVideoMuxStage.READY_TO_MUX)
            val muxOutput = File(workspace, MUX_OUTPUT_NAME)
            if (muxOutput.exists() && !muxOutput.delete()) {
                throw MuxAbort(DownloadFailureReason.STORAGE_UNAVAILABLE)
            }
            tracker.setStage(AudioVideoMuxStage.MUXING)
            val muxResult = muxer.mux(
                videoFile = readyFile(workspace, TrackKind.VIDEO),
                audioFile = readyFile(workspace, TrackKind.AUDIO),
                outputFile = muxOutput,
            )
            val muxBytes = when (muxResult) {
                is LocalMuxResult.Completed -> muxResult.bytesWritten
                is LocalMuxResult.Failure -> throw MuxAbort(muxResult.reason)
            }
            if (muxBytes <= 0 || !muxOutput.isFile || muxOutput.length() != muxBytes) {
                throw MuxAbort(DownloadFailureReason.INTEGRITY_MISMATCH)
            }
            val bytesWritten = publish(muxOutput, destination)
            tracker.setStage(AudioVideoMuxStage.COMPLETED)
            val completed = tracker.snapshot()
            cleanupWorkspace(workspace, strict = false)
            AudioVideoMuxResult.Completed(bytesWritten, completed)
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { runCatching { tracker.emit() } }
            throw cancellation
        } catch (abort: MuxAbort) {
            File(workspace, MUX_OUTPUT_NAME).delete()
            val checkpoint = tracker.resetToReadyAfterMuxFailure()
            if (abort.reason in NON_RESUMABLE_FAILURES) {
                cleanupAll(plan, workspace)
            }
            AudioVideoMuxResult.Failure(
                DownloadFailure(abort.reason),
                checkpoint,
            )
        } catch (error: IOException) {
            File(workspace, MUX_OUTPUT_NAME).delete()
            AudioVideoMuxResult.Failure(
                DownloadFailure(error.storageFailureReason()),
                tracker.resetToReadyAfterMuxFailure(),
            )
        }
    }

    override suspend fun discard(plan: AudioVideoMuxDownloadPlan) =
        withContext(Dispatchers.IO) {
            var failure: Throwable? = null
            runCatching { dashTransfer.discard(plan.video) }
                .onFailure { failure = it }
            runCatching { dashTransfer.discard(plan.audio) }
                .onFailure { if (failure == null) failure = it }
            runCatching {
                cleanupWorkspace(workspaceFor(plan.taskId), strict = true)
            }.onFailure { if (failure == null) failure = it }
            failure?.let { throw IOException("Cannot discard mux transfer", it) }
            Unit
        }

    private suspend fun transferTrack(
        kind: TrackKind,
        plan: AudioVideoMuxDownloadPlan,
        workspace: File,
        resumeFrom: DashTransferCheckpoint?,
        tracker: MuxCheckpointTracker,
    ): DownloadFailure? {
        val trackPlan = if (kind == TrackKind.VIDEO) plan.video else plan.audio
        val result = dashTransfer.transfer(
            plan = trackPlan,
            destination = FileDownloadDestination(
                partialFile = partialFile(workspace, kind),
                completedFile = readyFile(workspace, kind),
            ),
            resumeFrom = resumeFrom,
            onProgress = { progress -> tracker.recordTotal(kind, progress.totalBytes) },
            onCheckpoint = { checkpoint -> tracker.update(kind, checkpoint) },
        )
        return when (result) {
            is DashTransferResult.Completed -> {
                tracker.update(kind, result.checkpoint)
                tracker.markReady(kind)
                null
            }
            is DashTransferResult.Failure -> {
                tracker.update(kind, result.checkpoint)
                result.failure
            }
        }
    }

    private fun reconcileCheckpoint(
        workspace: File,
        saved: AudioVideoMuxCheckpoint?,
    ): AudioVideoMuxCheckpoint {
        var checkpoint = saved ?: AudioVideoMuxCheckpoint()
        val videoReady = checkpoint.videoReady &&
            readyFile(workspace, TrackKind.VIDEO).isNonEmptyFile()
        val audioReady = checkpoint.audioReady &&
            readyFile(workspace, TrackKind.AUDIO).isNonEmptyFile()
        if (!videoReady) clearTrackFiles(workspace, TrackKind.VIDEO)
        if (!audioReady) clearTrackFiles(workspace, TrackKind.AUDIO)
        File(workspace, MUX_OUTPUT_NAME).let { file ->
            if (file.exists() && !file.delete()) {
                throw IOException("Cannot reset mux output")
            }
        }
        checkpoint = checkpoint.copy(
            videoReady = videoReady,
            audioReady = audioReady,
            stage = AudioVideoMuxStage.DOWNLOADING_TRACKS,
        )
        return checkpoint
    }

    private suspend fun publish(
        source: File,
        destination: DownloadDestination,
    ): Long {
        val totalBytes = source.length().takeIf { it > 0 }
            ?: throw MuxAbort(DownloadFailureReason.INTEGRITY_MISMATCH)
        try {
            destination.prepare(totalBytes)
            destination.open().use { output ->
                val buffer = ByteArray(bufferBytes)
                var position = 0L
                source.inputStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(position, buffer, 0, read)
                        position += read
                    }
                }
                output.sync()
                if (position != totalBytes) {
                    throw MuxAbort(DownloadFailureReason.INTEGRITY_MISMATCH)
                }
            }
            if (destination.temporaryLength() != totalBytes) {
                throw MuxAbort(DownloadFailureReason.INTEGRITY_MISMATCH)
            }
            destination.commit()
            return totalBytes
        } catch (abort: MuxAbort) {
            throw abort
        } catch (error: IOException) {
            throw MuxAbort(error.storageFailureReason())
        } catch (_: IllegalStateException) {
            throw MuxAbort(DownloadFailureReason.STORAGE_UNAVAILABLE)
        }
    }

    private fun prepareWorkspace(taskId: String): File {
        val root = workspaceRoot.canonicalFile
        if (!root.isDirectory && !root.mkdirs()) {
            throw IOException("Cannot create mux workspace")
        }
        val workspace = workspaceFor(taskId).canonicalFile
        if (workspace.parentFile != root) throw IOException("Unsafe mux workspace")
        if (!workspace.isDirectory && !workspace.mkdirs()) {
            throw IOException("Cannot create mux task workspace")
        }
        return workspace
    }

    private fun workspaceFor(taskId: String): File =
        File(workspaceRoot, DownloadWorkspaces.nameFor(DownloadWorkspaces.MUX_PREFIX, taskId))

    private suspend fun cleanupAll(
        plan: AudioVideoMuxDownloadPlan,
        workspace: File,
    ) {
        runCatching { dashTransfer.discard(plan.video) }
        runCatching { dashTransfer.discard(plan.audio) }
        cleanupWorkspace(workspace, strict = false)
    }

    private fun clearTrackFiles(workspace: File, kind: TrackKind) {
        listOf(partialFile(workspace, kind), readyFile(workspace, kind)).forEach { file ->
            if (file.exists() && !file.delete()) {
                throw IOException("Cannot reset mux track")
            }
        }
    }

    private fun cleanupWorkspace(workspace: File, strict: Boolean) {
        if (!workspace.exists()) return
        val root = workspaceRoot.canonicalFile
        val canonical = workspace.canonicalFile
        if (canonical.parentFile != root) throw IOException("Unsafe mux workspace")
        var failed = false
        canonical.walkBottomUp().forEach { file ->
            if (!file.delete()) failed = true
        }
        if (strict && failed) throw IOException("Cannot clean mux workspace")
    }

    private fun partialFile(workspace: File, kind: TrackKind): File =
        File(workspace, "${kind.fileStem}.part")

    private fun readyFile(workspace: File, kind: TrackKind): File =
        File(workspace, "${kind.fileStem}.ready")

    private fun failure(
        reason: DownloadFailureReason,
        checkpoint: AudioVideoMuxCheckpoint,
    ): AudioVideoMuxResult.Failure = AudioVideoMuxResult.Failure(
        DownloadFailure(reason),
        checkpoint,
    )

    private class MuxAbort(val reason: DownloadFailureReason) : Exception()

    private enum class TrackKind(val fileStem: String) {
        VIDEO("video"),
        AUDIO("audio"),
    }

    /**
     * Keeps the combined checkpoint and progress of both tracks.
     *
     * Progress has a total only once both track lengths are known: a finished track's length is
     * what it downloaded, and a track still downloading reports its length when it knows one.
     */
    private class MuxCheckpointTracker(
        initial: AudioVideoMuxCheckpoint,
        private val onProgress: suspend (DownloadProgress) -> Unit,
        private val onCheckpoint: suspend (AudioVideoMuxCheckpoint) -> Unit,
    ) {
        private val gate = Mutex()
        private var checkpoint = initial
        private val trackTotals = mutableMapOf<TrackKind, Long>().apply {
            if (initial.videoReady) initial.video?.let { put(TrackKind.VIDEO, it.downloadedBytes) }
            if (initial.audioReady) initial.audio?.let { put(TrackKind.AUDIO, it.downloadedBytes) }
        }

        /** Notes a track's length; the next checkpoint update reports it. */
        suspend fun recordTotal(kind: TrackKind, totalBytes: Long?) = gate.withLock {
            if (totalBytes != null && totalBytes > 0) trackTotals[kind] = totalBytes
        }

        suspend fun update(kind: TrackKind, track: DashTransferCheckpoint) = gate.withLock {
            checkpoint = when (kind) {
                TrackKind.VIDEO -> checkpoint.copy(video = track)
                TrackKind.AUDIO -> checkpoint.copy(audio = track)
            }
            emitLocked()
        }

        suspend fun markReady(kind: TrackKind) = gate.withLock {
            checkpoint = when (kind) {
                TrackKind.VIDEO -> checkpoint.copy(videoReady = true)
                TrackKind.AUDIO -> checkpoint.copy(audioReady = true)
            }
            val track = if (kind == TrackKind.VIDEO) checkpoint.video else checkpoint.audio
            track?.let { trackTotals[kind] = it.downloadedBytes }
            emitLocked()
        }

        suspend fun setStage(stage: AudioVideoMuxStage) = gate.withLock {
            checkpoint = checkpoint.copy(stage = stage)
            emitLocked()
        }

        suspend fun emit() = gate.withLock { emitLocked() }

        suspend fun snapshot(): AudioVideoMuxCheckpoint = gate.withLock { checkpoint }

        suspend fun resetToReadyAfterMuxFailure(): AudioVideoMuxCheckpoint = gate.withLock {
            if (checkpoint.videoReady && checkpoint.audioReady) {
                checkpoint = checkpoint.copy(stage = AudioVideoMuxStage.READY_TO_MUX)
            }
            checkpoint
        }

        private suspend fun emitLocked() {
            val activeChunks = listOfNotNull(checkpoint.video, checkpoint.audio)
                .sumOf { track -> track.chunks.count { !it.completed } }
            val downloaded = checkpoint.downloadedBytes
            val total = trackTotals.values.sum()
                .takeIf { trackTotals.size == TrackKind.entries.size && it >= downloaded }
            onProgress(
                DownloadProgress(
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    activeSegmentCount = activeChunks,
                ),
            )
            onCheckpoint(checkpoint)
        }
    }

    private companion object {
        const val MUX_OUTPUT_NAME = "mux-output.mp4"
        val NON_RESUMABLE_FAILURES = setOf(
            DownloadFailureReason.INVALID_URL,
            DownloadFailureReason.DRM_PROTECTED,
            DownloadFailureReason.UNSUPPORTED_SOURCE,
            DownloadFailureReason.INCOMPATIBLE_TRACKS,
            DownloadFailureReason.MALFORMED_RESPONSE,
            DownloadFailureReason.INTEGRITY_MISMATCH,
        )
    }
}

private fun File.isNonEmptyFile(): Boolean = isFile && length() > 0

private fun IOException.storageFailureReason(): DownloadFailureReason {
    val text = message.orEmpty().lowercase(Locale.US)
    return if (
        "enospc" in text ||
        "no space left" in text ||
        "disk full" in text
    ) {
        DownloadFailureReason.INSUFFICIENT_STORAGE
    } else {
        DownloadFailureReason.STORAGE_UNAVAILABLE
    }
}
