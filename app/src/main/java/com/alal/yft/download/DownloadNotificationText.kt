package com.alal.yft.download

import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.feature.downloads.DownloadStage
import com.alal.yft.feature.downloads.failureLabel
import com.alal.yft.feature.downloads.mergeStageLabel
import com.alal.yft.feature.downloads.speedLabel
import com.alal.yft.feature.downloads.timeLeftLabel
import com.alal.yft.ui.format.YftFormat

/**
 * What the ongoing download notification says (P34, R22), worked out without Android so the
 * texts can be tested on the JVM:
 * - one download: its title, "45% · 1.2 MB/s · 61 MB of 96 MB · 15 s left" and its bar;
 * - several: "Downloading 3 videos · 45%", "2.4 MB/s · 1 min left" and one line each;
 * - a merge or save: P27's "Merging audio and video · 45%";
 * - waiting: "Waiting for network" or "Waiting for Wi-Fi".
 */
internal data class ActiveNotificationContent(
    val title: String,
    val text: String,
    /** One line per download when there are several (up to [MAX_LINES]); empty for one. */
    val lines: List<String>,
    /** 0–100, or null for an indeterminate bar. */
    val percent: Int?,
    /** How many downloads the notification is about, for a secure lock screen. */
    val count: Int,
)

internal fun activeNotificationContent(
    tasks: List<StoredDownloadTask>,
    bytesPerSecond: Map<String, Long>,
    network: TransferNetworkState,
): ActiveNotificationContent {
    val active = tasks.filter { it.status in DownloadNotificationFactory.FOREGROUND_STATUSES }
    val percent = overallPercent(active)
    val single = active.singleOrNull()
    if (single != null) {
        val speed = bytesPerSecond[single.id]
        return ActiveNotificationContent(
            title = shortTitle(single),
            text = singleText(single, speed, network),
            lines = emptyList(),
            percent = taskPercent(single),
            count = 1,
        )
    }
    val totalSpeed = active.sumOf { bytesPerSecond[it.id] ?: 0L }.takeIf { it > 0L }
    val remaining = active.takeIf { tasks -> tasks.all { it.totalBytes != null } }
        ?.sumOf { (it.totalBytes ?: 0L) - it.downloadedBytes }
    val summary = listOfNotNull(
        totalSpeed?.let(::speedLabel),
        totalSpeed?.let { speed -> remaining?.let { timeLeft(it, speed) } },
    ).joinToString(SEPARATOR)
    return ActiveNotificationContent(
        title = listOfNotNull("Downloading ${active.size} videos", percent?.let { "$it%" })
            .joinToString(SEPARATOR),
        text = summary.ifEmpty {
            active.firstOrNull()?.let { stateText(it, network) } ?: PREPARING
        },
        lines = active.take(MAX_LINES).map { task ->
            "${shortTitle(task)} \u2014 ${lineText(task, bytesPerSecond[task.id], network)}"
        },
        percent = percent,
        count = active.size,
    )
}

/** "Downloaded · <title>" or "Download failed · <title> — <short reason>"; no path or address. */
internal fun finishedNoticeText(task: StoredDownloadTask): String? = when (task.status) {
    DownloadTaskStatus.COMPLETED -> "Downloaded$SEPARATOR${shortTitle(task)}"
    DownloadTaskStatus.FAILED -> "Download failed$SEPARATOR${shortTitle(task)} \u2014 " +
        failureLabel(task.failureReason ?: DownloadFailureReason.NETWORK)
    DownloadTaskStatus.NEEDS_REFRESH ->
        "Download failed$SEPARATOR${shortTitle(task)} \u2014 link expired"
    else -> null
}

/** The video's title without its extension, cut to fit one line. */
internal fun shortTitle(task: StoredDownloadTask): String {
    val title = YftFormat.title(task.displayName).trim()
    return if (title.length <= MAX_TITLE) title else title.take(MAX_TITLE - 1).trimEnd() + "\u2026"
}

