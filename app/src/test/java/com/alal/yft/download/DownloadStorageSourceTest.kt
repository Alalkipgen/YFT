package com.alal.yft.download

import com.alal.yft.core.data.preferences.DownloadPreferencesRepository
import com.alal.yft.core.model.settings.DownloadLocation
import com.alal.yft.core.model.settings.DownloadPreferences
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
    fun noneReportsNothing() = runTest {
        assertNull(DownloadStorageSource.None.location.first())
        assertNull(DownloadStorageSource.None.freeBytes(DownloadLocation.APP_STORAGE))
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
