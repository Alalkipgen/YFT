package com.alal.yft.core.download

import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureDetails
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadPlan
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.download.HlsTransferCheckpoint
import com.alal.yft.core.model.download.Mp3Encoding
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.TransferCheckpoint
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
    private val transferDispatcher: DownloadTransferDispatcher,
    private val scope: CoroutineScope,
    maxConcurrentDownloads: Int = 2,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(
        store: DownloadTaskStore,
        transferRunner: DirectTransferRunner,
        scope: CoroutineScope,
        maxConcurrentDownloads: Int = 2,
        clock: () -> Long = System::currentTimeMillis,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        store = store,
        transferDispatcher = DirectOnlyDownloadTransferDispatcher(transferRunner),
        scope = scope,
        maxConcurrentDownloads = maxConcurrentDownloads,
        clock = clock,
        ioDispatcher = ioDispatcher,
    )

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
    ): String = enqueueRuntime(
        runtime = RuntimeTask(plan, metadata, destination),
        planType = DownloadPlanType.DIRECT,
        checkpoint = DirectTransferCheckpoint(
            totalBytes = metadata.totalBytes,
            entityTag = metadata.entityTag,
            lastModified = metadata.lastModified,
            segments = emptyList(),
        ),
        totalBytes = metadata.totalBytes,
        mimeType = plan.mp3?.let { Mp3Encoding.MIME_TYPE }
            ?: AUDIO_ONLY_MIME_TYPE.takeIf { plan.audioOnly }
            ?: metadata.contentType ?: plan.mimeType,
        preferredSegmentCount = plan.preferredSegmentCount,
        destinationSpec = destinationSpec,
    )

    suspend fun enqueue(
        plan: HlsDownloadPlan,
        destination: DownloadDestination,
        destinationSpec: DownloadDestinationSpec = DownloadDestinationSpec(
            DownloadDestinationKind.APP_PRIVATE,
        ),
    ): String = enqueueRuntime(
        runtime = RuntimeTask(plan, null, destination),
        planType = DownloadPlanType.HLS,
        checkpoint = HlsTransferCheckpoint(null, emptyList()),
        totalBytes = null,
        mimeType = plan.mimeType,
        preferredSegmentCount = DEFAULT_STREAM_SEGMENT_COUNT,
        destinationSpec = destinationSpec,
    )

    suspend fun enqueue(
        plan: DashDownloadPlan,
        destination: DownloadDestination,
        destinationSpec: DownloadDestinationSpec = DownloadDestinationSpec(
            DownloadDestinationKind.APP_PRIVATE,
        ),
    ): String = enqueueRuntime(
        runtime = RuntimeTask(plan, null, destination),
        planType = DownloadPlanType.DASH,
        checkpoint = DashTransferCheckpoint(null, emptyList()),
        totalBytes = null,
        mimeType = plan.mimeType,
        preferredSegmentCount = DEFAULT_STREAM_SEGMENT_COUNT,
        destinationSpec = destinationSpec,
    )

    suspend fun enqueue(
        plan: AudioVideoMuxDownloadPlan,
        destination: DownloadDestination,
        destinationSpec: DownloadDestinationSpec = DownloadDestinationSpec(
            DownloadDestinationKind.APP_PRIVATE,
        ),
    ): String = enqueueRuntime(
        runtime = RuntimeTask(plan, null, destination),
        planType = DownloadPlanType.AUDIO_VIDEO_MUX,
        checkpoint = AudioVideoMuxCheckpoint(),
        totalBytes = null,
        mimeType = plan.outputMimeType,
        preferredSegmentCount = DEFAULT_STREAM_SEGMENT_COUNT,
        destinationSpec = destinationSpec,
    )

    private suspend fun enqueueRuntime(
        runtime: RuntimeTask,
        planType: DownloadPlanType,
        checkpoint: TransferCheckpoint,
        totalBytes: Long?,
        mimeType: String?,
        preferredSegmentCount: Int,
        destinationSpec: DownloadDestinationSpec,
    ): String = gate.withLock {
        restoreLocked()
        require(mutableTasks.value.none { it.id == runtime.plan.taskId }) {
            "A task with this ID already exists"
        }
        val now = clock()
        val task = StoredDownloadTask(
            id = runtime.plan.taskId,
            displayName = runtime.plan.suggestedFileName,
            status = if (networkAvailable) {
                DownloadTaskStatus.QUEUED
            } else {
                DownloadTaskStatus.WAITING_FOR_NETWORK
            },
            planType = planType,
            totalBytes = totalBytes,
            downloadedBytes = checkpoint.downloadedBytes,
            mimeType = mimeType,
            destinationKind = destinationSpec.kind,
            destinationUri = runtime.destination.recoveryUri ?: destinationSpec.uri,
            preferredSegmentCount = preferredSegmentCount,
            requiresLinkRefresh = false,
            failureReason = null,
            checkpoint = checkpoint,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        runtimeTasks[task.id] = runtime
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
    ) = refreshRuntime(
        id = id,
        runtime = RuntimeTask(plan, metadata, destination),
        planType = DownloadPlanType.DIRECT,
        emptyCheckpoint = DirectTransferCheckpoint(
            totalBytes = metadata.totalBytes,
            entityTag = metadata.entityTag,
            lastModified = metadata.lastModified,
            segments = emptyList(),
        ),
        totalBytes = metadata.totalBytes,
        mimeType = plan.mp3?.let { Mp3Encoding.MIME_TYPE }
            ?: AUDIO_ONLY_MIME_TYPE.takeIf { plan.audioOnly }
            ?: metadata.contentType ?: plan.mimeType,
        preferredSegmentCount = plan.preferredSegmentCount,
    )

    suspend fun refresh(
        id: String,
        plan: HlsDownloadPlan,
        destination: DownloadDestination,
    ) = refreshRuntime(
        id = id,
        runtime = RuntimeTask(plan, null, destination),
        planType = DownloadPlanType.HLS,
        emptyCheckpoint = HlsTransferCheckpoint(null, emptyList()),
        totalBytes = null,
        mimeType = plan.mimeType,
        preferredSegmentCount = DEFAULT_STREAM_SEGMENT_COUNT,
    )

    suspend fun refresh(
        id: String,
        plan: DashDownloadPlan,
        destination: DownloadDestination,
    ) = refreshRuntime(
        id = id,
        runtime = RuntimeTask(plan, null, destination),
        planType = DownloadPlanType.DASH,
        emptyCheckpoint = DashTransferCheckpoint(null, emptyList()),
        totalBytes = null,
        mimeType = plan.mimeType,
        preferredSegmentCount = DEFAULT_STREAM_SEGMENT_COUNT,
    )

    suspend fun refresh(
        id: String,
        plan: AudioVideoMuxDownloadPlan,
        destination: DownloadDestination,
    ) = refreshRuntime(
        id = id,
        runtime = RuntimeTask(plan, null, destination),
        planType = DownloadPlanType.AUDIO_VIDEO_MUX,
        emptyCheckpoint = AudioVideoMuxCheckpoint(),
        totalBytes = null,
        mimeType = plan.outputMimeType,
        preferredSegmentCount = DEFAULT_STREAM_SEGMENT_COUNT,
    )

    private suspend fun refreshRuntime(
        id: String,
        runtime: RuntimeTask,
        planType: DownloadPlanType,
        emptyCheckpoint: TransferCheckpoint,
        totalBytes: Long?,
        mimeType: String?,
        preferredSegmentCount: Int,
    ) = gate.withLock {
        restoreLocked()
        require(runtime.plan.taskId == id)
        val current = taskLocked(id) ?: error("Download task does not exist")
        check(current.status != DownloadTaskStatus.COMPLETED)
        runtimeTasks[id] = runtime
        pending.remove(id)
        val checkpoint = current.checkpoint.takeIf { current.planType == planType }
            ?: emptyCheckpoint
        val refreshed = current.copy(
            displayName = runtime.plan.suggestedFileName,
            status = if (networkAvailable) {
                DownloadTaskStatus.QUEUED
            } else {
                DownloadTaskStatus.WAITING_FOR_NETWORK
            },
            planType = planType,
            totalBytes = totalBytes?.takeIf { it >= checkpoint.downloadedBytes },
            downloadedBytes = checkpoint.downloadedBytes,
            mimeType = mimeType,
            destinationUri = runtime.destination.recoveryUri ?: current.destinationUri,
            preferredSegmentCount = preferredSegmentCount,
            requiresLinkRefresh = false,
            failureReason = null,
            failure = null,
            checkpoint = checkpoint,
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
        val base = if (
            current.status == DownloadTaskStatus.FAILED &&
            withContext(ioDispatcher) { mustStartOver(current, runtime) }
        ) {
            val renewed = try {
                withContext(ioDispatcher) { startOver(runtime) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // No new destination: the Retry fails again, with the new details.
                val failure = error.toStorageFailure(DownloadFailureStage.OPEN_FILE)
                saveAndPublishLocked(
                    current.copy(
                        failureReason = failure.reason,
                        failure = failure,
                        updatedAtEpochMs = clock(),
                    ),
                )
                return@withLock
            }
            runtimeTasks[id] = renewed
            current.startedOver(renewed)
        } else {
            current
        }
        val resumed = base.copy(
            status = if (networkAvailable) {
                DownloadTaskStatus.QUEUED
            } else {
                DownloadTaskStatus.WAITING_FOR_NETWORK
            },
            failureReason = null,
            failure = null,
            updatedAtEpochMs = clock(),
        )
        saveAndPublishLocked(resumed)
        pending.remove(id)
        if (networkAvailable) {
            pending.addLast(id)
            drainLocked()
        }
    }

    /**
     * Whether a Retry must start over from byte 0 (P21): after the storage failed, or when the
     * partial file a direct download wrote is gone or shorter than its checkpoint. Other
     * failures resume from the checkpoint.
     */
    private fun mustStartOver(task: StoredDownloadTask, runtime: RuntimeTask): Boolean {
        if (task.failureReason in START_OVER_FAILURES) return true
        // Streams and conversions write the destination only at the end, from their workspace.
        val direct = runtime.plan as? DirectDownloadPlan ?: return false
        if (direct.converts) return false
        val checkpoint = task.checkpoint as? DirectTransferCheckpoint ?: return false
        val written = checkpoint.segments
            .filter { it.downloadedBytes > 0 }
            .maxOfOrNull { it.startByte + it.downloadedBytes }
            ?: return false
        val length = try {
            runtime.destination.temporaryLength()
        } catch (error: Exception) {
            if (!error.isStorageError()) throw error
            return true
        }
        return length == null || length < written
    }

    /** A new destination in place of the old one, and no engine state left from the old. */
    private suspend fun startOver(runtime: RuntimeTask): RuntimeTask {
        val renewed = runtime.destination.renew()
        runCatching { transferDispatcher.discard(runtime.plan) }
        return runtime.copy(destination = renewed ?: runtime.destination)
    }

    private fun StoredDownloadTask.startedOver(runtime: RuntimeTask): StoredDownloadTask {
        val metadata = runtime.metadata
        val empty = when (planType) {
            DownloadPlanType.DIRECT -> DirectTransferCheckpoint(
                totalBytes = metadata?.totalBytes,
                entityTag = metadata?.entityTag,
                lastModified = metadata?.lastModified,
                segments = emptyList(),
            )
            DownloadPlanType.HLS -> HlsTransferCheckpoint(null, emptyList())
            DownloadPlanType.DASH -> DashTransferCheckpoint(null, emptyList())
            DownloadPlanType.AUDIO_VIDEO_MUX -> AudioVideoMuxCheckpoint()
        }
        return copy(
            totalBytes = (empty as? DirectTransferCheckpoint)?.totalBytes,
            downloadedBytes = 0,
            destinationUri = runtime.destination.recoveryUri ?: destinationUri,
            checkpoint = empty,
        )
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
        val discardFailure = withContext(ioDispatcher) {
            var failure: Throwable? = null
            if (runtime != null) {
                runCatching { transferDispatcher.discard(runtime.plan) }
                    .onFailure { failure = it }
                runCatching { runtime.destination.discard() }
                    .onFailure { if (failure == null) failure = it }
            }
            failure
        }
        gate.withLock {
            val current = taskLocked(id) ?: return@withLock
            val cancelled = if (discardFailure == null) {
                current.copy(
                    status = DownloadTaskStatus.CANCELLED,
                    failureReason = null,
                    failure = null,
                    updatedAtEpochMs = clock(),
                )
            } else {
                val failure = discardFailure.toStorageFailure(null)
                current.copy(
                    status = DownloadTaskStatus.FAILED,
                    failureReason = failure.reason,
                    failure = failure,
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
        // Stream engines state a length only in their progress, for example a merge of two
        // whole files; the next checkpoint gives the task that total.
        val reportedTotal = AtomicReference<Long?>(null)
        try {
            when (
                val result = transferDispatcher.transfer(
                    plan = runtime.plan,
                    metadata = runtime.metadata,
                    destination = runtime.destination,
                    resumeFrom = initial.checkpoint,
                    onProgress = { progress -> reportedTotal.set(progress.totalBytes) },
                    onCheckpoint = { checkpoint ->
                        persistCheckpoint(initial.id, checkpoint, reportedTotal.get())
                    },
                )
            ) {
                is QueueTransferResult.Completed -> completeTask(initial.id, result)
                is QueueTransferResult.Failure -> failTask(initial.id, result)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // The reason stays as before; the details keep the error's class.
            val failure = DownloadFailure(
                reason = DownloadFailureReason.NETWORK,
                detail = DownloadFailureDetails.of(error),
            )
            gate.withLock {
                val current = taskLocked(initial.id) ?: return@withLock
                saveAndPublishLocked(
                    current.copy(
                        status = DownloadTaskStatus.FAILED,
                        failureReason = failure.reason,
                        failure = failure,
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
        checkpoint: TransferCheckpoint,
        reportedTotal: Long? = null,
    ) = gate.withLock {
        val current = taskLocked(id) ?: return@withLock
        if (!current.planType.accepts(checkpoint)) return@withLock
        val checkpointTotal = (checkpoint as? DirectTransferCheckpoint)?.totalBytes
            ?: reportedTotal
        val updated = current.copy(
            totalBytes = checkpointTotal
                ?.takeIf { it >= checkpoint.downloadedBytes }
                ?: current.totalBytes?.takeIf { it >= checkpoint.downloadedBytes },
            downloadedBytes = checkpoint.downloadedBytes,
            checkpoint = checkpoint,
            updatedAtEpochMs = clock(),
        )
        saveAndPublishLocked(updated)
    }

    private suspend fun completeTask(
        id: String,
        result: QueueTransferResult.Completed,
    ) = gate.withLock {
        val current = taskLocked(id) ?: return@withLock
        val publishedUri = runtimeTasks[id]?.destination?.publishedUri
        val checkpointBytes = result.checkpoint.downloadedBytes
        val totalBytes = when (current.planType) {
            DownloadPlanType.AUDIO_VIDEO_MUX -> null
            else -> (current.totalBytes ?: result.bytesWritten)
                .coerceAtLeast(checkpointBytes)
        }
        val completed = current.copy(
            status = DownloadTaskStatus.COMPLETED,
            totalBytes = totalBytes,
            downloadedBytes = checkpointBytes,
            requiresLinkRefresh = false,
            failureReason = null,
            failure = null,
            destinationUri = publishedUri ?: current.destinationUri,
            checkpoint = result.checkpoint,
            updatedAtEpochMs = clock(),
        )
        runtimeTasks.remove(id)
        saveAndPublishLocked(completed)
    }

    private suspend fun failTask(
        id: String,
        result: QueueTransferResult.Failure,
    ) = gate.withLock {
        val current = taskLocked(id) ?: return@withLock
        val needsRefresh = result.failure.reason in REFRESH_FAILURES
        val checkpointTotal = (result.checkpoint as? DirectTransferCheckpoint)?.totalBytes
        val failed = current.copy(
            status = if (needsRefresh) {
                DownloadTaskStatus.NEEDS_REFRESH
            } else {
                DownloadTaskStatus.FAILED
            },
            totalBytes = checkpointTotal
                ?.takeIf { it >= result.checkpoint.downloadedBytes }
                ?: current.totalBytes?.takeIf {
                    it >= result.checkpoint.downloadedBytes
                },
            downloadedBytes = result.checkpoint.downloadedBytes,
            requiresLinkRefresh = needsRefresh,
            failureReason = result.failure.reason,
            failure = result.failure,
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

    private data class RuntimeTask(
        val plan: DownloadPlan,
        val metadata: RemoteFileMetadata?,
        val destination: DownloadDestination,
    )

    private data class StopPreparation(
        val shouldFinalize: Boolean,
        val job: Job?,
    )

    private companion object {
        const val DEFAULT_STREAM_SEGMENT_COUNT = 1

        /** An M4A kept from a video's sound (P3). */
        const val AUDIO_ONLY_MIME_TYPE = "audio/mp4"

        val TERMINAL_STATUSES = setOf(
            DownloadTaskStatus.COMPLETED,
            DownloadTaskStatus.FAILED,
            DownloadTaskStatus.CANCELLED,
            DownloadTaskStatus.NEEDS_REFRESH,
        )
        /**
         * A Retry after these starts over. A full device keeps its partial file: the space a
         * Retry needs is freed by the owner, not by losing the bytes already downloaded.
         */
        val START_OVER_FAILURES = setOf(DownloadFailureReason.STORAGE_UNAVAILABLE)
        val REFRESH_FAILURES = setOf(
            DownloadFailureReason.EXPIRED_URL,
            DownloadFailureReason.AUTHENTICATION_REQUIRED,
            DownloadFailureReason.ACCESS_DENIED,
            DownloadFailureReason.GONE,
        )
    }
}
