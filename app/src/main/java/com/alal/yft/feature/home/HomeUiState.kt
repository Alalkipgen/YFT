package com.alal.yft.feature.home

import com.alal.yft.core.model.logging.DiagnosticTextSanitizer
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.feature.library.LibraryItem
import com.alal.yft.ui.components.PromptboxStatus

data class HomeUiState(
    val link: String = "",
    val status: PromptboxStatus = PromptboxStatus.Editing,
    val sites: List<HomeSite> = emptyList(),
    val editingSites: Boolean = false,
    val siteDialog: SiteDialogState? = null,
    /** The newest finished downloads, at most [HomeViewModel.RECENT_COUNT]. */
    val recent: List<LibraryItem> = emptyList(),
    /** Sanitized, memory-only steps for the last failed lookup; cleared on edit/new lookup. */
    val failureDetails: List<String> = emptyList(),
    /** The last lookup found one video, so View opens "Video you copied". */
    val quickDownload: Boolean = false,
)

internal fun HomeUiState.lookupDetailsText(): String {
    val failure = status as? PromptboxStatus.NotFound ?: return ""
    val steps = failureDetails.ifEmpty { listOf("lookup: ${failure.message}") }
    return DiagnosticTextSanitizer.details(steps).joinToString("\n")
        .ifEmpty { "lookup: diagnostics unavailable" }
}

/** The Add site dialog's fields and the problem with each, if any. */
data class SiteDialogState(
    val name: String = "",
    val address: String = "",
    val nameError: String? = null,
    val addressError: String? = null,
)

sealed interface HomeAction {
    data class LinkChanged(val text: String) : HomeAction

    /** Paste: copied text fills the field; nothing is looked up yet. */
    data class Pasted(val clipboardText: String?) : HomeAction

    /** The clipboard row's Use: copied text fills the field and is looked up straight away. */
    data class UseCopied(val clipboardText: String?) : HomeAction
    data object Submit : HomeAction
    data object CancelSearch : HomeAction

    /** Tapping a Found or No-media row returns to the editable field with the same link. */
    data object EditLink : HomeAction
    data object ClearLink : HomeAction

    data object ToggleEditSites : HomeAction
    data object AddSite : HomeAction
    data class SiteNameChanged(val name: String) : HomeAction
    data class SiteAddressChanged(val address: String) : HomeAction
    data object SaveSite : HomeAction
    data object DismissSiteDialog : HomeAction
    data class RemoveSite(val site: HomeSite) : HomeAction

    data object RefreshRecent : HomeAction

    /** Home is resumed; with window focus the copied-link check may read the clipboard. */
    data class CheckCopiedLink(val windowFocused: Boolean) : HomeAction
}
