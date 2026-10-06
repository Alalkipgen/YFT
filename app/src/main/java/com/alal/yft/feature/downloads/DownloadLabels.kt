package com.alal.yft.feature.downloads

import androidx.annotation.DrawableRes
import com.alal.yft.core.download.DownloadDestinationKind
import com.alal.yft.core.download.DownloadPlanType
import com.alal.yft.core.model.download.DownloadFailureDetails
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.DownloadFailureStage
import com.alal.yft.core.model.download.Mp3Encoding
import com.alal.yft.core.model.download.DownloadTaskStatus
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.download.policy.TransferNetworkState
import com.alal.yft.feature.library.LibraryLocation
import com.alal.yft.ui.components.YftStatusTone
import com.alal.yft.ui.format.YftFormat
import com.alal.yft.ui.theme.YftIcons
import java.util.Locale

/** Statuses drawn with a progress bar; the others show a status chip or the finished row. */
internal val PROGRESS_STATUSES = setOf(
    DownloadTaskStatus.PROBING,
    DownloadTaskStatus.RUNNING,
    DownloadTaskStatus.PAUSING,
    DownloadTaskStatus.PAUSED,
    DownloadTaskStatus.VERIFYING,
)

/** A status pill on a card that has no progress bar. */
internal data class DownloadStatusChip(
    val text: String,
    val tone: YftStatusTone,
    @DrawableRes val icon: Int? = null,
)

internal fun actionTag(action: DownloadAction, id: String): String =
    "download-action-${action.name.lowercase(Locale.US)}-$id"

internal fun menuTag(action: DownloadAction, id: String): String =
    "download-menu-${action.name.lowercase(Locale.US)}-$id"

/** Visible button text. */
internal fun actionLabel(action: DownloadAction): String = when (action) {
    DownloadAction.PAUSE -> "Pause"
    DownloadAction.RESUME -> "Resume"
    DownloadAction.RETRY -> "Retry"
    DownloadAction.CANCEL -> "Cancel"
    DownloadAction.DELETE -> "Remove"
}

/** Menu and accessibility action text; removing keeps the finished file. */
internal fun menuLabel(action: DownloadAction): String = when (action) {
    DownloadAction.DELETE -> "Remove from list"
    DownloadAction.CANCEL -> "Cancel download"
    else -> actionLabel(action)
}

/**
 * "MP4", "HLS · MP4", "M4A · 12 MB": what the file is, and its size once it is not moving. A
 * finished video whose picture size has been read from the file ([quality]) reads
 * "720p · 96 MB" instead, as in the Library.
 */
internal fun metaLabel(row: DownloadRowUiState, quality: String? = null): String {
    val stream = when (row.planType) {
        DownloadPlanType.HLS -> "HLS"
        DownloadPlanType.DASH -> "DASH"
        DownloadPlanType.DIRECT, DownloadPlanType.AUDIO_VIDEO_MUX -> null
    }
    val size = when {
        row.status in PROGRESS_STATUSES -> null
        row.status == DownloadTaskStatus.COMPLETED ->
            row.totalBytes ?: row.downloadedBytes.takeIf { it > 0L }
        else -> row.totalBytes
    }
    val finishedQuality = quality?.takeIf {
        row.status == DownloadTaskStatus.COMPLETED && !row.isAudio
    }
    val parts = if (finishedQuality != null) {
        listOf(finishedQuality)
    } else {
        listOfNotNull(stream, row.format)
    }
    return (parts + listOfNotNull(size?.let(YftFormat::bytes)))
        .joinToString(" · ")
        .ifEmpty { planLabel(row.planType) }
}

/** "64%" next to the meta line while the bar is determinate. */
internal fun percentLabel(row: DownloadRowUiState): String? =
    row.progressFraction
        ?.takeIf { row.status in PROGRESS_STATUSES }
        ?.let { "${row.progressPercent}%" }

/**
 * The line under the bar: "61 of 96 MB · 2.4 MB/s · 15 s left", "Paused", "Checking source".
 * Without [withSpeed] the speed is left out, for cards too narrow for the whole line.
 */
internal fun progressDetail(row: DownloadRowUiState, withSpeed: Boolean = true): String {
    val amount = row.totalBytes
        ?.let { amountOf(row.downloadedBytes, it) }
        ?: row.downloadedBytes.takeIf { it > 0L }?.let(YftFormat::bytes)
    return when (row.status) {
        DownloadTaskStatus.PROBING -> "Checking source"
        DownloadTaskStatus.VERIFYING -> "Verifying file"
        DownloadTaskStatus.PAUSING -> "Pausing"
        DownloadTaskStatus.PAUSED -> listOfNotNull(amount, "Paused").joinToString(" · ")
        else -> listOfNotNull(
            if (row.totalBytes == null) "Downloading" else null,
            amount,
            row.bytesPerSecond?.takeIf { withSpeed }?.let(::speedLabel),
            row.secondsLeft?.let(::timeLeftLabel),
        ).joinToString(" · ")
    }
}

