package com.alal.yft.core.download

import java.security.MessageDigest

/**
 * Names the per-task scratch directories of the streaming engines.
 *
 * The name is a hash of the task id, so no title or URL reaches the file system, and the storage
 * janitor can tell which directories still belong to a stored task.
 */
object DownloadWorkspaces {
    const val HASH_CHARS = 24
    const val HLS_PREFIX = "hls"
    const val DASH_PREFIX = "dash"
    const val MUX_PREFIX = "mux"

    fun nameFor(prefix: String, taskId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(taskId.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
        return "$prefix-${digest.take(HASH_CHARS)}"
    }

    /** True for names [nameFor] can produce with [prefix]; anything else is left alone. */
    fun isWorkspaceName(prefix: String, name: String): Boolean {
        if (!name.startsWith("$prefix-")) return false
        val hash = name.substring(prefix.length + 1)
        return hash.length == HASH_CHARS && hash.all { it in '0'..'9' || it in 'a'..'f' }
    }
}
