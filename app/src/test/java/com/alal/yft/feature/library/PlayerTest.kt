package com.alal.yft.feature.library

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.media.player.MediaPlayerFactory
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.theme.YftTheme
import java.io.File
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
// Native graphics draws real bitmaps and hit-tests the mini player's top-rounded shape.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val song = libraryItem("waves", "Ocean Waves.m4a")
    private val video = libraryItem("lake", "Mountain Lake.mp4")

    @Test
    fun theMiniPlayerShowsTitleTimeAndControls() {
        val calls = mutableListOf<String>()
        var state by mutableStateOf(
            PlaybackState(song, isPlaying = true, positionMs = 84_000, durationMs = 178_000),
        )
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                MiniPlayer(
                    state = state,
                    onPlayPause = {
                        calls += "toggle"
                        state = state.copy(isPlaying = !state.isPlaying)
                    },
                    onSeek = { calls += "seek:$it" },
                    onClose = { calls += "close" },
                )
            }
        }

        composeRule.onNodeWithText("Ocean Waves").assertIsDisplayed()
        composeRule.onNodeWithTag("mini-player-time").assertTextEquals("1:24 / 2:58")
        composeRule.onNodeWithTag("mini-player-play-pause")
            .assertContentDescriptionEquals("Pause Ocean Waves")
            .performClick()
        composeRule.onNodeWithTag("mini-player-play-pause")
            .assertContentDescriptionEquals("Play Ocean Waves")
        composeRule.onNodeWithTag("mini-player-seek")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        composeRule.onNodeWithTag("mini-player-close").performClick()

        assertEquals(listOf("toggle", "seek:0.5", "close"), calls)
    }

    @Test
    fun theMiniPlayerShowsWhileAudioPlaysOnly() {
        val playback = FakeLibraryPlayback()
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.DARK) { MiniPlayerHost(playback) }
        }
        composeRule.onAllNodesWithTag("mini-player").assertCountEquals(0)

        composeRule.runOnIdle { playback.play(song) }
        composeRule.onNodeWithTag("mini-player").assertIsDisplayed()
        composeRule.onNodeWithTag("mini-player-close").performClick()
        composeRule.onAllNodesWithTag("mini-player").assertCountEquals(0)
        assertEquals("stop", playback.calls.last())

        composeRule.runOnIdle { playback.play(video) }
        composeRule.onAllNodesWithTag("mini-player").assertCountEquals(0)
    }

    @Test
    fun timeShowsTheElapsedPartUntilTheLengthIsKnown() {
        assertEquals("0:05", playbackTime(5_000, 0))
        assertEquals("1:24 / 2:58", playbackTime(84_000, 178_000))
        assertEquals("1:00:00 / 1:30:00", playbackTime(3_600_000, 5_400_000))
    }

    @Test
    fun theFullScreenPlayerTogglesSeeksAndCloses() {
        val calls = mutableListOf<String>()
        var state by mutableStateOf(
            PlaybackState(video, isPlaying = true, positionMs = 60_000, durationMs = 252_000),
        )
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                PlayerScreen(
                    state = state,
                    onPlayPause = { state = state.copy(isPlaying = !state.isPlaying) },
                    onSeek = { calls += "seek:$it" },
                    onClose = { calls += "close" },
                    surface = { modifier -> Box(modifier.testTag("fake-surface")) },
                )
            }
        }

        composeRule.onNodeWithTag("fake-surface").assertExists()
        composeRule.onNodeWithText("Mountain Lake").assertIsDisplayed()
        composeRule.onNodeWithTag("player-time").assertTextEquals("1:00 / 4:12")
        composeRule.onNodeWithTag("player-play-pause")
            .assertContentDescriptionEquals("Pause Mountain Lake")
            .performClick()
        composeRule.onNodeWithTag("player-play-pause")
            .assertContentDescriptionEquals("Play Mountain Lake")
        composeRule.onNodeWithTag("player-seek")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.25f) }
        composeRule.onNodeWithTag("player-close").performClick()

        assertEquals(listOf("seek:0.25", "close"), calls)
    }

    @Test
    fun leavingThePlayerStopsAVideoButNotAudio() {
        val playback = FakeLibraryPlayback()
        val factory = viewModelFactory { initializer { PlayerViewModel(playback) } }

        playback.play(video)
        ViewModelStore().also { ViewModelProvider(it, factory)[PlayerViewModel::class.java] }
            .clear()
        assertNull(playback.state.value)

        playback.play(song)
        ViewModelStore().also { ViewModelProvider(it, factory)[PlayerViewModel::class.java] }
            .clear()
        assertEquals("waves", playback.state.value?.item?.id)
    }

    @Test
    fun theAppPlayerLoadsPausesAndReleases() {
        val playback = ExoLibraryPlayback(MediaPlayerFactory(context()))

        playback.play(video)
        assertEquals(video, playback.state.value?.item)
        val player = playback.player!!
        assertTrue(player.playWhenReady)

        playback.togglePlayPause()
        assertFalse(player.playWhenReady)
        playback.togglePlayPause()
        assertTrue(player.playWhenReady)
        playback.pause()
        assertFalse(player.playWhenReady)
        // The length is not known yet, so there is nothing to seek in.
        playback.seekTo(0.5f)

        playback.stop()
        assertNull(playback.state.value)
        assertNull(playback.player)
    }

    @Test
    fun aFileThatCannotBePlayedStopsAndIsReported() {
        val playback = ExoLibraryPlayback(MediaPlayerFactory(context()))
        val failures = mutableListOf<String>()
        val collector = CoroutineScope(Dispatchers.Main.immediate).launch {
            playback.failures.collect { failures += it.id }
        }
        val missing = File(context().cacheDir, "missing.mp4")
        playback.play(video.copy(uri = Uri.fromFile(missing).toString()))

        repeat(MAX_WAITS) {
            if (playback.state.value != null) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
                Thread.sleep(10)
            }
        }
        collector.cancel()

        assertNull(playback.state.value)
        assertEquals(listOf("lake"), failures)
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private companion object {
        const val MAX_WAITS = 200
    }
}
