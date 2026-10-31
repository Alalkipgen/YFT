package com.alal.yft.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1.0.2 item 5b: a long press on a Library tile starts select mode; taps pick and drop tiles,
 * and Delete asks once ("Delete 3 files?") for all of them. Before 5b the Library deleted one
 * file at a time from its ⋯ menu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class LibrarySelectModeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val alpha = libraryItem("alpha", "Alpha.mp4", modifiedAt = 40)
    private val beta = libraryItem(
        id = "beta",
        name = "Beta.mp4",
        modifiedAt = 30,
        location = LibraryLocation.SHARED_DOWNLOADS,
    )

    /** Item 28: a file in the folder picked in Android's folder picker. */
    private val clip = libraryItem(
        id = "clip",
        name = "Gamma clip.mp4",
        modifiedAt = 20,
        location = LibraryLocation.CHOSEN_FOLDER,
    ).copy(place = "Movies")
    private val song = libraryItem("song", "Song.m4a", modifiedAt = 10)

    @Test
    fun aLongPressStartsSelectModeAndTapsPickAndDrop() {
        val played = mutableListOf<String>()
        setScreen(onPlay = { played += it.id })
        composeRule.onAllNodesWithTag("library-select-bar").assertCountEquals(0)

        tile("alpha").performTouchInput { longClick() }

        composeRule.onNodeWithTag("library-select-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("library-select-count").assertTextEquals("1 selected")
        tile("alpha").assert(isSelected(true))
        composeRule.onNodeWithTag("library-pick-alpha", useUnmergedTree = true).assertIsDisplayed()
        // The bar stands where the title was, and the tiles have no ⋯ in select mode.
        composeRule.onAllNodesWithText("Library").assertCountEquals(0)
        composeRule.onAllNodesWithTag("library-more-alpha").assertCountEquals(0)

        // A tap picks; it plays nothing.
        tile("beta").performClick()
        composeRule.onNodeWithTag("library-select-count").assertTextEquals("2 selected")
        tile("beta").assert(isSelected(true))
        tile("clip").assert(isSelected(false))
        assertEquals(emptyList<String>(), played)

        // Another tap drops it; select mode ends with the last one.
        tile("beta").performClick()
        composeRule.onNodeWithTag("library-select-count").assertTextEquals("1 selected")
        composeRule.onAllNodesWithTag("library-pick-beta", useUnmergedTree = true)
            .assertCountEquals(0)
        tile("alpha").performClick()
        composeRule.onAllNodesWithTag("library-select-bar").assertCountEquals(0)
        composeRule.onNodeWithText("Library").assertIsDisplayed()
        composeRule.onNodeWithTag("library-more-alpha").assertExists()
        tile("alpha").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        tile("alpha").performClick()
        assertEquals(listOf("alpha"), played)
    }

    @Test
    fun selectAllPicksEveryShownFileAndCancelOrBackLeaves() {
        setScreen()

        tile("alpha").performTouchInput { longClick() }
        composeRule.onNodeWithTag("library-select-delete").assertTextEquals("Delete file")
        composeRule.onNodeWithTag("library-select-all").assertIsEnabled().performClick()

        composeRule.onNodeWithTag("library-select-count").assertTextEquals("4 selected")
        composeRule.onNodeWithTag("library-select-all").assertIsNotEnabled()
        composeRule.onNodeWithTag("library-select-delete").assertTextEquals("Delete files")

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onAllNodesWithTag("library-select-bar").assertCountEquals(0)
        composeRule.onNodeWithText("Library").assertIsDisplayed()

        tile("clip").performTouchInput { longClick() }
        composeRule.onNodeWithTag("library-select-cancel").performClick()
        composeRule.onAllNodesWithTag("library-select-bar").assertCountEquals(0)
    }

    @Test
    fun anotherFilterOrAChangedSearchEndsSelectMode() {
        setScreen()

        tile("alpha").performTouchInput { longClick() }
        composeRule.onNodeWithTag("library-filter-video").performClick()
        composeRule.onAllNodesWithTag("library-select-bar").assertCountEquals(0)

        // Select all picks only what the filter shows; the same filter again keeps them.
        tile("beta").performTouchInput { longClick() }
        composeRule.onNodeWithTag("library-select-all").performClick()
        composeRule.onNodeWithTag("library-select-count").assertTextEquals("3 selected")
        composeRule.onNodeWithTag("library-filter-video").performClick()
        composeRule.onNodeWithTag("library-select-count").assertTextEquals("3 selected")
        composeRule.onNodeWithTag("library-select-cancel").performClick()

        composeRule.onNodeWithTag("library-search").performClick()
        composeRule.onNodeWithTag("library-search-field").performTextInput("a")
        tile("clip").performTouchInput { longClick() }
        composeRule.onNodeWithTag("library-select-count").assertTextEquals("1 selected")
        composeRule.onNodeWithTag("library-search-field").performTextInput("m")
        composeRule.onAllNodesWithTag("library-select-bar").assertCountEquals(0)
        tile("clip").assertExists()
    }

    @Test
    fun deleteAsksOnceForThePickedFilesAndEveryOneGoes() {
        val repository = RecordingRepository(mutableListOf(alpha, beta, clip, song))
        val viewModel = LibraryViewModel(repository, FakeLibraryPlayback())
        viewModel.refresh()
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalMediaDetailsSource provides MediaDetailsSource.None) {
                    val state by viewModel.uiState.collectAsState()
                    LibraryScreen(
                        uiState = state,
                        onRequestDelete = viewModel::requestDelete,
                        onRequestDeletePicked = viewModel::requestDeletePicked,
                        onConfirmDelete = viewModel::confirmDelete,
                        onDismissDelete = viewModel::dismissDelete,
                        onDismissMessage = viewModel::dismissMessage,
                    )
                }
            }
        }

        tile("alpha").performTouchInput { longClick() }
        tile("beta").performClick()
        tile("clip").performClick()
        composeRule.onNodeWithTag("library-select-delete").performClick()

        composeRule.onNodeWithText("Delete 3 files?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "These 3 files will be removed from App storage, Download/YFT and Movies. " +
                "This can't be undone.",
        ).assertIsDisplayed()
        // Cancel keeps what was picked.
        composeRule.onNodeWithTag("library-delete-dismiss").performClick()
        composeRule.onAllNodesWithTag("library-delete-dialog").assertCountEquals(0)
        composeRule.onNodeWithTag("library-select-count").assertTextEquals("3 selected")
        assertTrue(repository.calls.isEmpty())

        composeRule.onNodeWithTag("library-select-delete").performClick()
        composeRule.onAllNodesWithText("Delete 3 files?").assertCountEquals(1)
        composeRule.onNodeWithTag("library-delete-confirm").performClick()

        composeRule.onAllNodesWithTag("library-delete-dialog").assertCountEquals(0)
        composeRule.onAllNodesWithTag("library-select-bar").assertCountEquals(0)
        composeRule.onNodeWithTag("library-message").assertTextEquals("Deleted 3 files.")
        assertEquals(listOf(listOf("alpha", "beta", "clip")), repository.calls)
        listOf("alpha", "beta", "clip").forEach { id ->
            composeRule.onAllNodesWithTag("library-item-$id").assertCountEquals(0)
        }
        tile("song").assertIsDisplayed()
    }

    @Test
    fun theQuestionNamesTheFileOrWhereThePickedFilesAre() {
        assertEquals(
            "Alpha.mp4 will be removed from App storage. This can't be undone.",
            deleteQuestionText(listOf(alpha)),
        )
        assertEquals(
            "These 2 files will be removed from App storage. This can't be undone.",
            deleteQuestionText(listOf(alpha, song)),
        )
        assertEquals(
            "These 3 files will be removed from Download/YFT and App storage. " +
                "This can't be undone.",
            deleteQuestionText(listOf(beta, alpha, song)),
        )
    }

    private fun tile(id: String) = composeRule.onNodeWithTag("library-item-$id")

    private fun isSelected(selected: Boolean) =
        SemanticsMatcher.expectValue(SemanticsProperties.Selected, selected)

    private fun setScreen(onPlay: (LibraryItem) -> Unit = {}) {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalMediaDetailsSource provides MediaDetailsSource.None) {
                    LibraryScreen(
                        uiState = LibraryUiState.Ready(items = listOf(alpha, beta, clip, song)),
                        onPlay = onPlay,
                    )
                }
            }
        }
    }

    /** Deletes what it is asked to and records each call's files. */
    private class RecordingRepository(val items: MutableList<LibraryItem>) : LibraryRepository {
        val calls = mutableListOf<List<String>>()

        override suspend fun items(): List<LibraryItem> = items.toList()

        override suspend fun delete(item: LibraryItem): Boolean = error("deleteAll only")

        override suspend fun deleteAll(items: List<LibraryItem>): LibraryDeletion {
            calls += items.map(LibraryItem::id)
            this.items.removeAll(items)
            return LibraryDeletion(deleted = items)
        }
    }
}
