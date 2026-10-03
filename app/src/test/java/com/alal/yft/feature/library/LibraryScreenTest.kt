package com.alal.yft.feature.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private val lake = libraryItem(
        id = "lake",
        name = "Mountain Lake.mp4",
        sizeBytes = 100_663_296,
        modifiedAt = 20,
        location = LibraryLocation.SHARED_DOWNLOADS,
    )
    private val waves = libraryItem(
        id = "waves",
        name = "Ocean Waves.m4a",
        sizeBytes = 7_340_032,
        modifiedAt = 30,
    )
    private val notes = libraryItem("notes", "notes.bin", mimeType = null, modifiedAt = 10)
    private val details = FixedMediaDetails(
        mapOf(
            lake.uri to MediaDetails(durationMs = 252_000, width = 1280, height = 720),
            waves.uri to MediaDetails(durationMs = 178_000),
        ),
    )

    @Test
    fun loadingEmptyAndErrorStatesAreExplicit() {
        val state = mutableStateOf<LibraryUiState>(LibraryUiState.Loading)
        var refreshes = 0
        setScreen(state = { state.value }, onRefresh = { refreshes += 1 })

        composeRule.onNodeWithTag("library-loading").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Loading the library").assertIsDisplayed()

        state.value = LibraryUiState.Ready(items = emptyList())
        composeRule.onNodeWithTag("library-empty").assertIsDisplayed()
        composeRule.onNodeWithText("No finished downloads yet").assertIsDisplayed()

        state.value = LibraryUiState.Error("The library could not be read. Try again.")
        composeRule.onNodeWithTag("library-error").assertIsDisplayed()
        composeRule.onNodeWithTag("library-retry").performClick()
        assertEquals(1, refreshes)
    }

    @Test
    fun tilesShowTheFilesLengthAndPictureSizeAndTapPlaysOrOpens() {
        val calls = mutableListOf<String>()
        setScreen(
            state = { LibraryUiState.Ready(items = listOf(lake, waves, notes)) },
            onPlay = { calls += "play:${it.id}" },
            onOpen = { calls += "open:${it.id}" },
        )

        meta("lake").assertTextEquals("720p · 96 MB")
        meta("waves").assertTextEquals("M4A · 7 MB")
        composeRule.onNodeWithTag("library-item-lake").assert(hasMergedText("4:12"))
        composeRule.onNodeWithTag("library-item-waves").assert(hasMergedText("2:58"))

        composeRule.onNodeWithTag("library-item-lake").performClick()
        composeRule.onNodeWithTag("library-item-waves").performClick()
        composeRule.onNodeWithTag("library-item-notes").performClick()

        assertEquals(listOf("play:lake", "play:waves", "open:notes"), calls)
    }

    @Test
    fun theMoreMenuOffersOnlyTheActionsThatApply() {
        val calls = mutableListOf<String>()
        setScreen(
            state = { LibraryUiState.Ready(items = listOf(lake, notes)) },
            onPlay = { calls += "play:${it.id}" },
            onOpen = { calls += "open:${it.id}" },
            onShare = { calls += "share:${it.id}" },
            onRequestDelete = { calls += "delete:${it.id}" },
        )

        composeRule.onNodeWithContentDescription("More options for Mountain Lake").performClick()
        composeRule.onNodeWithTag("library-play-lake").performClick()
        composeRule.onNodeWithTag("library-more-lake").performClick()
        composeRule.onNodeWithTag("library-share-lake").performClick()
        composeRule.onNodeWithTag("library-more-notes").performClick()
        composeRule.onAllNodesWithTag("library-play-notes").assertCountEquals(0)
        composeRule.onNodeWithTag("library-open-notes").performClick()
        composeRule.onNodeWithTag("library-more-notes").performClick()
        composeRule.onNodeWithTag("library-delete-notes").performClick()

        assertEquals(listOf("play:lake", "share:lake", "open:notes", "delete:notes"), calls)
    }

    @Test
    fun tilesOfferTheirMenuToAccessibilityServicesAndMarkWhatPlays() {
        setScreen(
            state = { LibraryUiState.Ready(items = listOf(lake, waves)) },
            playingId = "waves",
        )

        val labels = composeRule.onNodeWithTag("library-item-lake").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .map { it.label }
        assertEquals(listOf("Open with…", "Share", "Delete"), labels)
        composeRule.onNodeWithTag("library-item-waves")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Playing"))
        composeRule.onNodeWithTag("library-item-lake")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun filtersAndSearchNarrowTheGrid() {
        setScreen(state = { LibraryUiState.Ready(items = listOf(lake, waves)) })

        composeRule.onNodeWithTag("library-filter-audio").performClick()
        composeRule.onAllNodesWithTag("library-item-lake").assertCountEquals(0)
        composeRule.onNodeWithTag("library-item-waves").assertIsDisplayed()
        composeRule.onNodeWithTag("library-filter-video").performClick()
        composeRule.onAllNodesWithTag("library-item-waves").assertCountEquals(0)
        composeRule.onNodeWithTag("library-item-lake").assertIsDisplayed()
        composeRule.onNodeWithTag("library-filter-all").performClick()

        composeRule.onNodeWithTag("library-search").performClick()
        composeRule.onNodeWithTag("library-search-field").performTextInput("ocean")
        composeRule.onAllNodesWithTag("library-item-lake").assertCountEquals(0)
        composeRule.onNodeWithTag("library-item-waves").assertIsDisplayed()

        composeRule.onNodeWithTag("library-search-field").performTextReplacement("zzz")
        composeRule.onNodeWithTag("library-no-match").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing matches \u201Czzz\u201D").assertIsDisplayed()

        composeRule.onNodeWithTag("library-search-close").performClick()
        composeRule.onAllNodesWithTag("library-search-field").assertCountEquals(0)
        composeRule.onNodeWithTag("library-item-lake").assertIsDisplayed()
        composeRule.onNodeWithTag("library-item-waves").assertIsDisplayed()
    }

    @Test
    fun aFilterWithNothingInItSaysSo() {
        setScreen(state = { LibraryUiState.Ready(items = listOf(lake)) })

        composeRule.onNodeWithTag("library-filter-audio").performClick()

        composeRule.onNodeWithText("No audio yet").assertIsDisplayed()
    }

    @Test
    fun theSortMenuReordersTheGrid() {
        setScreen(state = { LibraryUiState.Ready(items = listOf(lake, waves)) })
        // Newest first: Ocean Waves (30) before Mountain Lake (20).
        assertTrue(left("waves") < left("lake"))

        composeRule.onNodeWithContentDescription("Sort, newest first").performClick()
        composeRule.onNodeWithTag("library-sort-largest").performClick()
        assertTrue(left("lake") < left("waves"))

        composeRule.onNodeWithTag("library-sort").performClick()
        composeRule.onNodeWithTag("library-sort-oldest").performClick()
        assertTrue(left("lake") < left("waves"))
        composeRule.onNodeWithContentDescription("Sort, oldest first").assertExists()
    }

    @Test
    fun deleteAsksForConfirmationFirst() {
        val state = mutableStateOf(LibraryUiState.Ready(items = listOf(lake)))
        var confirmed = 0
        setScreen(
            state = { state.value },
            onRequestDelete = { state.value = state.value.copy(pendingDelete = it) },
            onDismissDelete = { state.value = state.value.copy(pendingDelete = null) },
            onConfirmDelete = {
                confirmed += 1
                state.value = state.value.copy(pendingDelete = null)
            },
        )

        composeRule.onNodeWithTag("library-more-lake").performClick()
        composeRule.onNodeWithTag("library-delete-lake").performClick()
        composeRule.onNodeWithText("Delete this file?").assertIsDisplayed()
        composeRule.onNodeWithTag("library-delete-dismiss").performClick()
        composeRule.onAllNodesWithTag("library-delete-dialog").assertCountEquals(0)
        assertEquals(0, confirmed)

        composeRule.onNodeWithTag("library-more-lake").performClick()
        composeRule.onNodeWithTag("library-delete-lake").performClick()
        composeRule.onNodeWithText(
            "Mountain Lake.mp4 will be removed from Download/YFT. This cannot be undone.",
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("library-delete-confirm").performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun theLastOutcomeShowsUntilDismissed() {
        var dismissed = 0
        setScreen(
            state = { LibraryUiState.Ready(items = listOf(lake), message = "Deleted a.mp4.") },
            onDismissMessage = { dismissed += 1 },
        )

        composeRule.onNodeWithTag("library-message").assertTextEquals("Deleted a.mp4.")
        composeRule.onNodeWithTag("library-message-dismiss").performClick()
        assertEquals(1, dismissed)
    }

    private fun meta(id: String) =
        composeRule.onNodeWithTag("library-meta-$id", useUnmergedTree = true)

    private fun left(id: String) =
        composeRule.onNodeWithTag("library-item-$id").getUnclippedBoundsInRoot().left

    private fun hasMergedText(text: String) = SemanticsMatcher("has text $text") { node ->
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text == text }
    }

    private fun setScreen(
        state: () -> LibraryUiState,
        playingId: String? = null,
        onRefresh: () -> Unit = {},
        onPlay: (LibraryItem) -> Unit = {},
        onOpen: (LibraryItem) -> Unit = {},
        onShare: (LibraryItem) -> Unit = {},
        onRequestDelete: (LibraryItem) -> Unit = {},
        onConfirmDelete: () -> Unit = {},
        onDismissDelete: () -> Unit = {},
        onDismissMessage: () -> Unit = {},
    ) {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalMediaDetailsSource provides details) {
                    LibraryScreen(
                        uiState = state(),
                        playingId = playingId,
                        onRefresh = onRefresh,
                        onPlay = onPlay,
                        onOpen = onOpen,
                        onShare = onShare,
                        onRequestDelete = onRequestDelete,
                        onConfirmDelete = onConfirmDelete,
                        onDismissDelete = onDismissDelete,
                        onDismissMessage = onDismissMessage,
                    )
                }
            }
        }
    }
}
