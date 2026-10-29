package com.alal.yft.feature.downloads

import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.download.DownloadRenewals
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.feature.library.LibraryMimeTypes
import com.alal.yft.ui.format.YftFormat

/** Queue control a user can invoke for a single stored task. */
enum class DownloadAction {
    PAUSE,
    RESUME,
    RETRY,
    CANCEL,
    DELETE,
}

/**
 * Statuses that still occupy the queue. `PAUSED` is excluded because a paused task holds no slot;
 * a task getting a fresh link keeps its slot (Track E).
 */
internal val OCCUPYING_STATUSES = setOf(
    DownloadTaskStatus.QUEUED,
    DownloadTaskStatus.PROBING,
    DownloadTaskStatus.RUNNING,
    DownloadTaskStatus.PAUSING,
    DownloadTaskStatus.VERIFYING,
    DownloadTaskStatus.WAITING_FOR_NETWORK,
    DownloadTaskStatus.RENEWING_LINK,
)

/** Statuses the queue will not advance without a new user action. */
internal val TERMINAL_STATUSES = setOf(
    DownloadTaskStatus.COMPLETED,
    DownloadTaskStatus.FAILED,
    DownloadTaskStatus.CANCELLED,
    DownloadTaskStatus.NEEDS_REFRESH,
)

/**
 * Statuses counted as active on the Downloads tab badge and filter: work that is moving now.
 * Queued and network-held tasks are counted separately as queued.
 */
internal val ACTIVE_STATUSES = setOf(
    DownloadTaskStatus.PROBING,
    DownloadTaskStatus.RUNNING,
    DownloadTaskStatus.PAUSING,
    DownloadTaskStatus.VERIFYING,
    DownloadTaskStatus.RENEWING_LINK,
)

/** Number of [ACTIVE_STATUSES] tasks, shown as the Coral badge on the Downloads tab. */
internal fun List<StoredDownloadTask>.activeDownloadCount(): Int =
    count { it.status in ACTIVE_STATUSES }

/** Statuses that can still be paused. `PAUSING` is already stopping. */
internal val PAUSABLE_STATUSES = setOf(
    DownloadTaskStatus.QUEUED,
    DownloadTaskStatus.PROBING,
    DownloadTaskStatus.RUNNING,
    DownloadTaskStatus.VERIFYING,
    DownloadTaskStatus.WAITING_FOR_NETWORK,
    DownloadTaskStatus.RENEWING_LINK,
)

/**
 * The Downloads filter chips. Every task is under [ALL] and under at most one other filter;
 * cancelled tasks are only under [ALL].
 */
enum class DownloadsFilter(val label: String) {
    ALL("All"),

    /** Moving now, or paused part-way. */
    ACTIVE("Active"),

    /** Waiting for a free slot or for an allowed network. */
    QUEUED("Queued"),

    DONE("Done"),

    /** Stopped by an error or an expired link. */
    FAILED("Failed"),
    ;

    fun matches(status: DownloadTaskStatus): Boolean = when (this) {
        ALL -> true
        ACTIVE -> status in ACTIVE_STATUSES || status == DownloadTaskStatus.PAUSED
        QUEUED -> status == DownloadTaskStatus.QUEUED ||
            status == DownloadTaskStatus.WAITING_FOR_NETWORK
        DONE -> status == DownloadTaskStatus.COMPLETED
        FAILED -> status == DownloadTaskStatus.FAILED ||
            status == DownloadTaskStatus.NEEDS_REFRESH
    }
}

/**
 * The step after a merged download's tracks are in (P27), with its percent when known: the
 * card and the notification say "Merging audio and video · 45%", then "Saving to … · 80%".
 */
data class DownloadStage(
    val step: Step,
    /** 0–100, or null while the step's size is unknown (the bar is then indeterminate). */
    val percent: Int?,
) {
    enum class Step {
        MERGING,
        SAVING,
    }

    companion object {
        /** The stage of a running merged download, or null while it downloads or is another. */
        fun of(task: StoredDownloadTask): DownloadStage? {
            if (task.status != DownloadTaskStatus.RUNNING) return null
            val checkpoint = task.checkpoint as? AudioVideoMuxCheckpoint ?: return null
            val step = when (checkpoint.stage) {
                AudioVideoMuxStage.READY_TO_MUX, AudioVideoMuxStage.MUXING -> Step.MERGING
                AudioVideoMuxStage.SAVING -> Step.SAVING
                AudioVideoMuxStage.DOWNLOADING_TRACKS, AudioVideoMuxStage.COMPLETED -> return null
            }
            return DownloadStage(step, checkpoint.stepPercent)
        }
    }
}

