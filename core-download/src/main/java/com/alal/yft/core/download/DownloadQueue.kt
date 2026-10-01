package com.alal.yft.core.download

import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DirectTransferResult
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.RemoteFileMetadata
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class DownloadDestinationSpec(
    val kind: DownloadDestinationKind,
    val uri: String? = null,
)

/**
 * Owns a bounded in-process transfer queue while persisting only non-sensitive recovery state.
 *
 * URLs, cookies and request headers remain in [runtimeTasks]. After process death every incomplete
 * task therefore becomes `NEEDS_REFRESH` while its verified segment checkpoint remains available.
 */
class DownloadQueue(
    private val store: DownloadTaskStore,
    private val transferRunner: DirectTransferRunner,
    private val scope: CoroutineScope,
    maxConcurrentDownloads: Int = 2,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val gate = Mutex()
    private var capacity = maxConcurrentDownloads.also { require(it in 1..10) }
    private var restored = false
    private var networkAvailable = true
    private val pending = ArrayDeque<String>()
    private val jobs = mutableMapOf<String, Job>()
    private val runtimeTasks = mutableMapOf<String, RuntimeTask>()
    private val mutableTasks = MutableStateFlow<List<StoredDownloadTask>>(emptyList())

    val tasks: StateFlow<List<StoredDownloadTask>> = mutableTasks.asStateFlow()

    suspend fun restore() {
        gate.withLock { restoreLocked() }
    }

    suspend fun enqueue(
        plan: DirectDownloadPlan,
        metadata: RemoteFileMetadata,
        destination: DownloadDestination,
        destinationSpec: DownloadDestinationSpec = DownloadDestinationSpec(
            DownloadDestinationKind.APP_PRIVATE,
        ),
    ): String = gate.withLock {
        restoreLocked()
        require(mutableTasks.value.none { it.id == plan.taskId }) {
            "A task with this ID already exists"
        }
        val now = clock()
        val checkpoint = DirectTransferCheckpoint(
            totalBytes = metadata.totalBytes,
            entityTag = metadata.entityTag,
            lastModified = metadata.lastModified,
            segments = emptyList(),
        )
        val task = StoredDownloadTask(
            id = plan.taskId,
            displayName = plan.suggestedFileName,
            status = if (networkAvailable) {
                DownloadTaskStatus.QUEUED
            } else {
                DownloadTaskStatus.WAITING_FOR_NETWORK
            },
            totalBytes = metadata.totalBytes,
            downloadedBytes = 0,
            mimeType = metadata.contentType ?: plan.mimeType,
            destinationKind = destinationSpec.kind,
            destinationUri = destinationSpec.uri,
            preferredSegmentCount = plan.preferredSegmentCount,
            requiresLinkRefresh = false,
            failureReason = null,
            checkpoint = checkpoint,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        runtimeTasks[task.id] = RuntimeTask(plan, metadata, destination)
        saveAndPublishLocked(task)
        if (networkAvailable) {
            pending.addLast(task.id)
            drainLocked()
        }
        task.id
    }

    suspend fun refresh(
        id: String,
        plan: DirectDownloadPlan,
        metadata: RemoteFileMetadata,
        destination: DownloadDestination,
    ) = gate.withLock {
        restoreLocked()
        require(plan.taskId == id)
        val current = taskLocked(id) ?: error("Download task does not exist")
        check(current.status != DownloadTaskStatus.COMPLETED)
        runtimeTasks[id] = RuntimeTask(plan, metadata, destination)
        pending.remove(id)
        val refreshed = current.copy(
            displayName = plan.suggestedFileName,
            status = if (networkAvailable) {
                DownloadTaskStatus.QUEUED
            } else {
                DownloadTaskStatus.WAITING_FOR_NETWORK
            },
            mimeType = metadata.contentType ?: plan.mimeType,
            preferredSegmentCount = plan.preferredSegmentCount,
            requiresLinkRefresh = false,
            failureReason = null,
            updatedAtEpochMs = clock(),
        )
        saveAndPublishLocked(refreshed)
        if (networkAvailable) {
            pending.addLast(id)
            drainLocked()
        }
    }

    suspend fun resume(id: String) = gate.withLock {
        restoreLocked()
        val current = taskLocked(id) ?: return@withLock
        if (
            current.status == DownloadTaskStatus.COMPLETED ||
            current.status == DownloadTaskStatus.RUNNING ||
            current.status == DownloadTaskStatus.QUEUED
        ) {
            return@withLock
        }
        val runtime = runtimeTasks[id]
        if (runtime == null) {
            saveAndPublishLocked(
                current.copy(
                    status = DownloadTaskStatus.NEEDS_REFRESH,
                    requiresLinkRefresh = true,
                    updatedAtEpochMs = clock(),
                ),
            )
            return@withLock
        }
        val resumed = current.copy(
            status = if (networkAvailable) {
                DownloadTaskStatus.QUEUED
            } else {
                DownloadTaskStatus.WAITING_FOR_NETWORK
            },
            failureReason = null,
            updatedAtEpochMs = clock(),
        )
        saveAndPublishLocked(resumed)
        pending.remove(id)
        if (networkAvailable) {
            pending.addLast(id)
            drainLocked()
        }
    }

    suspend fun pause(id: String) {
        val stop = prepareStop(id)
        if (!stop.shouldFinalize) return
        stop.job?.cancelAndJoin()
        gate.withLock {
            val current = taskLocked(id) ?: return@withLock
            if (current.status != DownloadTaskStatus.COMPLETED) {
                saveAndPublishLocked(
                    current.copy(
                        status = DownloadTaskStatus.PAUSED,
                        updatedAtEpochMs = clock(),
                    ),
                )
            }
            drainLocked()
        }
    }

    suspend fun cancel(id: String) {
        val stop = prepareStop(id)
        if (!stop.shouldFinalize) return
        stop.job?.cancelAndJoin()
        val runtime = gate.withLock { runtimeTasks[id] }
        val discardFailure = withContext(Dispatchers.IO) {
            runCatching { runtime?.destination?.discard() }.exceptionOrNull()
        }
        gate.withLock {
            val current = taskLocked(id) ?: return@withLock
            val cancelled = if (discardFailure == null) {
                current.copy(
                    status = DownloadTaskStatus.CANCELLED,
                    failureReason = null,
                    updatedAtEpochMs = clock(),
                )
            } else {
                current.copy(
                    status = DownloadTaskStatus.FAILED,
                    failureReason = discardFailure.toStorageFailureReason(),
                    updatedAtEpochMs = clock(),
                )
            }
            runtimeTasks.remove(id)
            saveAndPublishLocked(cancelled)
            drainLocked()
        }
    }

    suspend fun deleteRecord(id: String) {
        cancel(id)
        gate.withLock {
            pending.remove(id)
            runtimeTasks.remove(id)
            store.delete(id)
            mutableTasks.value = mutableTasks.value.filterNot { it.id == id }
        }
    }

    suspend fun setNetworkAvailable(available: Boolean) {
        val toCancel = gate.withLock {
            restoreLocked()
            if (networkAvailable == available) return@withLock emptyList()
            networkAvailable = available
            if (!available) {
                pending.clear()
                val affected = mutableTasks.value.filter {
                    it.status == DownloadTaskStatus.RUNNING ||
                        it.status == DownloadTaskStatus.QUEUED
                }
                affected.forEach { task ->
                    saveAndPublishLocked(
                        task.copy(
                            status = DownloadTaskStatus.WAITING_FOR_NETWORK,
                            updatedAtEpochMs = clock(),
                        ),
                    )
                }
                jobs.values.toList()
            } else {
                mutableTasks.value
                    .filter { it.status == DownloadTaskStatus.WAITING_FOR_NETWORK }
                    .forEach { task ->
                        if (runtimeTasks.containsKey(task.id)) {
                            val queued = task.copy(
                                status = DownloadTaskStatus.QUEUED,
                                updatedAtEpochMs = clock(),
                            )
                            saveAndPublishLocked(queued)
                            pending.addLast(task.id)
                        } else {
                            saveAndPublishLocked(
                                task.copy(
                                    status = DownloadTaskStatus.NEEDS_REFRESH,
                                    requiresLinkRefresh = true,
                                    updatedAtEpochMs = clock(),
                                ),
                            )
                        }
                    }
                drainLocked()
                emptyList()
            }
        }
        toCancel.forEach { it.cancel() }
        toCancel.forEach { it.join() }
    }

    suspend fun setMaxConcurrentDownloads(maxConcurrentDownloads: Int) {
        require(maxConcurrentDownloads in 1..10)
        gate.withLock {
            capacity = maxConcurrentDownloads
            drainLocked()
        }
    }

    suspend fun pauseAll() {
        val ids = gate.withLock {
            restoreLocked()
            mutableTasks.value
                .filter {
                    it.status == DownloadTaskStatus.RUNNING ||
                        it.status == DownloadTaskStatus.QUEUED
                }
                .map(StoredDownloadTask::id)
        }
        ids.forEach { pause(it) }
    }

    private suspend fun prepareStop(id: String): StopPreparation = gate.withLock {
        restoreLocked()
        pending.remove(id)
        val current = taskLocked(id)
            ?: return@withLock StopPreparation(shouldFinalize = false, job = null)
        if (current.status == DownloadTaskStatus.COMPLETED) {
            return@withLock StopPreparation(shouldFinalize = false, job = null)
        }
        saveAndPublishLocked(
            current.copy(
                status = DownloadTaskStatus.PAUSING,
                updatedAtEpochMs = clock(),
            ),
        )
        StopPreparation(shouldFinalize = true, job = jobs[id])
    }

    private suspend fun restoreLocked() {
        if (restored) return
        val loaded = store.loadAll()
        val restoredTasks = loaded.map { task ->
            if (task.status in TERMINAL_STATUSES) {
                task
            } else {
                task.copy(
                    status = DownloadTaskStatus.NEEDS_REFRESH,
                    requiresLinkRefresh = true,
                    updatedAtEpochMs = clock(),
                )
            }
        }
        restoredTasks.zip(loaded).forEach { (restoredTask, original) ->
            if (restoredTask != original) store.save(restoredTask)
        }
        mutableTasks.value = restoredTasks
        restored = true
    }

    private suspend fun drainLocked() {
        while (networkAvailable && jobs.size < capacity && pending.isNotEmpty()) {
            val id = pending.removeFirst()
            val task = taskLocked(id)
                ?.takeIf { it.status == DownloadTaskStatus.QUEUED }
                ?: continue
            val runtime = runtimeTasks[id]
            if (runtime == null) {
                saveAndPublishLocked(
                    task.copy(
                        status = DownloadTaskStatus.NEEDS_REFRESH,
                        requiresLinkRefresh = true,
                        updatedAtEpochMs = clock(),
                    ),
                )
                continue
            }
            val running = task.copy(
                status = DownloadTaskStatus.RUNNING,
                updatedAtEpochMs = clock(),
            )
            saveAndPublishLocked(running)
            val job = scope.launch(start = CoroutineStart.LAZY) {
                runTask(running, runtime)
            }
            jobs[id] = job
            job.start()
        }
    }

    private suspend fun runTask(
        initial: StoredDownloadTask,
        runtime: RuntimeTask,
    ) {
        val ownJob = kotlin.coroutines.coroutineContext[Job]
        try {
            when (
                val result = transferRunner.transfer(
                    plan = runtime.plan,
                    metadata = runtime.metadata,
                    destination = runtime.destination,
                    resumeFrom = initial.checkpoint,
                    onCheckpoint = { checkpoint ->
                        persistCheckpoint(initial.id, checkpoint)
                    },
                )
            ) {
                is DirectTransferResult.Completed -> completeTask(initial.id, result)
                is DirectTransferResult.Failure -> failTask(initial.id, result)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            gate.withLock {
                val current = taskLocked(initial.id) ?: return@withLock
                saveAndPublishLocked(
                    current.copy(
                        status = DownloadTaskStatus.FAILED,
                        failureReason = DownloadFailureReason.NETWORK,
                        updatedAtEpochMs = clock(),
                    ),
                )
            }
        } finally {
            withContext(NonCancellable) {
                gate.withLock {
                    if (jobs[initial.id] === ownJob) jobs.remove(initial.id)
                    drainLocked()
                }
            }
        }
    }

    private suspend fun persistCheckpoint(
        id: String,
        checkpoint: DirectTransferCheckpoint,
    ) = gate.withLock {
        val current = taskLocked(id) ?: return@withLock
        val updated = current.copy(
            totalBytes = checkpoint.totalBytes ?: current.totalBytes,
            downloadedBytes = checkpoint.downloadedBytes,
            checkpoint = checkpoint,
            updatedAtEpochMs = clock(),
        )
        saveAndPublishLocked(updated)
    }

    private suspend fun completeTask(
        id: String,
        result: DirectTransferResult.Completed,
    ) = gate.withLock {
        val current = taskLocked(id) ?: return@withLock
        val completed = current.copy(
            status = DownloadTaskStatus.COMPLETED,
            totalBytes = result.checkpoint.totalBytes ?: result.bytesWritten,
            downloadedBytes = result.bytesWritten,
            requiresLinkRefresh = false,
            failureReason = null,
            checkpoint = result.checkpoint,
            updatedAtEpochMs = clock(),
        )
        runtimeTasks.remove(id)
        saveAndPublishLocked(completed)
    }

    private suspend fun failTask(
        id: String,
        result: DirectTransferResult.Failure,
    ) = gate.withLock {
        val current = taskLocked(id) ?: return@withLock
        val needsRefresh = result.failure.reason in REFRESH_FAILURES
        val failed = current.copy(
            status = if (needsRefresh) {
                DownloadTaskStatus.NEEDS_REFRESH
            } else {
                DownloadTaskStatus.FAILED
            },
            totalBytes = result.checkpoint.totalBytes ?: current.totalBytes,
            downloadedBytes = result.checkpoint.downloadedBytes,
            requiresLinkRefresh = needsRefresh,
            failureReason = result.failure.reason,
            checkpoint = result.checkpoint,
            updatedAtEpochMs = clock(),
        )
        if (needsRefresh) runtimeTasks.remove(id)
        saveAndPublishLocked(failed)
    }

    private suspend fun saveAndPublishLocked(task: StoredDownloadTask) {
        store.save(task)
        val existing = mutableTasks.value.indexOfFirst { it.id == task.id }
        mutableTasks.value = if (existing < 0) {
            mutableTasks.value + task
        } else {
            mutableTasks.value.toMutableList().apply { set(existing, task) }
        }
    }

    private fun taskLocked(id: String): StoredDownloadTask? =
        mutableTasks.value.firstOrNull { it.id == id }

    private fun Throwable?.toStorageFailureReason(): DownloadFailureReason {
        val message = this?.message.orEmpty().lowercase(Locale.US)
        return if (
            this is IOException &&
            ("enospc" in message || "no space left" in message || "disk full" in message)
        ) {
            DownloadFailureReason.INSUFFICIENT_STORAGE
        } else {
            DownloadFailureReason.STORAGE_UNAVAILABLE
        }
    }

    private data class RuntimeTask(
        val plan: DirectDownloadPlan,
        val metadata: RemoteFileMetadata,
        val destination: DownloadDestination,
    )

    private data class StopPreparation(
        val shouldFinalize: Boolean,
        val job: Job?,
    )

    private companion object {
        val TERMINAL_STATUSES = setOf(
            DownloadTaskStatus.COMPLETED,
            DownloadTaskStatus.FAILED,
            DownloadTaskStatus.CANCELLED,
            DownloadTaskStatus.NEEDS_REFRESH,
        )
        val REFRESH_FAILURES = setOf(
            DownloadFailureReason.EXPIRED_URL,
            DownloadFailureReason.AUTHENTICATION_REQUIRED,
            DownloadFailureReason.ACCESS_DENIED,
            DownloadFailureReason.GONE,
        )
    }
}