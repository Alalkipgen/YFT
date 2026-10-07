package com.alal.yft.feature.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.alal.yft.core.data.history.BrowserHistoryEntry
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.ui.theme.YftTheme
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class BrowserHistoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val zone = TimeZone.getDefault()
    private val now = Calendar.getInstance(zone, Locale.US).apply {
        set(2026, Calendar.OCTOBER, 7, 15, 30, 0)
    }.timeInMillis

    @Test
    fun pagesAreGroupedByDayNewestFirstWithTheirHostAndTime() {
        val groups = historyGroups(pages(), now, zone)

        assertEquals(listOf("Today", "Yesterday", "Earlier"), groups.map { it.title })
        assertEquals(listOf("News", "YouTube"), groups[0].pages.map { it.title })
        assertEquals(listOf("Shop"), groups[1].pages.map { it.title })
        assertEquals(listOf("Old page", "Last year"), groups[2].pages.map { it.title })
        assertEquals("15:00", historyTime(now - 30 * MINUTE, now, zone))
        assertEquals("09:30", historyTime(now - 30 * HOUR, now, zone))
        assertEquals("27 Sep", historyTime(now - 10 * DAY, now, zone))
        assertEquals("7 Oct 2025", historyTime(now - 365 * DAY, now, zone))
    }

    @Test
    fun aRowOpensItsPageItsMenuDeletesItAndTheWordsSearch() {
        val opened = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var query by mutableStateOf("")
        setPanel(
            state = { BrowserHistoryUiState(query = query, pages = filtered(query)) },
            onQueryChanged = { query = it },
            onOpen = { opened += it.url },
            onDelete = { deleted += it.url },
        )

        composeRule.onNodeWithTag("browser-history").assertIsDisplayed()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("Yesterday").assertIsDisplayed()
        composeRule.onNodeWithText("example.com · 15:00").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-history-page-$NEWS").performClick()
        assertEquals(listOf(NEWS), opened)

        composeRule.onNodeWithTag("browser-history-menu-$SHOP").performClick()
        composeRule.onNodeWithTag("browser-history-delete").performClick()
        assertEquals(listOf(SHOP), deleted)

        composeRule.onNodeWithTag("browser-history-search").performTextInput("tube")
        composeRule.waitForIdle()
        assertEquals("tube", query)
        composeRule.onNodeWithText("YouTube").assertIsDisplayed()
        composeRule.onNodeWithText("News").assertDoesNotExist()

        composeRule.onNodeWithTag("browser-history-search-clear").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("News").assertIsDisplayed()
    }

    @Test
    fun clearHistoryAsksFirstAndAnEmptyListSaysWhatComesThere() {
        var cleared = 0
        var pages by mutableStateOf(pages())
        setPanel(
            state = { BrowserHistoryUiState(pages = pages, saving = false) },
            onClear = {
                cleared++
                pages = emptyList()
            },
        )
        composeRule.onNodeWithTag("browser-history-off").assertIsDisplayed()

        composeRule.onNodeWithTag("browser-history-clear").performClick()
        composeRule.onNodeWithTag("browser-history-clear-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("browser-history-clear-cancel").performClick()
        assertEquals(0, cleared)
        composeRule.onNodeWithText("News").assertIsDisplayed()

        composeRule.onNodeWithTag("browser-history-clear").performClick()
        composeRule.onNodeWithTag("browser-history-clear-confirm").performClick()
        composeRule.waitForIdle()

        assertEquals(1, cleared)
        composeRule.onNodeWithTag("browser-history-empty")
            .assertIsDisplayed()
            .assert(hasText("Pages you open in the browser appear here."))
        composeRule.onNodeWithTag("browser-history-clear").assertIsNotEnabled()
    }

    @Test
    fun theStartPageShowsTheRecentPagesAndTheMenuOpensHistory() {
        val opened = mutableListOf<String>()
        var historyShown = 0
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                BrowserScreen(
                    uiState = BrowserUiState(),
                    canGoBack = false,
                    canGoForward = false,
                    onAddressChanged = {},
                    onGo = {},
                    onBrowserBack = {},
                    onBrowserForward = {},
                    onReload = {},
                    onStop = {},
                    onDownloadGroup = {},
                    onNavigateBack = {},
                    hasBrowserPage = false,
                    recentPages = pages().take(2),
                    onOpenRecent = { opened += it.url },
                    onShowHistory = { historyShown++ },
                    browserSurface = { Box(modifier = it.testTag("test-page")) },
                )
            }
        }

        composeRule.onNodeWithTag("browser-recent").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("browser-recent-0").assert(hasText("News"))
        composeRule.onNodeWithTag("browser-recent-1").performClick()
        assertEquals(listOf(YOUTUBE), opened)

        composeRule.onNodeWithTag("browser-recent-history").performClick()
        composeRule.onNodeWithTag("browser-menu").performClick()
        composeRule.onNodeWithTag("browser-menu-history").performClick()
        assertEquals(2, historyShown)
    }

    @Test
    fun withoutPagesTheStartPageHasNoRecent() {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                BrowserStartPage(
                    query = "",
                    sites = emptyList(),
                    copiedLinkHint = false,
                    onSearch = {},
                    onDownloadCopiedLink = {},
                    onUseCopiedLink = {},
                    onOpenSite = {},
                    onEditSites = {},
                )
            }
        }

        composeRule.onNodeWithTag("browser-start").assertIsDisplayed()
        composeRule.onNode(hasTestTag("browser-recent")).assertDoesNotExist()
    }

    private fun setPanel(
        state: () -> BrowserHistoryUiState,
        onQueryChanged: (String) -> Unit = {},
        onOpen: (BrowserHistoryEntry) -> Unit = {},
        onDelete: (BrowserHistoryEntry) -> Unit = {},
        onClear: () -> Unit = {},
    ) {
        composeRule.setContent {
            YftTheme(themeMode = ThemeMode.LIGHT) {
                BrowserHistoryPanel(
                    state = state(),
                    nowEpochMs = now,
                    onQueryChanged = onQueryChanged,
                    onOpen = onOpen,
                    onDelete = onDelete,
                    onClear = onClear,
                    onClose = {},
                )
            }
        }
    }

    private fun filtered(query: String) = pages().filter {
        query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)
    }

    private fun pages() = listOf(
        entry(NEWS, "News", "example.com", now - 30 * MINUTE),
        entry(YOUTUBE, "YouTube", "m.youtube.com", now - 2 * HOUR),
        entry(SHOP, "Shop", "shop.example", now - 30 * HOUR),
        entry("https://old.example/", "Old page", "old.example", now - 10 * DAY),
        entry("https://older.example/", "Last year", "older.example", now - 365 * DAY),
    )

    private fun entry(url: String, title: String, host: String, at: Long) =
        BrowserHistoryEntry(url, title, host, at, visitCount = 1)

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
        const val NEWS = "https://example.com/news"
        const val YOUTUBE = "https://m.youtube.com/"
        const val SHOP = "https://shop.example/item"
    }
}
