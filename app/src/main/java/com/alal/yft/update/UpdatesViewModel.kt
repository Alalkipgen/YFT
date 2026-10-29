package com.alal.yft.update

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** The Updates screen's, the Settings row's and the auto-open's view of the updater. */
@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val controller: UpdateController,
    private val installFlow: UpdateInstallFlow,
) : ViewModel() {
    val state: StateFlow<UpdateState> = controller.state

    /** The in-app download, checks and Android's installer (U2). */
    val install: StateFlow<InstallState> = installFlow.state

    fun checkNow() = controller.checkNow()

    fun setAutoCheck(on: Boolean) = controller.setAutoCheck(on)

    fun markOpened(version: String) = controller.markOpened(version)

    fun deferAutoOpen() = controller.deferAutoOpen()

    fun download(update: FoundUpdate) = installFlow.download(update)

    fun cancelInstall() = installFlow.cancel()

    fun reinstallThisBuild() = installFlow.reinstallThisBuild()

    fun confirmStarted(sessionId: Int) = installFlow.confirmStarted(sessionId)

    fun confirmFailed(sessionId: Int) = installFlow.confirmFailed(sessionId)
}

/** Settings → About's Updates row: its second line and the New badge (§4.5). */
data class UpdatesRow(val supporting: String, val isNew: Boolean)

/**
 * The row never claims more than YFT knows: New with the version once one is found, "Up to date"
 * only after a good answer found none, else "Not checked yet". No row when the build never checks.
 */
fun UpdateState.settingsRow(): UpdatesRow? {
    if (mode == UpdateMode.OFF) return null
    val update = found
    return when {
        update != null -> UpdatesRow(
            supporting = "${update.version.label} available" + if (testMode) TEST_SUFFIX else "",
            isNew = true,
        )
        lastGoodAt != null -> UpdatesRow(supporting = "Up to date", isNew = false)
        else -> UpdatesRow(supporting = "Not checked yet", isNew = false)
    }
}

/** Home's small card while an update is found (v1.0.2; `IN_APP_UPDATE_PLAN.md` §10): its lines. */
data class HomeUpdateCard(val title: String, val supporting: String)

/**
 * The card says only what the Settings row's New says: a found version, kept until the installed
 * version reaches it. None before a find, and none in a build that never checks. No request of
 * its own: Home reads the updater's state.
 */
fun UpdateState.homeCard(): HomeUpdateCard? {
    if (mode == UpdateMode.OFF) return null
    val update = found ?: return null
    return HomeUpdateCard(
        title = "Update available",
        supporting = update.version.label + (if (testMode) TEST_SUFFIX else "") + SEE_WHATS_NEW,
    )
}

private const val SEE_WHATS_NEW = " · See what's new"

/** Test mode's mark on every offer (debug and preview builds). */
internal const val TEST_SUFFIX = " (test)"
