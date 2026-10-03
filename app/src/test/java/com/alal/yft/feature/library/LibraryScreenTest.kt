package com.alal.yft.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val video = LibraryItem(
        id = "media:1",
        displayName = "Clip.mp4",
        uri = "content://media/external_primary/downloads/1",
        mimeType = "video/mp4",
        sizeBytes = 2_097_152,
        modifiedAtEpochMs = null,
        location = LibraryLocation.SHARED_DOWNLOADS,
    )
    private val unknown = LibraryItem(
        id = "app:notes.bin",
        displayName = "notes.bin",
        uri = "content://com.alal.yft.downloads/notes.bin",
        mimeType = null,
        sizeBytes = null,
        modifiedAtEpochMs = null,
        location = LibraryLocation.APP_STORAGE,
    )

    @Test
    fun loadingEmptyAndErrorStatesAreExplicit() {
        val state = mutableStateOf<LibraryUiState>(LibraryUiState.Loading)
        var refreshes = 0
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                LibraryScreen(
                    uiState = state.value,
                    onNavigateBack = {},
                    onRefresh = { refreshes += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("library-loading").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Loading the library").assertIsDisplayed()

        state.value = LibraryUiState.Ready(items = emptyList())
        composeRule.onNodeWithTag("library-empty").assertIsDisplayed()
        composeRule.onNodeWithText("No finished downloads yet").assertIsDisplayed()

        state.value = LibraryUiState.Error("The library could not be read. Try again.")
        composeRule.onNodeWithTag("library-error").assertIsDisplayed()
        composeRule.onNodeWithTag("library-retry").performClick()
        composeRule.onNodeWithContentDescription("Refresh").performClick()
        assertEquals(2, refreshes)
    }

    @Test
    fun itemsShowHonestMetadataAndOfferOnlyTheActionsThatApply() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                LibraryScreen(
                    uiState = LibraryUiState.Ready(items = listOf(video, unknown)),
                    onNavigateBack = {},
                    onPlay = { opened += "play:${it.id}" },
                    onOpen = { opened += "open:${it.id}" },
                    onShare = { opened += "share:${it.id}" },
                )
            }
        }

        composeRule.onNodeWithText("Video · 2 MB · Download/YFT").assertIsDisplayed()
        composeRule.onNodeWithText("File · App storage").assertIsDisplayed()
        composeRule.onAllNodesWithTag("library-play-app:notes.bin").assertCountEquals(0)

        composeRule.onNodeWithTag("library-play-media:1").performClick()
        composeRule.onNodeWithTag("library-open-app:notes.bin").performClick()
        composeRule.onNodeWithTag("library-share-media:1").performClick()

        assertEquals(listOf("play:media:1", "open:app:notes.bin", "share:media:1"), opened)
    }

    @Test
    fun deleteAsksForConfirmationFirst() {
        val state = mutableStateOf(LibraryUiState.Ready(items = listOf(video)))
        var confirmed = 0
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                LibraryScreen(
                    uiState = state.value,
                    onNavigateBack = {},
                    onRequestDelete = { state.value = state.value.copy(pendingDelete = it) },
                    onDismissDelete = { state.value = state.value.copy(pendingDelete = null) },
                    onConfirmDelete = {
                        confirmed += 1
                        state.value = state.value.copy(pendingDelete = null)
                    },
                )
            }
        }

        composeRule.onNodeWithTag("library-delete-media:1").performClick()
        composeRule.onNodeWithText("Delete this file?").assertIsDisplayed()
        composeRule.onNodeWithTag("library-delete-dismiss").performClick()
        composeRule.onAllNodesWithTag("library-delete-dialog").assertCountEquals(0)
        assertEquals(0, confirmed)

        composeRule.onNodeWithTag("library-delete-media:1").performClick()
        composeRule.onNodeWithText(
            "Clip.mp4 will be removed from Download/YFT. This cannot be undone.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("library-delete-confirm").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun theInAppPlayerShowsAboveTheListAndCanBeClosed() {
        val state = mutableStateOf(LibraryUiState.Ready(items = listOf(video), playing = video))
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.DARK) {
                LibraryScreen(
                    uiState = state.value,
                    onNavigateBack = {},
                    onStopPlayback = { state.value = state.value.copy(playing = null) },
                    playerSurface = { _, modifier ->
                        Box(modifier.testTag("fake-player"))
                    },
                )
            }
        }

        composeRule.onNodeWithTag("library-player").assertIsDisplayed()
        composeRule.onNodeWithTag("fake-player").assertIsDisplayed()
        composeRule.onNodeWithTag("library-player-close").performClick()
        composeRule.onAllNodesWithTag("library-player").assertCountEquals(0)
    }
}