/** Where new downloads are saved and the free space there, for the footer pill. */
data class DownloadStorageSummary(
    val location: DownloadLocation,
    /** Null when the volume cannot be measured. */
    val freeBytes: Long?,
)

/**
 * Presentation state for one persisted download.
 *
 * Only non-sensitive fields are exposed; source URLs, cookies and request headers stay inside the
 * download queue runtime and never reach the UI layer.
 */
data class DownloadRowUiState(
    val id: String,
    val displayName: String,
    val status: DownloadTaskStatus,
    val planType: DownloadPlanType,
    val destinationKind: DownloadDestinationKind,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val progressPercent: Int,
    val requiresLinkRefresh: Boolean,
    val failureReason: DownloadFailureReason?,
    val mimeType: String? = null,
    /** MediaStore or document URI of the result; null in app storage, which is opened by name. */
    val destinationUri: String? = null,
    val updatedAtEpochMs: Long = 0L,
    /** Smoothed speed while running, measured in memory by this process only. */
    val bytesPerSecond: Long? = null,
    /**
     * The failure behind [failureReason] with its stage, HTTP status and detail when they are
     * known (P21), for the Details dialog; null when the task has not failed.
     */
    val failure: DownloadFailure? = failureReason?.let(::DownloadFailure),
    /** Merging or saving after a merged download's tracks are in (P27); null otherwise. */
    val stage: DownloadStage? = null,
    /**
     * Track E (E-D6): the app has this download's source in this session, so Retry on its
     * "Link expired" looks the page up again for a fresh link.
     */
    val canRenew: Boolean = false,
    /** Track E: this download's fresh links ("Fresh link (403 at 41.0 MB): resumed at …"). */
    val renewalLines: List<String> = emptyList(),
    /**
     * v1.0.2: the page, read again for this download's last fresh link, had no player data (a
     * notice, a check); Retry would read it the same way, so the hint says to reopen it.
     */
    val noPlayerOnPage: Boolean = false,
    /**
     * v1.0.2 (item 50): the quality the download was picked as ("1080p"), while the app holds it
     * (this session, until it finishes); the meta line names it.
     */
    val pickedQuality: String? = null,
) {
    /** The file name without its extension, as the cards show it. */
    val title: String = YftFormat.title(displayName)

    /** "MP4", "M4A": the container, from the file name or the MIME type. */
    val format: String? = YftFormat.format(displayName, mimeType)

    val isAudio: Boolean =
        (mimeType ?: LibraryMimeTypes.forFileName(displayName))?.startsWith("audio/") == true

    /**
     * Determinate fraction, or null when the remote size is unknown so the UI must not invent a
     * determinate bar. While merging or saving it is that step's fraction (P27).
     */
    val progressFraction: Float? = if (stage != null) {
        stage.percent?.let { percent -> (percent / 100f).coerceIn(0f, 1f) }
    } else {
        totalBytes
            ?.takeIf { it > 0L }
            ?.let { (downloadedBytes.toDouble() / it.toDouble()).toFloat().coerceIn(0f, 1f) }
    }

    val isOccupyingQueue: Boolean = status in OCCUPYING_STATUSES

    val isTerminal: Boolean = status in TERMINAL_STATUSES

    /** Seconds left at the current speed; null unless running with a known size and speed. */
    val secondsLeft: Long? = run {
        val total = totalBytes
        val speed = bytesPerSecond?.takeIf { it > 0L }
        if (status != DownloadTaskStatus.RUNNING || total == null || speed == null ||
            stage != null
        ) {
            null
        } else {
            ((total - downloadedBytes).coerceAtLeast(0L) + speed - 1L) / speed
        }
    }

    val availableActions: Set<DownloadAction> = buildSet {
        if (status in PAUSABLE_STATUSES) add(DownloadAction.PAUSE)
        if (status == DownloadTaskStatus.PAUSED) add(DownloadAction.RESUME)
        if (status == DownloadTaskStatus.FAILED ||
            (status == DownloadTaskStatus.NEEDS_REFRESH && canRenew)
        ) {
            add(DownloadAction.RETRY)
        }
        if (status !in TERMINAL_STATUSES) add(DownloadAction.CANCEL)
        if (status in TERMINAL_STATUSES) add(DownloadAction.DELETE)
    }
}

