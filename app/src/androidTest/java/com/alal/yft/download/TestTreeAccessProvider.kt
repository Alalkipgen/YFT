package com.alal.yft.download

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract

/**
 * v1.0.2 item 28: the folder picker's part for [ChosenFolderInstrumentedTest]. It runs in the
 * process of [TestTreeProvider], whose documents only this APK may hand out, and it gives the
 * app the same lasting read and write access to the folder as the picker does ([GRANT]). It
 * also takes that access back ([REVOKE]), deletes the folder ([REMOVE]) and empties it
 * ([RESET]). Each call answers the folder's tree address under [TREE].
 */
class TestTreeAccessProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = requireNotNull(context)
        val tree = treeOf(context)
        val folder = TestTreeProvider.folder(context)
        when (method) {
            GRANT -> context.grantUriPermission(requireNotNull(arg), tree, PICKER_ACCESS)
            REVOKE -> context.revokeUriPermission(tree, ACCESS)
            REMOVE -> folder.deleteRecursively()
            RESET -> {
                folder.deleteRecursively()
                check(folder.mkdirs()) { "Cannot make the folder" }
            }

            else -> return super.call(method, arg, extras)
        }
        return Bundle().apply { putString(TREE, tree.toString()) }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        /** Gives the package in the call's argument the picker's access to the folder. */
        const val GRANT = "grant"
        const val REVOKE = "revoke"
        const val REMOVE = "remove"
        const val RESET = "reset"
        const val TREE = "tree"

        private const val ACCESS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        /** What the folder picker gives for a picked folder. */
        private const val PICKER_ACCESS = ACCESS or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

        private fun treeOf(context: Context): Uri = DocumentsContract.buildTreeDocumentUri(
            "${context.packageName}.tree",
            TestTreeProvider.ROOT_ID,
        )
    }
}
