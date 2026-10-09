package com.alal.yft.core.download

import com.alal.yft.core.data.db.DownloadRecordDao
import com.alal.yft.core.data.db.DownloadRecordEntity
import com.alal.yft.core.data.db.DownloadRecordWithSegments
import com.alal.yft.core.data.db.DownloadSegmentEntity
import com.alal.yft.core.model.download.AudioVideoMuxCheckpoint
import com.alal.yft.core.model.download.AudioVideoMuxStage
import com.alal.yft.core.model.download.DashTransferCheckpoint
import com.alal.yft.core.model.download.DirectTransferCheckpoint
import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureDetails
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.DownloadSegment
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.download.HlsTransferCheckpoint
import com.alal.yft.core.model.download.StreamChunkCheckpoint
import com.alal.yft.core.model.download.TransferCheckpoint

enum class DownloadDestinationKind {
    APP_PRIVATE,
    MEDIA_STORE,
    SAF_DOCUMENT,
}

enum class DownloadPlanType {
    DIRECT,
    HLS,
    DASH,
    AUDIO_VIDEO_MUX,
}

data class StoredDownloadTask(
    val id: String,
    val displayName: String,
    val status: DownloadTaskStatus,
    val planType: DownloadPlanType = DownloadPlanType.DIRECT,
    val totalBytes: Long?,
    val downloadedBytes: Long,
    val mimeType: String?,
    val destinationKind: DownloadDestinationKind,
    val destinationUri: String?,
    val preferredSegmentCount: Int,
    val requiresLinkRefresh: Boolean,
    val failureReason: DownloadFailureReason?,
    val checkpoint: TransferCheckpoint,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    /**
     * The whole failure behind [failureReason] (P21): its stage, HTTP status and detail. Null
     * when the task has not failed or when only the reason is known; a failure whose reason is
     * not [failureReason] is out of date and is not shown.
     */
    val failure: DownloadFailure? = null,
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
        require(totalBytes == null || totalBytes >= 0)
        require(downloadedBytes >= 0)
        require(totalBytes == null || downloadedBytes <= totalBytes)
        require(preferredSegmentCount in 1..32)
        require(planType.accepts(checkpoint))
        // A running DASH or merged task may show the bytes written since its last checkpoint
        // (P41); every other task shows exactly what its checkpoint keeps.
        require(
            checkpoint.downloadedBytes == downloadedBytes ||
                (planType.showsBytesInFlight && downloadedBytes > checkpoint.downloadedBytes),
        )
    }

    val progressPercent: Int
        get() = when {
            status == DownloadTaskStatus.COMPLETED -> 100
            totalBytes == null || totalBytes == 0L -> 0
            else -> ((downloadedBytes * 100.0) / totalBytes)
                .toInt()
                .coerceIn(0, 99)
        }

    override fun toString(): String = buildString {
        append("StoredDownloadTask(id=")
        append(id)
        append(", displayName=")
        append(displayName)
        append(", status=")
        append(status)
        append(", planType=")
        append(planType)
        append(", totalBytes=")
        append(totalBytes)
        append(", downloadedBytes=")
        append(downloadedBytes)
        append(", destinationKind=")
        append(destinationKind)
        append(", destinationUriPresent=")
        append(!destinationUri.isNullOrBlank())
        append(", requiresLinkRefresh=")
        append(requiresLinkRefresh)
        append(", failureReason=")
        append(failureReason)
        append(", failureStage=")
        append(failure?.stage)
        append(')')
    }
}

interface DownloadTaskStore {
    suspend fun loadAll(): List<StoredDownloadTask>
    suspend fun save(task: StoredDownloadTask)
    suspend fun delete(id: String)
}

