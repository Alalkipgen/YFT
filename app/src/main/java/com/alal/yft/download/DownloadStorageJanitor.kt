package com.alal.yft.download

import com.alal.yft.core.download.DownloadQueue
import com.alal.yft.core.download.DownloadWorkspaces
import com.alal.yft.core.download.StoredDownloadTask
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.thumbnail.DownloadThumbnails
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A streaming engine's workspace root and the prefix of its per-task directories. */
data class WorkspaceRoot(val directory: File, val prefix: String)

/** What one janitor pass removed. */
data class JanitorReport(
    val partialFiles: Int = 0,
    val workspaces: Int = 0,
    val records: Int = 0,
)

/**
 * Removes download storage nothing can use any more (hardening finding P2).
 *
 * Transfer runtime state lives only in memory, so after process death a staged `.part` file in
 * app storage can never be resumed, and a streaming workspace whose task was deleted or finished
 * is dead weight. Only entries last modified before this process started are touched, so work
 * that begins while the janitor runs is never affected; workspaces of tasks still listed as
 * unfinished are kept. The oldest finished records beyond [keepFinishedRecords] are removed from
 * the list; their published files stay where they are.
 */
class DownloadStorageJanitor(
    private val queue: DownloadQueue,
    private val appDownloadsRoot: File,
    private val workspaceRoots: List<WorkspaceRoot>,
    private val processStartEpochMs: () -> Long,
    private val scope: CoroutineScope,
    private val keepFinishedRecords: Int = DEFAULT_KEEP_FINISHED_RECORDS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val thumbnails: DownloadThumbnails = DownloadThumbnails.None,
) {
    private val started = AtomicBoolean(false)

    /** Starts one pass for this process; later calls do nothing. Failures are not fatal. */
    fun launchOnce() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { runCatching { clean() } }
    }

    suspend fun clean(): JanitorReport {
        queue.restore()
        val cutoff = processStartEpochMs()
        val records = pruneFinishedRecords(queue.tasks.value)
        val unfinished = queue.tasks.value
            .filter { it.status !in WORKSPACE_FREE_STATUSES }
            .map(StoredDownloadTask::id)
        val listed = queue.tasks.value.mapTo(mutableSetOf(), StoredDownloadTask::id)
        return withContext(ioDispatcher) {
            // P19: pictures of downloads whose record went while nothing was watching.
            thumbnails.keepOnly(listed, olderThanEpochMs = cutoff)
            JanitorReport(
                partialFiles = deleteStalePartialFiles(cutoff),
                workspaces = workspaceRoots.sumOf { root ->
                    deleteOrphanWorkspaces(root, unfinished, cutoff)
                },
                records = records,
            )
        }
    }

    private suspend fun pruneFinishedRecords(tasks: List<StoredDownloadTask>): Int {
        val excess = tasks
            .filter { it.status in FINISHED_STATUSES }
            .sortedWith(
                compareByDescending<StoredDownloadTask> { it.updatedAtEpochMs }.thenBy { it.id },
            )
            .drop(keepFinishedRecords)
        excess.forEach { queue.deleteRecord(it.id) }
        return excess.size
    }

    private fun deleteStalePartialFiles(cutoff: Long): Int {
        val root = appDownloadsRoot.takeIf(File::isDirectory)?.canonicalFile ?: return 0
        return root.listFiles().orEmpty().count { file ->
            file.isFile &&
                file.name.endsWith(PARTIAL_SUFFIX) &&
                file.lastModified() < cutoff &&
                file.canonicalFile.parentFile == root &&
                file.delete()
        }
    }

    private fun deleteOrphanWorkspaces(
        workspaceRoot: WorkspaceRoot,
        unfinishedTaskIds: List<String>,
        cutoff: Long,
    ): Int {
        val root = workspaceRoot.directory.takeIf(File::isDirectory)?.canonicalFile ?: return 0
        val kept = unfinishedTaskIds
            .map { DownloadWorkspaces.nameFor(workspaceRoot.prefix, it) }
            .toSet()
        return root.listFiles().orEmpty().count { directory ->
            directory.isDirectory &&
                DownloadWorkspaces.isWorkspaceName(workspaceRoot.prefix, directory.name) &&
                directory.name !in kept &&
                directory.lastModified() < cutoff &&
                directory.canonicalFile.parentFile == root &&
                directory.deleteRecursively()
        }
    }

    companion object {
        const val DEFAULT_KEEP_FINISHED_RECORDS = 200
        private const val PARTIAL_SUFFIX = ".part"

        /** Records the user can only delete; pruning them never loses resumable work. */
        val FINISHED_STATUSES: Set<DownloadTaskStatus> = setOf(
            DownloadTaskStatus.COMPLETED,
            DownloadTaskStatus.FAILED,
            DownloadTaskStatus.CANCELLED,
        )

        /** Engines remove the workspace when a task completes or is cancelled. */
        private val WORKSPACE_FREE_STATUSES: Set<DownloadTaskStatus> = setOf(
            DownloadTaskStatus.COMPLETED,
            DownloadTaskStatus.CANCELLED,
        )
    }
}
