package com.alal.yft.download

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import com.alal.yft.core.model.settings.DownloadLocation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs

@RunWith(RobolectricTestRunner::class)
class StatFsStorageSpaceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [35])
    fun appStorageMeasuresTheDataVolumeAndSharedTakesTheSmallerOne() {
        ShadowStatFs.registerStats(context.noBackupFilesDir, 1_000, 500, 400)
        @Suppress("DEPRECATION")
        ShadowStatFs.registerStats(Environment.getExternalStorageDirectory(), 1_000, 300, 200)
        val space = StatFsStorageSpace(context)

        assertEquals(
            400L * ShadowStatFs.BLOCK_SIZE,
            space.availableBytes(DownloadLocation.APP_STORAGE),
        )
        assertEquals(
            200L * ShadowStatFs.BLOCK_SIZE,
            space.availableBytes(DownloadLocation.SHARED_DOWNLOADS),
        )
    }

    @Test
    @Config(sdk = [28])
    fun beforeAndroid10SharedDownloadsAreStagedInAppStorageOnly() {
        ShadowStatFs.registerStats(context.noBackupFilesDir, 1_000, 500, 400)
        @Suppress("DEPRECATION")
        ShadowStatFs.registerStats(Environment.getExternalStorageDirectory(), 1_000, 300, 200)

        assertEquals(
            400L * ShadowStatFs.BLOCK_SIZE,
            StatFsStorageSpace(context).availableBytes(DownloadLocation.SHARED_DOWNLOADS),
        )
    }
}
