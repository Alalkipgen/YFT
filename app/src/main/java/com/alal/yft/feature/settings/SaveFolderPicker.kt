package com.alal.yft.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts

/**
 * v1.0.2 item 28: Android's folder picker (ACTION_OPEN_DOCUMENT_TREE) for Settings › Save files
 * to. On Android 7-9 it also offers the phone's own storage, which the picker hides there unless
 * asked. A cancelled picker gives null.
 */
internal class SaveFolderPicker : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        super.createIntent(context, input).putExtra(EXTRA_SHOW_ADVANCED, true)

    private companion object {
        /** The picker's own extra for its "Show internal storage" choice. */
        const val EXTRA_SHOW_ADVANCED = "android.content.extra.SHOW_ADVANCED"
    }
}
