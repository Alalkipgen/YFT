package com.alal.yft.download

import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.Mp3Encoding

/**
 * What a running task does right now (P34). The queue keeps a task `RUNNING` through its
 * download, its merge or MP3 conversion and its save, so the step is read from its checkpoint
 * and its bytes: a merged download says so in its [AudioVideoMuxCheckpoint], any other task
 * whose bytes are all in is converting (MP3) or saving.
 */
internal enum class TaskWork {
    DOWNLOADING,
    MERGING,
    CONVERTING,
    SAVING,
    ;

    /** Bytes come over the network: the Wi-Fi lock is worth holding. */
    val usesNetwork: Boolean get() = this == DOWNLOADING

    /** Work on the phone only (Android 15's media-processing service type). */
    val isProcessing: Boolean get() = this != DOWNLOADING
}

/** The step of a task that runs, or null when it waits, is paused or has ended. */
internal fun StoredDownloadTask.work(): TaskWork? {
    if (status !in RUNNING_STATUSES) return null
    (checkpoint as? AudioVideoMuxCheckpoint)?.let { mux ->
        return when (mux.stage) {
            AudioVideoMuxStage.DOWNLOADING_TRACKS -> TaskWork.DOWNLOADING
            AudioVideoMuxStage.READY_TO_MUX, AudioVideoMuxStage.MUXING -> TaskWork.MERGING
            AudioVideoMuxStage.SAVING, AudioVideoMuxStage.COMPLETED -> TaskWork.SAVING
        }
    }
    val total = totalBytes
    return when {
        total == null || total == 0L || downloadedBytes < total -> TaskWork.DOWNLOADING
        mimeType == Mp3Encoding.MIME_TYPE -> TaskWork.CONVERTING
        else -> TaskWork.SAVING
    }
}

/** What the whole queue does: whether anything runs, downloads or works on the phone. */
internal data class BackgroundWork(
    val running: Boolean,
    val downloading: Boolean,
    val processing: Boolean,
    /** The step of the first running task, for the freeze log line. */
    val stage: TaskWork?,
) {
    companion object {
        val Idle = BackgroundWork(
            running = false,
            downloading = false,
            processing = false,
            stage = null,
        )

        fun of(tasks: List<StoredDownloadTask>): BackgroundWork {
            val steps = tasks.mapNotNull(StoredDownloadTask::work)
            if (steps.isEmpty()) return Idle
            return BackgroundWork(
                running = true,
                downloading = steps.any(TaskWork::usesNetwork),
                processing = steps.any(TaskWork::isProcessing),
                stage = steps.first(),
            )
        }
    }
}

/** A pausing task is still inside its engine, so it keeps the locks until it has stopped. */
private val RUNNING_STATUSES = setOf(DownloadTaskStatus.RUNNING, DownloadTaskStatus.PAUSING)
