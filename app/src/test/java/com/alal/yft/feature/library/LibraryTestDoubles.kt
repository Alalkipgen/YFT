package com.alal.yft.feature.library

import androidx.media3.common.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Playback that only records what it was asked to do. */
internal class FakeLibraryPlayback : LibraryPlayback {
    val mutableState = MutableStateFlow<PlaybackState?>(null)
    val mutableFailures = MutableSharedFlow<LibraryItem>(extraBufferCapacity = 1)
    val calls = mutableListOf<String>()

    override val state: StateFlow<PlaybackState?> = mutableState
    override val failures: Flow<LibraryItem> = mutableFailures
    override val player: Player? = null

    override fun play(item: LibraryItem) {
        calls += "play:${item.id}"
        mutableState.value = PlaybackState(item = item)
    }

    override fun togglePlayPause() {
        calls += "toggle"
        mutableState.update { it?.copy(isPlaying = !it.isPlaying) }
    }

    override fun pause() {
        calls += "pause"
        mutableState.update { it?.copy(isPlaying = false) }
    }

    override fun seekTo(fraction: Float) {
        calls += "seek:$fraction"
    }

    override fun stop() {
        calls += "stop"
        mutableState.value = null
    }
}

/** Details known up front, by URI; anything else reads as unknown. */
internal class FixedMediaDetails(
    private val details: Map<String, MediaDetails>,
) : MediaDetailsSource {
    override fun cached(uri: String): MediaDetails? = details[uri]

    override suspend fun load(uri: String, isAudio: Boolean): MediaDetails =
        details[uri] ?: MediaDetails.Unknown
}

internal fun libraryItem(
    id: String,
    name: String = "$id.mp4",
    mimeType: String? = LibraryMimeTypes.forFileName(name),
    sizeBytes: Long? = 1,
    modifiedAt: Long? = 1,
    location: LibraryLocation = LibraryLocation.APP_STORAGE,
) = LibraryItem(
    id = id,
    displayName = name,
    uri = "content://example/$id",
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    modifiedAtEpochMs = modifiedAt,
    location = location,
)
