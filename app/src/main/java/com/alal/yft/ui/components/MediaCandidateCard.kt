package com.alal.yft.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import java.net.URI
import java.util.Locale

/**
 * One detected media candidate with its known facts and a Preview action. Shared by the browser's
 * candidate sheet and the Detected Media screen so both describe a candidate the same way.
 */
@Composable
fun MediaCandidateCard(
    candidate: MediaCandidate,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
    previewTag: String = "preview-candidate",
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = candidate.safeTitle(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = candidate.safeOrigin(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = listOf(
                    candidate.kind.displayName(),
                    candidate.mimeType ?: "MIME unknown",
                    candidate.contentLengthBytes?.formatBytes() ?: "Size unknown",
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Duration: ${candidate.durationMillis?.formatDuration() ?: "Unknown"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "DRM: ${candidate.drmLabel()}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Found via ${candidate.sources.sourceLabel()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onPreview,
                enabled = candidate.drmHint != true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(previewTag),
            ) {
                Text(if (candidate.drmHint == true) "DRM not supported" else "Preview")
            }
        }
    }
}

internal fun MediaCandidate.safeTitle(): String = title
    ?.trim()
    ?.take(120)
    ?.takeIf(String::isNotEmpty)
    ?: when (kind) {
        MediaKind.HLS -> "HLS stream"
        MediaKind.DASH -> "DASH stream"
        MediaKind.DIRECT -> "Media file"
        MediaKind.UNKNOWN -> "Media candidate"
    }

internal fun MediaCandidate.safeOrigin(): String {
    val uri = runCatching { URI(mediaUrl) }.getOrNull()
    return uri?.host?.takeIf(String::isNotBlank) ?: "Origin unavailable"
}

private fun MediaKind.displayName(): String = when (this) {
    MediaKind.DIRECT -> "Direct"
    MediaKind.HLS -> "HLS"
    MediaKind.DASH -> "DASH"
    MediaKind.UNKNOWN -> "Type unknown"
}

private fun Long.formatBytes(): String {
    if (this < 1_024) return "$this B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = toDouble()
    var unit = -1
    while (value >= 1_024 && unit < units.lastIndex) {
        value /= 1_024
        unit += 1
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}

private fun Long.formatDuration(): String {
    val totalSeconds = this / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun MediaCandidate.drmLabel(): String = when (drmHint) {
    true -> "Hint detected"
    false -> "No hint detected"
    null -> "Unknown"
}

private fun Set<CandidateSource>.sourceLabel(): String = sortedBy(CandidateSource::ordinal)
    .joinToString { source ->
        source.name
            .lowercase()
            .replace('_', ' ')
            .replaceFirstChar { it.titlecase(Locale.US) }
    }
