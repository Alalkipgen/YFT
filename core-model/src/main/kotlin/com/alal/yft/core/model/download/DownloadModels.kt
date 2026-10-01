package com.alal.yft.core.model.download

import com.alal.yft.core.model.media.BrowserRequestContext

/** A download plan contains transient request context and must not be persisted verbatim. */
sealed interface DownloadPlan {
    val taskId: String
    val suggestedFileName: String
    val expiresAtEpochMs: Long?
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
) : DownloadPlan {
    init {
        require(taskId.isNotBlank())
        require(sourceUrl.isNotBlank())
        require(suggestedFileName.isNotBlank())
        require(expectedBytes == null || expectedBytes >= 0)
        require(preferredSegmentCount in 1..MAX_SEGMENT_COUNT)
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
        append(')')
    }

    companion object {
        const val DEFAULT_SEGMENT_COUNT = 4
        const val MAX_SEGMENT_COUNT = 32
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
) {
    init {
        require(httpStatusCode == null || httpStatusCode in 100..599)
    }

    val isRetryable: Boolean
        get() = reason == DownloadFailureReason.NETWORK ||
            reason == DownloadFailureReason.SERVER_ERROR
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