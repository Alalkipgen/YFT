package com.alal.yft.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryArrangementTest {
    private val lake = libraryItem("lake", "Mountain Lake.mp4", sizeBytes = 96, modifiedAt = 30)
    private val waves = libraryItem("waves", "Ocean Waves.m4a", sizeBytes = 7, modifiedAt = 50)
    private val city = libraryItem("city", "city lights.mp4", sizeBytes = 186, modifiedAt = 10)
    private val notes = libraryItem("notes", "notes.bin", sizeBytes = null, modifiedAt = null)
    private val all = listOf(lake, waves, city, notes)

    @Test
    fun filtersKeepVideoOrAudioAndAllKeepsEverything() {
        assertEquals(all.toSet(), arrange(all, LibraryFilter.ALL, LibrarySort.NAME, "").toSet())
        assertEquals(
            listOf("city", "lake"),
            arrange(all, LibraryFilter.VIDEO, LibrarySort.NAME, "").map(LibraryItem::id),
        )
        assertEquals(
            listOf("waves"),
            arrange(all, LibraryFilter.AUDIO, LibrarySort.NAME, "").map(LibraryItem::id),
        )
    }

    @Test
    fun searchMatchesPartOfTheNameInAnyCase() {
        assertEquals(
            listOf("city"),
            arrange(all, LibraryFilter.ALL, LibrarySort.NEWEST, "  LIGHTS ").map(LibraryItem::id),
        )
        assertEquals(
            emptyList<String>(),
            arrange(all, LibraryFilter.AUDIO, LibrarySort.NEWEST, "lake").map(LibraryItem::id),
        )
    }

    @Test
    fun everySortOrderPutsUnknownsLast() {
        fun order(sort: LibrarySort) = arrange(all, LibraryFilter.ALL, sort, "").map { it.id }

        assertEquals(listOf("waves", "lake", "city", "notes"), order(LibrarySort.NEWEST))
        assertEquals(listOf("city", "lake", "waves", "notes"), order(LibrarySort.OLDEST))
        assertEquals(listOf("city", "lake", "notes", "waves"), order(LibrarySort.NAME))
        assertEquals(listOf("city", "lake", "waves", "notes"), order(LibrarySort.LARGEST))
    }

    @Test
    fun metaPrefersThePictureSizeForVideosAndTheFormatOtherwise() {
        val hd = MediaDetails(durationMs = 252_000, width = 1280, height = 720)
        val big = libraryItem("big", "Lake.mp4", sizeBytes = 100_663_296)

        assertEquals("720p · 96 MB", libraryMeta(big, hd))
        assertEquals("MP4 · 96 MB", libraryMeta(big, null))
        assertEquals("MP4 · 96 MB", libraryMeta(big, MediaDetails.Unknown))
        assertEquals(
            "M4A · 7 MB",
            libraryMeta(libraryItem("a", "Waves.m4a", sizeBytes = 7_340_032), hd),
        )
        assertEquals("MP4", libraryMeta(libraryItem("c", "Clip.mp4", sizeBytes = null), null))
    }
}
