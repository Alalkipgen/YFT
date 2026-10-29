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
import com.alal.yft.core.model.download.DownloadProgress
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.download.HlsTransferCheckpoint
import com.alal.yft.core.model.download.Mp3Encoding
import com.alal.yft.core.model.download.RemoteFileMetadata
import com.alal.yft.core.model.download.TransferCheckpoint
import com.alal.yft.core.model.media.BrowserRequestContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class DownloadDestinationSpec(
    val kind: DownloadDestinationKind,
    val uri: String? = null,
)

/**
 * Owns a bounded in-process transfer queue while persisting only non-sensitive recovery state.
 *
 * URLs, cookies and request headers remain in [runtimeTasks]. After process death every incomplete
 * task therefore becomes `NEEDS_REFRESH` while its verified segment checkpoint remains available.
 *
 * Track E: when a file's link dies after the download started, [linkRenewal] is asked for a fresh
 * link of the same file ([DownloadTaskStatus.RENEWING_LINK]), in the task's own job: the task
 * keeps its slot, and Pause, Cancel and a lost network stop the lookup as they stop a transfer.
 * At most [MAX_AUTOMATIC_RENEWALS] fresh links come without a tap, another only after the last
 * one gave bytes (E-D3); a Retry of a "Link expired" row asks once more (E-D6).
 */
