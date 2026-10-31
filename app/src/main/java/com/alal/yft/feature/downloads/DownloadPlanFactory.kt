package com.alal.yft.feature.downloads

import android.os.Build
import com.alal.yft.core.download.AudioVideoMuxCompatibility
import com.alal.yft.core.download.MuxCompatibility
import com.alal.yft.core.model.download.AudioVideoMuxDownloadPlan
import com.alal.yft.core.model.download.DashDownloadPlan
import com.alal.yft.core.model.download.DirectDownloadPlan
import com.alal.yft.core.model.download.DownloadFailureReason
import com.alal.yft.core.model.download.FileNames
import com.alal.yft.core.model.download.HlsDownloadPlan
import com.alal.yft.core.model.download.Mp3Encoding
import com.alal.yft.core.model.download.WholeFileTrack
import com.alal.yft.core.model.media.CompanionAudio
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.PlausibleSize
import com.alal.yft.core.model.media.VariantSupport
import com.alal.yft.detection.Av1MergeSwitch
import java.net.URI
import java.util.Locale

/** A typed plan the download queue can accept, paired with its non-sensitive output naming. */
sealed interface DownloadRequest {
    val fileName: String
    val mimeType: String?

    data class Direct(
        val plan: DirectDownloadPlan,
        override val fileName: String,
        override val mimeType: String?,
    ) : DownloadRequest

    data class Hls(
        val plan: HlsDownloadPlan,
        override val fileName: String,
        override val mimeType: String?,
    ) : DownloadRequest

    data class Dash(
        val plan: DashDownloadPlan,
        override val fileName: String,
        override val mimeType: String?,
    ) : DownloadRequest

    /** A video-only file and its companion audio, downloaded and merged into one MP4. */
    data class Mux(
        val plan: AudioVideoMuxDownloadPlan,
        override val fileName: String,
        override val mimeType: String?,
    ) : DownloadRequest
}

sealed interface DownloadPlanResult {
    data class Ready(val request: DownloadRequest) : DownloadPlanResult

    data class Rejected(
        val reason: DownloadFailureReason,
        val message: String,
    ) : DownloadPlanResult
}

/**
 * Converts an already-resolved preview selection into a typed download plan.
 *
 * This stays pure so the sensitive playback URL is only copied into the plan, never logged or
 * persisted here. Unsupported or expired selections are rejected explicitly instead of being
 * downgraded to a guess.
 */
object DownloadPlanFactory {
    const val MAX_FILE_NAME_LENGTH = 96
    private const val FALLBACK_NAME = "yft-download"
    private val HLS_MIME_TYPES = setOf(
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "audio/mpegurl",
        "audio/x-mpegurl",
    )

    /** D1: containers that name a playlist, not a file's type. */
    private val PLAYLIST_CONTAINERS = setOf("m3u8", "m3u", "mpd", "hls", "dash")
    private const val HLS_VIDEO_MIME_TYPE = "video/mp4"
    private const val HLS_AUDIO_MIME_TYPE = "audio/mp4"

