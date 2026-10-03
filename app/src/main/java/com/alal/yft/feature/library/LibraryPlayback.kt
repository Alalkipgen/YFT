package com.alal.yft.feature.library

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.alal.yft.core.media.player.MediaPlayerFactory
import com.alal.yft.ui.format.YftFormat
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The saved file playing in the app and how far it got. */
data class PlaybackState(
    val item: LibraryItem,
    /** Whether it plays or is about to (buffering), which decides the Pause / Play button. */
    val isPlaying: Boolean = true,
    val positionMs: Long = 0L,
    /** 0 until the player knows the length. */
    val durationMs: Long = 0L,
) {
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** The app's player for screens outside the Library, such as Downloads; null in tests. */
val LocalLibraryPlayback = staticCompositionLocalOf<LibraryPlayback?> { null }

/** What the app says when [item] cannot be played in YFT. */
internal fun playbackFailedMessage(item: LibraryItem): String =
    "${YftFormat.title(item.displayName)} can't be played here. Try Open with… instead."

/**
 * One app-wide player for saved files, so audio keeps playing in the mini player while the user
 * moves between tabs; videos play full screen from the same player. Call from the main thread.
 */
interface LibraryPlayback {
    /** What plays, or null when nothing is loaded. */
    val state: StateFlow<PlaybackState?>

    /** Files that could not be played, which the app reports wherever the user is. */
    val failures: Flow<LibraryItem>

    /** The player a video surface draws from while something is loaded. */
    val player: Player?

    /** Plays [item] from the start, or resumes it when it is the one already loaded. */
    fun play(item: LibraryItem)

    fun togglePlayPause()

    fun pause()

    /** Jumps to [fraction] of the length once the length is known. */
    fun seekTo(fraction: Float)

    /** Stops and releases the player; [state] becomes null. */
    fun stop()
}

@Singleton
class ExoLibraryPlayback @Inject constructor(
    private val playerFactory: MediaPlayerFactory,
) : LibraryPlayback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<PlaybackState?>(null)
    private val mutableFailures = MutableSharedFlow<LibraryItem>(extraBufferCapacity = 1)
    private var exoPlayer: ExoPlayer? = null
    private var ticker: Job? = null

    override val state: StateFlow<PlaybackState?> = mutableState.asStateFlow()
    override val failures: Flow<LibraryItem> = mutableFailures.asSharedFlow()
    override val player: Player?
        get() = exoPlayer

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = sync()

        override fun onPlaybackStateChanged(playbackState: Int) = sync()

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            sync()
            if (isPlaying) startTicker() else ticker?.cancel()
        }

        override fun onPlayerError(error: PlaybackException) {
            val failed = mutableState.value?.item
            stop()
            if (failed != null) mutableFailures.tryEmit(failed)
        }
    }

    override fun play(item: LibraryItem) {
        val player = exoPlayer ?: playerFactory.create().also {
            it.addListener(listener)
            exoPlayer = it
        }
        if (mutableState.value?.item?.id == item.id) {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
            return
        }
        mutableState.value = PlaybackState(item = item)
        player.setMediaItem(MediaItem.fromUri(item.uri))
        player.prepare()
        player.play()
    }

    override fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    override fun pause() {
        exoPlayer?.pause()
    }

    override fun seekTo(fraction: Float) {
        val player = exoPlayer ?: return
        val durationMs = mutableState.value?.durationMs ?: 0L
        if (durationMs <= 0L) return
        player.seekTo((durationMs * fraction.coerceIn(0f, 1f)).toLong())
        sync()
    }

    override fun stop() {
        ticker?.cancel()
        ticker = null
        exoPlayer?.let { player ->
            player.removeListener(listener)
            player.release()
        }
        exoPlayer = null
        mutableState.value = null
    }

    private fun sync() {
        val player = exoPlayer ?: return
        val ended = player.playbackState == Player.STATE_ENDED
        mutableState.update { current ->
            current?.copy(
                isPlaying = player.playWhenReady && !ended,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.takeIf { it > 0L } ?: current.durationMs,
            )
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                sync()
                delay(PROGRESS_TICK_MS)
            }
        }
    }

    private companion object {
        const val PROGRESS_TICK_MS = 250L
    }
}
