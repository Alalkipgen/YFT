package com.alal.yft.download

import com.alal.yft.core.model.settings.SaveFolder
import java.net.URI
import java.net.URLDecoder

/**
 * M1 (v1.0.2): a folder picked in Android's folder picker as people read it: its place on the
 * phone's storage or a memory card ("Movies/Test", "Download/YFT"), read from the picker's
 * address (`content://com.android.externalstorage.documents/tree/primary%3AMovies%2FTest`).
 */
internal object SaveFolderPaths {
    private const val STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val PHONE_STORAGE = "primary"

    /** "Movies/Test"; null when the address names no folder on a storage volume (a cloud app). */
    fun path(folder: SaveFolder): String? = documentId(folder)
        ?.substringAfter(':')
        ?.trim('/')
        ?.takeIf { it.isNotEmpty() }

    /** Whether the folder is on the phone's own storage, whose free space can be read. */
    fun onPhoneStorage(folder: SaveFolder): Boolean =
        documentId(folder)?.substringBefore(':') == PHONE_STORAGE

    /** The Downloads chip's and the empty Library's name for the folder: its path, else name. */
    fun label(folder: SaveFolder): String = path(folder) ?: folder.name

    private fun documentId(folder: SaveFolder): String? {
        val uri = runCatching { URI(folder.treeUri) }.getOrNull() ?: return null
        if (uri.scheme != "content" || uri.rawAuthority != STORAGE_AUTHORITY) return null
        val segments = uri.rawPath.orEmpty().split('/')
        val tree = segments.indexOf("tree")
        if (tree < 0) return null
        val id = segments.getOrNull(tree + 1)?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { URLDecoder.decode(id.replace("+", "%2B"), "UTF-8") }
            .getOrNull()
            ?.takeIf { ':' in it }
    }
}
