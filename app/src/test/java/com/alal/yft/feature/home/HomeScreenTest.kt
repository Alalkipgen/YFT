package com.alal.yft.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.feature.library.LibraryItem
import com.alal.yft.feature.library.LibraryLocation
import com.alal.yft.ui.components.PromptboxStatus
import com.alal.yft.ui.theme.YftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var state by mutableStateOf(HomeUiState())
    private var copiedLinkHint by mutableStateOf(false)
    private val actions = mutableListOf<HomeAction>()
    private val openedBrowser = mutableListOf<String?>()
    private var openedDetectedMedia = 0
    private var openedLibrary = 0

    private fun setContent(initial: HomeUiState = HomeUiState()) {
        state = initial
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                HomeScreen(
                    state = state,
                    onAction = { action ->
                        actions += action
                        if (action is HomeAction.LinkChanged) state = state.copy(link = action.text)
                    },
                    onOpenBrowser = { openedBrowser += it },
                    onOpenDetectedMedia = { openedDetectedMedia++ },
                    onOpenLibrary = { openedLibrary++ },
                    copiedLinkHint = copiedLinkHint,
                )
            }
        }
    }

    @Test
    fun typedLinkIsLookedUpFromTheArrow() {
        setContent()

        composeRule.onNodeWithTag("home-link").performTextInput("example.com/watch")
        composeRule.onNodeWithTag("home-open-link").performClick()

        assertEquals(HomeAction.LinkChanged("example.com/watch"), actions.first())
        assertEquals(HomeAction.Submit, actions.last())
        composeRule.onNodeWithTag("home-link-clear").performClick()
        assertEquals(HomeAction.ClearLink, actions.last())
    }

    @Test
    fun arrowWithoutALinkSubmitsNothing() {
        setContent()

        composeRule.onNodeWithTag("home-open-link").performClick()

        assertTrue(actions.none { it == HomeAction.Submit })
    }

    @Test
    fun pasteReadsTheClipboardOnlyWhenTapped() {
        clipboard().setPrimaryClip(ClipData.newPlainText("copied", "Look: https://a.test/v/1."))
        setContent()
        assertTrue(actions.isEmpty())

        composeRule.onNodeWithTag("home-paste").performClick()

        assertEquals(listOf(HomeAction.Pasted("Look: https://a.test/v/1.")), actions)
    }

    @Test
    fun copiedLinkRowAppearsOnlyForAnEmptyFieldAndUsesTheClipboard() {
        clipboard().setPrimaryClip(ClipData.newPlainText("copied", "https://a.test/v/2"))
        copiedLinkHint = true
        setContent()

        composeRule.onNodeWithText("Use copied link").assertIsDisplayed()
        composeRule.onNodeWithTag("home-use-clipboard").performClick()
        assertEquals(listOf(HomeAction.UseCopied("https://a.test/v/2")), actions)

        state = state.copy(link = "typed")
        composeRule.onNodeWithTag("home-use-clipboard").assertDoesNotExist()
    }

    @Test
    fun openBrowserCarriesTheTypedLinkButNotDuringASearch() {
        setContent()

        composeRule.onNodeWithTag("home-open-browser").performClick()
        state = state.copy(link = "  example.com  ")
        composeRule.onNodeWithTag("home-open-browser").performClick()
        state = state.copy(status = PromptboxStatus.Searching)
        composeRule.onNodeWithTag("home-open-browser").performClick()

        assertEquals(listOf(null, "example.com", null), openedBrowser)
    }

    @Test
    fun searchingCanBeCancelled() {
        setContent(HomeUiState(link = "https://a.test", status = PromptboxStatus.Searching))

        composeRule.onNodeWithTag("home-search-progress").assertIsDisplayed()
        composeRule.onNodeWithTag("home-search-cancel").performClick()

        assertEquals(listOf(HomeAction.CancelSearch), actions)
    }

    @Test
    fun foundMediaOpensTheDetectedList() {
        setContent(HomeUiState(link = "https://a.test", status = PromptboxStatus.Found(3)))

        composeRule.onNodeWithText("3 media found").assertIsDisplayed()
        composeRule.onNodeWithTag("home-view-media").performClick()
        assertEquals(1, openedDetectedMedia)

        composeRule.onNodeWithTag("home-found").performClick()
        assertEquals(listOf(HomeAction.EditLink), actions)
    }

    @Test
    fun noMediaOffersTheBrowserWithTheSameLink() {
        setContent(
            HomeUiState(
                link = "https://a.test/page",
                status = PromptboxStatus.NotFound(PromptboxStatus.NO_MEDIA_MESSAGE),
            ),
        )

        composeRule.onNodeWithText(PromptboxStatus.NO_MEDIA_MESSAGE).assertIsDisplayed()
        composeRule.onNodeWithTag("home-open-in-browser").performClick()
        assertEquals(listOf<String?>("https://a.test/page"), openedBrowser)

        state = state.copy(
            status = PromptboxStatus.NotFound("Only HTTPS pages are supported", false),
        )
        composeRule.onNodeWithText("Only HTTPS pages are supported").assertIsDisplayed()
        composeRule.onNodeWithTag("home-open-in-browser").assertDoesNotExist()
    }

    @Test
    fun notFoundDetailsAreCopiedOnlyWhenTapped() {
        clipboard().setPrimaryClip(ClipData.newPlainText("copied", "original clipboard"))
        setContent(
            HomeUiState(
                link = "https://a.test/page",
                status = PromptboxStatus.NotFound(PromptboxStatus.NO_MEDIA_MESSAGE),
            ),
        )

        assertEquals("original clipboard", clipboard().primaryClip!!.getItemAt(0).text)
        composeRule.onNodeWithTag("home-copy-details").assertIsDisplayed().performClick()

        assertEquals(
            "lookup: ${PromptboxStatus.NO_MEDIA_MESSAGE}",
            clipboard().primaryClip!!.getItemAt(0).text,
        )
        assertTrue(actions.isEmpty())
    }

    @Test
    fun copiedFailureDetailsExcludeUrlsAndSessionValuesAndHideWhenFound() {
        setContent(
            HomeUiState(
                link = "https://a.test/page",
                status = PromptboxStatus.NotFound("No media"),
                failureDetails = listOf(
                    "page GET 403",
                    "Cookie: redaction-fixture",
                    "media https://a.test/private?opaque=redaction-fixture",
                    "pot=redaction-fixture",
                ),
            ),
        )
        composeRule.onNodeWithTag("home-copy-details").performClick()
        assertEquals(
            "page GET 403\nmedia https://a.test",
            clipboard().primaryClip!!.getItemAt(0).text,
        )
        state = state.copy(status = PromptboxStatus.Found(1))
        composeRule.onNodeWithTag("home-copy-details").assertDoesNotExist()
        state = state.copy(status = PromptboxStatus.Searching)
        composeRule.onNodeWithTag("home-copy-details").assertDoesNotExist()
    }

    @Test
    fun sitesOpenInTheBrowserAndEditModeRemovesThem() {
        val archive = HomeSite("Archive", "https://archive.org")
        setContent(HomeUiState(sites = listOf(archive)))

        composeRule.onNodeWithTag("home-site-https://archive.org").performClick()
        assertEquals(listOf<String?>("https://archive.org"), openedBrowser)
        composeRule.onNodeWithTag("home-sites-edit").assertTextEquals("Edit").performClick()
        assertEquals(HomeAction.ToggleEditSites, actions.last())

        state = state.copy(editingSites = true)
        composeRule.onNodeWithTag("home-sites-edit").assertTextEquals("Done")
        composeRule.onNodeWithTag("home-site-add").assertDoesNotExist()
        composeRule.onNodeWithTag("home-site-https://archive.org").performClick()
        assertEquals(HomeAction.RemoveSite(archive), actions.last())
        assertEquals(1, openedBrowser.size)
    }

    @Test
    fun addSiteDialogReportsProblemsAndSaves() {
        setContent()

        composeRule.onNodeWithTag("home-site-add").performClick()
        assertEquals(HomeAction.AddSite, actions.last())

        state = state.copy(
            siteDialog = SiteDialogState(addressError = "Enter a valid HTTPS address"),
        )
        composeRule.onNodeWithText("Enter a valid HTTPS address").assertIsDisplayed()
        composeRule.onNodeWithTag("home-site-name").performTextInput("Docs")
        composeRule.onNodeWithTag("home-site-address").performTextInput("docs.test")
        composeRule.onNodeWithTag("home-site-save").performClick()

        assertTrue(HomeAction.SiteNameChanged("Docs") in actions)
        assertTrue(HomeAction.SiteAddressChanged("docs.test") in actions)
        assertEquals(HomeAction.SaveSite, actions.last())
        composeRule.onNodeWithTag("home-site-cancel").performClick()
        assertEquals(HomeAction.DismissSiteDialog, actions.last())
    }

    @Test
    fun recentShowsTheNewestDownloadsAndOpensTheLibrary() {
        setContent(
            HomeUiState(
                recent = listOf(
                    item(1, "Sunset timelapse.mp4", "video/mp4", 96L * 1024 * 1024),
                    item(2, "Morning talk.m4a", "audio/mp4", 7_759_462),
                ),
            ),
        )

        composeRule.onNodeWithTag("home-list")
            .performScrollToNode(hasTestTag("home-recent-2"))
        composeRule.onNode(hasText("Sunset timelapse")).assertIsDisplayed()
        composeRule.onNode(hasText("MP4 · 96 MB")).assertIsDisplayed()
        composeRule.onNode(hasText("M4A · 7.4 MB")).assertIsDisplayed()
        composeRule.onNodeWithTag("home-recent-1").performClick()
        composeRule.onNodeWithTag("home-recent-all").performClick()

        assertEquals(2, openedLibrary)
    }

    @Test
    fun emptyRecentExplainsWhereDownloadsAppear() {
        setContent()

        composeRule.onNodeWithTag("home-list")
            .performScrollToNode(hasTestTag("home-recent-empty"))
        composeRule.onNodeWithTag("home-recent-empty").assertIsDisplayed()
    }

    private fun clipboard() = ApplicationProvider.getApplicationContext<Context>()
        .getSystemService(ClipboardManager::class.java)

    private fun item(id: Int, name: String, mime: String, size: Long) = LibraryItem(
        id = id.toString(),
        displayName = name,
        uri = "content://media/external/downloads/$id",
        mimeType = mime,
        sizeBytes = size,
        modifiedAtEpochMs = id.toLong(),
        location = LibraryLocation.SHARED_DOWNLOADS,
    )
}
