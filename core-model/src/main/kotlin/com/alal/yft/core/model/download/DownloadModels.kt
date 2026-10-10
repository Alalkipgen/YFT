package com.alal.yft.core.model.download

import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaTrackType

/** A download plan contains transient request context and must not be persisted verbatim. */
sealed interface DownloadPlan {
    val taskId: String
    val suggestedFileName: String
    val expiresAtEpochMs: Long?
}

sealed interface TransferCheckpoint {
    val downloadedBytes: Long
}

data class DirectDownloadPlan(
    override val taskId: String,
    val sourceUrl: String,
    override val suggestedFileName: String,
    val requestContext: BrowserRequestContext,
    val mimeType: String? = null,
    val expectedBytes: Long? = null,
    override val expiresAtEpochMs: Long? = null,
    val preferredSegmentCount: Int = DEFAULT_SEGMENT_COUNT,
    /**
     * The most bytes one ranged request asks for, or null to ask for the rest of a segment at
     * once. YouTube's media servers slow down large single requests, so their files are fetched
     * in bounded ranges.
     */
    val maxRequestBytes: Long? = null,
    /** Set when the downloaded AAC file is converted to MP3 before it is published (T18). */
    val mp3: Mp3Encoding? = null,
    /**
     * Set when only the sound of the downloaded MP4 is kept: its AAC track is copied into an M4A
     * before it is published (P3). Ignored when [mp3] is set, which reads the same track.
     */
    val audioOnly: Boolean = false,
) : DownloadPlan {
    init {
        require(taskId.isNotBlank())
        require(sourceUrl.isNotBlank())
        require(suggestedFileName.isNotBlank())
        require(expectedBytes == null || expectedBytes >= 0)
        require(preferredSegmentCount in 1..MAX_SEGMENT_COUNT)
        require(maxRequestBytes == null || maxRequestBytes >= MIN_REQUEST_BYTES)
    }

    override fun toString(): String = buildString {
        append("DirectDownloadPlan(taskId=")
        append(taskId)
        append(", sourceUrl=[REDACTED], suggestedFileName=")
        append(suggestedFileName)
        append(", requestContext=[REDACTED], mimeType=")
        append(mimeType)
        append(", expectedBytes=")
        append(expectedBytes)
        append(", expiresAtEpochMs=")
        append(expiresAtEpochMs)
        append(", preferredSegmentCount=")
        append(preferredSegmentCount)
        append(", maxRequestBytes=")
        append(maxRequestBytes)
        append(", mp3=")
        append(mp3)
        append(", audioOnly=")
        append(audioOnly)
        append(')')
    }

    companion object {
        const val DEFAULT_SEGMENT_COUNT = 4
        const val MAX_SEGMENT_COUNT = 32
        const val MIN_REQUEST_BYTES: Long = 64L * 1_024
    }
}

/** How a downloaded AAC file is encoded as MP3: constant [bitrateKbps], ID3 [title]. */
data class Mp3Encoding(
    val bitrateKbps: Int,
    val title: String? = null,
) {
    init {
        require(bitrateKbps in MIN_KBPS..MAX_KBPS)
    }

    companion object {
        const val MIME_TYPE = "audio/mpeg"
        const val MIN_KBPS = 32
        const val MAX_KBPS = 320
    }
}

data class HlsDownloadPlan(
    override val taskId: String,
    val playlistUrl: String,
    override val suggestedFileName: String,
    val requestContext: BrowserRequestContext,
    val mimeType: String? = null,
    override val expiresAtEpochMs: Long? = null,
) : DownloadPlan {
    init {
        require(taskId.isNotBlank())
        require(playlistUrl.isNotBlank())
        require(suggestedFileName.isNotBlank())
    }

    override fun toString(): String = buildString {
        append("HlsDownloadPlan(taskId=")
        append(taskId)
        append(", playlistUrl=[REDACTED], suggestedFileName=")
        append(suggestedFileName)
        append(", requestContext=[REDACTED], mimeType=")
        append(mimeType)
        append(", expiresAtEpochMs=")
        append(expiresAtEpochMs)
        append(')')
    }
}