    fun create(
        asset: MediaAsset,
        variant: MediaVariant,
        taskId: String,
        nowEpochMs: Long,
        sdkInt: Int = Build.VERSION.SDK_INT,
        // v1.0.2 item 26: AV1 merges — the release's constant, or a debug build's test switch.
        av1Enabled: Boolean = Av1MergeSwitch.merges(),
    ): DownloadPlanResult {
        require(taskId.isNotBlank())

        if (variant.support != VariantSupport.SUPPORTED) {
            return rejected(
                DownloadFailureReason.UNSUPPORTED_SOURCE,
                "This track uses a codec YFT cannot download.",
            )
        }
        if (!variant.playbackUrl.startsWith("https://", ignoreCase = true)) {
            return rejected(
                DownloadFailureReason.INVALID_URL,
                "Only HTTPS media sources can be downloaded.",
            )
        }
        val expiry = variant.expiresAtEpochMs
        if (expiry != null && expiry <= nowEpochMs) {
            return rejected(
                DownloadFailureReason.EXPIRED_URL,
                "This media link already expired. Reload the page and try again.",
            )
        }

        val fileName = fileName(asset, variant)
        val context = variant.requestContext
        // G1 (v1.0.2): a size too small for this length is not the download's total.
        val length = variant.durationMillis?.takeIf { it > 0 } ?: asset.durationMillis
        variant.audioCompanion?.let { companion ->
            return mergedRequest(
                variant, companion, taskId, fileName, nowEpochMs, sdkInt, av1Enabled, length,
            )
        }

        return when (variant.kind) {
            MediaKind.DIRECT -> DownloadPlanResult.Ready(
                DownloadRequest.Direct(
                    plan = DirectDownloadPlan(
                        taskId = taskId,
                        sourceUrl = variant.playbackUrl,
                        suggestedFileName = fileName,
                        requestContext = context,
                        mimeType = variant.mimeType,
                        expectedBytes = PlausibleSize.of(variant.exactSizeBytes(), length),
                        expiresAtEpochMs = expiry,
                        maxRequestBytes = maxRequestBytesFor(variant.playbackUrl),
                        mp3 = variant.mp3?.let { conversion ->
                            Mp3Encoding(
                                bitrateKbps = conversion.bitrateKbps,
                                title = asset.title?.trim()?.takeIf(String::isNotEmpty),
                            )
                        },
                        // An MP3 reads the video's AAC track itself; an M4A copies it (P3).
                        audioOnly = variant.audioFromVideo && variant.mp3 == null,
                    ),
                    fileName = fileName,
                    mimeType = variant.mimeType,
                ),
            )

            MediaKind.HLS -> {
                // D1: the playlist is never the file's type: its pieces become an MP4 (an M4A for
                // sound alone), or keep their own format when they cannot be remuxed.
                val mimeType = if (variant.trackType == MediaTrackType.AUDIO) {
                    HLS_AUDIO_MIME_TYPE
                } else {
                    HLS_VIDEO_MIME_TYPE
                }
                DownloadPlanResult.Ready(
                    DownloadRequest.Hls(
                        plan = HlsDownloadPlan(
                            taskId = taskId,
                            playlistUrl = variant.playbackUrl,
                            suggestedFileName = fileName,
                            requestContext = context,
                            mimeType = mimeType,
                            expiresAtEpochMs = expiry,
                            remuxToMp4 = true,
                        ),
                        fileName = fileName,
                        mimeType = mimeType,
                    ),
                )
            }

            MediaKind.DASH -> {
                val representationId = variant.manifestVariantId
                if (representationId.isNullOrBlank()) {
                    rejected(
                        DownloadFailureReason.UNSUPPORTED_SOURCE,
                        "This DASH track has no representation to download.",
                    )
                } else {
                    DownloadPlanResult.Ready(
                        DownloadRequest.Dash(
                            plan = DashDownloadPlan(
                                taskId = taskId,
                                manifestUrl = variant.playbackUrl,
                                representationId = representationId,
                                trackType = variant.trackType,
                                suggestedFileName = fileName,
                                requestContext = context,
                                mimeType = variant.mimeType,
                                codecs = variant.codecs.filter(String::isNotBlank),
                                expiresAtEpochMs = expiry,
                            ),
                            fileName = fileName,
                            mimeType = variant.mimeType,
                        ),
                    )
                }
            }

            MediaKind.UNKNOWN -> rejected(
                DownloadFailureReason.UNSUPPORTED_SOURCE,
                "YFT could not identify this media source.",
            )
        }
    }