/** Observable state of the whole queue, ordered so unfinished work stays on top. */
data class DownloadsUiState(
    val rows: List<DownloadRowUiState> = emptyList(),
    /** Why queued work is not moving, when the network policy holds it back. */
    val network: TransferNetworkState = TransferNetworkState.ALLOWED,
    /** Save location and free space, or null until measured. */
    val storage: DownloadStorageSummary? = null,
    /** The notifications and battery cards (P34). */
    val background: BackgroundCardsUiState = BackgroundCardsUiState.None,
) {
    val isEmpty: Boolean = rows.isEmpty()

    val occupyingCount: Int = rows.count { it.isOccupyingQueue }

    val canPauseAll: Boolean = rows.any { DownloadAction.PAUSE in it.availableActions }

    fun rowsFor(filter: DownloadsFilter): List<DownloadRowUiState> =
        rows.filter { filter.matches(it.status) }

    fun count(filter: DownloadsFilter): Int = rows.count { filter.matches(it.status) }

    companion object {
        val Empty = DownloadsUiState()

        /**
         * The rows' order (v1.0.2): unfinished work first, the newest added first by
         * [StoredDownloadTask.createdAtEpochMs], which only enqueueing sets, so progress writes and
         * status changes never move a row; finished, failed, cancelled and expired rows below, the
         * most recently changed first. A retried row returns to its place by when it was added;
         * ties fall to the id, so they keep one order.
         */
        internal val ROW_ORDER: Comparator<StoredDownloadTask> =
            compareBy<StoredDownloadTask> { it.status in TERMINAL_STATUSES }
                .thenByDescending { task ->
                    if (task.status in TERMINAL_STATUSES) {
                        task.updatedAtEpochMs
                    } else {
                        task.createdAtEpochMs
                    }
                }
                .thenBy { it.id }

        /**
         * Rows for [tasks] in [ROW_ORDER]; [bytesPerSecond] holds the measured speed of running
         * tasks by id, [renewals] the downloads a Retry renews and their fresh links' lines
         * (Track E).
         */
        fun from(
            tasks: List<StoredDownloadTask>,
            bytesPerSecond: Map<String, Long> = emptyMap(),
            renewals: DownloadRenewals = DownloadRenewals.None,
        ): DownloadsUiState = DownloadsUiState(
            rows = tasks
                .sortedWith(ROW_ORDER)
                .map { task ->
                    val stage = DownloadStage.of(task)
                    DownloadRowUiState(
                        id = task.id,
                        displayName = task.displayName,
                        status = task.status,
                        planType = task.planType,
                        destinationKind = task.destinationKind,
                        downloadedBytes = task.downloadedBytes,
                        totalBytes = task.totalBytes,
                        progressPercent = stage?.percent ?: task.progressPercent,
                        requiresLinkRefresh = task.requiresLinkRefresh,
                        failureReason = task.failureReason,
                        mimeType = task.mimeType,
                        destinationUri = task.destinationUri,
                        updatedAtEpochMs = task.updatedAtEpochMs,
                        bytesPerSecond = bytesPerSecond[task.id],
                        failure = task.failure
                            ?.takeIf { it.reason == task.failureReason }
                            ?: task.failureReason?.let(::DownloadFailure),
                        stage = stage,
                        canRenew = task.id in renewals.renewable,
                        renewalLines = renewals.notes[task.id].orEmpty(),
                        noPlayerOnPage = task.id in renewals.noPlayer,
                        pickedQuality = renewals.qualities[task.id],
                    )
                },
        )
    }
}