/**
 * One track of a DASH presentation.
 *
 * [manifestUrl] is the DASH manifest and [representationId] the representation in it. For a
 * [wholeFile] track, such as one of YouTube's adaptive streams, there is no manifest:
 * [manifestUrl] is the media file itself and [representationId] only names the track.
 * P50: for an [hlsPlaylist] track, [manifestUrl] is an HLS media playlist (a video quality or
 * an audio rendition of a master playlist) and the track is its segments in order.
 */
data class DashDownloadPlan(
    override val taskId: String,
    val manifestUrl: String,
    val representationId: String,
    val trackType: MediaTrackType,
    override val suggestedFileName: String,
    val requestContext: BrowserRequestContext,
    val mimeType: String? = null,
    val codecs: List<String> = emptyList(),
    override val expiresAtEpochMs: Long? = null,
    val wholeFile: WholeFileTrack? = null,
    val hlsPlaylist: Boolean = false,
) : DownloadPlan {
    init {
        require(taskId.isNotBlank())
        require(manifestUrl.isNotBlank())
        require(representationId.isNotBlank())
        require(!hlsPlaylist || wholeFile == null) { "An HLS track is a playlist, not one file" }
        require(suggestedFileName.isNotBlank())
        require(codecs.none(String::isBlank))
    }

    override fun toString(): String = buildString {
        append("DashDownloadPlan(taskId=")
        append(taskId)
        append(", manifestUrl=[REDACTED], representationId=[REDACTED], trackType=")
        append(trackType)
        append(", suggestedFileName=")
        append(suggestedFileName)
        append(", requestContext=[REDACTED], mimeType=")
        append(mimeType)
        append(", codecCount=")
        append(codecs.size)
        append(", expiresAtEpochMs=")
        append(expiresAtEpochMs)
        append(", wholeFile=")
        append(wholeFile)
        append(", hlsPlaylist=")
        append(hlsPlaylist)
        append(')')
    }
}

/**
 * A track served as one media file instead of manifest segments.
 *
 * The file is fetched in byte ranges of at most [maxRequestBytes], each kept as its own chunk,
 * so an interrupted track resumes chunk by chunk and no single request is large (YouTube slows
 * down large single requests). [totalBytes] is the file's length when the source stated it;
 * otherwise the first ranged request reads it.
 */
data class WholeFileTrack(
    val totalBytes: Long? = null,
    val maxRequestBytes: Long = DEFAULT_MAX_REQUEST_BYTES,
) {
    init {
        require(totalBytes == null || totalBytes > 0)
        require(maxRequestBytes in 1..MAX_REQUEST_BYTES)
    }

    companion object {
        /** 10 MiB, the chunk size yt-dlp uses for YouTube's media servers. */
        const val DEFAULT_MAX_REQUEST_BYTES: Long = 10L * 1_024 * 1_024
        const val MAX_REQUEST_BYTES: Long = 64L * 1_024 * 1_024
    }
}

data class AudioVideoMuxDownloadPlan(
    override val taskId: String,
    val video: DashDownloadPlan,
    val audio: DashDownloadPlan,
    override val suggestedFileName: String,
    val outputMimeType: String = "video/mp4",
) : DownloadPlan {
    init {
        require(taskId.isNotBlank())
        require(video.trackType == MediaTrackType.VIDEO)
        require(audio.trackType == MediaTrackType.AUDIO)
        require(video.taskId != audio.taskId)
        require(suggestedFileName.isNotBlank())
        require(outputMimeType.isNotBlank())
    }

    override val expiresAtEpochMs: Long?
        get() = listOfNotNull(video.expiresAtEpochMs, audio.expiresAtEpochMs).minOrNull()

    override fun toString(): String = buildString {
        append("AudioVideoMuxDownloadPlan(taskId=")
        append(taskId)
        append(", video=[REDACTED], audio=[REDACTED], suggestedFileName=")
        append(suggestedFileName)
        append(", outputMimeType=")
        append(outputMimeType)
        append(", expiresAtEpochMs=")
        append(expiresAtEpochMs)
        append(')')
    }
}

