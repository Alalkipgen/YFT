package com.alal.yft.feature.library

import com.alal.yft.ui.format.YftFormat
import java.util.Locale

/** The Library's filter chips. */
enum class LibraryFilter(val label: String) {
    ALL("All"),
    VIDEO("Video"),
    AUDIO("Audio"),
    ;

    fun matches(item: LibraryItem): Boolean = when (this) {
        ALL -> true
        VIDEO -> item.isVideo
        AUDIO -> item.isAudio
    }
}

/** The orders the sort menu offers; newest first unless the user picks another. */
enum class LibrarySort(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    NAME("Name"),
    LARGEST("Largest first"),
}

/** [items] that pass [filter] and contain [query] in their title, in [sort] order. */
internal fun arrange(
    items: List<LibraryItem>,
    filter: LibraryFilter,
    sort: LibrarySort,
    query: String,
): List<LibraryItem> {
    val needle = query.trim().lowercase(Locale.ROOT)
    val shown = items.filter { item ->
        filter.matches(item) &&
            (needle.isEmpty() || item.displayName.lowercase(Locale.ROOT).contains(needle))
    }
    val byName = compareBy<LibraryItem> { YftFormat.title(it.displayName).lowercase(Locale.ROOT) }
    return when (sort) {
        LibrarySort.NEWEST -> shown.sortedWith(
            compareByDescending<LibraryItem> { it.modifiedAtEpochMs ?: Long.MIN_VALUE }
                .then(byName),
        )
        LibrarySort.OLDEST -> shown.sortedWith(
            compareBy<LibraryItem> { it.modifiedAtEpochMs ?: Long.MAX_VALUE }.then(byName),
        )
        LibrarySort.NAME -> shown.sortedWith(byName)
        LibrarySort.LARGEST -> shown.sortedWith(
            compareByDescending<LibraryItem> { it.sizeBytes ?: -1L }.then(byName),
        )
    }
}

/**
 * The line under a Library tile or Recent card: "720p · 96 MB" once the file's picture size is
 * read, otherwise its format ("MP4 · 96 MB", "M4A · 7 MB").
 */
internal fun libraryMeta(item: LibraryItem, details: MediaDetails?): String = listOfNotNull(
    details?.qualityLabel?.takeUnless { item.isAudio }
        ?: YftFormat.format(item.displayName, item.mimeType),
    item.sizeBytes?.let(YftFormat::bytes),
).joinToString(" · ")