private fun singleText(
    task: StoredDownloadTask,
    speed: Long?,
    network: TransferNetworkState,
): String {
    if (task.status != DownloadTaskStatus.RUNNING || task.work() != TaskWork.DOWNLOADING) {
        return stateText(task, network)
    }
    val total = task.totalBytes
    if (total == null || total == 0L) {
        val amount = task.downloadedBytes.takeIf { it > 0L }?.let(YftFormat::bytes)
        return listOfNotNull(amount, speed?.let(::speedLabel))
            .joinToString(SEPARATOR)
            .ifEmpty { "Downloading" }
    }
    return listOfNotNull(
        "${task.progressPercent}%",
        speed?.let(::speedLabel),
        "${YftFormat.bytes(task.downloadedBytes)} of ${YftFormat.bytes(total)}",
        speed?.let { timeLeft(total - task.downloadedBytes, it) },
    ).joinToString(SEPARATOR)
}

/** One InboxStyle line's text after the title: "45% · 1.2 MB/s", "Merging … · 45%". */
private fun lineText(
    task: StoredDownloadTask,
    speed: Long?,
    network: TransferNetworkState,
): String {
    if (task.status != DownloadTaskStatus.RUNNING || task.work() != TaskWork.DOWNLOADING) {
        return stateText(task, network)
    }
    val total = task.totalBytes
    val amount = if (total == null || total == 0L) {
        task.downloadedBytes.takeIf { it > 0L }?.let(YftFormat::bytes)
    } else {
        "${task.progressPercent}%"
    }
    return listOfNotNull(amount, speed?.let(::speedLabel))
        .joinToString(SEPARATOR)
        .ifEmpty { "Downloading" }
}

/** The text of a task that is not moving bytes: a merge, a save, a wait or a queue place. */
private fun stateText(task: StoredDownloadTask, network: TransferNetworkState): String =
    when (task.status) {
        DownloadTaskStatus.QUEUED -> "Queued"
        DownloadTaskStatus.PAUSING -> "Pausing"
        DownloadTaskStatus.WAITING_FOR_NETWORK -> when (network) {
            TransferNetworkState.WAITING_FOR_UNMETERED -> "Waiting for Wi-Fi"
            TransferNetworkState.OFFLINE, TransferNetworkState.ALLOWED -> "Waiting for network"
        }
        DownloadTaskStatus.RUNNING -> {
            val stage = DownloadStage.of(task)
            when {
                stage != null -> mergeStageLabel(stage, task.destinationKind)
                task.work() == TaskWork.CONVERTING -> "Converting to MP3"
                task.work() == TaskWork.SAVING -> mergeStageLabel(
                    DownloadStage(DownloadStage.Step.SAVING, percent = null),
                    task.destinationKind,
                )
                else -> "Downloading"
            }
        }
        else -> PREPARING
    }

/** The bar of one task: its merge or save step's percent, or its bytes' percent. */
private fun taskPercent(task: StoredDownloadTask): Int? {
    DownloadStage.of(task)?.let { return it.percent }
    if (task.work()?.isProcessing == true) return null
    val total = task.totalBytes?.takeIf { it > 0L } ?: return null
    return ((task.downloadedBytes * PERCENT) / total).toInt().coerceIn(0, MAX_PERCENT)
}

/** All bytes of all active tasks, when every size is known. */
private fun overallPercent(active: List<StoredDownloadTask>): Int? {
    if (active.size == 1) return taskPercent(active.single())
    val total = active.takeIf { tasks -> tasks.all { it.totalBytes != null } }
        ?.sumOf { it.totalBytes ?: 0L }
        ?.takeIf { it > 0L }
        ?: return null
    val done = active.sumOf { it.downloadedBytes }
    return ((done * PERCENT) / total).toInt().coerceIn(0, MAX_PERCENT)
}

private fun timeLeft(remainingBytes: Long, bytesPerSecond: Long): String? {
    if (bytesPerSecond <= 0L) return null
    val seconds = (remainingBytes.coerceAtLeast(0L) + bytesPerSecond - 1L) / bytesPerSecond
    return timeLeftLabel(seconds)
}

internal const val MAX_LINES = 5
private const val MAX_TITLE = 40
private const val PERCENT = 100.0
private const val MAX_PERCENT = 99
internal const val SEPARATOR = " \u00b7 "
private const val PREPARING = "Preparing download"