enum class DownloadTaskStatus {
    QUEUED,
    PROBING,
    RUNNING,
    PAUSING,
    PAUSED,
    WAITING_FOR_NETWORK,
    NEEDS_REFRESH,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class DownloadFailureReason {
    INVALID_URL,
    EXPIRED_URL,
    DRM_PROTECTED,
    UNSUPPORTED_SOURCE,
    INCOMPATIBLE_TRACKS,
    UNSAFE_REDIRECT,
    TOO_MANY_REDIRECTS,
    AUTHENTICATION_REQUIRED,
    ACCESS_DENIED,
    NOT_FOUND,
    GONE,
    RANGE_NOT_SATISFIABLE,
    SERVER_ERROR,
    HTTP_STATUS,
    MALFORMED_RESPONSE,
    NETWORK,
    STORAGE_UNAVAILABLE,
    INSUFFICIENT_STORAGE,
    INTEGRITY_MISMATCH,
}

data class DownloadFailure(
    val reason: DownloadFailureReason,
    val httpStatusCode: Int? = null,
    /** Where the download failed (P21); null when it is not known. */
    val stage: DownloadFailureStage? = null,
    /**
     * The exception class and message behind the failure, without addresses or secrets and at
     * most [DownloadFailureDetails.MAX_CHARS] characters ([DownloadFailureDetails.of]); null
     * when there is none.
     */
    val detail: String? = null,
    /**
     * When the download's first steps happened (P41), for example "start: plan 0.0 s · length
     * 0.4 s (probe) · first byte 0.9 s · first progress 1.0 s"; times only, null when unknown.
     */
    val startTimeline: String? = null,
) {
    init {
        require(httpStatusCode == null || httpStatusCode in 100..599)
    }

    val isRetryable: Boolean
        get() = reason == DownloadFailureReason.NETWORK ||
            reason == DownloadFailureReason.SERVER_ERROR
}

/** The step of a download that failed, for the failure details (P21). */
enum class DownloadFailureStage {
    /** Asking the server for the file, or its answer. */
    CONNECT,

    /** Reading the file's bytes from the server. */
    READ_SOURCE,

    /** Creating or opening the file being written. */
    OPEN_FILE,

    /** Writing bytes into that file. */
    WRITE_FILE,

    /** Making the finished file visible, for example in Downloads. */
    PUBLISH,

    /** Merging the video and its sound into one file. */
    MERGE,

    /** Turning the sound into an MP3 or an M4A. */
    CONVERT,

    /** Checking that the file has every byte. */
    VERIFY,
}

data class DownloadSegment(
    val index: Int,
    val startByte: Long,
    val endByteInclusive: Long?,
    val downloadedBytes: Long = 0,
) {
    init {
        require(index >= 0)
        require(startByte >= 0)
        require(endByteInclusive == null || endByteInclusive >= startByte)
        require(downloadedBytes >= 0)
        require(endByteInclusive != Long.MAX_VALUE || startByte > 0) {
            "Segment length must fit in a signed Long"
        }
        val expectedLength = lengthBytes
        require(expectedLength == null || downloadedBytes <= expectedLength)
    }

    val lengthBytes: Long?
        get() = endByteInclusive?.let { end -> end - startByte + 1 }

    val remainingBytes: Long?
        get() = lengthBytes?.minus(downloadedBytes)
}

data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Long = 0,
    val activeSegmentCount: Int = 0,
) {
    init {
        require(downloadedBytes >= 0)
        require(totalBytes == null || totalBytes >= 0)
        require(totalBytes == null || downloadedBytes <= totalBytes)
        require(bytesPerSecond >= 0)
        require(activeSegmentCount >= 0)
    }

    val fraction: Double?
        get() = totalBytes
            ?.takeIf { it > 0 }
            ?.let { downloadedBytes.toDouble() / it.toDouble() }
}

