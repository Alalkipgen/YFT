package com.alal.yft.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alal.yft.ui.components.YftIconButton
import com.alal.yft.ui.components.YftMediaKind
import com.alal.yft.ui.components.YftSeekBar
import com.alal.yft.ui.components.YftThumbnail
import com.alal.yft.ui.format.YftFormat
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme

/**
 * The mini player above the bottom bar (`05`) while audio plays: tile, title, "1:24 / 2:58", a
 * Mint seek line, Pause or Play, and X, which stops playback. Videos play full screen instead.
 */
@Composable
fun MiniPlayerHost(playback: LibraryPlayback, modifier: Modifier = Modifier) {
    val state by playback.state.collectAsStateWithLifecycle()
    val current = state?.takeUnless { it.item.isVideo } ?: return
    MiniPlayer(
        state = current,
        onPlayPause = playback::togglePlayPause,
        onSeek = playback::seekTo,
        onClose = playback::stop,
        modifier = modifier,
    )
}

@Composable
fun MiniPlayer(
    state: PlaybackState,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = YftTheme.colors
    val item = state.item
    val title = YftFormat.title(item.displayName)
    val details = rememberMediaDetails(item.uri, item.isAudio)
    val durationMs = state.durationMs.takeIf { it > 0L } ?: details?.durationMs ?: 0L
    val fraction = if (durationMs > 0L) {
        (state.positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MINI_PLAYER_SHAPE)
            .background(colors.card)
            .drawBehind {
                drawLine(
                    color = colors.border,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 4.dp)
            .testTag("mini-player"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YftThumbnail(
            image = details?.image,
            kind = if (item.isAudio) YftMediaKind.Audio else YftMediaKind.Video,
            modifier = Modifier.size(44.dp),
            shape = YftShapes.thumbnailSmall,
            iconSize = 22.dp,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 4.dp),
        ) {
            Text(
                text = title,
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = playbackTime(state.positionMs, durationMs),
                modifier = Modifier.testTag("mini-player-time"),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
            )
            YftSeekBar(
                fraction = fraction,
                onSeek = onSeek,
                contentDescription = "Position in $title",
                modifier = Modifier.testTag("mini-player-seek"),
                handle = true,
                height = SEEK_HEIGHT,
            )
        }
        YftIconButton(
            icon = if (state.isPlaying) YftIcons.Pause else YftIcons.Play,
            contentDescription = if (state.isPlaying) "Pause $title" else "Play $title",
            onClick = onPlayPause,
            modifier = Modifier.testTag("mini-player-play-pause"),
        )
        YftIconButton(
            icon = YftIcons.Close,
            contentDescription = "Close player",
            onClick = onClose,
            modifier = Modifier.testTag("mini-player-close"),
        )
    }
}

/** "1:24 / 2:58", or just the elapsed time while the length is unknown. */
internal fun playbackTime(positionMs: Long, durationMs: Long): String =
    if (durationMs > 0L) {
        "${YftFormat.duration(positionMs)} / ${YftFormat.duration(durationMs)}"
    } else {
        YftFormat.duration(positionMs)
    }

private val MINI_PLAYER_SHAPE = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val SEEK_HEIGHT = 16.dp