/** "61 of 96 MB", or "800 KB of 96 MB" when the units differ. */
internal fun amountOf(downloaded: Long, total: Long): String {
    val done = YftFormat.bytes(downloaded)
    val all = YftFormat.bytes(total)
    val sameUnit = done.substringAfter(' ') == all.substringAfter(' ')
    return if (sameUnit) "${done.substringBefore(' ')} of $all" else "$done of $all"
}

internal fun speedLabel(bytesPerSecond: Long): String = "${YftFormat.bytes(bytesPerSecond)}/s"

/** "15 s left", "3 min left", "1 h 5 min left"; nothing past a week, where it means little. */
internal fun timeLeftLabel(seconds: Long): String? {
    if (seconds < 0L || seconds > MAX_TIME_LEFT_SECONDS) return null
    if (seconds < SECONDS_PER_MINUTE) return "${seconds.coerceAtLeast(1L)} s left"
    val minutes = (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
    if (minutes < MINUTES_PER_HOUR) return "$minutes min left"
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return if (rest == 0L) "$hours h left" else "$hours h $rest min left"
}

/** The pill on a card without a progress bar, or null for moving and finished downloads. */
internal fun statusChip(
    row: DownloadRowUiState,
    network: TransferNetworkState,
): DownloadStatusChip? = when (row.status) {
    DownloadTaskStatus.QUEUED ->
        DownloadStatusChip("Queued", YftStatusTone.Neutral, YftIcons.Schedule)

    DownloadTaskStatus.WAITING_FOR_NETWORK -> when (network) {
        TransferNetworkState.WAITING_FOR_UNMETERED ->
            DownloadStatusChip("Waiting for Wi-Fi", YftStatusTone.Waiting, YftIcons.Wifi)

        TransferNetworkState.OFFLINE ->
            DownloadStatusChip("Waiting for network", YftStatusTone.Waiting, YftIcons.WifiOff)

        TransferNetworkState.ALLOWED ->
            DownloadStatusChip("Waiting for network", YftStatusTone.Waiting, YftIcons.Wifi)
    }

    DownloadTaskStatus.FAILED -> DownloadStatusChip(
        text = when {
            row.isAudioFromIncompatibleSound() -> "Failed · Sound can't be saved as audio"
            else -> row.failureReason
                ?.let { "Failed · ${failureLabel(it).replaceFirstChar(Char::uppercaseChar)}" }
                ?: "Failed"
        },
        tone = YftStatusTone.Failed,
    )

    DownloadTaskStatus.NEEDS_REFRESH -> DownloadStatusChip("Link expired", YftStatusTone.Failed)
    DownloadTaskStatus.CANCELLED -> DownloadStatusChip("Cancelled", YftStatusTone.Neutral)
    else -> null
}

/** "Download/YFT · 18 GB free", or the location alone when the volume cannot be measured. */
internal fun storageLabel(storage: DownloadStorageSummary): String {
    val location = when (storage.location) {
        DownloadLocation.SHARED_DOWNLOADS -> LibraryLocation.SHARED_DOWNLOADS.label
        DownloadLocation.APP_STORAGE -> LibraryLocation.APP_STORAGE.label
    }
    return storage.freeBytes
        ?.let { "$location · ${YftFormat.bytes(it)} free" }
        ?: location
}

internal fun planLabel(planType: DownloadPlanType): String = when (planType) {
    DownloadPlanType.DIRECT -> "Direct file"
    DownloadPlanType.HLS -> "HLS stream"
    DownloadPlanType.DASH -> "DASH stream"
    DownloadPlanType.AUDIO_VIDEO_MUX -> "Audio + video"
}

internal fun failureLabel(reason: DownloadFailureReason): String = when (reason) {
    DownloadFailureReason.INVALID_URL -> "invalid link"
    DownloadFailureReason.EXPIRED_URL -> "link expired"
    DownloadFailureReason.DRM_PROTECTED -> "protected content"
    DownloadFailureReason.UNSUPPORTED_SOURCE -> "unsupported source"
    DownloadFailureReason.INCOMPATIBLE_TRACKS -> "incompatible tracks"
    DownloadFailureReason.UNSAFE_REDIRECT -> "unsafe redirect"
    DownloadFailureReason.TOO_MANY_REDIRECTS -> "too many redirects"
    DownloadFailureReason.AUTHENTICATION_REQUIRED -> "sign-in required"
    DownloadFailureReason.ACCESS_DENIED -> "access denied"
    DownloadFailureReason.NOT_FOUND -> "not found"
    DownloadFailureReason.GONE -> "no longer available"
    DownloadFailureReason.RANGE_NOT_SATISFIABLE -> "resume not supported"
    DownloadFailureReason.SERVER_ERROR -> "server error"
    DownloadFailureReason.HTTP_STATUS -> "unexpected HTTP status"
    DownloadFailureReason.MALFORMED_RESPONSE -> "malformed response"
    DownloadFailureReason.NETWORK -> "network error"
    DownloadFailureReason.STORAGE_UNAVAILABLE -> "storage unavailable"
    DownloadFailureReason.INSUFFICIENT_STORAGE -> "not enough storage"
    DownloadFailureReason.INTEGRITY_MISMATCH -> "integrity mismatch"
}

/** The step a download failed at, for the failure details (P21). */
internal fun stageLabel(stage: DownloadFailureStage): String = when (stage) {
    DownloadFailureStage.CONNECT -> "Connecting to the server"
    DownloadFailureStage.READ_SOURCE -> "Reading from the server"
    DownloadFailureStage.OPEN_FILE -> "Opening the file"
    DownloadFailureStage.WRITE_FILE -> "Writing the file"
    DownloadFailureStage.PUBLISH -> "Saving the finished file"
    DownloadFailureStage.MERGE -> "Merging video and sound"
    DownloadFailureStage.CONVERT -> "Converting the sound"
    DownloadFailureStage.VERIFY -> "Checking the file"
}

/** "MP3", "Direct", "HLS", "DASH", "Merge (audio + video)": how the file was fetched. */
internal fun planKindLabel(row: DownloadRowUiState): String = when (row.planType) {
    DownloadPlanType.DIRECT -> if (row.mimeType == Mp3Encoding.MIME_TYPE) "MP3" else "Direct"
    DownloadPlanType.HLS -> "HLS"
    DownloadPlanType.DASH -> "DASH"
    DownloadPlanType.AUDIO_VIDEO_MUX -> "Merge (audio + video)"
}

/** Where the file is written: Downloads (MediaStore), a chosen folder (SAF) or the app. */
internal fun destinationKindLabel(kind: DownloadDestinationKind): String = when (kind) {
    DownloadDestinationKind.MEDIA_STORE -> "Downloads (MediaStore)"
    DownloadDestinationKind.SAF_DOCUMENT -> "Chosen folder (SAF)"
    DownloadDestinationKind.APP_PRIVATE -> "App storage"
}

/**
 * The text of the failure Details dialog and of Copy details (P21): reason, stage, HTTP status,
 * detail, download type, destination and versions. It names no file, link or address, and the
 * detail is cleaned once more before it is shown.
 */
internal fun failureDetailsText(
    row: DownloadRowUiState,
    appVersion: String,
    androidRelease: String,
    sdkInt: Int,
): String {
    val failure = row.failure
    val reason = (failure?.reason ?: row.failureReason)?.let { reason ->
        "${failureLabel(reason).replaceFirstChar(Char::uppercaseChar)} (${reason.name})"
    }
    val stage = failure?.stage?.let { stage -> "${stageLabel(stage)} (${stage.name})" }
    return listOfNotNull(
        "YFT download failure",
        "Reason: ${reason ?: UNKNOWN}",
        "What to do: $AUDIO_FROM_VIDEO_HINT".takeIf { row.isAudioFromIncompatibleSound() },
        "Stage: ${stage ?: UNKNOWN}",
        "HTTP status: ${failure?.httpStatusCode ?: UNKNOWN}",
        "Detail: ${DownloadFailureDetails.sanitize(failure?.detail) ?: UNKNOWN}",
        "Download type: ${planKindLabel(row)}",
        "Saved to: ${destinationKindLabel(row.destinationKind)}",
        "App: YFT ${appVersion.ifBlank { UNKNOWN }}",
        "Android: ${androidRelease.ifBlank { UNKNOWN }} (API $sdkInt)",
    ).joinToString("\n")
}

/**
 * An M4A or MP3 made from a video whose sound is not AAC fails as INCOMPATIBLE_TRACKS: the
 * video itself still downloads, so say that instead of "Incompatible tracks".
 */
internal fun DownloadRowUiState.isAudioFromIncompatibleSound(): Boolean =
    isAudio && (failure?.reason ?: failureReason) == DownloadFailureReason.INCOMPATIBLE_TRACKS

internal const val AUDIO_FROM_VIDEO_HINT =
    "This video's sound can't be saved as audio. Download the video instead."

private const val UNKNOWN = "\u2014"
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val MAX_TIME_LEFT_SECONDS = 7L * 24 * 60 * 60
