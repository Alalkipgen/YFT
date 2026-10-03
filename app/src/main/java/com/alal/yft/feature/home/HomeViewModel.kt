package com.alal.yft.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alal.yft.core.browser.policy.BrowserAddressNormalizer
import com.alal.yft.core.browser.policy.BrowserAddressResult
import com.alal.yft.core.data.preferences.HomeSitesRepository
import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.core.model.settings.HomeSites
import com.alal.yft.feature.detectedmedia.DetectedMediaStore
import com.alal.yft.feature.library.LibraryRepository
import com.alal.yft.ui.components.PromptboxStatus
import com.alal.yft.ui.components.isSavable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Home: the Promptbox, "Your sites" and the two newest downloads.
 *
 * A submitted link is checked by [LinkInspector]; what it finds goes to [DetectedMediaStore],
 * the same memory-only place the browser fills, so View opens the usual Detected Media list.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val inspector: LinkInspector,
    private val sitesRepository: HomeSitesRepository,
    private val library: LibraryRepository,
    private val detectedMediaStore: DetectedMediaStore,
) : ViewModel() {
    private val local = MutableStateFlow(HomeUiState())
    private var inspection: Job? = null
    private var recentJob: Job? = null

    val uiState: StateFlow<HomeUiState> = combine(local, sitesRepository.sites) { state, sites ->
        state.copy(sites = sites)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

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
        }
    }

    private fun submit() {
        val link = local.value.link.trim()
        if (link.isEmpty()) return
        stopInspection()
        local.update {
            it.copy(link = link, status = PromptboxStatus.Searching, failureDetails = emptyList())
        }
        inspection = viewModelScope.launch {
            var details = emptyList<String>()
            val status = when (val result = inspector.inspect(link)) {
                is LinkInspection.Found -> {
                    detectedMediaStore.publish(
                        pageUrl = result.pageUrl,
                        pageTitle = result.pageTitle,
                        candidates = result.candidates,
                    )
                    // Only media YFT may save is counted; DRM-protected candidates are never
                    // offered, so a page with nothing else reads as "not found".
                    val savable = result.candidates
                        .take(DetectedMediaStore.MAX_CANDIDATES)
                        .count { it.isSavable }
                    if (savable > 0) {
                        PromptboxStatus.Found(count = savable)
                    } else {
                        details = listOf("media check: DRM only")
                        PromptboxStatus.NotFound(message = PROTECTED_ONLY_MESSAGE)
                    }
                }
                is LinkInspection.NotFound -> {
                    details = DiagnosticTextSanitizer.details(
                        result.details.ifEmpty { listOf("lookup: ${result.message}") },
                    )
                    PromptboxStatus.NotFound(
                        message = result.message,
                        canOpenInBrowser = result.canOpenInBrowser,
                    )
                }
            }
            local.update { it.copy(status = status, failureDetails = details) }
        }
    }

    private fun stopInspection() {
        inspection?.cancel()
        inspection = null
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
        const val RECENT_COUNT = 2
        const val PROTECTED_ONLY_MESSAGE = "Protected media (DRM) can't be saved"
    }
}