    /**
     * Plans a video-only file and its companion audio as two whole-file tracks merged into one
     * MP4. Each track is fetched in bounded byte ranges with its own request context; the pair is
     * rejected up front when the phone's muxer cannot combine the codecs. P50: or an HLS video
     * quality with its HLS audio rendition, both fetched as playlists. v1.0.2 item 59: or a DASH
     * video representation with its manifest's sound ([CompanionAudio.representationId]), each
     * one read from the manifest (a `SegmentBase` file is asked in ranges).
     */
    private fun mergedRequest(
        variant: MediaVariant,
        companion: CompanionAudio,
        taskId: String,
        fileName: String,
        nowEpochMs: Long,
        sdkInt: Int,
        av1Enabled: Boolean,
        length: Long?,
    ): DownloadPlanResult {
        val playlists = variant.kind == MediaKind.HLS
        val dashVideo = variant.manifestVariantId?.takeIf(String::isNotBlank)
        val dashAudio = companion.representationId
        val dash = variant.kind == MediaKind.DASH
        val paired = when (variant.kind) {
            MediaKind.DIRECT -> !companion.isHlsPlaylist() && dashAudio == null
            MediaKind.HLS -> companion.isHlsPlaylist() && dashAudio == null
            MediaKind.DASH -> dashVideo != null && dashAudio != null
            MediaKind.UNKNOWN -> false
        }
        if (!paired) {
            return rejected(
                DownloadFailureReason.UNSUPPORTED_SOURCE,
                "YFT can only merge audio into a single video file.",
            )
        }
        if (!companion.mediaUrl.startsWith("https://", ignoreCase = true)) {
            return rejected(
                DownloadFailureReason.INVALID_URL,
                "Only HTTPS media sources can be downloaded.",
            )
        }
        val audioExpiry = companion.expiresAtEpochMs
        if (audioExpiry != null && audioExpiry <= nowEpochMs) {
            return rejected(
                DownloadFailureReason.EXPIRED_URL,
                "This media link already expired. Reload the page and try again.",
            )
        }
        val stem = fileName.substringBeforeLast('.')
        // VP9 WebM video is merged with its WebM sound into a WebM (P6), everything else into MP4.
        val outputMimeType = AudioVideoMuxCompatibility.outputMimeTypeFor(variant.mimeType)
        val webm = outputMimeType == AudioVideoMuxCompatibility.WEBM_OUTPUT_MIME
        val plan = AudioVideoMuxDownloadPlan(
            taskId = taskId,
            video = DashDownloadPlan(
                taskId = "$taskId-video",
                manifestUrl = variant.playbackUrl,
                representationId = if (dash && dashVideo != null) dashVideo else "video",
                trackType = MediaTrackType.VIDEO,
                suggestedFileName = if (webm) "$stem.video.webm" else "$stem.video.mp4",
                requestContext = variant.requestContext,
                mimeType = variant.mimeType,
                codecs = variant.codecs.filter(String::isNotBlank),
                expiresAtEpochMs = variant.expiresAtEpochMs,
                wholeFile = if (playlists || dash) {
                    null
                } else {
                    val total = statedLengthOf(variant.playbackUrl)
                    WholeFileTrack(totalBytes = PlausibleSize.of(total, length))
                },
                hlsPlaylist = playlists,
                expectedDurationMillis = variant.durationMillis?.takeIf { it > 0 },
            ),
            audio = DashDownloadPlan(
                taskId = "$taskId-audio",
                manifestUrl = companion.mediaUrl,
                representationId = dashAudio ?: "audio",
                trackType = MediaTrackType.AUDIO,
                suggestedFileName = if (webm) "$stem.audio.webm" else "$stem.audio.m4a",
                requestContext = companion.requestContext,
                mimeType = companion.mimeType,
                codecs = companion.codecs,
                expiresAtEpochMs = companion.expiresAtEpochMs,
                wholeFile = if (playlists || dash) {
                    null
                } else {
                    val stated = companion.contentLengthBytes
                    val total = statedLengthOf(companion.mediaUrl, stated)
                    WholeFileTrack(totalBytes = PlausibleSize.of(total, length))
                },
                hlsPlaylist = playlists,
                expectedDurationMillis = variant.durationMillis?.takeIf { it > 0 },
            ),
            suggestedFileName = fileName,
            outputMimeType = outputMimeType,
        )
        if (AudioVideoMuxCompatibility.evaluate(plan, sdkInt, av1Enabled) is MuxCompatibility.Incompatible) {
            return rejected(
                DownloadFailureReason.INCOMPATIBLE_TRACKS,
                "This video and its audio cannot be combined on this phone.",
            )
        }
        return DownloadPlanResult.Ready(
            DownloadRequest.Mux(plan = plan, fileName = fileName, mimeType = plan.outputMimeType),
        )
    }

    /**
     * YouTube's media servers slow down single requests larger than about 10 MB, so their files
     * are fetched in bounded ranges. Other servers get each segment in one request.
     */
    internal fun maxRequestBytesFor(url: String): Long? {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase(Locale.US) ?: return null
        val youTubeMedia = host == GOOGLEVIDEO_HOST || host.endsWith(".$GOOGLEVIDEO_HOST")
        return WholeFileTrack.DEFAULT_MAX_REQUEST_BYTES.takeIf { youTubeMedia }
    }

