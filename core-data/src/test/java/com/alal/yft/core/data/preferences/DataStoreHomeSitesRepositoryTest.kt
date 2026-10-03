package com.alal.yft.core.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.alal.yft.core.model.settings.HomeSite
import com.alal.yft.core.model.settings.HomeSites
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreHomeSitesRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `first run shows the default sites and changes persist, including an empty list`() =
        runTest {
            val store = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { File(temporaryFolder.root, "settings.preferences_pb") },
            )
            val repository = DataStoreHomeSitesRepository(store)
            assertEquals(HomeSites.DEFAULTS, repository.sites.first())

            val added = HomeSite("Example", "https://example.com")
            repository.update { it + added }
            assertEquals(
                HomeSites.DEFAULTS + added,
                DataStoreHomeSitesRepository(store).sites.first(),
            )

            repository.update { emptyList() }
            assertEquals(emptyList<HomeSite>(), DataStoreHomeSitesRepository(store).sites.first())
        }
}
