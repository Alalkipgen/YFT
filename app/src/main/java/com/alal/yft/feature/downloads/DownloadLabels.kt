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

/** Track E: a download whose link died while its page is looked up again for the same file. */
internal const val RENEWING_LINK_LABEL = "Getting a fresh link\u2026"

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

/** "Delete file" on a finished download (P42), beside "Remove from list". */
internal const val DELETE_FILE_LABEL = "Delete file"
internal const val DELETE_FILE_TITLE = "Delete this file?"

internal fun deleteFileTag(id: String): String = "download-menu-delete-file-$id"

/** The question's text (P42): the file goes from Download/YFT and from the list, for good. */
internal fun deleteFileQuestionText(fileName: String): String =
    "\u201c$fileName\u201d will be removed from Download/YFT and from this list. " +
        "This can't be undone."

/** Item 5 (v1.0.2): "Delete 3 files?" for the downloads picked in select mode. */
internal fun deleteFilesTitle(count: Int): String =
    if (count == 1) DELETE_FILE_TITLE else "Delete $count files?"

internal fun deleteFilesQuestionText(count: Int): String =
    "These $count files will be removed from Download/YFT and from this list. " +
        "This can't be undone."

/** Item 5 (v1.0.2): select mode, started with a long press on a finished download. */
internal const val SELECT_LABEL = "Select"
internal const val SELECT_ALL_LABEL = "Select all"

internal fun selectedCountLabel(count: Int): String = "$count selected"

/** The select bar's "Delete file" ("Delete files" for more than one). */
internal fun deleteFilesLabel(count: Int): String =
    if (count == 1) DELETE_FILE_LABEL else "Delete files"

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
 * "720p · 96 MB" instead, as in the Library. v1.0.2 (item 50): an unfinished video whose picked
 * quality the app holds ([DownloadRowUiState.pickedQuality]) names it with its stream, else its
 * format: "1080p · HLS", "1080p · MP4"; the finished row then names the file's own quality.
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
    val pickedQuality = row.pickedQuality?.takeIf {
        row.status != DownloadTaskStatus.COMPLETED && !row.isAudio
    }
    val parts = when {
        finishedQuality != null -> listOf(finishedQuality)
        pickedQuality != null -> listOfNotNull(pickedQuality, stream ?: row.format)
        else -> listOfNotNull(stream, row.format)
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
    row.stage?.let { stage -> return mergeStageLabel(stage, row.destinationKind) }
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

/**
 * "Merging audio and video · 45%", "Saving to Download/YFT · 80%": the step after a merged
 * download's tracks are in (P27), with its percent when known.
 */
internal fun mergeStageLabel(stage: DownloadStage, destination: DownloadDestinationKind): String {
    val step = when (stage.step) {
        DownloadStage.Step.MERGING -> "Merging audio and video"
        DownloadStage.Step.SAVING -> when (destination) {
            DownloadDestinationKind.MEDIA_STORE ->
                "Saving to ${LibraryLocation.SHARED_DOWNLOADS.label}"
            DownloadDestinationKind.APP_PRIVATE -> "Saving to ${LibraryLocation.APP_STORAGE.label}"
            DownloadDestinationKind.SAF_DOCUMENT -> "Saving to the chosen folder"
        }
    }
    return listOfNotNull(step, stage.percent?.let { "$it%" }).joinToString(" · ")
}

/** "61 of 96 MB", or "800 KB of 96 MB" when the units differ. */
internal fun amountOf(downloaded: Long, total: Long): String {
    val done = YftFormat.bytes(downloaded)
    val all = YftFormat.bytes(total)
    val sameUnit = done.substringAfter(' ') == all.substringAfter(' ')
    return if (sameUnit) "${done.substringBefore(' ')} of $all" else "$done of $all"
}

/**
 * The speed on the cards and in the notification (P34, G8): below 1,024 KB/s whole kilobytes
 * ("850 KB/s", "1023 KB/s"), from there one decimal ("1.0 MB/s", "12.3 MB/s"), 1,024-based.
 */
internal fun speedLabel(bytesPerSecond: Long): String {
    if (bytesPerSecond < BYTES_PER_KB) return "${bytesPerSecond.coerceAtLeast(0L)} B/s"
    val kilobytes = bytesPerSecond / BYTES_PER_KB
    if (kilobytes < BYTES_PER_KB) return "$kilobytes KB/s"
    var value = bytesPerSecond / (BYTES_PER_KB * BYTES_PER_KB).toDouble()
    var unit = 0
    while (value >= BYTES_PER_KB && unit < FAST_UNITS.lastIndex) {
        value /= BYTES_PER_KB
        unit++
    }
    return String.format(Locale.US, "%.1f %s/s", value, FAST_UNITS[unit])
}

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

    DownloadTaskStatus.NEEDS_REFRESH -> DownloadStatusChip(
        text = if (row.isYouTubeRefusal()) "YouTube refused" else "Link expired",
        tone = YftStatusTone.Failed,
    )

    DownloadTaskStatus.CANCELLED -> DownloadStatusChip("Cancelled", YftStatusTone.Neutral)
    DownloadTaskStatus.RENEWING_LINK ->
        DownloadStatusChip(RENEWING_LINK_LABEL, YftStatusTone.Active, YftIcons.Refresh)
    else -> null
}

