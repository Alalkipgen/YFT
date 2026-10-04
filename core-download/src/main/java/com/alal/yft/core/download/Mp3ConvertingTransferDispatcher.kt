package com.alal.yft.core.download

import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.Mp3Encoding
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.TransferCheckpoint
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Converts direct audio downloads that ask for MP3 (T18) or for the sound of a video only (P3,
 * [DirectDownloadPlan.audioOnly], kept as an M4A by [audioExtractor]); every other plan goes to
 * [delegate].
 *
 * The AAC file is downloaded into a per-task workspace (resumable like any direct download),
 * converted there by [transcoder] and only then copied into the real destination and published,
 * so a failed conversion never leaves a broken MP3 in Downloads. A finished AAC file is kept
 * while a retry can still use it.
 */
class Mp3ConvertingTransferDispatcher(
    private val delegate: DownloadTransferDispatcher,
    private val transcoder: LocalMp3Transcoder,
    private val workspaceRoot: File,
    private val bufferBytes: Int = 64 * 1_024,
    private val audioExtractor: LocalAudioExtractor = AndroidAudioExtractor(),
) : DownloadTransferDispatcher {
    init {
        require(bufferBytes in 1_024..1024 * 1_024)
    }

    override suspend fun transfer(
        plan: DownloadPlan,
        metadata: RemoteFileMetadata?,
        destination: DownloadDestination,
        resumeFrom: TransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (TransferCheckpoint) -> Unit,
    ): QueueTransferResult {
        val direct = plan as? DirectDownloadPlan
        if (direct == null || !direct.converts) {
            return delegate.transfer(
                plan,
                metadata,
                destination,
                resumeFrom,
                onProgress,
                onCheckpoint,
            )
        }
        val encoding = direct.mp3
        return withContext(Dispatchers.IO) {
            convert(
                plan = direct,
                encoding = encoding,
                metadata = metadata,
                destination = destination,
                resumeFrom = resumeFrom as? DirectTransferCheckpoint,
                onProgress = onProgress,
                onCheckpoint = onCheckpoint,
            )
        }
    }

    override suspend fun discard(plan: DownloadPlan) {
        runCatching { delegate.discard(plan) }
        if ((plan as? DirectDownloadPlan)?.converts == true) {
            withContext(Dispatchers.IO) { workspaceFor(plan.taskId).deleteRecursively() }
        }
    }

    private suspend fun convert(
        plan: DirectDownloadPlan,
        encoding: Mp3Encoding?,
        metadata: RemoteFileMetadata?,
        destination: DownloadDestination,
        resumeFrom: DirectTransferCheckpoint?,
        onProgress: suspend (DownloadProgress) -> Unit,
        onCheckpoint: suspend (TransferCheckpoint) -> Unit,
    ): QueueTransferResult {
        val empty = DirectTransferCheckpoint(
            totalBytes = metadata?.totalBytes,
            entityTag = metadata?.entityTag,
            lastModified = metadata?.lastModified,
            segments = emptyList(),
        )
        val workspace = try {
            prepareWorkspace(plan.taskId)
        } catch (error: IOException) {
            return failure(error.storageReason(), resumeFrom ?: empty)
        }
        val source = File(workspace, SOURCE_NAME)
        val partial = File(workspace, SOURCE_PARTIAL_NAME)
        val downloaded = if (resumeFrom != null && resumeFrom.finished(source)) {
            resumeFrom
        } else {
            if (source.exists() && !source.delete()) {
                return failure(DownloadFailureReason.STORAGE_UNAVAILABLE, resumeFrom ?: empty)
            }
            // A checkpoint is only worth resuming while its partial file is still there.
            val resume = resumeFrom?.takeIf { partial.isFile }
            val staging = FileDownloadDestination(partialFile = partial, completedFile = source)
            when (
                val result = delegate.transfer(
                    plan,
                    metadata,
                    staging,
                    resume,
                    onProgress,
                    onCheckpoint,
                )
            ) {
                is QueueTransferResult.Completed -> result.checkpoint
                is QueueTransferResult.Failure -> return result
            }
        }
        if (!source.isFile || source.length() <= 0) {
            workspace.deleteRecursively()
            return failure(DownloadFailureReason.INTEGRITY_MISMATCH, empty)
        }

        val output = File(workspace, if (encoding != null) OUTPUT_NAME else AUDIO_OUTPUT_NAME)
        if (output.exists() && !output.delete()) {
            return failure(DownloadFailureReason.STORAGE_UNAVAILABLE, downloaded)
        }
        val encoded = try {
            if (encoding != null) {
                transcoder.transcode(source, output, encoding)
            } else {
                audioExtractor.extract(source, output)
            }
        } catch (cancellation: CancellationException) {
            output.delete()
            throw cancellation
        }
        if (encoded is Mp3TranscodeResult.Failure) {
            output.delete()
            return settle(encoded.reason, workspace, downloaded, empty)
        }
        val written = try {
            publish(output, destination)
        } catch (abort: PublishAbort) {
            output.delete()
            return settle(abort.reason, workspace, downloaded, empty)
        }
        workspace.deleteRecursively()
        return QueueTransferResult.Completed(written, downloaded)
    }

    /** Keeps the AAC file for a retry; a failure no retry can fix frees the space at once. */
    private fun settle(
        reason: DownloadFailureReason,
        workspace: File,
        downloaded: TransferCheckpoint,
        empty: DirectTransferCheckpoint,
    ): QueueTransferResult {
        if (DownloadFailure(reason).isRetryable || reason in KEEP_SOURCE_FAILURES) {
            return failure(reason, downloaded)
        }
        workspace.deleteRecursively()
        return failure(reason, empty)
    }

    private fun DirectTransferCheckpoint.finished(source: File): Boolean {
        val length = source.takeIf(File::isFile)?.length() ?: return false
        val total = totalBytes
        return length > 0 && downloadedBytes == length && (total == null || total == length)
    }

    private fun publish(source: File, destination: DownloadDestination): Long {
        val totalBytes = source.length().takeIf { it > 0 }
            ?: throw PublishAbort(DownloadFailureReason.INTEGRITY_MISMATCH)
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
                    throw PublishAbort(DownloadFailureReason.INTEGRITY_MISMATCH)
                }
            }
            if (destination.temporaryLength() != totalBytes) {
                throw PublishAbort(DownloadFailureReason.INTEGRITY_MISMATCH)
            }
            destination.commit()
            return totalBytes
        } catch (abort: PublishAbort) {
            throw abort
        } catch (error: IOException) {
            throw PublishAbort(error.storageReason())
        } catch (_: IllegalStateException) {
            throw PublishAbort(DownloadFailureReason.STORAGE_UNAVAILABLE)
        }
    }

    private fun prepareWorkspace(taskId: String): File {
        val root = workspaceRoot.canonicalFile
        if (!root.isDirectory && !root.mkdirs()) throw IOException("Cannot create mp3 workspace")
        val workspace = workspaceFor(taskId).canonicalFile
        if (workspace.parentFile != root) throw IOException("Unsafe mp3 workspace")
        if (!workspace.isDirectory && !workspace.mkdirs()) {
            throw IOException("Cannot create mp3 task workspace")
        }
        return workspace
    }

    private fun workspaceFor(taskId: String): File =
        File(workspaceRoot, DownloadWorkspaces.nameFor(DownloadWorkspaces.MP3_PREFIX, taskId))

    private fun failure(
        reason: DownloadFailureReason,
        checkpoint: TransferCheckpoint,
    ): QueueTransferResult = QueueTransferResult.Failure(DownloadFailure(reason), checkpoint)

    private class PublishAbort(val reason: DownloadFailureReason) : Exception()

    private companion object {
        const val SOURCE_NAME = "source.m4a"
        const val SOURCE_PARTIAL_NAME = "source.m4a.part"
        const val OUTPUT_NAME = "output.mp3"
        const val AUDIO_OUTPUT_NAME = "output.m4a"

        /** Space problems can clear up; the downloaded AAC file is still good. */
        val KEEP_SOURCE_FAILURES = setOf(
            DownloadFailureReason.STORAGE_UNAVAILABLE,
            DownloadFailureReason.INSUFFICIENT_STORAGE,
        )
    }
}

/** MP3 always reads the AAC track itself; only a plain M4A asks for the extraction. */
private val DirectDownloadPlan.converts: Boolean
    get() = mp3 != null || audioOnly

private fun IOException.storageReason(): DownloadFailureReason {
    val text = message.orEmpty().lowercase(Locale.US)
    return if ("enospc" in text || "no space left" in text || "disk full" in text) {
        DownloadFailureReason.INSUFFICIENT_STORAGE
    } else {
        DownloadFailureReason.STORAGE_UNAVAILABLE
    }
}