data class RemoteFileMetadata(
    val finalUrl: String,
    val totalBytes: Long?,
    val supportsByteRanges: Boolean,
    val entityTag: String?,
    val lastModified: String?,
    val contentType: String?,
    val suggestedFileName: String,
) {
    init {
        require(finalUrl.isNotBlank())
        require(totalBytes == null || totalBytes >= 0)
        require(suggestedFileName.isNotBlank())
    }

    override fun toString(): String = buildString {
        append("RemoteFileMetadata(finalUrl=[REDACTED], totalBytes=")
        append(totalBytes)
        append(", supportsByteRanges=")
        append(supportsByteRanges)
        append(", entityTagPresent=")
        append(!entityTag.isNullOrBlank())
        append(", lastModifiedPresent=")
        append(!lastModified.isNullOrBlank())
        append(", contentType=")
        append(contentType)
        append(", suggestedFileName=")
        append(suggestedFileName)
        append(')')
    }
}

sealed interface DirectProbeResult {
    data class Success(val metadata: RemoteFileMetadata) : DirectProbeResult

    data class Failure(val failure: DownloadFailure) : DirectProbeResult
}

data class DirectTransferCheckpoint(
    val totalBytes: Long?,
    val entityTag: String?,
    val lastModified: String?,
    val segments: List<DownloadSegment>,
) : TransferCheckpoint {
    init {
        require(totalBytes == null || totalBytes >= 0)
        require(segments.map(DownloadSegment::index).distinct().size == segments.size)
        require(segments.zipWithNext().all { (left, right) -> left.index < right.index })
    }

    override val downloadedBytes: Long
        get() = segments.sumOf(DownloadSegment::downloadedBytes)

    override fun toString(): String = buildString {
        append("DirectTransferCheckpoint(totalBytes=")
        append(totalBytes)
        append(", entityTagPresent=")
        append(!entityTag.isNullOrBlank())
        append(", lastModifiedPresent=")
        append(!lastModified.isNullOrBlank())
        append(", segmentCount=")
        append(segments.size)
        append(", downloadedBytes=")
        append(downloadedBytes)
        append(')')
    }
}

sealed interface DirectTransferResult {
    data class Completed(
        val bytesWritten: Long,
        val checkpoint: DirectTransferCheckpoint,
    ) : DirectTransferResult

    data class Failure(
        val failure: DownloadFailure,
        val checkpoint: DirectTransferCheckpoint,
    ) : DirectTransferResult
}

data class StreamChunkCheckpoint(
    val index: Int,
    val downloadedBytes: Long,
    val completed: Boolean,
) {
    init {
        require(index >= 0)
        require(downloadedBytes >= 0)
    }
}

data class HlsTransferCheckpoint(
    val manifestFingerprint: String?,
    val chunks: List<StreamChunkCheckpoint>,
) : TransferCheckpoint {
    init {
        require(manifestFingerprint == null || manifestFingerprint.matches(SHA_256_PATTERN))
        require(chunks.map(StreamChunkCheckpoint::index).distinct().size == chunks.size)
        require(chunks.zipWithNext().all { (left, right) -> left.index < right.index })
        require(chunks.withIndex().all { (position, chunk) -> position == chunk.index })
    }

    override val downloadedBytes: Long
        get() = chunks.sumOf(StreamChunkCheckpoint::downloadedBytes)

    val completedChunkCount: Int
        get() = chunks.count(StreamChunkCheckpoint::completed)

    override fun toString(): String = buildString {
        append("HlsTransferCheckpoint(manifestFingerprintPresent=")
        append(manifestFingerprint != null)
        append(", chunkCount=")
        append(chunks.size)
        append(", completedChunkCount=")
        append(completedChunkCount)
        append(", downloadedBytes=")
        append(downloadedBytes)
        append(')')
    }

    private companion object {
        val SHA_256_PATTERN = Regex("[0-9a-f]{64}")
    }
}

sealed interface HlsTransferResult {
    data class Completed(
        val bytesWritten: Long,
        val checkpoint: HlsTransferCheckpoint,
    ) : HlsTransferResult

    data class Failure(
        val failure: DownloadFailure,
        val checkpoint: HlsTransferCheckpoint,
    ) : HlsTransferResult
}