/**
 * "Download/YFT · 18 GB free", or the location alone when the volume cannot be measured; a
 * chosen folder (v1.0.2 item 28) by its path, M1: "Movies/Test · 18 GB free" on the phone's
 * storage, "Movies" alone on a memory card or in a cloud app.
 */
internal fun storageLabel(storage: DownloadStorageSummary): String {
    val location = when (storage.location) {
        DownloadLocation.SHARED_DOWNLOADS -> LibraryLocation.SHARED_DOWNLOADS.label
        DownloadLocation.APP_STORAGE -> LibraryLocation.APP_STORAGE.label
        DownloadLocation.CHOSEN_FOLDER ->
            storage.folderName ?: LibraryLocation.CHOSEN_FOLDER.label
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
    DownloadFailureReason.LIVE_NOT_READY -> "YouTube still preparing this live video, try later"
    DownloadFailureReason.YOUTUBE_REFUSED -> "YouTube refused the download"
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

/**
 * v1.0.2 item 28: a row's notice when the chosen folder could not be used, so the file went to
 * the default place ([kind]) instead.
 */
internal fun folderUnavailableNotice(kind: DownloadDestinationKind): String {
    val place = when (kind) {
        DownloadDestinationKind.APP_PRIVATE -> "app storage"
        DownloadDestinationKind.MEDIA_STORE, DownloadDestinationKind.SAF_DOCUMENT ->
            LibraryLocation.SHARED_DOWNLOADS.label
    }
    return "The chosen folder can't be used, so this file is saved in $place."
}

/** Where the file is written: Downloads (MediaStore), a chosen folder (SAF) or the app. */
internal fun destinationKindLabel(kind: DownloadDestinationKind): String = when (kind) {
    DownloadDestinationKind.MEDIA_STORE -> "Downloads (MediaStore)"
    DownloadDestinationKind.SAF_DOCUMENT -> "Chosen folder (SAF)"
    DownloadDestinationKind.APP_PRIVATE -> "App storage"
}

/**
 * The text of the failure Details dialog and of Copy details (P21): reason, stage, HTTP status,
 * detail, start times (P41), download type, destination and versions. It names no file, link
 * or address, and the detail is cleaned once more before it is shown. v1.0.2 item 26: a merged
 * download's last line is [av1Decoder] ([av1DecoderLine]) when one is given.
 */
internal fun failureDetailsText(
    row: DownloadRowUiState,
    appVersion: String,
    androidRelease: String,
    sdkInt: Int,
    av1Decoder: String? = null,
): String {
    val failure = row.failure
    val reason = (failure?.reason ?: row.failureReason)?.let { reason ->
        "${failureLabel(reason).replaceFirstChar(Char::uppercaseChar)} (${reason.name})"
    }
    val stage = failure?.stage?.let { stage -> "${stageLabel(stage)} (${stage.name})" }
    return listOfNotNull(
        "YFT download failure",
        "Reason: ${reason ?: unknownReason(row)}",
        "What to do: $AUDIO_FROM_VIDEO_HINT".takeIf { row.isAudioFromIncompatibleSound() },
        "What to do: $YOUTUBE_REFUSED_HINT".takeIf { row.isYouTubeRefusal() },
        "Stage: ${stage ?: UNKNOWN}",
        "HTTP status: ${failure?.httpStatusCode ?: UNKNOWN}",
        "Detail: ${DownloadFailureDetails.sanitize(failure?.detail) ?: UNKNOWN}",
        // How long the start took (P41): "Start: plan 0.0 s · length 0.4 s (probe) · …".
        DownloadFailureDetails.sanitize(failure?.startTimeline)
            ?.removePrefix("start:")
            ?.trim()
            ?.let { "Start: $it" },
        *renewalLines(row).toTypedArray(),
        "Download type: ${planKindLabel(row)}",
        "Saved to: ${destinationKindLabel(row.destinationKind)}",
        "App: YFT ${appVersion.ifBlank { UNKNOWN }}",
        "Android: ${androidRelease.ifBlank { UNKNOWN }} (API $sdkInt)",
        av1DecoderLine(row, av1Decoder),
    ).joinToString("\n")
}

/**
 * v1.0.2 item 26: whether the phone has an AV1 decoder ("AV1 decoder: hardware (…)", "software
 * (…)" or "none"), on a merged download's Details only, and only while AV1 merges are on (a debug
 * build's test switch: the caller passes [av1Decoder], else null; the release passes none).
 */
internal fun av1DecoderLine(row: DownloadRowUiState, av1Decoder: String?): String? =
    av1Decoder?.takeIf { row.planType == DownloadPlanType.AUDIO_VIDEO_MUX }

/**
 * F6: a download that needs a fresh link shows its details too, like a failed one: its link
 * either failed (the reason and the link's facts) or was never tried ([unknownReason]).
 */
internal val DownloadRowUiState.showsFailureDetails: Boolean
    get() = status == DownloadTaskStatus.FAILED || status == DownloadTaskStatus.NEEDS_REFRESH

/**
 * Track E: a download that got a fresh link shows Details too while it runs, is paused or after
 * it completed: how each fresh link went on ([downloadDetailsText]).
 */
internal val DownloadRowUiState.showsDetails: Boolean
    get() = showsFailureDetails || renewalLines.isNotEmpty()

/** The Details text: a failure's ([failureDetailsText]), else the fresh links' (Track E). */
internal fun downloadDetailsText(
    row: DownloadRowUiState,
    appVersion: String,
    androidRelease: String,
    sdkInt: Int,
    av1Decoder: String? = null,
): String {
    if (row.showsFailureDetails) {
        return failureDetailsText(row, appVersion, androidRelease, sdkInt, av1Decoder)
    }
    return (
        listOf("YFT download", "Status: ${row.status.name}") +
            renewalLines(row) +
            listOfNotNull(
                "Download type: ${planKindLabel(row)}",
                "Saved to: ${destinationKindLabel(row.destinationKind)}",
                "App: YFT ${appVersion.ifBlank { UNKNOWN }}",
                "Android: ${androidRelease.ifBlank { UNKNOWN }} (API $sdkInt)",
                av1DecoderLine(row, av1Decoder),
            )
        ).joinToString("\n")
}

/** Track E: one line per fresh link, cleaned once more like a failure's detail. */
private fun renewalLines(row: DownloadRowUiState): List<String> =
    row.renewalLines.mapNotNull(DownloadFailureDetails::sanitize)

/**
 * A download that needs a fresh link without a recorded failure was never tried with its link:
 * the app keeps a download's link only while it runs, so after a restart the link is gone.
 */
private fun unknownReason(row: DownloadRowUiState): String =
    if (row.status == DownloadTaskStatus.NEEDS_REFRESH) NOT_TRIED_REASON else UNKNOWN

/** Track E (E-D6): a "Link expired" row whose page YFT can look up again. */
internal const val RENEWABLE_LINK_NOTE =
    "Tap Retry to get a fresh link, or reopen the page in the browser."

/**
 * A "Link expired" row YFT cannot renew, or (v1.0.2) one whose page, read again, had no player
 * data (a notice, a check): Retry would read the same page.
 */
internal const val REOPEN_PAGE_NOTE = "Reopen the page in the browser to get a fresh link."

internal const val NOT_TRIED_REASON =
    "Link not tried (the app was closed or restarted, and YFT keeps a download's link only " +
        "while it runs)"

/**
 * An M4A or MP3 made from a video whose sound is not AAC fails as INCOMPATIBLE_TRACKS: the
 * video itself still downloads, so say that instead of "Incompatible tracks".
 */
internal fun DownloadRowUiState.isAudioFromIncompatibleSound(): Boolean =
    isAudio && (failure?.reason ?: failureReason) == DownloadFailureReason.INCOMPATIBLE_TRACKS

internal const val AUDIO_FROM_VIDEO_HINT =
    "This video's sound can't be saved as audio. Download the video instead."

/**
 * Item 7 Track B1: YouTube refused the download while its link still had time left
 * ([DownloadFailureReason.YOUTUBE_REFUSED]): the link did not expire, so the row must not say
 * "Link expired" or that reopening the page gets a working one.
 */
internal fun DownloadRowUiState.isYouTubeRefusal(): Boolean =
    (failure?.reason ?: failureReason) == DownloadFailureReason.YOUTUBE_REFUSED

/** Item 7 Track B3: offered on a [isYouTubeRefusal] row, on its card and in its menu. */
internal const val RESET_YOUTUBE_LABEL = "Reset YouTube session"

internal const val YOUTUBE_REFUSED_HINT =
    "YouTube refused this download while its link still had time left, so reopening the page " +
        "with the same session may not help. Try again later, or tap $RESET_YOUTUBE_LABEL (it " +
        "signs you out of YouTube in YFT only), then reopen the page. Still refused: Clear " +
        "browsing data in Settings (it signs you out of every site in YFT)."

internal const val YOUTUBE_RESET_QUESTION =
    "YFT's browser forgets YouTube's cookies, site data and remembered YouTube lookups, so you " +
        "are signed out of YouTube in YFT. Other sites, Google's included, keep their sign-ins, " +
        "and downloads and files stay. Then reopen the video's page and download again."

private const val UNKNOWN = "\u2014"
private const val BYTES_PER_KB = 1_024L
private val FAST_UNITS = listOf("MB", "GB")
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val MAX_TIME_LEFT_SECONDS = 7L * 24 * 60 * 60
