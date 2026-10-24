package com.alal.yft.update

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** The Updates screen's, the Settings row's and the auto-open's view of [UpdateController]. */
@HiltViewModel
class UpdatesViewModel @Inject constructor(
    private val controller: UpdateController,
) : ViewModel() {
    val state: StateFlow<UpdateState> = controller.state

    fun checkNow() = controller.checkNow()

    fun setAutoCheck(on: Boolean) = controller.setAutoCheck(on)

    fun markOpened(version: String) = controller.markOpened(version)

    fun deferAutoOpen() = controller.deferAutoOpen()
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

/** Test mode's mark on every offer (debug and preview builds). */
internal const val TEST_SUFFIX = " (test)"
