package com.alal.yft.download

import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.DownloadPreferences
import com.alal.yft.core.model.settings.SaveFolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadStorageSourceTest {
    private val preferences = FakePreferences(
        DownloadPreferences(location = DownloadLocation.SHARED_DOWNLOADS),
    )
    private val measured = mutableListOf<DownloadLocation>()
    private val space = StorageSpace { location ->
        measured += location
        FREE_BYTES
    }

    @Test
    fun theChosenLocationIsReportedAndMeasured() = runTest {
        val source = PreferencesDownloadStorageSource(
            preferences = preferences,
            space = space,
            sharedDownloadsSupported = true,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        assertEquals(DownloadLocation.SHARED_DOWNLOADS, source.location.first())
        assertEquals(FREE_BYTES, source.freeBytes(DownloadLocation.SHARED_DOWNLOADS))
        assertEquals(listOf(DownloadLocation.SHARED_DOWNLOADS), measured)

        preferences.state.value = DownloadPreferences(location = DownloadLocation.APP_STORAGE)
        assertEquals(DownloadLocation.APP_STORAGE, source.location.first())
    }

    @Test
    fun sharedDownloadsFallBackToAppStorageBeforeAndroid10() = runTest {
        val source = PreferencesDownloadStorageSource(
            preferences = preferences,
            space = space,
            sharedDownloadsSupported = false,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        assertEquals(DownloadLocation.APP_STORAGE, source.location.first())
    }

    @Test
    fun `a chosen folder is named while it can be used, and never measured`() = runTest {
        val folder = SaveFolder("content://example/tree/primary%3AMovies", "Movies")
        preferences.state.value = DownloadPreferences(
            location = DownloadLocation.CHOSEN_FOLDER,
            folder = folder,
        )
        val folders = UsableFolders(usable = true)
        val source = PreferencesDownloadStorageSource(
            preferences = preferences,
            space = space,
            sharedDownloadsSupported = true,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            folders = folders,
        )

        assertEquals(DownloadLocation.CHOSEN_FOLDER, source.location.first())
        assertEquals("Movies", source.folderName())

        folders.usable = false
        assertNull(source.folderName())
        assertEquals(DownloadLocation.SHARED_DOWNLOADS, source.defaultLocation)
        assertEquals(listOf(folder, folder), folders.asked)
        assertEquals(emptyList<DownloadLocation>(), measured)
    }

    @Test
    fun `without a chosen folder there is no folder name`() = runTest {
        val source = PreferencesDownloadStorageSource(
            preferences = preferences,
            space = space,
            sharedDownloadsSupported = false,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            folders = UsableFolders(usable = true),
        )

        assertNull(source.folderName())
        assertEquals(DownloadLocation.APP_STORAGE, source.defaultLocation)
        assertNull(DownloadStorageSource.None.folderName())
    }

    @Test
    fun noneReportsNothing() = runTest {
        assertNull(DownloadStorageSource.None.location.first())
        assertNull(DownloadStorageSource.None.freeBytes(DownloadLocation.APP_STORAGE))
    }

    private class UsableFolders(var usable: Boolean) : SaveFolders {
        val asked = mutableListOf<SaveFolder>()

        override fun take(treeUri: String): SaveFolder? = null

        override fun usable(folder: SaveFolder): Boolean {
            asked += folder
            return usable
        }

        override fun entries(folder: SaveFolder): List<FolderEntry>? = null
    }

    private class FakePreferences(initial: DownloadPreferences) : DownloadPreferencesRepository {
        val state = MutableStateFlow(initial)

        override val preferences: Flow<DownloadPreferences> = state

        override suspend fun update(transform: (DownloadPreferences) -> DownloadPreferences) {
            state.value = transform(state.value)
        }
    }

    private companion object {
        const val FREE_BYTES = 19_541_180_006L
    }
}
