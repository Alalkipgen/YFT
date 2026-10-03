package com.alal.yft.feature.downloads

import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.download.policy.TransferNetworkState

/** Queue control a user can invoke for a single stored task. */
enum class DownloadAction {
    PAUSE,
    RESUME,
    RETRY,
    CANCEL,
    DELETE,
}

/**
 * Statuses that still occupy the queue. `PAUSED` is excluded because a paused task holds no slot.
 */
internal val OCCUPYING_STATUSES = setOf(
    DownloadTaskStatus.QUEUED,
    DownloadTaskStatus.PROBING,
    DownloadTaskStatus.RUNNING,
    DownloadTaskStatus.PAUSING,
    DownloadTaskStatus.VERIFYING,
    DownloadTaskStatus.WAITING_FOR_NETWORK,
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
) {
    /**
     * Determinate fraction, or null when the remote size is unknown so the UI must not invent a
     * determinate bar.
     */
    val progressFraction: Float? = totalBytes
        ?.takeIf { it > 0L }
        ?.let { (downloadedBytes.toDouble() / it.toDouble()).toFloat().coerceIn(0f, 1f) }

    val isOccupyingQueue: Boolean = status in OCCUPYING_STATUSES

    val isTerminal: Boolean = status in TERMINAL_STATUSES

    val availableActions: Set<DownloadAction> = buildSet {
        if (status in PAUSABLE_STATUSES) add(DownloadAction.PAUSE)
        if (status == DownloadTaskStatus.PAUSED) add(DownloadAction.RESUME)
        if (status == DownloadTaskStatus.FAILED) add(DownloadAction.RETRY)
        if (status !in TERMINAL_STATUSES) add(DownloadAction.CANCEL)
        if (status in TERMINAL_STATUSES) add(DownloadAction.DELETE)
    }
}

/** Observable state of the whole queue, ordered so unfinished work stays on top. */
data class DownloadsUiState(
    val rows: List<DownloadRowUiState> = emptyList(),
    /** Why queued work is not moving, when the network policy holds it back. */
    val network: TransferNetworkState = TransferNetworkState.ALLOWED,
) {
    val isEmpty: Boolean = rows.isEmpty()

    val occupyingCount: Int = rows.count { it.isOccupyingQueue }

    val canPauseAll: Boolean = rows.any { DownloadAction.PAUSE in it.availableActions }

    companion object {
        val Empty = DownloadsUiState()

        fun from(tasks: List<StoredDownloadTask>): DownloadsUiState = DownloadsUiState(
            rows = tasks
                .sortedWith(
                    compareBy<StoredDownloadTask> { it.status in TERMINAL_STATUSES }
                        .thenByDescending { it.updatedAtEpochMs }
                        .thenBy { it.id },
                )
                .map { task ->
                    DownloadRowUiState(
                        id = task.id,
                        displayName = task.displayName,
                        status = task.status,
                        planType = task.planType,
                        destinationKind = task.destinationKind,
                        downloadedBytes = task.downloadedBytes,
                        totalBytes = task.totalBytes,
                        progressPercent = task.progressPercent,
                        requiresLinkRefresh = task.requiresLinkRefresh,
                        failureReason = task.failureReason,
                    )
                },
        )
    }
}
