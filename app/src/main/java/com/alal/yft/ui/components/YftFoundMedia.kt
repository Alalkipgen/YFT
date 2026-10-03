package com.alal.yft.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.ui.format.YftFormat
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme
import java.net.URI
import java.util.Locale

/**
 * One row of "Found on this page" (`02`): thumbnail, title, fact chips (MP4 · Auto quality ·
 * 186 MB) and a Mint Preview pill. Shared by the browser sheet and the Found media screen so a
 * candidate always reads the same. Only facts detection actually knows are shown; the exact
 * qualities appear in Download as once the variants are resolved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YftFoundMediaRow(
    candidate: MediaCandidate,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnail: ImageBitmap? = null,
    previewTag: String = "preview-candidate",
) {
    val colors = YftTheme.colors
    val audio = candidate.isAudio()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftThumbnail(
            image = thumbnail,
            kind = if (audio) YftMediaKind.Audio else YftMediaKind.Video,
            modifier = Modifier.size(FOUND_THUMBNAIL),
            shape = YftShapes.thumbnailSmall,
            iconSize = 26.dp,
            glyph = if (audio) YftIcons.Waveform else null,
            muted = audio && thumbnail == null,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = candidate.safeTitle(),
                color = colors.textPrimary,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val facts = candidate.factLabels()
            if (facts.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    facts.forEach { fact -> YftMetaChip(text = fact) }
                }
            }
        }
        YftPrimaryButton(
            text = "Preview",
            onClick = onPreview,
            compact = true,
            modifier = Modifier.testTag(previewTag),
        )
    }
}

/** The sheet footer from `02`: only media the user may save is listed, and never DRM. */
@Composable
fun YftAllowedMediaNote(modifier: Modifier = Modifier) {
    val colors = YftTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(YftShapes.thumbnail)
            .background(colors.chip)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftIcon(
            icon = YftIcons.Shield,
            contentDescription = null,
            tint = colors.textSecondary,
            size = 20.dp,
        )
        Text(
            text = ALLOWED_MEDIA_NOTE,
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Says how many DRM-protected items were left out of a list, or null when none were. */
fun protectedHiddenLabel(hidden: Int): String? = when {
    hidden <= 0 -> null
    hidden == 1 -> "1 protected item is not listed: DRM media can't be saved."
    else -> "$hidden protected items are not listed: DRM media can't be saved."
}

const val ALLOWED_MEDIA_NOTE = "Only media you're allowed to save is shown. No DRM."

/** Media YFT may offer: anything without a DRM hint. Protected media is never listed. */
val MediaCandidate.isSavable: Boolean
    get() = drmHint != true

fun MediaCandidate.safeTitle(): String = title
    ?.trim()
    ?.take(MAX_TITLE)
    ?.takeIf(String::isNotEmpty)
    ?: when (kind) {
        MediaKind.HLS -> "HLS stream"
        MediaKind.DASH -> "DASH stream"
        MediaKind.DIRECT -> if (isAudio()) "Audio file" else "Video file"
        MediaKind.UNKNOWN -> "Media file"
    }

/** The media host only; candidate addresses can carry signed tokens. */
fun MediaCandidate.safeOrigin(): String =
    runCatching { URI(mediaUrl).host }.getOrNull()?.takeIf(String::isNotBlank)
        ?: "Origin unavailable"

fun MediaCandidate.isAudio(): Boolean {
    val type = mimeType?.lowercase(Locale.US).orEmpty()
    if (type.startsWith("audio/")) return true
    if (type.startsWith("video/") || kind == MediaKind.HLS || kind == MediaKind.DASH) return false
    return urlExtension() in AUDIO_EXTENSIONS
}

/** "MP4", "M4A", "HLS"; "Auto quality" for adaptive streams; size and length when known. */
fun MediaCandidate.factLabels(): List<String> = buildList {
    formatLabel()?.let(::add)
    if (kind == MediaKind.HLS || kind == MediaKind.DASH) add("Auto quality")
    contentLengthBytes?.takeIf { it > 0 }?.let { add(YftFormat.bytes(it)) }
    durationMillis?.takeIf { it > 0 }?.let { add(YftFormat.duration(it)) }
}

fun MediaCandidate.formatLabel(): String? = when (kind) {
    MediaKind.HLS -> "HLS"
    MediaKind.DASH -> "DASH"
    MediaKind.DIRECT, MediaKind.UNKNOWN -> {
        val type = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.US)
        val subtype = type?.substringAfter('/')
        when {
            type == "audio/mp4" -> "M4A"
            subtype != null && subtype in MIME_LABELS -> MIME_LABELS.getValue(subtype)
            else -> urlExtension()?.takeIf { it in KNOWN_EXTENSIONS }?.uppercase(Locale.US)
        }
    }
}

private fun MediaCandidate.urlExtension(): String? {
    val path = runCatching { URI(mediaUrl).path }.getOrNull() ?: return null
    return path.substringAfterLast('/').substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.US)
        .takeIf { it.isNotEmpty() && it.length <= MAX_EXTENSION }
}

private val FOUND_THUMBNAIL = 48.dp

/** Where the dividers between rows start: under the title, past the thumbnail. */
val FoundMediaDividerInset = 78.dp
private const val MAX_TITLE = 120
private const val MAX_EXTENSION = 5
private val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "aac", "ogg", "oga", "opus", "wav", "flac")
private val KNOWN_EXTENSIONS = AUDIO_EXTENSIONS + setOf("mp4", "m4v", "webm", "mov", "mkv", "3gp")
private val MIME_LABELS = mapOf(
    "mp4" to "MP4",
    "x-m4v" to "MP4",
    "webm" to "WEBM",
    "quicktime" to "MOV",
    "x-matroska" to "MKV",
    "3gpp" to "3GP",
    "mpeg" to "MP3",
    "mp3" to "MP3",
    "aac" to "AAC",
    "x-m4a" to "M4A",
    "ogg" to "OGG",
    "opus" to "OPUS",
    "wav" to "WAV",
    "flac" to "FLAC",
)