class RoomDownloadTaskStore(
    private val dao: DownloadRecordDao,
) : DownloadTaskStore {
    override suspend fun loadAll(): List<StoredDownloadTask> =
        dao.loadAllWithSegments().map { it.toStoredTask() }

    override suspend fun save(task: StoredDownloadTask) {
        dao.replaceCheckpoint(
            record = task.toEntity(),
            segments = task.directSegments(),
        )
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    private fun DownloadRecordWithSegments.toStoredTask(): StoredDownloadTask {
        val planType = enumValueOrDefault(record.planType, DownloadPlanType.DIRECT)
        val checkpoint = when (planType) {
            DownloadPlanType.DIRECT -> directCheckpoint()
            DownloadPlanType.HLS -> CheckpointPayloadCodec.decode(
                planType,
                record.checkpointPayload,
            ) ?: HlsTransferCheckpoint(null, emptyList())
            DownloadPlanType.DASH -> CheckpointPayloadCodec.decode(
                planType,
                record.checkpointPayload,
            ) ?: DashTransferCheckpoint(null, emptyList())
            DownloadPlanType.AUDIO_VIDEO_MUX -> CheckpointPayloadCodec.decode(
                planType,
                record.checkpointPayload,
            ) ?: AudioVideoMuxCheckpoint()
        }
        val downloadedBytes = checkpoint.downloadedBytes
        val totalBytes = record.totalBytes?.takeIf { it >= downloadedBytes }
        val failureReason = record.lastErrorCode?.let {
            enumValueOrDefault(it, DownloadFailureReason.HTTP_STATUS)
        }
        return StoredDownloadTask(
            id = record.id,
            displayName = record.displayName,
            status = enumValueOrDefault(record.status, DownloadTaskStatus.FAILED),
            planType = planType,
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            mimeType = record.mimeType,
            destinationKind = enumValueOrDefault(
                record.destinationKind,
                DownloadDestinationKind.APP_PRIVATE,
            ),
            destinationUri = record.destinationUri,
            preferredSegmentCount = record.preferredSegmentCount.coerceIn(1, 32),
            requiresLinkRefresh = record.requiresLinkRefresh,
            failureReason = failureReason,
            checkpoint = checkpoint,
            createdAtEpochMs = record.createdAtEpochMs,
            updatedAtEpochMs = record.updatedAtEpochMs,
            failure = FailureDetailCodec.decode(failureReason, record.lastErrorDetail),
        )
    }

    private fun DownloadRecordWithSegments.directCheckpoint(): DirectTransferCheckpoint {
        val orderedSegments = segments
            .sortedBy(DownloadSegmentEntity::segmentIndex)
            .map { segment ->
                DownloadSegment(
                    index = segment.segmentIndex,
                    startByte = segment.startByte,
                    endByteInclusive = segment.endByteInclusive,
                    downloadedBytes = segment.downloadedBytes,
                )
            }
        return DirectTransferCheckpoint(
            totalBytes = record.totalBytes,
            entityTag = record.entityTag,
            lastModified = record.lastModified,
            segments = orderedSegments,
        )
    }

    private fun StoredDownloadTask.directSegments(): List<DownloadSegmentEntity> {
        val direct = checkpoint as? DirectTransferCheckpoint ?: return emptyList()
        return direct.segments.map { segment ->
            DownloadSegmentEntity(
                downloadId = id,
                segmentIndex = segment.index,
                startByte = segment.startByte,
                endByteInclusive = segment.endByteInclusive,
                downloadedBytes = segment.downloadedBytes,
            )
        }
    }

    private fun StoredDownloadTask.toEntity(): DownloadRecordEntity {
        val direct = checkpoint as? DirectTransferCheckpoint
        return DownloadRecordEntity(
            id = id,
            displayName = displayName,
            status = status.name,
            progressPercent = progressPercent,
            createdAtEpochMs = createdAtEpochMs,
            lastErrorCode = failureReason?.name,
            planType = planType.name,
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            mimeType = mimeType,
            destinationKind = destinationKind.name,
            destinationUri = destinationUri,
            entityTag = direct?.entityTag,
            lastModified = direct?.lastModified,
            preferredSegmentCount = preferredSegmentCount,
            requiresLinkRefresh = requiresLinkRefresh,
            checkpointPayload = CheckpointPayloadCodec.encode(checkpoint),
            updatedAtEpochMs = updatedAtEpochMs,
            lastErrorDetail = FailureDetailCodec.encode(
                failure?.takeIf { it.reason == failureReason },
            ),
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        value: String,
        fallback: T,
    ): T = enumValues<T>().firstOrNull { it.name == value } ?: fallback
}

/** Stream tasks whose shown bytes move between checkpoints (P41). */
internal val DownloadPlanType.showsBytesInFlight: Boolean
    get() = this == DownloadPlanType.DASH || this == DownloadPlanType.AUDIO_VIDEO_MUX

internal fun DownloadPlanType.accepts(checkpoint: TransferCheckpoint): Boolean = when (this) {
    DownloadPlanType.DIRECT -> checkpoint is DirectTransferCheckpoint
    DownloadPlanType.HLS -> checkpoint is HlsTransferCheckpoint
    DownloadPlanType.DASH -> checkpoint is DashTransferCheckpoint
    DownloadPlanType.AUDIO_VIDEO_MUX -> checkpoint is AudioVideoMuxCheckpoint
}

/**
 * The `last_error_detail` column (P21): "STAGE|HTTP status|detail", each part empty when unknown.
 * The reason stays in `last_error_code`; the detail is cleaned again when it is read.
 */
internal object FailureDetailCodec {
    fun encode(failure: DownloadFailure?): String? {
        if (failure == null) return null
        val startTimeline = DownloadFailureDetails.sanitize(failure.startTimeline)
            ?.replace(SEPARATOR, " ")
            ?.let { text -> if (text.startsWith(START_PREFIX)) text else "$START_PREFIX $text" }
        if (
            failure.stage == null &&
            failure.httpStatusCode == null &&
            failure.detail == null &&
            startTimeline == null
        ) {
            return null
        }
        val detail = DownloadFailureDetails.sanitize(failure.detail).orEmpty()
        return listOf(
            failure.stage?.name.orEmpty(),
            failure.httpStatusCode?.toString().orEmpty(),
            // The start times (P41) follow the detail; a payload saved before has none.
            startTimeline?.let { "$detail$SEPARATOR$it" } ?: detail,
        ).joinToString(SEPARATOR)
    }

    fun decode(reason: DownloadFailureReason?, payload: String?): DownloadFailure? {
        if (reason == null || payload.isNullOrBlank()) return null
        val parts = payload.take(MAX_PAYLOAD_CHARS).split(SEPARATOR, limit = 3)
        if (parts.size != 3) return null
        val stage = DownloadFailureStage.entries.firstOrNull { it.name == parts[0] }
        val httpStatusCode = parts[1].toIntOrNull()?.takeIf { it in 100..599 }
        val marker = parts[2].lastIndexOf(START_MARKER)
        val detail = DownloadFailureDetails.sanitize(
            if (marker < 0) parts[2] else parts[2].substring(0, marker),
        )
        val startTimeline = if (marker < 0) {
            null
        } else {
            DownloadFailureDetails.sanitize(parts[2].substring(marker + SEPARATOR.length))
        }
        if (stage == null && httpStatusCode == null && detail == null && startTimeline == null) {
            return null
        }
        return DownloadFailure(reason, httpStatusCode, stage, detail, startTimeline)
    }

    private const val SEPARATOR = "|"
    private const val START_PREFIX = "start:"
    private const val START_MARKER = SEPARATOR + START_PREFIX
    private const val MAX_PAYLOAD_CHARS = 512
}

internal object CheckpointPayloadCodec {
    fun encode(checkpoint: TransferCheckpoint): String? = when (checkpoint) {
        is DirectTransferCheckpoint -> null
        is HlsTransferCheckpoint -> "$VERSION\n${checkpoint.encodeStream()}"
        is DashTransferCheckpoint -> "$VERSION\n${checkpoint.encodeStream()}"
        is AudioVideoMuxCheckpoint -> listOf(
            VERSION,
            checkpoint.stage.name,
            checkpoint.videoReady.toBit(),
            checkpoint.audioReady.toBit(),
            checkpoint.video?.encodeStream() ?: NULL_TRACK,
            checkpoint.audio?.encodeStream() ?: NULL_TRACK,
        ).joinToString("\n")
    }

    fun decode(
        planType: DownloadPlanType,
        payload: String?,
    ): TransferCheckpoint? {
        val encoded = payload
            ?.takeIf { it.length <= MAX_PAYLOAD_CHARS }
            ?: return null
        return runCatching {
            val lines = encoded.split('\n')
            if (lines.firstOrNull() != VERSION) return null
            when (planType) {
                DownloadPlanType.DIRECT -> null
                DownloadPlanType.HLS -> lines.singlePayloadLine()
                    ?.decodeStream()
                    ?.let { (fingerprint, chunks) ->
                        HlsTransferCheckpoint(fingerprint, chunks)
                    }
                DownloadPlanType.DASH -> lines.singlePayloadLine()
                    ?.decodeStream()
                    ?.let { (fingerprint, chunks) ->
                        DashTransferCheckpoint(fingerprint, chunks)
                    }
                DownloadPlanType.AUDIO_VIDEO_MUX -> decodeMux(lines)
            }
        }.getOrNull()
    }

    private fun decodeMux(lines: List<String>): AudioVideoMuxCheckpoint? {
        if (lines.size != MUX_LINE_COUNT) return null
        val stage = enumValues<AudioVideoMuxStage>()
            .firstOrNull { it.name == lines[1] }
            ?: return null
        val videoReady = lines[2].toBit() ?: return null
        val audioReady = lines[3].toBit() ?: return null
        val video = lines[4].decodeOptionalStream()
        val audio = lines[5].decodeOptionalStream()
        if (lines[4] != NULL_TRACK && video == null) return null
        if (lines[5] != NULL_TRACK && audio == null) return null
        return AudioVideoMuxCheckpoint(
            video = video?.let { DashTransferCheckpoint(it.first, it.second) },
            audio = audio?.let { DashTransferCheckpoint(it.first, it.second) },
            videoReady = videoReady,
            audioReady = audioReady,
            stage = stage,
        )
    }

    private fun List<String>.singlePayloadLine(): String? =
        takeIf { it.size == 2 }?.get(1)

    private fun HlsTransferCheckpoint.encodeStream(): String =
        encodeStream(manifestFingerprint, chunks)

    private fun DashTransferCheckpoint.encodeStream(): String =
        encodeStream(manifestFingerprint, chunks)

    private fun encodeStream(
        fingerprint: String?,
        chunks: List<StreamChunkCheckpoint>,
    ): String = buildString {
        append(fingerprint ?: NULL_FINGERPRINT)
        append(':')
        chunks.forEachIndexed { index, chunk ->
            if (index > 0) append(';')
            append(chunk.index)
            append(',')
            append(chunk.downloadedBytes)
            append(',')
            append(chunk.completed.toBit())
        }
    }

    private fun String.decodeOptionalStream(): Pair<String?, List<StreamChunkCheckpoint>>? =
        if (this == NULL_TRACK) null else decodeStream()

    private fun String.decodeStream(): Pair<String?, List<StreamChunkCheckpoint>>? {
        val pieces = split(':', limit = 2)
        if (pieces.size != 2) return null
        val fingerprint = pieces[0].takeUnless { it == NULL_FINGERPRINT }
        if (fingerprint != null && !fingerprint.matches(SHA_256_PATTERN)) return null
        val chunks = if (pieces[1].isEmpty()) {
            emptyList()
        } else {
            pieces[1].split(';').map { encoded ->
                val values = encoded.split(',')
                if (values.size != 3) return null
                StreamChunkCheckpoint(
                    index = values[0].toIntOrNull() ?: return null,
                    downloadedBytes = values[1].toLongOrNull() ?: return null,
                    completed = values[2].toBit() ?: return null,
                )
            }
        }
        return fingerprint to chunks
    }

    private fun Boolean.toBit(): String = if (this) "1" else "0"

    private fun String.toBit(): Boolean? = when (this) {
        "1" -> true
        "0" -> false
        else -> null
    }

    private const val VERSION = "v1"
    private const val NULL_TRACK = "~"
    private const val NULL_FINGERPRINT = "-"
    private const val MUX_LINE_COUNT = 6
    private const val MAX_PAYLOAD_CHARS = 4 * 1024 * 1024
    private val SHA_256_PATTERN = Regex("[0-9a-f]{64}")
}