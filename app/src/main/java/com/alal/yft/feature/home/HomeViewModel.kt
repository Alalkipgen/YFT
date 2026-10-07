package com.alal.yft.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.browser.policy.BrowserAddressNormalizer
import com.alal.yft.core.browser.policy.BrowserAddressResult
import com.alal.yft.core.data.preferences.HomeSitesRepository
import com.alal.yft.core.data.preferences.SettingsRepository
import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import com.alal.yft.core.model.media.MediaGroup
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.core.model.settings.HomeSites
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.detectedmedia.LookupOwner
import com.alal.yft.feature.detectedmedia.PageVideoLookup
import com.alal.yft.feature.library.LibraryRepository
import com.alal.yft.ui.components.PromptboxStatus
import com.alal.yft.ui.components.isSavable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Home: the Promptbox, "Your sites" and the two newest downloads.
 *
 * A submitted link is checked by [LinkInspector]; what it finds goes to [DetectedMediaStore],
 * the same memory-only place the browser fills, so View opens the usual Detected Media list.
 * Candidates of one video count once (P3). When it found one video, Home opens its download
 * sheet instead and View reopens it. P16: a link to a video of a site an adapter reads opens the
 * sheet at once; the sheet waits on this lookup, shows its failure with Try again, and closing it
 * stops the lookup. P24: previews and ads of a page are not counted; a page with one main video
 * opens its sheet at once with the rest under "Other videos on this page".
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val inspector: LinkInspector,
    private val sitesRepository: HomeSitesRepository,
    private val library: LibraryRepository,
    private val detectedMediaStore: DetectedMediaStore,
    private val settings: SettingsRepository,
    private val copiedLinks: CopiedLinkWatcher,
) : ViewModel() {
    private val local = MutableStateFlow(HomeUiState())
    private val quickDownloads = Channel<Unit>(Channel.CONFLATED)

    /** One event per lookup that found one video: Home then opens its download sheet. */
    val quickDownloadRequests: Flow<Unit> = quickDownloads.receiveAsFlow()
    private var inspection: Job? = null

    /** The one video the last lookup found, which View hands to the sheet again. */
    private var foundVideo: MediaGroup? = null

    /** P24: how many previews and other videos the page of [foundVideo] has. */
    private var foundOthers = 0
    private var recentJob: Job? = null

    /** P29: the page the last lookup found and the link it came from, for Try again. */
    private var foundPage: String? = null
    private var foundLink: String? = null

    /** P16: the lookup the open sheet waits on, and the link it looks up, until it found it. */
    private var sheetLookup: PageVideoLookup? = null
    private var sheetLink: String? = null

    val uiState: StateFlow<HomeUiState> = combine(local, sitesRepository.sites) { state, sites ->
        state.copy(sites = sites)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        viewModelScope.launch {
            detectedMediaStore.lookupRetries.collect(::retrySheetLookup)
        }
        viewModelScope.launch {
            detectedMediaStore.lookupCloses.collect(::closeSheetLookup)
        }
        viewModelScope.launch {
            detectedMediaStore.pageReads.collect(::readFoundPageAgain)
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.LinkChanged -> local.update {
                it.copy(link = action.text.take(HomeLinks.MAX_LENGTH), failureDetails = emptyList())
            }
            is HomeAction.Pasted -> HomeLinks.fromClipboard(action.clipboardText)?.let { link ->
                stopInspection()
                local.update {
                    it.copy(
                        link = link,
                        status = PromptboxStatus.Editing,
                        failureDetails = emptyList(),
                    )
                }
            }
            is HomeAction.UseCopied -> HomeLinks.fromClipboard(action.clipboardText)?.let { link ->
                local.update { it.copy(link = link) }
                submit()
            }
            HomeAction.Submit -> submit()
            HomeAction.CancelSearch, HomeAction.EditLink -> {
                stopInspection()
                local.update {
                    it.copy(status = PromptboxStatus.Editing, failureDetails = emptyList())
                }
            }
            HomeAction.ClearLink -> {
                stopInspection()
                local.update {
                    it.copy(
                        link = "",
                        status = PromptboxStatus.Editing,
                        failureDetails = emptyList(),
                    )
                }
            }
            HomeAction.ToggleEditSites -> local.update { it.copy(editingSites = !it.editingSites) }
            HomeAction.AddSite -> local.update { it.copy(siteDialog = SiteDialogState()) }
            is HomeAction.SiteNameChanged -> local.update { state ->
                state.copy(
                    siteDialog = state.siteDialog?.copy(
                        name = action.name.take(HomeSites.MAX_NAME_LENGTH),
                        nameError = null,
                    ),
                )
            }
            is HomeAction.SiteAddressChanged -> local.update { state ->
                state.copy(
                    siteDialog = state.siteDialog?.copy(
                        address = action.address.take(HomeSites.MAX_URL_LENGTH),
                        addressError = null,
                    ),
                )
            }
            HomeAction.SaveSite -> saveSite()
            HomeAction.DismissSiteDialog -> local.update { it.copy(siteDialog = null) }
            is HomeAction.RemoveSite -> viewModelScope.launch {
                sitesRepository.update { sites -> sites.filterNot { it.url == action.site.url } }
            }
            // Sent by Home each time it comes into view, so Recent follows the library.
            HomeAction.RefreshRecent -> refreshRecent()
            is HomeAction.CheckCopiedLink -> checkCopiedLink(action.windowFocused)
        }
    }

    private fun submit() {
        val link = local.value.link.trim()
        if (link.isEmpty()) return
        stopInspection()
        foundVideo = null
        foundOthers = 0
        foundPage = null
        foundLink = null
        // P16: a supported site's video opens its sheet now; the lookup below fills it.
        val sheet = inspector.siteVideo(link)?.let { video ->
            PageVideoLookup(video.key, video.pageUrl, title = null, owner = LookupOwner.HOME)
        }
        if (sheet != null) detectedMediaStore.awaitPageVideo()
        inspect(link, sheet)
        if (sheet != null) quickDownloads.trySend(Unit)
    }

    /**
     * Looks [link] up; [sheet] is the lookup an open sheet shows, null when none waits. [fresh]
     * (the sheet's Try again) skips a remembered answer (P17).
     */
    private fun inspect(link: String, sheet: PageVideoLookup?, fresh: Boolean = false) {
        sheetLookup = sheet
        sheetLink = link
        sheet?.let(detectedMediaStore::showLookup)
        local.update {
            it.copy(
                link = link,
                status = PromptboxStatus.Searching,
                failureDetails = emptyList(),
                quickDownload = false,
            )
        }
        inspection = viewModelScope.launch {
            val slowStatus = launch {
                delay(SLOW_CONNECTION_MILLIS)
                local.update { state ->
                    if (state.status == PromptboxStatus.Searching) {
                        state.copy(status = PromptboxStatus.SlowSearching)
                    } else {
                        state
                    }
                }
            }
            try {
                var details = emptyList<String>()
                var quick = false
                var sheetFailure: PageVideoLookup? = null
                val result = if (fresh) inspector.inspectAgain(link) else inspector.inspect(link)
                val status = when (result) {
                    is LinkInspection.Found -> {
                        // P28: the length the page states tells its video from its ads.
                        val facts = result.facts
                        val candidates = MediaGroups.withPageRoles(result.candidates, facts)
                        detectedMediaStore.publish(
                            pageUrl = result.pageUrl,
                            pageTitle = result.pageTitle,
                            candidates = candidates,
                            facts = facts,
                            owner = LookupOwner.HOME,
                        )
                        foundPage = result.pageUrl
                        foundLink = link
                        // Only media YFT may save is counted, once per video; DRM-protected
                        // candidates are never offered, so a page with nothing else reads as "not
                        // found".
                        val videos = MediaGroups.pageVideos(
                            candidates
                                .take(DetectedMediaStore.MAX_CANDIDATES)
                                .filter { it.isSavable },
                        )
                        if (videos.isNotEmpty()) {
                            // P24: the count counts the page's videos, not its previews and
                            // ads; one main video opens its sheet with the rest behind it.
                            // P28: with the page's title and picture where it names none.
                            val list = MediaGroups.ofPage(videos, facts)
                            foundOthers = videos.size - 1
                            foundVideo = list.videos.singleOrNull()
                                ?.let { MediaGroups.withPageFacts(it, facts) }
                                ?.also { main ->
                                    detectedMediaStore.select(main, otherVideos = foundOthers)
                                }
                            quick = foundVideo != null
                            // P16: the open sheet shows the link's video, the list the rest.
                            if (sheet != null && foundVideo == null) {
                                detectedMediaStore.select(
                                    MediaGroups.withPageFacts(list.videos.first(), facts),
                                )
                            }
                            PromptboxStatus.Found(count = list.videos.size)
                        } else {
                            details = listOf("media check: DRM only")
                            sheetFailure = sheet?.copy(failure = PROTECTED_ONLY_MESSAGE)
                            PromptboxStatus.NotFound(message = PROTECTED_ONLY_MESSAGE)
                        }
                    }
                    is LinkInspection.NotFound -> {
                        details = DiagnosticTextSanitizer.details(
                            result.details.ifEmpty { listOf("lookup: ${result.message}") },
                        )
                        sheetFailure = sheet?.copy(
                            failure = result.message,
                            canRetry = result.canRetry,
                        )
                        PromptboxStatus.NotFound(
                            message = result.message,
                            canOpenInBrowser = result.canOpenInBrowser,
                        )
                    }
                }
                local.update {
                    it.copy(status = status, failureDetails = details, quickDownload = quick)
                }
                when {
                    sheet == null -> if (quick) quickDownloads.trySend(Unit)
                    // The sheet that waits shows why, with Try again when that can help.
                    sheetFailure != null -> detectedMediaStore.showLookup(sheetFailure)
                    else -> {
                        sheetLookup = null
                        detectedMediaStore.clearLookup(LookupOwner.HOME)
                    }
                }
            } finally {
                slowStatus.cancel()
            }
        }
    }

    /**
     * "Check copied links when YFT opens": a link copied since the last check fills the
     * Promptbox and is looked up like Use. The watcher reads the clip only when allowed.
     */
    private fun checkCopiedLink(windowFocused: Boolean) {
        if (!windowFocused) return
        viewModelScope.launch {
            val enabled = try {
                settings.checkCopiedLinks.first()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                false
            }
            val link = copiedLinks.poll(enabled = enabled, windowFocused = true) ?: return@launch
            local.update { it.copy(link = link) }
            submit()
        }
    }

    /** View on one found video: the sheet shows it again, whatever the browser found since. */
    fun selectFoundVideo() {
        foundVideo?.let { detectedMediaStore.select(it, otherVideos = foundOthers) }
    }

    private fun stopInspection() {
        inspection?.cancel()
        inspection = null
        // P16: a sheet must not wait on a lookup that no longer runs.
        sheetLookup = null
        detectedMediaStore.clearLookup(LookupOwner.HOME)
    }

    /** P16: the sheet's Try again after this link's lookup failed asks once more. */
    private fun retrySheetLookup(key: String) {
        val sheet = sheetLookup ?: return
        val shown = detectedMediaStore.lookup.value ?: return
        if (sheet.key != key || shown.owner != LookupOwner.HOME || shown.key != key) return
        if (!shown.canRetry || inspection?.isActive == true) return
        val link = sheetLink ?: return
        inspect(link, sheet, fresh = true)
    }

    /**
     * P29: the sheet's Try again on the page Home found: the page is read again without a
     * remembered answer, quietly (the Promptbox keeps its state), so the sheet gets the page's
     * current addresses instead of the dead one.
     */
    private fun readFoundPageAgain(pageUrl: String) {
        val link = foundLink?.takeIf { foundPage == pageUrl }
        if (link == null) {
            detectedMediaStore.pageReadDone(pageUrl)
            return
        }
        viewModelScope.launch {
            try {
                val result = inspector.inspectAgain(link)
                if (result is LinkInspection.Found && result.pageUrl == pageUrl) {
                    val facts = result.facts
                        ?.orElse(detectedMediaStore.page.value?.facts)
                        ?: detectedMediaStore.page.value?.facts
                    detectedMediaStore.publish(
                        pageUrl = result.pageUrl,
                        pageTitle = result.pageTitle,
                        candidates = MediaGroups.withPageRoles(result.candidates, facts),
                        facts = facts,
                        owner = LookupOwner.HOME,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // The sheet keeps the files it had and says why they failed.
            } finally {
                detectedMediaStore.pageReadDone(pageUrl)
            }
        }
    }

    /** P16: the sheet closed before this link's video came; its lookup stops. */
    private fun closeSheetLookup(key: String) {
        if (sheetLookup?.key != key) return
        val running = inspection?.isActive == true
        stopInspection()
        if (running) {
            local.update {
                it.copy(status = PromptboxStatus.Editing, failureDetails = emptyList())
            }
        }
    }

    private fun saveSite() {
        val dialog = local.value.siteDialog ?: return
        val name = HomeSites.cleanName(dialog.name)
        val address = BrowserAddressNormalizer.normalize(dialog.address)
        val nameError = if (name.isEmpty()) "Enter a name" else null
        val url = (address as? BrowserAddressResult.Valid)?.url
            ?.takeIf { it.startsWith("https://") }
        val addressError = when {
            address is BrowserAddressResult.Invalid -> address.reason
            url == null -> "Enter a valid HTTPS address"
            uiState.value.sites.any { it.url == url } -> "This site is already on Home"
            uiState.value.sites.size >= HomeSites.MAX_SITES ->
                "Home holds up to ${HomeSites.MAX_SITES} sites. Remove one first."
            else -> null
        }
        if (nameError != null || addressError != null || url == null) {
            val checked = dialog.copy(nameError = nameError, addressError = addressError)
            local.update { it.copy(siteDialog = checked) }
            return
        }
        local.update { it.copy(siteDialog = null) }
        viewModelScope.launch {
            sitesRepository.update { sites ->
                if (sites.any { it.url == url }) sites else sites + HomeSite(name, url)
            }
        }
    }

    private fun refreshRecent() {
        recentJob?.cancel()
        recentJob = viewModelScope.launch {
            val items = try {
                library.items().take(RECENT_COUNT)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // An unreadable library only empties Recent; the Library tab reports the error.
                emptyList()
            }
            local.update { it.copy(recent = items) }
        }
    }

    internal companion object {
        const val SLOW_CONNECTION_MILLIS = 10_000L
        const val RECENT_COUNT = 2
        const val PROTECTED_ONLY_MESSAGE = "Protected media (DRM) can't be saved"
    }
}