data class DashTransferCheckpoint(
    val manifestFingerprint: String?,
    val chunks: List<StreamChunkCheckpoint>,
) : TransferCheckpoint {
    init {
        require(manifestFingerprint == null || manifestFingerprint.matches(SHA_256_PATTERN))
        require(chunks.map(StreamChunkCheckpoint::index).distinct().size == chunks.size)
        require(chunks.zipWithNext().all { (left, right) -> left.index < right.index })
        require(chunks.withIndex().all { (position, chunk) -> position == chunk.index })
    }

    override val downloadedBytes: Long
        get() = chunks.sumOf(StreamChunkCheckpoint::downloadedBytes)

    val completedChunkCount: Int
        get() = chunks.count(StreamChunkCheckpoint::completed)

    override fun toString(): String = buildString {
        append("DashTransferCheckpoint(manifestFingerprintPresent=")
        append(manifestFingerprint != null)
        append(", chunkCount=")
        append(chunks.size)
        append(", completedChunkCount=")
        append(completedChunkCount)
        append(", downloadedBytes=")
        append(downloadedBytes)
        append(')')
    }

    private companion object {
        val SHA_256_PATTERN = Regex("[0-9a-f]{64}")
    }
}

sealed interface DashTransferResult {
    data class Completed(
        val bytesWritten: Long,
        val checkpoint: DashTransferCheckpoint,
    ) : DashTransferResult

    data class Failure(
        val failure: DownloadFailure,
        val checkpoint: DashTransferCheckpoint,
    ) : DashTransferResult
}

enum class AudioVideoMuxStage {
    DOWNLOADING_TRACKS,
    READY_TO_MUX,
    MUXING,

    /** The merged file is copied into its destination and checked (P27). */
    SAVING,
    COMPLETED,
}

data class AudioVideoMuxCheckpoint(
    val video: DashTransferCheckpoint? = null,
    val audio: DashTransferCheckpoint? = null,
    val videoReady: Boolean = false,
    val audioReady: Boolean = false,
    val stage: AudioVideoMuxStage = AudioVideoMuxStage.DOWNLOADING_TRACKS,
    /**
     * How far the step after the download is (P27), out of [stepTotal]: while [stage] is
     * [AudioVideoMuxStage.MUXING] the sample time written of the tracks' duration (or the bytes
     * read of the tracks' bytes when the duration is unknown), while it is
     * [AudioVideoMuxStage.SAVING] the bytes saved of the merged file. Kept in memory only: a saved
     * checkpoint does not keep it.
     */
    val stepDone: Long = 0,
    val stepTotal: Long? = null,
) : TransferCheckpoint {
    init {
        require(!videoReady || video.isComplete())
        require(!audioReady || audio.isComplete())
        if (stage != AudioVideoMuxStage.DOWNLOADING_TRACKS) {
            require(videoReady && audioReady)
        }
        require(stepDone >= 0)
        require(stepTotal == null || stepTotal >= 0)
    }

    override val downloadedBytes: Long
        get() = (video?.downloadedBytes ?: 0) + (audio?.downloadedBytes ?: 0)

    /** [stepDone] of [stepTotal] as 0–100, or null while the step's size is unknown. */
    val stepPercent: Int?
        get() = stepTotal
            ?.takeIf { it > 0 }
            ?.let { total -> (stepDone.toDouble() * 100.0 / total.toDouble()).toInt() }
            ?.coerceIn(0, 100)

    override fun toString(): String = buildString {
        append("AudioVideoMuxCheckpoint(videoPresent=")
        append(video != null)
        append(", audioPresent=")
        append(audio != null)
        append(", videoReady=")
        append(videoReady)
        append(", audioReady=")
        append(audioReady)
        append(", stage=")
        append(stage)
        append(", stepPercent=")
        append(stepPercent)
        append(", downloadedBytes=")
        append(downloadedBytes)
        append(')')
    }

    private fun DashTransferCheckpoint?.isComplete(): Boolean =
        this != null && chunks.isNotEmpty() && completedChunkCount == chunks.size
}

sealed interface AudioVideoMuxResult {
    data class Completed(
        val bytesWritten: Long,
        val checkpoint: AudioVideoMuxCheckpoint,
    ) : AudioVideoMuxResult

    data class Failure(
        val failure: DownloadFailure,
        val checkpoint: AudioVideoMuxCheckpoint,
    ) : AudioVideoMuxResult
}
