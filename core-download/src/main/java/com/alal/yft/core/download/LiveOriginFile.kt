package com.alal.yft.core.download

import java.util.Locale
import okhttp3.HttpUrl

/**
 * A YouTube media file of a live broadcast, on air or recently ended (`source=yt_live_broadcast`,
 * or `yt_premiere_broadcast` for a premiere). After the stream the file is still being written:
 * its stated length can be stale, a range answer can stop short of what was asked, and its
 * validators can change. For such a file the engines trust the first range's length, ask again
 * from the next byte after a short answer, and do not compare totals or validators; every other
 * file stays strict.
 */
internal object LiveOriginFile {
    /** Short answers in a row one range may take before it fails. */
    const val MAX_SHORT_ANSWERS = 1_024

    private val SOURCES = setOf("yt_live_broadcast", "yt_premiere_broadcast")

    fun matches(url: HttpUrl): Boolean =
        url.queryParameter("source")?.lowercase(Locale.US) in SOURCES
}
