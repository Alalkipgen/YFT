package com.alal.yft.feature.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.components.YftMediaKind
import com.alal.yft.ui.components.YftSeekBar
import com.alal.yft.ui.components.YftThumbnail
import com.alal.yft.ui.format.YftFormat
import com.alal.yft.ui.theme.YftIcons
import com.alal.yft.ui.theme.YftShapes
import com.alal.yft.ui.theme.YftTheme
import kotlinx.coroutines.delay

/**
 * The real preview player inside the "Download as" frame: ExoPlayer drawing into a PlayerView
 * without Media3's own controller, under YFT's play / pause, time and seek controls. Audio
 * variants show the audio tile instead of a black picture. The player is released when the
 * variant changes or the sheet closes; playback errors reach the ViewModel.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PreviewPlayerSurface(
    variant: MediaVariant,
    modifier: Modifier,
    viewModel: PreviewViewModel,
) {
    val player = remember(variant.id) { viewModel.createPlayer() }
    var playing by remember(player) { mutableStateOf(false) }
    var ended by remember(player) { mutableStateOf(false) }
    var positionMs by remember(player) { mutableLongStateOf(0L) }
    var durationMs by remember(player) { mutableLongStateOf(variant.durationMillis ?: 0L) }
    DisposableEffect(player, variant.id) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                viewModel.onPlaybackError(variant.id)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                ended = playbackState == Player.STATE_ENDED
                if (player.duration != C.TIME_UNSET && player.duration > 0) {
                    durationMs = player.duration
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, variant.id) {
        runCatching {
            player.setMediaSource(viewModel.createMediaSource())
            player.playWhenReady = false
            player.prepare()
        }.onFailure {
            viewModel.onPlaybackError(variant.id)
        }
    }
    LaunchedEffect(player, playing) {
        while (playing) {
            positionMs = player.currentPosition
            delay(PROGRESS_TICK_MS)
        }
        positionMs = player.currentPosition
    }
    Box(modifier = modifier) {
        if (variant.trackType == MediaTrackType.AUDIO) {
            YftThumbnail(
                image = null,
                kind = YftMediaKind.Audio,
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                iconSize = 40.dp,
            )
        } else {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        this.player = player
                    }
                },
                update = { view -> view.player = player },
                onRelease = { view -> view.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
        PreviewPlayerControls(
            playing = playing,
            positionMs = positionMs,
            durationMs = durationMs,
            onPlayPause = {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    if (ended) player.seekTo(0)
                    player.play()
                }
            },
            onSeek = { fraction ->
                if (durationMs > 0) {
                    val target = (durationMs * fraction).toLong()
                    player.seekTo(target)
                    positionMs = target
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Play / pause, time and seek over the preview frame. Paused, it shows the design's centred play
 * button and the length ("4:12"); playing, the picture stays clear apart from the elapsed time
 * and a Mint progress line that can be tapped or dragged to seek. The whole frame is one
 * button, so a tap anywhere pauses or resumes.
 */
@Composable
internal fun PreviewPlayerControls(
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val started = playing || positionMs > 0
    val action = if (playing) "Pause preview" else "Play preview"
    Box(
        modifier = modifier
            .clickable(onClickLabel = action, role = Role.Button, onClick = onPlayPause)
            .semantics { contentDescription = action }
            .testTag("preview-play-pause"),
    ) {
        if (!playing) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = CONTROL_SCRIM_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                YftIcon(
                    icon = YftIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    size = 24.dp,
                )
            }
        }
        timeLabel(started, positionMs, durationMs)?.let { time ->
            Text(
                text = time,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(YftShapes.badge)
                    .background(YftTheme.colors.scrim)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                color = YftTheme.colors.onScrim,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        if (started && durationMs > 0) {
            YftSeekBar(
                fraction = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f),
                onSeek = onSeek,
                contentDescription = "Preview position",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .testTag("preview-seek"),
                trackColor = Color.White.copy(alpha = TRACK_ALPHA),
                lineAtBottom = true,
            )
        }
    }
}

/** "4:12" before playback, "0:12 / 4:12" once it started, or just the elapsed time. */
internal fun timeLabel(started: Boolean, positionMs: Long, durationMs: Long): String? = when {
    !started && durationMs > 0 -> YftFormat.duration(durationMs)
    started && durationMs > 0 ->
        "${YftFormat.duration(positionMs)} / ${YftFormat.duration(durationMs)}"
    started -> YftFormat.duration(positionMs)
    else -> null
}

private const val PROGRESS_TICK_MS = 250L
private const val CONTROL_SCRIM_ALPHA = 0.45f
private const val TRACK_ALPHA = 0.35f
