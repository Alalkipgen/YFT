package com.alal.yft.feature.browser

import com.alal.yft.core.data.history.BrowserHistoryEntry
import com.alal.yft.core.model.settings.BrowserPreferences
import com.alal.yft.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserHistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val history = FakeBrowserHistoryRepository()
    private val preferences = FakeBrowserPreferencesRepository()
    private var now = 1_000L

    @Test
    fun visitsAreKeptWhileSavingIsOnAndNothingNewAfterItIsTurnedOff() = runTest {
        val viewModel = BrowserHistoryViewModel(history, preferences) { now }
        runCurrent()
        assertTrue(viewModel.uiState.value.saving)

        viewModel.recordVisit("https://example.com/a?utm_source=x#top", "A")
        now = 2_000L
        viewModel.recordVisit("https://example.com/a", null)
        viewModel.renamePage("https://example.com/a", "A, renamed")
        runCurrent()
        assertEquals(
            listOf(BrowserHistoryEntry(PAGE_A, "A, renamed", "example.com", 2_000, 2)),
            viewModel.uiState.value.pages,
        )

        preferences.set(BrowserPreferences(saveHistory = false))
        runCurrent()
        viewModel.recordVisit("https://example.com/b", "B")
        viewModel.renamePage("https://example.com/a", "Not kept")
        runCurrent()

        assertFalse(viewModel.uiState.value.saving)
        assertEquals(listOf("A, renamed"), viewModel.uiState.value.pages.map { it.title })
    }

    @Test
    fun recentIsTheLastSixPagesAndTheWordsFilterTheList() = runTest {
        history.pages.value = (1..8).map { page(it) }
        val viewModel = BrowserHistoryViewModel(history, preferences) { now }
        runCurrent()

        val recent = viewModel.uiState.value.recent.map { it.title }
        assertEquals((8 downTo 3).map { "Page $it" }, recent)
        assertEquals(8, viewModel.uiState.value.pages.size)

        viewModel.onQueryChanged("page 7")
        runCurrent()

        assertEquals("page 7", viewModel.uiState.value.query)
        assertEquals(listOf("Page 7"), viewModel.uiState.value.pages.map { it.title })
        assertEquals(6, viewModel.uiState.value.recent.size)
    }

    @Test
    fun oneDeleteRemovesOnePageAndClearRemovesAll() = runTest {
        history.pages.value = (1..3).map { page(it) }
        val viewModel = BrowserHistoryViewModel(history, preferences) { now }
        runCurrent()

        viewModel.delete("https://example.com/2")
        runCurrent()
        assertEquals(listOf("Page 3", "Page 1"), viewModel.uiState.value.pages.map { it.title })

        viewModel.clear()
        runCurrent()
        assertEquals(emptyList<BrowserHistoryEntry>(), viewModel.uiState.value.pages)
        assertEquals(emptyList<BrowserHistoryEntry>(), viewModel.uiState.value.recent)
    }

    private fun page(index: Int) = BrowserHistoryEntry(
        url = "https://example.com/$index",
        title = "Page $index",
        host = "example.com",
        lastVisitedAtEpochMs = index * 1_000L,
        visitCount = 1,
    )

    private companion object {
        const val PAGE_A = "https://example.com/a"
    }
}
