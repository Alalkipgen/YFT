package com.alal.yft.feature.home

import com.alal.yft.core.data.preferences.HomeSitesRepository
import com.alal.yft.core.data.preferences.SettingsRepository
import com.alal.yft.core.model.ThemeMode
import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.core.model.settings.HomeSites
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.library.LibraryItem
import com.alal.yft.feature.library.LibraryLocation
import com.alal.yft.feature.library.LibraryRepository
import com.alal.yft.testing.MainDispatcherRule
import com.alal.yft.ui.components.PromptboxStatus
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    /** Unconfined, so the combined state is current right after each action. */
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    private val inspector = FakeInspector()
    private val sites = FakeSitesRepository(HomeSites.DEFAULTS)
    private val library = FakeLibrary()
    private val store = DetectedMediaStore()

    private val settings = FakeSettings()
    private val clipboard = FakeClipboard()
    private val watcher = CopiedLinkWatcher(clipboard)

    private fun viewModel() = HomeViewModel(inspector, sites, library, store, settings, watcher)

    @Test
    fun foundMediaIsSharedWithTheDetectedListAndCounted() = runTest {
        val viewModel = viewModel()
        val answer = CompletableDeferred<LinkInspection>()
        inspector.answer = { answer.await() }

        viewModel.onAction(HomeAction.LinkChanged("  https://a.test/watch  "))
        viewModel.onAction(HomeAction.Submit)
        runCurrent()

        assertEquals(PromptboxStatus.Searching, viewModel.uiState.value.status)
        assertEquals("https://a.test/watch", viewModel.uiState.value.link)
        assertEquals(listOf("https://a.test/watch"), inspector.links)

        answer.complete(found(count = 2))
        advanceUntilIdle()

        assertEquals(PromptboxStatus.Found(2), viewModel.uiState.value.status)
        val page = store.page.value!!
        assertEquals("https://a.test/watch", page.pageUrl)
        assertEquals("Clip page", page.pageTitle)
        assertEquals(2, page.candidates.size)
    }

    @Test
    fun protectedMediaIsNeitherCountedNorOffered() = runTest {
        val viewModel = viewModel()
        val mixed = found(count = 3).let { result ->
            result.copy(
                candidates = result.candidates.mapIndexed { index, candidate ->
                    if (index == 0) candidate.copy(drmHint = true) else candidate
                },
            )
        }
        inspector.answer = { mixed }

        viewModel.onAction(HomeAction.LinkChanged("https://a.test/mixed"))
        viewModel.onAction(HomeAction.Submit)
        advanceUntilIdle()
        assertEquals(PromptboxStatus.Found(2), viewModel.uiState.value.status)

        val protectedOnly = found(count = 2).let { result ->
            result.copy(candidates = result.candidates.map { it.copy(drmHint = true) })
        }
        inspector.answer = { protectedOnly }
        viewModel.onAction(HomeAction.Submit)
        advanceUntilIdle()
        assertEquals(
            PromptboxStatus.NotFound(HomeViewModel.PROTECTED_ONLY_MESSAGE),
            viewModel.uiState.value.status,
        )
    }

    @Test
    fun foundCountNeverExceedsWhatTheListKeeps() = runTest {
        val viewModel = viewModel()
        inspector.answer = { found(count = DetectedMediaStore.MAX_CANDIDATES + 5) }

        viewModel.onAction(HomeAction.LinkChanged("https://a.test/many"))
        viewModel.onAction(HomeAction.Submit)
        advanceUntilIdle()

        assertEquals(
            PromptboxStatus.Found(DetectedMediaStore.MAX_CANDIDATES),
            viewModel.uiState.value.status,
        )
    }

    @Test
    fun noMediaKeepsTheMessageAndWhetherTheBrowserMayHelp() = runTest {
        val viewModel = viewModel()
        inspector.answer = { LinkInspection.NotFound("Only HTTPS pages are supported", false) }

        viewModel.onAction(HomeAction.LinkChanged("http://a.test"))
        viewModel.onAction(HomeAction.Submit)
        advanceUntilIdle()

        assertEquals(
            PromptboxStatus.NotFound("Only HTTPS pages are supported", canOpenInBrowser = false),
            viewModel.uiState.value.status,
        )
        assertNull(store.page.value)
    }

    @Test
    fun cancelledSearchIgnoresTheLateAnswer() = runTest {
        val viewModel = viewModel()
        val answer = CompletableDeferred<LinkInspection>()
        inspector.answer = { answer.await() }
        viewModel.onAction(HomeAction.LinkChanged("https://a.test/slow"))
        viewModel.onAction(HomeAction.Submit)
        runCurrent()

        viewModel.onAction(HomeAction.CancelSearch)
        answer.complete(found(count = 1))
        advanceUntilIdle()

        assertEquals(PromptboxStatus.Editing, viewModel.uiState.value.status)
        assertEquals("https://a.test/slow", viewModel.uiState.value.link)
        assertNull(store.page.value)
    }

    @Test
    fun blankLinkIsNotLookedUp() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(HomeAction.LinkChanged("   "))
        viewModel.onAction(HomeAction.Submit)
        advanceUntilIdle()

        assertTrue(inspector.links.isEmpty())
        assertEquals(PromptboxStatus.Editing, viewModel.uiState.value.status)
    }

    @Test
    fun pasteTakesTheLinkOutOfCopiedTextWithoutLookingItUp() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(HomeAction.Pasted("Watch this: https://a.test/v/1."))
        advanceUntilIdle()

        assertEquals("https://a.test/v/1", viewModel.uiState.value.link)
        assertTrue(inspector.links.isEmpty())
        viewModel.onAction(HomeAction.Pasted(null))
        assertEquals("https://a.test/v/1", viewModel.uiState.value.link)
    }

    @Test
    fun usingTheCopiedLinkLooksItUpStraightAway() = runTest {
        val viewModel = viewModel()
        inspector.answer = { found(count = 1) }

        viewModel.onAction(HomeAction.UseCopied("https://a.test/v/2"))
        advanceUntilIdle()

        assertEquals(listOf("https://a.test/v/2"), inspector.links)
        assertEquals(PromptboxStatus.Found(1), viewModel.uiState.value.status)
    }

    @Test
    fun editingAndClearingReturnToTheField() = runTest {
        val viewModel = viewModel()
        inspector.answer = { LinkInspection.NotFound(PromptboxStatus.NO_MEDIA_MESSAGE) }
        viewModel.onAction(HomeAction.LinkChanged("https://a.test/page"))
        viewModel.onAction(HomeAction.Submit)
        advanceUntilIdle()

        viewModel.onAction(HomeAction.EditLink)
        assertEquals(PromptboxStatus.Editing, viewModel.uiState.value.status)
        assertEquals("https://a.test/page", viewModel.uiState.value.link)

        viewModel.onAction(HomeAction.ClearLink)
        assertEquals("", viewModel.uiState.value.link)
    }

    @Test
    fun typedLinksAreCappedAtTheBrowserLimit() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(HomeAction.LinkChanged("a".repeat(HomeLinks.MAX_LENGTH + 10)))

        assertEquals(HomeLinks.MAX_LENGTH, viewModel.uiState.value.link.length)
    }

    @Test
    fun sitesFollowTheRepository() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(HomeSites.DEFAULTS, viewModel.uiState.value.sites)

        viewModel.onAction(HomeAction.RemoveSite(HomeSites.DEFAULTS.first()))
        advanceUntilIdle()

        assertEquals(HomeSites.DEFAULTS.drop(1), viewModel.uiState.value.sites)
    }

    @Test
    fun addSiteChecksTheFieldsBeforeSaving() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(HomeAction.AddSite)
        viewModel.onAction(HomeAction.SaveSite)
        var dialog = viewModel.uiState.value.siteDialog!!
        assertEquals("Enter a name", dialog.nameError)
        assertEquals("Enter a web address", dialog.addressError)

        viewModel.onAction(HomeAction.SiteNameChanged("Docs"))
        viewModel.onAction(HomeAction.SiteAddressChanged("http://docs.test"))
        viewModel.onAction(HomeAction.SaveSite)
        dialog = viewModel.uiState.value.siteDialog!!
        assertNull(dialog.nameError)
        assertEquals("Only HTTPS pages are supported", dialog.addressError)

        viewModel.onAction(HomeAction.SiteAddressChanged("m.youtube.com"))
        viewModel.onAction(HomeAction.SaveSite)
        assertEquals(
            "This site is already on Home",
            viewModel.uiState.value.siteDialog!!.addressError,
        )

        viewModel.onAction(HomeAction.SiteAddressChanged("docs.test/start"))
        assertNull(viewModel.uiState.value.siteDialog!!.addressError)
        viewModel.onAction(HomeAction.SaveSite)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.siteDialog)
        assertEquals(
            HomeSite("Docs", "https://docs.test/start"),
            viewModel.uiState.value.sites.last(),
        )
    }

    @Test
    fun addSiteStopsAtTheLimit() = runTest {
        sites.value.value = (1..HomeSites.MAX_SITES).map { index ->
            HomeSite("Site $index", "https://s$index.test")
        }
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onAction(HomeAction.AddSite)
        viewModel.onAction(HomeAction.SiteNameChanged("One more"))
        viewModel.onAction(HomeAction.SiteAddressChanged("more.test"))
        viewModel.onAction(HomeAction.SaveSite)

        assertEquals(
            "Home holds up to ${HomeSites.MAX_SITES} sites. Remove one first.",
            viewModel.uiState.value.siteDialog!!.addressError,
        )
        viewModel.onAction(HomeAction.DismissSiteDialog)
        assertNull(viewModel.uiState.value.siteDialog)
    }

    @Test
    fun editModeToggles() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(HomeAction.ToggleEditSites)
        assertTrue(viewModel.uiState.value.editingSites)
        viewModel.onAction(HomeAction.ToggleEditSites)
        assertTrue(!viewModel.uiState.value.editingSites)
    }

    @Test
    fun recentShowsTheTwoNewestDownloadsWhenHomeAsks() = runTest {
        library.items = listOf(item("3"), item("2"), item("1"))
        val viewModel = viewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.recent.isEmpty())

        viewModel.onAction(HomeAction.RefreshRecent)
        advanceUntilIdle()

        assertEquals(listOf("3", "2"), viewModel.uiState.value.recent.map { it.id })
    }

    @Test
    fun unreadableLibraryLeavesRecentEmpty() = runTest {
        library.failure = IOException("storage gone")
        val viewModel = viewModel()

        viewModel.onAction(HomeAction.RefreshRecent)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.recent.isEmpty())
    }

    @Test
    fun oneVideoFoundOpensVideoYouCopiedAndSeveralKeepTheList() = runTest {
        val viewModel = viewModel()
        val opened = mutableListOf<Unit>()
        val collector = launch { viewModel.quickDownloadRequests.collect { opened += it } }

        inspector.answer = { found(1) }
        viewModel.onAction(HomeAction.LinkChanged("https://a.test/watch"))
        viewModel.onAction(HomeAction.Submit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.quickDownload)
        assertEquals(1, opened.size)

        inspector.answer = { found(2) }
        viewModel.onAction(HomeAction.Submit)
        advanceUntilIdle()
        assertEquals(PromptboxStatus.Found(2), viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.quickDownload)
        assertEquals(1, opened.size)
        collector.cancel()
    }

    private fun found(count: Int) = LinkInspection.Found(
        pageUrl = "https://a.test/watch",
        pageTitle = "Clip page",
        candidates = (1..count).map { index ->
            MediaCandidate(
                pageUrl = "https://a.test/watch",
                mediaUrl = "https://cdn.a.test/$index.mp4",
                sources = setOf(CandidateSource.DOM),
                kind = MediaKind.DIRECT,
            )
        },
    )

    @Test
    fun lastFailureDetailsAreSanitizedAndClearedOnEditOrNewLookup() = runTest {
        inspector.answer = {
            LinkInspection.NotFound(
                "not found",
                details = listOf("page GET 403", "Cookie: redaction-fixture"),
            )
        }
        val viewModel = viewModel()
        val edits = listOf(
            HomeAction.EditLink,
            HomeAction.ClearLink,
            HomeAction.CancelSearch,
            HomeAction.Pasted("https://a.test/replacement"),
            HomeAction.LinkChanged("replacement"),
        )
        edits.forEach { action ->
            viewModel.onAction(HomeAction.LinkChanged("https://a.test/page"))
            viewModel.onAction(HomeAction.Submit)
            advanceUntilIdle()
            assertEquals(listOf("page GET 403"), viewModel.uiState.value.failureDetails)
            viewModel.onAction(action)
            assertTrue(viewModel.uiState.value.failureDetails.isEmpty())
        }

        val answer = CompletableDeferred<LinkInspection>()
        inspector.answer = { answer.await() }
        viewModel.onAction(HomeAction.UseCopied("https://a.test/new"))
        runCurrent()
        assertEquals(PromptboxStatus.Searching, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.failureDetails.isEmpty())
        answer.complete(found(1))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.failureDetails.isEmpty())
    }

    private fun item(id: String) = LibraryItem(
        id = id,
        displayName = "Clip $id.mp4",
        uri = "content://media/external/downloads/$id",
        mimeType = "video/mp4",
        sizeBytes = 1_024,
        modifiedAtEpochMs = id.toLong(),
        location = LibraryLocation.SHARED_DOWNLOADS,
    )

    @Test
    fun aCopiedLinkIsLookedUpOnceWhenTheCheckIsOnAndTheWindowHasFocus() = runTest {
        inspector.answer = { found(2) }
        clipboard.text = "Look: https://a.test/watch?v=1"
        val viewModel = viewModel()

        viewModel.onAction(HomeAction.CheckCopiedLink(windowFocused = false))
        advanceUntilIdle()
        assertEquals(0, clipboard.reads)

        settings.checkCopiedLinks.value = false
        viewModel.onAction(HomeAction.CheckCopiedLink(windowFocused = true))
        advanceUntilIdle()
        assertEquals(0, clipboard.reads)
        assertEquals("", viewModel.uiState.value.link)

        settings.checkCopiedLinks.value = true
        viewModel.onAction(HomeAction.CheckCopiedLink(windowFocused = true))
        advanceUntilIdle()
        assertEquals("https://a.test/watch?v=1", viewModel.uiState.value.link)
        assertEquals(PromptboxStatus.Found(2), viewModel.uiState.value.status)
        assertEquals(listOf("https://a.test/watch?v=1"), inspector.links)

        viewModel.onAction(HomeAction.CheckCopiedLink(windowFocused = true))
        advanceUntilIdle()
        assertEquals(1, inspector.links.size)
    }

    private class FakeSettings : SettingsRepository {
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        override val checkCopiedLinks = MutableStateFlow(true)

        override suspend fun setThemeMode(themeMode: ThemeMode) = Unit

        override suspend fun setCheckCopiedLinks(enabled: Boolean) {
            checkCopiedLinks.value = enabled
        }
    }

    private class FakeClipboard : ClipboardAccess {
        var text: String? = null
        var reads = 0

        override fun peek(): ClipPeek? = text?.let { ClipPeek(isText = true, timestamp = 1) }

        override fun readText(): CharSequence? {
            reads += 1
            return text
        }
    }

    private class FakeInspector : LinkInspector {
        val links = mutableListOf<String>()
        var answer: suspend () -> LinkInspection = { LinkInspection.NotFound("none") }

        override suspend fun inspect(link: String): LinkInspection {
            links += link
            return answer()
        }
    }

    private class FakeSitesRepository(initial: List<HomeSite>) : HomeSitesRepository {
        val value = MutableStateFlow(initial)
        override val sites = value

        override suspend fun update(transform: (List<HomeSite>) -> List<HomeSite>) {
            value.update(transform)
        }
    }

    private class FakeLibrary : LibraryRepository {
        var items: List<LibraryItem> = emptyList()
        var failure: Exception? = null

        override suspend fun items(): List<LibraryItem> {
            failure?.let { throw it }
            return items
        }

        override suspend fun delete(item: LibraryItem): Boolean = false
    }
}
