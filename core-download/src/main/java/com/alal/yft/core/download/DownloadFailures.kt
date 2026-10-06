package com.alal.yft.core.download

import com.alal.yft.core.model.download.DownloadFailure
import com.alal.yft.core.model.download.DownloadFailureDetails
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException

/**
 * The storage reason of an error from the file side: [DownloadFailureReason.INSUFFICIENT_STORAGE]
 * when it, or one of its causes, says the device is full, otherwise
 * [DownloadFailureReason.STORAGE_UNAVAILABLE].
 */
internal fun Throwable.toStorageReason(): DownloadFailureReason {
    val text = generateSequence(this) { error -> error.cause?.takeIf { it !== error } }
        .take(MAX_CAUSES)
        .joinToString(" ") { it.message.orEmpty() }
        .lowercase(Locale.US)
    return if ("enospc" in text || "no space left" in text || "disk full" in text) {
        DownloadFailureReason.INSUFFICIENT_STORAGE
    } else {
        DownloadFailureReason.STORAGE_UNAVAILABLE
    }
}

/** A failure of the file being written at [stage], with this error's class and message. */
internal fun Throwable.toStorageFailure(stage: DownloadFailureStage?): DownloadFailure =
    DownloadFailure(
        reason = toStorageReason(),
        stage = stage,
        detail = DownloadFailureDetails.of(this),
    )

/** A failure of the connection or the source at [stage], with this error's class and message. */
internal fun Throwable.toNetworkFailure(stage: DownloadFailureStage?): DownloadFailure =
    DownloadFailure(
        reason = DownloadFailureReason.NETWORK,
        stage = stage,
        detail = DownloadFailureDetails.of(this),
    )

/**
 * Whether an error thrown by a destination or a local file call means the storage failed: an
 * I/O error, a destination used after it was discarded or published, or a revoked permission.
 * A cancellation is an [IllegalStateException] too, and never a storage failure.
 */
internal fun Throwable.isStorageError(): Boolean =
    this !is CancellationException &&
        (this is IOException || this is IllegalStateException || this is SecurityException)

private const val MAX_CAUSES = 4
