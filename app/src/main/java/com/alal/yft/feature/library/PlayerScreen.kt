package com.alal.yft.feature.library

import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.alal.yft.ui.components.YftIcon
import com.alal.yft.ui.components.YftIconButton
import com.alal.yft.ui.components.YftSeekBar
import com.alal.yft.ui.format.YftFormat
import com.alal.yft.ui.theme.YftIcons
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** The full-screen player's view of the app-wide playback; leaving the screen stops a video. */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playback: LibraryPlayback,
) : ViewModel() {
    val state: StateFlow<PlaybackState?> = playback.state

    val player: Player?
        get() = playback.player

    fun togglePlayPause() = playback.togglePlayPause()

    fun seekTo(fraction: Float) = playback.seekTo(fraction)

    fun close() = playback.stop()

    override fun onCleared() {
        if (playback.state.value?.item?.isVideo == true) playback.stop()
    }
}

/** A saved video full screen with the system bars hidden; X or Back closes it and stops it. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerRoute(onClose: () -> Unit, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    val closed = state == null
    LaunchedEffect(closed) { if (closed) currentOnClose() }
    HideSystemBars()
    val current = state ?: return
    BackHandler(onBack = viewModel::close)
    PlayerScreen(
        state = current,
        onPlayPause = viewModel::togglePlayPause,
        onSeek = viewModel::seekTo,
        onClose = viewModel::close,
        surface = { modifier ->
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        setShutterBackgroundColor(AndroidColor.BLACK)
                        player = viewModel.player
                    }
                },
                update = { view -> view.player = viewModel.player },
                onRelease = { view -> view.player = null },
                modifier = modifier,
            )
        },
    )
}

@Composable
fun PlayerScreen(
    state: PlaybackState,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    surface: @Composable (Modifier) -> Unit = {},
) {
    val title = YftFormat.title(state.item.displayName)
    val action = if (state.isPlaying) "Pause" else "Play"
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player-screen"),
    ) {
        surface(Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClickLabel = action, role = Role.Button, onClick = onPlayPause)
                .semantics { contentDescription = "$action $title" }
                .testTag("player-play-pause"),
        )
        if (!state.isPlaying) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = SCRIM_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                YftIcon(
                    icon = YftIcons.Play,
                    contentDescription = null,
                    tint = Color.White,
                    size = 36.dp,
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(SCRIM, Color.Transparent)))
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                )
                .padding(start = 4.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YftIconButton(
                icon = YftIcons.Close,
                contentDescription = "Close player",
                onClick = onClose,
                modifier = Modifier.testTag("player-close"),
                tint = Color.White,
            )
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, SCRIM)))
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                    ),
                )
                .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
        ) {
            Text(
                text = playbackTime(state.positionMs, state.durationMs),
                modifier = Modifier.testTag("player-time"),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            YftSeekBar(
                fraction = state.fraction,
                onSeek = onSeek,
                contentDescription = "Position in $title",
                modifier = Modifier.testTag("player-seek"),
                trackColor = Color.White.copy(alpha = TRACK_ALPHA),
                handle = true,
            )
        }
    }
}

/** Hides the status and navigation bars while the player shows; a swipe brings them back. */
@Composable
private fun HideSystemBars() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private val SCRIM = Color.Black.copy(alpha = 0.55f)
private const val SCRIM_ALPHA = 0.45f
private const val TRACK_ALPHA = 0.35f