    /**
     * The exact length YouTube's media servers give a file in its address (clen), or the
     * [contentLength] YouTube stated for it, so the download needs no request to learn it (P41).
     * Null for other servers, whose stated sizes may be estimates.
     */
    internal fun statedLengthOf(url: String, contentLength: Long? = null): Long? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.US) ?: return null
        if (host != GOOGLEVIDEO_HOST && !host.endsWith(".$GOOGLEVIDEO_HOST")) return null
        val clen = uri.rawQuery
            ?.split('&')
            ?.firstOrNull { parameter -> parameter.startsWith("clen=") }
            ?.substringAfter('=')
            ?.toLongOrNull()
        return (clen ?: contentLength)?.takeIf { it > 0 }
    }

    private const val GOOGLEVIDEO_HOST = "googlevideo.com"

    /**
     * Builds a safe output name from non-sensitive metadata only. The playback URL is never used
     * because signed URLs can carry credentials. D3: at most [MAX_FILE_NAME_LENGTH] characters
     * and [FileNames.MAX_NAME_BYTES] UTF-8 bytes with the extension, cut at a grapheme boundary.
     */
    internal fun fileName(asset: MediaAsset, variant: MediaVariant): String {
        val title = asset.title?.sanitizeForFileSystem()?.takeIf { it.isNotBlank() }
        val label = variant.label?.sanitizeForFileSystem()?.takeIf { it.isNotBlank() }

        val extension = extensionFor(variant)
        val maxBase = (MAX_FILE_NAME_LENGTH - extension.length - 1).coerceAtLeast(1)
        val maxBaseBytes =
            (FileNames.MAX_NAME_BYTES - FileNames.utf8Bytes(extension) - 1).coerceAtLeast(1)
        // N9 (v1.0.2): a name too long keeps its quality (" 480p") and extension whole; only
        // the title is shortened, at the end of a word when one ends near.
        val suffix = label?.let { " $it" }.orEmpty()
        val titleChars = maxBase - suffix.length
        val titleBytes = maxBaseBytes - FileNames.utf8Bytes(suffix)
        val base = when {
            title == null -> label ?: FALLBACK_NAME
            titleChars <= 0 || titleBytes <= 0 -> "$title$suffix"
            else -> FileNames.cutAtWord(title, titleChars, titleBytes).ifEmpty { null }
                ?.let { it + suffix }
                ?: label
                ?: FALLBACK_NAME
        }
        val shortened = FileNames.cut(base, maxBase, maxBaseBytes)
            .trimEnd('.', ' ', '-', '_')
            .ifEmpty { FALLBACK_NAME }
        return "$shortened.$extension"
    }

    internal fun extensionFor(variant: MediaVariant): String {
        // Merged downloads are written as MP4, VP9 WebM ones as WebM (P6).
        if (variant.audioCompanion != null) {
            val output = AudioVideoMuxCompatibility.outputMimeTypeFor(variant.mimeType)
            return if (output == AudioVideoMuxCompatibility.WEBM_OUTPUT_MIME) "webm" else "mp4"
        }
        // D1: an HLS download is remuxed into an MP4, an M4A for sound alone.
        if (variant.kind == MediaKind.HLS) return defaultExtension(variant)
        // Sound alone in an MP4 container is an M4A file (Facebook's and YouTube's audio tracks).
        if (variant.trackType == MediaTrackType.AUDIO && variant.isMp4Container()) return "m4a"
        variant.container?.takeIf { it.isNotBlank() }?.let { container ->
            val extension = container.lowercase(Locale.US).trimStart('.').sanitizeForFileSystem()
            // D1: a playlist (m3u8, mpd) is never a file's type; its media type says it.
            if (extension !in PLAYLIST_CONTAINERS) {
                return extension.ifBlank { defaultExtension(variant) }
            }
        }
        return when (variant.mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.US)) {
            "video/mp4", "application/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/x-matroska" -> "mkv"
            // D1: MPEG-TS bytes downloaded whole stay MPEG-TS, never under an MP4's name.
            "video/mp2t" -> "ts"
            "audio/mp4", "audio/mp4a-latm" -> "m4a"
            "audio/mpeg" -> "mp3"
            "audio/webm" -> "weba"
            "audio/ogg" -> "ogg"
            else -> defaultExtension(variant)
        }
    }

    private fun CompanionAudio.isHlsPlaylist(): Boolean =
        mimeType.substringBefore(';').trim().lowercase(Locale.US) in HLS_MIME_TYPES ||
            mediaUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true)

    private fun MediaVariant.isMp4Container(): Boolean {
        val box = container?.trim()?.trimStart('.')?.lowercase(Locale.US)
        return box == "mp4" || (box.isNullOrEmpty() && mimeType.equals("audio/mp4", true))
    }

    private fun defaultExtension(variant: MediaVariant): String =
        if (variant.trackType == MediaTrackType.AUDIO) "m4a" else "mp4"

    private fun rejected(
        reason: DownloadFailureReason,
        message: String,
    ): DownloadPlanResult.Rejected = DownloadPlanResult.Rejected(reason, message)

    private fun MediaVariant.exactSizeBytes(): Long? = sizeBytes?.takeIf {
        sizeAccuracy == com.alal.yft.core.model.media.MediaSizeAccuracy.EXACT
    }

    /**
     * Keeps only characters that are safe in a user-visible file name.
     *
     * Dots are intentionally dropped so a hostile title can never produce a traversal segment
     * such as `..`; the extension is appended separately. D3: a letter's marks stay with it
     * (Myanmar, Thai, Hindi), no longer turned into spaces ([FileNames.keepWords]).
     */
    private fun String.sanitizeForFileSystem(): String =
        FileNames.keepWords(this, ALLOWED_PUNCTUATION)

    private const val ALLOWED_PUNCTUATION = "-_()[] "
}