class DownloadQueue(
    private val store: DownloadTaskStore,
    private val transferDispatcher: DownloadTransferDispatcher,
    private val scope: CoroutineScope,
    maxConcurrentDownloads: Int = 2,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val linkRenewal: DownloadLinkRenewal = DownloadLinkRenewal.None,
    /** The longest one renewal may take, its file probe included (Home's lookup limit). */
    private val renewalTimeoutMillis: Long = DEFAULT_RENEWAL_TIMEOUT_MILLIS,
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

    init {
        require(renewalTimeoutMillis > 0)
    }

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
        val checkpoint = current.checkpoint.takeIf { current.planType == planType }
            ?: emptyCheckpoint
        // A new link of the app's own: the next link number, the automatic renewals kept (E-D3).
        val previous = runtimeTasks[id]
        runtimeTasks[id] = runtime.copy(
            link = previous?.let { it.link + 1 } ?: 0,
            linkStartBytes = checkpoint.downloadedBytes,
            automaticRenewals = previous?.automaticRenewals ?: 0,
            youTubeRefusalRenewed = previous?.youTubeRefusalRenewed ?: false,
        )
        pending.remove(id)
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
            current.status == DownloadTaskStatus.QUEUED ||
            current.status == DownloadTaskStatus.RENEWING_LINK
        ) {
            return@withLock
        }
        val runtime = runtimeTasks[id]
        // Track E: a task whose link died looks its page up again first; a tap's lookup is not
        // counted in the automatic ones (E-D6).
        val due = runtime?.due?.takeIf { linkRenewal !== DownloadLinkRenewal.None }
        if (runtime == null || runtime.parked && due == null) {
            saveAndPublishLocked(
                current.copy(
                    status = DownloadTaskStatus.NEEDS_REFRESH,
                    requiresLinkRefresh = true,
                    updatedAtEpochMs = clock(),
                ),
            )
            return@withLock
        }
        if (due != null) runtimeTasks[id] = runtime.copy(due = due.copy(byUser = true))
        val base = if (
            due == null &&
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
            requiresLinkRefresh = false,
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
                // A fresh lookup stops too; it runs again first when the network is back.
                val affected = mutableTasks.value.filter {
                    it.status == DownloadTaskStatus.RUNNING ||
                        it.status == DownloadTaskStatus.QUEUED ||
                        it.status == DownloadTaskStatus.RENEWING_LINK
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
                        it.status == DownloadTaskStatus.QUEUED ||
                        it.status == DownloadTaskStatus.RENEWING_LINK
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
            // Track E: a task whose link died gets a fresh one first, in its own job.
            val due = runtime?.due?.takeIf { linkRenewal !== DownloadLinkRenewal.None }
            if (runtime == null || runtime.parked && due == null) {
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
                status = if (due != null) {
                    DownloadTaskStatus.RENEWING_LINK
                } else {
                    DownloadTaskStatus.RUNNING
                },
                updatedAtEpochMs = clock(),
            )
            saveAndPublishLocked(running)
            val job = scope.launch(start = CoroutineStart.LAZY) {
                runTask(running, runtime, due)
            }
            jobs[id] = job
            job.start()
        }
    }

    private suspend fun runTask(
        initial: StoredDownloadTask,
        initialRuntime: RuntimeTask,
        firstRenewal: RenewalAsk?,
    ) {
        val ownJob = kotlin.coroutines.coroutineContext[Job]
        try {
            var task = initial
            var runtime = initialRuntime
            var renewal = firstRenewal
            // Track E: a fresh link runs the engine again in this job, so the task keeps its slot.
            while (true) {
                var freshLink = false
                if (renewal != null) {
                    val renewed = renewLink(initial.id, renewal) ?: return
                    task = renewed.task
                    runtime = renewed.runtime
                    freshLink = true
                }
                renewal = transferOnce(task, runtime, freshLink) ?: return
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

    /**
     * One run of the engine; a link that died and may be renewed now comes back as its ask. A run
     * with a [freshLink] tells [linkRenewal] where it went on (its first reported bytes).
     */
    private suspend fun transferOnce(
        task: StoredDownloadTask,
        runtime: RuntimeTask,
        freshLink: Boolean = false,
    ): RenewalAsk? {
        // Stream engines state a length only in their progress, for example a merge of two
        // whole files; the next checkpoint gives the task that total.
        val reportedTotal = AtomicReference<Long?>(null)
        // The bytes this run reported as written (P41), ahead of its checkpoints.
        val reportedBytes = AtomicLong(0)
        val kept = task.checkpoint.downloadedBytes
        val linkBytes = LinkBytes(kept)
        val wentOn = AtomicBoolean(!freshLink)
        val firstBytes: (Long) -> Unit = { bytes ->
            if (wentOn.compareAndSet(false, true)) {
                runCatching { linkRenewal.wentOn(task.id, kept, bytes) }
            }
        }
        val result = withContext(DownloadRequestTag(task.id, runtime.link)) {
            transferDispatcher.transfer(
                plan = runtime.plan,
                metadata = runtime.metadata,
                destination = runtime.destination,
                resumeFrom = task.checkpoint,
                onProgress = { progress ->
                    firstBytes(progress.downloadedBytes)
                    reportedTotal.set(progress.totalBytes)
                    reportedBytes.accumulateAndGet(progress.downloadedBytes, ::maxOf)
                    linkBytes.see(progress.downloadedBytes)
                    publishProgress(task.id, progress)
                },
                onCheckpoint = { checkpoint ->
                    firstBytes(checkpoint.downloadedBytes)
                    linkBytes.see(checkpoint.downloadedBytes)
                    persistCheckpoint(
                        id = task.id,
                        checkpoint = checkpoint,
                        reportedTotal = reportedTotal.get(),
                        reportedBytes = reportedBytes.get(),
                    )
                },
            )
        }
        return when (result) {
            is QueueTransferResult.Completed -> {
                completeTask(task.id, result)
                null
            }
            is QueueTransferResult.Failure -> failTask(task.id, result, linkBytes.gained)
        }
    }

    /**
     * Asks [linkRenewal] for a fresh link of [id] and applies the answer: the task with its new
     * runtime to run again, or null when the task stopped (a "Link expired" or failed row, or a
     * Pause, Cancel or lost network while the lookup ran: a late answer is dropped).
     */
    private suspend fun renewLink(id: String, ask: RenewalAsk): Renewed? {
        val request = gate.withLock {
            val current = taskLocked(id)
                ?.takeIf { it.status == DownloadTaskStatus.RENEWING_LINK }
                ?: return null
            val runtime = runtimeTasks[id] ?: return null
            LinkRenewalRequest(
                taskId = id,
                planType = current.planType,
                reason = ask.failure.reason,
                httpStatusCode = ask.failure.httpStatusCode,
                stage = ask.failure.stage,
                bytesKept = current.checkpoint.downloadedBytes,
                bytesFromThisLink = ask.bytesFromThisLink,
                byUser = ask.byUser,
                automaticRenewals = runtime.automaticRenewals,
            )
        }
        val answer = askForFreshLink(request)
        return gate.withLock { applyRenewalLocked(id, ask, answer) }
    }

    private suspend fun askForFreshLink(request: LinkRenewalRequest): LinkRenewal = try {
        withTimeoutOrNull(renewalTimeoutMillis) { linkRenewal.renew(request) }
            ?: LinkRenewal.Unavailable(
                "the site did not answer in ${renewalTimeoutMillis / MILLIS_PER_SECOND} s",
            )
    } catch (cancellation: CancellationException) {
        // This task's Pause, Cancel or lost network ends here; the lookup's own does not.
        currentCoroutineContext().ensureActive()
        LinkRenewal.Unavailable("the lookup stopped")
    } catch (error: Exception) {
        LinkRenewal.Unavailable(DownloadFailureDetails.of(error))
    }

    private suspend fun applyRenewalLocked(
        id: String,
        ask: RenewalAsk,
        answer: LinkRenewal,
    ): Renewed? {
        val current = taskLocked(id)
            ?.takeIf { it.status == DownloadTaskStatus.RENEWING_LINK }
            ?: return null
        val runtime = runtimeTasks[id] ?: return null
        return when (answer) {
            is LinkRenewal.Fresh -> {
                val plan = answer.plan
                when {
                    plan.taskId != id || plan.planType != current.planType -> {
                        stopRenewalLocked(current, runtime, ask, "another kind of file")
                        null
                    }
                    plan is DirectDownloadPlan && answer.metadata == null -> {
                        stopRenewalLocked(current, runtime, ask, "the file did not answer")
                        null
                    }
                    else -> {
                        val kept = current.checkpoint.downloadedBytes
                        val fresh = RuntimeTask(
                            plan = plan,
                            metadata = answer.metadata,
                            destination = runtime.destination,
                            link = runtime.link + 1,
                            linkStartBytes = kept,
                            renewed = true,
                            automaticRenewals = runtime.automaticRenewals +
                                if (ask.byUser) 0 else 1,
                            youTubeRefusalRenewed = runtime.youTubeRefusalRenewed ||
                                ask.failure.reason == DownloadFailureReason.YOUTUBE_REFUSED,
                        )
                        val total = (plan as? DirectDownloadPlan)
                            ?.let { answer.metadata?.totalBytes }
                            ?.takeIf { it >= kept }
                            ?: current.totalBytes?.takeIf { it >= kept }
                        val running = current.copy(
                            status = DownloadTaskStatus.RUNNING,
                            totalBytes = total,
                            requiresLinkRefresh = false,
                            failureReason = null,
                            failure = null,
                            updatedAtEpochMs = clock(),
                        )
                        runtimeTasks[id] = fresh
                        saveAndPublishLocked(running)
                        Renewed(running, fresh)
                    }
                }
            }
            is LinkRenewal.Unavailable -> {
                stopRenewalLocked(current, runtime, ask, answer.why)
                null
            }
            LinkRenewal.Declined -> {
                endWithoutRenewalLocked(current, runtime, ask)
                null
            }
        }
    }

    /** No fresh link: "Link expired" with the lookup's line first, the partial file kept. */
    private suspend fun stopRenewalLocked(
        current: StoredDownloadTask,
        runtime: RuntimeTask,
        ask: RenewalAsk,
        why: String,
    ) {
        val failure = ask.failure.copy(
            detail = DownloadFailureDetails.sanitize(
                listOfNotNull("Fresh link: $why", ask.failure.detail).joinToString("; "),
            ),
        )
        runtimeTasks[current.id] = runtime.parked(due = ask)
        saveAndPublishLocked(
            current.copy(
                status = DownloadTaskStatus.NEEDS_REFRESH,
                requiresLinkRefresh = true,
                failureReason = failure.reason,
                failure = failure,
                updatedAtEpochMs = clock(),
            ),
        )
    }

    /** The app does not renew this download: the failure ends as it did before Track E. */
    private suspend fun endWithoutRenewalLocked(
        current: StoredDownloadTask,
        runtime: RuntimeTask,
        ask: RenewalAsk,
    ) {
        val needsRefresh = ask.failure.reason in REFRESH_FAILURES
        runtimeTasks[current.id] = if (needsRefresh) {
            runtime.parked(due = ask)
        } else {
            runtime.copy(due = null)
        }
        saveAndPublishLocked(
            current.copy(
                status = if (needsRefresh) {
                    DownloadTaskStatus.NEEDS_REFRESH
                } else {
                    DownloadTaskStatus.FAILED
                },
                requiresLinkRefresh = needsRefresh,
                failureReason = ask.failure.reason,
                failure = ask.failure,
                updatedAtEpochMs = clock(),
            ),
        )
    }

    /**
     * Shows the bytes a running stream task wrote between its checkpoints (P41): a DASH range
     * or a merged download's tracks report them as they come. Only the list moves; the store
     * keeps the checkpoints.
     */
    private suspend fun publishProgress(id: String, progress: DownloadProgress) = gate.withLock {
        val current = taskLocked(id) ?: return@withLock
        if (current.status != DownloadTaskStatus.RUNNING) return@withLock
        if (!current.planType.showsBytesInFlight) return@withLock
        val downloaded = maxOf(current.downloadedBytes, progress.downloadedBytes)
        val total = progress.totalBytes?.takeIf { it >= downloaded }
            ?: current.totalBytes?.takeIf { it >= downloaded }
        if (downloaded == current.downloadedBytes && total == current.totalBytes) {
            return@withLock
        }
        publishLocked(current.copy(downloadedBytes = downloaded, totalBytes = total))
    }

    private suspend fun persistCheckpoint(
        id: String,
        checkpoint: TransferCheckpoint,
        reportedTotal: Long? = null,
        reportedBytes: Long = 0,
    ) = gate.withLock {
        val current = taskLocked(id) ?: return@withLock
        if (!current.planType.accepts(checkpoint)) return@withLock
        val checkpointTotal = (checkpoint as? DirectTransferCheckpoint)?.totalBytes
            ?: reportedTotal
        // A running stream task keeps the bytes it showed (P41); a stopped one shows its
        // checkpoint, which is what a resume keeps.
        val downloaded = if (
            current.status == DownloadTaskStatus.RUNNING &&
            current.planType.showsBytesInFlight
        ) {
            maxOf(checkpoint.downloadedBytes, reportedBytes)
        } else {
            checkpoint.downloadedBytes
        }
        val updated = current.copy(
            totalBytes = checkpointTotal
                ?.takeIf { it >= downloaded }
                ?: current.totalBytes?.takeIf { it >= downloaded },
            downloadedBytes = downloaded,
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
        // D1 follow-ups 3 and 4: a download shows the size of the file it published, one size
        // on every screen, for every plan type: an HLS download's MP4, an MP3 or M4A made from
        // a direct download, a merged video, a DASH file; not its pieces', source's or tracks'.
        // The checkpoint keeps what was downloaded.
        val publishedBytes = result.bytesWritten
        // D1: a file kept in another format than planned shows the name it was published as.
        val published = result.publishedAs
        val completed = current.copy(
            displayName = published?.displayName ?: current.displayName,
            mimeType = published?.mimeType ?: current.mimeType,
            status = DownloadTaskStatus.COMPLETED,
            totalBytes = publishedBytes,
            downloadedBytes = publishedBytes,
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

    /**
     * Stores [result]'s failure. Track E: a link that died (a reason in [REFRESH_FAILURES], or a
     * 400 after this link gave [linkBytes]) keeps the resume point (G1) and, with a renewal
     * source, comes back as the ask for a fresh link when one may come now (E-D3); else the task
     * ends as before, a "Link expired" one keeping only its destination for Cancel and Delete
     * (G2).
     */
    private suspend fun failTask(
        id: String,
        result: QueueTransferResult.Failure,
        linkBytes: Long,
    ): RenewalAsk? = gate.withLock {
        val current = taskLocked(id) ?: return@withLock null
        val now = clock()
        val runtime = runtimeTasks[id]
        val plan = runtime?.plan
        // Item 7 Track B1: YouTube refusing a link with time left on it is not an expired link.
        val reported = if (
            plan != null && GoogleVideoLinkFacts.refusedWithTimeLeft(plan, result.failure, now)
        ) {
            result.failure.copy(reason = DownloadFailureReason.YOUTUBE_REFUSED)
        } else {
            result.failure
        }
        val needsRefresh = reported.reason in REFRESH_FAILURES
        // F6 (debug spike): a YouTube media link's facts lead the details, without the link.
        val failure = plan
            ?.let { known ->
                GoogleVideoLinkFacts.describe(
                    plan = known,
                    nowEpochMs = now,
                    downloadedBytes = result.checkpoint.downloadedBytes,
                    ageMs = now - current.createdAtEpochMs,
                )
            }
            ?.let { facts ->
                reported.copy(
                    detail = DownloadFailureDetails.sanitize(
                        listOfNotNull(facts, reported.detail).joinToString("; "),
                    ),
                )
            }
            ?: reported
        val linkDied = needsRefresh || (
            reported.reason == DownloadFailureReason.HTTP_STATUS &&
                reported.httpStatusCode == HTTP_BAD_REQUEST &&
                linkBytes > 0
            )
        val checkpoint = if (linkDied) {
            result.checkpoint.orSaved(current.checkpoint)
        } else {
            result.checkpoint
        }
        val ask = RenewalAsk(failure, linkBytes, byUser = false).takeIf {
            linkDied && runtime != null && linkRenewal !== DownloadLinkRenewal.None
        }
        val renews = ask != null &&
            current.status == DownloadTaskStatus.RUNNING &&
            runtime?.allowsAutomaticRenewal(reported.reason, linkBytes) == true
        val status = when {
            renews -> DownloadTaskStatus.RENEWING_LINK
            needsRefresh -> DownloadTaskStatus.NEEDS_REFRESH
            else -> DownloadTaskStatus.FAILED
        }
        val checkpointTotal = (checkpoint as? DirectTransferCheckpoint)?.totalBytes
        val failed = current.copy(
            status = status,
            totalBytes = checkpointTotal
                ?.takeIf { it >= checkpoint.downloadedBytes }
                ?: current.totalBytes?.takeIf {
                    it >= checkpoint.downloadedBytes
                },
            downloadedBytes = checkpoint.downloadedBytes,
            requiresLinkRefresh = status == DownloadTaskStatus.NEEDS_REFRESH,
            failureReason = failure.reason,
            failure = failure,
            checkpoint = checkpoint,
            updatedAtEpochMs = now,
        )
        if (runtime != null) {
            runtimeTasks[id] = when (status) {
                // The dead link stays until the fresh one replaces it.
                DownloadTaskStatus.RENEWING_LINK -> runtime.copy(due = ask)
                DownloadTaskStatus.NEEDS_REFRESH -> runtime.parked(due = ask)
                else -> runtime.copy(due = null)
            }
        }
        saveAndPublishLocked(failed)
        ask.takeIf { renews }
    }

    private suspend fun saveAndPublishLocked(task: StoredDownloadTask) {
        // The store keeps checkpoints (P41): bytes in flight are only shown while running.
        store.save(task.withCheckpointBytes())
        publishLocked(
            if (task.status == DownloadTaskStatus.RUNNING) task else task.withCheckpointBytes(),
        )
    }

    private fun publishLocked(task: StoredDownloadTask) {
        val existing = mutableTasks.value.indexOfFirst { it.id == task.id }
        mutableTasks.value = if (existing < 0) {
            mutableTasks.value + task
        } else {
            mutableTasks.value.toMutableList().apply { set(existing, task) }
        }
    }

    /**
     * A stopped stream task shows what its checkpoint keeps (P41), not the bytes in flight; a
     * completed one the file it published (D1 follow-up 4).
     */
    private fun StoredDownloadTask.withCheckpointBytes(): StoredDownloadTask =
        if (planType.showsBytesInFlight && !status.showsPublishedBytes) {
            copy(downloadedBytes = checkpoint.downloadedBytes)
        } else {
            this
        }

    private fun taskLocked(id: String): StoredDownloadTask? =
        mutableTasks.value.firstOrNull { it.id == id }

    /**
     * A task's link and destination, in memory only. Track E adds the link's facts: its number
     * ([link], the requests' [DownloadRequestTag]), the checkpoint's bytes when it came, whether
     * a renewal gave it, the automatic renewals so far (E-D3), whether a "YouTube refused" was
     * renewed (E-D5': once), and the fresh link the task needs before it runs again ([due]).
     */
    private data class RuntimeTask(
        val plan: DownloadPlan,
        val metadata: RemoteFileMetadata?,
        val destination: DownloadDestination,
        val link: Int = 0,
        val linkStartBytes: Long = 0,
        val renewed: Boolean = false,
        val automaticRenewals: Int = 0,
        val youTubeRefusalRenewed: Boolean = false,
        /** G2: only the destination is left; the dead link and its cookies are gone. */
        val parked: Boolean = false,
        val due: RenewalAsk? = null,
    ) {
        /**
         * Whether a fresh link may come without a tap (E-D3): at most [MAX_AUTOMATIC_RENEWALS],
         * another only after the last fresh link gave bytes; "YouTube refused" once (E-D5').
         */
        fun allowsAutomaticRenewal(reason: DownloadFailureReason, linkBytes: Long): Boolean =
            automaticRenewals < MAX_AUTOMATIC_RENEWALS &&
                (!renewed || linkBytes > 0) &&
                (reason != DownloadFailureReason.YOUTUBE_REFUSED || !youTubeRefusalRenewed)

        /** G2: this task without its link, cookies and file facts; Cancel and Delete clean up. */
        fun parked(due: RenewalAsk?): RuntimeTask = copy(
            plan = plan.withoutLink(),
            metadata = null,
            parked = true,
            due = due,
        )
    }

    /** The failure that needs a fresh link, its link's bytes (E-D3) and whether a tap asks. */
    private data class RenewalAsk(
        val failure: DownloadFailure,
        val bytesFromThisLink: Long,
        val byUser: Boolean,
    )

    private data class Renewed(
        val task: StoredDownloadTask,
        val runtime: RuntimeTask,
    )

    /**
     * The bytes one link gave (E-D3): each rise of the bytes its run reports. A drop is the
     * engine starting again at byte 0 or a piece it could not keep, never a gain.
     */
    private class LinkBytes(start: Long) {
        private val last = AtomicLong(start)
        private val total = AtomicLong(0)

        fun see(bytes: Long) {
            val previous = last.getAndSet(bytes)
            if (bytes > previous) total.addAndGet(bytes - previous)
        }

        val gained: Long get() = total.get()
    }

    private data class StopPreparation(
        val shouldFinalize: Boolean,
        val job: Job?,
    )

    private companion object {
        const val DEFAULT_STREAM_SEGMENT_COUNT = 1

        /** Track E (E-D3): fresh links without a tap, per download. */
        const val MAX_AUTOMATIC_RENEWALS = 3

        /** Track E: one renewal's limit, as Home's lookup (`LinkInspector.TIMEOUT_MILLIS`). */
        const val DEFAULT_RENEWAL_TIMEOUT_MILLIS = 90_000L
        const val MILLIS_PER_SECOND = 1_000L

        /** VK's media hosts answer 400 for a link bound to another network (E-D1). */
        const val HTTP_BAD_REQUEST = 400

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
            // Track B1: the refused link stays refused; a fresh lookup gives a new one.
            DownloadFailureReason.YOUTUBE_REFUSED,
        )
    }
}

/** The plan type of this plan, as the queue stores it. */
private val DownloadPlan.planType: DownloadPlanType
    get() = when (this) {
        is DirectDownloadPlan -> DownloadPlanType.DIRECT
        is HlsDownloadPlan -> DownloadPlanType.HLS
        is DashDownloadPlan -> DownloadPlanType.DASH
        is AudioVideoMuxDownloadPlan -> DownloadPlanType.AUDIO_VIDEO_MUX
    }

/** G2: this plan with no link and no request context; its task id and options stay. */
private fun DownloadPlan.withoutLink(): DownloadPlan = when (this) {
    is DirectDownloadPlan -> copy(sourceUrl = PARKED_PLAN_LINK, requestContext = NO_PLAN_CONTEXT)
    is HlsDownloadPlan -> copy(playlistUrl = PARKED_PLAN_LINK, requestContext = NO_PLAN_CONTEXT)
    is DashDownloadPlan -> withoutLink()
    is AudioVideoMuxDownloadPlan -> copy(video = video.withoutLink(), audio = audio.withoutLink())
}

private fun DashDownloadPlan.withoutLink(): DashDownloadPlan =
    copy(manifestUrl = PARKED_PLAN_LINK, requestContext = NO_PLAN_CONTEXT)

/**
 * G1: an engine that failed before it read the saved state (an expired link's check, a
 * playlist's or manifest's refusal) returns an untouched, empty checkpoint; [saved] then stays.
 * Each engine checks the saved files again when it resumes.
 */
private fun TransferCheckpoint.orSaved(saved: TransferCheckpoint): TransferCheckpoint =
    when (this) {
        is DirectTransferCheckpoint ->
            if (segments.isEmpty() && saved is DirectTransferCheckpoint) saved else this
        is HlsTransferCheckpoint ->
            if (isUntouched() && saved is HlsTransferCheckpoint) saved else this
        is DashTransferCheckpoint ->
            if (isUntouched() && saved is DashTransferCheckpoint) saved else this
        is AudioVideoMuxCheckpoint -> orSaved(saved as? AudioVideoMuxCheckpoint)
    }

/** A merge keeps a saved track only where this checkpoint did not touch it and it is not ready. */
private fun AudioVideoMuxCheckpoint.orSaved(
    saved: AudioVideoMuxCheckpoint?,
): AudioVideoMuxCheckpoint {
    saved ?: return this
    if (!videoReady && !audioReady && video.isUntouched() && audio.isUntouched()) return saved
    return copy(
        video = if (!videoReady && !saved.videoReady && video.isUntouched()) saved.video else video,
        audio = if (!audioReady && !saved.audioReady && audio.isUntouched()) saved.audio else audio,
    )
}

private fun HlsTransferCheckpoint.isUntouched(): Boolean =
    manifestFingerprint == null && chunks.isEmpty()

private fun DashTransferCheckpoint?.isUntouched(): Boolean =
    this == null || manifestFingerprint == null && chunks.isEmpty()

/** G2: a parked plan's link: not a web address, so no engine could fetch it. */
private const val PARKED_PLAN_LINK = "about:blank"
private val NO_PLAN_CONTEXT = BrowserRequestContext(pageUrl = null, userAgent = null, cookie = null)
